/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.ClippingMediaPeriod;
import com.google.android.exoplayer2.source.EmptySampleStream;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Holds a {@link MediaPeriod} with information required to play it as part of a timeline. */
/* package */ final class MediaPeriodHolder {

  private static final String TAG = "MediaPeriodHolder";

  /** The {@link MediaPeriod} wrapped by this class. */
  public final MediaPeriod mediaPeriod;
  /** The unique timeline period identifier the media period belongs to. */
  public final Object uid;
  /**
   * The sample streams for each renderer associated with this period. May contain null elements.
   */
  public final @NullableType SampleStream[] sampleStreams;

  /** Whether the media period has finished preparing. */
  public boolean prepared;
  /** Whether any of the tracks of this media period are enabled. */
  public boolean hasEnabledTracks;
  /** {@link MediaPeriodInfo} about this media period. */
  public MediaPeriodInfo info;

  private final boolean[] mayRetainStreamFlags;
  private final RendererCapabilities[] rendererCapabilities;
  private final TrackSelector trackSelector;
  private final MediaSource mediaSource;

  @Nullable private MediaPeriodHolder next;
  @Nullable private TrackGroupArray trackGroups;
  @Nullable private TrackSelectorResult trackSelectorResult;
  private long rendererPositionOffsetUs;

  /**
   * Creates a new holder with information required to play it as part of a timeline.
   *
   * @param rendererCapabilities The renderer capabilities.
   * @param rendererPositionOffsetUs The renderer time of the start of the period, in microseconds.
   * @param trackSelector The track selector.
   * @param allocator The allocator.
   * @param mediaSource The media source that produced the media period.
   * @param info Information used to identify this media period in its timeline period.
   */
  public MediaPeriodHolder(
      RendererCapabilities[] rendererCapabilities,
      long rendererPositionOffsetUs,
      TrackSelector trackSelector,
      Allocator allocator,
      MediaSource mediaSource,
      MediaPeriodInfo info) {
    this.rendererCapabilities = rendererCapabilities;
    this.rendererPositionOffsetUs = rendererPositionOffsetUs;
    this.trackSelector = trackSelector;
    this.mediaSource = mediaSource;
    this.uid = info.id.periodUid;
    this.info = info;
    sampleStreams = new SampleStream[rendererCapabilities.length];
    mayRetainStreamFlags = new boolean[rendererCapabilities.length];
    mediaPeriod =
        createMediaPeriod(
            info.id, mediaSource, allocator, info.startPositionUs, info.endPositionUs);
  }

  /**
   * Converts time relative to the start of the period to the respective renderer time using {@link
   * #getRendererOffset()}, in microseconds.
   */
  public long toRendererTime(long periodTimeUs) {
    return periodTimeUs + getRendererOffset();
  }

  /**
   * Converts renderer time to the respective time relative to the start of the period using {@link
   * #getRendererOffset()}, in microseconds.
   */
  public long toPeriodTime(long rendererTimeUs) {
    return rendererTimeUs - getRendererOffset();
  }

  /** Returns the renderer time of the start of the period, in microseconds. */
  public long getRendererOffset() {
    return rendererPositionOffsetUs;
  }

  /**
   * Sets the renderer time of the start of the period, in microseconds.
   *
   * @param rendererPositionOffsetUs The new renderer position offset, in microseconds.
   */
  public void setRendererOffset(long rendererPositionOffsetUs) {
    this.rendererPositionOffsetUs = rendererPositionOffsetUs;
  }

  /** Returns start position of period in renderer time. */
  public long getStartPositionRendererTime() {
    return info.startPositionUs + rendererPositionOffsetUs;
  }

  /** Returns whether the period is fully buffered. */
  public boolean isFullyBuffered() {
    return prepared
        && (!hasEnabledTracks || mediaPeriod.getBufferedPositionUs() == C.TIME_END_OF_SOURCE);
  }

  /**
   * Returns the buffered position in microseconds. If the period is buffered to the end, then the
   * period duration is returned.
   *
   * @return The buffered position in microseconds.
   */
  public long getBufferedPositionUs() {
    if (!prepared) {
      return info.startPositionUs;
    }
    long bufferedPositionUs =
        hasEnabledTracks ? mediaPeriod.getBufferedPositionUs() : C.TIME_END_OF_SOURCE;
    return bufferedPositionUs == C.TIME_END_OF_SOURCE ? info.durationUs : bufferedPositionUs;
  }

  /**
   * Returns the next load time relative to the start of the period, or {@link C#TIME_END_OF_SOURCE}
   * if loading has finished.
   */
  public long getNextLoadPositionUs() {
    return !prepared ? 0 : mediaPeriod.getNextLoadPositionUs();
  }

  /**
   * Handles period preparation.
   *
   * @param playbackSpeed The current playback speed.
   * @param timeline The current {@link Timeline}.
   * @throws ExoPlaybackException If an error occurs during track selection.
   */
  public void handlePrepared(float playbackSpeed, Timeline timeline) throws ExoPlaybackException {
    prepared = true;
    trackGroups = mediaPeriod.getTrackGroups();
    TrackSelectorResult selectorResult =
        Assertions.checkNotNull(selectTracks(playbackSpeed, timeline));
    long newStartPositionUs =
        applyTrackSelection(
            selectorResult, info.startPositionUs, /* forceRecreateStreams= */ false);
    rendererPositionOffsetUs += info.startPositionUs - newStartPositionUs;
    info = info.copyWithStartPositionUs(newStartPositionUs);
  }

  /**
   * Reevaluates the buffer of the media period at the given renderer position. Should only be
   * called if this is the loading media period.
   *
   * @param rendererPositionUs The playing position in renderer time, in microseconds.
   */
  public void reevaluateBuffer(long rendererPositionUs) {
    Assertions.checkState(isLoadingMediaPeriod());
    if (prepared) {
      mediaPeriod.reevaluateBuffer(toPeriodTime(rendererPositionUs));
    }
  }

  /**
   * Continues loading the media period at the given renderer position. Should only be called if
   * this is the loading media period.
   *
   * @param rendererPositionUs The load position in renderer time, in microseconds.
   */
  public void continueLoading(long rendererPositionUs) {
    Assertions.checkState(isLoadingMediaPeriod());
    long loadingPeriodPositionUs = toPeriodTime(rendererPositionUs);
    mediaPeriod.continueLoading(loadingPeriodPositionUs);
  }

  /**
   * Selects tracks for the period and returns the new result if the selection changed. Must only be
   * called if {@link #prepared} is {@code true}.
   *
   * @param playbackSpeed The current playback speed.
   * @param timeline The current {@link Timeline}.
   * @return The {@link TrackSelectorResult} if the result changed. Or null if nothing changed.
   * @throws ExoPlaybackException If an error occurs during track selection.
   */
  @Nullable
  public TrackSelectorResult selectTracks(float playbackSpeed, Timeline timeline)
      throws ExoPlaybackException {
    TrackSelectorResult selectorResult =
        trackSelector.selectTracks(rendererCapabilities, getTrackGroups(), info.id, timeline);
    if (selectorResult.isEquivalent(trackSelectorResult)) {
      return null;
    }
    for (TrackSelection trackSelection : selectorResult.selections.getAll()) {
      if (trackSelection != null) {
        trackSelection.onPlaybackSpeed(playbackSpeed);
      }
    }
    return selectorResult;
  }

  /**
   * Applies a {@link TrackSelectorResult} to the period.
   *
   * @param trackSelectorResult The {@link TrackSelectorResult} to apply.
   * @param positionUs The position relative to the start of the period at which to apply the new
   *     track selections, in microseconds.
   * @param forceRecreateStreams Whether all streams are forced to be recreated.
   * @return The actual position relative to the start of the period at which the new track
   *     selections are applied.
   */
  public long applyTrackSelection(
      TrackSelectorResult trackSelectorResult, long positionUs, boolean forceRecreateStreams) {
    return applyTrackSelection(
        trackSelectorResult,
        positionUs,
        forceRecreateStreams,
        new boolean[rendererCapabilities.length]);
  }

  /**
   * Applies a {@link TrackSelectorResult} to the period.
   *
   * @param newTrackSelectorResult The {@link TrackSelectorResult} to apply.
   * @param positionUs The position relative to the start of the period at which to apply the new
   *     track selections, in microseconds.
   * @param forceRecreateStreams Whether all streams are forced to be recreated.
   * @param streamResetFlags Will be populated to indicate which streams have been reset or were
   *     newly created.
   * @return The actual position relative to the start of the period at which the new track
   *     selections are applied.
   */
  public long applyTrackSelection(
      TrackSelectorResult newTrackSelectorResult,
      long positionUs,
      boolean forceRecreateStreams,
      boolean[] streamResetFlags) {
    for (int i = 0; i < newTrackSelectorResult.length; i++) {
      mayRetainStreamFlags[i] =
          !forceRecreateStreams && newTrackSelectorResult.isEquivalent(trackSelectorResult, i);
    }

    // Undo the effect of previous call to associate no-sample renderers with empty tracks
    // so the mediaPeriod receives back whatever it sent us before.
    disassociateNoSampleRenderersWithEmptySampleStream(sampleStreams);
    disableTrackSelectionsInResult();
    trackSelectorResult = newTrackSelectorResult;
    enableTrackSelectionsInResult();
    // Disable streams on the period and get new streams for updated/newly-enabled tracks.
    TrackSelectionArray trackSelections = newTrackSelectorResult.selections;
    positionUs =
        mediaPeriod.selectTracks(
            trackSelections.getAll(),
            mayRetainStreamFlags,
            sampleStreams,
            streamResetFlags,
            positionUs);
    associateNoSampleRenderersWithEmptySampleStream(sampleStreams);

    // Update whether we have enabled tracks and sanity check the expected streams are non-null.
    hasEnabledTracks = false;
    for (int i = 0; i < sampleStreams.length; i++) {
      if (sampleStreams[i] != null) {
        Assertions.checkState(newTrackSelectorResult.isRendererEnabled(i));
        // hasEnabledTracks should be true only when non-empty streams exists.
        if (rendererCapabilities[i].getTrackType() != C.TRACK_TYPE_NONE) {
          hasEnabledTracks = true;
        }
      } else {
        Assertions.checkState(trackSelections.get(i) == null);
      }
    }
    return positionUs;
  }

  /** Releases the media period. No other method should be called after the release. */
  public void release() {
    disableTrackSelectionsInResult();
    trackSelectorResult = null;
    releaseMediaPeriod(info.endPositionUs, mediaSource, mediaPeriod);
  }

  /**
   * Sets the next media period holder in the queue.
   *
   * @param nextMediaPeriodHolder The next holder, or null if this will be the new loading media
   *     period holder at the end of the queue.
   */
  public void setNext(@Nullable MediaPeriodHolder nextMediaPeriodHolder) {
    if (nextMediaPeriodHolder == next) {
      return;
    }
    disableTrackSelectionsInResult();
    next = nextMediaPeriodHolder;
    enableTrackSelectionsInResult();
  }

  /**
   * Returns the next media period holder in the queue, or null if this is the last media period
   * (and thus the loading media period).
   */
  @Nullable
  public MediaPeriodHolder getNext() {
    return next;
  }

  /**
   * Returns the {@link TrackGroupArray} exposed by this media period. Must only be called if {@link
   * #prepared} is {@code true}.
   */
  public TrackGroupArray getTrackGroups() {
    return Assertions.checkNotNull(trackGroups);
  }

  /**
   * Returns the {@link TrackSelectorResult} which is currently applied. Must only be called if
   * {@link #prepared} is {@code true}.
   */
  public TrackSelectorResult getTrackSelectorResult() {
    return Assertions.checkNotNull(trackSelectorResult);
  }

  private void enableTrackSelectionsInResult() {
    TrackSelectorResult trackSelectorResult = this.trackSelectorResult;
    if (!isLoadingMediaPeriod() || trackSelectorResult == null) {
      return;
    }
    for (int i = 0; i < trackSelectorResult.length; i++) {
      boolean rendererEnabled = trackSelectorResult.isRendererEnabled(i);
      TrackSelection trackSelection = trackSelectorResult.selections.get(i);
      if (rendererEnabled && trackSelection != null) {
        trackSelection.enable();
      }
    }
  }

  private void disableTrackSelectionsInResult() {
    TrackSelectorResult trackSelectorResult = this.trackSelectorResult;
    if (!isLoadingMediaPeriod() || trackSelectorResult == null) {
      return;
    }
    for (int i = 0; i < trackSelectorResult.length; i++) {
      boolean rendererEnabled = trackSelectorResult.isRendererEnabled(i);
      TrackSelection trackSelection = trackSelectorResult.selections.get(i);
      if (rendererEnabled && trackSelection != null) {
        trackSelection.disable();
      }
    }
  }

  /**
   * For each renderer of type {@link C#TRACK_TYPE_NONE}, we will remove the dummy {@link
   * EmptySampleStream} that was associated with it.
   */
  private void disassociateNoSampleRenderersWithEmptySampleStream(
      @NullableType SampleStream[] sampleStreams) {
    for (int i = 0; i < rendererCapabilities.length; i++) {
      if (rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE) {
        sampleStreams[i] = null;
      }
    }
  }

  /**
   * For each renderer of type {@link C#TRACK_TYPE_NONE} that was enabled, we will associate it with
   * a dummy {@link EmptySampleStream}.
   */
  private void associateNoSampleRenderersWithEmptySampleStream(
      @NullableType SampleStream[] sampleStreams) {
    TrackSelectorResult trackSelectorResult = Assertions.checkNotNull(this.trackSelectorResult);
    for (int i = 0; i < rendererCapabilities.length; i++) {
      if (rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE
          && trackSelectorResult.isRendererEnabled(i)) {
        sampleStreams[i] = new EmptySampleStream();
      }
    }
  }

  private boolean isLoadingMediaPeriod() {
    return next == null;
  }

  /** Returns a media period corresponding to the given {@code id}. */
  private static MediaPeriod createMediaPeriod(
      MediaPeriodId id,
      MediaSource mediaSource,
      Allocator allocator,
      long startPositionUs,
      long endPositionUs) {
    MediaPeriod mediaPeriod = mediaSource.createPeriod(id, allocator, startPositionUs);
    if (endPositionUs != C.TIME_UNSET && endPositionUs != C.TIME_END_OF_SOURCE) {
      mediaPeriod =
          new ClippingMediaPeriod(
              mediaPeriod, /* enableInitialDiscontinuity= */ true, /* startUs= */ 0, endPositionUs);
    }
    return mediaPeriod;
  }

  /** Releases the given {@code mediaPeriod}, logging and suppressing any errors. */
  private static void releaseMediaPeriod(
      long endPositionUs, MediaSource mediaSource, MediaPeriod mediaPeriod) {
    try {
      if (endPositionUs != C.TIME_UNSET && endPositionUs != C.TIME_END_OF_SOURCE) {
        mediaSource.releasePeriod(((ClippingMediaPeriod) mediaPeriod).mediaPeriod);
      } else {
        mediaSource.releasePeriod(mediaPeriod);
      }
    } catch (RuntimeException e) {
      // There's nothing we can do.
      Log.e(TAG, "Period release failed.", e);
    }
  }
}
