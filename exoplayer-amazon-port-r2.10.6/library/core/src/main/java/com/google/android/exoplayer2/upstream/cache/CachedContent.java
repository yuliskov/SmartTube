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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import java.io.File;
import java.util.TreeSet;

/** Defines the cached content for a single stream. */
/* package */ final class CachedContent {

  private static final String TAG = "CachedContent";

  /** The cache file id that uniquely identifies the original stream. */
  public final int id;
  /** The cache key that uniquely identifies the original stream. */
  public final String key;
  /** The cached spans of this content. */
  private final TreeSet<SimpleCacheSpan> cachedSpans;
  /** Metadata values. */
  private DefaultContentMetadata metadata;
  /** Whether the content is locked. */
  private boolean locked;

  /**
   * Creates a CachedContent.
   *
   * @param id The cache file id.
   * @param key The cache stream key.
   */
  public CachedContent(int id, String key) {
    this(id, key, DefaultContentMetadata.EMPTY);
  }

  public CachedContent(int id, String key, DefaultContentMetadata metadata) {
    this.id = id;
    this.key = key;
    this.metadata = metadata;
    this.cachedSpans = new TreeSet<>();
  }

  /** Returns the metadata. */
  public DefaultContentMetadata getMetadata() {
    return metadata;
  }

  /**
   * Applies {@code mutations} to the metadata.
   *
   * @return Whether {@code mutations} changed any metadata.
   */
  public boolean applyMetadataMutations(ContentMetadataMutations mutations) {
    DefaultContentMetadata oldMetadata = metadata;
    metadata = metadata.copyWithMutationsApplied(mutations);
    return !metadata.equals(oldMetadata);
  }

  /** Returns whether the content is locked. */
  public boolean isLocked() {
    return locked;
  }

  /** Sets the locked state of the content. */
  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  /** Adds the given {@link SimpleCacheSpan} which contains a part of the content. */
  public void addSpan(SimpleCacheSpan span) {
    cachedSpans.add(span);
  }

  /** Returns a set of all {@link SimpleCacheSpan}s. */
  public TreeSet<SimpleCacheSpan> getSpans() {
    return cachedSpans;
  }

  /**
   * Returns the span containing the position. If there isn't one, it returns a hole span
   * which defines the maximum extents of the hole in the cache.
   */
  public SimpleCacheSpan getSpan(long position) {
    SimpleCacheSpan lookupSpan = SimpleCacheSpan.createLookup(key, position);
    SimpleCacheSpan floorSpan = cachedSpans.floor(lookupSpan);
    if (floorSpan != null && floorSpan.position + floorSpan.length > position) {
      return floorSpan;
    }
    SimpleCacheSpan ceilSpan = cachedSpans.ceiling(lookupSpan);
    return ceilSpan == null ? SimpleCacheSpan.createOpenHole(key, position)
        : SimpleCacheSpan.createClosedHole(key, position, ceilSpan.position - position);
  }

  /**
   * Returns the length of the cached data block starting from the {@code position} to the block end
   * up to {@code length} bytes. If the {@code position} isn't cached then -(the length of the gap
   * to the next cached data up to {@code length} bytes) is returned.
   *
   * @param position The starting position of the data.
   * @param length The maximum length of the data to be returned.
   * @return the length of the cached or not cached data block length.
   */
  public long getCachedBytesLength(long position, long length) {
    SimpleCacheSpan span = getSpan(position);
    if (span.isHoleSpan()) {
      // We don't have a span covering the start of the queried region.
      return -Math.min(span.isOpenEnded() ? Long.MAX_VALUE : span.length, length);
    }
    long queryEndPosition = position + length;
    long currentEndPosition = span.position + span.length;
    if (currentEndPosition < queryEndPosition) {
      for (SimpleCacheSpan next : cachedSpans.tailSet(span, false)) {
        if (next.position > currentEndPosition) {
          // There's a hole in the cache within the queried region.
          break;
        }
        // We expect currentEndPosition to always equal (next.position + next.length), but
        // perform a max check anyway to guard against the existence of overlapping spans.
        currentEndPosition = Math.max(currentEndPosition, next.position + next.length);
        if (currentEndPosition >= queryEndPosition) {
          // We've found spans covering the queried region.
          break;
        }
      }
    }
    return Math.min(currentEndPosition - position, length);
  }

  /**
   * Sets the given span's last touch timestamp. The passed span becomes invalid after this call.
   *
   * @param cacheSpan Span to be copied and updated.
   * @param lastTouchTimestamp The new last touch timestamp.
   * @param updateFile Whether the span file should be renamed to have its timestamp match the new
   *     last touch time.
   * @return A span with the updated last touch timestamp.
   */
  public SimpleCacheSpan setLastTouchTimestamp(
      SimpleCacheSpan cacheSpan, long lastTouchTimestamp, boolean updateFile) {
    Assertions.checkState(cachedSpans.remove(cacheSpan));
    File file = cacheSpan.file;
    if (updateFile) {
      File directory = file.getParentFile();
      long position = cacheSpan.position;
      File newFile = SimpleCacheSpan.getCacheFile(directory, id, position, lastTouchTimestamp);
      if (file.renameTo(newFile)) {
        file = newFile;
      } else {
        Log.w(TAG, "Failed to rename " + file + " to " + newFile);
      }
    }
    SimpleCacheSpan newCacheSpan =
        cacheSpan.copyWithFileAndLastTouchTimestamp(file, lastTouchTimestamp);
    cachedSpans.add(newCacheSpan);
    return newCacheSpan;
  }

  /** Returns whether there are any spans cached. */
  public boolean isEmpty() {
    return cachedSpans.isEmpty();
  }

  /** Removes the given span from cache. */
  public boolean removeSpan(CacheSpan span) {
    if (cachedSpans.remove(span)) {
      span.file.delete();
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = id;
    result = 31 * result + key.hashCode();
    result = 31 * result + metadata.hashCode();
    return result;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CachedContent that = (CachedContent) o;
    return id == that.id
        && key.equals(that.key)
        && cachedSpans.equals(that.cachedSpans)
        && metadata.equals(that.metadata);
  }
}
