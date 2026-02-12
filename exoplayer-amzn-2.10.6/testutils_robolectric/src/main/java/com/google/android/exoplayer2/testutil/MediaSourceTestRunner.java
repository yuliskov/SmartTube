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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A runner for {@link MediaSource} tests. */
public class MediaSourceTestRunner {

  public static final int TIMEOUT_MS = 10000;

  private final MediaSource mediaSource;
  private final MediaSourceListener mediaSourceListener;
  private final HandlerThread playbackThread;
  private final Handler playbackHandler;
  private final Allocator allocator;

  private final LinkedBlockingDeque<Timeline> timelines;
  private final CopyOnWriteArrayList<Pair<Integer, MediaPeriodId>> completedLoads;
  private final AtomicReference<MediaPeriodId> lastCreatedMediaPeriod;
  private final AtomicReference<MediaPeriodId> lastReleasedMediaPeriod;

  private Timeline timeline;

  /**
   * @param mediaSource The source under test.
   * @param allocator The allocator to use during the test run.
   */
  public MediaSourceTestRunner(MediaSource mediaSource, Allocator allocator) {
    this.mediaSource = mediaSource;
    this.allocator = allocator;
    playbackThread = new HandlerThread("PlaybackThread");
    playbackThread.start();
    Looper playbackLooper = playbackThread.getLooper();
    playbackHandler = new Handler(playbackLooper);
    mediaSourceListener = new MediaSourceListener();
    timelines = new LinkedBlockingDeque<>();
    completedLoads = new CopyOnWriteArrayList<>();
    lastCreatedMediaPeriod = new AtomicReference<>();
    lastReleasedMediaPeriod = new AtomicReference<>();
    mediaSource.addEventListener(playbackHandler, mediaSourceListener);
  }

  /**
   * Runs the provided {@link Runnable} on the playback thread, blocking until execution completes.
   *
   * @param runnable The {@link Runnable} to run.
   */
  public void runOnPlaybackThread(final Runnable runnable) {
    final Throwable[] throwable = new Throwable[1];
    final ConditionVariable finishedCondition = new ConditionVariable();
    playbackHandler.post(
        () -> {
          try {
            runnable.run();
          } catch (Throwable e) {
            throwable[0] = e;
          } finally {
            finishedCondition.open();
          }
        });
    assertThat(finishedCondition.block(TIMEOUT_MS)).isTrue();
    if (throwable[0] != null) {
      Util.sneakyThrow(throwable[0]);
    }
  }

  /**
   * Prepares the source on the playback thread, asserting that it provides an initial timeline.
   *
   * @return The initial {@link Timeline}.
   */
  public Timeline prepareSource() throws IOException {
    final IOException[] prepareError = new IOException[1];
    runOnPlaybackThread(
        () -> {
          mediaSource.prepareSource(mediaSourceListener, /* mediaTransferListener= */ null);
          try {
            // TODO: This only catches errors that are set synchronously in prepareSource. To
            // capture async errors we'll need to poll maybeThrowSourceInfoRefreshError until the
            // first call to onSourceInfoRefreshed.
            mediaSource.maybeThrowSourceInfoRefreshError();
          } catch (IOException e) {
            prepareError[0] = e;
          }
        });
    if (prepareError[0] != null) {
      throw prepareError[0];
    }
    return assertTimelineChangeBlocking();
  }

  /**
   * Calls {@link MediaSource#createPeriod(MediaSource.MediaPeriodId, Allocator, long)} with a zero
   * start position on the playback thread, asserting that a non-null {@link MediaPeriod} is
   * returned.
   *
   * @param periodId The id of the period to create.
   * @return The created {@link MediaPeriod}.
   */
  public MediaPeriod createPeriod(final MediaPeriodId periodId) {
    return createPeriod(periodId, /* startPositionUs= */ 0);
  }

  /**
   * Calls {@link MediaSource#createPeriod(MediaSource.MediaPeriodId, Allocator, long)} on the
   * playback thread, asserting that a non-null {@link MediaPeriod} is returned.
   *
   * @param periodId The id of the period to create.
   * @return The created {@link MediaPeriod}.
   */
  public MediaPeriod createPeriod(final MediaPeriodId periodId, long startPositionUs) {
    final MediaPeriod[] holder = new MediaPeriod[1];
    runOnPlaybackThread(
        () -> holder[0] = mediaSource.createPeriod(periodId, allocator, startPositionUs));
    assertThat(holder[0]).isNotNull();
    return holder[0];
  }

