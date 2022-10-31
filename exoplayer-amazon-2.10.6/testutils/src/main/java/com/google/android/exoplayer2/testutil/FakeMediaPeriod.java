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

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import java.util.Collections;

/**
 * Fake {@link MediaPeriod} that provides tracks from the given {@link TrackGroupArray}. Selecting
 * tracks will give the player {@link FakeSampleStream}s. Loading data completes immediately after
 * the period has finished preparing.
 */
public class FakeMediaPeriod implements MediaPeriod {

  public static final DataSpec FAKE_DATA_SPEC = new DataSpec(Uri.parse("http://fake.uri"));

  private final TrackGroupArray trackGroupArray;
  protected final EventDispatcher eventDispatcher;

  @Nullable private Handler playerHandler;
  @Nullable private Callback prepareCallback;

  private boolean deferOnPrepared;
  private boolean notifiedReadingStarted;
  private boolean prepared;
  private long seekOffsetUs;
  private long discontinuityPositionUs;

  /**
   * @param trackGroupArray The track group array.
   * @param eventDispatcher A dispatcher for media source events.
   */
  public FakeMediaPeriod(TrackGroupArray trackGroupArray, EventDispatcher eventDispatcher) {
    this(trackGroupArray, eventDispatcher, /* deferOnPrepared */ false);
  }

  /**
   * @param trackGroupArray The track group array.
   * @param eventDispatcher A dispatcher for media source events.
   * @param deferOnPrepared Whether {@link MediaPeriod.Callback#onPrepared(MediaPeriod)} should be
   *     called only after {@link #setPreparationComplete()} has been called. If {@code false}
   *     preparation completes immediately.
   */
  public FakeMediaPeriod(
      TrackGroupArray trackGroupArray, EventDispatcher eventDispatcher, boolean deferOnPrepared) {
    this.trackGroupArray = trackGroupArray;
    this.eventDispatcher = eventDispatcher;
    this.deferOnPrepared = deferOnPrepared;
    discontinuityPositionUs = C.TIME_UNSET;
    eventDispatcher.mediaPeriodCreated();
  }

  /**
   * Sets a discontinuity position to be returned from the next call to
   * {@link #readDiscontinuity()}.
   *
   * @param discontinuityPositionUs The position to be returned, in microseconds.
   */
  public void setDiscontinuityPositionUs(long discontinuityPositionUs) {
    this.discontinuityPositionUs = discontinuityPositionUs;
  }

  /**
   * Allows the fake media period to complete preparation. May be called on any thread.
   */
  public synchronized void setPreparationComplete() {
    deferOnPrepared = false;
    if (playerHandler != null && prepareCallback != null) {
      playerHandler.post(this::finishPreparation);
    }
  }

  /**
   * Sets an offset to be applied to positions returned by {@link #seekToUs(long)}.
   *
   * @param seekOffsetUs The offset to be applied, in microseconds.
   */
  public void setSeekToUsOffset(long seekOffsetUs) {
    this.seekOffsetUs = seekOffsetUs;
  }

  public void release() {
    prepared = false;
    eventDispatcher.mediaPeriodReleased();
  }

  @Override
  public synchronized void prepare(Callback callback, long positionUs) {
    eventDispatcher.loadStarted(
        FAKE_DATA_SPEC,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        /* mediaEndTimeUs = */ C.TIME_UNSET,
        SystemClock.elapsedRealtime());
    prepareCallback = callback;
    if (deferOnPrepared) {
      playerHandler = new Handler();
    } else {
      finishPreparation();
    }
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    // Do nothing.
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    assertThat(prepared).isTrue();
    return trackGroupArray;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    assertThat(prepared).isTrue();
    int rendererCount = selections.length;
    for (int i = 0; i < rendererCount; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        streams[i] = null;
      }
      if (streams[i] == null && selections[i] != null) {
        TrackSelection selection = selections[i];
        assertThat(selection.length()).isAtLeast(1);
        TrackGroup trackGroup = selection.getTrackGroup();
        assertThat(trackGroupArray.indexOf(trackGroup) != C.INDEX_UNSET).isTrue();
        int indexInTrackGroup = selection.getIndexInTrackGroup(selection.getSelectedIndex());
        assertThat(indexInTrackGroup).isAtLeast(0);
        assertThat(indexInTrackGroup).isLessThan(trackGroup.length);
        streams[i] = createSampleStream(selection);
        streamResetFlags[i] = true;
      }
    }
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    // Do nothing.
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  @Override
  public long readDiscontinuity() {
    assertThat(prepared).isTrue();
    if (!notifiedReadingStarted) {
      eventDispatcher.readingStarted();
      notifiedReadingStarted = true;
    }
    long positionDiscontinuityUs = this.discontinuityPositionUs;
    this.discontinuityPositionUs = C.TIME_UNSET;
    return positionDiscontinuityUs;
  }

  @Override
  public long getBufferedPositionUs() {
    assertThat(prepared).isTrue();
    return C.TIME_END_OF_SOURCE;
  }

  @Override
  public long seekToUs(long positionUs) {
    assertThat(prepared).isTrue();
    return positionUs + seekOffsetUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  @Override
  public long getNextLoadPositionUs() {
    assertThat(prepared).isTrue();
    return C.TIME_END_OF_SOURCE;
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return false;
  }

  protected SampleStream createSampleStream(TrackSelection selection) {
    return new FakeSampleStream(
        selection.getSelectedFormat(), eventDispatcher, /* shouldOutputSample= */ true);
  }

  private void finishPreparation() {
    prepared = true;
    prepareCallback.onPrepared(this);
    eventDispatcher.loadCompleted(
        FAKE_DATA_SPEC,
        FAKE_DATA_SPEC.uri,
        /* responseHeaders= */ Collections.emptyMap(),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        /* mediaEndTimeUs = */ C.TIME_UNSET,
        SystemClock.elapsedRealtime(),
        /* loadDurationMs= */ 0,
        /* bytesLoaded= */ 100);
  }
}
