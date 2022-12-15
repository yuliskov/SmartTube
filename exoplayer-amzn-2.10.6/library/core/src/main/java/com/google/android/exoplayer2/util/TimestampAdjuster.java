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
package com.google.android.exoplayer2.util;

import com.google.android.exoplayer2.C;

/**
 * Offsets timestamps according to an initial sample timestamp offset. MPEG-2 TS timestamps scaling
 * and adjustment is supported, taking into account timestamp rollover.
 */
public final class TimestampAdjuster {

  /**
   * A special {@code firstSampleTimestampUs} value indicating that presentation timestamps should
   * not be offset.
   */
  public static final long DO_NOT_OFFSET = Long.MAX_VALUE;

  /**
   * The value one greater than the largest representable (33 bit) MPEG-2 TS 90 kHz clock
   * presentation timestamp.
   */
  private static final long MAX_PTS_PLUS_ONE = 0x200000000L;

  private long firstSampleTimestampUs;
  private long timestampOffsetUs;

  // Volatile to allow isInitialized to be called on a different thread to adjustSampleTimestamp.
  private volatile long lastSampleTimestampUs;

  /**
   * @param firstSampleTimestampUs See {@link #setFirstSampleTimestampUs(long)}.
   */
  public TimestampAdjuster(long firstSampleTimestampUs) {
    lastSampleTimestampUs = C.TIME_UNSET;
    setFirstSampleTimestampUs(firstSampleTimestampUs);
  }

  /**
   * Sets the desired result of the first call to {@link #adjustSampleTimestamp(long)}. Can only be
   * called before any timestamps have been adjusted.
   *
   * @param firstSampleTimestampUs The first adjusted sample timestamp in microseconds, or
   *     {@link #DO_NOT_OFFSET} if presentation timestamps should not be offset.
   */
  public synchronized void setFirstSampleTimestampUs(long firstSampleTimestampUs) {
    Assertions.checkState(lastSampleTimestampUs == C.TIME_UNSET);
    this.firstSampleTimestampUs = firstSampleTimestampUs;
  }

  /** Returns the last value passed to {@link #setFirstSampleTimestampUs(long)}. */
  public long getFirstSampleTimestampUs() {
    return firstSampleTimestampUs;
  }

  /**
   * Returns the last value obtained from {@link #adjustSampleTimestamp}. If {@link
   * #adjustSampleTimestamp} has not been called, returns the result of calling {@link
   * #getFirstSampleTimestampUs()}. If this value is {@link #DO_NOT_OFFSET}, returns {@link
   * C#TIME_UNSET}.
   */
  public long getLastAdjustedTimestampUs() {
    return lastSampleTimestampUs != C.TIME_UNSET
        ? (lastSampleTimestampUs + timestampOffsetUs)
        : firstSampleTimestampUs != DO_NOT_OFFSET ? firstSampleTimestampUs : C.TIME_UNSET;
  }

  /**
   * Returns the offset between the input of {@link #adjustSampleTimestamp(long)} and its output.
   * If {@link #DO_NOT_OFFSET} was provided to the constructor, 0 is returned. If the timestamp
   * adjuster is yet not initialized, {@link C#TIME_UNSET} is returned.
   *
   * @return The offset between {@link #adjustSampleTimestamp(long)}'s input and output.
   *     {@link C#TIME_UNSET} if the adjuster is not yet initialized and 0 if timestamps should not
   *     be offset.
   */
  public long getTimestampOffsetUs() {
    return firstSampleTimestampUs == DO_NOT_OFFSET
        ? 0
        : lastSampleTimestampUs == C.TIME_UNSET ? C.TIME_UNSET : timestampOffsetUs;
  }

  /**
   * Resets the instance to its initial state.
   */
  public void reset() {
    lastSampleTimestampUs = C.TIME_UNSET;
  }

  /**
   * Scales and offsets an MPEG-2 TS presentation timestamp considering wraparound.
   *
   * @param pts90Khz A 90 kHz clock MPEG-2 TS presentation timestamp.
   * @return The adjusted timestamp in microseconds.
   */
  public long adjustTsTimestamp(long pts90Khz) {
    if (pts90Khz == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    if (lastSampleTimestampUs != C.TIME_UNSET) {
      // The wrap count for the current PTS may be closestWrapCount or (closestWrapCount - 1),
      // and we need to snap to the one closest to lastSampleTimestampUs.
      long lastPts = usToPts(lastSampleTimestampUs);
      long closestWrapCount = (lastPts + (MAX_PTS_PLUS_ONE / 2)) / MAX_PTS_PLUS_ONE;
      long ptsWrapBelow = pts90Khz + (MAX_PTS_PLUS_ONE * (closestWrapCount - 1));
      long ptsWrapAbove = pts90Khz + (MAX_PTS_PLUS_ONE * closestWrapCount);
      pts90Khz =
          Math.abs(ptsWrapBelow - lastPts) < Math.abs(ptsWrapAbove - lastPts)
              ? ptsWrapBelow
              : ptsWrapAbove;
    }
    return adjustSampleTimestamp(ptsToUs(pts90Khz));
  }

  /**
   * Offsets a timestamp in microseconds.
   *
   * @param timeUs The timestamp to adjust in microseconds.
   * @return The adjusted timestamp in microseconds.
   */
  public long adjustSampleTimestamp(long timeUs) {
    if (timeUs == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    // Record the adjusted PTS to adjust for wraparound next time.
    if (lastSampleTimestampUs != C.TIME_UNSET) {
      lastSampleTimestampUs = timeUs;
    } else {
      if (firstSampleTimestampUs != DO_NOT_OFFSET) {
        // Calculate the timestamp offset.
        timestampOffsetUs = firstSampleTimestampUs - timeUs;
      }
      synchronized (this) {
        lastSampleTimestampUs = timeUs;
        // Notify threads waiting for this adjuster to be initialized.
        notifyAll();
      }
    }
    return timeUs + timestampOffsetUs;
  }

  /**
   * Blocks the calling thread until this adjuster is initialized.
   *
   * @throws InterruptedException If the thread was interrupted.
   */
  public synchronized void waitUntilInitialized() throws InterruptedException {
    while (lastSampleTimestampUs == C.TIME_UNSET) {
      wait();
    }
  }

  /**
   * Converts a 90 kHz clock timestamp to a timestamp in microseconds.
   *
   * @param pts A 90 kHz clock timestamp.
   * @return The corresponding value in microseconds.
   */
  public static long ptsToUs(long pts) {
    return (pts * C.MICROS_PER_SECOND) / 90000;
  }

  /**
   * Converts a timestamp in microseconds to a 90 kHz clock timestamp.
   *
   * @param us A value in microseconds.
   * @return The corresponding value as a 90 kHz clock timestamp.
   */
  public static long usToPts(long us) {
    return (us * 90000) / C.MICROS_PER_SECOND;
  }

}
