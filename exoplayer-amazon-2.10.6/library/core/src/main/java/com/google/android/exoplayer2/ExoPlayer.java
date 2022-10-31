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

import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;

/**
 * An extensible media player that plays {@link MediaSource}s. Instances can be obtained from {@link
 * ExoPlayerFactory}.
 *
 * <h3>Player components</h3>
 *
 * <p>ExoPlayer is designed to make few assumptions about (and hence impose few restrictions on) the
 * type of the media being played, how and where it is stored, and how it is rendered. Rather than
 * implementing the loading and rendering of media directly, ExoPlayer implementations delegate this
 * work to components that are injected when a player is created or when it's prepared for playback.
 * Components common to all ExoPlayer implementations are:
 *
 * <ul>
 *   <li>A <b>{@link MediaSource}</b> that defines the media to be played, loads the media, and from
 *       which the loaded media can be read. A MediaSource is injected via {@link
 *       #prepare(MediaSource)} at the start of playback. The library modules provide default
 *       implementations for progressive media files ({@link ProgressiveMediaSource}), DASH
 *       (DashMediaSource), SmoothStreaming (SsMediaSource) and HLS (HlsMediaSource), an
 *       implementation for loading single media samples ({@link SingleSampleMediaSource}) that's
 *       most often used for side-loaded subtitle files, and implementations for building more
 *       complex MediaSources from simpler ones ({@link MergingMediaSource}, {@link
 *       ConcatenatingMediaSource}, {@link LoopingMediaSource} and {@link ClippingMediaSource}).
 *   <li><b>{@link Renderer}</b>s that render individual components of the media. The library
 *       provides default implementations for common media types ({@link MediaCodecVideoRenderer},
 *       {@link MediaCodecAudioRenderer}, {@link TextRenderer} and {@link MetadataRenderer}). A
 *       Renderer consumes media from the MediaSource being played. Renderers are injected when the
 *       player is created.
 *   <li>A <b>{@link TrackSelector}</b> that selects tracks provided by the MediaSource to be
 *       consumed by each of the available Renderers. The library provides a default implementation
 *       ({@link DefaultTrackSelector}) suitable for most use cases. A TrackSelector is injected
 *       when the player is created.
 *   <li>A <b>{@link LoadControl}</b> that controls when the MediaSource buffers more media, and how
 *       much media is buffered. The library provides a default implementation ({@link
 *       DefaultLoadControl}) suitable for most use cases. A LoadControl is injected when the player
 *       is created.
 * </ul>
 *
 * <p>An ExoPlayer can be built using the default components provided by the library, but may also
 * be built using custom implementations if non-standard behaviors are required. For example a
 * custom LoadControl could be injected to change the player's buffering strategy, or a custom
 * Renderer could be injected to add support for a video codec not supported natively by Android.
 *
 * <p>The concept of injecting components that implement pieces of player functionality is present
 * throughout the library. The default component implementations listed above delegate work to
 * further injected components. This allows many sub-components to be individually replaced with
 * custom implementations. For example the default MediaSource implementations require one or more
 * {@link DataSource} factories to be injected via their constructors. By providing a custom factory
 * it's possible to load data from a non-standard source, or through a different network stack.
 *
 * <h3>Threading model</h3>
 *
 * <p>The figure below shows ExoPlayer's threading model.
 *
 * <p align="center"><img src="doc-files/exoplayer-threading-model.svg" alt="ExoPlayer's threading
 * model">
 *
 * <ul>
 *   <li>ExoPlayer instances must be accessed from a single application thread. For the vast
 *       majority of cases this should be the application's main thread. Using the application's
 *       main thread is also a requirement when using ExoPlayer's UI components or the IMA
 *       extension. The thread on which an ExoPlayer instance must be accessed can be explicitly
 *       specified by passing a `Looper` when creating the player. If no `Looper` is specified, then
 *       the `Looper` of the thread that the player is created on is used, or if that thread does
 *       not have a `Looper`, the `Looper` of the application's main thread is used. In all cases
 *       the `Looper` of the thread from which the player must be accessed can be queried using
 *       {@link #getApplicationLooper()}.
 *   <li>Registered listeners are called on the thread associated with {@link
 *       #getApplicationLooper()}. Note that this means registered listeners are called on the same
 *       thread which must be used to access the player.
 *   <li>An internal playback thread is responsible for playback. Injected player components such as
 *       Renderers, MediaSources, TrackSelectors and LoadControls are called by the player on this
 *       thread.
 *   <li>When the application performs an operation on the player, for example a seek, a message is
 *       delivered to the internal playback thread via a message queue. The internal playback thread
 *       consumes messages from the queue and performs the corresponding operations. Similarly, when
 *       a playback event occurs on the internal playback thread, a message is delivered to the
 *       application thread via a second message queue. The application thread consumes messages
 *       from the queue, updating the application visible state and calling corresponding listener
 *       methods.
 *   <li>Injected player components may use additional background threads. For example a MediaSource
 *       may use background threads to load data. These are implementation specific.
 * </ul>
 */
