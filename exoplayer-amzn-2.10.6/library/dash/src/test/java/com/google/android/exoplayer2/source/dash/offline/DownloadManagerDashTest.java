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
package com.google.android.exoplayer2.source.dash.offline;

import static com.google.android.exoplayer2.source.dash.offline.DashDownloadTestData.TEST_ID;
import static com.google.android.exoplayer2.source.dash.offline.DashDownloadTestData.TEST_MPD;
import static com.google.android.exoplayer2.source.dash.offline.DashDownloadTestData.TEST_MPD_URI;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCacheEmpty;
import static com.google.android.exoplayer2.testutil.CacheAsserts.assertCachedData;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.ConditionVariable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.testutil.CacheAsserts.RequestSet;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.DummyMainThread.TestRunnable;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.testutil.TestDownloadManagerListener;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

/** Tests {@link DownloadManager}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class DownloadManagerDashTest {

  private static final int ASSERT_TRUE_TIMEOUT = 1000;

  private SimpleCache cache;
  private File tempFolder;
  private FakeDataSet fakeDataSet;
  private DownloadManager downloadManager;
  private StreamKey fakeStreamKey1;
  private StreamKey fakeStreamKey2;
  private TestDownloadManagerListener downloadManagerListener;
  private DefaultDownloadIndex downloadIndex;
  private DummyMainThread dummyMainThread;

  @Before
  public void setUp() throws Exception {
    ShadowLog.stream = System.out;
    dummyMainThread = new DummyMainThread();
    Context context = ApplicationProvider.getApplicationContext();
    tempFolder = Util.createTempDirectory(context, "ExoPlayerTest");
    File cacheFolder = new File(tempFolder, "cache");
    cacheFolder.mkdir();
    cache = new SimpleCache(cacheFolder, new NoOpCacheEvictor());
    MockitoAnnotations.initMocks(this);
    fakeDataSet =
        new FakeDataSet()
            .setData(TEST_MPD_URI, TEST_MPD)
            .setRandomData("audio_init_data", 10)
            .setRandomData("audio_segment_1", 4)
            .setRandomData("audio_segment_2", 5)
            .setRandomData("audio_segment_3", 6)
            .setRandomData("text_segment_1", 1)
            .setRandomData("text_segment_2", 2)
            .setRandomData("text_segment_3", 3);

    fakeStreamKey1 = new StreamKey(0, 0, 0);
    fakeStreamKey2 = new StreamKey(0, 1, 0);
    downloadIndex = new DefaultDownloadIndex(TestUtil.getTestDatabaseProvider());
    createDownloadManager();
  }

  @After
  public void tearDown() {
    runOnMainThread(() -> downloadManager.release());
    Util.recursiveDelete(tempFolder);
    dummyMainThread.release();
  }

  // Disabled due to flakiness.
  @Ignore
  @Test
  public void testSaveAndLoadActionFile() throws Throwable {
    // Configure fakeDataSet to block until interrupted when TEST_MPD is read.
    fakeDataSet
        .newData(TEST_MPD_URI)
        .appendReadAction(
            () -> {
              try {
                // Wait until interrupted.
                while (true) {
                  Thread.sleep(100000);
                }
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
            })
        .appendReadData(TEST_MPD)
        .endData();

    // Run DM accessing code on UI/main thread as it should be. Also not to block handling of loaded
    // actions.
    runOnMainThread(
        () -> {
          // Setup an Action and immediately release the DM.
          DownloadRequest request = getDownloadRequest(fakeStreamKey1, fakeStreamKey2);
          downloadManager.addDownload(request);
          downloadManager.release();
        });

    assertCacheEmpty(cache);

    // Revert fakeDataSet to normal.
    fakeDataSet.setData(TEST_MPD_URI, TEST_MPD);

    dummyMainThread.runOnMainThread(this::createDownloadManager);

    // Block on the test thread.
    blockUntilTasksCompleteAndThrowAnyDownloadError();
    assertCachedData(cache, fakeDataSet);
  }

  @Test
  public void testHandleDownloadRequest() throws Throwable {
    handleDownloadRequest(fakeStreamKey1, fakeStreamKey2);
    blockUntilTasksCompleteAndThrowAnyDownloadError();
    assertCachedData(cache, new RequestSet(fakeDataSet).useBoundedDataSpecFor("audio_init_data"));
  }

  @Test
  public void testHandleMultipleDownloadRequest() throws Throwable {
    handleDownloadRequest(fakeStreamKey1);
    handleDownloadRequest(fakeStreamKey2);
    blockUntilTasksCompleteAndThrowAnyDownloadError();
    assertCachedData(cache, new RequestSet(fakeDataSet).useBoundedDataSpecFor("audio_init_data"));
  }

  @Test
  public void testHandleInterferingDownloadRequest() throws Throwable {
    fakeDataSet
        .newData("audio_segment_2")
        .appendReadAction(() -> handleDownloadRequest(fakeStreamKey2))
        .appendReadData(TestUtil.buildTestData(5))
        .endData();

    handleDownloadRequest(fakeStreamKey1);

    blockUntilTasksCompleteAndThrowAnyDownloadError();
    assertCachedData(cache, new RequestSet(fakeDataSet).useBoundedDataSpecFor("audio_init_data"));
  }

  @Test
  public void testHandleRemoveAction() throws Throwable {
    handleDownloadRequest(fakeStreamKey1);

    blockUntilTasksCompleteAndThrowAnyDownloadError();

    handleRemoveAction();

    blockUntilTasksCompleteAndThrowAnyDownloadError();

    assertCacheEmpty(cache);
  }

  // Disabled due to flakiness.
  @Test
  public void testHandleRemoveActionBeforeDownloadFinish() throws Throwable {
    handleDownloadRequest(fakeStreamKey1);
    handleRemoveAction();

    blockUntilTasksCompleteAndThrowAnyDownloadError();

    assertCacheEmpty(cache);
  }

  // Disabled due to flakiness [Internal: b/122290449].
  @Test
  public void testHandleInterferingRemoveAction() throws Throwable {
    final ConditionVariable downloadInProgressCondition = new ConditionVariable();
    fakeDataSet
        .newData("audio_segment_2")
        .appendReadAction(downloadInProgressCondition::open)
        .appendReadData(TestUtil.buildTestData(5))
        .endData();

    handleDownloadRequest(fakeStreamKey1);

    assertThat(downloadInProgressCondition.block(ASSERT_TRUE_TIMEOUT)).isTrue();

    handleRemoveAction();

    blockUntilTasksCompleteAndThrowAnyDownloadError();

    assertCacheEmpty(cache);
  }

  private void blockUntilTasksCompleteAndThrowAnyDownloadError() throws Throwable {
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  private void handleDownloadRequest(StreamKey... keys) {
    DownloadRequest request = getDownloadRequest(keys);
    runOnMainThread(() -> downloadManager.addDownload(request));
  }

  private DownloadRequest getDownloadRequest(StreamKey... keys) {
    ArrayList<StreamKey> keysList = new ArrayList<>();
    Collections.addAll(keysList, keys);
    return new DownloadRequest(
        TEST_ID,
        DownloadRequest.TYPE_DASH,
        TEST_MPD_URI,
        keysList,
        /* customCacheKey= */ null,
        null);
  }

  private void handleRemoveAction() {
    runOnMainThread(() -> downloadManager.removeDownload(TEST_ID));
  }

  private void createDownloadManager() {
    runOnMainThread(
        () -> {
          Factory fakeDataSourceFactory = new FakeDataSource.Factory().setFakeDataSet(fakeDataSet);
          downloadManager =
              new DownloadManager(
                  ApplicationProvider.getApplicationContext(),
                  downloadIndex,
                  new DefaultDownloaderFactory(
                      new DownloaderConstructorHelper(cache, fakeDataSourceFactory)));
          downloadManager.setRequirements(new Requirements(0));

          downloadManagerListener =
              new TestDownloadManagerListener(
                  downloadManager, dummyMainThread, /* timeout= */ 3000);
          downloadManager.resumeDownloads();
        });
  }

  private void runOnMainThread(TestRunnable r) {
    dummyMainThread.runTestOnMainThread(r);
  }
}
