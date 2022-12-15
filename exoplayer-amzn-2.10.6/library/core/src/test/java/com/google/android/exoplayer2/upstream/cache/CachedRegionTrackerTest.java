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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link CachedRegionTracker}. */
@RunWith(AndroidJUnit4.class)
public final class CachedRegionTrackerTest {

  private static final String CACHE_KEY = "abc";
  private static final long MS_IN_US = 1000;

  // 5 chunks, each 20 bytes long and 100 ms long.
  private static final ChunkIndex CHUNK_INDEX =
      new ChunkIndex(
          new int[] {20, 20, 20, 20, 20},
          new long[] {100, 120, 140, 160, 180},
          new long[] {
            100 * MS_IN_US, 100 * MS_IN_US, 100 * MS_IN_US, 100 * MS_IN_US, 100 * MS_IN_US
          },
          new long[] {0, 100 * MS_IN_US, 200 * MS_IN_US, 300 * MS_IN_US, 400 * MS_IN_US});

  @Mock private Cache cache;
  private CachedRegionTracker tracker;

  private CachedContentIndex index;
  private File cacheDir;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(cache.addListener(anyString(), any(Cache.Listener.class))).thenReturn(new TreeSet<>());
    tracker = new CachedRegionTracker(cache, CACHE_KEY, CHUNK_INDEX);
    cacheDir =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    index = new CachedContentIndex(TestUtil.getTestDatabaseProvider());
  }

  @After
  public void tearDown() throws Exception {
    Util.recursiveDelete(cacheDir);
  }

  @Test
  public void testGetRegion_noSpansInCache() {
    assertThat(tracker.getRegionEndTimeMs(100)).isEqualTo(CachedRegionTracker.NOT_CACHED);
    assertThat(tracker.getRegionEndTimeMs(150)).isEqualTo(CachedRegionTracker.NOT_CACHED);
  }

  @Test
  public void testGetRegion_fullyCached() throws Exception {
    tracker.onSpanAdded(cache, newCacheSpan(100, 100));

    assertThat(tracker.getRegionEndTimeMs(101)).isEqualTo(CachedRegionTracker.CACHED_TO_END);
    assertThat(tracker.getRegionEndTimeMs(121)).isEqualTo(CachedRegionTracker.CACHED_TO_END);
  }

  @Test
  public void testGetRegion_partiallyCached() throws Exception {
    tracker.onSpanAdded(cache, newCacheSpan(100, 40));

    assertThat(tracker.getRegionEndTimeMs(101)).isEqualTo(200);
    assertThat(tracker.getRegionEndTimeMs(121)).isEqualTo(200);
  }

  @Test
  public void testGetRegion_multipleSpanAddsJoinedCorrectly() throws Exception {
    tracker.onSpanAdded(cache, newCacheSpan(100, 20));
    tracker.onSpanAdded(cache, newCacheSpan(120, 20));

    assertThat(tracker.getRegionEndTimeMs(101)).isEqualTo(200);
    assertThat(tracker.getRegionEndTimeMs(121)).isEqualTo(200);
  }

  @Test
  public void testGetRegion_fullyCachedThenPartiallyRemoved() throws Exception {
    // Start with the full stream in cache.
    tracker.onSpanAdded(cache, newCacheSpan(100, 100));

    // Remove the middle bit.
    tracker.onSpanRemoved(cache, newCacheSpan(140, 40));

    assertThat(tracker.getRegionEndTimeMs(101)).isEqualTo(200);
    assertThat(tracker.getRegionEndTimeMs(121)).isEqualTo(200);

    assertThat(tracker.getRegionEndTimeMs(181)).isEqualTo(CachedRegionTracker.CACHED_TO_END);
  }

  @Test
  public void testGetRegion_subchunkEstimation() throws Exception {
    tracker.onSpanAdded(cache, newCacheSpan(100, 10));

    assertThat(tracker.getRegionEndTimeMs(101)).isEqualTo(50);
    assertThat(tracker.getRegionEndTimeMs(111)).isEqualTo(CachedRegionTracker.NOT_CACHED);
  }

  private CacheSpan newCacheSpan(int position, int length) throws IOException {
    int id = index.assignIdForKey(CACHE_KEY);
    File cacheFile = createCacheSpanFile(cacheDir, id, position, length, 0);
    return SimpleCacheSpan.createCacheEntry(cacheFile, length, index);
  }

  public static File createCacheSpanFile(
      File cacheDir, int id, long offset, int length, long lastTouchTimestamp) throws IOException {
    File cacheFile = SimpleCacheSpan.getCacheFile(cacheDir, id, offset, lastTouchTimestamp);
    createTestFile(cacheFile, length);
    return cacheFile;
  }

  private static void createTestFile(File file, int length) throws IOException {
    FileOutputStream output = new FileOutputStream(file);
    for (int i = 0; i < length; i++) {
      output.write(i);
    }
    output.close();
  }
}
