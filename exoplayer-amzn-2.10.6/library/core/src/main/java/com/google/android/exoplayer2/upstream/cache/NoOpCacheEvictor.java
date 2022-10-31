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


/**
 * Evictor that doesn't ever evict cache files.
 *
 * Warning: Using this evictor might have unforeseeable consequences if cache
 * size is not managed elsewhere.
 */
public final class NoOpCacheEvictor implements CacheEvictor {

  @Override
  public boolean requiresCacheSpanTouches() {
    return false;
  }

  @Override
  public void onCacheInitialized() {
    // Do nothing.
  }

  @Override
  public void onStartFile(Cache cache, String key, long position, long maxLength) {
    // Do nothing.
  }

  @Override
  public void onSpanAdded(Cache cache, CacheSpan span) {
    // Do nothing.
  }

  @Override
  public void onSpanRemoved(Cache cache, CacheSpan span) {
    // Do nothing.
  }

  @Override
  public void onSpanTouched(Cache cache, CacheSpan oldSpan, CacheSpan newSpan) {
    // Do nothing.
  }

}
