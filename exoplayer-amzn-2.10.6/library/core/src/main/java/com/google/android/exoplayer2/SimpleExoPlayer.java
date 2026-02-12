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
package com.google.android.exoplayer2;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioFocusManager;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An {@link ExoPlayer} implementation that uses default {@link Renderer} components. Instances can
 * be obtained from {@link ExoPlayerFactory}.
 */
public class SimpleExoPlayer extends BasePlayer
    implements ExoPlayer,
        Player.AudioComponent,
        Player.VideoComponent,
        Player.TextComponent,
        Player.MetadataComponent {

  /** @deprecated Use {@link com.google.android.exoplayer2.video.VideoListener}. */
  @Deprecated
  public interface VideoListener extends com.google.android.exoplayer2.video.VideoListener {}

  private static final String TAG = "SimpleExoPlayer";

  protected final Renderer[] renderers;

  private final ExoPlayerImpl player;
  private final Handler eventHandler;
  private final ComponentListener componentListener;
  private final CopyOnWriteArraySet<com.google.android.exoplayer2.video.VideoListener>
      videoListeners;
  private final CopyOnWriteArraySet<AudioListener> audioListeners;
  private final CopyOnWriteArraySet<TextOutput> textOutputs;
  private final CopyOnWriteArraySet<MetadataOutput> metadataOutputs;
  private final CopyOnWriteArraySet<VideoRendererEventListener> videoDebugListeners;
  private final CopyOnWriteArraySet<AudioRendererEventListener> audioDebugListeners;
  private final BandwidthMeter bandwidthMeter;
  private final AnalyticsCollector analyticsCollector;

  private final AudioFocusManager audioFocusManager;

  @Nullable private Format videoFormat;
  @Nullable private Format audioFormat;

  @Nullable private Surface surface;
  private boolean ownsSurface;
  private @C.VideoScalingMode int videoScalingMode;
  @Nullable private SurfaceHolder surfaceHolder;
  @Nullable private TextureView textureView;
  private int surfaceWidth;
  private int surfaceHeight;
  @Nullable private DecoderCounters videoDecoderCounters;
  @Nullable private DecoderCounters audioDecoderCounters;
  private int audioSessionId;
  private AudioAttributes audioAttributes;
  private float audioVolume;
  @Nullable private MediaSource mediaSource;
  private List<Cue> currentCues;
  @Nullable private VideoFrameMetadataListener videoFrameMetadataListener;
  @Nullable private CameraMotionListener cameraMotionListener;
  private boolean hasNotifiedFullWrongThreadWarning;
  @Nullable private PriorityTaskManager priorityTaskManager;
  private boolean isPriorityTaskManagerRegistered;

  /**
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param bandwidthMeter The {@link BandwidthMeter} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  protected SimpleExoPlayer(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      Looper looper) {
    this(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        bandwidthMeter,
        new AnalyticsCollector.Factory(),
        looper);
  }

  /**
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param bandwidthMeter The {@link BandwidthMeter} that will be used by the instance.
   * @param analyticsCollectorFactory A factory for creating the {@link AnalyticsCollector} that
   *     will collect and forward all player events.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  protected SimpleExoPlayer(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      BandwidthMeter bandwidthMeter,
      AnalyticsCollector.Factory analyticsCollectorFactory,
      Looper looper) {
    this(
        context,
        renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        bandwidthMeter,
        analyticsCollectorFactory,
        Clock.DEFAULT,
        looper);
  }

  /**
   * @param context A {@link Context}.
   * @param renderersFactory A factory for creating {@link Renderer}s to be used by the instance.
   * @param trackSelector The {@link TrackSelector} that will be used by the instance.
   * @param loadControl The {@link LoadControl} that will be used by the instance.
   * @param drmSessionManager An optional {@link DrmSessionManager}. May be null if the instance
   *     will not be used for DRM protected playbacks.
   * @param bandwidthMeter The {@link BandwidthMeter} that will be used by the instance.
   * @param analyticsCollectorFactory A factory for creating the {@link AnalyticsCollector} that
   *     will collect and forward all player events.
   * @param clock The {@link Clock} that will be used by the instance. Should always be {@link
   *     Clock#DEFAULT}, unless the player is being used from a test.
   * @param looper The {@link Looper} which must be used for all calls to the player and which is
   *     used to call listeners on.
   */
  protected SimpleExoPlayer(
      Context context,
      RenderersFactory renderersFactory,
      TrackSelector trackSelector,
      LoadControl loadControl,
      @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
      BandwidthMeter bandwidthMeter,
      AnalyticsCollector.Factory analyticsCollectorFactory,
      Clock clock,
      Looper looper) {
    this.bandwidthMeter = bandwidthMeter;
    componentListener = new ComponentListener();
    videoListeners = new CopyOnWriteArraySet<>();
    audioListeners = new CopyOnWriteArraySet<>();
    textOutputs = new CopyOnWriteArraySet<>();
    metadataOutputs = new CopyOnWriteArraySet<>();
    videoDebugListeners = new CopyOnWriteArraySet<>();
    audioDebugListeners = new CopyOnWriteArraySet<>();
    eventHandler = new Handler(looper);
    renderers =
        renderersFactory.createRenderers(
            eventHandler,
            componentListener,
            componentListener,
            componentListener,
            componentListener,
            drmSessionManager);

    // Set initial values.
    audioVolume = 1;
    audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    audioAttributes = AudioAttributes.DEFAULT;
    videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT;
    currentCues = Collections.emptyList();

    // Build the player and associated objects.
    player =
        new ExoPlayerImpl(renderers, trackSelector, loadControl, bandwidthMeter, clock, looper);
    analyticsCollector = analyticsCollectorFactory.createAnalyticsCollector(player, clock);
    addListener(analyticsCollector);
    addListener(componentListener);
    videoDebugListeners.add(analyticsCollector);
    videoListeners.add(analyticsCollector);
    audioDebugListeners.add(analyticsCollector);
    audioListeners.add(analyticsCollector);
    addMetadataOutput(analyticsCollector);
    bandwidthMeter.addEventListener(eventHandler, analyticsCollector);
    if (drmSessionManager instanceof DefaultDrmSessionManager) {
      ((DefaultDrmSessionManager) drmSessionManager).addListener(eventHandler, analyticsCollector);
    }
    audioFocusManager = new AudioFocusManager(context, componentListener);
  }

  @Override
  @Nullable
  public AudioComponent getAudioComponent() {
    return this;
  }

  @Override
  @Nullable
  public VideoComponent getVideoComponent() {
    return this;
  }

  @Override
  @Nullable
  public TextComponent getTextComponent() {
    return this;
  }

  @Override
  @Nullable
  public MetadataComponent getMetadataComponent() {
    return this;
  }

  /**
   * Sets the video scaling mode.
   *
   * <p>Note that the scaling mode only applies if a {@link MediaCodec}-based video {@link Renderer}
   * is enabled and if the output surface is owned by a {@link android.view.SurfaceView}.
   *
   * @param videoScalingMode The video scaling mode.
   */
  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int videoScalingMode) {
    verifyApplicationThread();
    this.videoScalingMode = videoScalingMode;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        player
            .createMessage(renderer)
            .setType(C.MSG_SET_SCALING_MODE)
            .setPayload(videoScalingMode)
            .send();
      }
    }
  }

  @Override
  public @C.VideoScalingMode int getVideoScalingMode() {
    return videoScalingMode;
  }

  @Override
  public void clearVideoSurface() {
    verifyApplicationThread();
    setVideoSurface(null);
  }

  @Override
  public void clearVideoSurface(Surface surface) {
    verifyApplicationThread();
    if (surface != null && surface == this.surface) {
      setVideoSurface(null);
    }
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    setVideoSurfaceInternal(surface, false);
    int newSurfaceSize = surface == null ? 0 : C.LENGTH_UNSET;
    maybeNotifySurfaceSizeChanged(/* width= */ newSurfaceSize, /* height= */ newSurfaceSize);
  }

  @Override
  public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    this.surfaceHolder = surfaceHolder;
    if (surfaceHolder == null) {
      setVideoSurfaceInternal(null, false);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    } else {
      surfaceHolder.addCallback(componentListener);
      Surface surface = surfaceHolder.getSurface();
      if (surface != null && surface.isValid()) {
        setVideoSurfaceInternal(surface, /* ownsSurface= */ false);
        Rect surfaceSize = surfaceHolder.getSurfaceFrame();
        maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
      } else {
        setVideoSurfaceInternal(/* surface= */ null, /* ownsSurface= */ false);
        maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      }
    }
  }

  @Override
  public void clearVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
      setVideoSurfaceHolder(null);
    }
  }

  @Override
  public void setVideoSurfaceView(SurfaceView surfaceView) {
    setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  @Override
  public void clearVideoSurfaceView(SurfaceView surfaceView) {
    clearVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
  }

  @Override
  public void setVideoTextureView(TextureView textureView) {
    verifyApplicationThread();
    removeSurfaceCallbacks();
    this.textureView = textureView;
    if (textureView == null) {
      setVideoSurfaceInternal(null, true);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    } else {
      if (textureView.getSurfaceTextureListener() != null) {
        Log.w(TAG, "Replacing existing SurfaceTextureListener.");
      }
      textureView.setSurfaceTextureListener(componentListener);
      SurfaceTexture surfaceTexture = textureView.isAvailable() ? textureView.getSurfaceTexture()
          : null;
      if (surfaceTexture == null) {
        setVideoSurfaceInternal(/* surface= */ null, /* ownsSurface= */ true);
        maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      } else {
        setVideoSurfaceInternal(new Surface(surfaceTexture), /* ownsSurface= */ true);
        maybeNotifySurfaceSizeChanged(textureView.getWidth(), textureView.getHeight());
      }
    }
  }

  @Override
  public void clearVideoTextureView(TextureView textureView) {
    verifyApplicationThread();
    if (textureView != null && textureView == this.textureView) {
      setVideoTextureView(null);
    }
  }

  @Override
  public void addAudioListener(AudioListener listener) {
    audioListeners.add(listener);
  }

  @Override
  public void removeAudioListener(AudioListener listener) {
    audioListeners.remove(listener);
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false);
  }

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    verifyApplicationThread();
    if (!Util.areEqual(this.audioAttributes, audioAttributes)) {
      this.audioAttributes = audioAttributes;
      for (Renderer renderer : renderers) {
        if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
          player
              .createMessage(renderer)
              .setType(C.MSG_SET_AUDIO_ATTRIBUTES)
              .setPayload(audioAttributes)
              .send();
        }
      }
      for (AudioListener audioListener : audioListeners) {
        audioListener.onAudioAttributesChanged(audioAttributes);
      }
    }

    @AudioFocusManager.PlayerCommand
    int playerCommand =
        audioFocusManager.setAudioAttributes(
            handleAudioFocus ? audioAttributes : null, getPlayWhenReady(), getPlaybackState());
    updatePlayWhenReady(getPlayWhenReady(), playerCommand);
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return audioAttributes;
  }

  @Override
  public int getAudioSessionId() {
    return audioSessionId;
  }

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
    verifyApplicationThread();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        player
            .createMessage(renderer)
            .setType(C.MSG_SET_AUX_EFFECT_INFO)
            .setPayload(auxEffectInfo)
            .send();
      }
    }
  }

  @Override
  public void clearAuxEffectInfo() {
    setAuxEffectInfo(new AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, /* sendLevel= */ 0f));
  }

  @Override
  public void setVolume(float audioVolume) {
    verifyApplicationThread();
    audioVolume = Util.constrainValue(audioVolume, /* min= */ 0, /* max= */ 1);
    if (this.audioVolume == audioVolume) {
      return;
    }
    this.audioVolume = audioVolume;
    sendVolumeToRenderers();
    for (AudioListener audioListener : audioListeners) {
      audioListener.onVolumeChanged(audioVolume);
    }
  }

  @Override
  public float getVolume() {
    return audioVolume;
  }

  /**
   * Sets the stream type for audio playback, used by the underlying audio track.
   * <p>
   * Setting the stream type during playback may introduce a short gap in audio output as the audio
   * track is recreated. A new audio session id will also be generated.
   * <p>
   * Calling this method overwrites any attributes set previously by calling
   * {@link #setAudioAttributes(AudioAttributes)}.
   *
   * @deprecated Use {@link #setAudioAttributes(AudioAttributes)}.
   * @param streamType The stream type for audio playback.
   */
  @Deprecated
  public void setAudioStreamType(@C.StreamType int streamType) {
    @C.AudioUsage int usage = Util.getAudioUsageForStreamType(streamType);
    @C.AudioContentType int contentType = Util.getAudioContentTypeForStreamType(streamType);
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build();
    setAudioAttributes(audioAttributes);
  }

  /**
   * Returns the stream type for audio playback.
   *
   * @deprecated Use {@link #getAudioAttributes()}.
   */
  @Deprecated
  public @C.StreamType int getAudioStreamType() {
    return Util.getStreamTypeForAudioUsage(audioAttributes.usage);
  }

  /** Returns the {@link AnalyticsCollector} used for collecting analytics events. */
  public AnalyticsCollector getAnalyticsCollector() {
    return analyticsCollector;
  }

  /**
   * Adds an {@link AnalyticsListener} to receive analytics events.
   *
   * @param listener The listener to be added.
   */
  public void addAnalyticsListener(AnalyticsListener listener) {
    verifyApplicationThread();
    analyticsCollector.addListener(listener);
  }

  /**
   * Removes an {@link AnalyticsListener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeAnalyticsListener(AnalyticsListener listener) {
    verifyApplicationThread();
    analyticsCollector.removeListener(listener);
  }

  /**
   * Sets a {@link PriorityTaskManager}, or null to clear a previously set priority task manager.
   *
   * <p>The priority {@link C#PRIORITY_PLAYBACK} will be set while the player is loading.
   *
   * @param priorityTaskManager The {@link PriorityTaskManager}, or null to clear a previously set
   *     priority task manager.
   */
  public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
    verifyApplicationThread();
    if (Util.areEqual(this.priorityTaskManager, priorityTaskManager)) {
      return;
    }
    if (isPriorityTaskManagerRegistered) {
      Assertions.checkNotNull(this.priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
    }
    if (priorityTaskManager != null && isLoading()) {
      priorityTaskManager.add(C.PRIORITY_PLAYBACK);
      isPriorityTaskManagerRegistered = true;
    } else {
      isPriorityTaskManagerRegistered = false;
    }
    this.priorityTaskManager = priorityTaskManager;
  }

  /**
   * Sets the {@link PlaybackParams} governing audio playback.
   *
   * @deprecated Use {@link #setPlaybackParameters(PlaybackParameters)}.
   * @param params The {@link PlaybackParams}, or null to clear any previously set parameters.
   */
  @Deprecated
  @TargetApi(23)
  public void setPlaybackParams(@Nullable PlaybackParams params) {
    PlaybackParameters playbackParameters;
    if (params != null) {
      params.allowDefaults();
      playbackParameters = new PlaybackParameters(params.getSpeed(), params.getPitch());
    } else {
      playbackParameters = null;
    }
    setPlaybackParameters(playbackParameters);
  }

  /** Returns the video format currently being played, or null if no video is being played. */
  @Nullable
  public Format getVideoFormat() {
    return videoFormat;
  }

  /** Returns the audio format currently being played, or null if no audio is being played. */
  @Nullable
  public Format getAudioFormat() {
    return audioFormat;
  }

  /** Returns {@link DecoderCounters} for video, or null if no video is being played. */
  @Nullable
  public DecoderCounters getVideoDecoderCounters() {
    return videoDecoderCounters;
  }

  /** Returns {@link DecoderCounters} for audio, or null if no audio is being played. */
  @Nullable
  public DecoderCounters getAudioDecoderCounters() {
    return audioDecoderCounters;
  }

  @Override
  public void addVideoListener(com.google.android.exoplayer2.video.VideoListener listener) {
    videoListeners.add(listener);
  }

  @Override
  public void removeVideoListener(com.google.android.exoplayer2.video.VideoListener listener) {
    videoListeners.remove(listener);
  }

  @Override
  public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    verifyApplicationThread();
    videoFrameMetadataListener = listener;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        player
            .createMessage(renderer)
            .setType(C.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
            .setPayload(listener)
            .send();
      }
    }
  }

  @Override
  public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
    verifyApplicationThread();
    if (videoFrameMetadataListener != listener) {
      return;
    }
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        player
            .createMessage(renderer)
            .setType(C.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
            .setPayload(null)
            .send();
      }
    }
  }

  @Override
  public void setCameraMotionListener(CameraMotionListener listener) {
    verifyApplicationThread();
    cameraMotionListener = listener;
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_CAMERA_MOTION) {
        player
            .createMessage(renderer)
            .setType(C.MSG_SET_CAMERA_MOTION_LISTENER)
            .setPayload(listener)
            .send();
      }
    }
  }

  @Override
  public void clearCameraMotionListener(CameraMotionListener listener) {
    verifyApplicationThread();
    if (cameraMotionListener != listener) {
      return;
    }
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_CAMERA_MOTION) {
        player
            .createMessage(renderer)
            .setType(C.MSG_SET_CAMERA_MOTION_LISTENER)
            .setPayload(null)
            .send();
      }
    }
  }

  /**
   * Sets a listener to receive video events, removing all existing listeners.
   *
   * @param listener The listener.
   * @deprecated Use {@link #addVideoListener(com.google.android.exoplayer2.video.VideoListener)}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public void setVideoListener(VideoListener listener) {
    videoListeners.clear();
    if (listener != null) {
      addVideoListener(listener);
    }
  }

  /**
   * Equivalent to {@link #removeVideoListener(com.google.android.exoplayer2.video.VideoListener)}.
   *
   * @param listener The listener to clear.
   * @deprecated Use {@link
   *     #removeVideoListener(com.google.android.exoplayer2.video.VideoListener)}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public void clearVideoListener(VideoListener listener) {
    removeVideoListener(listener);
  }

  @Override
  public void addTextOutput(TextOutput listener) {
    if (!currentCues.isEmpty()) {
      listener.onCues(currentCues);
    }
    textOutputs.add(listener);
  }

  @Override
  public void removeTextOutput(TextOutput listener) {
    textOutputs.remove(listener);
  }

  /**
   * Sets an output to receive text events, removing all existing outputs.
   *
   * @param output The output.
   * @deprecated Use {@link #addTextOutput(TextOutput)}.
   */
  @Deprecated
  public void setTextOutput(TextOutput output) {
    textOutputs.clear();
    if (output != null) {
      addTextOutput(output);
    }
  }

  /**
   * Equivalent to {@link #removeTextOutput(TextOutput)}.
   *
   * @param output The output to clear.
   * @deprecated Use {@link #removeTextOutput(TextOutput)}.
   */
  @Deprecated
  public void clearTextOutput(TextOutput output) {
    removeTextOutput(output);
  }

  @Override
  public void addMetadataOutput(MetadataOutput listener) {
    metadataOutputs.add(listener);
  }

  @Override
  public void removeMetadataOutput(MetadataOutput listener) {
    metadataOutputs.remove(listener);
  }

  /**
   * Sets an output to receive metadata events, removing all existing outputs.
   *
   * @param output The output.
   * @deprecated Use {@link #addMetadataOutput(MetadataOutput)}.
   */
  @Deprecated
  public void setMetadataOutput(MetadataOutput output) {
    metadataOutputs.retainAll(Collections.singleton(analyticsCollector));
    if (output != null) {
      addMetadataOutput(output);
    }
  }

  /**
   * Equivalent to {@link #removeMetadataOutput(MetadataOutput)}.
   *
   * @param output The output to clear.
   * @deprecated Use {@link #removeMetadataOutput(MetadataOutput)}.
   */
  @Deprecated
  public void clearMetadataOutput(MetadataOutput output) {
    removeMetadataOutput(output);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public void setVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.retainAll(Collections.singleton(analyticsCollector));
    if (listener != null) {
      addVideoDebugListener(listener);
    }
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  public void addVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.add(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} and {@link
   *     #removeAnalyticsListener(AnalyticsListener)} to get more detailed debug information.
   */
  @Deprecated
  public void removeVideoDebugListener(VideoRendererEventListener listener) {
    videoDebugListeners.remove(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public void setAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.retainAll(Collections.singleton(analyticsCollector));
    if (listener != null) {
      addAudioDebugListener(listener);
    }
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} to get more detailed debug
   *     information.
   */
  @Deprecated
  public void addAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.add(listener);
  }

  /**
   * @deprecated Use {@link #addAnalyticsListener(AnalyticsListener)} and {@link
   *     #removeAnalyticsListener(AnalyticsListener)} to get more detailed debug information.
   */
  @Deprecated
  public void removeAudioDebugListener(AudioRendererEventListener listener) {
    audioDebugListeners.remove(listener);
  }

  // ExoPlayer implementation

  @Override
  public Looper getPlaybackLooper() {
    return player.getPlaybackLooper();
  }

  @Override
  public Looper getApplicationLooper() {
    return player.getApplicationLooper();
  }

  @Override
  public void addListener(Player.EventListener listener) {
    verifyApplicationThread();
    player.addListener(listener);
  }

  @Override
  public void removeListener(Player.EventListener listener) {
    verifyApplicationThread();
    player.removeListener(listener);
  }

  @Override
  public int getPlaybackState() {
    verifyApplicationThread();
    return player.getPlaybackState();
  }

  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    verifyApplicationThread();
    return player.getPlaybackSuppressionReason();
  }

  @Override
  @Nullable
  public ExoPlaybackException getPlaybackError() {
    verifyApplicationThread();
    return player.getPlaybackError();
  }

  @Override
  public void retry() {
    verifyApplicationThread();
    if (mediaSource != null
        && (getPlaybackError() != null || getPlaybackState() == Player.STATE_IDLE)) {
      prepare(mediaSource, /* resetPosition= */ false, /* resetState= */ false);
    }
  }

  @Override
  public void prepare(MediaSource mediaSource) {
    prepare(mediaSource, /* resetPosition= */ true, /* resetState= */ true);
  }

  @Override
  public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
    verifyApplicationThread();
    if (this.mediaSource != null) {
      this.mediaSource.removeEventListener(analyticsCollector);
      analyticsCollector.resetForNewMediaSource();
    }
    this.mediaSource = mediaSource;
    mediaSource.addEventListener(eventHandler, analyticsCollector);
    @AudioFocusManager.PlayerCommand
    int playerCommand = audioFocusManager.handlePrepare(getPlayWhenReady());
    updatePlayWhenReady(getPlayWhenReady(), playerCommand);
    player.prepare(mediaSource, resetPosition, resetState);
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThread();
    @AudioFocusManager.PlayerCommand
    int playerCommand = audioFocusManager.handleSetPlayWhenReady(playWhenReady, getPlaybackState());
    updatePlayWhenReady(playWhenReady, playerCommand);
  }

  @Override
  public boolean getPlayWhenReady() {
    verifyApplicationThread();
    return player.getPlayWhenReady();
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    verifyApplicationThread();
    return player.getRepeatMode();
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    verifyApplicationThread();
    player.setRepeatMode(repeatMode);
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    verifyApplicationThread();
    player.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public boolean getShuffleModeEnabled() {
    verifyApplicationThread();
    return player.getShuffleModeEnabled();
  }

  @Override
  public boolean isLoading() {
    verifyApplicationThread();
    return player.isLoading();
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    verifyApplicationThread();
    analyticsCollector.notifySeekStarted();
    player.seekTo(windowIndex, positionMs);
  }

  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    verifyApplicationThread();
    player.setPlaybackParameters(playbackParameters);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    verifyApplicationThread();
    return player.getPlaybackParameters();
  }

  @Override
  public void setSeekParameters(@Nullable SeekParameters seekParameters) {
    verifyApplicationThread();
    player.setSeekParameters(seekParameters);
  }

  @Override
  public SeekParameters getSeekParameters() {
    verifyApplicationThread();
    return player.getSeekParameters();
  }

  @Override
  public void setForegroundMode(boolean foregroundMode) {
    player.setForegroundMode(foregroundMode);
  }

  @Override
  public void stop(boolean reset) {
    verifyApplicationThread();
    player.stop(reset);
    if (mediaSource != null) {
      mediaSource.removeEventListener(analyticsCollector);
      analyticsCollector.resetForNewMediaSource();
      if (reset) {
        mediaSource = null;
      }
    }
    audioFocusManager.handleStop();
    currentCues = Collections.emptyList();
  }

  @Override
  public void release() {
    verifyApplicationThread();
    audioFocusManager.handleStop();
    player.release();
    removeSurfaceCallbacks();
    if (surface != null) {
      if (ownsSurface) {
        surface.release();
      }
      surface = null;
    }
    if (mediaSource != null) {
      mediaSource.removeEventListener(analyticsCollector);
      mediaSource = null;
    }
    if (isPriorityTaskManagerRegistered) {
      Assertions.checkNotNull(priorityTaskManager).remove(C.PRIORITY_PLAYBACK);
      isPriorityTaskManagerRegistered = false;
    }
    bandwidthMeter.removeEventListener(analyticsCollector);
    currentCues = Collections.emptyList();
  }

  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void sendMessages(ExoPlayerMessage... messages) {
    player.sendMessages(messages);
  }

  @Override
  public PlayerMessage createMessage(PlayerMessage.Target target) {
    verifyApplicationThread();
    return player.createMessage(target);
  }

  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void blockingSendMessages(ExoPlayerMessage... messages) {
    player.blockingSendMessages(messages);
  }

  @Override
  public int getRendererCount() {
    verifyApplicationThread();
    return player.getRendererCount();
  }

  @Override
  public int getRendererType(int index) {
    verifyApplicationThread();
    return player.getRendererType(index);
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    verifyApplicationThread();
    return player.getCurrentTrackGroups();
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    verifyApplicationThread();
    return player.getCurrentTrackSelections();
  }

  @Override
  public Timeline getCurrentTimeline() {
    verifyApplicationThread();
    return player.getCurrentTimeline();
  }

  @Override
  @Nullable
  public Object getCurrentManifest() {
    verifyApplicationThread();
    return player.getCurrentManifest();
  }

  @Override
  public int getCurrentPeriodIndex() {
    verifyApplicationThread();
    return player.getCurrentPeriodIndex();
  }

  @Override
  public int getCurrentWindowIndex() {
    verifyApplicationThread();
    return player.getCurrentWindowIndex();
  }

  @Override
  public long getDuration() {
    verifyApplicationThread();
    return player.getDuration();
  }

  @Override
  public long getCurrentPosition() {
    verifyApplicationThread();
    return player.getCurrentPosition();
  }

  @Override
  public long getBufferedPosition() {
    verifyApplicationThread();
    return player.getBufferedPosition();
  }

  @Override
  public long getTotalBufferedDuration() {
    verifyApplicationThread();
    return player.getTotalBufferedDuration();
  }

  @Override
  public boolean isPlayingAd() {
    verifyApplicationThread();
    return player.isPlayingAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    verifyApplicationThread();
    return player.getCurrentAdGroupIndex();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    verifyApplicationThread();
    return player.getCurrentAdIndexInAdGroup();
  }

  @Override
  public long getContentPosition() {
    verifyApplicationThread();
    return player.getContentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    verifyApplicationThread();
    return player.getContentBufferedPosition();
  }

  // Internal methods.

  private void removeSurfaceCallbacks() {
    if (textureView != null) {
      if (textureView.getSurfaceTextureListener() != componentListener) {
        Log.w(TAG, "SurfaceTextureListener already unset or replaced.");
      } else {
        textureView.setSurfaceTextureListener(null);
      }
      textureView = null;
    }
    if (surfaceHolder != null) {
      surfaceHolder.removeCallback(componentListener);
      surfaceHolder = null;
    }
  }

  private void setVideoSurfaceInternal(@Nullable Surface surface, boolean ownsSurface) {
    // Note: We don't turn this method into a no-op if the surface is being replaced with itself
    // so as to ensure onRenderedFirstFrame callbacks are still called in this case.
    List<PlayerMessage> messages = new ArrayList<>();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
        messages.add(
            player.createMessage(renderer).setType(C.MSG_SET_SURFACE).setPayload(surface).send());
      }
    }
    if (this.surface != null && this.surface != surface) {
      // We're replacing a surface. Block to ensure that it's not accessed after the method returns.
      try {
        for (PlayerMessage message : messages) {
          message.blockUntilDelivered();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      // If we created the previous surface, we are responsible for releasing it.
      if (this.ownsSurface) {
        this.surface.release();
      }
    }
    this.surface = surface;
    this.ownsSurface = ownsSurface;
  }

  private void maybeNotifySurfaceSizeChanged(int width, int height) {
    if (width != surfaceWidth || height != surfaceHeight) {
      surfaceWidth = width;
      surfaceHeight = height;
      for (com.google.android.exoplayer2.video.VideoListener videoListener : videoListeners) {
        videoListener.onSurfaceSizeChanged(width, height);
      }
    }
  }

  private void sendVolumeToRenderers() {
    float scaledVolume = audioVolume * audioFocusManager.getVolumeMultiplier();
    for (Renderer renderer : renderers) {
      if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
        player.createMessage(renderer).setType(C.MSG_SET_VOLUME).setPayload(scaledVolume).send();
      }
    }
  }

  private void updatePlayWhenReady(
      boolean playWhenReady, @AudioFocusManager.PlayerCommand int playerCommand) {
    playWhenReady = playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY;
    @PlaybackSuppressionReason
    int playbackSuppressionReason =
        playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_PLAY_WHEN_READY
            ? Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
            : Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    player.setPlayWhenReady(playWhenReady, playbackSuppressionReason);
  }

  private void verifyApplicationThread() {
    if (Looper.myLooper() != getApplicationLooper()) {
      Log.w(
          TAG,
          "Player is accessed on the wrong thread. See "
              + "https://exoplayer.dev/issues/player-accessed-on-wrong-thread",
          hasNotifiedFullWrongThreadWarning ? null : new IllegalStateException());
      hasNotifiedFullWrongThreadWarning = true;
    }
  }

  private final class ComponentListener
      implements VideoRendererEventListener,
          AudioRendererEventListener,
          TextOutput,
          MetadataOutput,
          SurfaceHolder.Callback,
          TextureView.SurfaceTextureListener,
          AudioFocusManager.PlayerControl,
          Player.EventListener {

    // VideoRendererEventListener implementation

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
      videoDecoderCounters = counters;
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoEnabled(counters);
      }
    }

    @Override
    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
      videoFormat = format;
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoInputFormatChanged(format);
      }
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onDroppedFrames(count, elapsed);
      }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      for (com.google.android.exoplayer2.video.VideoListener videoListener : videoListeners) {
        // Prevent duplicate notification if a listener is both a VideoRendererEventListener and
        // a VideoListener, as they have the same method signature.
        if (!videoDebugListeners.contains(videoListener)) {
          videoListener.onVideoSizeChanged(
              width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        }
      }
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoSizeChanged(width, height, unappliedRotationDegrees,
            pixelWidthHeightRatio);
      }
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
      if (SimpleExoPlayer.this.surface == surface) {
        for (com.google.android.exoplayer2.video.VideoListener videoListener : videoListeners) {
          videoListener.onRenderedFirstFrame();
        }
      }
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onRenderedFirstFrame(surface);
      }
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
        videoDebugListener.onVideoDisabled(counters);
      }
      videoFormat = null;
      videoDecoderCounters = null;
    }

    // AudioRendererEventListener implementation

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
      audioDecoderCounters = counters;
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioEnabled(counters);
      }
    }

    @Override
    public void onAudioSessionId(int sessionId) {
      if (audioSessionId == sessionId) {
        return;
      }
      audioSessionId = sessionId;
      for (AudioListener audioListener : audioListeners) {
        // Prevent duplicate notification if a listener is both a AudioRendererEventListener and
        // a AudioListener, as they have the same method signature.
        if (!audioDebugListeners.contains(audioListener)) {
          audioListener.onAudioSessionId(sessionId);
        }
      }
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioSessionId(sessionId);
      }
    }

    @Override
    public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
        long initializationDurationMs) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs,
            initializationDurationMs);
      }
    }

    @Override
    public void onAudioInputFormatChanged(Format format) {
      audioFormat = format;
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioInputFormatChanged(format);
      }
    }

    @Override
    public void onAudioSinkUnderrun(int bufferSize, long bufferSizeMs,
        long elapsedSinceLastFeedMs) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioSinkUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
      }
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
      for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
        audioDebugListener.onAudioDisabled(counters);
      }
      audioFormat = null;
      audioDecoderCounters = null;
      audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    }

    // TextOutput implementation

    @Override
    public void onCues(List<Cue> cues) {
      currentCues = cues;
      for (TextOutput textOutput : textOutputs) {
        textOutput.onCues(cues);
      }
    }

    // MetadataOutput implementation

    @Override
    public void onMetadata(Metadata metadata) {
      for (MetadataOutput metadataOutput : metadataOutputs) {
        metadataOutput.onMetadata(metadata);
      }
    }

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      setVideoSurfaceInternal(holder.getSurface(), false);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      setVideoSurfaceInternal(null, false);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
    }

    // TextureView.SurfaceTextureListener implementation

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
      setVideoSurfaceInternal(new Surface(surfaceTexture), true);
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
      maybeNotifySurfaceSizeChanged(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
      setVideoSurfaceInternal(null, true);
      maybeNotifySurfaceSizeChanged(/* width= */ 0, /* height= */ 0);
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
      // Do nothing.
    }

    // AudioFocusManager.PlayerControl implementation

    @Override
    public void setVolumeMultiplier(float volumeMultiplier) {
      sendVolumeToRenderers();
    }

    @Override
    public void executePlayerCommand(@AudioFocusManager.PlayerCommand int playerCommand) {
      updatePlayWhenReady(getPlayWhenReady(), playerCommand);
    }

    // Player.EventListener implementation.

    @Override
    public void onLoadingChanged(boolean isLoading) {
      if (priorityTaskManager != null) {
        if (isLoading && !isPriorityTaskManagerRegistered) {
          priorityTaskManager.add(C.PRIORITY_PLAYBACK);
          isPriorityTaskManagerRegistered = true;
        } else if (!isLoading && isPriorityTaskManagerRegistered) {
          priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
          isPriorityTaskManagerRegistered = false;
        }
      }
    }
  }
}
