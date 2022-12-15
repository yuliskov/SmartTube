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

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Period;
import com.google.android.exoplayer2.Timeline.Window;

/** Unit test for {@link Timeline}. */
public final class TimelineAsserts {

  private static final int[] REPEAT_MODES = {
    Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL
  };

  private TimelineAsserts() {}

  /** Assert that timeline is empty (i.e. has no windows or periods). */
  public static void assertEmpty(Timeline timeline) {
    assertWindowTags(timeline);
    assertPeriodCounts(timeline);
    for (boolean shuffled : new boolean[] {false, true}) {
      assertThat(timeline.getFirstWindowIndex(shuffled)).isEqualTo(C.INDEX_UNSET);
      assertThat(timeline.getLastWindowIndex(shuffled)).isEqualTo(C.INDEX_UNSET);
    }
  }

  /**
   * Asserts that window tags are set correctly.
   *
   * @param expectedWindowTags A list of expected window tags. If a tag is unknown or not important
   *     {@code null} can be passed to skip this window.
   */
  public static void assertWindowTags(Timeline timeline, Object... expectedWindowTags) {
    Window window = new Window();
    assertThat(timeline.getWindowCount()).isEqualTo(expectedWindowTags.length);
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      timeline.getWindow(i, window, true);
      if (expectedWindowTags[i] != null) {
        assertThat(window.tag).isEqualTo(expectedWindowTags[i]);
      }
    }
  }

  /** Asserts that window properties {@link Window}.isDynamic are set correctly. */
  public static void assertWindowIsDynamic(Timeline timeline, boolean... windowIsDynamic) {
    Window window = new Window();
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      timeline.getWindow(i, window, true);
      assertThat(window.isDynamic).isEqualTo(windowIsDynamic[i]);
    }
  }

  /**
   * Asserts that previous window indices for each window depending on the repeat mode and the
   * shuffle mode are equal to the given sequence.
   */
  public static void assertPreviousWindowIndices(
      Timeline timeline,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      int... expectedPreviousWindowIndices) {
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      assertThat(timeline.getPreviousWindowIndex(i, repeatMode, shuffleModeEnabled))
          .isEqualTo(expectedPreviousWindowIndices[i]);
    }
  }

  /**
   * Asserts that next window indices for each window depending on the repeat mode and the shuffle
   * mode are equal to the given sequence.
   */
  public static void assertNextWindowIndices(
      Timeline timeline,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      int... expectedNextWindowIndices) {
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      assertThat(timeline.getNextWindowIndex(i, repeatMode, shuffleModeEnabled))
          .isEqualTo(expectedNextWindowIndices[i]);
    }
  }

  /**
   * Asserts that the durations of the periods in the {@link Timeline} and the durations in the
   * given sequence are equal.
   */
  public static void assertPeriodDurations(Timeline timeline, long... durationsUs) {
    int periodCount = timeline.getPeriodCount();
    assertThat(periodCount).isEqualTo(durationsUs.length);
    Period period = new Period();
    for (int i = 0; i < periodCount; i++) {
      assertThat(timeline.getPeriod(i, period).durationUs).isEqualTo(durationsUs[i]);
    }
  }

  /**
   * Asserts that period counts for each window are set correctly. Also asserts that {@link
   * Window#firstPeriodIndex} and {@link Window#lastPeriodIndex} are set correctly, and it asserts
   * the correct behavior of {@link Timeline#getNextWindowIndex(int, int, boolean)}.
   */
  public static void assertPeriodCounts(Timeline timeline, int... expectedPeriodCounts) {
    int windowCount = timeline.getWindowCount();
    assertThat(windowCount).isEqualTo(expectedPeriodCounts.length);
    int[] accumulatedPeriodCounts = new int[windowCount + 1];
    accumulatedPeriodCounts[0] = 0;
    for (int i = 0; i < windowCount; i++) {
      accumulatedPeriodCounts[i + 1] = accumulatedPeriodCounts[i] + expectedPeriodCounts[i];
    }
    assertThat(timeline.getPeriodCount())
        .isEqualTo(accumulatedPeriodCounts[accumulatedPeriodCounts.length - 1]);
    Window window = new Window();
    Period period = new Period();
    for (int i = 0; i < windowCount; i++) {
      timeline.getWindow(i, window, true);
      assertThat(window.firstPeriodIndex).isEqualTo(accumulatedPeriodCounts[i]);
      assertThat(window.lastPeriodIndex).isEqualTo(accumulatedPeriodCounts[i + 1] - 1);
    }
    int expectedWindowIndex = 0;
    for (int i = 0; i < timeline.getPeriodCount(); i++) {
      timeline.getPeriod(i, period, true);
      while (i >= accumulatedPeriodCounts[expectedWindowIndex + 1]) {
        expectedWindowIndex++;
      }
      assertThat(period.windowIndex).isEqualTo(expectedWindowIndex);
      assertThat(timeline.getIndexOfPeriod(period.uid)).isEqualTo(i);
      assertThat(timeline.getUidOfPeriod(i)).isEqualTo(period.uid);
      for (int repeatMode : REPEAT_MODES) {
        if (i < accumulatedPeriodCounts[expectedWindowIndex + 1] - 1) {
          assertThat(timeline.getNextPeriodIndex(i, period, window, repeatMode, false))
              .isEqualTo(i + 1);
        } else {
          int nextWindow = timeline.getNextWindowIndex(expectedWindowIndex, repeatMode, false);
          int nextPeriod =
              nextWindow == C.INDEX_UNSET ? C.INDEX_UNSET : accumulatedPeriodCounts[nextWindow];
          assertThat(timeline.getNextPeriodIndex(i, period, window, repeatMode, false))
              .isEqualTo(nextPeriod);
        }
      }
    }
  }

  /** Asserts that periods' {@link Period#getAdGroupCount()} are set correctly. */
  public static void assertAdGroupCounts(Timeline timeline, int... expectedAdGroupCounts) {
    Period period = new Period();
    for (int i = 0; i < timeline.getPeriodCount(); i++) {
      timeline.getPeriod(i, period);
      assertThat(period.getAdGroupCount()).isEqualTo(expectedAdGroupCounts[i]);
    }
  }
}
