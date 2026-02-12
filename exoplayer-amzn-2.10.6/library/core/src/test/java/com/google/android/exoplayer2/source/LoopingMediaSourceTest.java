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
package com.google.android.exoplayer2.source;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.MediaSourceTestRunner;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit tests for {@link LoopingMediaSource}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class LoopingMediaSourceTest {

  private FakeTimeline multiWindowTimeline;

  @Before
  public void setUp() throws Exception {
    multiWindowTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(1, 111),
            new TimelineWindowDefinition(1, 222),
            new TimelineWindowDefinition(1, 333));
  }

  @Test
  public void testSingleLoopTimeline() throws IOException {
    Timeline timeline = getLoopingTimeline(multiWindowTimeline, 1);
    TimelineAsserts.assertWindowTags(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1);
    for (boolean shuffled : new boolean[] {false, true}) {
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_OFF, shuffled, C.INDEX_UNSET, 0, 1);
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_ONE, shuffled, 0, 1, 2);
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_ALL, shuffled, 2, 0, 1);
      TimelineAsserts.assertNextWindowIndices(
          timeline, Player.REPEAT_MODE_OFF, shuffled, 1, 2, C.INDEX_UNSET);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled, 0, 1, 2);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled, 1, 2, 0);
    }
  }

  @Test
  public void testMultiLoopTimeline() throws IOException {
    Timeline timeline = getLoopingTimeline(multiWindowTimeline, 3);
    TimelineAsserts.assertWindowTags(timeline, 111, 222, 333, 111, 222, 333, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1, 1, 1, 1, 1, 1, 1);
    for (boolean shuffled : new boolean[] {false, true}) {
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_OFF, shuffled, C.INDEX_UNSET, 0, 1, 2, 3, 4, 5, 6, 7, 8);
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_ONE, shuffled, 0, 1, 2, 3, 4, 5, 6, 7, 8);
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_ALL, shuffled, 8, 0, 1, 2, 3, 4, 5, 6, 7);
      TimelineAsserts.assertNextWindowIndices(
          timeline, Player.REPEAT_MODE_OFF, shuffled, 1, 2, 3, 4, 5, 6, 7, 8, C.INDEX_UNSET);
      TimelineAsserts.assertNextWindowIndices(
          timeline, Player.REPEAT_MODE_ONE, shuffled, 0, 1, 2, 3, 4, 5, 6, 7, 8);
      TimelineAsserts.assertNextWindowIndices(
          timeline, Player.REPEAT_MODE_ALL, shuffled, 1, 2, 3, 4, 5, 6, 7, 8, 0);
    }
  }

  @Test
  public void testInfiniteLoopTimeline() throws IOException {
    Timeline timeline = getLoopingTimeline(multiWindowTimeline, Integer.MAX_VALUE);
    TimelineAsserts.assertWindowTags(timeline, 111, 222, 333);
    TimelineAsserts.assertPeriodCounts(timeline, 1, 1, 1);
    for (boolean shuffled : new boolean[] {false, true}) {
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_OFF, shuffled, 2, 0, 1);
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_ONE, shuffled, 0, 1, 2);
      TimelineAsserts.assertPreviousWindowIndices(
          timeline, Player.REPEAT_MODE_ALL, shuffled, 2, 0, 1);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, shuffled, 1, 2, 0);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, shuffled, 0, 1, 2);
      TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, shuffled, 1, 2, 0);
    }
  }

  @Test
  public void testEmptyTimelineLoop() throws IOException {
    Timeline timeline = getLoopingTimeline(Timeline.EMPTY, 1);
    TimelineAsserts.assertEmpty(timeline);

    timeline = getLoopingTimeline(Timeline.EMPTY, 3);
    TimelineAsserts.assertEmpty(timeline);

    timeline = getLoopingTimeline(Timeline.EMPTY, Integer.MAX_VALUE);
    TimelineAsserts.assertEmpty(timeline);
  }

  @Test
  public void testSingleLoopPeriodCreation() throws Exception {
    testMediaPeriodCreation(multiWindowTimeline, /* loopCount= */ 1);
  }

  @Test
  public void testMultiLoopPeriodCreation() throws Exception {
    testMediaPeriodCreation(multiWindowTimeline, /* loopCount= */ 3);
  }

  @Test
  public void testInfiniteLoopPeriodCreation() throws Exception {
    testMediaPeriodCreation(multiWindowTimeline, /* loopCount= */ Integer.MAX_VALUE);
  }

  /**
   * Wraps the specified timeline in a {@link LoopingMediaSource} and returns the looping timeline.
   */
  private static Timeline getLoopingTimeline(Timeline timeline, int loopCount) throws IOException {
    FakeMediaSource fakeMediaSource = new FakeMediaSource(timeline, null);
    LoopingMediaSource mediaSource = new LoopingMediaSource(fakeMediaSource, loopCount);
    MediaSourceTestRunner testRunner = new MediaSourceTestRunner(mediaSource, null);
    try {
      Timeline loopingTimeline = testRunner.prepareSource();
      testRunner.releaseSource();
      fakeMediaSource.assertReleased();
      return loopingTimeline;
    } finally {
      testRunner.release();
    }
  }

  /**
   * Wraps the specified timeline in a {@link LoopingMediaSource} and asserts that all periods of
   * the looping timeline can be created and prepared.
   */
  private static void testMediaPeriodCreation(Timeline timeline, int loopCount) throws Exception {
    FakeMediaSource fakeMediaSource = new FakeMediaSource(timeline, null);
    LoopingMediaSource mediaSource = new LoopingMediaSource(fakeMediaSource, loopCount);
    MediaSourceTestRunner testRunner = new MediaSourceTestRunner(mediaSource, null);
    try {
      testRunner.prepareSource();
      testRunner.assertPrepareAndReleaseAllPeriods();
      testRunner.releaseSource();
    } finally {
      testRunner.release();
    }
  }
}
