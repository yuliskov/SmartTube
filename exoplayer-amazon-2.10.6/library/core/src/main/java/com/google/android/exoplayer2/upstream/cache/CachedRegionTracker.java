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
package com.google.android.exoplayer2.upstream.cache;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Utility class for efficiently tracking regions of data that are stored in a {@link Cache}
 * for a given cache key.
 */
public final class CachedRegionTracker implements Cache.Listener {

  private static final String TAG = "CachedRegionTracker";

  public static final int NOT_CACHED = -1;
  public static final int CACHED_TO_END = -2;

  private final Cache cache;
  private final String cacheKey;
  private final ChunkIndex chunkIndex;

  private final TreeSet<Region> regions;
  private final Region lookupRegion;

  public CachedRegionTracker(Cache cache, String cacheKey, ChunkIndex chunkIndex) {
    this.cache = cache;
    this.cacheKey = cacheKey;
    this.chunkIndex = chunkIndex;
    this.regions = new TreeSet<>();
    this.lookupRegion = new Region(0, 0);

    synchronized (this) {
      NavigableSet<CacheSpan> cacheSpans = cache.addListener(cacheKey, this);
      // Merge the spans into regions. mergeSpan is more efficient when merging from high to low,
      // which is why a descending iterator is used here.
      Iterator<CacheSpan> spanIterator = cacheSpans.descendingIterator();
      while (spanIterator.hasNext()) {
        CacheSpan span = spanIterator.next();
        mergeSpan(span);
      }
    }
  }

  public void release() {
    cache.removeListener(cacheKey, this);
  }

  /**
   * When provided with a byte offset, this method locates the cached region within which the
   * offset falls, and returns the approximate end position in milliseconds of that region. If the
   * byte offset does not fall within a cached region then {@link #NOT_CACHED} is returned.
   * If the cached region extends to the end of the stream, {@link #CACHED_TO_END} is returned.
   *
   * @param byteOffset The byte offset in the underlying stream.
   * @return The end position of the corresponding cache region, {@link #NOT_CACHED}, or
   *     {@link #CACHED_TO_END}.
   */
  public synchronized int getRegionEndTimeMs(long byteOffset) {
    lookupRegion.startOffset = byteOffset;
    Region floorRegion = regions.floor(lookupRegion);
    if (floorRegion == null || byteOffset > floorRegion.endOffset
        || floorRegion.endOffsetIndex == -1) {
      return NOT_CACHED;
    }
    int index = floorRegion.endOffsetIndex;
    if (index == chunkIndex.length - 1
        && floorRegion.endOffset == (chunkIndex.offsets[index] + chunkIndex.sizes[index])) {
      return CACHED_TO_END;
    }
    long segmentFractionUs = (chunkIndex.durationsUs[index]
        * (floorRegion.endOffset - chunkIndex.offsets[index])) / chunkIndex.sizes[index];
    return (int) ((chunkIndex.timesUs[index] + segmentFractionUs) / 1000);
  }

  @Override
  public synchronized void onSpanAdded(Cache cache, CacheSpan span) {
    mergeSpan(span);
  }

  @Override
  public synchronized void onSpanRemoved(Cache cache, CacheSpan span) {
    Region removedRegion = new Region(span.position, span.position + span.length);

    // Look up a region this span falls into.
    Region floorRegion = regions.floor(removedRegion);
    if (floorRegion == null) {
      Log.e(TAG, "Removed a span we were not aware of");
      return;
    }

    // Remove it.
    regions.remove(floorRegion);

    // Add new floor and ceiling regions, if necessary.
    if (floorRegion.startOffset < removedRegion.startOffset) {
      Region newFloorRegion = new Region(floorRegion.startOffset, removedRegion.startOffset);

      int index = Arrays.binarySearch(chunkIndex.offsets, newFloorRegion.endOffset);
      newFloorRegion.endOffsetIndex = index < 0 ? -index - 2 : index;
      regions.add(newFloorRegion);
    }

    if (floorRegion.endOffset > removedRegion.endOffset) {
      Region newCeilingRegion = new Region(removedRegion.endOffset + 1, floorRegion.endOffset);
      newCeilingRegion.endOffsetIndex = floorRegion.endOffsetIndex;
      regions.add(newCeilingRegion);
    }
  }

  @Override
  public void onSpanTouched(Cache cache, CacheSpan oldSpan, CacheSpan newSpan) {
    // Do nothing.
  }

  private void mergeSpan(CacheSpan span) {
    Region newRegion = new Region(span.position, span.position + span.length);
    Region floorRegion = regions.floor(newRegion);
    Region ceilingRegion = regions.ceiling(newRegion);
    boolean floorConnects = regionsConnect(floorRegion, newRegion);
    boolean ceilingConnects = regionsConnect(newRegion, ceilingRegion);

    if (ceilingConnects) {
      if (floorConnects) {
        // Extend floorRegion to cover both newRegion and ceilingRegion.
        floorRegion.endOffset = ceilingRegion.endOffset;
        floorRegion.endOffsetIndex = ceilingRegion.endOffsetIndex;
      } else {
        // Extend newRegion to cover ceilingRegion. Add it.
        newRegion.endOffset = ceilingRegion.endOffset;
        newRegion.endOffsetIndex = ceilingRegion.endOffsetIndex;
        regions.add(newRegion);
      }
      regions.remove(ceilingRegion);
    } else if (floorConnects) {
      // Extend floorRegion to the right to cover newRegion.
      floorRegion.endOffset = newRegion.endOffset;
      int index = floorRegion.endOffsetIndex;
      while (index < chunkIndex.length - 1
          && (chunkIndex.offsets[index + 1] <= floorRegion.endOffset)) {
        index++;
      }
      floorRegion.endOffsetIndex = index;
    } else {
      // This is a new region.
      int index = Arrays.binarySearch(chunkIndex.offsets, newRegion.endOffset);
      newRegion.endOffsetIndex = index < 0 ? -index - 2 : index;
      regions.add(newRegion);
    }
  }

  private boolean regionsConnect(Region lower, Region upper) {
    return lower != null && upper != null && lower.endOffset == upper.startOffset;
  }

  private static class Region implements Comparable<Region> {

    /**
     * The first byte of the region (inclusive).
     */
    public long startOffset;
    /**
     * End offset of the region (exclusive).
     */
    public long endOffset;
    /**
     * The index in chunkIndex that contains the end offset. May be -1 if the end offset comes
     * before the start of the first media chunk (i.e. if the end offset is within the stream
     * header).
     */
    public int endOffsetIndex;

    public Region(long position, long endOffset) {
      this.startOffset = position;
      this.endOffset = endOffset;
    }

    @Override
    public int compareTo(@NonNull Region another) {
      return Util.compareLong(startOffset, another.startOffset);
    }

  }

}
