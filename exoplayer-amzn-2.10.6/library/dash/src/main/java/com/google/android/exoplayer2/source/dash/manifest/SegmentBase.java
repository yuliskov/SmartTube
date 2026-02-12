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
package com.google.android.exoplayer2.source.dash.manifest;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.dash.DashSegmentIndex;
import com.google.android.exoplayer2.util.Util;
import java.util.List;

/**
 * An approximate representation of a SegmentBase manifest element.
 */
public abstract class SegmentBase {

  /* package */ final RangedUri initialization;
  /* package */ final long timescale;
  /* package */ final long presentationTimeOffset;

  /**
   * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
   *     exists.
   * @param timescale The timescale in units per second.
   * @param presentationTimeOffset The presentation time offset. The value in seconds is the
   *     division of this value and {@code timescale}.
   */
  public SegmentBase(RangedUri initialization, long timescale, long presentationTimeOffset) {
    this.initialization = initialization;
    this.timescale = timescale;
    this.presentationTimeOffset = presentationTimeOffset;
  }

  /**
   * Returns the {@link RangedUri} defining the location of initialization data for a given
   * representation, or null if no initialization data exists.
   *
   * @param representation The {@link Representation} for which initialization data is required.
   * @return A {@link RangedUri} defining the location of the initialization data, or null.
   */
  public RangedUri getInitialization(Representation representation) {
    return initialization;
  }

  /**
   * Returns the presentation time offset, in microseconds.
   */
  public long getPresentationTimeOffsetUs() {
    return Util.scaleLargeTimestamp(presentationTimeOffset, C.MICROS_PER_SECOND, timescale);
  }

  /**
   * A {@link SegmentBase} that defines a single segment.
   */
  public static class SingleSegmentBase extends SegmentBase {

    /* package */ final long indexStart;
    /* package */ final long indexLength;

    /**
     * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
     *     exists.
     * @param timescale The timescale in units per second.
     * @param presentationTimeOffset The presentation time offset. The value in seconds is the
     *     division of this value and {@code timescale}.
     * @param indexStart The byte offset of the index data in the segment.
     * @param indexLength The length of the index data in bytes.
     */
    public SingleSegmentBase(RangedUri initialization, long timescale, long presentationTimeOffset,
        long indexStart, long indexLength) {
      super(initialization, timescale, presentationTimeOffset);
      this.indexStart = indexStart;
      this.indexLength = indexLength;
    }

    public SingleSegmentBase() {
      this(null, 1, 0, 0, 0);
    }

    public RangedUri getIndex() {
      return indexLength <= 0 ? null : new RangedUri(null, indexStart, indexLength);
    }

  }

  /**
   * A {@link SegmentBase} that consists of multiple segments.
   */
  public abstract static class MultiSegmentBase extends SegmentBase {

    /* package */ final long startNumber;
    /* package */ final long duration;
    /* package */ final List<SegmentTimelineElement> segmentTimeline;

    /**
     * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
     *     exists.
     * @param timescale The timescale in units per second.
     * @param presentationTimeOffset The presentation time offset. The value in seconds is the
     *     division of this value and {@code timescale}.
     * @param startNumber The sequence number of the first segment.
     * @param duration The duration of each segment in the case of fixed duration segments. The
     *     value in seconds is the division of this value and {@code timescale}. If {@code
     *     segmentTimeline} is non-null then this parameter is ignored.
     * @param segmentTimeline A segment timeline corresponding to the segments. If null, then
     *     segments are assumed to be of fixed duration as specified by the {@code duration}
     *     parameter.
     */
    public MultiSegmentBase(
        RangedUri initialization,
        long timescale,
        long presentationTimeOffset,
        long startNumber,
        long duration,
        List<SegmentTimelineElement> segmentTimeline) {
      super(initialization, timescale, presentationTimeOffset);
      this.startNumber = startNumber;
      this.duration = duration;
      this.segmentTimeline = segmentTimeline;
    }

