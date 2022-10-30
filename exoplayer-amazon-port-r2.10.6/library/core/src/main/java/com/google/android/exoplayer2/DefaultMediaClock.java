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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.StandaloneMediaClock;

/**
 * Default {@link MediaClock} which uses a renderer media clock and falls back to a
 * {@link StandaloneMediaClock} if necessary.
 */
/* package */ final class DefaultMediaClock implements MediaClock {

  /**
   * Listener interface to be notified of changes to the active playback parameters.
   */
  public interface PlaybackParameterListener {

    /**
     * Called when the active playback parameters changed.
     *
     * @param newPlaybackParameters The newly active {@link PlaybackParameters}.
     */
    void onPlaybackParametersChanged(PlaybackParameters newPlaybackParameters);

  }

  private final StandaloneMediaClock standaloneMediaClock;
  private final PlaybackParameterListener listener;

  private @Nullable Renderer rendererClockSource;
  private @Nullable MediaClock rendererClock;

  /**
   * Creates a new instance with listener for playback parameter changes and a {@link Clock} to use
   * for the standalone clock implementation.
   *
   * @param listener A {@link PlaybackParameterListener} to listen for playback parameter
   *     changes.
   * @param clock A {@link Clock}.
   */
  public DefaultMediaClock(PlaybackParameterListener listener, Clock clock) {
    this.listener = listener;
    this.standaloneMediaClock = new StandaloneMediaClock(clock);
  }

  /**
   * Starts the standalone fallback clock.
   */
  public void start() {
    standaloneMediaClock.start();
  }

  /**
   * Stops the standalone fallback clock.
   */
  public void stop() {
    standaloneMediaClock.stop();
  }

  /**
   * Resets the position of the standalone fallback clock.
   *
   * @param positionUs The position to set in microseconds.
   */
  public void resetPosition(long positionUs) {
    standaloneMediaClock.resetPosition(positionUs);
  }

  /**
   * Notifies the media clock that a renderer has been enabled. Starts using the media clock of the
   * provided renderer if available.
   *
   * @param renderer The renderer which has been enabled.
   * @throws ExoPlaybackException If the renderer provides a media clock and another renderer media
   *     clock is already provided.
   */
  public void onRendererEnabled(Renderer renderer) throws ExoPlaybackException {
    MediaClock rendererMediaClock = renderer.getMediaClock();
    if (rendererMediaClock != null && rendererMediaClock != rendererClock) {
      if (rendererClock != null) {
        throw ExoPlaybackException.createForUnexpected(
            new IllegalStateException("Multiple renderer media clocks enabled."));
      }
      this.rendererClock = rendererMediaClock;
      this.rendererClockSource = renderer;
      rendererClock.setPlaybackParameters(standaloneMediaClock.getPlaybackParameters());
      ensureSynced();
    }
  }

  /**
   * Notifies the media clock that a renderer has been disabled. Stops using the media clock of this
   * renderer if used.
   *
   * @param renderer The renderer which has been disabled.
   */
  public void onRendererDisabled(Renderer renderer) {
    if (renderer == rendererClockSource) {
      this.rendererClock = null;
      this.rendererClockSource = null;
    }
  }

  /**
   * Syncs internal clock if needed and returns current clock position in microseconds.
   */
  public long syncAndGetPositionUs() {
    if (isUsingRendererClock()) {
      ensureSynced();
      return rendererClock.getPositionUs();
    } else {
      return standaloneMediaClock.getPositionUs();
    }
  }

  // MediaClock implementation.

  @Override
  public long getPositionUs() {
    if (isUsingRendererClock()) {
      return rendererClock.getPositionUs();
    } else {
      return standaloneMediaClock.getPositionUs();
    }
  }

  @Override
  public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
    if (rendererClock != null) {
      playbackParameters = rendererClock.setPlaybackParameters(playbackParameters);
    }
    standaloneMediaClock.setPlaybackParameters(playbackParameters);
    listener.onPlaybackParametersChanged(playbackParameters);
    return playbackParameters;
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return rendererClock != null ? rendererClock.getPlaybackParameters()
        : standaloneMediaClock.getPlaybackParameters();
  }

  private void ensureSynced() {
    long rendererClockPositionUs = rendererClock.getPositionUs();
    standaloneMediaClock.resetPosition(rendererClockPositionUs);
    PlaybackParameters playbackParameters = rendererClock.getPlaybackParameters();
    if (!playbackParameters.equals(standaloneMediaClock.getPlaybackParameters())) {
      standaloneMediaClock.setPlaybackParameters(playbackParameters);
      listener.onPlaybackParametersChanged(playbackParameters);
    }
  }

  private boolean isUsingRendererClock() {
    // Use the renderer clock if the providing renderer has not ended or needs the next sample
    // stream to reenter the ready state. The latter case uses the standalone clock to avoid getting
    // stuck if tracks in the current period have uneven durations.
    // See: https://github.com/google/ExoPlayer/issues/1874.
    return rendererClockSource != null && !rendererClockSource.isEnded()
        && (rendererClockSource.isReady() || !rendererClockSource.hasReadStreamToEnd());
  }

}
