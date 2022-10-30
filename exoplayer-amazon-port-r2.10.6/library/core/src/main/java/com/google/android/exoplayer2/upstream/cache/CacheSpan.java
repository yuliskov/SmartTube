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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.File;

/**
 * Defines a span of data that may or may not be cached (as indicated by {@link #isCached}).
 */
public class CacheSpan implements Comparable<CacheSpan> {

  /**
   * The cache key that uniquely identifies the original stream.
   */
  public final String key;
  /**
   * The position of the {@link CacheSpan} in the original stream.
   */
  public final long position;
  /**
   * The length of the {@link CacheSpan}, or {@link C#LENGTH_UNSET} if this is an open-ended hole.
   */
  public final long length;
  /**
   * Whether the {@link CacheSpan} is cached.
   */
  public final boolean isCached;
  /**
   * The file corresponding to this {@link CacheSpan}, or null if {@link #isCached} is false.
   */
  public final @Nullable File file;
  /** The last touch timestamp, or {@link C#TIME_UNSET} if {@link #isCached} is false. */
  public final long lastTouchTimestamp;

  /**
   * Creates a hole CacheSpan which isn't cached, has no last touch timestamp and no file
   * associated.
   *
   * @param key The cache key that uniquely identifies the original stream.
   * @param position The position of the {@link CacheSpan} in the original stream.
   * @param length The length of the {@link CacheSpan}, or {@link C#LENGTH_UNSET} if this is an
   *     open-ended hole.
   */
  public CacheSpan(String key, long position, long length) {
    this(key, position, length, C.TIME_UNSET, null);
  }

  /**
   * Creates a CacheSpan.
   *
   * @param key The cache key that uniquely identifies the original stream.
   * @param position The position of the {@link CacheSpan} in the original stream.
   * @param length The length of the {@link CacheSpan}, or {@link C#LENGTH_UNSET} if this is an
   *     open-ended hole.
   * @param lastTouchTimestamp The last touch timestamp, or {@link C#TIME_UNSET} if {@link
   *     #isCached} is false.
   * @param file The file corresponding to this {@link CacheSpan}, or null if it's a hole.
   */
  public CacheSpan(
      String key, long position, long length, long lastTouchTimestamp, @Nullable File file) {
    this.key = key;
    this.position = position;
    this.length = length;
    this.isCached = file != null;
    this.file = file;
    this.lastTouchTimestamp = lastTouchTimestamp;
  }

  /**
   * Returns whether this is an open-ended {@link CacheSpan}.
   */
  public boolean isOpenEnded() {
    return length == C.LENGTH_UNSET;
  }

  /**
   * Returns whether this is a hole {@link CacheSpan}.
   */
  public boolean isHoleSpan() {
    return !isCached;
  }

  @Override
  public int compareTo(@NonNull CacheSpan another) {
    if (!key.equals(another.key)) {
      return key.compareTo(another.key);
    }
    long startOffsetDiff = position - another.position;
    return startOffsetDiff == 0 ? 0 : ((startOffsetDiff < 0) ? -1 : 1);
  }

}
