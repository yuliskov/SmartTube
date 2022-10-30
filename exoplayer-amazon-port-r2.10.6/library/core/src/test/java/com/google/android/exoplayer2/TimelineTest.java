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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.FakeTimeline.TimelineWindowDefinition;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Timeline}. */
@RunWith(AndroidJUnit4.class)
public class TimelineTest {

  @Test
  public void testEmptyTimeline() {
    TimelineAsserts.assertEmpty(Timeline.EMPTY);
  }

  @Test
  public void testSinglePeriodTimeline() {
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(1, 111));
    TimelineAsserts.assertWindowTags(timeline, 111);
    TimelineAsserts.assertPeriodCounts(timeline, 1);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 0);
  }

  @Test
  public void testMultiPeriodTimeline() {
    Timeline timeline = new FakeTimeline(new TimelineWindowDefinition(5, 111));
    TimelineAsserts.assertWindowTags(timeline, 111);
    TimelineAsserts.assertPeriodCounts(timeline, 5);
    TimelineAsserts.assertPreviousWindowIndices(
        timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertPreviousWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_OFF, false, C.INDEX_UNSET);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ONE, false, 0);
    TimelineAsserts.assertNextWindowIndices(timeline, Player.REPEAT_MODE_ALL, false, 0);
  }
}
