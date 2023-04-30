/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.video;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Pair;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.mediacodec.MediaFormatUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener.EventDispatcher;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import com.google.android.exoplayer2.util.Logger;
import com.google.android.exoplayer2.util.AmazonQuirks;

/**
 * Decodes and renders video using {@link MediaCodec}.
 *
 * <p>This renderer accepts the following messages sent via {@link ExoPlayer#createMessage(Target)}
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link C#MSG_SET_SURFACE} to set the output surface. The message payload
 *       should be the target {@link Surface}, or null.
 *   <li>Message with type {@link C#MSG_SET_SCALING_MODE} to set the video scaling mode. The message
 *       payload should be one of the integer scaling modes in {@link C.VideoScalingMode}. Note that
 *       the scaling mode only applies if the {@link Surface} targeted by this renderer is owned by
 *       a {@link android.view.SurfaceView}.
 * </ul>
 */
public class MediaCodecVideoRenderer extends MediaCodecRenderer {

  private static final String TAG = "MediaCodecVideoRenderer";
  private static final String KEY_CROP_LEFT = "crop-left";
  private static final String KEY_CROP_RIGHT = "crop-right";
  private static final String KEY_CROP_BOTTOM = "crop-bottom";
  private static final String KEY_CROP_TOP = "crop-top";

  // Long edge length in pixels for standard video formats, in decreasing in order.
  private static final int[] STANDARD_LONG_EDGE_VIDEO_PX = new int[] {
      1920, 1600, 1440, 1280, 960, 854, 640, 540, 480};

  // Generally there is zero or one pending output stream offset. We track more offsets to allow for
  // pending output streams that have fewer frames than the codec latency.
  private static final int MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT = 10;
  /**
   * Scale factor for the initial maximum input size used to configure the codec in non-adaptive
   * playbacks. See {@link #getCodecMaxValues(MediaCodecInfo, Format, Format[])}.
   */
  private static final float INITIAL_FORMAT_MAX_INPUT_SIZE_SCALE_FACTOR = 1.5f;

  private static boolean evaluatedDeviceNeedsSetOutputSurfaceWorkaround;
  private static boolean deviceNeedsSetOutputSurfaceWorkaround;

  private final Context context;
  private final VideoFrameReleaseTimeHelper frameReleaseTimeHelper;
  private final EventDispatcher eventDispatcher;
  private final long allowedJoiningTimeMs;
  private final int maxDroppedFramesToNotify;
  private final boolean deviceNeedsNoPostProcessWorkaround;
  private final long[] pendingOutputStreamOffsetsUs;
  private final long[] pendingOutputStreamSwitchTimesUs;

  private CodecMaxValues codecMaxValues;
  private boolean codecNeedsSetOutputSurfaceWorkaround;

  private Surface surface;
  private Surface dummySurface;
  @C.VideoScalingMode
  private int scalingMode;
  private boolean renderedFirstFrame;
  private long initialPositionUs;
  private long joiningDeadlineMs;
  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrames;
  private int consecutiveDroppedFrameCount;
  private int buffersInCodecCount;
  private long lastRenderTimeUs;

  private int pendingRotationDegrees;
  private float pendingPixelWidthHeightRatio;
  private int currentWidth;
  private int currentHeight;
  private int currentUnappliedRotationDegrees;
  private float currentPixelWidthHeightRatio;
  private int reportedWidth;
  private int reportedHeight;
  private int reportedUnappliedRotationDegrees;
  private float reportedPixelWidthHeightRatio;

  private boolean tunneling;
  private int tunnelingAudioSessionId;
  /* package */ OnFrameRenderedListenerV23 tunnelingOnFrameRenderedListener;

  private long lastInputTimeUs;
  private long outputStreamOffsetUs;
  private int pendingOutputStreamOffsetCount;
  private @Nullable VideoFrameMetadataListener frameMetadataListener;

