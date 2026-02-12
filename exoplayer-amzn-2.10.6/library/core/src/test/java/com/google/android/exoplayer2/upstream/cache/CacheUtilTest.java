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
package com.google.android.exoplayer2.upstream.cache;

import static com.google.android.exoplayer2.C.LENGTH_UNSET;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCacheEmpty;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCachedData;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests {@link CacheUtil}. */
@RunWith(AndroidJUnit4.class)
public final class CacheUtilTest {

  /**
   * Abstract fake Cache implementation used by the test. This class must be public so Mockito can
   * create a proxy for it.
   */
  public abstract static class AbstractFakeCache implements Cache {

    // This array is set to alternating length of cached and not cached regions in tests:
    // spansAndGaps = {<length of 1st cached region>, <length of 1st not cached region>,
    //    <length of 2nd cached region>, <length of 2nd not cached region>, ... }
    // Ideally it should end with a cached region but it shouldn't matter for any code.
    private int[] spansAndGaps;
    private long contentLength;

    private void init() {
      spansAndGaps = new int[] {};
      contentLength = C.LENGTH_UNSET;
    }

    @Override
    public long getCachedLength(String key, long position, long length) {
      for (int i = 0; i < spansAndGaps.length; i++) {
        int spanOrGap = spansAndGaps[i];
        if (position < spanOrGap) {
          long left = Math.min(spanOrGap - position, length);
          return (i & 1) == 1 ? -left : left;
        }
        position -= spanOrGap;
      }
      return -length;
    }

    @Override
    public ContentMetadata getContentMetadata(String key) {
      DefaultContentMetadata metadata = new DefaultContentMetadata();
      ContentMetadataMutations mutations = new ContentMetadataMutations();
      ContentMetadataMutations.setContentLength(mutations, contentLength);
      return metadata.copyWithMutationsApplied(mutations);
    }
  }

