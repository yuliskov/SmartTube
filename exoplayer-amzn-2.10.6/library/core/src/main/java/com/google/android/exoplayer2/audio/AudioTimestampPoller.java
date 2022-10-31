/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Polls the {@link AudioTrack} timestamp, if the platform supports it, taking care of polling at
 * the appropriate rate to detect when the timestamp starts to advance.
 *
 * <p>When the audio track isn't paused, call {@link #maybePollTimestamp(long)} regularly to check
 * for timestamp updates. If it returns {@code true}, call {@link #getTimestampPositionFrames()} and
 * {@link #getTimestampSystemTimeUs()} to access the updated timestamp, then call {@link
 * #acceptTimestamp()} or {@link #rejectTimestamp()} to accept or reject it.
 *
 * <p>If {@link #hasTimestamp()} returns {@code true}, call {@link #getTimestampSystemTimeUs()} to
 * get the system time at which the latest timestamp was sampled and {@link
 * #getTimestampPositionFrames()} to get its position in frames. If {@link #isTimestampAdvancing()}
 * returns {@code true}, the caller should assume that the timestamp has been increasing in real
 * time since it was sampled. Otherwise, it may be stationary.
 *
 * <p>Call {@link #reset()} when pausing or resuming the track.
 */
/* package */ final class AudioTimestampPoller {

  /** Timestamp polling states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_INITIALIZING,
    STATE_TIMESTAMP,
    STATE_TIMESTAMP_ADVANCING,
    STATE_NO_TIMESTAMP,
    STATE_ERROR
  })
  private @interface State {}
  /** State when first initializing. */
  private static final int STATE_INITIALIZING = 0;
  /** State when we have a timestamp and we don't know if it's advancing. */
  private static final int STATE_TIMESTAMP = 1;
  /** State when we have a timestamp and we know it is advancing. */
  private static final int STATE_TIMESTAMP_ADVANCING = 2;
  /** State when the no timestamp is available. */
  private static final int STATE_NO_TIMESTAMP = 3;
  /** State when the last timestamp was rejected as invalid. */
  private static final int STATE_ERROR = 4;

  /** The polling interval for {@link #STATE_INITIALIZING} and {@link #STATE_TIMESTAMP}. */
  private static final int FAST_POLL_INTERVAL_US = 5_000;
  /**
   * The polling interval for {@link #STATE_TIMESTAMP_ADVANCING} and {@link #STATE_NO_TIMESTAMP}.
   */
  private static final int SLOW_POLL_INTERVAL_US = 10_000_000;
  /** The polling interval for {@link #STATE_ERROR}. */
  private static final int ERROR_POLL_INTERVAL_US = 500_000;

  /**
   * The minimum duration to remain in {@link #STATE_INITIALIZING} if no timestamps are being
   * returned before transitioning to {@link #STATE_NO_TIMESTAMP}.
   */
  private static final int INITIALIZING_DURATION_US = 500_000;

  private final @Nullable AudioTimestampV19 audioTimestamp;

  private @State int state;
  private long initializeSystemTimeUs;
  private long sampleIntervalUs;
  private long lastTimestampSampleTimeUs;
  private long initialTimestampPositionFrames;

  /**
   * Creates a new audio timestamp poller.
   *
   * @param audioTrack The audio track that will provide timestamps, if the platform supports it.
   */
  public AudioTimestampPoller(AudioTrack audioTrack) {
    if (Util.SDK_INT >= 19) {
      audioTimestamp = new AudioTimestampV19(audioTrack);
      reset();
    } else {
      audioTimestamp = null;
      updateState(STATE_NO_TIMESTAMP);
    }
  }

  // AMZN_CHANGE_BEGIN
  public boolean maybePollTimestamp(long systemTimeUs) {
    return maybePollTimestamp(systemTimeUs, false);
  }
  /**
   * Polls the timestamp if required and returns whether it was updated. If {@code true}, the latest
   * timestamp is available via {@link #getTimestampSystemTimeUs()} and {@link
   * #getTimestampPositionFrames()}, and the caller should call {@link #acceptTimestamp()} if the
   * timestamp was valid, or {@link #rejectTimestamp()} otherwise. The values returned by {@link
   * #hasTimestamp()} and {@link #isTimestampAdvancing()} may be updated.
   *
   * @param systemTimeUs The current system time, in microseconds.
   * @return Whether the timestamp was updated.
   */
  public boolean maybePollTimestamp(long systemTimeUs, boolean applyDolbyPassthroughQuirk) {
    if (!applyDolbyPassthroughQuirk
            && (audioTimestamp == null
                || (systemTimeUs - lastTimestampSampleTimeUs) < sampleIntervalUs)) {
      return false;
    }
    // AMZN_CHANGE_END
    lastTimestampSampleTimeUs = systemTimeUs;
    boolean updatedTimestamp = audioTimestamp.maybeUpdateTimestamp();
    switch (state) {
      case STATE_INITIALIZING:
        if (updatedTimestamp) {
          if (audioTimestamp.getTimestampSystemTimeUs() >= initializeSystemTimeUs
                  || applyDolbyPassthroughQuirk) {// AMZN_CHANGE_ONELINE
            // We have an initial timestamp, but don't know if it's advancing yet.
            initialTimestampPositionFrames = audioTimestamp.getTimestampPositionFrames();
            updateState(STATE_TIMESTAMP);
          } else {
            // Drop the timestamp, as it was sampled before the last reset.
            updatedTimestamp = false;
          }
        } else if (systemTimeUs - initializeSystemTimeUs > INITIALIZING_DURATION_US) {
          // We haven't received a timestamp for a while, so they probably aren't available for the
          // current audio route. Poll infrequently in case the route changes later.
          // TODO: Ideally we should listen for audio route changes in order to detect when a
          // timestamp becomes available again.
          updateState(STATE_NO_TIMESTAMP);
        }
        break;
      case STATE_TIMESTAMP:
        if (updatedTimestamp) {
          long timestampPositionFrames = audioTimestamp.getTimestampPositionFrames();
          if (timestampPositionFrames > initialTimestampPositionFrames) {
            updateState(STATE_TIMESTAMP_ADVANCING);
          }
        } else {
          reset();
        }
        break;
      case STATE_TIMESTAMP_ADVANCING:
        if (!updatedTimestamp) {
          // The audio route may have changed, so reset polling.
          reset();
        }
        break;
      case STATE_NO_TIMESTAMP:
        if (updatedTimestamp) {
          // The audio route may have changed, so reset polling.
          reset();
        }
        break;
      case STATE_ERROR:
        // Do nothing. If the caller accepts any new timestamp we'll reset polling.
        break;
      default:
        throw new IllegalStateException();
    }
    return updatedTimestamp;
  }

  /**
   * Rejects the timestamp last polled in {@link #maybePollTimestamp(long)}. The instance will enter
   * the error state and poll timestamps infrequently until the next call to {@link
   * #acceptTimestamp()}.
   */
  public void rejectTimestamp() {
    updateState(STATE_ERROR);
  }

  /**
   * Accepts the timestamp last polled in {@link #maybePollTimestamp(long)}. If the instance is in
   * the error state, it will begin to poll timestamps frequently again.
   */
  public void acceptTimestamp() {
    if (state == STATE_ERROR) {
      reset();
    }
  }

  /**
   * Returns whether this instance has a timestamp that can be used to calculate the audio track
   * position. If {@code true}, call {@link #getTimestampSystemTimeUs()} and {@link
   * #getTimestampSystemTimeUs()} to access the timestamp.
   */
  public boolean hasTimestamp() {
    return state == STATE_TIMESTAMP || state == STATE_TIMESTAMP_ADVANCING;
  }

  /**
   * Returns whether the timestamp appears to be advancing. If {@code true}, call {@link
   * #getTimestampSystemTimeUs()} and {@link #getTimestampSystemTimeUs()} to access the timestamp. A
   * current position for the track can be extrapolated based on elapsed real time since the system
   * time at which the timestamp was sampled.
   */
  public boolean isTimestampAdvancing() {
    return state == STATE_TIMESTAMP_ADVANCING;
  }

  /** Resets polling. Should be called whenever the audio track is paused or resumed. */
  public void reset() {
    if (audioTimestamp != null) {
      updateState(STATE_INITIALIZING);
    }
  }

  /**
   * If {@link #maybePollTimestamp(long)} or {@link #hasTimestamp()} returned {@code true}, returns
   * the system time at which the latest timestamp was sampled, in microseconds.
   */
  public long getTimestampSystemTimeUs() {
    return audioTimestamp != null ? audioTimestamp.getTimestampSystemTimeUs() : C.TIME_UNSET;
  }

  /**
   * If {@link #maybePollTimestamp(long)} or {@link #hasTimestamp()} returned {@code true}, returns
   * the latest timestamp's position in frames.
   */
  public long getTimestampPositionFrames() {
    return audioTimestamp != null ? audioTimestamp.getTimestampPositionFrames() : C.POSITION_UNSET;
  }

  private void updateState(@State int state) {
    this.state = state;
    switch (state) {
      case STATE_INITIALIZING:
        // Force polling a timestamp immediately, and poll quickly.
        lastTimestampSampleTimeUs = 0;
        initialTimestampPositionFrames = C.POSITION_UNSET;
        initializeSystemTimeUs = System.nanoTime() / 1000;
        sampleIntervalUs = FAST_POLL_INTERVAL_US;
        break;
      case STATE_TIMESTAMP:
        sampleIntervalUs = FAST_POLL_INTERVAL_US;
        break;
      case STATE_TIMESTAMP_ADVANCING:
      case STATE_NO_TIMESTAMP:
        sampleIntervalUs = SLOW_POLL_INTERVAL_US;
        break;
      case STATE_ERROR:
        sampleIntervalUs = ERROR_POLL_INTERVAL_US;
        break;
      default:
        throw new IllegalStateException();
    }
  }

  @TargetApi(19)
  private static final class AudioTimestampV19 {

    private final AudioTrack audioTrack;
    private final AudioTimestamp audioTimestamp;

    private long rawTimestampFramePositionWrapCount;
    private long lastTimestampRawPositionFrames;
    private long lastTimestampPositionFrames;

    /**
     * Creates a new {@link AudioTimestamp} wrapper.
     *
     * @param audioTrack The audio track that will provide timestamps.
     */
    public AudioTimestampV19(AudioTrack audioTrack) {
      this.audioTrack = audioTrack;
      audioTimestamp = new AudioTimestamp();
    }

    /**
     * Attempts to update the audio track timestamp. Returns {@code true} if the timestamp was
     * updated, in which case the updated timestamp system time and position can be accessed with
     * {@link #getTimestampSystemTimeUs()} and {@link #getTimestampPositionFrames()}. Returns {@code
     * false} if no timestamp is available, in which case those methods should not be called.
     */
    public boolean maybeUpdateTimestamp() {
      boolean updated = audioTrack.getTimestamp(audioTimestamp);
      if (updated) {
        long rawPositionFrames = audioTimestamp.framePosition;
        if (lastTimestampRawPositionFrames > rawPositionFrames) {
          // The value must have wrapped around.
          rawTimestampFramePositionWrapCount++;
        }
        lastTimestampRawPositionFrames = rawPositionFrames;
        lastTimestampPositionFrames =
            rawPositionFrames + (rawTimestampFramePositionWrapCount << 32);
      }
      return updated;
    }

    public long getTimestampSystemTimeUs() {
      return audioTimestamp.nanoTime / 1000;
    }

    public long getTimestampPositionFrames() {
      return lastTimestampPositionFrames;
    }
  }
}
