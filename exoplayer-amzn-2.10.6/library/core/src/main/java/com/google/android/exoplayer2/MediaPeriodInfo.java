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
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.util.Util;

/** Stores the information required to load and play a {@link MediaPeriod}. */
/* package */ final class MediaPeriodInfo {

  /** The media period's identifier. */
  public final MediaPeriodId id;
  /** The start position of the media to play within the media period, in microseconds. */
  public final long startPositionUs;
  /**
   * If this is an ad, the position to play in the next content media period. {@link C#TIME_UNSET}
   * if this is not an ad or the next content media period should be played from its default
   * position.
   */
  public final long contentPositionUs;
  /**
   * The end position to which the media period's content is clipped in order to play a following ad
   * group, in microseconds, or {@link C#TIME_UNSET} if there is no following ad group or if this
   * media period is an ad. The value {@link C#TIME_END_OF_SOURCE} indicates that a postroll ad
   * follows at the end of this content media period.
   */
  public final long endPositionUs;
  /**
   * The duration of the media period, like {@link #endPositionUs} but with {@link
   * C#TIME_END_OF_SOURCE} and {@link C#TIME_UNSET} resolved to the timeline period duration if
   * known.
   */
  public final long durationUs;
  /**
   * Whether this is the last media period in its timeline period (e.g., a postroll ad, or a media
   * period corresponding to a timeline period without ads).
   */
  public final boolean isLastInTimelinePeriod;
  /**
   * Whether this is the last media period in the entire timeline. If true, {@link
   * #isLastInTimelinePeriod} will also be true.
   */
  public final boolean isFinal;

  MediaPeriodInfo(
      MediaPeriodId id,
      long startPositionUs,
      long contentPositionUs,
      long endPositionUs,
      long durationUs,
      boolean isLastInTimelinePeriod,
      boolean isFinal) {
    this.id = id;
    this.startPositionUs = startPositionUs;
    this.contentPositionUs = contentPositionUs;
    this.endPositionUs = endPositionUs;
    this.durationUs = durationUs;
    this.isLastInTimelinePeriod = isLastInTimelinePeriod;
    this.isFinal = isFinal;
  }

  /**
   * Returns a copy of this instance with the start position set to the specified value. May return
   * the same instance if nothing changed.
   */
  public MediaPeriodInfo copyWithStartPositionUs(long startPositionUs) {
    return startPositionUs == this.startPositionUs
        ? this
        : new MediaPeriodInfo(
            id,
            startPositionUs,
            contentPositionUs,
            endPositionUs,
            durationUs,
            isLastInTimelinePeriod,
            isFinal);
  }

  /**
   * Returns a copy of this instance with the content position set to the specified value. May
   * return the same instance if nothing changed.
   */
  public MediaPeriodInfo copyWithContentPositionUs(long contentPositionUs) {
    return contentPositionUs == this.contentPositionUs
        ? this
        : new MediaPeriodInfo(
            id,
            startPositionUs,
            contentPositionUs,
            endPositionUs,
            durationUs,
            isLastInTimelinePeriod,
            isFinal);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MediaPeriodInfo that = (MediaPeriodInfo) o;
    return startPositionUs == that.startPositionUs
        && contentPositionUs == that.contentPositionUs
        && endPositionUs == that.endPositionUs
        && durationUs == that.durationUs
        && isLastInTimelinePeriod == that.isLastInTimelinePeriod
        && isFinal == that.isFinal
        && Util.areEqual(id, that.id);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + id.hashCode();
    result = 31 * result + (int) startPositionUs;
    result = 31 * result + (int) contentPositionUs;
    result = 31 * result + (int) endPositionUs;
    result = 31 * result + (int) durationUs;
    result = 31 * result + (isLastInTimelinePeriod ? 1 : 0);
    result = 31 * result + (isFinal ? 1 : 0);
    return result;
  }
}