  /**
   * Calls {@link MediaPeriod#prepare(MediaPeriod.Callback, long)} on the playback thread and blocks
   * until the method has been called.
   *
   * @param mediaPeriod The {@link MediaPeriod} to prepare.
   * @param positionUs The position at which to prepare.
   * @return A {@link CountDownLatch} that will be counted down when preparation completes.
   */
  public CountDownLatch preparePeriod(final MediaPeriod mediaPeriod, final long positionUs) {
    final ConditionVariable prepareCalled = new ConditionVariable();
    final CountDownLatch preparedCountDown = new CountDownLatch(1);
    runOnPlaybackThread(
        () -> {
          mediaPeriod.prepare(
              new MediaPeriod.Callback() {
                @Override
                public void onPrepared(MediaPeriod mediaPeriod1) {
                  preparedCountDown.countDown();
                }

                @Override
                public void onContinueLoadingRequested(MediaPeriod source) {
                  // Do nothing.
                }
              },
              positionUs);
          prepareCalled.open();
        });
    prepareCalled.block();
    return preparedCountDown;
  }

  /**
   * Calls {@link MediaSource#releasePeriod(MediaPeriod)} on the playback thread.
   *
   * @param mediaPeriod The {@link MediaPeriod} to release.
   */
  public void releasePeriod(final MediaPeriod mediaPeriod) {
    runOnPlaybackThread(() -> mediaSource.releasePeriod(mediaPeriod));
  }

  /**
   * Calls {@link MediaSource#releaseSource(MediaSource.SourceInfoRefreshListener)} on the playback
   * thread.
   */
  public void releaseSource() {
    runOnPlaybackThread(() -> mediaSource.releaseSource(mediaSourceListener));
  }

  /**
   * Asserts that the source has not notified its listener of a timeline change since the last call
   * to {@link #assertTimelineChangeBlocking()} or {@link #assertTimelineChange()} (or since the
   * runner was created if neither method has been called).
   */
  public void assertNoTimelineChange() {
    assertThat(timelines.isEmpty()).isTrue();
  }

  /**
   * Asserts that the source has notified its listener of a single timeline change.
   *
   * @return The new {@link Timeline}.
   */
  public Timeline assertTimelineChange() {
    timeline = timelines.removeFirst();
    assertNoTimelineChange();
    return timeline;
  }

