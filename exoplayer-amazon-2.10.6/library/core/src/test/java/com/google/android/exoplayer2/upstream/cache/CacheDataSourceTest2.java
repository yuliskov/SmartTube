/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.upstream.cache;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSink;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSink;
import com.google.android.exoplayer2.upstream.crypto.AesCipherDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Additional tests for {@link CacheDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class CacheDataSourceTest2 {

  private static final String EXO_CACHE_DIR = "exo";
  private static final int EXO_CACHE_MAX_FILESIZE = 128;

  private static final Uri URI = Uri.parse("http://test.com/content");
  private static final String KEY = "key";
  private static final byte[] DATA = TestUtil.buildTestData(8 * EXO_CACHE_MAX_FILESIZE + 1);

  // A DataSpec that covers the full file.
  private static final DataSpec FULL = new DataSpec(URI, 0, DATA.length, KEY);

  private static final int OFFSET_ON_BOUNDARY = EXO_CACHE_MAX_FILESIZE;
  // A DataSpec that starts at 0 and extends to a cache file boundary.
  private static final DataSpec END_ON_BOUNDARY = new DataSpec(URI, 0, OFFSET_ON_BOUNDARY, KEY);
  // A DataSpec that starts on the same boundary and extends to the end of the file.
  private static final DataSpec START_ON_BOUNDARY = new DataSpec(URI, OFFSET_ON_BOUNDARY,
      DATA.length - OFFSET_ON_BOUNDARY, KEY);

  private static final int OFFSET_OFF_BOUNDARY = EXO_CACHE_MAX_FILESIZE * 2 + 1;
  // A DataSpec that starts at 0 and extends to just past a cache file boundary.
  private static final DataSpec END_OFF_BOUNDARY = new DataSpec(URI, 0, OFFSET_OFF_BOUNDARY, KEY);
  // A DataSpec that starts on the same boundary and extends to the end of the file.
  private static final DataSpec START_OFF_BOUNDARY = new DataSpec(URI, OFFSET_OFF_BOUNDARY,
      DATA.length - OFFSET_OFF_BOUNDARY, KEY);

  @Test
  public void testWithoutEncryption() throws IOException {
    testReads(false);
  }

  @Test
  public void testWithEncryption() throws IOException {
    testReads(true);
  }

  private void testReads(boolean useEncryption) throws IOException {
    FakeDataSource upstreamSource = buildFakeUpstreamSource();
    CacheDataSource source =
        buildCacheDataSource(
            ApplicationProvider.getApplicationContext(), upstreamSource, useEncryption);
    // First read, should arrive from upstream.
    testRead(END_ON_BOUNDARY, source);
    assertSingleOpen(upstreamSource, 0, OFFSET_ON_BOUNDARY);
    // Second read, should arrive from upstream.
    testRead(START_OFF_BOUNDARY, source);
    assertSingleOpen(upstreamSource, OFFSET_OFF_BOUNDARY, DATA.length);
    // Second read, should arrive part from cache and part from upstream.
    testRead(END_OFF_BOUNDARY, source);
    assertSingleOpen(upstreamSource, OFFSET_ON_BOUNDARY, OFFSET_OFF_BOUNDARY);
    // Third read, should arrive from cache.
    testRead(FULL, source);
    assertNoOpen(upstreamSource);
    // Various reads, should all arrive from cache.
    testRead(FULL, source);
    assertNoOpen(upstreamSource);
    testRead(START_ON_BOUNDARY, source);
    assertNoOpen(upstreamSource);
    testRead(END_ON_BOUNDARY, source);
    assertNoOpen(upstreamSource);
    testRead(START_OFF_BOUNDARY, source);
    assertNoOpen(upstreamSource);
    testRead(END_OFF_BOUNDARY, source);
    assertNoOpen(upstreamSource);
  }

  private void testRead(DataSpec dataSpec, CacheDataSource source) throws IOException {
    byte[] scratch = new byte[4096];
    Random random = new Random(0);
    source.open(dataSpec);
    int position = (int) dataSpec.absoluteStreamPosition;
    int bytesRead = 0;
    while (bytesRead != C.RESULT_END_OF_INPUT) {
      int maxBytesToRead = random.nextInt(scratch.length) + 1;
      bytesRead = source.read(scratch, 0, maxBytesToRead);
      if (bytesRead != C.RESULT_END_OF_INPUT) {
        assertThat(copyOf(scratch, bytesRead))
            .isEqualTo(copyOfRange(DATA, position, position + bytesRead));
        position += bytesRead;
      }
    }
    source.close();
  }

  /**
   * Asserts that a single {@link DataSource#open(DataSpec)} call has been made to the upstream
   * source, with the specified start (inclusive) and end (exclusive) positions.
   */
  private void assertSingleOpen(FakeDataSource upstreamSource, int start, int end) {
    DataSpec[] openedDataSpecs = upstreamSource.getAndClearOpenedDataSpecs();
    assertThat(openedDataSpecs).hasLength(1);
    assertThat(openedDataSpecs[0].position).isEqualTo(start);
    assertThat(openedDataSpecs[0].absoluteStreamPosition).isEqualTo(start);
    assertThat(openedDataSpecs[0].length).isEqualTo(end - start);
  }

  /**
   * Asserts that the upstream source was not opened.
   */
  private void assertNoOpen(FakeDataSource upstreamSource) {
    DataSpec[] openedDataSpecs = upstreamSource.getAndClearOpenedDataSpecs();
    assertThat(openedDataSpecs).hasLength(0);
  }

  private static FakeDataSource buildFakeUpstreamSource() {
    FakeDataSource fakeDataSource = new FakeDataSource();
    fakeDataSource.getDataSet().newDefaultData().appendReadData(DATA);
    return fakeDataSource;
  }

  private static CacheDataSource buildCacheDataSource(Context context, DataSource upstreamSource,
      boolean useAesEncryption) throws CacheException {
    File cacheDir = context.getExternalCacheDir();
    Cache cache = new SimpleCache(new File(cacheDir, EXO_CACHE_DIR), new NoOpCacheEvictor());
    emptyCache(cache);

    // Source and cipher
    final String secretKey = "testKey:12345678";
    DataSource file = new FileDataSource();
    DataSource cacheReadDataSource = useAesEncryption
        ? new AesCipherDataSource(Util.getUtf8Bytes(secretKey), file) : file;

    // Sink and cipher
    CacheDataSink cacheSink = new CacheDataSink(cache, EXO_CACHE_MAX_FILESIZE);
    byte[] scratch = new byte[3897];
    DataSink cacheWriteDataSink = useAesEncryption
        ? new AesCipherDataSink(Util.getUtf8Bytes(secretKey), cacheSink, scratch) : cacheSink;

    return new CacheDataSource(cache,
        upstreamSource,
        cacheReadDataSource,
        cacheWriteDataSink,
        CacheDataSource.FLAG_BLOCK_ON_CACHE,
        null); // eventListener
  }

  private static void emptyCache(Cache cache) throws CacheException {
    for (String key : cache.getKeys()) {
      for (CacheSpan span : cache.getCachedSpans(key)) {
        cache.removeSpan(span);
      }
    }
    // Sanity check that the cache really is empty now.
    assertThat(cache.getKeys().isEmpty()).isTrue();
  }

}