public interface ExoPlayer extends Player {

  /** @deprecated Use {@link PlayerMessage.Target} instead. */
  @Deprecated
  interface ExoPlayerComponent extends PlayerMessage.Target {}

  /** @deprecated Use {@link PlayerMessage} instead. */
  @Deprecated
  final class ExoPlayerMessage {

    /** The target to receive the message. */
    public final PlayerMessage.Target target;
    /** The type of the message. */
    public final int messageType;
    /** The message. */
    public final Object message;

    /** @deprecated Use {@link ExoPlayer#createMessage(PlayerMessage.Target)} instead. */
    @Deprecated
    public ExoPlayerMessage(PlayerMessage.Target target, int messageType, Object message) {
      this.target = target;
      this.messageType = messageType;
      this.message = message;
    }
  }

  /** Returns the {@link Looper} associated with the playback thread. */
  Looper getPlaybackLooper();

  /**
   * Retries a failed or stopped playback. Does nothing if the player has been reset, or if playback
   * has not failed or been stopped.
   */
  void retry();

  /**
   * Prepares the player to play the provided {@link MediaSource}. Equivalent to
   * {@code prepare(mediaSource, true, true)}.
   */
  void prepare(MediaSource mediaSource);

  /**
   * Prepares the player to play the provided {@link MediaSource}, optionally resetting the playback
   * position the default position in the first {@link Timeline.Window}.
   *
   * @param mediaSource The {@link MediaSource} to play.
   * @param resetPosition Whether the playback position should be reset to the default position in
   *     the first {@link Timeline.Window}. If false, playback will start from the position defined
   *     by {@link #getCurrentWindowIndex()} and {@link #getCurrentPosition()}.
   * @param resetState Whether the timeline, manifest, tracks and track selections should be reset.
   *     Should be true unless the player is being prepared to play the same media as it was playing
   *     previously (e.g. if playback failed and is being retried).
   */
  void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState);

  /**
   * Creates a message that can be sent to a {@link PlayerMessage.Target}. By default, the message
   * will be delivered immediately without blocking on the playback thread. The default {@link
   * PlayerMessage#getType()} is 0 and the default {@link PlayerMessage#getPayload()} is null. If a
   * position is specified with {@link PlayerMessage#setPosition(long)}, the message will be
   * delivered at this position in the current window defined by {@link #getCurrentWindowIndex()}.
   * Alternatively, the message can be sent at a specific window using {@link
   * PlayerMessage#setPosition(int, long)}.
   */
  PlayerMessage createMessage(PlayerMessage.Target target);

  /** @deprecated Use {@link #createMessage(PlayerMessage.Target)} instead. */
  @Deprecated
  @SuppressWarnings("deprecation")
  void sendMessages(ExoPlayerMessage... messages);

  /**
   * @deprecated Use {@link #createMessage(PlayerMessage.Target)} with {@link
   *     PlayerMessage#blockUntilDelivered()}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  void blockingSendMessages(ExoPlayerMessage... messages);

  /**
   * Sets the parameters that control how seek operations are performed.
   *
   * @param seekParameters The seek parameters, or {@code null} to use the defaults.
   */
  void setSeekParameters(@Nullable SeekParameters seekParameters);

  /** Returns the currently active {@link SeekParameters} of the player. */
  SeekParameters getSeekParameters();

  /**
   * Sets whether the player is allowed to keep holding limited resources such as video decoders,
   * even when in the idle state. By doing so, the player may be able to reduce latency when
   * starting to play another piece of content for which the same resources are required.
   *
   * <p>This mode should be used with caution, since holding limited resources may prevent other
   * players of media components from acquiring them. It should only be enabled when <em>both</em>
   * of the following conditions are true:
   *
   * <ul>
   *   <li>The application that owns the player is in the foreground.
   *   <li>The player is used in a way that may benefit from foreground mode. For this to be true,
   *       the same player instance must be used to play multiple pieces of content, and there must
   *       be gaps between the playbacks (i.e. {@link #stop} is called to halt one playback, and
   *       {@link #prepare} is called some time later to start a new one).
   * </ul>
   *
   * <p>Note that foreground mode is <em>not</em> useful for switching between content without gaps
   * between the playbacks. For this use case {@link #stop} does not need to be called, and simply
   * calling {@link #prepare} for the new media will cause limited resources to be retained even if
   * foreground mode is not enabled.
   *
   * <p>If foreground mode is enabled, it's the application's responsibility to disable it when the
   * conditions described above no longer hold.
   *
   * @param foregroundMode Whether the player is allowed to keep limited resources even when in the
   *     idle state.
   */
  void setForegroundMode(boolean foregroundMode);
}