    /** @see DashSegmentIndex#getSegmentNum(long, long) */
    public long getSegmentNum(long timeUs, long periodDurationUs) {
      final long firstSegmentNum = getFirstSegmentNum();
      final long segmentCount = getSegmentCount(periodDurationUs);
      if (segmentCount == 0) {
        return firstSegmentNum;
      }
      if (segmentTimeline == null) {
        // All segments are of equal duration (with the possible exception of the last one).
        long durationUs = (duration * C.MICROS_PER_SECOND) / timescale;
        long segmentNum = startNumber + timeUs / durationUs;
        // Ensure we stay within bounds.
        return segmentNum < firstSegmentNum ? firstSegmentNum
            : segmentCount == DashSegmentIndex.INDEX_UNBOUNDED ? segmentNum
            : Math.min(segmentNum, firstSegmentNum + segmentCount - 1);
      } else {
        // The index cannot be unbounded. Identify the segment using binary search.
        long lowIndex = firstSegmentNum;
        long highIndex = firstSegmentNum + segmentCount - 1;
        while (lowIndex <= highIndex) {
          long midIndex = lowIndex + (highIndex - lowIndex) / 2;
          long midTimeUs = getSegmentTimeUs(midIndex);
          if (midTimeUs < timeUs) {
            lowIndex = midIndex + 1;
          } else if (midTimeUs > timeUs) {
            highIndex = midIndex - 1;
          } else {
            return midIndex;
          }
        }
        return lowIndex == firstSegmentNum ? lowIndex : highIndex;
      }
    }

    /** @see DashSegmentIndex#getDurationUs(long, long) */
    public final long getSegmentDurationUs(long sequenceNumber, long periodDurationUs) {
      if (segmentTimeline != null) {
        long duration = segmentTimeline.get((int) (sequenceNumber - startNumber)).duration;
        return (duration * C.MICROS_PER_SECOND) / timescale;
      } else {
        int segmentCount = getSegmentCount(periodDurationUs);
        return segmentCount != DashSegmentIndex.INDEX_UNBOUNDED
            && sequenceNumber == (getFirstSegmentNum() + segmentCount - 1)
            ? (periodDurationUs - getSegmentTimeUs(sequenceNumber))
            : ((duration * C.MICROS_PER_SECOND) / timescale);
      }
    }

    /** @see DashSegmentIndex#getTimeUs(long) */
    public final long getSegmentTimeUs(long sequenceNumber) {
      long unscaledSegmentTime;
      if (segmentTimeline != null) {
        unscaledSegmentTime =
            segmentTimeline.get((int) (sequenceNumber - startNumber)).startTime
                - presentationTimeOffset;
      } else {
        unscaledSegmentTime = (sequenceNumber - startNumber) * duration;
      }
      return Util.scaleLargeTimestamp(unscaledSegmentTime, C.MICROS_PER_SECOND, timescale);
    }

    /**
     * Returns a {@link RangedUri} defining the location of a segment for the given index in the
     * given representation.
     *
     * @see DashSegmentIndex#getSegmentUrl(long)
     */
    public abstract RangedUri getSegmentUrl(Representation representation, long index);

    /** @see DashSegmentIndex#getFirstSegmentNum() */
    public long getFirstSegmentNum() {
      return startNumber;
    }

    /**
     * @see DashSegmentIndex#getSegmentCount(long)
     */
    public abstract int getSegmentCount(long periodDurationUs);

    /**
     * @see DashSegmentIndex#isExplicit()
     */
    public boolean isExplicit() {
      return segmentTimeline != null;
    }

  }

  /**
   * A {@link MultiSegmentBase} that uses a SegmentList to define its segments.
   */
  public static class SegmentList extends MultiSegmentBase {

    /* package */ final List<RangedUri> mediaSegments;

    /**
     * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
     *     exists.
     * @param timescale The timescale in units per second.
     * @param presentationTimeOffset The presentation time offset. The value in seconds is the
     *     division of this value and {@code timescale}.
     * @param startNumber The sequence number of the first segment.
     * @param duration The duration of each segment in the case of fixed duration segments. The
     *     value in seconds is the division of this value and {@code timescale}. If {@code
     *     segmentTimeline} is non-null then this parameter is ignored.
     * @param segmentTimeline A segment timeline corresponding to the segments. If null, then
     *     segments are assumed to be of fixed duration as specified by the {@code duration}
     *     parameter.
     * @param mediaSegments A list of {@link RangedUri}s indicating the locations of the segments.
     */
    public SegmentList(
        RangedUri initialization,
        long timescale,
        long presentationTimeOffset,
        long startNumber,
        long duration,
        List<SegmentTimelineElement> segmentTimeline,
        List<RangedUri> mediaSegments) {
      super(initialization, timescale, presentationTimeOffset, startNumber, duration,
          segmentTimeline);
      this.mediaSegments = mediaSegments;
    }