  @Mock(answer = Answers.CALLS_REAL_METHODS) private AbstractFakeCache mockCache;
  private File tempFolder;
  private SimpleCache cache;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mockCache.init();
    tempFolder =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());
  }

  @After
  public void tearDown() {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void testGenerateKey() {
    assertThat(CacheUtil.generateKey(new DataSpec(Uri.EMPTY))).isNotNull();

    Uri testUri = Uri.parse("test");
    DataSpec testDataSpec = new DataSpec(testUri);
    String key = CacheUtil.generateKey(testDataSpec);
    assertThat(key).isNotNull();

    // Should generate the same key for the same input.
    assertThat(CacheUtil.generateKey(testDataSpec)).isEqualTo(key);

    // Should generate different key for different input.
    assertThat(key.equals(CacheUtil.generateKey(new DataSpec(Uri.parse("test2"))))).isFalse();
  }

  @Test
  public void testDefaultCacheKeyFactory_buildCacheKey() {
    Uri testUri = Uri.parse("test");
    String key = "key";
    // If DataSpec.key is present, returns it.
    assertThat(
            CacheUtil.DEFAULT_CACHE_KEY_FACTORY.buildCacheKey(
                new DataSpec(testUri, 0, LENGTH_UNSET, key)))
        .isEqualTo(key);
    // If not generates a new one using DataSpec.uri.
    assertThat(
            CacheUtil.DEFAULT_CACHE_KEY_FACTORY.buildCacheKey(
                new DataSpec(testUri, 0, LENGTH_UNSET, null)))
        .isEqualTo(testUri.toString());
  }

  @Test
  public void testGetCachedNoData() {
    Pair<Long, Long> contentLengthAndBytesCached =
        CacheUtil.getCached(
            new DataSpec(Uri.parse("test")), mockCache, /* cacheKeyFactory= */ null);

    assertThat(contentLengthAndBytesCached.first).isEqualTo(C.LENGTH_UNSET);
    assertThat(contentLengthAndBytesCached.second).isEqualTo(0);
  }

  @Test
  public void testGetCachedDataUnknownLength() {
    // Mock there is 100 bytes cached at the beginning
    mockCache.spansAndGaps = new int[] {100};
    Pair<Long, Long> contentLengthAndBytesCached =
        CacheUtil.getCached(
            new DataSpec(Uri.parse("test")), mockCache, /* cacheKeyFactory= */ null);

    assertThat(contentLengthAndBytesCached.first).isEqualTo(C.LENGTH_UNSET);
    assertThat(contentLengthAndBytesCached.second).isEqualTo(100);
  }

  @Test
  public void testGetCachedNoDataKnownLength() {
    mockCache.contentLength = 1000;
    Pair<Long, Long> contentLengthAndBytesCached =
        CacheUtil.getCached(
            new DataSpec(Uri.parse("test")), mockCache, /* cacheKeyFactory= */ null);

    assertThat(contentLengthAndBytesCached.first).isEqualTo(1000);
    assertThat(contentLengthAndBytesCached.second).isEqualTo(0);
  }

  @Test
  public void testGetCached() {
    mockCache.contentLength = 1000;
    mockCache.spansAndGaps = new int[] {100, 100, 200};
    Pair<Long, Long> contentLengthAndBytesCached =
        CacheUtil.getCached(
            new DataSpec(Uri.parse("test")), mockCache, /* cacheKeyFactory= */ null);

    assertThat(contentLengthAndBytesCached.first).isEqualTo(1000);
    assertThat(contentLengthAndBytesCached.second).isEqualTo(300);
  }

  @Test
  public void testGetCachedFromNonZeroPosition() {
    mockCache.contentLength = 1000;
    mockCache.spansAndGaps = new int[] {100, 100, 200};
    Pair<Long, Long> contentLengthAndBytesCached =
        CacheUtil.getCached(
            new DataSpec(
                Uri.parse("test"),
                /* absoluteStreamPosition= */ 100,
                /* length= */ C.LENGTH_UNSET,
                /* key= */ null),
            mockCache,
            /* cacheKeyFactory= */ null);

    assertThat(contentLengthAndBytesCached.first).isEqualTo(900);
    assertThat(contentLengthAndBytesCached.second).isEqualTo(200);
  }

  @Test
  public void testCache() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    CachingCounters counters = new CachingCounters();
    CacheUtil.cache(
        new DataSpec(Uri.parse("test_data")),
        cache,
        /* cacheKeyFactory= */ null,
        dataSource,
        counters,
        /* isCanceled= */ null);

    counters.assertValues(0, 100, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testCacheSetOffsetAndLength() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri testUri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(testUri, 10, 20, null);
    CachingCounters counters = new CachingCounters();
    CacheUtil.cache(
        dataSpec, cache, /* cacheKeyFactory= */ null, dataSource, counters, /* isCanceled= */ null);

    counters.assertValues(0, 20, 20);
    counters.reset();

    CacheUtil.cache(
        new DataSpec(testUri),
        cache,
        /* cacheKeyFactory= */ null,
        dataSource,
        counters,
        /* isCanceled= */ null);

    counters.assertValues(20, 80, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testCacheUnknownLength() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().newData("test_data")
        .setSimulateUnknownLength(true)
        .appendReadData(TestUtil.buildTestData(100)).endData();
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    DataSpec dataSpec = new DataSpec(Uri.parse("test_data"));
    CachingCounters counters = new CachingCounters();
    CacheUtil.cache(
        dataSpec, cache, /* cacheKeyFactory= */ null, dataSource, counters, /* isCanceled= */ null);

    counters.assertValues(0, 100, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testCacheUnknownLengthPartialCaching() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().newData("test_data")
        .setSimulateUnknownLength(true)
        .appendReadData(TestUtil.buildTestData(100)).endData();
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri testUri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(testUri, 10, 20, null);
    CachingCounters counters = new CachingCounters();
    CacheUtil.cache(
        dataSpec, cache, /* cacheKeyFactory= */ null, dataSource, counters, /* isCanceled= */ null);

    counters.assertValues(0, 20, 20);
    counters.reset();

    CacheUtil.cache(
        new DataSpec(testUri),
        cache,
        /* cacheKeyFactory= */ null,
        dataSource,
        counters,
        /* isCanceled= */ null);

    counters.assertValues(20, 80, 100);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testCacheLengthExceedsActualDataLength() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri testUri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(testUri, 0, 1000, null);
    CachingCounters counters = new CachingCounters();
    CacheUtil.cache(
        dataSpec, cache, /* cacheKeyFactory= */ null, dataSource, counters, /* isCanceled= */ null);

    counters.assertValues(0, 100, 1000);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testCacheThrowEOFException() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri testUri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(testUri, 0, 1000, null);

    try {
      CacheUtil.cache(
          dataSpec,
          cache,
          /* cacheKeyFactory= */ null,
          new CacheDataSource(cache, dataSource),
          new byte[CacheUtil.DEFAULT_BUFFER_SIZE_BYTES],
          /* priorityTaskManager= */ null,
          /* priority= */ 0,
          /* progressListener= */ null,
          /* isCanceled= */ null,
          /* enableEOFException= */ true);
      fail();
    } catch (EOFException e) {
      // Do nothing.
    }
  }

  @Test
  public void testCachePolling() throws Exception {
    final CachingCounters counters = new CachingCounters();
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .newData("test_data")
            .appendReadData(TestUtil.buildTestData(100))
            .appendReadAction(() -> counters.assertValues(0, 100, 300))
            .appendReadData(TestUtil.buildTestData(100))
            .appendReadAction(() -> counters.assertValues(0, 200, 300))
            .appendReadData(TestUtil.buildTestData(100))
            .endData();
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    CacheUtil.cache(
        new DataSpec(Uri.parse("test_data")),
        cache,
        /* cacheKeyFactory= */ null,
        dataSource,
        counters,
        /* isCanceled= */ null);

    counters.assertValues(0, 300, 300);
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testRemove() throws Exception {
    FakeDataSet fakeDataSet = new FakeDataSet().setRandomData("test_data", 100);
    FakeDataSource dataSource = new FakeDataSource(fakeDataSet);

    Uri uri = Uri.parse("test_data");
    DataSpec dataSpec = new DataSpec(uri, DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION);
    CacheUtil.cache(
        dataSpec,
        cache,
        /* cacheKeyFactory= */ null,
        // Set fragmentSize to 10 to make sure there are multiple spans.
        new CacheDataSource(
            cache,
            dataSource,
            new FileDataSource(),
            new CacheDataSink(cache, /* fragmentSize= */ 10),
            /* flags= */ 0,
            /* eventListener= */ null),
        new byte[CacheUtil.DEFAULT_BUFFER_SIZE_BYTES],
        /* priorityTaskManager= */ null,
        /* priority= */ 0,
        /* progressListener= */ null,
        /* isCanceled= */ null,
        true);
    CacheUtil.remove(dataSpec, cache, /* cacheKeyFactory= */ null);

    assertCacheEmpty(cache);
  }

  private static final class CachingCounters implements CacheUtil.ProgressListener {

    private long contentLength = C.LENGTH_UNSET;
    private long bytesAlreadyCached;
    private long bytesNewlyCached;
    private boolean seenFirstProgressUpdate;

    @Override
    public void onProgress(long contentLength, long bytesCached, long newBytesCached) {
      this.contentLength = contentLength;
      if (!seenFirstProgressUpdate) {
        bytesAlreadyCached = bytesCached;
        seenFirstProgressUpdate = true;
      }
      bytesNewlyCached = bytesCached - bytesAlreadyCached;
    }

    public void assertValues(int bytesAlreadyCached, int bytesNewlyCached, int contentLength) {
      assertThat(this.bytesAlreadyCached).isEqualTo(bytesAlreadyCached);
      assertThat(this.bytesNewlyCached).isEqualTo(bytesNewlyCached);
      assertThat(this.contentLength).isEqualTo(contentLength);
    }

    public void reset() {
      contentLength = C.LENGTH_UNSET;
      bytesAlreadyCached = 0;
      bytesNewlyCached = 0;
      seenFirstProgressUpdate = false;
    }
  }
}
