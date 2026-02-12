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

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;

/**
 * An overridable {@link Timeline} implementation forwarding all methods to another timeline.
 */
public abstract class ForwardingTimeline extends Timeline {

  protected final Timeline timeline;

  public ForwardingTimeline(Timeline timeline) {
    this.timeline = timeline;
  }

  @Override
  public int getWindowCount() {
    return timeline.getWindowCount();
  }

  @Override
  public int getNextWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    return timeline.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
  }

  @Override
  public int getPreviousWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    return timeline.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
  }

  @Override
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    return timeline.getLastWindowIndex(shuffleModeEnabled);
  }

  @Override
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    return timeline.getFirstWindowIndex(shuffleModeEnabled);
  }

  @Override
  public Window getWindow(
      int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
    return timeline.getWindow(windowIndex, window, setTag, defaultPositionProjectionUs);
  }

  @Override
  public int getPeriodCount() {
    return timeline.getPeriodCount();
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    return timeline.getPeriod(periodIndex, period, setIds);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    return timeline.getIndexOfPeriod(uid);
  }

  @Override
  public Object getUidOfPeriod(int periodIndex) {
    return timeline.getUidOfPeriod(periodIndex);
  }
}
