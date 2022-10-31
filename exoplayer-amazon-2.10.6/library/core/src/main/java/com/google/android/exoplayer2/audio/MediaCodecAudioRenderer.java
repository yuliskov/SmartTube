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
package com.google.android.exoplayer2.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlayerMessage.Target;
import com.google.android.exoplayer2.audio.AudioRendererEventListener.EventDispatcher;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.mediacodec.MediaFormatUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.AmazonQuirks;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Logger;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decodes and renders audio using {@link MediaCodec} and an {@link AudioSink}.
 *
 * <p>This renderer accepts the following messages sent via {@link ExoPlayer#createMessage(Target)}
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link C#MSG_SET_VOLUME} to set the volume. The message payload should be
 *       a {@link Float} with 0 being silence and 1 being unity gain.
 *   <li>Message with type {@link C#MSG_SET_AUDIO_ATTRIBUTES} to set the audio attributes. The
 *       message payload should be an {@link com.google.android.exoplayer2.audio.AudioAttributes}
 *       instance that will configure the underlying audio track.
 *   <li>Message with type {@link C#MSG_SET_AUX_EFFECT_INFO} to set the auxiliary effect. The
 *       message payload should be an {@link AuxEffectInfo} instance that will configure the
 *       underlying audio track.
 * </ul>
 */
public class MediaCodecAudioRenderer extends MediaCodecRenderer implements MediaClock {

  /**
   * Maximum number of tracked pending stream change times. Generally there is zero or one pending
   * stream change. We track more to allow for pending changes that have fewer samples than the
   * codec latency.
   */
  private static final int MAX_PENDING_STREAM_CHANGE_COUNT = 10;

  private static final String TAG = "MediaCodecAudioRenderer";

  private final Context context;
  private final EventDispatcher eventDispatcher;
  private final AudioSink audioSink;
  private final long[] pendingStreamChangeTimesUs;

  private int codecMaxInputSize;
  private boolean passthroughEnabled;
  private boolean codecNeedsDiscardChannelsWorkaround;
  private boolean codecNeedsEosBufferTimestampWorkaround;
  private android.media.MediaFormat passthroughMediaFormat;
  private @C.Encoding int pcmEncoding;
  private int channelCount;
  private int encoderDelay;
  private int encoderPadding;
  private long currentPositionUs;
  private boolean allowFirstBufferPositionDiscontinuity;
  private boolean allowPositionDiscontinuity;
  private long lastInputTimeUs;
  private int pendingStreamChangeCount;

  private final Logger log = new Logger(Logger.Module.Audio, TAG);

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   */
  public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
    this(
        context,
        mediaCodecSelector,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param drmSessionManager For use with encrypted content. May be null if support for encrypted
   *     content is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys) {
    this(
        context,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        /* eventHandler= */ null,
        /* eventListener= */ null);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener) {
    this(
        context,
        mediaCodecSelector,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false,
        eventHandler,
        eventListener);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
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
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener) {
    this(
        context,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        eventHandler,
        eventListener,
        (AudioCapabilities) null);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
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
   * @param audioCapabilities The audio capabilities for playback on this device. May be null if the
   *     default capabilities (no encoded audio passthrough support) should be assumed.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process PCM audio before
   *     output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      @Nullable AudioCapabilities audioCapabilities,
      AudioProcessor... audioProcessors) {
    this(
        context,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        eventHandler,
        eventListener,
        new DefaultAudioSink(audioCapabilities, audioProcessors));
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
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
   * @param audioSink The sink to which audio will be output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    this(
        context,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        /* enableDecoderFallback= */ false,
        eventHandler,
        eventListener,
        audioSink);
  }

  /**
   * @param context A context.
   * @param mediaCodecSelector A decoder selector.
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
   * @param audioSink The sink to which audio will be output.
   */
  public MediaCodecAudioRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      boolean playClearSamplesWithoutKeys,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(
        C.TRACK_TYPE_AUDIO,
        mediaCodecSelector,
        drmSessionManager,
        playClearSamplesWithoutKeys,
        enableDecoderFallback,
        /* assumedMinimumCodecOperatingRate= */ 44100);
    this.context = context.getApplicationContext();
    this.audioSink = audioSink;
    lastInputTimeUs = C.TIME_UNSET;
    pendingStreamChangeTimesUs = new long[MAX_PENDING_STREAM_CHANGE_COUNT];
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    audioSink.setListener(new AudioSinkListener());
  }

  @Override
  protected int supportsFormat(MediaCodecSelector mediaCodecSelector,
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Format format)
      throws DecoderQueryException {
    String mimeType = format.sampleMimeType;
    if (!MimeTypes.isAudio(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    int tunnelingSupport = Util.SDK_INT >= 21 ? TUNNELING_SUPPORTED : TUNNELING_NOT_SUPPORTED;
    boolean supportsFormatDrm = supportsFormatDrm(drmSessionManager, format.drmInitData);
    if (supportsFormatDrm
        && allowPassthrough(format.channelCount, mimeType)
        && mediaCodecSelector.getPassthroughDecoderInfo() != null) {
      return ADAPTIVE_NOT_SEAMLESS | tunnelingSupport | FORMAT_HANDLED;
    }
    if ((MimeTypes.AUDIO_RAW.equals(mimeType)
            && !audioSink.supportsOutput(format.channelCount, format.pcmEncoding))
        || !audioSink.supportsOutput(format.channelCount, C.ENCODING_PCM_16BIT)) {
      // Assume the decoder outputs 16-bit PCM, unless the input is raw.
      return FORMAT_UNSUPPORTED_SUBTYPE;
    }
    boolean requiresSecureDecryption = false;
    DrmInitData drmInitData = format.drmInitData;
    if (drmInitData != null) {
      for (int i = 0; i < drmInitData.schemeDataCount; i++) {
        requiresSecureDecryption |= drmInitData.get(i).requiresSecureDecryption;
      }
    }
    List<MediaCodecInfo> decoderInfos =
        mediaCodecSelector.getDecoderInfos(
            format.sampleMimeType, requiresSecureDecryption, /* requiresTunnelingDecoder= */ false);
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
    if (!supportsFormatDrm) {
      return FORMAT_UNSUPPORTED_DRM;
    }
    // Check capabilities for the first decoder in the list, which takes priority.
    MediaCodecInfo decoderInfo = decoderInfos.get(0);
    boolean isFormatSupported = decoderInfo.isFormatSupported(format);
    int adaptiveSupport =
        isFormatSupported && decoderInfo.isSeamlessAdaptationSupported(format)
            ? ADAPTIVE_SEAMLESS
            : ADAPTIVE_NOT_SEAMLESS;
    int formatSupport = isFormatSupported ? FORMAT_HANDLED : FORMAT_EXCEEDS_CAPABILITIES;
    return adaptiveSupport | tunnelingSupport | formatSupport;
  }

  @Override
  protected List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException {
    if (allowPassthrough(format.channelCount, format.sampleMimeType)
            && AmazonQuirks.useDefaultPassthroughDecoder()) { // AMZN_CHANGE_ONELINE
      MediaCodecInfo passthroughDecoderInfo = mediaCodecSelector.getPassthroughDecoderInfo();
      if (passthroughDecoderInfo != null) {
        return Collections.singletonList(passthroughDecoderInfo);
      }
    }
    List<MediaCodecInfo> decoderInfos =
        mediaCodecSelector.getDecoderInfos(
            format.sampleMimeType, requiresSecureDecoder, /* requiresTunnelingDecoder= */ false);
    if (MimeTypes.AUDIO_E_AC3_JOC.equals(format.sampleMimeType)) {
      // E-AC3 decoders can decode JOC streams, but in 2-D rather than 3-D.
      List<MediaCodecInfo> decoderInfosWithEac3 = new ArrayList<>(decoderInfos);
      decoderInfosWithEac3.addAll(
          mediaCodecSelector.getDecoderInfos(
              MimeTypes.AUDIO_E_AC3, requiresSecureDecoder, /* requiresTunnelingDecoder= */ false));
      decoderInfos = decoderInfosWithEac3;
    }
    return Collections.unmodifiableList(decoderInfos);
  }

  /**
   * Returns whether encoded audio passthrough should be used for playing back the input format.
   * This implementation returns true if the {@link AudioSink} indicates that encoded audio output
   * is supported.
   *
   * @param channelCount The number of channels in the input media, or {@link Format#NO_VALUE} if
   *     not known.
   * @param mimeType The type of input media.
   * @return Whether passthrough playback is supported.
   */
  protected boolean allowPassthrough(int channelCount, String mimeType) {
    return getPassthroughEncoding(channelCount, mimeType) != C.ENCODING_INVALID;
  }

  @Override
  protected void configureCodec(
      MediaCodecInfo codecInfo,
      MediaCodec codec,
      Format format,
      MediaCrypto crypto,
      float codecOperatingRate) {
    codecMaxInputSize = getCodecMaxInputSize(codecInfo, format, getStreamFormats());
    log.setTAG(codecInfo.name + "-" + TAG); // AMZN_CHANGE_ONELINE
    codecNeedsDiscardChannelsWorkaround = codecNeedsDiscardChannelsWorkaround(codecInfo.name);
    codecNeedsEosBufferTimestampWorkaround = codecNeedsEosBufferTimestampWorkaround(codecInfo.name);
    passthroughEnabled = codecInfo.passthrough;
    String codecMimeType = passthroughEnabled ? MimeTypes.AUDIO_RAW : codecInfo.codecMimeType;
    MediaFormat mediaFormat =
        getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate);
    codec.configure(mediaFormat, /* surface= */ null, crypto, /* flags= */ 0);
    if (passthroughEnabled) {
      // Store the input MIME type if we're using the passthrough codec.
      passthroughMediaFormat = mediaFormat;
      passthroughMediaFormat.setString(MediaFormat.KEY_MIME, format.sampleMimeType);
    } else {
      passthroughMediaFormat = null;
    }
  }

  @Override
  protected @KeepCodecResult int canKeepCodec(
      MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    // TODO: We currently rely on recreating the codec when encoder delay or padding is non-zero.
    // Re-creating the codec is necessary to guarantee that onOutputFormatChanged is called, which
    // is where encoder delay and padding are propagated to the sink. We should find a better way to
    // propagate these values, and then allow the codec to be re-used in cases where this would
    // otherwise be possible.
    if (getCodecMaxInputSize(codecInfo, newFormat) > codecMaxInputSize
        || oldFormat.encoderDelay != 0
        || oldFormat.encoderPadding != 0
        || newFormat.encoderDelay != 0
        || newFormat.encoderPadding != 0) {
      return KEEP_CODEC_RESULT_NO;
    } else if (codecInfo.isSeamlessAdaptationSupported(
        oldFormat, newFormat, /* isNewFormatComplete= */ true)) {
      return KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION;
    } else if (areCodecConfigurationCompatible(oldFormat, newFormat)) {
      return KEEP_CODEC_RESULT_YES_WITH_FLUSH;
    } else {
      return KEEP_CODEC_RESULT_NO;
    }
  }

  @Override
  public MediaClock getMediaClock() {
    return this;
  }

  @Override
  protected float getCodecOperatingRateV23(
      float operatingRate, Format format, Format[] streamFormats) {
    // Use the highest known stream sample-rate up front, to avoid having to reconfigure the codec
    // should an adaptive switch to that stream occur.
    int maxSampleRate = -1;
    for (Format streamFormat : streamFormats) {
      int streamSampleRate = streamFormat.sampleRate;
      if (streamSampleRate != Format.NO_VALUE) {
        maxSampleRate = Math.max(maxSampleRate, streamSampleRate);
      }
    }
    return maxSampleRate == -1 ? CODEC_OPERATING_RATE_UNSET : (maxSampleRate * operatingRate);
  }

  @Override
  protected void onCodecInitialized(String name, long initializedTimestampMs,
      long initializationDurationMs) {
    eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
  }

  @Override
  protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
    super.onInputFormatChanged(newFormat);
    eventDispatcher.inputFormatChanged(newFormat);
    // If the input format is anything other than PCM then we assume that the audio decoder will
    // output 16-bit PCM.
    pcmEncoding = MimeTypes.AUDIO_RAW.equals(newFormat.sampleMimeType) ? newFormat.pcmEncoding
        : C.ENCODING_PCM_16BIT;
    channelCount = newFormat.channelCount;
    encoderDelay = newFormat.encoderDelay;
    encoderPadding = newFormat.encoderPadding;
  }

  @Override
  protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat)
      throws ExoPlaybackException {
    log.i("onOutputFormatChanged: outputFormat:" + outputFormat
            + ", codec:" + codec);
    @C.Encoding int encoding;
    MediaFormat format;
    if (passthroughMediaFormat != null) {
      format = passthroughMediaFormat;
      encoding =
          getPassthroughEncoding(
              format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
              format.getString(MediaFormat.KEY_MIME));
    } else {
      format = outputFormat;
      // AMZN_CHANGE_BEGIN
      // In Amazon Devices, some platform dolby decoders may output mime types depending on the
      // audio capabilities of the connected device and Dolby settings. So, as a general rule, if
      // platform decoder is being used instead of OMX.google.raw.decoder, need to
      // configure audio track based on the output mime type returned by the media codec.
      encoding = AmazonQuirks.isAmazonDevice() ?
              MimeTypes.getEncoding(format.getString(MediaFormat.KEY_MIME)) : pcmEncoding;
      // AMZN_CHANGE_END
    }
    int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    int[] channelMap;
    if (codecNeedsDiscardChannelsWorkaround && channelCount == 6 && this.channelCount < 6) {
      channelMap = new int[this.channelCount];
      for (int i = 0; i < this.channelCount; i++) {
        channelMap[i] = i;
      }
    } else {
      channelMap = null;
    }

    try {
      audioSink.configure(encoding, channelCount, sampleRate, 0, channelMap, encoderDelay,
          encoderPadding);
    } catch (AudioSink.ConfigurationException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  /**
   * Returns the {@link C.Encoding} constant to use for passthrough of the given format, or {@link
   * C#ENCODING_INVALID} if passthrough is not possible.
   */
  @C.Encoding
  protected int getPassthroughEncoding(int channelCount, String mimeType) {
    if (MimeTypes.AUDIO_E_AC3_JOC.equals(mimeType)) {
      if (audioSink.supportsOutput(channelCount, C.ENCODING_E_AC3_JOC)) {
        return MimeTypes.getEncoding(MimeTypes.AUDIO_E_AC3_JOC);
      }
      // E-AC3 receivers can decode JOC streams, but in 2-D rather than 3-D, so try to fall back.
      mimeType = MimeTypes.AUDIO_E_AC3;
    }

    @C.Encoding int encoding = MimeTypes.getEncoding(mimeType);
    if (audioSink.supportsOutput(channelCount, encoding)) {
      return encoding;
    } else {
      return C.ENCODING_INVALID;
    }
  }

  /**
   * Called when the audio session id becomes known. The default implementation is a no-op. One
   * reason for overriding this method would be to instantiate and enable a {@link Virtualizer} in
   * order to spatialize the audio channels. For this use case, any {@link Virtualizer} instances
   * should be released in {@link #onDisabled()} (if not before).
   *
   * @see AudioSink.Listener#onAudioSessionId(int)
   */
  protected void onAudioSessionId(int audioSessionId) {
    // Do nothing.
  }

  /**
   * @see AudioSink.Listener#onPositionDiscontinuity()
   */
  protected void onAudioTrackPositionDiscontinuity() {
    // Do nothing.
  }

  /**
   * @see AudioSink.Listener#onUnderrun(int, long, long)
   */
  protected void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs,
      long elapsedSinceLastFeedMs) {
    // Do nothing.
  }

  @Override
  protected void onEnabled(boolean joining) throws ExoPlaybackException {
    super.onEnabled(joining);
    eventDispatcher.enabled(decoderCounters);
    int tunnelingAudioSessionId = getConfiguration().tunnelingAudioSessionId;
    if (tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
      audioSink.enableTunnelingV21(tunnelingAudioSessionId);
    } else {
      audioSink.disableTunneling();
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long offsetUs) throws ExoPlaybackException {
    super.onStreamChanged(formats, offsetUs);
    if (lastInputTimeUs != C.TIME_UNSET) {
      if (pendingStreamChangeCount == pendingStreamChangeTimesUs.length) {
        Log.w(
            TAG,
            "Too many stream changes, so dropping change at "
                + pendingStreamChangeTimesUs[pendingStreamChangeCount - 1]);
      } else {
        pendingStreamChangeCount++;
      }
      pendingStreamChangeTimesUs[pendingStreamChangeCount - 1] = lastInputTimeUs;
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    super.onPositionReset(positionUs, joining);
    audioSink.flush();
    currentPositionUs = positionUs;
    allowFirstBufferPositionDiscontinuity = true;
    allowPositionDiscontinuity = true;
    lastInputTimeUs = C.TIME_UNSET;
    pendingStreamChangeCount = 0;
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    audioSink.play();
  }

  @Override
  protected void onStopped() {
    updateCurrentPosition();
    audioSink.pause();
    super.onStopped();
  }

  @Override
  protected void onDisabled() {
    try {
      lastInputTimeUs = C.TIME_UNSET;
      pendingStreamChangeCount = 0;
      audioSink.flush();
    } finally {
      try {
        super.onDisabled();
      } finally {
        eventDispatcher.disabled(decoderCounters);
      }
    }
  }

  @Override
  protected void onReset() {
    try {
      super.onReset();
    } finally {
      audioSink.reset();
    }
  }

  @Override
  public boolean isEnded() {
    return super.isEnded() && audioSink.isEnded();
  }

  @Override
  public boolean isReady() {
    return audioSink.hasPendingData() || super.isReady();
  }

  @Override
  public long getPositionUs() {
    if (getState() == STATE_STARTED) {
      updateCurrentPosition();
    }
    return currentPositionUs;
  }

  @Override
  public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
    return audioSink.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return audioSink.getPlaybackParameters();
  }

  @Override
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    if (allowFirstBufferPositionDiscontinuity && !buffer.isDecodeOnly()) {
      // TODO: Remove this hack once we have a proper fix for [Internal: b/71876314].
      // Allow the position to jump if the first presentable input buffer has a timestamp that
      // differs significantly from what was expected.
      if (Math.abs(buffer.timeUs - currentPositionUs) > 500000) {
        currentPositionUs = buffer.timeUs;
      }
      allowFirstBufferPositionDiscontinuity = false;
    }
    lastInputTimeUs = Math.max(buffer.timeUs, lastInputTimeUs);
  }

  @CallSuper
  @Override
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    while (pendingStreamChangeCount != 0 && presentationTimeUs >= pendingStreamChangeTimesUs[0]) {
      audioSink.handleDiscontinuity();
      pendingStreamChangeCount--;
      System.arraycopy(
          pendingStreamChangeTimesUs,
          /* srcPos= */ 1,
          pendingStreamChangeTimesUs,
          /* destPos= */ 0,
          pendingStreamChangeCount);
    }
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
    if (codecNeedsEosBufferTimestampWorkaround
        && bufferPresentationTimeUs == 0
        && (bufferFlags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
        && lastInputTimeUs != C.TIME_UNSET) {
      bufferPresentationTimeUs = lastInputTimeUs;
    }

    // AMZN_CHANGE_BEGIN
    if (log.allowDebug()) {
      log.d("processOutputBuffer: positionUs = " + positionUs +
              ", elapsedRealtimeUs =  " + elapsedRealtimeUs +
              ", bufferIndex = " + bufferIndex +
              ", isDecodeOnlyBuffer = " + isDecodeOnlyBuffer +
              ", isLastBuffer = " + isLastBuffer +
              ", bufferPresentationTimeUs = " + bufferPresentationTimeUs);
    }
    // AMZN_CHANGE_END

    if (passthroughEnabled && (bufferFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
      // Discard output buffers from the passthrough (raw) decoder containing codec specific data.
      codec.releaseOutputBuffer(bufferIndex, false);
      return true;
    }

    if (isDecodeOnlyBuffer) {
      codec.releaseOutputBuffer(bufferIndex, false);
      decoderCounters.skippedOutputBufferCount++;
      audioSink.handleDiscontinuity();
      return true;
    }

    try {
      if (audioSink.handleBuffer(buffer, bufferPresentationTimeUs)) {
        codec.releaseOutputBuffer(bufferIndex, false);
        decoderCounters.renderedOutputBufferCount++;
        return true;
      }
    } catch (AudioSink.InitializationException | AudioSink.WriteException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
    return false;
  }

  @Override
  protected void renderToEndOfStream() throws ExoPlaybackException {
    try {
      audioSink.playToEndOfStream();
    } catch (AudioSink.WriteException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    switch (messageType) {
      case C.MSG_SET_VOLUME:
        audioSink.setVolume((Float) message);
        break;
      case C.MSG_SET_AUDIO_ATTRIBUTES:
        AudioAttributes audioAttributes = (AudioAttributes) message;
        audioSink.setAudioAttributes(audioAttributes);
        break;
      case C.MSG_SET_AUX_EFFECT_INFO:
        AuxEffectInfo auxEffectInfo = (AuxEffectInfo) message;
        audioSink.setAuxEffectInfo(auxEffectInfo);
        break;
      default:
        super.handleMessage(messageType, message);
        break;
    }
  }

  /**
   * Returns a maximum input size suitable for configuring a codec for {@code format} in a way that
   * will allow possible adaptation to other compatible formats in {@code streamFormats}.
   *
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param format The format for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return A suitable maximum input size.
   */
  protected int getCodecMaxInputSize(
      MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
    int maxInputSize = getCodecMaxInputSize(codecInfo, format);
    if (streamFormats.length == 1) {
      // The single entry in streamFormats must correspond to the format for which the codec is
      // being configured.
      return maxInputSize;
    }
    for (Format streamFormat : streamFormats) {
      if (codecInfo.isSeamlessAdaptationSupported(
          format, streamFormat, /* isNewFormatComplete= */ false)) {
        maxInputSize = Math.max(maxInputSize, getCodecMaxInputSize(codecInfo, streamFormat));
      }
    }
    return maxInputSize;
  }

  /**
   * Returns a maximum input buffer size for a given format.
   *
   * @param codecInfo A {@link MediaCodecInfo} describing the decoder.
   * @param format The format.
   * @return A maximum input buffer size in bytes, or {@link Format#NO_VALUE} if a maximum could not
   *     be determined.
   */
  private int getCodecMaxInputSize(MediaCodecInfo codecInfo, Format format) {
    if ("OMX.google.raw.decoder".equals(codecInfo.name)) {
      // OMX.google.raw.decoder didn't resize its output buffers correctly prior to N, except on
      // Android TV running M, so there's no point requesting a non-default input size. Doing so may
      // cause a native crash, whereas not doing so will cause a more controlled failure when
      // attempting to fill an input buffer. See: https://github.com/google/ExoPlayer/issues/4057.
      if (Util.SDK_INT < 24 && !(Util.SDK_INT == 23 && Util.isTv(context))) {
        return Format.NO_VALUE;
      }
    }
    return format.maxInputSize;
  }

  /**
   * Returns whether two {@link Format}s will cause the same codec to be configured in an identical
   * way, excluding {@link MediaFormat#KEY_MAX_INPUT_SIZE} and configuration that does not come from
   * the {@link Format}.
   *
   * @param oldFormat The first format.
   * @param newFormat The second format.
   * @return Whether the two formats will cause a codec to be configured in an identical way,
   *     excluding {@link MediaFormat#KEY_MAX_INPUT_SIZE} and configuration that does not come from
   *     the {@link Format}.
   */
  protected boolean areCodecConfigurationCompatible(Format oldFormat, Format newFormat) {
    return Util.areEqual(oldFormat.sampleMimeType, newFormat.sampleMimeType)
        && oldFormat.channelCount == newFormat.channelCount
        && oldFormat.sampleRate == newFormat.sampleRate
        && oldFormat.initializationDataEquals(newFormat);
  }

  /**
   * Returns the framework {@link MediaFormat} that can be used to configure a {@link MediaCodec}
   * for decoding the given {@link Format} for playback.
   *
   * @param format The format of the media.
   * @param codecMimeType The MIME type handled by the codec.
   * @param codecMaxInputSize The maximum input size supported by the codec.
   * @param codecOperatingRate The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if
   *     no codec operating rate should be set.
   * @return The framework media format.
   */
  @SuppressLint("InlinedApi")
  protected MediaFormat getMediaFormat(
      Format format, String codecMimeType, int codecMaxInputSize, float codecOperatingRate) {
    MediaFormat mediaFormat = new MediaFormat();
    // Set format parameters that should always be set.
    mediaFormat.setString(MediaFormat.KEY_MIME, codecMimeType);
    mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, format.channelCount);
    mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, format.sampleRate);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    // Set codec max values.
    MediaFormatUtil.maybeSetInteger(mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, codecMaxInputSize);
    // Set codec configuration values.
    if (Util.SDK_INT >= 23) {
      mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0 /* realtime priority */);
      if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET && !deviceDoesntSupportOperatingRate()) {
        mediaFormat.setFloat(MediaFormat.KEY_OPERATING_RATE, codecOperatingRate);
      }
    }
    if (Util.SDK_INT <= 28 && MimeTypes.AUDIO_AC4.equals(format.sampleMimeType)) {
      // On some older builds, the AC-4 decoder expects to receive samples formatted as raw frames
      // not sync frames. Set a format key to override this.
      mediaFormat.setInteger("ac4-is-sync", 1);
    }
    return mediaFormat;
  }

  private void updateCurrentPosition() {
    long newCurrentPositionUs = audioSink.getCurrentPositionUs(isEnded());
    if (newCurrentPositionUs != AudioSink.CURRENT_POSITION_NOT_SET) {
      currentPositionUs =
          allowPositionDiscontinuity
              ? newCurrentPositionUs
              : Math.max(currentPositionUs, newCurrentPositionUs);
      allowPositionDiscontinuity = false;
    }
  }

  /**
   * Returns whether the device's decoders are known to not support setting the codec operating
   * rate.
   *
   * <p>See <a href="https://github.com/google/ExoPlayer/issues/5821">GitHub issue #5821</a>.
   */
  private static boolean deviceDoesntSupportOperatingRate() {
    return Util.SDK_INT == 23
        && ("ZTE B2017G".equals(Util.MODEL) || "AXON 7 mini".equals(Util.MODEL));
  }

  /**
   * Returns whether the decoder is known to output six audio channels when provided with input with
   * fewer than six channels.
   * <p>
   * See [Internal: b/35655036].
   */
  private static boolean codecNeedsDiscardChannelsWorkaround(String codecName) {
    // The workaround applies to Samsung Galaxy S6 and Samsung Galaxy S7.
    return Util.SDK_INT < 24 && "OMX.SEC.aac.dec".equals(codecName)
        && "samsung".equals(Util.MANUFACTURER)
        && (Util.DEVICE.startsWith("zeroflte") || Util.DEVICE.startsWith("herolte")
        || Util.DEVICE.startsWith("heroqlte"));
  }

  /**
   * Returns whether the decoder may output a non-empty buffer with timestamp 0 as the end of stream
   * buffer.
   *
   * <p>See <a href="https://github.com/google/ExoPlayer/issues/5045">GitHub issue #5045</a>.
   */
  private static boolean codecNeedsEosBufferTimestampWorkaround(String codecName) {
    return Util.SDK_INT < 21
        && "OMX.SEC.mp3.dec".equals(codecName)
        && "samsung".equals(Util.MANUFACTURER)
        && (Util.DEVICE.startsWith("baffin")
            || Util.DEVICE.startsWith("grand")
            || Util.DEVICE.startsWith("fortuna")
            || Util.DEVICE.startsWith("gprimelte")
            || Util.DEVICE.startsWith("j2y18lte")
            || Util.DEVICE.startsWith("ms01"));
  }

  private final class AudioSinkListener implements AudioSink.Listener {

    @Override
    public void onAudioSessionId(int audioSessionId) {
      eventDispatcher.audioSessionId(audioSessionId);
      MediaCodecAudioRenderer.this.onAudioSessionId(audioSessionId);
    }

    @Override
    public void onPositionDiscontinuity() {
      onAudioTrackPositionDiscontinuity();
      // We are out of sync so allow currentPositionUs to jump backwards.
      MediaCodecAudioRenderer.this.allowPositionDiscontinuity = true;
    }

    @Override
    public void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
      eventDispatcher.audioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
    }

  }

}
