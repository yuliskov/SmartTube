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
package com.google.android.exoplayer2.testutil;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;

/**
 * Fake {@link Timeline} which can be setup to return custom {@link TimelineWindowDefinition}s.
 */
public final class FakeTimeline extends Timeline {

  /**
   * Definition used to define a {@link FakeTimeline}.
   */
  public static final class TimelineWindowDefinition {

    /** Default test window duration in microseconds. */
    public static final long DEFAULT_WINDOW_DURATION_US = 10 * C.MICROS_PER_SECOND;

    public final int periodCount;
    public final Object id;
    public final boolean isSeekable;
    public final boolean isDynamic;
    public final long durationUs;
    public final AdPlaybackState adPlaybackState;

    /**
     * Creates a seekable, non-dynamic window definition with a duration of
     * {@link #DEFAULT_WINDOW_DURATION_US}.
     *
     * @param periodCount The number of periods in the window. Each period get an equal slice of the
     *     total window duration.
     * @param id The UID of the window.
     */
    public TimelineWindowDefinition(int periodCount, Object id) {
      this(periodCount, id, true, false, DEFAULT_WINDOW_DURATION_US);
    }

    /**
     * Creates a window definition with one period.
     *
     * @param isSeekable Whether the window is seekable.
     * @param isDynamic Whether the window is dynamic.
     * @param durationUs The duration of the window in microseconds.
     */
    public TimelineWindowDefinition(boolean isSeekable, boolean isDynamic, long durationUs) {
      this(1, 0, isSeekable, isDynamic, durationUs);
    }

    /**
     * Creates a window definition.
     *
     * @param periodCount The number of periods in the window. Each period get an equal slice of the
     *     total window duration.
     * @param id The UID of the window.
     * @param isSeekable Whether the window is seekable.
     * @param isDynamic Whether the window is dynamic.
     * @param durationUs The duration of the window in microseconds.
     */
    public TimelineWindowDefinition(int periodCount, Object id, boolean isSeekable,
        boolean isDynamic, long durationUs) {
      this(periodCount, id, isSeekable, isDynamic, durationUs, AdPlaybackState.NONE);
    }

    /**
     * Creates a window definition with ad groups.
     *
     * @param periodCount The number of periods in the window. Each period get an equal slice of the
     *     total window duration.
     * @param id The UID of the window.
     * @param isSeekable Whether the window is seekable.
     * @param isDynamic Whether the window is dynamic.
     * @param durationUs The duration of the window in microseconds.
     * @param adPlaybackState The ad playback state.
     */
    public TimelineWindowDefinition(
        int periodCount,
        Object id,
        boolean isSeekable,
        boolean isDynamic,
        long durationUs,
        AdPlaybackState adPlaybackState) {
      this.periodCount = periodCount;
      this.id = id;
      this.isSeekable = isSeekable;
      this.isDynamic = isDynamic;
      this.durationUs = durationUs;
      this.adPlaybackState = adPlaybackState;
    }

  }

  private static final long AD_DURATION_US = 10 * C.MICROS_PER_SECOND;

  private final TimelineWindowDefinition[] windowDefinitions;
  private final int[] periodOffsets;

  /**
   * Returns an ad playback state with the specified number of ads in each of the specified ad
   * groups, each ten seconds long.
   *
   * @param adsPerAdGroup The number of ads per ad group.
   * @param adGroupTimesUs The times of ad groups, in microseconds.
   * @return The ad playback state.
   */
  public static AdPlaybackState createAdPlaybackState(int adsPerAdGroup, long... adGroupTimesUs) {
    int adGroupCount = adGroupTimesUs.length;
    AdPlaybackState adPlaybackState = new AdPlaybackState(adGroupTimesUs);
    long[][] adDurationsUs = new long[adGroupCount][];
    for (int i = 0; i < adGroupCount; i++) {
      adPlaybackState = adPlaybackState.withAdCount(i, adsPerAdGroup);
      adDurationsUs[i] = new long[adsPerAdGroup];
      Arrays.fill(adDurationsUs[i], AD_DURATION_US);
    }
    adPlaybackState = adPlaybackState.withAdDurationsUs(adDurationsUs);
    return adPlaybackState;
  }

  /**
   * Creates a fake timeline with the given number of seekable, non-dynamic windows with one period
   * with a duration of {@link TimelineWindowDefinition#DEFAULT_WINDOW_DURATION_US} each.
   *
   * @param windowCount The number of windows.
   */
  public FakeTimeline(int windowCount) {
    this(createDefaultWindowDefinitions(windowCount));
  }

  /**
   * Creates a fake timeline with the given window definitions.
   *
   * @param windowDefinitions A list of {@link TimelineWindowDefinition}s.
   */
  public FakeTimeline(TimelineWindowDefinition... windowDefinitions) {
    this.windowDefinitions = windowDefinitions;
    periodOffsets = new int[windowDefinitions.length + 1];
    periodOffsets[0] = 0;
    for (int i = 0; i < windowDefinitions.length; i++) {
      periodOffsets[i + 1] = periodOffsets[i] + windowDefinitions[i].periodCount;
    }
  }

  @Override
  public int getWindowCount() {
    return windowDefinitions.length;
  }

  @Override
  public Window getWindow(
      int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
    TimelineWindowDefinition windowDefinition = windowDefinitions[windowIndex];
    Object tag = setTag ? windowDefinition.id : null;
    return window.set(
        tag,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        windowDefinition.isSeekable,
        windowDefinition.isDynamic,
        /* defaultPositionUs= */ 0,
        windowDefinition.durationUs,
        periodOffsets[windowIndex],
        periodOffsets[windowIndex + 1] - 1,
        /* positionInFirstPeriodUs= */ 0);
  }

  @Override
  public int getPeriodCount() {
    return periodOffsets[periodOffsets.length - 1];
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    int windowIndex = Util.binarySearchFloor(periodOffsets, periodIndex, true, false);
    int windowPeriodIndex = periodIndex - periodOffsets[windowIndex];
    TimelineWindowDefinition windowDefinition = windowDefinitions[windowIndex];
    Object id = setIds ? windowPeriodIndex : null;
    Object uid = setIds ? Pair.create(windowDefinition.id, windowPeriodIndex) : null;
    long periodDurationUs = windowDefinition.durationUs / windowDefinition.periodCount;
    long positionInWindowUs = periodDurationUs * windowPeriodIndex;
    return period.set(
        id,
        uid,
        windowIndex,
        periodDurationUs,
        positionInWindowUs,
        windowDefinition.adPlaybackState);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    for (int i = 0; i < getPeriodCount(); i++) {
      if (getUidOfPeriod(i).equals(uid)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public Object getUidOfPeriod(int periodIndex) {
    Assertions.checkIndex(periodIndex, 0, getPeriodCount());
    int windowIndex =
        Util.binarySearchFloor(
            periodOffsets, periodIndex, /* inclusive= */ true, /* stayInBounds= */ false);
    int windowPeriodIndex = periodIndex - periodOffsets[windowIndex];
    TimelineWindowDefinition windowDefinition = windowDefinitions[windowIndex];
    return Pair.create(windowDefinition.id, windowPeriodIndex);
  }

  private static TimelineWindowDefinition[] createDefaultWindowDefinitions(int windowCount) {
    TimelineWindowDefinition[] windowDefinitions = new TimelineWindowDefinition[windowCount];
    for (int i = 0; i < windowCount; i++) {
      windowDefinitions[i] = new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ i);
    }
    return windowDefinitions;
  }

}
