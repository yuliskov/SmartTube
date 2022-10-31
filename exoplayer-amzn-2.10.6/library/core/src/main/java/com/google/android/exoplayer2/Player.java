/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import com.google.android.exoplayer2.C.VideoScalingMode;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A media player interface defining traditional high-level functionality, such as the ability to
 * play, pause, seek and query properties of the currently playing media.
 * <p>
 * Some important properties of media players that implement this interface are:
 * <ul>
 *     <li>They can provide a {@link Timeline} representing the structure of the media being played,
 *     which can be obtained by calling {@link #getCurrentTimeline()}.</li>
 *     <li>They can provide a {@link TrackGroupArray} defining the currently available tracks,
 *     which can be obtained by calling {@link #getCurrentTrackGroups()}.</li>
 *     <li>They contain a number of renderers, each of which is able to render tracks of a single
 *     type (e.g. audio, video or text). The number of renderers and their respective track types
 *     can be obtained by calling {@link #getRendererCount()} and {@link #getRendererType(int)}.
 *     </li>
 *     <li>They can provide a {@link TrackSelectionArray} defining which of the currently available
 *     tracks are selected to be rendered by each renderer. This can be obtained by calling
 *     {@link #getCurrentTrackSelections()}}.</li>
 * </ul>
 */
public interface Player {

  /** The audio component of a {@link Player}. */
  interface AudioComponent {

    /**
     * Adds a listener to receive audio events.
     *
     * @param listener The listener to register.
     */
    void addAudioListener(AudioListener listener);

    /**
     * Removes a listener of audio events.
     *
     * @param listener The listener to unregister.
     */
    void removeAudioListener(AudioListener listener);

    /**
     * Sets the attributes for audio playback, used by the underlying audio track. If not set, the
     * default audio attributes will be used. They are suitable for general media playback.
     *
     * <p>Setting the audio attributes during playback may introduce a short gap in audio output as
     * the audio track is recreated. A new audio session id will also be generated.
     *
     * <p>If tunneling is enabled by the track selector, the specified audio attributes will be
     * ignored, but they will take effect if audio is later played without tunneling.
     *
     * <p>If the device is running a build before platform API version 21, audio attributes cannot
     * be set directly on the underlying audio track. In this case, the usage will be mapped onto an
     * equivalent stream type using {@link Util#getStreamTypeForAudioUsage(int)}.
     *
     * @param audioAttributes The attributes to use for audio playback.
     * @deprecated Use {@link AudioComponent#setAudioAttributes(AudioAttributes, boolean)}.
     */
    @Deprecated
    void setAudioAttributes(AudioAttributes audioAttributes);

    /**
     * Sets the attributes for audio playback, used by the underlying audio track. If not set, the
     * default audio attributes will be used. They are suitable for general media playback.
     *
     * <p>Setting the audio attributes during playback may introduce a short gap in audio output as
     * the audio track is recreated. A new audio session id will also be generated.
     *
     * <p>If tunneling is enabled by the track selector, the specified audio attributes will be
     * ignored, but they will take effect if audio is later played without tunneling.
     *
     * <p>If the device is running a build before platform API version 21, audio attributes cannot
     * be set directly on the underlying audio track. In this case, the usage will be mapped onto an
     * equivalent stream type using {@link Util#getStreamTypeForAudioUsage(int)}.
     *
     * <p>If audio focus should be handled, the {@link AudioAttributes#usage} must be {@link
     * C#USAGE_MEDIA} or {@link C#USAGE_GAME}. Other usages will throw an {@link
     * IllegalArgumentException}.
     *
     * @param audioAttributes The attributes to use for audio playback.
     * @param handleAudioFocus True if the player should handle audio focus, false otherwise.
     */
    void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus);

    /** Returns the attributes for audio playback. */
    AudioAttributes getAudioAttributes();

    /** Returns the audio session identifier, or {@link C#AUDIO_SESSION_ID_UNSET} if not set. */
    int getAudioSessionId();

    /** Sets information on an auxiliary audio effect to attach to the underlying audio track. */
    void setAuxEffectInfo(AuxEffectInfo auxEffectInfo);

    /** Detaches any previously attached auxiliary audio effect from the underlying audio track. */
    void clearAuxEffectInfo();

    /**
     * Sets the audio volume, with 0 being silence and 1 being unity gain.
     *
     * @param audioVolume The audio volume.
     */
    void setVolume(float audioVolume);

    /** Returns the audio volume, with 0 being silence and 1 being unity gain. */
    float getVolume();
  }

  /** The video component of a {@link Player}. */
  interface VideoComponent {

    /**
     * Sets the {@link VideoScalingMode}.
     *
     * @param videoScalingMode The {@link VideoScalingMode}.
     */
    void setVideoScalingMode(@VideoScalingMode int videoScalingMode);

    /** Returns the {@link VideoScalingMode}. */
    @VideoScalingMode
    int getVideoScalingMode();

    /**
     * Adds a listener to receive video events.
     *
     * @param listener The listener to register.
     */
    void addVideoListener(VideoListener listener);

    /**
     * Removes a listener of video events.
     *
     * @param listener The listener to unregister.
     */
    void removeVideoListener(VideoListener listener);

    /**
     * Sets a listener to receive video frame metadata events.
     *
     * <p>This method is intended to be called by the same component that sets the {@link Surface}
     * onto which video will be rendered. If using ExoPlayer's standard UI components, this method
     * should not be called directly from application code.
     *
     * @param listener The listener.
     */
    void setVideoFrameMetadataListener(VideoFrameMetadataListener listener);

    /**
     * Clears the listener which receives video frame metadata events if it matches the one passed.
     * Else does nothing.
     *
     * @param listener The listener to clear.
     */
    void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener);

    /**
     * Sets a listener of camera motion events.
     *
     * @param listener The listener.
     */
    void setCameraMotionListener(CameraMotionListener listener);

    /**
     * Clears the listener which receives camera motion events if it matches the one passed. Else
     * does nothing.
     *
     * @param listener The listener to clear.
     */
    void clearCameraMotionListener(CameraMotionListener listener);

    /**
     * Clears any {@link Surface}, {@link SurfaceHolder}, {@link SurfaceView} or {@link TextureView}
     * currently set on the player.
     */
    void clearVideoSurface();

    /**
     * Clears the {@link Surface} onto which video is being rendered if it matches the one passed.
     * Else does nothing.
     *
     * @param surface The surface to clear.
     */
    void clearVideoSurface(Surface surface);

    /**
     * Sets the {@link Surface} onto which video will be rendered. The caller is responsible for
     * tracking the lifecycle of the surface, and must clear the surface by calling {@code
     * setVideoSurface(null)} if the surface is destroyed.
     *
     * <p>If the surface is held by a {@link SurfaceView}, {@link TextureView} or {@link
     * SurfaceHolder} then it's recommended to use {@link #setVideoSurfaceView(SurfaceView)}, {@link
     * #setVideoTextureView(TextureView)} or {@link #setVideoSurfaceHolder(SurfaceHolder)} rather
     * than this method, since passing the holder allows the player to track the lifecycle of the
     * surface automatically.
     *
     * @param surface The {@link Surface}.
     */
    void setVideoSurface(@Nullable Surface surface);

    /**
     * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
     * rendered. The player will track the lifecycle of the surface automatically.
     *
     * @param surfaceHolder The surface holder.
     */
    void setVideoSurfaceHolder(SurfaceHolder surfaceHolder);

    /**
     * Clears the {@link SurfaceHolder} that holds the {@link Surface} onto which video is being
     * rendered if it matches the one passed. Else does nothing.
     *
     * @param surfaceHolder The surface holder to clear.
     */
    void clearVideoSurfaceHolder(SurfaceHolder surfaceHolder);

    /**
     * Sets the {@link SurfaceView} onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     * @param surfaceView The surface view.
     */
    void setVideoSurfaceView(SurfaceView surfaceView);

    /**
     * Clears the {@link SurfaceView} onto which video is being rendered if it matches the one
     * passed. Else does nothing.
     *
     * @param surfaceView The texture view to clear.
     */
    void clearVideoSurfaceView(SurfaceView surfaceView);

    /**
     * Sets the {@link TextureView} onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     * @param textureView The texture view.
     */
    void setVideoTextureView(TextureView textureView);

    /**
     * Clears the {@link TextureView} onto which video is being rendered if it matches the one
     * passed. Else does nothing.
     *
     * @param textureView The texture view to clear.
     */
    void clearVideoTextureView(TextureView textureView);
  }

  /** The text component of a {@link Player}. */
  interface TextComponent {

    /**
     * Registers an output to receive text events.
     *
     * @param listener The output to register.
     */
    void addTextOutput(TextOutput listener);

    /**
     * Removes a text output.
     *
     * @param listener The output to remove.
     */
    void removeTextOutput(TextOutput listener);
  }

  /** The metadata component of a {@link Player}. */
  interface MetadataComponent {

    /**
     * Adds a {@link MetadataOutput} to receive metadata.
     *
     * @param output The output to register.
     */
    void addMetadataOutput(MetadataOutput output);

    /**
     * Removes a {@link MetadataOutput}.
     *
     * @param output The output to remove.
     */
    void removeMetadataOutput(MetadataOutput output);
  }

  /**
   * Listener of changes in player state. All methods have no-op default implementations to allow
   * selective overrides.
   */
  interface EventListener {

    /**
     * Called when the timeline and/or manifest has been refreshed.
     *
     * <p>Note that if the timeline has changed then a position discontinuity may also have
     * occurred. For example, the current period index may have changed as a result of periods being
     * added or removed from the timeline. This will <em>not</em> be reported via a separate call to
     * {@link #onPositionDiscontinuity(int)}.
     *
     * @param timeline The latest timeline. Never null, but may be empty.
     * @param manifest The latest manifest. May be null.
     * @param reason The {@link TimelineChangeReason} responsible for this timeline change.
     */
    default void onTimelineChanged(
        Timeline timeline, @Nullable Object manifest, @TimelineChangeReason int reason) {}

    /**
     * Called when the available or selected tracks change.
     *
     * @param trackGroups The available tracks. Never null, but may be of length zero.
     * @param trackSelections The track selections for each renderer. Never null and always of
     *     length {@link #getRendererCount()}, but may contain null elements.
     */
    default void onTracksChanged(
        TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

    /**
     * Called when the player starts or stops loading the source.
     *
     * @param isLoading Whether the source is currently being loaded.
     */
    default void onLoadingChanged(boolean isLoading) {}

    /**
     * Called when the value returned from either {@link #getPlayWhenReady()} or {@link
     * #getPlaybackState()} changes.
     *
     * @param playWhenReady Whether playback will proceed when ready.
     * @param playbackState One of the {@code STATE} constants.
     */
    default void onPlayerStateChanged(boolean playWhenReady, int playbackState) {}

    /**
     * Called when the value returned from {@link #getPlaybackSuppressionReason()} changes.
     *
     * @param playbackSuppressionReason The current {@link PlaybackSuppressionReason}.
     */
    default void onPlaybackSuppressionReasonChanged(
        @PlaybackSuppressionReason int playbackSuppressionReason) {}

    /**
     * Called when the value of {@link #isPlaying()} changes.
     *
     * @param isPlaying Whether the player is playing.
     */
    default void onIsPlayingChanged(boolean isPlaying) {}

    /**
     * Called when the value of {@link #getRepeatMode()} changes.
     *
     * @param repeatMode The {@link RepeatMode} used for playback.
     */
    default void onRepeatModeChanged(@RepeatMode int repeatMode) {}

    /**
     * Called when the value of {@link #getShuffleModeEnabled()} changes.
     *
     * @param shuffleModeEnabled Whether shuffling of windows is enabled.
     */
    default void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {}

    /**
     * Called when an error occurs. The playback state will transition to {@link #STATE_IDLE}
     * immediately after this method is called. The player instance can still be used, and {@link
     * #release()} must still be called on the player should it no longer be required.
     *
     * @param error The error.
     */
    default void onPlayerError(ExoPlaybackException error) {}

    /**
     * Called when a position discontinuity occurs without a change to the timeline. A position
     * discontinuity occurs when the current window or period index changes (as a result of playback
     * transitioning from one period in the timeline to the next), or when the playback position
     * jumps within the period currently being played (as a result of a seek being performed, or
     * when the source introduces a discontinuity internally).
     *
     * <p>When a position discontinuity occurs as a result of a change to the timeline this method
     * is <em>not</em> called. {@link #onTimelineChanged(Timeline, Object, int)} is called in this
     * case.
     *
     * @param reason The {@link DiscontinuityReason} responsible for the discontinuity.
     */
    default void onPositionDiscontinuity(@DiscontinuityReason int reason) {}

    /**
     * Called when the current playback parameters change. The playback parameters may change due to
     * a call to {@link #setPlaybackParameters(PlaybackParameters)}, or the player itself may change
     * them (for example, if audio playback switches to passthrough mode, where speed adjustment is
     * no longer possible).
     *
     * @param playbackParameters The playback parameters.
     */
    default void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}

    /**
     * Called when all pending seek requests have been processed by the player. This is guaranteed
     * to happen after any necessary changes to the player state were reported to {@link
     * #onPlayerStateChanged(boolean, int)}.
     */
    default void onSeekProcessed() {}
  }

  /**
   * @deprecated Use {@link EventListener} interface directly for selective overrides as all methods
   *     are implemented as no-op default methods.
   */
  @Deprecated
  abstract class DefaultEventListener implements EventListener {

    @Override
    @SuppressWarnings("deprecation")
    public void onTimelineChanged(
        Timeline timeline, @Nullable Object manifest, @TimelineChangeReason int reason) {
      // Call deprecated version. Otherwise, do nothing.
      onTimelineChanged(timeline, manifest);
    }

    /** @deprecated Use {@link EventListener#onTimelineChanged(Timeline, Object, int)} instead. */
    @Deprecated
    public void onTimelineChanged(Timeline timeline, @Nullable Object manifest) {
      // Do nothing.
    }
  }

  /**
   * The player does not have any media to play.
   */
  int STATE_IDLE = 1;
  /**
   * The player is not able to immediately play from its current position. This state typically
   * occurs when more data needs to be loaded.
   */
  int STATE_BUFFERING = 2;
  /**
   * The player is able to immediately play from its current position. The player will be playing if
   * {@link #getPlayWhenReady()} is true, and paused otherwise.
   */
  int STATE_READY = 3;
  /**
   * The player has finished playing the media.
   */
  int STATE_ENDED = 4;

  /**
   * Reason why playback is suppressed even though {@link #getPlayWhenReady()} is {@code true}. One
   * of {@link #PLAYBACK_SUPPRESSION_REASON_NONE} or {@link
   * #PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PLAYBACK_SUPPRESSION_REASON_NONE,
    PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
  })
  @interface PlaybackSuppressionReason {}
  /** Playback is not suppressed. */
  int PLAYBACK_SUPPRESSION_REASON_NONE = 0;
  /** Playback is suppressed due to transient audio focus loss. */
  int PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS = 1;

  /**
   * Repeat modes for playback. One of {@link #REPEAT_MODE_OFF}, {@link #REPEAT_MODE_ONE} or {@link
   * #REPEAT_MODE_ALL}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL})
  @interface RepeatMode {}
  /**
   * Normal playback without repetition.
   */
  int REPEAT_MODE_OFF = 0;
  /**
   * "Repeat One" mode to repeat the currently playing window infinitely.
   */
  int REPEAT_MODE_ONE = 1;
  /**
   * "Repeat All" mode to repeat the entire timeline infinitely.
   */
  int REPEAT_MODE_ALL = 2;

  /**
   * Reasons for position discontinuities. One of {@link #DISCONTINUITY_REASON_PERIOD_TRANSITION},
   * {@link #DISCONTINUITY_REASON_SEEK}, {@link #DISCONTINUITY_REASON_SEEK_ADJUSTMENT}, {@link
   * #DISCONTINUITY_REASON_AD_INSERTION} or {@link #DISCONTINUITY_REASON_INTERNAL}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    DISCONTINUITY_REASON_PERIOD_TRANSITION,
    DISCONTINUITY_REASON_SEEK,
    DISCONTINUITY_REASON_SEEK_ADJUSTMENT,
    DISCONTINUITY_REASON_AD_INSERTION,
    DISCONTINUITY_REASON_INTERNAL
  })
  @interface DiscontinuityReason {}
  /**
   * Automatic playback transition from one period in the timeline to the next. The period index may
   * be the same as it was before the discontinuity in case the current period is repeated.
   */
  int DISCONTINUITY_REASON_PERIOD_TRANSITION = 0;
  /** Seek within the current period or to another period. */
  int DISCONTINUITY_REASON_SEEK = 1;
  /**
   * Seek adjustment due to being unable to seek to the requested position or because the seek was
   * permitted to be inexact.
   */
  int DISCONTINUITY_REASON_SEEK_ADJUSTMENT = 2;
  /** Discontinuity to or from an ad within one period in the timeline. */
  int DISCONTINUITY_REASON_AD_INSERTION = 3;
  /** Discontinuity introduced internally by the source. */
  int DISCONTINUITY_REASON_INTERNAL = 4;

  /**
   * Reasons for timeline and/or manifest changes. One of {@link #TIMELINE_CHANGE_REASON_PREPARED},
   * {@link #TIMELINE_CHANGE_REASON_RESET} or {@link #TIMELINE_CHANGE_REASON_DYNAMIC}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TIMELINE_CHANGE_REASON_PREPARED,
    TIMELINE_CHANGE_REASON_RESET,
    TIMELINE_CHANGE_REASON_DYNAMIC
  })
  @interface TimelineChangeReason {}
  /**
   * Timeline and manifest changed as a result of a player initialization with new media.
   */
  int TIMELINE_CHANGE_REASON_PREPARED = 0;
  /**
   * Timeline and manifest changed as a result of a player reset.
   */
  int TIMELINE_CHANGE_REASON_RESET = 1;
  /**
   * Timeline or manifest changed as a result of an dynamic update introduced by the played media.
   */
  int TIMELINE_CHANGE_REASON_DYNAMIC = 2;

  /** Returns the component of this player for audio output, or null if audio is not supported. */
  @Nullable
  AudioComponent getAudioComponent();

  /** Returns the component of this player for video output, or null if video is not supported. */
  @Nullable
  VideoComponent getVideoComponent();

  /** Returns the component of this player for text output, or null if text is not supported. */
  @Nullable
  TextComponent getTextComponent();

  /**
   * Returns the component of this player for metadata output, or null if metadata is not supported.
   */
  @Nullable
  MetadataComponent getMetadataComponent();

  /**
   * Returns the {@link Looper} associated with the application thread that's used to access the
   * player and on which player events are received.
   */
  Looper getApplicationLooper();

  /**
   * Register a listener to receive events from the player. The listener's methods will be called on
   * the thread that was used to construct the player. However, if the thread used to construct the
   * player does not have a {@link Looper}, then the listener will be called on the main thread.
   *
   * @param listener The listener to register.
   */
  void addListener(EventListener listener);

  /**
   * Unregister a listener. The listener will no longer receive events from the player.
   *
   * @param listener The listener to unregister.
   */
  void removeListener(EventListener listener);

  /**
   * Returns the current state of the player.
   *
   * @return One of the {@code STATE} constants defined in this interface.
   */
  int getPlaybackState();

  /**
   * Returns the reason why playback is suppressed even though {@link #getPlayWhenReady()} is {@code
   * true}, or {@link #PLAYBACK_SUPPRESSION_REASON_NONE} if playback is not suppressed.
   *
   * @return The current {@link PlaybackSuppressionReason playback suppression reason}.
   */
  @PlaybackSuppressionReason
  int getPlaybackSuppressionReason();

  /**
   * Returns whether the player is playing, i.e. {@link #getContentPosition()} is advancing.
   *
   * <p>If {@code false}, then at least one of the following is true:
   *
   * <ul>
   *   <li>The {@link #getPlaybackState() playback state} is not {@link #STATE_READY ready}.
   *   <li>There is no {@link #getPlayWhenReady() intention to play}.
   *   <li>Playback is {@link #getPlaybackSuppressionReason() suppressed for other reasons}.
   * </ul>
   *
   * @return Whether the player is playing.
   */
  boolean isPlaying();

  /**
   * Returns the error that caused playback to fail. This is the same error that will have been
   * reported via {@link Player.EventListener#onPlayerError(ExoPlaybackException)} at the time of
   * failure. It can be queried using this method until {@code stop(true)} is called or the player
   * is re-prepared.
   *
   * <p>Note that this method will always return {@code null} if {@link #getPlaybackState()} is not
   * {@link #STATE_IDLE}.
   *
   * @return The error, or {@code null}.
   */
  @Nullable
  ExoPlaybackException getPlaybackError();

  /**
   * Sets whether playback should proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   * <p>
   * If the player is already in the ready state then this method can be used to pause and resume
   * playback.
   *
   * @param playWhenReady Whether playback should proceed when ready.
   */
  void setPlayWhenReady(boolean playWhenReady);

  /**
   * Whether playback will proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   *
   * @return Whether playback will proceed when ready.
   */
  boolean getPlayWhenReady();

  /**
   * Sets the {@link RepeatMode} to be used for playback.
   *
   * @param repeatMode A repeat mode.
   */
  void setRepeatMode(@RepeatMode int repeatMode);

  /**
   * Returns the current {@link RepeatMode} used for playback.
   *
   * @return The current repeat mode.
   */
  @RepeatMode int getRepeatMode();

  /**
   * Sets whether shuffling of windows is enabled.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   */
  void setShuffleModeEnabled(boolean shuffleModeEnabled);

  /**
   * Returns whether shuffling of windows is enabled.
   */
  boolean getShuffleModeEnabled();

  /**
   * Whether the player is currently loading the source.
   *
   * @return Whether the player is currently loading the source.
   */
  boolean isLoading();

  /**
   * Seeks to the default position associated with the current window. The position can depend on
   * the type of media being played. For live streams it will typically be the live edge of the
   * window. For other streams it will typically be the start of the window.
   */
  void seekToDefaultPosition();

  /**
   * Seeks to the default position associated with the specified window. The position can depend on
   * the type of media being played. For live streams it will typically be the live edge of the
   * window. For other streams it will typically be the start of the window.
   *
   * @param windowIndex The index of the window whose associated default position should be seeked
   *     to.
   */
  void seekToDefaultPosition(int windowIndex);

  /**
   * Seeks to a position specified in milliseconds in the current window.
   *
   * @param positionMs The seek position in the current window, or {@link C#TIME_UNSET} to seek to
   *     the window's default position.
   */
  void seekTo(long positionMs);

  /**
   * Seeks to a position specified in milliseconds in the specified window.
   *
   * @param windowIndex The index of the window.
   * @param positionMs The seek position in the specified window, or {@link C#TIME_UNSET} to seek to
   *     the window's default position.
   * @throws IllegalSeekPositionException If the player has a non-empty timeline and the provided
   *     {@code windowIndex} is not within the bounds of the current timeline.
   */
  void seekTo(int windowIndex, long positionMs);

  /**
   * Returns whether a previous window exists, which may depend on the current repeat mode and
   * whether shuffle mode is enabled.
   */
  boolean hasPrevious();

  /**
   * Seeks to the default position of the previous window in the timeline, which may depend on the
   * current repeat mode and whether shuffle mode is enabled. Does nothing if {@link #hasPrevious()}
   * is {@code false}.
   */
  void previous();

  /**
   * Returns whether a next window exists, which may depend on the current repeat mode and whether
   * shuffle mode is enabled.
   */
  boolean hasNext();

  /**
   * Seeks to the default position of the next window in the timeline, which may depend on the
   * current repeat mode and whether shuffle mode is enabled. Does nothing if {@link #hasNext()} is
   * {@code false}.
   */
  void next();

  /**
   * Attempts to set the playback parameters. Passing {@code null} sets the parameters to the
   * default, {@link PlaybackParameters#DEFAULT}, which means there is no speed or pitch adjustment.
   * <p>
   * Playback parameters changes may cause the player to buffer.
   * {@link EventListener#onPlaybackParametersChanged(PlaybackParameters)} will be called whenever
   * the currently active playback parameters change. When that listener is called, the parameters
   * passed to it may not match {@code playbackParameters}. For example, the chosen speed or pitch
   * may be out of range, in which case they are constrained to a set of permitted values. If it is
   * not possible to change the playback parameters, the listener will not be invoked.
   *
   * @param playbackParameters The playback parameters, or {@code null} to use the defaults.
   */
  void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters);

  /**
   * Returns the currently active playback parameters.
   *
   * @see EventListener#onPlaybackParametersChanged(PlaybackParameters)
   */
  PlaybackParameters getPlaybackParameters();

  /**
   * Stops playback without resetting the player. Use {@code setPlayWhenReady(false)} rather than
   * this method if the intention is to pause playback.
   *
   * <p>Calling this method will cause the playback state to transition to {@link #STATE_IDLE}. The
   * player instance can still be used, and {@link #release()} must still be called on the player if
   * it's no longer required.
   *
   * <p>Calling this method does not reset the playback position.
   */
  void stop();

  /**
   * Stops playback and optionally resets the player. Use {@code setPlayWhenReady(false)} rather
   * than this method if the intention is to pause playback.
   *
   * <p>Calling this method will cause the playback state to transition to {@link #STATE_IDLE}. The
   * player instance can still be used, and {@link #release()} must still be called on the player if
   * it's no longer required.
   *
   * @param reset Whether the player should be reset.
   */
  void stop(boolean reset);

  /**
   * Releases the player. This method must be called when the player is no longer required. The
   * player must not be used after calling this method.
   */
  void release();

  /**
   * Returns the number of renderers.
   */
  int getRendererCount();

  /**
   * Returns the track type that the renderer at a given index handles.
   *
   * @see Renderer#getTrackType()
   * @param index The index of the renderer.
   * @return One of the {@code TRACK_TYPE_*} constants defined in {@link C}.
   */
  int getRendererType(int index);

  /**
   * Returns the available track groups.
   */
  TrackGroupArray getCurrentTrackGroups();

  /**
   * Returns the current track selections for each renderer.
   */
  TrackSelectionArray getCurrentTrackSelections();

  /**
   * Returns the current manifest. The type depends on the type of media being played. May be null.
   */
  @Nullable Object getCurrentManifest();

  /**
   * Returns the current {@link Timeline}. Never null, but may be empty.
   */
  Timeline getCurrentTimeline();

  /**
   * Returns the index of the period currently being played.
   */
  int getCurrentPeriodIndex();

  /**
   * Returns the index of the window currently being played.
   */
  int getCurrentWindowIndex();

  /**
   * Returns the index of the next timeline window to be played, which may depend on the current
   * repeat mode and whether shuffle mode is enabled. Returns {@link C#INDEX_UNSET} if the window
   * currently being played is the last window.
   */
  int getNextWindowIndex();

  /**
   * Returns the index of the previous timeline window to be played, which may depend on the current
   * repeat mode and whether shuffle mode is enabled. Returns {@link C#INDEX_UNSET} if the window
   * currently being played is the first window.
   */
  int getPreviousWindowIndex();

  /**
   * Returns the tag of the currently playing window in the timeline. May be null if no tag is set
   * or the timeline is not yet available.
   */
  @Nullable Object getCurrentTag();

  /**
   * Returns the duration of the current content window or ad in milliseconds, or {@link
   * C#TIME_UNSET} if the duration is not known.
   */
  long getDuration();

  /** Returns the playback position in the current content window or ad, in milliseconds. */
  long getCurrentPosition();

  /**
   * Returns an estimate of the position in the current content window or ad up to which data is
   * buffered, in milliseconds.
   */
  long getBufferedPosition();

  /**
   * Returns an estimate of the percentage in the current content window or ad up to which data is
   * buffered, or 0 if no estimate is available.
   */
  int getBufferedPercentage();

  /**
   * Returns an estimate of the total buffered duration from the current position, in milliseconds.
   * This includes pre-buffered data for subsequent ads and windows.
   */
  long getTotalBufferedDuration();

  /**
   * Returns whether the current window is dynamic, or {@code false} if the {@link Timeline} is
   * empty.
   *
   * @see Timeline.Window#isDynamic
   */
  boolean isCurrentWindowDynamic();

  /**
   * Returns whether the current window is seekable, or {@code false} if the {@link Timeline} is
   * empty.
   *
   * @see Timeline.Window#isSeekable
   */
  boolean isCurrentWindowSeekable();

  /**
   * Returns whether the player is currently playing an ad.
   */
  boolean isPlayingAd();

  /**
   * If {@link #isPlayingAd()} returns true, returns the index of the ad group in the period
   * currently being played. Returns {@link C#INDEX_UNSET} otherwise.
   */
  int getCurrentAdGroupIndex();

  /**
   * If {@link #isPlayingAd()} returns true, returns the index of the ad in its ad group. Returns
   * {@link C#INDEX_UNSET} otherwise.
   */
  int getCurrentAdIndexInAdGroup();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns the duration of the current content
   * window in milliseconds, or {@link C#TIME_UNSET} if the duration is not known. If there is no ad
   * playing, the returned duration is the same as that returned by {@link #getDuration()}.
   */
  long getContentDuration();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns the content position that will be
   * played once all ads in the ad group have finished playing, in milliseconds. If there is no ad
   * playing, the returned position is the same as that returned by {@link #getCurrentPosition()}.
   */
  long getContentPosition();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns an estimate of the content position in
   * the current content window up to which data is buffered, in milliseconds. If there is no ad
   * playing, the returned position is the same as that returned by {@link #getBufferedPosition()}.
   */
  long getContentBufferedPosition();
}