  /**
   * Asserts that the source notifies its listener of a single timeline change. If the source has
   * not yet notified its listener, it has up to the timeout passed to the constructor to do so.
   *
   * @return The new {@link Timeline}.
   */
  public Timeline assertTimelineChangeBlocking() {
    try {
      timeline = timelines.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(timeline).isNotNull(); // Null indicates the poll timed out.
      assertNoTimelineChange();
      return timeline;
    } catch (InterruptedException e) {
      // Should never happen.
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates and releases all periods (including ad periods) defined in the last timeline to be
   * returned from {@link #prepareSource()}, {@link #assertTimelineChange()} or {@link
   * #assertTimelineChangeBlocking()}. The {@link MediaPeriodId#windowSequenceNumber} is set to the
   * index of the window.
   */
  public void assertPrepareAndReleaseAllPeriods() throws InterruptedException {
    Timeline.Period period = new Timeline.Period();
    for (int i = 0; i < timeline.getPeriodCount(); i++) {
      timeline.getPeriod(i, period, /* setIds= */ true);
      assertPrepareAndReleasePeriod(new MediaPeriodId(period.uid, period.windowIndex));
      for (int adGroupIndex = 0; adGroupIndex < period.getAdGroupCount(); adGroupIndex++) {
        for (int adIndex = 0; adIndex < period.getAdCountInAdGroup(adGroupIndex); adIndex++) {
          assertPrepareAndReleasePeriod(
              new MediaPeriodId(period.uid, adGroupIndex, adIndex, period.windowIndex));
        }
      }
    }
  }

  private void assertPrepareAndReleasePeriod(MediaPeriodId mediaPeriodId)
      throws InterruptedException {
    MediaPeriod mediaPeriod = createPeriod(mediaPeriodId);
    assertThat(lastCreatedMediaPeriod.getAndSet(/* newValue= */ null)).isEqualTo(mediaPeriodId);
    CountDownLatch preparedCondition = preparePeriod(mediaPeriod, 0);
    assertThat(preparedCondition.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    // MediaSource is supposed to support multiple calls to createPeriod without an intervening call
    // to releasePeriod.
    MediaPeriodId secondMediaPeriodId =
        new MediaPeriodId(
            mediaPeriodId.periodUid,
            mediaPeriodId.adGroupIndex,
            mediaPeriodId.adIndexInAdGroup,
            mediaPeriodId.windowSequenceNumber + 1000);
    MediaPeriod secondMediaPeriod = createPeriod(secondMediaPeriodId);
    assertThat(lastCreatedMediaPeriod.getAndSet(/* newValue= */ null))
        .isEqualTo(secondMediaPeriodId);
    CountDownLatch secondPreparedCondition = preparePeriod(secondMediaPeriod, 0);
    assertThat(secondPreparedCondition.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    // Release the periods.
    releasePeriod(mediaPeriod);
    assertThat(lastReleasedMediaPeriod.getAndSet(/* newValue= */ null)).isEqualTo(mediaPeriodId);
    releasePeriod(secondMediaPeriod);
    assertThat(lastReleasedMediaPeriod.getAndSet(/* newValue= */ null))
        .isEqualTo(secondMediaPeriodId);
  }

  /**
   * Asserts that the media source reported completed loads via {@link
   * MediaSourceEventListener#onLoadCompleted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)} for
   * each specified window index and a null period id. Also asserts that no other loads with media
   * period id null are reported.
   */
  public void assertCompletedManifestLoads(Integer... windowIndices) {
    List<Integer> expectedWindowIndices = new ArrayList<>(Arrays.asList(windowIndices));
    for (Pair<Integer, MediaPeriodId> windowIndexAndMediaPeriodId : completedLoads) {
      if (windowIndexAndMediaPeriodId.second == null) {
        boolean loadExpected = expectedWindowIndices.remove(windowIndexAndMediaPeriodId.first);
        assertThat(loadExpected).isTrue();
      }
    }
    assertWithMessage("Not all expected media source loads have been completed.")
        .that(expectedWindowIndices)
        .isEmpty();
  }

  /**
   * Asserts that the media source reported completed loads via {@link
   * MediaSourceEventListener#onLoadCompleted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)} for
   * each specified media period id, and asserts that the associated window index matches the one in
   * the last known timeline returned from {@link #prepareSource()}, {@link #assertTimelineChange()}
   * or {@link #assertTimelineChangeBlocking()}.
   */
  public void assertCompletedMediaPeriodLoads(MediaPeriodId... mediaPeriodIds) {
    Timeline.Period period = new Timeline.Period();
    HashSet<MediaPeriodId> expectedLoads = new HashSet<>(Arrays.asList(mediaPeriodIds));
    for (Pair<Integer, MediaPeriodId> windowIndexAndMediaPeriodId : completedLoads) {
      int windowIndex = windowIndexAndMediaPeriodId.first;
      MediaPeriodId mediaPeriodId = windowIndexAndMediaPeriodId.second;
      if (expectedLoads.remove(mediaPeriodId)) {
        int periodIndex = timeline.getIndexOfPeriod(mediaPeriodId.periodUid);
        assertThat(windowIndex).isEqualTo(timeline.getPeriod(periodIndex, period).windowIndex);
      }
    }
    assertWithMessage("Not all expected media source loads have been completed.")
        .that(expectedLoads)
        .isEmpty();
  }

  /** Releases the runner. Should be called when the runner is no longer required. */
  public void release() {
    playbackThread.quit();
  }

  private class MediaSourceListener
      implements MediaSource.SourceInfoRefreshListener, MediaSourceEventListener {

    // SourceInfoRefreshListener methods.

    @Override
    public void onSourceInfoRefreshed(MediaSource source, Timeline timeline, Object manifest) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
      timelines.addLast(timeline);
    }

    // MediaSourceEventListener methods.

    @Override
    public void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
      lastCreatedMediaPeriod.set(mediaPeriodId);
    }

    @Override
    public void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
      lastReleasedMediaPeriod.set(mediaPeriodId);
    }

    @Override
    public void onLoadStarted(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
    }

    @Override
    public void onLoadCompleted(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
      completedLoads.add(Pair.create(windowIndex, mediaPeriodId));
    }

    @Override
    public void onLoadCanceled(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
    }

    @Override
    public void onLoadError(
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
    }

    @Override
    public void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
    }

    @Override
    public void onUpstreamDiscarded(
        int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
    }

    @Override
    public void onDownstreamFormatChanged(
        int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
      Assertions.checkState(Looper.myLooper() == playbackThread.getLooper());
    }
  }
}
