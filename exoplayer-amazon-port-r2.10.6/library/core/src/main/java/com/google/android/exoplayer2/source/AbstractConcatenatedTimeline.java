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

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;

/**
 * Abstract base class for the concatenation of one or more {@link Timeline}s.
 */
/* package */ abstract class AbstractConcatenatedTimeline extends Timeline {

  private final int childCount;
  private final ShuffleOrder shuffleOrder;
  private final boolean isAtomic;

  /**
   * Returns UID of child timeline from a concatenated period UID.
   *
   * @param concatenatedUid UID of a period in a concatenated timeline.
   * @return UID of the child timeline this period belongs to.
   */
  public static Object getChildTimelineUidFromConcatenatedUid(Object concatenatedUid) {
    return ((Pair<?, ?>) concatenatedUid).first;
  }

  /**
   * Returns UID of the period in the child timeline from a concatenated period UID.
   *
   * @param concatenatedUid UID of a period in a concatenated timeline.
   * @return UID of the period in the child timeline.
   */
  public static Object getChildPeriodUidFromConcatenatedUid(Object concatenatedUid) {
    return ((Pair<?, ?>) concatenatedUid).second;
  }

  /**
   * Returns concatenated UID for a period in a child timeline.
   *
   * @param childTimelineUid UID of the child timeline this period belongs to.
   * @param childPeriodUid UID of the period in the child timeline.
   * @return UID of the period in the concatenated timeline.
   */
  public static Object getConcatenatedUid(Object childTimelineUid, Object childPeriodUid) {
    return Pair.create(childTimelineUid, childPeriodUid);
  }

  /**
   * Sets up a concatenated timeline with a shuffle order of child timelines.
   *
   * @param isAtomic Whether the child timelines shall be treated as atomic, i.e., treated as a
   *     single item for repeating and shuffling.
   * @param shuffleOrder A shuffle order of child timelines. The number of child timelines must
   *     match the number of elements in the shuffle order.
   */
  public AbstractConcatenatedTimeline(boolean isAtomic, ShuffleOrder shuffleOrder) {
    this.isAtomic = isAtomic;
    this.shuffleOrder = shuffleOrder;
    this.childCount = shuffleOrder.getLength();
  }

  @Override
  public int getNextWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    if (isAtomic) {
      // Adapt repeat and shuffle mode to atomic concatenation.
      repeatMode = repeatMode == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_ALL : repeatMode;
      shuffleModeEnabled = false;
    }
    // Find next window within current child.
    int childIndex = getChildIndexByWindowIndex(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    int nextWindowIndexInChild = getTimelineByChildIndex(childIndex).getNextWindowIndex(
        windowIndex - firstWindowIndexInChild,
        repeatMode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_OFF : repeatMode,
        shuffleModeEnabled);
    if (nextWindowIndexInChild != C.INDEX_UNSET) {
      return firstWindowIndexInChild + nextWindowIndexInChild;
    }
    // If not found, find first window of next non-empty child.
    int nextChildIndex = getNextChildIndex(childIndex, shuffleModeEnabled);
    while (nextChildIndex != C.INDEX_UNSET && getTimelineByChildIndex(nextChildIndex).isEmpty()) {
      nextChildIndex = getNextChildIndex(nextChildIndex, shuffleModeEnabled);
    }
    if (nextChildIndex != C.INDEX_UNSET) {
      return getFirstWindowIndexByChildIndex(nextChildIndex)
          + getTimelineByChildIndex(nextChildIndex).getFirstWindowIndex(shuffleModeEnabled);
    }
    // If not found, this is the last window.
    if (repeatMode == Player.REPEAT_MODE_ALL) {
      return getFirstWindowIndex(shuffleModeEnabled);
    }
    return C.INDEX_UNSET;
  }

  @Override
  public int getPreviousWindowIndex(int windowIndex, @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled) {
    if (isAtomic) {
      // Adapt repeat and shuffle mode to atomic concatenation.
      repeatMode = repeatMode == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_ALL : repeatMode;
      shuffleModeEnabled = false;
    }
    // Find previous window within current child.
    int childIndex = getChildIndexByWindowIndex(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    int previousWindowIndexInChild = getTimelineByChildIndex(childIndex).getPreviousWindowIndex(
        windowIndex - firstWindowIndexInChild,
        repeatMode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_OFF : repeatMode,
        shuffleModeEnabled);
    if (previousWindowIndexInChild != C.INDEX_UNSET) {
      return firstWindowIndexInChild + previousWindowIndexInChild;
    }
    // If not found, find last window of previous non-empty child.
    int previousChildIndex = getPreviousChildIndex(childIndex, shuffleModeEnabled);
    while (previousChildIndex != C.INDEX_UNSET
        && getTimelineByChildIndex(previousChildIndex).isEmpty()) {
      previousChildIndex = getPreviousChildIndex(previousChildIndex, shuffleModeEnabled);
    }
    if (previousChildIndex != C.INDEX_UNSET) {
      return getFirstWindowIndexByChildIndex(previousChildIndex)
          + getTimelineByChildIndex(previousChildIndex).getLastWindowIndex(shuffleModeEnabled);
    }
    // If not found, this is the first window.
    if (repeatMode == Player.REPEAT_MODE_ALL) {
      return getLastWindowIndex(shuffleModeEnabled);
    }
    return C.INDEX_UNSET;
  }

  @Override
  public int getLastWindowIndex(boolean shuffleModeEnabled) {
    if (childCount == 0) {
      return C.INDEX_UNSET;
    }
    if (isAtomic) {
      shuffleModeEnabled = false;
    }
    // Find last non-empty child.
    int lastChildIndex = shuffleModeEnabled ? shuffleOrder.getLastIndex() : childCount - 1;
    while (getTimelineByChildIndex(lastChildIndex).isEmpty()) {
      lastChildIndex = getPreviousChildIndex(lastChildIndex, shuffleModeEnabled);
      if (lastChildIndex == C.INDEX_UNSET) {
        // All children are empty.
        return C.INDEX_UNSET;
      }
    }
    return getFirstWindowIndexByChildIndex(lastChildIndex)
        + getTimelineByChildIndex(lastChildIndex).getLastWindowIndex(shuffleModeEnabled);
  }

  @Override
  public int getFirstWindowIndex(boolean shuffleModeEnabled) {
    if (childCount == 0) {
      return C.INDEX_UNSET;
    }
    if (isAtomic) {
      shuffleModeEnabled = false;
    }
    // Find first non-empty child.
    int firstChildIndex = shuffleModeEnabled ? shuffleOrder.getFirstIndex() : 0;
    while (getTimelineByChildIndex(firstChildIndex).isEmpty()) {
      firstChildIndex = getNextChildIndex(firstChildIndex, shuffleModeEnabled);
      if (firstChildIndex == C.INDEX_UNSET) {
        // All children are empty.
        return C.INDEX_UNSET;
      }
    }
    return getFirstWindowIndexByChildIndex(firstChildIndex)
        + getTimelineByChildIndex(firstChildIndex).getFirstWindowIndex(shuffleModeEnabled);
  }

  @Override
  public final Window getWindow(
      int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
    int childIndex = getChildIndexByWindowIndex(windowIndex);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    int firstPeriodIndexInChild = getFirstPeriodIndexByChildIndex(childIndex);
    getTimelineByChildIndex(childIndex)
        .getWindow(
            windowIndex - firstWindowIndexInChild, window, setTag, defaultPositionProjectionUs);
    window.firstPeriodIndex += firstPeriodIndexInChild;
    window.lastPeriodIndex += firstPeriodIndexInChild;
    return window;
  }

  @Override
  public final Period getPeriodByUid(Object uid, Period period) {
    Object childUid = getChildTimelineUidFromConcatenatedUid(uid);
    Object periodUid = getChildPeriodUidFromConcatenatedUid(uid);
    int childIndex = getChildIndexByChildUid(childUid);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    getTimelineByChildIndex(childIndex).getPeriodByUid(periodUid, period);
    period.windowIndex += firstWindowIndexInChild;
    period.uid = uid;
    return period;
  }

  @Override
  public final Period getPeriod(int periodIndex, Period period, boolean setIds) {
    int childIndex = getChildIndexByPeriodIndex(periodIndex);
    int firstWindowIndexInChild = getFirstWindowIndexByChildIndex(childIndex);
    int firstPeriodIndexInChild = getFirstPeriodIndexByChildIndex(childIndex);
    getTimelineByChildIndex(childIndex).getPeriod(periodIndex - firstPeriodIndexInChild, period,
        setIds);
    period.windowIndex += firstWindowIndexInChild;
    if (setIds) {
      period.uid = getConcatenatedUid(getChildUidByChildIndex(childIndex), period.uid);
    }
    return period;
  }

  @Override
  public final int getIndexOfPeriod(Object uid) {
    if (!(uid instanceof Pair)) {
      return C.INDEX_UNSET;
    }
    Object childUid = getChildTimelineUidFromConcatenatedUid(uid);
    Object periodUid = getChildPeriodUidFromConcatenatedUid(uid);
    int childIndex = getChildIndexByChildUid(childUid);
    if (childIndex == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    int periodIndexInChild = getTimelineByChildIndex(childIndex).getIndexOfPeriod(periodUid);
    return periodIndexInChild == C.INDEX_UNSET ? C.INDEX_UNSET
        : getFirstPeriodIndexByChildIndex(childIndex) + periodIndexInChild;
  }

  @Override
  public final Object getUidOfPeriod(int periodIndex) {
    int childIndex = getChildIndexByPeriodIndex(periodIndex);
    int firstPeriodIndexInChild = getFirstPeriodIndexByChildIndex(childIndex);
    Object periodUidInChild =
        getTimelineByChildIndex(childIndex).getUidOfPeriod(periodIndex - firstPeriodIndexInChild);
    return getConcatenatedUid(getChildUidByChildIndex(childIndex), periodUidInChild);
  }

  /**
   * Returns the index of the child timeline containing the given period index.
   *
   * @param periodIndex A valid period index within the bounds of the timeline.
   */
  protected abstract int getChildIndexByPeriodIndex(int periodIndex);

  /**
   * Returns the index of the child timeline containing the given window index.
   *
   * @param windowIndex A valid window index within the bounds of the timeline.
   */
  protected abstract int getChildIndexByWindowIndex(int windowIndex);

  /**
   * Returns the index of the child timeline with the given UID or {@link C#INDEX_UNSET} if not
   * found.
   *
   * @param childUid A child UID.
   * @return Index of child timeline or {@link C#INDEX_UNSET} if UID was not found.
   */
  protected abstract int getChildIndexByChildUid(Object childUid);

  /**
   * Returns the child timeline for the child with the given index.
   *
   * @param childIndex A valid child index within the bounds of the timeline.
   */
  protected abstract Timeline getTimelineByChildIndex(int childIndex);

  /**
   * Returns the first period index belonging to the child timeline with the given index.
   *
   * @param childIndex A valid child index within the bounds of the timeline.
   */
  protected abstract int getFirstPeriodIndexByChildIndex(int childIndex);

  /**
   * Returns the first window index belonging to the child timeline with the given index.
   *
   * @param childIndex A valid child index within the bounds of the timeline.
   */
  protected abstract int getFirstWindowIndexByChildIndex(int childIndex);

  /**
   * Returns the UID of the child timeline with the given index.
   *
   * @param childIndex A valid child index within the bounds of the timeline.
   */
  protected abstract Object getChildUidByChildIndex(int childIndex);

  private int getNextChildIndex(int childIndex, boolean shuffleModeEnabled) {
    return shuffleModeEnabled ? shuffleOrder.getNextIndex(childIndex)
        : childIndex < childCount - 1 ? childIndex + 1 : C.INDEX_UNSET;
  }

  private int getPreviousChildIndex(int childIndex, boolean shuffleModeEnabled) {
    return shuffleModeEnabled ? shuffleOrder.getPreviousIndex(childIndex)
        : childIndex > 0 ? childIndex - 1 : C.INDEX_UNSET;
  }

}
