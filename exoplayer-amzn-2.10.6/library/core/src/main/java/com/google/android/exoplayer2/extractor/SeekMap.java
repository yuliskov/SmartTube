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
package com.google.android.exoplayer2.extractor;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;

/**
 * Maps seek positions (in microseconds) to corresponding positions (byte offsets) in the stream.
 */
public interface SeekMap {

  /** A {@link SeekMap} that does not support seeking. */
  class Unseekable implements SeekMap {

    private final long durationUs;
    private final SeekPoints startSeekPoints;

    /**
     * @param durationUs The duration of the stream in microseconds, or {@link C#TIME_UNSET} if the
     *     duration is unknown.
     */
    public Unseekable(long durationUs) {
      this(durationUs, 0);
    }

    /**
     * @param durationUs The duration of the stream in microseconds, or {@link C#TIME_UNSET} if the
     *     duration is unknown.
     * @param startPosition The position (byte offset) of the start of the media.
     */
    public Unseekable(long durationUs, long startPosition) {
      this.durationUs = durationUs;
      startSeekPoints =
          new SeekPoints(startPosition == 0 ? SeekPoint.START : new SeekPoint(0, startPosition));
    }

    @Override
    public boolean isSeekable() {
      return false;
    }

    @Override
    public long getDurationUs() {
      return durationUs;
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      return startSeekPoints;
    }
  }

  /** Contains one or two {@link SeekPoint}s. */
  final class SeekPoints {

    /** The first seek point. */
    public final SeekPoint first;
    /** The second seek point, or {@link #first} if there's only one seek point. */
    public final SeekPoint second;

    /** @param point The single seek point. */
    public SeekPoints(SeekPoint point) {
      this(point, point);
    }

    /**
     * @param first The first seek point.
     * @param second The second seek point.
     */
    public SeekPoints(SeekPoint first, SeekPoint second) {
      this.first = Assertions.checkNotNull(first);
      this.second = Assertions.checkNotNull(second);
    }

    @Override
    public String toString() {
      return "[" + first + (first.equals(second) ? "" : (", " + second)) + "]";
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      SeekPoints other = (SeekPoints) obj;
      return first.equals(other.first) && second.equals(other.second);
    }

    @Override
    public int hashCode() {
      return (31 * first.hashCode()) + second.hashCode();
    }
  }

  /**
   * Returns whether seeking is supported.
   *
   * @return Whether seeking is supported.
   */
  boolean isSeekable();

  /**
   * Returns the duration of the stream in microseconds.
   *
   * @return The duration of the stream in microseconds, or {@link C#TIME_UNSET} if the duration is
   *     unknown.
   */
  long getDurationUs();

  /**
   * Obtains seek points for the specified seek time in microseconds. The returned {@link
   * SeekPoints} will contain one or two distinct seek points.
   *
   * <p>Two seek points [A, B] are returned in the case that seeking can only be performed to
   * discrete points in time, there does not exist a seek point at exactly the requested time, and
   * there exist seek points on both sides of it. In this case A and B are the closest seek points
   * before and after the requested time. A single seek point is returned in all other cases.
   *
   * @param timeUs A seek time in microseconds.
   * @return The corresponding seek points.
   */
  SeekPoints getSeekPoints(long timeUs);
}