  private final Logger log = new Logger(Logger.Module.Video, TAG);
  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
    this(context, mediaCodecSelector, 0);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   */
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs) {
    this(
        context,
        mediaCodecSelector,
        allowedJoiningTimeMs,
        /* eventHandler= */ null,
        /* eventListener= */ null,
        /* maxDroppedFramesToNotify= */ -1);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        context,
        mediaCodecSelector,
        allowedJoiningTimeMs,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        context,
        mediaCodecSelector,
        allowedJoiningTimeMs,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        /* enableDecoderFallback= */ false,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param enableDecoderFallback Whether to enable fallback to lower-priority decoders if decoder
   *     initialization fails. This may result in using a decoder that is slower/less efficient than
   *     the primary decoder.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    super(
        C.TRACK_TYPE_VIDEO,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        enableDecoderFallback,
        /* assumedMinimumCodecOperatingRate= */ 30);
    this.allowedJoiningTimeMs = allowedJoiningTimeMs;
    this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
    this.context = context.getApplicationContext();
    // AMZN_CHANGE_BEGIN
    if (AmazonQuirks.isSnappingToVsyncDisabled()) {
      frameReleaseTimeHelper = new VideoFrameReleaseTimeHelper();
    } else {
      frameReleaseTimeHelper = new VideoFrameReleaseTimeHelper(this.context);
    }
    // AMZN_CHANGE_END
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    deviceNeedsNoPostProcessWorkaround = deviceNeedsNoPostProcessWorkaround();
    pendingOutputStreamOffsetsUs = new long[MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT];
    pendingOutputStreamSwitchTimesUs = new long[MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT];
    outputStreamOffsetUs = C.TIME_UNSET;
    lastInputTimeUs = C.TIME_UNSET;
    joiningDeadlineMs = C.TIME_UNSET;
    currentWidth = Format.NO_VALUE;
    currentHeight = Format.NO_VALUE;
    currentPixelWidthHeightRatio = Format.NO_VALUE;
    pendingPixelWidthHeightRatio = Format.NO_VALUE;
    scalingMode = C.VIDEO_SCALING_MODE_DEFAULT;
    clearReportedVideoSize();
  }

  @Override
  protected int supportsFormat(MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Format format)
      throws DecoderQueryException {
    String mimeType = format.sampleMimeType;
    if (!MimeTypes.isVideo(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    boolean requiresSecureDecryption = false;
    DrmInitData drmInitData = format.drmInitData;
    if (drmInitData != null) {
      for (int i = 0; i < drmInitData.schemeDataCount; i++) {
        requiresSecureDecryption |= drmInitData.get(i).requiresSecureDecryption;
      }
    }
    List<MediaCodecInfo> decoderInfos =
        getDecoderInfos(mediaCodecSelector, format, requiresSecureDecryption);
    if (decoderInfos.isEmpty()) {
      return requiresSecureDecryption
              && !mediaCodecSelector
                  .getDecoderInfos(
                      format.sampleMimeType,
                      /* requiresSecureDecoder= */ false,
                      /* requiresTunnelingDecoder= */ false)
                  .isEmpty()
          ? FORMAT_UNSUPPORTED_DRM
          : FORMAT_UNSUPPORTED_SUBTYPE;
    }
    if (!supportsFormatDrm(drmSessionManager, drmInitData)) {
      return FORMAT_UNSUPPORTED_DRM;
    }
    // Check capabilities for the first decoder in the list, which takes priority.
    MediaCodecInfo decoderInfo = decoderInfos.get(0);
    boolean isFormatSupported = decoderInfo.isFormatSupported(format);
    int adaptiveSupport =
        decoderInfo.isSeamlessAdaptationSupported(format)
            ? ADAPTIVE_SEAMLESS
            : ADAPTIVE_NOT_SEAMLESS;
    int tunnelingSupport = TUNNELING_NOT_SUPPORTED;
    if (isFormatSupported) {
      List<MediaCodecInfo> tunnelingDecoderInfos =
          mediaCodecSelector.getDecoderInfos(
              format.sampleMimeType,
              requiresSecureDecryption,
              /* requiresTunnelingDecoder= */ true);
      if (!tunnelingDecoderInfos.isEmpty()) {
        MediaCodecInfo tunnelingDecoderInfo = tunnelingDecoderInfos.get(0);
        if (tunnelingDecoderInfo.isFormatSupported(format)
            && tunnelingDecoderInfo.isSeamlessAdaptationSupported(format)) {
          tunnelingSupport = TUNNELING_SUPPORTED;
        }
      }
    }
    int formatSupport = isFormatSupported ? FORMAT_HANDLED : FORMAT_EXCEEDS_CAPABILITIES;
    return adaptiveSupport | tunnelingSupport | formatSupport;
  }

  @Override
  protected List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException {
    List<MediaCodecInfo> decoderInfos =
        mediaCodecSelector.getDecoderInfos(format.sampleMimeType, requiresSecureDecoder, tunneling);
    return Collections.unmodifiableList(decoderInfos);
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    super.onEnabled(joining);
    int oldTunnelingAudioSessionId = tunnelingAudioSessionId;
    tunnelingAudioSessionId = getConfiguration().tunnelingAudioSessionId;
    tunneling = tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET;
    if (tunnelingAudioSessionId != oldTunnelingAudioSessionId) {
      releaseCodec();
    }
    eventDispatcher.enabled(decoderCounters);
    frameReleaseTimeHelper.enable();
  }

  @Override
  protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
    if (outputStreamOffsetUs == C.TIME_UNSET) {
      outputStreamOffsetUs = offsetUs;
    } else {
      if (pendingOutputStreamOffsetCount == pendingOutputStreamOffsetsUs.length) {
        Log.w(TAG, "Too many stream changes, so dropping offset: "
            + pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1]);
      } else {
        pendingOutputStreamOffsetCount++;
      }
      pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1] = offsetUs;
      pendingOutputStreamSwitchTimesUs[pendingOutputStreamOffsetCount - 1] = lastInputTimeUs;
    }
    super.onStreamChanged(formats, offsetUs);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    clearRenderedFirstFrame();
    initialPositionUs = C.TIME_UNSET;
    consecutiveDroppedFrameCount = 0;
    lastInputTimeUs = C.TIME_UNSET;
    if (pendingOutputStreamOffsetCount != 0) {
      outputStreamOffsetUs = pendingOutputStreamOffsetsUs[pendingOutputStreamOffsetCount - 1];
      pendingOutputStreamOffsetCount = 0;
    }
    if (joining) {
      setJoiningDeadlineMs();
    } else {
      joiningDeadlineMs = C.TIME_UNSET;
    }
  }

  @Override
  public boolean isReady() {
    if (super.isReady() && (renderedFirstFrame || (dummySurface != null && surface == dummySurface)
        || getCodec() == null || tunneling)) {
      // Ready. If we were joining then we've now joined, so clear the joining deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return true;
    } else if (joiningDeadlineMs == C.TIME_UNSET) {
      // Not joining.
      return false;
    } else if (SystemClock.elapsedRealtime() < joiningDeadlineMs) {
      // Joining and still within the joining deadline.
      return true;
    } else {
      // The joining deadline has been exceeded. Give up and clear the deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return false;
    }
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    droppedFrames = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
    lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000;
  }

  @Override
  protected void onStopped() {
    joiningDeadlineMs = C.TIME_UNSET;
    maybeNotifyDroppedFrames();
    super.onStopped();
  }

  @Override
  protected void onDisabled() {
    lastInputTimeUs = C.TIME_UNSET;
    outputStreamOffsetUs = C.TIME_UNSET;
    pendingOutputStreamOffsetCount = 0;
    clearReportedVideoSize();
    clearRenderedFirstFrame();
    frameReleaseTimeHelper.disable();
    tunnelingOnFrameRenderedListener = null;
    try {
      super.onDisabled();
    } finally {
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  protected void onReset() {
    try {
      super.onReset();
    } finally {
      if (dummySurface != null) {
        if (surface == dummySurface) {
          surface = null;
        }
        dummySurface.release();
        dummySurface = null;
      }
    }
  }

  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    if (messageType == C.MSG_SET_SURFACE) {
      setSurface((Surface) message);
    } else if (messageType == C.MSG_SET_SCALING_MODE) {
      scalingMode = (Integer) message;
      MediaCodec codec = getCodec();
      if (codec != null) {
        codec.setVideoScalingMode(scalingMode);
      }
    } else if (messageType == C.MSG_SET_VIDEO_FRAME_METADATA_LISTENER) {
      frameMetadataListener = (VideoFrameMetadataListener) message;
    } else {
      super.handleMessage(messageType, message);
    }
  }

  private void setSurface(Surface surface) throws ExoPlaybackException {
    if (surface == null) {
      // Use a dummy surface if possible.
      if (dummySurface != null) {
        surface = dummySurface;
      } else {
        MediaCodecInfo codecInfo = getCodecInfo();
        if (codecInfo != null && shouldUseDummySurface(codecInfo)) {
          dummySurface = DummySurface.newInstanceV17(context, codecInfo.secure);
          surface = dummySurface;
        }
      }
    }
    // We only need to update the codec if the surface has changed.
    if (this.surface != surface) {
      this.surface = surface;
      @State int state = getState();
      MediaCodec codec = getCodec();
      if (codec != null) {
        if (Util.SDK_INT >= 23 && surface != null && !codecNeedsSetOutputSurfaceWorkaround) {
          setOutputSurfaceV23(codec, surface);
        } else {
          releaseCodec();
          maybeInitCodec();
        }
      }
      if (surface != null && surface != dummySurface) {
        // If we know the video size, report it again immediately.
        maybeRenotifyVideoSizeChanged();
        // We haven't rendered to the new surface yet.
        clearRenderedFirstFrame();
        if (state == STATE_STARTED) {
          setJoiningDeadlineMs();
        }
      } else {
        // The surface has been removed.
        clearReportedVideoSize();
        clearRenderedFirstFrame();
      }
    } else if (surface != null && surface != dummySurface) {
      // The surface is set and unchanged. If we know the video size and/or have already rendered to
      // the surface, report these again immediately.
      maybeRenotifyVideoSizeChanged();
      maybeRenotifyRenderedFirstFrame();
    }
  }

  @Override
  protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
    return surface != null || shouldUseDummySurface(codecInfo);
  }

  @Override
  protected boolean getCodecNeedsEosPropagation() {
    // In tunneling mode we can't dequeue an end-of-stream buffer, so propagate it in the renderer.
    return tunneling;
  }

  @Override
  protected void configureCodec(
      MediaCodecInfo codecInfo,
      MediaCodec codec,
      Format format,
      MediaCrypto crypto,
      float codecOperatingRate) {
    String codecMimeType = codecInfo.codecMimeType;
    codecMaxValues = getCodecMaxValues(codecInfo, format, getStreamFormats());
    MediaFormat mediaFormat =
        getMediaFormat(
            format,
            codecMimeType,
            codecMaxValues,
            codecOperatingRate,
            deviceNeedsNoPostProcessWorkaround,
            tunnelingAudioSessionId);
    if (surface == null) {
      Assertions.checkState(shouldUseDummySurface(codecInfo));
      if (dummySurface == null) {
        dummySurface = DummySurface.newInstanceV17(context, codecInfo.secure);
      }
      surface = dummySurface;
    }

    // AMZN_CHANGE_BEGIN
    log.setTAG(codecName + "-" + TAG);
    log.i("configureCodec: codecName = " + codec +
            ", deviceNeedsNoPostProcessWorkaround = " + deviceNeedsNoPostProcessWorkaround +
            ", format = " + format +
            ", surface = " + surface +
            ", crypto = " + crypto );
    // AMZN_CHANGE_END

    codec.configure(mediaFormat, surface, crypto, 0);
    if (Util.SDK_INT >= 23 && tunneling) {
      tunnelingOnFrameRenderedListener = new OnFrameRenderedListenerV23(codec);
    }
  }

  @Override
  protected @KeepCodecResult int canKeepCodec(
      MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    if (codecInfo.isSeamlessAdaptationSupported(
            oldFormat, newFormat, /* isNewFormatComplete= */ true)
        && newFormat.width <= codecMaxValues.width
        && newFormat.height <= codecMaxValues.height
        && getMaxInputSize(codecInfo, newFormat) <= codecMaxValues.inputSize) {
      return oldFormat.initializationDataEquals(newFormat)
          ? KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION
          : KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION;
    }
    return KEEP_CODEC_RESULT_NO;
  }

  @CallSuper
  @Override
  protected void releaseCodec() {
    try {
      super.releaseCodec();
    } finally {
      buffersInCodecCount = 0;
    }
  }

  @CallSuper
  @Override
  protected boolean flushOrReleaseCodec() {
    try {
      return super.flushOrReleaseCodec();
    } finally {
      buffersInCodecCount = 0;
    }
  }

  @Override
  protected float getCodecOperatingRateV23(
      float operatingRate, Format format, Format[] streamFormats) {
    // Use the highest known stream frame-rate up front, to avoid having to reconfigure the codec
    // should an adaptive switch to that stream occur.
    float maxFrameRate = -1;
    for (Format streamFormat : streamFormats) {
      float streamFrameRate = streamFormat.frameRate;
      if (streamFrameRate != Format.NO_VALUE) {
        maxFrameRate = Math.max(maxFrameRate, streamFrameRate);
      }
    }
    return maxFrameRate == -1 ? CODEC_OPERATING_RATE_UNSET : (maxFrameRate * operatingRate);
  }

  @Override
  protected void onCodecInitialized(String name, long initializedTimestampMs,
      long initializationDurationMs) {
    eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
    codecNeedsSetOutputSurfaceWorkaround = codecNeedsSetOutputSurfaceWorkaround(name);
  }

  @Override
  protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
    super.onInputFormatChanged(newFormat);
    log.i("onInputFormatChanged: format = " + newFormat);
    eventDispatcher.inputFormatChanged(newFormat);
    pendingPixelWidthHeightRatio = newFormat.pixelWidthHeightRatio;
    pendingRotationDegrees = newFormat.rotationDegrees;
  }

  /**
   * Called immediately before an input buffer is queued into the codec.
   *
   * @param buffer The buffer to be queued.
   */
  @CallSuper
  @Override
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    buffersInCodecCount++;
    lastInputTimeUs = Math.max(buffer.timeUs, lastInputTimeUs);
    if (Util.SDK_INT < 23 && tunneling) {
      // In tunneled mode before API 23 we don't have a way to know when the buffer is output, so
      // treat it as if it were output immediately.
      onProcessedTunneledBuffer(buffer.timeUs);
    }
  }

  @Override
  protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) {
    log.i("onOutputFormatChanged: outputFormat:" + outputFormat
            + ", codec:" + codec);
    boolean hasCrop = outputFormat.containsKey(KEY_CROP_RIGHT)
        && outputFormat.containsKey(KEY_CROP_LEFT) && outputFormat.containsKey(KEY_CROP_BOTTOM)
        && outputFormat.containsKey(KEY_CROP_TOP);
    int width =
        hasCrop
            ? outputFormat.getInteger(KEY_CROP_RIGHT) - outputFormat.getInteger(KEY_CROP_LEFT) + 1
            : outputFormat.getInteger(MediaFormat.KEY_WIDTH);
    int height =
        hasCrop
            ? outputFormat.getInteger(KEY_CROP_BOTTOM) - outputFormat.getInteger(KEY_CROP_TOP) + 1
            : outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
    processOutputFormat(codec, width, height);
  }

  @Override
  protected boolean processOutputBuffer(
      long positionUs,
      long elapsedRealtimeUs,
      MediaCodec codec,
      ByteBuffer buffer,
      int bufferIndex,
      int bufferFlags,
      long bufferPresentationTimeUs,
      boolean isDecodeOnlyBuffer,
      boolean isLastBuffer,
      Format format)
      throws ExoPlaybackException {
    if (initialPositionUs == C.TIME_UNSET) {
      initialPositionUs = positionUs;
    }

    long presentationTimeUs = bufferPresentationTimeUs - outputStreamOffsetUs;

    if (log.allowDebug()) {
      log.d("processOutputBuffer: positionUs = " + positionUs +
          ", elapsedRealtimeUs = " + elapsedRealtimeUs +
          ", bufferIndex = " + bufferIndex +
          ", isDecodeOnlyBuffer = " + isDecodeOnlyBuffer +
          ", isLastBuffer = " + isLastBuffer +
          ", presentationTimeUs = " + bufferPresentationTimeUs);
    }

    if (isDecodeOnlyBuffer && !isLastBuffer) {
      skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
      return true;
    }

    long earlyUs = bufferPresentationTimeUs - positionUs;
    if (surface == dummySurface) {
      // Skip frames in sync with playback, so we'll be at the right frame if the mode changes.
      if (isBufferLate(earlyUs)) {
        skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
        return true;
      }
      return false;
    }

    long elapsedRealtimeNowUs = SystemClock.elapsedRealtime() * 1000;
    boolean isStarted = getState() == STATE_STARTED;
    if (!renderedFirstFrame
        || (isStarted
            && shouldForceRenderOutputBuffer(earlyUs, elapsedRealtimeNowUs - lastRenderTimeUs))) {
      long releaseTimeNs = System.nanoTime();
      notifyFrameMetadataListener(presentationTimeUs, releaseTimeNs, format);
      if (Util.SDK_INT >= 21) {
        renderOutputBufferV21(codec, bufferIndex, presentationTimeUs, releaseTimeNs);
      } else {
        renderOutputBuffer(codec, bufferIndex, presentationTimeUs);
      }
      return true;
    }

    if (!isStarted || positionUs == initialPositionUs) {
      return false;
    }

    // Fine-grained adjustment of earlyUs based on the elapsed time since the start of the current
    // iteration of the rendering loop.
    long elapsedSinceStartOfLoopUs = elapsedRealtimeNowUs - elapsedRealtimeUs;
    earlyUs -= elapsedSinceStartOfLoopUs;

    // Compute the buffer's desired release time in nanoseconds.
    long systemTimeNs = System.nanoTime();
    long unadjustedFrameReleaseTimeNs = systemTimeNs + (earlyUs * 1000);

    // Apply a timestamp adjustment, if there is one.
    long adjustedReleaseTimeNs = frameReleaseTimeHelper.adjustReleaseTime(
        bufferPresentationTimeUs, unadjustedFrameReleaseTimeNs);
    earlyUs = (adjustedReleaseTimeNs - systemTimeNs) / 1000;

    if (shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs, isLastBuffer)
        && maybeDropBuffersToKeyframe(codec, bufferIndex, presentationTimeUs, positionUs)) {
      return false;
    } else if (shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs, isLastBuffer)) {
      dropOutputBuffer(codec, bufferIndex, presentationTimeUs);
      return true;
    }

    if (Util.SDK_INT >= 21) {
      // Let the underlying framework time the release.
      if (earlyUs < 50000) {
        notifyFrameMetadataListener(presentationTimeUs, adjustedReleaseTimeNs, format);
        renderOutputBufferV21(codec, bufferIndex, presentationTimeUs, adjustedReleaseTimeNs);
        return true;
      }
    } else {
      // We need to time the release ourselves.
      if (earlyUs < 30000) {
        if (earlyUs > 11000) {
          // We're a little too early to render the frame. Sleep until the frame can be rendered.
          // Note: The 11ms threshold was chosen fairly arbitrarily.
          try {
            // Subtracting 10000 rather than 11000 ensures the sleep time will be at least 1ms.
            Thread.sleep((earlyUs - 10000) / 1000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
          }
        }
        notifyFrameMetadataListener(presentationTimeUs, adjustedReleaseTimeNs, format);
        renderOutputBuffer(codec, bufferIndex, presentationTimeUs);
        return true;
      }
    }

    // We're either not playing, or it's not time to render the frame yet.
    return false;
  }

  private void processOutputFormat(MediaCodec codec, int width, int height) {
    currentWidth = width;
    currentHeight = height;
    currentPixelWidthHeightRatio = pendingPixelWidthHeightRatio;
    if (Util.SDK_INT >= 21) {
      // On API level 21 and above the decoder applies the rotation when rendering to the surface.
      // Hence currentUnappliedRotation should always be 0. For 90 and 270 degree rotations, we need
      // to flip the width, height and pixel aspect ratio to reflect the rotation that was applied.
      if (pendingRotationDegrees == 90 || pendingRotationDegrees == 270) {
        int rotatedHeight = currentWidth;
        currentWidth = currentHeight;
        currentHeight = rotatedHeight;
        currentPixelWidthHeightRatio = 1 / currentPixelWidthHeightRatio;
      }
    } else {
      // On API level 20 and below the decoder does not apply the rotation.
      currentUnappliedRotationDegrees = pendingRotationDegrees;
    }
    // Must be applied each time the output format changes.
    codec.setVideoScalingMode(scalingMode);
  }

  private void notifyFrameMetadataListener(
      long presentationTimeUs, long releaseTimeNs, Format format) {
    if (frameMetadataListener != null) {
      frameMetadataListener.onVideoFrameAboutToBeRendered(
          presentationTimeUs, releaseTimeNs, format);
    }
  }

  /**
   * Returns the offset that should be subtracted from {@code bufferPresentationTimeUs} in {@link
   * #processOutputBuffer(long, long, MediaCodec, ByteBuffer, int, int, long, boolean, boolean,
   * Format)} to get the playback position with respect to the media.
   */
  protected long getOutputStreamOffsetUs() {
    return outputStreamOffsetUs;
  }

  /** Called when a buffer was processed in tunneling mode. */
  protected void onProcessedTunneledBuffer(long presentationTimeUs) {
    @Nullable Format format = updateOutputFormatForTime(presentationTimeUs);
    if (format != null) {
      processOutputFormat(getCodec(), format.width, format.height);
    }
    maybeNotifyVideoSizeChanged();
    maybeNotifyRenderedFirstFrame();
    onProcessedOutputBuffer(presentationTimeUs);
  }

  /**
   * Called when an output buffer is successfully processed.
   *
   * @param presentationTimeUs The timestamp associated with the output buffer.
   */
  @CallSuper
  @Override
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    buffersInCodecCount--;
    while (pendingOutputStreamOffsetCount != 0
        && presentationTimeUs >= pendingOutputStreamSwitchTimesUs[0]) {
      outputStreamOffsetUs = pendingOutputStreamOffsetsUs[0];
      pendingOutputStreamOffsetCount--;
      System.arraycopy(
          pendingOutputStreamOffsetsUs,
          /* srcPos= */ 1,
          pendingOutputStreamOffsetsUs,
          /* destPos= */ 0,
          pendingOutputStreamOffsetCount);
      System.arraycopy(
          pendingOutputStreamSwitchTimesUs,
          /* srcPos= */ 1,
          pendingOutputStreamSwitchTimesUs,
          /* destPos= */ 0,
          pendingOutputStreamOffsetCount);
    }
  }

  /**
   * Returns whether the buffer being processed should be dropped.
   *
   * @param earlyUs The time until the buffer should be presented in microseconds. A negative value
   *     indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @param isLastBuffer Whether the buffer is the last buffer in the current stream.
   */
  protected boolean shouldDropOutputBuffer(
      long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
    return isBufferLate(earlyUs) && !isLastBuffer;
  }

  /**
   * Returns whether to drop all buffers from the buffer being processed to the keyframe at or after
   * the current playback position, if possible.
   *
   * @param earlyUs The time until the current buffer should be presented in microseconds. A
   *     negative value indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @param isLastBuffer Whether the buffer is the last buffer in the current stream.
   */
  protected boolean shouldDropBuffersToKeyframe(
      long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
    return isBufferVeryLate(earlyUs) && !isLastBuffer;
  }

  /**
   * Returns whether to force rendering an output buffer.
   *
   * @param earlyUs The time until the current buffer should be presented in microseconds. A
   *     negative value indicates that the buffer is late.
   * @param elapsedSinceLastRenderUs The elapsed time since the last output buffer was rendered, in
   *     microseconds.
   * @return Returns whether to force rendering an output buffer.
   */
  protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
    return isBufferLate(earlyUs) && elapsedSinceLastRenderUs > 100000;
  }

  /**
   * Skips the output buffer with the specified index.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to skip.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   */
  protected void skipOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
    log.i("skipOutputBuffer: bufferIndex = " + index + ", PTS = " + presentationTimeUs);
    TraceUtil.beginSection("skipVideoBuffer");
    codec.releaseOutputBuffer(index, false);
    TraceUtil.endSection();
    decoderCounters.skippedOutputBufferCount++;
  }

  /**
   * Drops the output buffer with the specified index.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to drop.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   */
  protected void dropOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
    log.i("dropOutputBuffer: bufferIndex = " + index + ", PTS = " + presentationTimeUs);
    TraceUtil.beginSection("dropVideoBuffer");
    codec.releaseOutputBuffer(index, false);
    TraceUtil.endSection();
    updateDroppedBufferCounters(1);
  }

  /**
   * Drops frames from the current output buffer to the next keyframe at or before the playback
   * position. If no such keyframe exists, as the playback position is inside the same group of
   * pictures as the buffer being processed, returns {@code false}. Returns {@code true} otherwise.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to drop.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   * @param positionUs The current playback position, in microseconds.
   * @return Whether any buffers were dropped.
   * @throws ExoPlaybackException If an error occurs flushing the codec.
   */
  protected boolean maybeDropBuffersToKeyframe(MediaCodec codec, int index, long presentationTimeUs,
      long positionUs) throws ExoPlaybackException {
    int droppedSourceBufferCount = skipSource(positionUs);
    if (droppedSourceBufferCount == 0) {
      return false;
    }
    decoderCounters.droppedToKeyframeCount++;
    // We dropped some buffers to catch up, so update the decoder counters and flush the codec,
    // which releases all pending buffers buffers including the current output buffer.
    updateDroppedBufferCounters(buffersInCodecCount + droppedSourceBufferCount);
    flushOrReinitializeCodec();
    return true;
  }

  /**
   * Updates decoder counters to reflect that {@code droppedBufferCount} additional buffers were
   * dropped.
   *
   * @param droppedBufferCount The number of additional dropped buffers.
   */
  protected void updateDroppedBufferCounters(int droppedBufferCount) {
    decoderCounters.droppedBufferCount += droppedBufferCount;
    droppedFrames += droppedBufferCount;
    consecutiveDroppedFrameCount += droppedBufferCount;
    decoderCounters.maxConsecutiveDroppedBufferCount = Math.max(consecutiveDroppedFrameCount,
        decoderCounters.maxConsecutiveDroppedBufferCount);
    if (maxDroppedFramesToNotify > 0 && droppedFrames >= maxDroppedFramesToNotify) {
      maybeNotifyDroppedFrames();
    }
  }

  /**
   * Renders the output buffer with the specified index. This method is only called if the platform
   * API version of the device is less than 21.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to drop.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   */
  protected void renderOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
    if (log.allowDebug()) {
      log.d("renderOutputBuffer: " + index + ", PTS = " + presentationTimeUs);
    }
    maybeNotifyVideoSizeChanged();
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(index, true);
    TraceUtil.endSection();
    lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000;
    decoderCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    maybeNotifyRenderedFirstFrame();
  }

  /**
   * Renders the output buffer with the specified index. This method is only called if the platform
   * API version of the device is 21 or later.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to drop.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   * @param releaseTimeNs The wallclock time at which the frame should be displayed, in nanoseconds.
   */
  @TargetApi(21)
  protected void renderOutputBufferV21(
      MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
    if (log.allowDebug()) {
      log.d("renderOutputBufferV21: bufferIndex = " + index + ", PTS = " + presentationTimeUs +
              ", releaseTimeNs = " + releaseTimeNs);
    }
    maybeNotifyVideoSizeChanged();
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(index, releaseTimeNs);
    TraceUtil.endSection();
    lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000;
    decoderCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    maybeNotifyRenderedFirstFrame();
  }

  private boolean shouldUseDummySurface(MediaCodecInfo codecInfo) {
    return Util.SDK_INT >= 23
        && !tunneling
        && !codecNeedsSetOutputSurfaceWorkaround(codecInfo.name)
        && (!codecInfo.secure || DummySurface.isSecureSupported(context));
  }

  private void setJoiningDeadlineMs() {
    joiningDeadlineMs = allowedJoiningTimeMs > 0
        ? (SystemClock.elapsedRealtime() + allowedJoiningTimeMs) : C.TIME_UNSET;
  }

  private void clearRenderedFirstFrame() {
    renderedFirstFrame = false;
    // The first frame notification is triggered by renderOutputBuffer or renderOutputBufferV21 for
    // non-tunneled playback, onQueueInputBuffer for tunneled playback prior to API level 23, and
    // OnFrameRenderedListenerV23.onFrameRenderedListener for tunneled playback on API level 23 and
    // above.
    if (Util.SDK_INT >= 23 && tunneling) {
      MediaCodec codec = getCodec();
      // If codec is null then the listener will be instantiated in configureCodec.
      if (codec != null) {
        tunnelingOnFrameRenderedListener = new OnFrameRenderedListenerV23(codec);
      }
    }
  }

  /* package */ void maybeNotifyRenderedFirstFrame() {
    if (!renderedFirstFrame) {
      renderedFirstFrame = true;
      eventDispatcher.renderedFirstFrame(surface);
    }
  }

  private void maybeRenotifyRenderedFirstFrame() {
    if (renderedFirstFrame) {
      eventDispatcher.renderedFirstFrame(surface);
    }
  }

  private void clearReportedVideoSize() {
    reportedWidth = Format.NO_VALUE;
    reportedHeight = Format.NO_VALUE;
    reportedPixelWidthHeightRatio = Format.NO_VALUE;
    reportedUnappliedRotationDegrees = Format.NO_VALUE;
  }

  private void maybeNotifyVideoSizeChanged() {
    if ((currentWidth != Format.NO_VALUE || currentHeight != Format.NO_VALUE)
      && (reportedWidth != currentWidth || reportedHeight != currentHeight
        || reportedUnappliedRotationDegrees != currentUnappliedRotationDegrees
        || reportedPixelWidthHeightRatio != currentPixelWidthHeightRatio)) {
      eventDispatcher.videoSizeChanged(currentWidth, currentHeight, currentUnappliedRotationDegrees,
          currentPixelWidthHeightRatio);
      reportedWidth = currentWidth;
      reportedHeight = currentHeight;
      reportedUnappliedRotationDegrees = currentUnappliedRotationDegrees;
      reportedPixelWidthHeightRatio = currentPixelWidthHeightRatio;
    }
  }

  private void maybeRenotifyVideoSizeChanged() {
    if (reportedWidth != Format.NO_VALUE || reportedHeight != Format.NO_VALUE) {
      eventDispatcher.videoSizeChanged(reportedWidth, reportedHeight,
          reportedUnappliedRotationDegrees, reportedPixelWidthHeightRatio);
    }
  }

  private void maybeNotifyDroppedFrames() {
    if (droppedFrames > 0) {
      long now = SystemClock.elapsedRealtime();
      long elapsedMs = now - droppedFrameAccumulationStartTimeMs;
      eventDispatcher.droppedFrames(droppedFrames, elapsedMs);
      droppedFrames = 0;
      droppedFrameAccumulationStartTimeMs = now;
    }
  }

  /**
   * MOD: Make function overridable
   */
  protected boolean isBufferLate(long earlyUs) {
    // Class a buffer as late if it should have been presented more than 30 ms ago.
    return earlyUs < -30000;
  }

  /**
   * MOD: Make function overridable
   */
  protected boolean isBufferVeryLate(long earlyUs) {
    // Class a buffer as very late if it should have been presented more than 500 ms ago.
    return earlyUs < -500000;
  }

  @TargetApi(23)
  private static void setOutputSurfaceV23(MediaCodec codec, Surface surface) {
    codec.setOutputSurface(surface);
  }

  @TargetApi(21)
  private static void configureTunnelingV21(MediaFormat mediaFormat, int tunnelingAudioSessionId) {
    mediaFormat.setFeatureEnabled(CodecCapabilities.FEATURE_TunneledPlayback, true);
    mediaFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, tunnelingAudioSessionId);
  }

  /**
   * Returns the framework {@link MediaFormat} that should be used to configure the decoder.
   *
   * @param format The format of media.
   * @param codecMimeType The MIME type handled by the codec.
   * @param codecMaxValues Codec max values that should be used when configuring the decoder.
   * @param codecOperatingRate The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if
   *     no codec operating rate should be set.
   * @param deviceNeedsNoPostProcessWorkaround Whether the device is known to do post processing by
   *     default that isn't compatible with ExoPlayer.
   * @param tunnelingAudioSessionId The audio session id to use for tunneling, or {@link
   *     C#AUDIO_SESSION_ID_UNSET} if tunneling should not be enabled.
   * @return The framework {@link MediaFormat} that should be used to configure the decoder.
   */
  @SuppressLint("InlinedApi")
  protected MediaFormat getMediaFormat(
      Format format,
      String codecMimeType,
      CodecMaxValues codecMaxValues,
      float codecOperatingRate,
      boolean deviceNeedsNoPostProcessWorkaround,
      int tunnelingAudioSessionId) {
    MediaFormat mediaFormat = new MediaFormat();
    // Set format parameters that should always be set.
    mediaFormat.setString(MediaFormat.KEY_MIME, codecMimeType);
    mediaFormat.setInteger(MediaFormat.KEY_WIDTH, format.width);
    mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, format.height);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    // Set format parameters that may be unset.
    MediaFormatUtil.maybeSetFloat(mediaFormat, MediaFormat.KEY_FRAME_RATE, format.frameRate);
    MediaFormatUtil.maybeSetInteger(mediaFormat, MediaFormat.KEY_ROTATION, format.rotationDegrees);
    MediaFormatUtil.maybeSetColorInfo(mediaFormat, format.colorInfo);
    if (MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) {
      // Some phones require the profile to be set on the codec.
      // See https://github.com/google/ExoPlayer/pull/5438.
      Pair<Integer, Integer> codecProfileAndLevel =
          MediaCodecUtil.getCodecProfileAndLevel(format.codecs);
      if (codecProfileAndLevel != null) {
        MediaFormatUtil.maybeSetInteger(
            mediaFormat, MediaFormat.KEY_PROFILE, codecProfileAndLevel.first);
      }
    }
    // Set codec max values.
    mediaFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, codecMaxValues.width);
    mediaFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, codecMaxValues.height);
    MediaFormatUtil.maybeSetInteger(
        mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, codecMaxValues.inputSize);
    // Set codec configuration values.
    if (Util.SDK_INT >= 23) {
      mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0 /* realtime priority */);
      if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET) {
        mediaFormat.setFloat(MediaFormat.KEY_OPERATING_RATE, codecOperatingRate);
      }
    }
    if (deviceNeedsNoPostProcessWorkaround) {
      mediaFormat.setInteger("no-post-process", 1);
      mediaFormat.setInteger("auto-frc", 0);
    }
    if (tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
      configureTunnelingV21(mediaFormat, tunnelingAudioSessionId);
    }
    return mediaFormat;
  }

  /**
   * Returns {@link CodecMaxValues} suitable for configuring a codec for {@code format} in a way
   * that will allow possible adaptation to other compatible formats in {@code streamFormats}.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The format for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return Suitable {@link CodecMaxValues}.
   */
  protected CodecMaxValues getCodecMaxValues(
      MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
    int maxWidth = format.width;
    int maxHeight = format.height;
    int maxInputSize = getMaxInputSize(codecInfo, format);
    if (streamFormats.length == 1) {
      // The single entry in streamFormats must correspond to the format for which the codec is
      // being configured.
      if (maxInputSize != Format.NO_VALUE) {
        int codecMaxInputSize =
            getCodecMaxInputSize(codecInfo, format.sampleMimeType, format.width, format.height);
        if (codecMaxInputSize != Format.NO_VALUE) {
          // Scale up the initial video decoder maximum input size so playlist item transitions with
          // small increases in maximum sample size don't require reinitialization. This only makes
          // a difference if the exact maximum sample sizes are known from the container.
          int scaledMaxInputSize =
              (int) (maxInputSize * INITIAL_FORMAT_MAX_INPUT_SIZE_SCALE_FACTOR);
          // Avoid exceeding the maximum expected for the codec.
          maxInputSize = Math.min(scaledMaxInputSize, codecMaxInputSize);
        }
      }
      return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
    }
    boolean haveUnknownDimensions = false;
    for (Format streamFormat : streamFormats) {
      if (codecInfo.isSeamlessAdaptationSupported(
          format, streamFormat, /* isNewFormatComplete= */ false)) {
        haveUnknownDimensions |=
            (streamFormat.width == Format.NO_VALUE || streamFormat.height == Format.NO_VALUE);
        maxWidth = Math.max(maxWidth, streamFormat.width);
        maxHeight = Math.max(maxHeight, streamFormat.height);
        maxInputSize = Math.max(maxInputSize, getMaxInputSize(codecInfo, streamFormat));
      }
    }
    if (haveUnknownDimensions) {
      Log.w(TAG, "Resolutions unknown. Codec max resolution: " + maxWidth + "x" + maxHeight);
      Point codecMaxSize = getCodecMaxSize(codecInfo, format);
      if (codecMaxSize != null) {
        maxWidth = Math.max(maxWidth, codecMaxSize.x);
        maxHeight = Math.max(maxHeight, codecMaxSize.y);
        maxInputSize =
            Math.max(
                maxInputSize,
                getCodecMaxInputSize(codecInfo, format.sampleMimeType, maxWidth, maxHeight));
        Log.w(TAG, "Codec max resolution adjusted to: " + maxWidth + "x" + maxHeight);
      }
    }
    return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
  }

  /**
   * Returns a maximum video size to use when configuring a codec for {@code format} in a way that
   * will allow possible adaptation to other compatible formats that are expected to have the same
   * aspect ratio, but whose sizes are unknown.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The format for which the codec is being configured.
   * @return The maximum video size to use, or null if the size of {@code format} should be used.
   */
  private static Point getCodecMaxSize(MediaCodecInfo codecInfo, Format format) {
    boolean isVerticalVideo = format.height > format.width;
    int formatLongEdgePx = isVerticalVideo ? format.height : format.width;
    int formatShortEdgePx = isVerticalVideo ? format.width : format.height;
    float aspectRatio = (float) formatShortEdgePx / formatLongEdgePx;
    for (int longEdgePx : STANDARD_LONG_EDGE_VIDEO_PX) {
      int shortEdgePx = (int) (longEdgePx * aspectRatio);
      if (longEdgePx <= formatLongEdgePx || shortEdgePx <= formatShortEdgePx) {
        // Don't return a size not larger than the format for which the codec is being configured.
        return null;
      } else if (Util.SDK_INT >= 21) {
        Point alignedSize = codecInfo.alignVideoSizeV21(isVerticalVideo ? shortEdgePx : longEdgePx,
            isVerticalVideo ? longEdgePx : shortEdgePx);
        float frameRate = format.frameRate;
        if (codecInfo.isVideoSizeAndRateSupportedV21(alignedSize.x, alignedSize.y, frameRate)) {
          return alignedSize;
        }
      } else {
        try {
          // Conservatively assume the codec requires 16px width and height alignment.
          longEdgePx = Util.ceilDivide(longEdgePx, 16) * 16;
          shortEdgePx = Util.ceilDivide(shortEdgePx, 16) * 16;
          if (longEdgePx * shortEdgePx <= MediaCodecUtil.maxH264DecodableFrameSize()) {
            return new Point(
                isVerticalVideo ? shortEdgePx : longEdgePx,
                isVerticalVideo ? longEdgePx : shortEdgePx);
          }
        } catch (DecoderQueryException e) {
          // We tried our best. Give up!
          return null;
        }
      }
    }
    return null;
  }

  /**
   * Returns a maximum input buffer size for a given codec and format.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The format.
   * @return A maximum input buffer size in bytes, or {@link Format#NO_VALUE} if a maximum could not
   *     be determined.
   */
  private static int getMaxInputSize(MediaCodecInfo codecInfo, Format format) {
    if (format.maxInputSize != Format.NO_VALUE) {
      // The format defines an explicit maximum input size. Add the total size of initialization
      // data buffers, as they may need to be queued in the same input buffer as the largest sample.
      int totalInitializationDataSize = 0;
      int initializationDataCount = format.initializationData.size();
      for (int i = 0; i < initializationDataCount; i++) {
        totalInitializationDataSize += format.initializationData.get(i).length;
      }
      return format.maxInputSize + totalInitializationDataSize;
    } else {
      // Calculated maximum input sizes are overestimates, so it's not necessary to add the size of
      // initialization data.
      return getCodecMaxInputSize(codecInfo, format.sampleMimeType, format.width, format.height);
    }
  }

  /**
   * Returns a maximum input size for a given codec, MIME type, width and height.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param sampleMimeType The format mime type.
   * @param width The width in pixels.
   * @param height The height in pixels.
   * @return A maximum input size in bytes, or {@link Format#NO_VALUE} if a maximum could not be
   *     determined.
   */
  private static int getCodecMaxInputSize(
      MediaCodecInfo codecInfo, String sampleMimeType, int width, int height) {
    if (width == Format.NO_VALUE || height == Format.NO_VALUE) {
      // We can't infer a maximum input size without video dimensions.
      return Format.NO_VALUE;
    }

    // Attempt to infer a maximum input size from the format.
    int maxPixels;
    int minCompressionRatio;
    switch (sampleMimeType) {
      case MimeTypes.VIDEO_H263:
      case MimeTypes.VIDEO_MP4V:
        maxPixels = width * height;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_H264:
        if ("BRAVIA 4K 2015".equals(Util.MODEL) // Sony Bravia 4K
            || ("Amazon".equals(Util.MANUFACTURER)
                && ("KFSOWI".equals(Util.MODEL) // Kindle Soho
                    || ("AFTS".equals(Util.MODEL) && codecInfo.secure)))) { // Fire TV Gen 2
          // Use the default value for cases where platform limitations may prevent buffers of the
          // calculated maximum input size from being allocated.
          return Format.NO_VALUE;
        }
        // Round up width/height to an integer number of macroblocks.
        maxPixels = Util.ceilDivide(width, 16) * Util.ceilDivide(height, 16) * 16 * 16;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_VP8:
        // VPX does not specify a ratio so use the values from the platform's SoftVPX.cpp.
        maxPixels = width * height;
        minCompressionRatio = 2;
        break;
      case MimeTypes.VIDEO_H265:
      case MimeTypes.VIDEO_VP9:
        maxPixels = width * height;
        minCompressionRatio = 4;
        break;
      default:
        // Leave the default max input size.
        return Format.NO_VALUE;
    }
    // Estimate the maximum input size assuming three channel 4:2:0 subsampled input frames.
    return (maxPixels * 3) / (2 * minCompressionRatio);
  }

  /**
   * Returns whether the device is known to do post processing by default that isn't compatible with
   * ExoPlayer.
   *
   * @return Whether the device is known to do post processing by default that isn't compatible with
   *     ExoPlayer.
   */
  private static boolean deviceNeedsNoPostProcessWorkaround() {
    // Nvidia devices prior to M try to adjust the playback rate to better map the frame-rate of
    // content to the refresh rate of the display. For example playback of 23.976fps content is
    // adjusted to play at 1.001x speed when the output display is 60Hz. Unfortunately the
    // implementation causes ExoPlayer's reported playback position to drift out of sync. Captions
    // also lose sync [Internal: b/26453592]. Even after M, the devices may apply post processing
    // operations that can modify frame output timestamps, which is incompatible with ExoPlayer's
    // logic for skipping decode-only frames.
    return "NVIDIA".equals(Util.MANUFACTURER);
  }

  /*
   * TODO:
   *
   * 1. Validate that Android device certification now ensures correct behavior, and add a
   *    corresponding SDK_INT upper bound for applying the workaround (probably SDK_INT < 26).
   * 2. Determine a complete list of affected devices.
   * 3. Some of the devices in this list only fail to support setOutputSurface when switching from
   *    a SurfaceView provided Surface to a Surface of another type (e.g. TextureView/DummySurface),
   *    and vice versa. One hypothesis is that setOutputSurface fails when the surfaces have
   *    different pixel formats. If we can find a way to query the Surface instances to determine
   *    whether this case applies, then we'll be able to provide a more targeted workaround.
   */
  /**
   * Returns whether the codec is known to implement {@link MediaCodec#setOutputSurface(Surface)}
   * incorrectly.
   *
   * <p>If true is returned then we fall back to releasing and re-instantiating the codec instead.
   *
   * @param name The name of the codec.
   * @return True if the device is known to implement {@link MediaCodec#setOutputSurface(Surface)}
   *     incorrectly.
   */
  protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
    if (name.startsWith("OMX.google")) {
      // Google OMX decoders are not known to have this issue on any API level.
      return false;
    }
    synchronized (MediaCodecVideoRenderer.class) {
      if (!evaluatedDeviceNeedsSetOutputSurfaceWorkaround) {
        if (Util.SDK_INT <= 27 && ("dangal".equals(Util.DEVICE) || "HWEML".equals(Util.DEVICE))) {
          // A small number of devices are affected on API level 27:
          // https://github.com/google/ExoPlayer/issues/5169.
          deviceNeedsSetOutputSurfaceWorkaround = true;
        } else if (Util.SDK_INT >= 27) {
          // In general, devices running API level 27 or later should be unaffected. Do nothing.
        } else {
          // Enable the workaround on a per-device basis. Works around:
          // https://github.com/google/ExoPlayer/issues/3236,
          // https://github.com/google/ExoPlayer/issues/3355,
          // https://github.com/google/ExoPlayer/issues/3439,
          // https://github.com/google/ExoPlayer/issues/3724,
          // https://github.com/google/ExoPlayer/issues/3835,
          // https://github.com/google/ExoPlayer/issues/4006,
          // https://github.com/google/ExoPlayer/issues/4084,
          // https://github.com/google/ExoPlayer/issues/4104,
          // https://github.com/google/ExoPlayer/issues/4134,
          // https://github.com/google/ExoPlayer/issues/4315,
          // https://github.com/google/ExoPlayer/issues/4419,
          // https://github.com/google/ExoPlayer/issues/4460,
          // https://github.com/google/ExoPlayer/issues/4468,
          // https://github.com/google/ExoPlayer/issues/5312.
          switch (Util.DEVICE) {
            case "1601":
            case "1713":
            case "1714":
            case "A10-70F":
            case "A10-70L":
            case "A1601":
            case "A2016a40":
            case "A7000-a":
            case "A7000plus":
            case "A7010a48":
            case "A7020a48":
            case "AquaPowerM":
            case "ASUS_X00AD_2":
            case "Aura_Note_2":
            case "BLACK-1X":
            case "BRAVIA_ATV2":
            case "BRAVIA_ATV3_4K":
            case "C1":
            case "ComioS1":
            case "CP8676_I02":
            case "CPH1609":
            case "CPY83_I00":
            case "cv1":
            case "cv3":
            case "deb":
            case "E5643":
            case "ELUGA_A3_Pro":
            case "ELUGA_Note":
            case "ELUGA_Prim":
            case "ELUGA_Ray_X":
            case "EverStar_S":
            case "F3111":
            case "F3113":
            case "F3116":
            case "F3211":
            case "F3213":
            case "F3215":
            case "F3311":
            case "flo":
            case "fugu":
            case "GiONEE_CBL7513":
            case "GiONEE_GBL7319":
            case "GIONEE_GBL7360":
            case "GIONEE_SWW1609":
            case "GIONEE_SWW1627":
            case "GIONEE_SWW1631":
            case "GIONEE_WBL5708":
            case "GIONEE_WBL7365":
            case "GIONEE_WBL7519":
            case "griffin":
            case "htc_e56ml_dtul":
            case "hwALE-H":
            case "HWBLN-H":
            case "HWCAM-H":
            case "HWVNS-H":
            case "HWWAS-H":
            case "i9031":
            case "iball8735_9806":
            case "Infinix-X572":
            case "iris60":
            case "itel_S41":
            case "j2xlteins":
            case "JGZ":
            case "K50a40":
            case "kate":
            case "le_x6":
            case "LS-5017":
            case "M5c":
            case "manning":
            case "marino_f":
            case "MEIZU_M5":
            case "mh":
            case "mido":
            case "MX6":
            case "namath":
            case "nicklaus_f":
            case "NX541J":
            case "NX573J":
            case "OnePlus5T":
            case "p212":
            case "P681":
            case "P85":
            case "panell_d":
            case "panell_dl":
            case "panell_ds":
            case "panell_dt":
            case "PB2-670M":
            case "PGN528":
            case "PGN610":
            case "PGN611":
            case "Phantom6":
            case "Pixi4-7_3G":
            case "Pixi5-10_4G":
            case "PLE":
            case "PRO7S":
            case "Q350":
            case "Q4260":
            case "Q427":
            case "Q4310":
            case "Q5":
            case "QM16XE_U":
            case "QX1":
            case "santoni":
            case "Slate_Pro":
            case "SVP-DTV15":
            case "s905x018":
            case "taido_row":
            case "TB3-730F":
            case "TB3-730X":
            case "TB3-850F":
            case "TB3-850M":
            case "tcl_eu":
            case "V1":
            case "V23GB":
            case "V5":
            case "vernee_M5":
            case "watson":
            case "whyred":
            case "woods_f":
            case "woods_fn":
            case "X3_HK":
            case "XE2X":
            case "XT1663":
            case "Z12_PRO":
            case "Z80":
              deviceNeedsSetOutputSurfaceWorkaround = true;
              break;
            default:
              // Do nothing.
              break;
          }
          switch (Util.MODEL) {
            case "AFTA":
            case "AFTN":
              deviceNeedsSetOutputSurfaceWorkaround = true;
              break;
            default:
              // Do nothing.
              break;
          }
        }
        evaluatedDeviceNeedsSetOutputSurfaceWorkaround = true;
      }
    }
    return deviceNeedsSetOutputSurfaceWorkaround;
  }

  protected static final class CodecMaxValues {

    public final int width;
    public final int height;
    public final int inputSize;

    public CodecMaxValues(int width, int height, int inputSize) {
      this.width = width;
      this.height = height;
      this.inputSize = inputSize;
    }

  }

  @TargetApi(23)
  private final class OnFrameRenderedListenerV23 implements MediaCodec.OnFrameRenderedListener {

    private OnFrameRenderedListenerV23(MediaCodec codec) {
      codec.setOnFrameRenderedListener(this, new Handler());
    }

    @Override
    public void onFrameRendered(@NonNull MediaCodec codec, long presentationTimeUs, long nanoTime) {
      if (this != tunnelingOnFrameRenderedListener) {
        // Stale event.
        return;
      }
      onProcessedTunneledBuffer(presentationTimeUs);
    }

  }

}
