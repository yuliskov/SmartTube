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

import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCacheEmpty;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.CacheAsserts;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.NavigableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link CacheDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class CacheDataSourceTest {

  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
  private static final int CACHE_FRAGMENT_SIZE = 3;
  private static final String DATASPEC_KEY = "dataSpecKey";
  private static final int MIN_PARALLEL_CHUNCKS_DOWNLOADS = 2;

  private Uri testDataUri;
  private DataSpec unboundedDataSpec;
  private DataSpec boundedDataSpec;
  private DataSpec unboundedDataSpecWithKey;
  private DataSpec boundedDataSpecWithKey;
  private String defaultCacheKey;
  private String customCacheKey;
  private CacheKeyFactory cacheKeyFactory;
  private File tempFolder;
  private SimpleCache cache;

  @Before
  public void setUp() throws Exception {
    testDataUri = Uri.parse("https://www.test.com/data");
    unboundedDataSpec = buildDataSpec(/* unbounded= */ true, /* key= */ null);
    boundedDataSpec = buildDataSpec(/* unbounded= */ false, /* key= */ null);
    unboundedDataSpecWithKey = buildDataSpec(/* unbounded= */ true, DATASPEC_KEY);
    boundedDataSpecWithKey = buildDataSpec(/* unbounded= */ false, DATASPEC_KEY);
    defaultCacheKey = CacheUtil.DEFAULT_CACHE_KEY_FACTORY.buildCacheKey(unboundedDataSpec);
    customCacheKey = "customKey." + defaultCacheKey;
    cacheKeyFactory = new CacheKeyFactory() {
      @Override
      public String buildCacheKey(DataSpec dataSpec) {
        return customCacheKey;
      }

      @Override
      public int maxDownloadParallelSegments() {
        // TODO Implement a better logic to determine the number of concurrent chunck downloads or
        // provide a way to customize by client. For now it uses at least MIN_PARALLEL_CHUNCKS_DOWNLOADS
        // and at most the half of the number of device processor.
        return Math.max(MIN_PARALLEL_CHUNCKS_DOWNLOADS, Runtime.getRuntime().availableProcessors() / 2);
      }
    };
    tempFolder =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
    cache = new SimpleCache(tempFolder, new NoOpCacheEvictor());
  }

  @After
  public void tearDown() throws Exception {
    Util.recursiveDelete(tempFolder);
  }

  @Test
  public void testFragmentSize() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, false);
    assertReadDataContentLength(cacheDataSource, boundedDataSpec, false, false);
    for (String key : cache.getKeys()) {
      for (CacheSpan cacheSpan : cache.getCachedSpans(key)) {
        assertThat(cacheSpan.length <= CACHE_FRAGMENT_SIZE).isTrue();
        assertThat(cacheSpan.file.length() <= CACHE_FRAGMENT_SIZE).isTrue();
      }
    }
  }

  @Test
  public void testCacheAndReadUnboundedRequest() throws Exception {
    assertCacheAndRead(unboundedDataSpec, /* unknownLength= */ false);
  }

  @Test
  public void testCacheAndReadUnknownLength() throws Exception {
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ true);
  }

  @Test
  public void testCacheAndReadUnboundedRequestUnknownLength() throws Exception {
    assertCacheAndRead(unboundedDataSpec, /* unknownLength= */ true);
  }

  @Test
  public void testCacheAndRead() throws Exception {
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ false);
  }

  @Test
  public void testUnsatisfiableRange() throws Exception {
    // Bounded request but the content length is unknown. This forces all data to be cached but not
    // the length.
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ true);

    // Now do an unbounded request. This will read all of the data from cache and then try to read
    // more from upstream which will cause to a 416 so CDS will store the length.
    CacheDataSource cacheDataSource =
        createCacheDataSource(/* setReadException= */ true, /* unknownLength= */ true);
    assertReadDataContentLength(
        cacheDataSource, unboundedDataSpec, /* unknownLength= */ true, /* customCacheKey= */ false);

    // If the user try to access off range then it should throw an IOException.
    try {
      cacheDataSource =
          createCacheDataSource(/* setReadException= */ false, /* unknownLength= */ false);
      cacheDataSource.open(buildDataSpec(TEST_DATA.length, /* length= */ 1, defaultCacheKey));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void testCacheAndReadUnboundedRequestWithCacheKeyFactoryWithNullDataSpecCacheKey()
      throws Exception {
    assertCacheAndRead(unboundedDataSpec, /* unknownLength= */ false, cacheKeyFactory);
  }

  @Test
  public void testCacheAndReadUnknownLengthWithCacheKeyFactoryOverridingWithNullDataSpecCacheKey()
      throws Exception {
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ true, cacheKeyFactory);
  }

  @Test
  public void
      testCacheAndReadUnboundedRequestUnknownLengthWithCacheKeyFactoryWithNullDataSpecCacheKey()
          throws Exception {
    assertCacheAndRead(unboundedDataSpec, /* unknownLength= */ true, cacheKeyFactory);
  }

  @Test
  public void testCacheAndReadWithCacheKeyFactoryWithNullDataSpecCacheKey() throws Exception {
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ false, cacheKeyFactory);
  }

  @Test
  public void testUnsatisfiableRangeWithCacheKeyFactoryNullDataSpecCacheKey() throws Exception {
    // Bounded request but the content length is unknown. This forces all data to be cached but not
    // the length.
    assertCacheAndRead(boundedDataSpec, /* unknownLength= */ true, cacheKeyFactory);

    // Now do an unbounded request. This will read all of the data from cache and then try to read
    // more from upstream which will cause to a 416 so CDS will store the length.
    CacheDataSource cacheDataSource =
        createCacheDataSource(
            /* setReadException= */ true, /* unknownLength= */ true, cacheKeyFactory);
    assertReadDataContentLength(
        cacheDataSource, unboundedDataSpec, /* unknownLength= */ true, /* customCacheKey= */ true);

    // If the user try to access off range then it should throw an IOException.
    try {
      cacheDataSource =
          createCacheDataSource(
              /* setReadException= */ false, /* unknownLength= */ false, cacheKeyFactory);
      cacheDataSource.open(buildDataSpec(TEST_DATA.length, /* length= */ 1, customCacheKey));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void testCacheAndReadUnboundedRequestWithCacheKeyFactoryOverridingDataSpecCacheKey()
      throws Exception {
    assertCacheAndRead(unboundedDataSpecWithKey, false, cacheKeyFactory);
  }

  @Test
  public void testCacheAndReadUnknownLengthWithCacheKeyFactoryOverridingDataSpecCacheKey()
      throws Exception {
    assertCacheAndRead(boundedDataSpecWithKey, true, cacheKeyFactory);
  }

  @Test
  public void
      testCacheAndReadUnboundedRequestUnknownLengthWithCacheKeyFactoryOverridingDataSpecCacheKey()
          throws Exception {
    assertCacheAndRead(unboundedDataSpecWithKey, /* unknownLength= */ true, cacheKeyFactory);
  }

  @Test
  public void testCacheAndReadWithCacheKeyFactoryOverridingDataSpecCacheKey() throws Exception {
    assertCacheAndRead(boundedDataSpecWithKey, /* unknownLength= */ false, cacheKeyFactory);
  }

  @Test
  public void testUnsatisfiableRangeWithCacheKeyFactoryOverridingDataSpecCacheKey()
      throws Exception {
    // Bounded request but the content length is unknown. This forces all data to be cached but not
    // the length.
    assertCacheAndRead(boundedDataSpecWithKey, /* unknownLength= */ true, cacheKeyFactory);

    // Now do an unbounded request. This will read all of the data from cache and then try to read
    // more from upstream which will cause to a 416 so CDS will store the length.
    CacheDataSource cacheDataSource =
        createCacheDataSource(
            /* setReadException= */ true, /* unknownLength= */ true, cacheKeyFactory);
    assertReadDataContentLength(
        cacheDataSource,
        unboundedDataSpecWithKey,
        /* unknownLength= */ true,
        /* customCacheKey= */ true);

    // If the user try to access off range then it should throw an IOException.
    try {
      cacheDataSource =
          createCacheDataSource(
              /* setReadException= */ false, /* unknownLength= */ false, cacheKeyFactory);
      cacheDataSource.open(buildDataSpec(TEST_DATA.length, /* length= */ 1, customCacheKey));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  @Test
  public void testContentLengthEdgeCases() throws Exception {
    DataSpec dataSpec = buildDataSpec(TEST_DATA.length - 2, 2);

    // Read partial at EOS but don't cross it so length is unknown.
    CacheDataSource cacheDataSource = createCacheDataSource(false, true);
    assertReadData(cacheDataSource, dataSpec, true);
    assertThat(ContentMetadata.getContentLength(cache.getContentMetadata(defaultCacheKey)))
        .isEqualTo(C.LENGTH_UNSET);

    // Now do an unbounded request for whole data. This will cause a bounded request from upstream.
    // End of data from upstream shouldn't be mixed up with EOS and cause length set wrong.
    cacheDataSource = createCacheDataSource(false, true);
    assertReadDataContentLength(cacheDataSource, unboundedDataSpec, true, false);

    // Now the length set correctly do an unbounded request with offset.
    assertThat(
            cacheDataSource.open(
                buildDataSpec(TEST_DATA.length - 2, C.LENGTH_UNSET, defaultCacheKey)))
        .isEqualTo(2);

    // An unbounded request with offset for not cached content.
    dataSpec =
        new DataSpec(
            Uri.parse("https://www.test.com/other"),
            TEST_DATA.length - 2,
            C.LENGTH_UNSET,
            /* key= */ null);
    assertThat(cacheDataSource.open(dataSpec)).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void testUnknownLengthContentReadInOneConnectionAndLengthIsResolved() throws Exception {
    FakeDataSource upstream = new FakeDataSource();
    upstream
        .getDataSet()
        .newData(testDataUri)
        .appendReadData(TEST_DATA)
        .setSimulateUnknownLength(true);
    CacheDataSource cacheDataSource = new CacheDataSource(cache, upstream, 0);

    cacheDataSource.open(unboundedDataSpec);
    TestUtil.readToEnd(cacheDataSource);
    cacheDataSource.close();

    assertThat(upstream.getAndClearOpenedDataSpecs()).hasLength(1);
    assertThat(ContentMetadata.getContentLength(cache.getContentMetadata(defaultCacheKey)))
        .isEqualTo(TEST_DATA.length);
  }

  @Test
  public void testIgnoreCacheForUnsetLengthRequests() throws Exception {
    FakeDataSource upstream = new FakeDataSource();
    upstream.getDataSet().setData(testDataUri, TEST_DATA);
    CacheDataSource cacheDataSource =
        new CacheDataSource(
            cache, upstream, CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS);

    cacheDataSource.open(unboundedDataSpec);
    TestUtil.readToEnd(cacheDataSource);
    cacheDataSource.close();

    assertThat(cache.getKeys()).isEmpty();
  }

  @Test
  public void testReadOnlyCache() throws Exception {
    CacheDataSource cacheDataSource = createCacheDataSource(false, false, 0, null);
    assertReadDataContentLength(cacheDataSource, boundedDataSpec, false, false);
    assertCacheEmpty(cache);
  }

  @Test
  public void testSwitchToCacheSourceWithReadOnlyCacheDataSource() throws Exception {
    // Create a fake data source with a 1 MB default data.
    FakeDataSource upstream = new FakeDataSource();
    FakeData fakeData = upstream.getDataSet().newDefaultData().appendReadData(1024 * 1024 - 1);
    // Insert an action just before the end of the data to fail the test if reading from upstream
    // reaches end of the data.
    fakeData
        .appendReadAction(() -> fail("Read from upstream shouldn't reach to the end of the data."))
        .appendReadData(1);
    // Create cache read-only CacheDataSource.
    CacheDataSource cacheDataSource =
        new CacheDataSource(cache, upstream, new FileDataSource(), null, 0, null);

    // Open source and read some data from upstream as the data hasn't cached yet.
    cacheDataSource.open(unboundedDataSpec);
    byte[] buffer = new byte[1024];
    cacheDataSource.read(buffer, 0, buffer.length);

    // Cache the data. Although we use another FakeDataSource instance, it shouldn't matter.
    FakeDataSource upstream2 =
        new FakeDataSource(
            new FakeDataSource()
                .getDataSet()
                .newDefaultData()
                .appendReadData(1024 * 1024)
                .endData());
    CacheUtil.cache(
        unboundedDataSpec,
        cache,
        /* cacheKeyFactory= */ null,
        upstream2,
        /* progressListener= */ null,
        /* isCanceled= */ null);

    // Read the rest of the data.
    TestUtil.readToEnd(cacheDataSource);
    cacheDataSource.close();
  }

  @Test
  public void testSwitchToCacheSourceWithNonBlockingCacheDataSource() throws Exception {
    // Create a fake data source with a 1 MB default data.
    FakeDataSource upstream = new FakeDataSource();
    FakeData fakeData = upstream.getDataSet().newDefaultData().appendReadData(1024 * 1024 - 1);
    // Insert an action just before the end of the data to fail the test if reading from upstream
    // reaches end of the data.
    fakeData
        .appendReadAction(() -> fail("Read from upstream shouldn't reach to the end of the data."))
        .appendReadData(1);

    // Lock the content on the cache.
    SimpleCacheSpan cacheSpan = cache.startReadWriteNonBlocking(defaultCacheKey, 0);
    assertThat(cacheSpan).isNotNull();
    assertThat(cacheSpan.isHoleSpan()).isTrue();

    // Create non blocking CacheDataSource.
    CacheDataSource cacheDataSource = new CacheDataSource(cache, upstream, 0);

    // Open source and read some data from upstream without writing to cache as the data is locked.
    cacheDataSource.open(unboundedDataSpec);
    byte[] buffer = new byte[1024];
    cacheDataSource.read(buffer, 0, buffer.length);

    // Unlock the span.
    cache.releaseHoleSpan(cacheSpan);
    assertCacheEmpty(cache);

    // Cache the data. Although we use another FakeDataSource instance, it shouldn't matter.
    FakeDataSource upstream2 =
        new FakeDataSource(
            new FakeDataSource()
                .getDataSet()
                .newDefaultData()
                .appendReadData(1024 * 1024)
                .endData());
    CacheUtil.cache(
        unboundedDataSpec,
        cache,
        /* cacheKeyFactory= */ null,
        upstream2,
        /* progressListener= */ null,
        /* isCanceled= */ null);

    // Read the rest of the data.
    TestUtil.readToEnd(cacheDataSource);
    cacheDataSource.close();
  }

  @Test
  public void testDeleteCachedWhileReadingFromUpstreamWithReadOnlyCacheDataSourceDoesNotCrash()
      throws Exception {
    // Create a fake data source with a 1 KB default data.
    FakeDataSource upstream = new FakeDataSource();
    int dataLength = 1024;
    upstream.getDataSet().newDefaultData().appendReadData(dataLength).endData();

    // Cache the latter half of the data.
    int halfDataLength = 512;
    DataSpec dataSpec = buildDataSpec(halfDataLength, C.LENGTH_UNSET);
    CacheUtil.cache(
        dataSpec,
        cache,
        /* cacheKeyFactory= */ null,
        upstream,
        /* progressListener= */ null,
        /* isCanceled= */ null);

    // Create cache read-only CacheDataSource.
    CacheDataSource cacheDataSource =
        new CacheDataSource(cache, upstream, new FileDataSource(), null, 0, null);

    // Open source and read some data from upstream as the data hasn't cached yet.
    cacheDataSource.open(unboundedDataSpec);
    TestUtil.readExactly(cacheDataSource, 100);

    // Delete cached data.
    CacheUtil.remove(unboundedDataSpec, cache, /* cacheKeyFactory= */ null);
    assertCacheEmpty(cache);

    // Read the rest of the data.
    TestUtil.readToEnd(cacheDataSource);
    cacheDataSource.close();
  }

  @Test
  public void testDeleteCachedWhileReadingFromUpstreamWithBlockingCacheDataSourceDoesNotBlock()
      throws Exception {
    // Create a fake data source with a 1 KB default data.
    FakeDataSource upstream = new FakeDataSource();
    int dataLength = 1024;
    upstream.getDataSet().newDefaultData().appendReadData(dataLength).endData();

    // Cache the latter half of the data.
    int halfDataLength = 512;
    DataSpec dataSpec = buildDataSpec(/* position= */ 0, halfDataLength);
    CacheUtil.cache(
        dataSpec,
        cache,
        /* cacheKeyFactory= */ null,
        upstream,
        /* progressListener= */ null,
        /* isCanceled= */ null);

    // Create blocking CacheDataSource.
    CacheDataSource cacheDataSource =
        new CacheDataSource(cache, upstream, CacheDataSource.FLAG_BLOCK_ON_CACHE);

    cacheDataSource.open(unboundedDataSpec);

    // Read the first half from upstream as it hasn't cached yet.
    TestUtil.readExactly(cacheDataSource, halfDataLength);

    // Delete the cached latter half.
    NavigableSet<CacheSpan> cachedSpans = cache.getCachedSpans(defaultCacheKey);
    for (CacheSpan cachedSpan : cachedSpans) {
      if (cachedSpan.position >= halfDataLength) {
        cache.removeSpan(cachedSpan);
      }
    }

    // Read the rest of the data.
    TestUtil.readToEnd(cacheDataSource);
    cacheDataSource.close();
  }

  private void assertCacheAndRead(DataSpec dataSpec, boolean unknownLength) throws IOException {
    assertCacheAndRead(dataSpec, unknownLength, /* cacheKeyFactory= */ null);
  }

  private void assertCacheAndRead(
      DataSpec dataSpec, boolean unknownLength, @Nullable CacheKeyFactory cacheKeyFactory)
      throws IOException {
    // Read all data from upstream and write to cache.
    CacheDataSource cacheDataSource =
        createCacheDataSource(/* setReadException= */ false, unknownLength, cacheKeyFactory);
    assertReadDataContentLength(cacheDataSource, dataSpec, unknownLength, cacheKeyFactory != null);

    // Just read from cache.
    cacheDataSource =
        createCacheDataSource(/* setReadException= */ true, unknownLength, cacheKeyFactory);
    assertReadDataContentLength(
        cacheDataSource,
        dataSpec,
        /* unknownLength= */ false,
        /* customCacheKey= */ cacheKeyFactory != null);
  }

  /**
   * Reads data until EOI and compares it to {@link #TEST_DATA}. Also checks content length returned
   * from open() call and the cached content length.
   */
  private void assertReadDataContentLength(
      CacheDataSource cacheDataSource,
      DataSpec dataSpec,
      boolean unknownLength,
      boolean customCacheKey)
      throws IOException {
    assertReadData(cacheDataSource, dataSpec, unknownLength);
    // If the request was unbounded then the content length should be cached, either because the
    // content length was known or because EOS was read. If the request was bounded then the content
    // length will not have been determined.
    ContentMetadata metadata =
        cache.getContentMetadata(customCacheKey ? this.customCacheKey : defaultCacheKey);
    assertThat(ContentMetadata.getContentLength(metadata))
        .isEqualTo(dataSpec.length == C.LENGTH_UNSET ? TEST_DATA.length : C.LENGTH_UNSET);
  }

  private void assertReadData(
      CacheDataSource cacheDataSource, DataSpec dataSpec, boolean unknownLength)
      throws IOException {
    int position = (int) dataSpec.absoluteStreamPosition;
    int requestLength = (int) dataSpec.length;
    int readLength = TEST_DATA.length - position;
    if (requestLength != C.LENGTH_UNSET) {
      readLength = Math.min(readLength, requestLength);
    }
    assertThat(cacheDataSource.open(dataSpec))
        .isEqualTo(unknownLength ? requestLength : readLength);
    cacheDataSource.close();

    byte[] expected = Arrays.copyOfRange(TEST_DATA, position, position + readLength);
    CacheAsserts.assertReadData(cacheDataSource, dataSpec, expected);
  }

  private CacheDataSource createCacheDataSource(boolean setReadException, boolean unknownLength) {
    return createCacheDataSource(
        setReadException, unknownLength, CacheDataSource.FLAG_BLOCK_ON_CACHE);
  }

  private CacheDataSource createCacheDataSource(
      boolean setReadException, boolean unknownLength, CacheKeyFactory cacheKeyFactory) {
    return createCacheDataSource(
        setReadException,
        unknownLength,
        CacheDataSource.FLAG_BLOCK_ON_CACHE,
        new CacheDataSink(cache, CACHE_FRAGMENT_SIZE),
        cacheKeyFactory);
  }

  private CacheDataSource createCacheDataSource(
      boolean setReadException, boolean unknownLength, @CacheDataSource.Flags int flags) {
    return createCacheDataSource(
        setReadException, unknownLength, flags, new CacheDataSink(cache, CACHE_FRAGMENT_SIZE));
  }

  private CacheDataSource createCacheDataSource(
      boolean setReadException,
      boolean unknownLength,
      @CacheDataSource.Flags int flags,
      CacheDataSink cacheWriteDataSink) {
    return createCacheDataSource(
        setReadException, unknownLength, flags, cacheWriteDataSink, /* cacheKeyFactory= */ null);
  }

  private CacheDataSource createCacheDataSource(
      boolean setReadException,
      boolean unknownLength,
      @CacheDataSource.Flags int flags,
      CacheDataSink cacheWriteDataSink,
      CacheKeyFactory cacheKeyFactory) {
    FakeDataSource upstream = new FakeDataSource();
    FakeData fakeData =
        upstream
            .getDataSet()
            .newDefaultData()
            .setSimulateUnknownLength(unknownLength)
            .appendReadData(TEST_DATA);
    if (setReadException) {
      fakeData.appendReadError(new IOException("Shouldn't read from upstream"));
    }
    return new CacheDataSource(
        cache,
        upstream,
        new FileDataSource(),
        cacheWriteDataSink,
        flags,
        /* eventListener= */ null,
        cacheKeyFactory);
  }

  private DataSpec buildDataSpec(boolean unbounded, @Nullable String key) {
    return buildDataSpec(/* position= */ 0, unbounded ? C.LENGTH_UNSET : TEST_DATA.length, key);
  }

  private DataSpec buildDataSpec(long position, long length) {
    return buildDataSpec(position, length, /* key= */ null);
  }

  private DataSpec buildDataSpec(long position, long length, @Nullable String key) {
    return new DataSpec(
        testDataUri, position, length, key, DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION);
  }
}