    @Override
    public RangedUri getSegmentUrl(Representation representation, long sequenceNumber) {
      return mediaSegments.get((int) (sequenceNumber - startNumber));
    }

    @Override
    public int getSegmentCount(long periodDurationUs) {
      return mediaSegments.size();
    }

    @Override
    public boolean isExplicit() {
      return true;
    }

  }

  /**
   * A {@link MultiSegmentBase} that uses a SegmentTemplate to define its segments.
   */
  public static class SegmentTemplate extends MultiSegmentBase {

    /* package */ final UrlTemplate initializationTemplate;
    /* package */ final UrlTemplate mediaTemplate;
    /* package */ final long endNumber;

    /**
     * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
     *     exists. The value of this parameter is ignored if {@code initializationTemplate} is
     *     non-null.
     * @param timescale The timescale in units per second.
     * @param presentationTimeOffset The presentation time offset. The value in seconds is the
     *     division of this value and {@code timescale}.
     * @param startNumber The sequence number of the first segment.
     * @param endNumber The sequence number of the last segment as specified by the
     *     SupplementalProperty with schemeIdUri="http://dashif.org/guidelines/last-segment-number",
     *     or {@link C#INDEX_UNSET}.
     * @param duration The duration of each segment in the case of fixed duration segments. The
     *     value in seconds is the division of this value and {@code timescale}. If {@code
     *     segmentTimeline} is non-null then this parameter is ignored.
     * @param segmentTimeline A segment timeline corresponding to the segments. If null, then
     *     segments are assumed to be of fixed duration as specified by the {@code duration}
     *     parameter.
     * @param initializationTemplate A template defining the location of initialization data, if
     *     such data exists. If non-null then the {@code initialization} parameter is ignored. If
     *     null then {@code initialization} will be used.
     * @param mediaTemplate A template defining the location of each media segment.
     */
    public SegmentTemplate(
        RangedUri initialization,
        long timescale,
        long presentationTimeOffset,
        long startNumber,
        long endNumber,
        long duration,
        List<SegmentTimelineElement> segmentTimeline,
        UrlTemplate initializationTemplate,
        UrlTemplate mediaTemplate) {
      super(
          initialization,
          timescale,
          presentationTimeOffset,
          startNumber,
          duration,
          segmentTimeline);
      this.initializationTemplate = initializationTemplate;
      this.mediaTemplate = mediaTemplate;
      this.endNumber = endNumber;
    }

    @Override
    public RangedUri getInitialization(Representation representation) {
      if (initializationTemplate != null) {
        String urlString = initializationTemplate.buildUri(representation.format.id, 0,
            representation.format.bitrate, 0);
        return new RangedUri(urlString, 0, C.LENGTH_UNSET);
      } else {
        return super.getInitialization(representation);
      }
    }

    @Override
    public RangedUri getSegmentUrl(Representation representation, long sequenceNumber) {
      long time;
      if (segmentTimeline != null) {
        time = segmentTimeline.get((int) (sequenceNumber - startNumber)).startTime;
      } else {
        time = (sequenceNumber - startNumber) * duration;
      }
      String uriString = mediaTemplate.buildUri(representation.format.id, sequenceNumber,
          representation.format.bitrate, time);
      return new RangedUri(uriString, 0, C.LENGTH_UNSET);
    }

    @Override
    public int getSegmentCount(long periodDurationUs) {
      if (segmentTimeline != null) {
        return segmentTimeline.size();
      } else if (endNumber != C.INDEX_UNSET) {
        return (int) (endNumber - startNumber + 1);
      } else if (periodDurationUs != C.TIME_UNSET) {
        long durationUs = (duration * C.MICROS_PER_SECOND) / timescale;
        return (int) Util.ceilDivide(periodDurationUs, durationUs);
      } else {
        return DashSegmentIndex.INDEX_UNBOUNDED;
      }
    }
  }

  /**
   * Represents a timeline segment from the MPD's SegmentTimeline list.
   */
  public static class SegmentTimelineElement {

    /* package */ final long startTime;
    /* package */ final long duration;

    /**
     * @param startTime The start time of the element. The value in seconds is the division of this
     *     value and the {@code timescale} of the enclosing element.
     * @param duration The duration of the element. The value in seconds is the division of this
     *     value and the {@code timescale} of the enclosing element.
     */
    public SegmentTimelineElement(long startTime, long duration) {
      this.startTime = startTime;
      this.duration = duration;
    }

  }

}
