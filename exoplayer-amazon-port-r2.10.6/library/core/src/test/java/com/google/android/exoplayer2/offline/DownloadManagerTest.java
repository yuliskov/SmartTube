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
package com.google.android.exoplayer2.offline;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.Download.State;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.testutil.DummyMainThread;
import com.google.android.exoplayer2.testutil.DummyMainThread.TestRunnable;
import com.google.android.exoplayer2.testutil.RobolectricUtil;
import com.google.android.exoplayer2.testutil.TestDownloadManagerListener;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

/** Tests {@link DownloadManager}. */
@RunWith(AndroidJUnit4.class)
@Config(shadows = {RobolectricUtil.CustomLooper.class, RobolectricUtil.CustomMessageQueue.class})
public class DownloadManagerTest {

  /** Used to check if condition becomes true in this time interval. */
  private static final int ASSERT_TRUE_TIMEOUT = 10000;
  /** Used to check if condition stays false for this time interval. */
  private static final int ASSERT_FALSE_TIME = 1000;
  /** Maximum retry delay in DownloadManager. */
  private static final int MAX_RETRY_DELAY = 5000;
  /** Maximum number of times a downloader can be restarted before doing a released check. */
  private static final int MAX_STARTS_BEFORE_RELEASED = 1;
  /** A stop reason. */
  private static final int APP_STOP_REASON = 1;
  /** The minimum number of times a task must be retried before failing. */
  private static final int MIN_RETRY_COUNT = 3;
  /** Dummy value for the current time. */
  private static final long NOW_MS = 1234;

  private Uri uri1;
  private Uri uri2;
  private Uri uri3;
  private DummyMainThread dummyMainThread;
  private DefaultDownloadIndex downloadIndex;
  private TestDownloadManagerListener downloadManagerListener;
  private FakeDownloaderFactory downloaderFactory;
  private DownloadManager downloadManager;

  @Before
  public void setUp() throws Exception {
    ShadowLog.stream = System.out;
    MockitoAnnotations.initMocks(this);
    uri1 = Uri.parse("http://abc.com/media1");
    uri2 = Uri.parse("http://abc.com/media2");
    uri3 = Uri.parse("http://abc.com/media3");
    dummyMainThread = new DummyMainThread();
    downloadIndex = new DefaultDownloadIndex(TestUtil.getTestDatabaseProvider());
    downloaderFactory = new FakeDownloaderFactory();
    setUpDownloadManager(100);
  }

  @After
  public void tearDown() throws Exception {
    releaseDownloadManager();
    dummyMainThread.release();
  }

  @Test
  public void downloadRunner_multipleInstancePerContent_throwsException() {
    boolean exceptionThrown = false;
    try {
      new DownloadRunner(uri1);
      new DownloadRunner(uri1);
      // can't put fail() here as it would be caught in the catch below.
    } catch (Throwable e) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  public void multipleRequestsForTheSameContent_executedOnTheSameTask() {
    // Two download requests on first task
    new DownloadRunner(uri1).postDownloadRequest().postDownloadRequest();
    // One download, one remove requests on second task
    new DownloadRunner(uri2).postDownloadRequest().postRemoveRequest();
    // Two remove requests on third task
    new DownloadRunner(uri3).postRemoveRequest().postRemoveRequest();
  }

  @Test
  public void requestsForDifferentContent_executedOnDifferentTasks() {
    TaskWrapper task1 = new DownloadRunner(uri1).postDownloadRequest().getTask();
    TaskWrapper task2 = new DownloadRunner(uri2).postDownloadRequest().getTask();
    TaskWrapper task3 = new DownloadRunner(uri3).postRemoveRequest().getTask();

    assertThat(task1).isNoneOf(task2, task3);
    assertThat(task2).isNotEqualTo(task3);
  }

  @Test
  public void postDownloadRequest_downloads() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    TaskWrapper task = runner.postDownloadRequest().getTask();
    task.assertDownloading();
    runner.getDownloader(0).unblock().assertReleased().assertStartCount(1);
    task.assertCompleted();
    runner.assertCreatedDownloaderCount(1);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void postRemoveRequest_removes() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    TaskWrapper task = runner.postDownloadRequest().postRemoveRequest().getTask();
    task.assertRemoving();
    runner.getDownloader(1).unblock().assertReleased().assertStartCount(1);
    task.assertRemoved();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void downloadFails_retriesThenTaskFails() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();
    FakeDownloader downloader = runner.getDownloader(0);

    for (int i = 0; i <= MIN_RETRY_COUNT; i++) {
      downloader.assertStarted(MAX_RETRY_DELAY).fail();
    }

    downloader.assertReleased().assertStartCount(MIN_RETRY_COUNT + 1);
    runner.getTask().assertFailed();
    downloadManagerListener.blockUntilTasksComplete();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void downloadFails_retries() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();
    FakeDownloader downloader = runner.getDownloader(0);

    for (int i = 0; i < MIN_RETRY_COUNT; i++) {
      downloader.assertStarted(MAX_RETRY_DELAY).fail();
    }
    downloader.assertStarted(MAX_RETRY_DELAY).unblock();

    downloader.assertReleased().assertStartCount(MIN_RETRY_COUNT + 1);
    runner.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksComplete();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
  }

