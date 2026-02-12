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
package com.google.android.exoplayer2.ext.cast;

import androidx.annotation.Nullable;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import java.util.Arrays;

/**
 * A {@link Timeline} for Cast media queues.
 */
/* package */ final class CastTimeline extends Timeline {

  /** Holds {@link Timeline} related data for a Cast media item. */
  public static final class ItemData {

    /** Holds no media information. */
    public static final ItemData EMPTY = new ItemData();

    /** The duration of the item in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public final long durationUs;
    /**
     * The default start position of the item in microseconds, or {@link C#TIME_UNSET} if unknown.
     */
    public final long defaultPositionUs;

    private ItemData() {
      this(/* durationUs= */ C.TIME_UNSET, /* defaultPositionUs */ C.TIME_UNSET);
    }

    /**
     * Creates an instance.
     *
     * @param durationUs See {@link #durationsUs}.
     * @param defaultPositionUs See {@link #defaultPositionUs}.
     */
    public ItemData(long durationUs, long defaultPositionUs) {
      this.durationUs = durationUs;
      this.defaultPositionUs = defaultPositionUs;
    }

    /** Returns an instance with the given {@link #durationsUs}. */
    public ItemData copyWithDurationUs(long durationUs) {
      if (durationUs == this.durationUs) {
        return this;
      }
      return new ItemData(durationUs, defaultPositionUs);
    }

    /** Returns an instance with the given {@link #defaultPositionsUs}. */
    public ItemData copyWithDefaultPositionUs(long defaultPositionUs) {
      if (defaultPositionUs == this.defaultPositionUs) {
        return this;
      }
      return new ItemData(durationUs, defaultPositionUs);
    }
  }

  /** {@link Timeline} for a cast queue that has no items. */
  public static final CastTimeline EMPTY_CAST_TIMELINE =
      new CastTimeline(new int[0], new SparseArray<>());

  private final SparseIntArray idsToIndex;
  private final int[] ids;
  private final long[] durationsUs;
  private final long[] defaultPositionsUs;

  /**
   * Creates a Cast timeline from the given data.
   *
   * @param itemIds The ids of the items in the timeline.
   * @param itemIdToData Maps item ids to {@link ItemData}.
   */
  public CastTimeline(int[] itemIds, SparseArray<ItemData> itemIdToData) {
    int itemCount = itemIds.length;
    idsToIndex = new SparseIntArray(itemCount);
    ids = Arrays.copyOf(itemIds, itemCount);
    durationsUs = new long[itemCount];
    defaultPositionsUs = new long[itemCount];
    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      idsToIndex.put(id, i);
      ItemData data = itemIdToData.get(id, ItemData.EMPTY);
      durationsUs[i] = data.durationUs;
      defaultPositionsUs[i] = data.defaultPositionUs;
    }
  }

  // Timeline implementation.

  @Override
  public int getWindowCount() {
    return ids.length;
  }

  @Override
  public Window getWindow(
      int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
    long durationUs = durationsUs[windowIndex];
    boolean isDynamic = durationUs == C.TIME_UNSET;
    Object tag = setTag ? ids[windowIndex] : null;
    return window.set(
        tag,
        /* presentationStartTimeMs= */ C.TIME_UNSET,
        /* windowStartTimeMs= */ C.TIME_UNSET,
        /* isSeekable= */ !isDynamic,
        isDynamic,
        defaultPositionsUs[windowIndex],
        durationUs,
        /* firstPeriodIndex= */ windowIndex,
        /* lastPeriodIndex= */ windowIndex,
        /* positionInFirstPeriodUs= */ 0);
  }

  @Override
  public int getPeriodCount() {
    return ids.length;
  }

  @Override
  public Period getPeriod(int periodIndex, Period period, boolean setIds) {
    int id = ids[periodIndex];
    return period.set(id, id, periodIndex, durationsUs[periodIndex], 0);
  }

  @Override
  public int getIndexOfPeriod(Object uid) {
    return uid instanceof Integer ? idsToIndex.get((int) uid, C.INDEX_UNSET) : C.INDEX_UNSET;
  }

  @Override
  public Integer getUidOfPeriod(int periodIndex) {
    return ids[periodIndex];
  }

  // equals and hashCode implementations.

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof CastTimeline)) {
      return false;
    }
    CastTimeline that = (CastTimeline) other;
    return Arrays.equals(ids, that.ids)
        && Arrays.equals(durationsUs, that.durationsUs)
        && Arrays.equals(defaultPositionsUs, that.defaultPositionsUs);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(ids);
    result = 31 * result + Arrays.hashCode(durationsUs);
    result = 31 * result + Arrays.hashCode(defaultPositionsUs);
    return result;
  }

}