  @Test
  public void downloadProgressOnRetry_retryCountResets() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    runner.postDownloadRequest();
    FakeDownloader downloader = runner.getDownloader(0);

    int tooManyRetries = MIN_RETRY_COUNT + 10;
    for (int i = 0; i < tooManyRetries; i++) {
      downloader.incrementBytesDownloaded();
      downloader.assertStarted(MAX_RETRY_DELAY).fail();
    }
    downloader.assertStarted(MAX_RETRY_DELAY).unblock();

    downloader.assertReleased().assertStartCount(tooManyRetries + 1);
    runner.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksComplete();
  }

  @Test
  public void removeCancelsDownload() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(0);

    runner.postDownloadRequest();
    downloader1.assertStarted();
    runner.postRemoveRequest();

    downloader1.assertCanceled().assertStartCount(1);
    runner.getDownloader(1).unblock().assertNotCanceled();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void downloadNotCancelRemove() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(1);

    runner.postDownloadRequest().postRemoveRequest();
    downloader1.assertStarted();
    runner.postDownloadRequest();

    downloader1.unblock().assertNotCanceled();
    runner.getDownloader(2).unblock().assertNotCanceled();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void secondSameRemoveRequestIgnored() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(1);

    runner.postDownloadRequest().postRemoveRequest();
    downloader1.assertStarted();
    runner.postRemoveRequest();

    downloader1.unblock().assertNotCanceled();
    runner.getTask().assertRemoved();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void removeAllDownloads_removesAllDownloads() throws Throwable {
    // Finish one download and keep one running.
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    runner1.postDownloadRequest();
    runner1.getDownloader(0).unblock();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
    runner2.postDownloadRequest();

    runner1.postRemoveAllRequest();
    runner1.getDownloader(1).unblock();
    runner2.getDownloader(1).unblock();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();

    runner1.getTask().assertRemoved();
    runner2.getTask().assertRemoved();
    assertThat(downloadManager.getCurrentDownloads()).isEmpty();
    assertThat(downloadIndex.getDownloads().getCount()).isEqualTo(0);
  }

  @Test
  public void differentDownloadRequestsMerged() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1);
    FakeDownloader downloader1 = runner.getDownloader(0);

    StreamKey streamKey1 = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 0);
    StreamKey streamKey2 = new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 1);

    runner.postDownloadRequest(streamKey1);
    downloader1.assertStarted();
    runner.postDownloadRequest(streamKey2);

    downloader1.assertCanceled();

    FakeDownloader downloader2 = runner.getDownloader(1);
    downloader2.assertStarted();
    assertThat(downloader2.request.streamKeys).containsExactly(streamKey1, streamKey2);
    downloader2.unblock();

    runner.getTask().assertCompleted();
    runner.assertCreatedDownloaderCount(2);
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void requestsForDifferentContent_executedInParallel() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1).postDownloadRequest();
    DownloadRunner runner2 = new DownloadRunner(uri2).postDownloadRequest();
    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);

    downloader1.assertStarted();
    downloader2.assertStarted();
    downloader1.unblock();
    downloader2.unblock();

    runner1.getTask().assertCompleted();
    runner2.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void requestsForDifferentContent_ifMaxDownloadIs1_executedSequentially() throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1).postDownloadRequest();
    DownloadRunner runner2 = new DownloadRunner(uri2).postDownloadRequest();
    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);

    downloader1.assertStarted();
    downloader2.assertDoesNotStart();
    runner2.getTask().assertQueued();
    downloader1.unblock();
    downloader2.assertStarted();
    downloader2.unblock();

    runner1.getTask().assertCompleted();
    runner2.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void removeRequestForDifferentContent_ifMaxDownloadIs1_executedInParallel()
      throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1).postDownloadRequest();
    DownloadRunner runner2 = new DownloadRunner(uri2).postDownloadRequest().postRemoveRequest();
    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);

    downloader1.assertStarted();
    downloader2.assertStarted();
    downloader1.unblock();
    downloader2.unblock();

    runner1.getTask().assertCompleted();
    runner2.getTask().assertRemoved();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void downloadRequestFollowingRemove_ifMaxDownloadIs1_isNotStarted() throws Throwable {
    setUpDownloadManager(1);
    DownloadRunner runner1 = new DownloadRunner(uri1).postDownloadRequest();
    DownloadRunner runner2 = new DownloadRunner(uri2).postDownloadRequest().postRemoveRequest();
    runner2.postDownloadRequest();
    FakeDownloader downloader1 = runner1.getDownloader(0);
    FakeDownloader downloader2 = runner2.getDownloader(0);
    FakeDownloader downloader3 = runner2.getDownloader(1);

    downloader1.assertStarted();
    downloader2.assertStarted();
    downloader2.unblock();
    downloader3.assertDoesNotStart();
    downloader1.unblock();
    downloader3.assertStarted();
    downloader3.unblock();

    runner1.getTask().assertCompleted();
    runner2.getTask().assertCompleted();
    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void getCurrentDownloads_returnsCurrentDownloads() {
    TaskWrapper task1 = new DownloadRunner(uri1).postDownloadRequest().getTask();
    TaskWrapper task2 = new DownloadRunner(uri2).postDownloadRequest().getTask();
    TaskWrapper task3 =
        new DownloadRunner(uri3).postDownloadRequest().postRemoveRequest().getTask();

    task3.assertRemoving();
    List<Download> downloads = downloadManager.getCurrentDownloads();

    assertThat(downloads).hasSize(3);
    String[] taskIds = {task1.taskId, task2.taskId, task3.taskId};
    String[] downloadIds = {
      downloads.get(0).request.id, downloads.get(1).request.id, downloads.get(2).request.id
    };
    assertThat(downloadIds).isEqualTo(taskIds);
  }

  @Test
  public void pauseAndResume() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    DownloadRunner runner3 = new DownloadRunner(uri3);

    runner1.postDownloadRequest().getTask().assertDownloading();
    runner2.postDownloadRequest().postRemoveRequest().getTask().assertRemoving();
    runner2.postDownloadRequest();

    runOnMainThread(() -> downloadManager.pauseDownloads());

    runner1.getTask().assertQueued();

    // remove requests aren't stopped.
    runner2.getDownloader(1).unblock().assertReleased();
    runner2.getTask().assertQueued();
    // Although remove2 is finished, download2 doesn't start.
    runner2.getDownloader(2).assertDoesNotStart();

    // When a new remove request is added, it cancels stopped download requests with the same media.
    runner1.postRemoveRequest();
    runner1.getDownloader(1).assertStarted().unblock();
    runner1.getTask().assertRemoved();

    // New download requests can be added but they don't start.
    runner3.postDownloadRequest().getDownloader(0).assertDoesNotStart();

    runOnMainThread(() -> downloadManager.resumeDownloads());

    runner2.getDownloader(2).assertStarted().unblock();
    runner3.getDownloader(0).assertStarted().unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void setAndClearSingleDownloadStopReason() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1).postDownloadRequest();
    TaskWrapper task = runner.getTask();

    task.assertDownloading();

    runOnMainThread(() -> downloadManager.setStopReason(task.taskId, APP_STOP_REASON));

    task.assertStopped();

    runOnMainThread(() -> downloadManager.setStopReason(task.taskId, Download.STOP_REASON_NONE));

    runner.getDownloader(1).assertStarted().unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void setSingleDownloadStopReasonThenRemove_removesDownload() throws Throwable {
    DownloadRunner runner = new DownloadRunner(uri1).postDownloadRequest();
    TaskWrapper task = runner.getTask();

    task.assertDownloading();

    runOnMainThread(() -> downloadManager.setStopReason(task.taskId, APP_STOP_REASON));

    task.assertStopped();

    runner.postRemoveRequest();
    runner.getDownloader(1).assertStarted().unblock();
    task.assertRemoved();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void setSingleDownloadStopReason_doesNotAffectOtherDownloads() throws Throwable {
    DownloadRunner runner1 = new DownloadRunner(uri1);
    DownloadRunner runner2 = new DownloadRunner(uri2);
    DownloadRunner runner3 = new DownloadRunner(uri3);

    runner1.postDownloadRequest().getTask().assertDownloading();
    runner2.postDownloadRequest().postRemoveRequest().getTask().assertRemoving();

    runOnMainThread(() -> downloadManager.setStopReason(runner1.getTask().taskId, APP_STOP_REASON));

    runner1.getTask().assertStopped();

    // Other downloads aren't affected.
    runner2.getDownloader(1).unblock().assertReleased();

    // New download requests can be added and they start.
    runner3.postDownloadRequest().getDownloader(0).assertStarted().unblock();

    downloadManagerListener.blockUntilTasksCompleteAndThrowAnyDownloadError();
  }

  @Test
  public void mergeRequest_removing_becomesRestarting() {
    DownloadRequest downloadRequest = createDownloadRequest();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadRequest).setState(Download.STATE_REMOVING);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeRequest(download, downloadRequest, download.stopReason, NOW_MS);

    Download expectedDownload =
        downloadBuilder.setStartTimeMs(NOW_MS).setState(Download.STATE_RESTARTING).build();
    assertEqualIgnoringUpdateTime(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeRequest_failed_becomesQueued() {
    DownloadRequest downloadRequest = createDownloadRequest();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadRequest)
            .setState(Download.STATE_FAILED)
            .setFailureReason(Download.FAILURE_REASON_UNKNOWN);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeRequest(download, downloadRequest, download.stopReason, NOW_MS);

    Download expectedDownload =
        downloadBuilder
            .setStartTimeMs(NOW_MS)
            .setState(Download.STATE_QUEUED)
            .setFailureReason(Download.FAILURE_REASON_NONE)
            .build();
    assertEqualIgnoringUpdateTime(mergedDownload, expectedDownload);
  }

  @Test
  public void mergeRequest_stopped_staysStopped() {
    DownloadRequest downloadRequest = createDownloadRequest();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadRequest)
            .setState(Download.STATE_STOPPED)
            .setStopReason(/* stopReason= */ 1);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeRequest(download, downloadRequest, download.stopReason, NOW_MS);

    assertEqualIgnoringUpdateTime(mergedDownload, download);
  }

  @Test
  public void mergeRequest_completedWithStopReason_becomesStopped() {
    DownloadRequest downloadRequest = createDownloadRequest();
    DownloadBuilder downloadBuilder =
        new DownloadBuilder(downloadRequest)
            .setState(Download.STATE_COMPLETED)
            .setStopReason(/* stopReason= */ 1);
    Download download = downloadBuilder.build();

    Download mergedDownload =
        DownloadManager.mergeRequest(download, downloadRequest, download.stopReason, NOW_MS);

    Download expectedDownload =
        downloadBuilder.setStartTimeMs(NOW_MS).setState(Download.STATE_STOPPED).build();
    assertEqualIgnoringUpdateTime(mergedDownload, expectedDownload);
  }

  private void setUpDownloadManager(final int maxParallelDownloads) throws Exception {
    if (downloadManager != null) {
      releaseDownloadManager();
    }
    try {
      runOnMainThread(
          () -> {
            downloadManager =
                new DownloadManager(
                    ApplicationProvider.getApplicationContext(), downloadIndex, downloaderFactory);
            downloadManager.setMaxParallelDownloads(maxParallelDownloads);
            downloadManager.setMinRetryCount(MIN_RETRY_COUNT);
            downloadManager.setRequirements(new Requirements(0));
            downloadManager.resumeDownloads();
            downloadManagerListener =
                new TestDownloadManagerListener(downloadManager, dummyMainThread);
          });
      downloadManagerListener.waitUntilInitialized();
    } catch (Throwable throwable) {
      throw new Exception(throwable);
    }
  }

  private void releaseDownloadManager() throws Exception {
    try {
      runOnMainThread(() -> downloadManager.release());
    } catch (Throwable throwable) {
      throw new Exception(throwable);
    }
  }

  private void runOnMainThread(TestRunnable r) {
    dummyMainThread.runTestOnMainThread(r);
  }

  private static void assertEqualIgnoringUpdateTime(Download download, Download that) {
    assertThat(download.request).isEqualTo(that.request);
    assertThat(download.state).isEqualTo(that.state);
    assertThat(download.startTimeMs).isEqualTo(that.startTimeMs);
    assertThat(download.contentLength).isEqualTo(that.contentLength);
    assertThat(download.failureReason).isEqualTo(that.failureReason);
    assertThat(download.stopReason).isEqualTo(that.stopReason);
    assertThat(download.getPercentDownloaded()).isEqualTo(that.getPercentDownloaded());
    assertThat(download.getBytesDownloaded()).isEqualTo(that.getBytesDownloaded());
  }

  private static DownloadRequest createDownloadRequest() {
    return new DownloadRequest(
        "id",
        DownloadRequest.TYPE_DASH,
        Uri.parse("https://www.test.com/download"),
        Collections.emptyList(),
        /* customCacheKey= */ null,
        /* data= */ null);
  }

  private final class DownloadRunner {

    private final Uri uri;
    private final String id;
    private final ArrayList<FakeDownloader> downloaders;
    private int createdDownloaderCount = 0;
    private FakeDownloader downloader;
    private final TaskWrapper taskWrapper;

    private DownloadRunner(Uri uri) {
      this.uri = uri;
      id = uri.toString();
      downloaders = new ArrayList<>();
      downloader = addDownloader();
      downloaderFactory.registerDownloadRunner(this);
      taskWrapper = new TaskWrapper(id);
    }

    private DownloadRunner postRemoveRequest() {
      runOnMainThread(() -> downloadManager.removeDownload(id));
      return this;
    }

    private DownloadRunner postRemoveAllRequest() {
      runOnMainThread(() -> downloadManager.removeAllDownloads());
      return this;
    }

    private DownloadRunner postDownloadRequest(StreamKey... keys) {
      DownloadRequest downloadRequest =
          new DownloadRequest(
              id,
              DownloadRequest.TYPE_PROGRESSIVE,
              uri,
              Arrays.asList(keys),
              /* customCacheKey= */ null,
              /* data= */ null);
      runOnMainThread(() -> downloadManager.addDownload(downloadRequest));
      return this;
    }

    private synchronized FakeDownloader addDownloader() {
      FakeDownloader fakeDownloader = new FakeDownloader();
      downloaders.add(fakeDownloader);
      return fakeDownloader;
    }

    private synchronized FakeDownloader getDownloader(int index) {
      while (downloaders.size() <= index) {
        addDownloader();
      }
      return downloaders.get(index);
    }

    private synchronized Downloader createDownloader(DownloadRequest request) {
      downloader = getDownloader(createdDownloaderCount++);
      downloader.request = request;
      return downloader;
    }

    private TaskWrapper getTask() {
      return taskWrapper;
    }

    private void assertCreatedDownloaderCount(int count) {
      assertThat(createdDownloaderCount).isEqualTo(count);
    }
  }

  private final class TaskWrapper {
    private final String taskId;

    private TaskWrapper(String taskId) {
      this.taskId = taskId;
    }

    private TaskWrapper assertDownloading() {
      return assertState(Download.STATE_DOWNLOADING);
    }

    private TaskWrapper assertCompleted() {
      return assertState(Download.STATE_COMPLETED);
    }

    private TaskWrapper assertRemoving() {
      return assertState(Download.STATE_REMOVING);
    }

    private TaskWrapper assertFailed() {
      return assertState(Download.STATE_FAILED);
    }

    private TaskWrapper assertQueued() {
      return assertState(Download.STATE_QUEUED);
    }

    private TaskWrapper assertStopped() {
      return assertState(Download.STATE_STOPPED);
    }

    private TaskWrapper assertState(@State int expectedState) {
      downloadManagerListener.assertState(taskId, expectedState, ASSERT_TRUE_TIMEOUT);
      return this;
    }

    private TaskWrapper assertRemoved() {
      downloadManagerListener.assertRemoved(taskId, ASSERT_TRUE_TIMEOUT);
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      return taskId.equals(((TaskWrapper) o).taskId);
    }

    @Override
    public int hashCode() {
      return taskId.hashCode();
    }
  }

  private static final class FakeDownloaderFactory implements DownloaderFactory {

    private final HashMap<Uri, DownloadRunner> downloaders;

    public FakeDownloaderFactory() {
      downloaders = new HashMap<>();
    }

    public void registerDownloadRunner(DownloadRunner downloadRunner) {
      assertThat(downloaders.put(downloadRunner.uri, downloadRunner)).isNull();
    }

    @Override
    public Downloader createDownloader(DownloadRequest request) {
      return downloaders.get(request.uri).createDownloader(request);
    }
  }

  private static final class FakeDownloader implements Downloader {

    private final com.google.android.exoplayer2.util.ConditionVariable blocker;

    private DownloadRequest request;
    private CountDownLatch started;
    private volatile boolean interrupted;
    private volatile boolean cancelled;
    private volatile boolean enableDownloadIOException;
    private volatile int startCount;
    private volatile int bytesDownloaded;

    private FakeDownloader() {
      this.started = new CountDownLatch(1);
      this.blocker = new com.google.android.exoplayer2.util.ConditionVariable();
    }

    @SuppressWarnings({"NonAtomicOperationOnVolatileField", "NonAtomicVolatileUpdate"})
    @Override
    public void download(ProgressListener listener) throws InterruptedException, IOException {
      // It's ok to update this directly as no other thread will update it.
      startCount++;
      started.countDown();
      block();
      if (bytesDownloaded > 0) {
        listener.onProgress(C.LENGTH_UNSET, bytesDownloaded, C.PERCENTAGE_UNSET);
      }
      if (enableDownloadIOException) {
        enableDownloadIOException = false;
        throw new IOException();
      }
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    @SuppressWarnings({"NonAtomicOperationOnVolatileField", "NonAtomicVolatileUpdate"})
    @Override
    public void remove() throws InterruptedException {
      // It's ok to update this directly as no other thread will update it.
      startCount++;
      started.countDown();
      block();
    }

    private void block() throws InterruptedException {
      try {
        while (true) {
          try {
            blocker.block();
            break;
          } catch (InterruptedException e) {
            interrupted = true;
            throw e;
          }
        }
      } finally {
        blocker.close();
      }
    }

    private FakeDownloader assertStarted() throws InterruptedException {
      return assertStarted(ASSERT_TRUE_TIMEOUT);
    }

    private FakeDownloader assertStarted(int timeout) throws InterruptedException {
      assertThat(started.await(timeout, TimeUnit.MILLISECONDS)).isTrue();
      started = new CountDownLatch(1);
      return this;
    }

    private FakeDownloader assertStartCount(int count) {
      assertThat(startCount).isEqualTo(count);
      return this;
    }

    private FakeDownloader assertReleased() throws InterruptedException {
      int count = 0;
      while (started.await(ASSERT_TRUE_TIMEOUT, TimeUnit.MILLISECONDS)) {
        if (count++ >= MAX_STARTS_BEFORE_RELEASED) {
          fail();
        }
        started = new CountDownLatch(1);
      }
      return this;
    }

    private FakeDownloader assertCanceled() throws InterruptedException {
      assertReleased();
      assertThat(interrupted).isTrue();
      assertThat(cancelled).isTrue();
      return this;
    }

    private FakeDownloader assertNotCanceled() throws InterruptedException {
      assertReleased();
      assertThat(interrupted).isFalse();
      assertThat(cancelled).isFalse();
      return this;
    }

    private FakeDownloader unblock() {
      blocker.open();
      return this;
    }

    private FakeDownloader fail() {
      enableDownloadIOException = true;
      return unblock();
    }

    private void assertDoesNotStart() throws InterruptedException {
      Thread.sleep(ASSERT_FALSE_TIME);
      assertThat(started.getCount()).isEqualTo(1);
    }

    @SuppressWarnings({"NonAtomicOperationOnVolatileField", "NonAtomicVolatileUpdate"})
    private void incrementBytesDownloaded() {
      bytesDownloaded++;
    }
  }
}
