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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Loads media corresponding to a {@link Timeline.Period}, and allows that media to be read. All
 * methods are called on the player's internal playback thread, as described in the
 * {@link ExoPlayer} Javadoc.
 */
public interface MediaPeriod extends SequenceableLoader {

  /**
   * A callback to be notified of {@link MediaPeriod} events.
   */
  interface Callback extends SequenceableLoader.Callback<MediaPeriod> {

    /**
     * Called when preparation completes.
     *
     * <p>Called on the playback thread. After invoking this method, the {@link MediaPeriod} can
     * expect for {@link #selectTracks(TrackSelection[], boolean[], SampleStream[], boolean[],
     * long)} to be called with the initial track selection.
     *
     * @param mediaPeriod The prepared {@link MediaPeriod}.
     */
    void onPrepared(MediaPeriod mediaPeriod);
  }

  /**
   * Prepares this media period asynchronously.
   *
   * <p>{@code callback.onPrepared} is called when preparation completes. If preparation fails,
   * {@link #maybeThrowPrepareError()} will throw an {@link IOException}.
   *
   * <p>If preparation succeeds and results in a source timeline change (e.g. the period duration
   * becoming known), {@link
   * MediaSource.SourceInfoRefreshListener#onSourceInfoRefreshed(MediaSource, Timeline, Object)}
   * will be called before {@code callback.onPrepared}.
   *
   * @param callback Callback to receive updates from this period, including being notified when
   *     preparation completes.
   * @param positionUs The expected starting position, in microseconds.
   */
  void prepare(Callback callback, long positionUs);

  /**
   * Throws an error that's preventing the period from becoming prepared. Does nothing if no such
   * error exists.
   *
   * <p>This method is only called before the period has completed preparation.
   *
   * @throws IOException The underlying error.
   */
  void maybeThrowPrepareError() throws IOException;

  /**
   * Returns the {@link TrackGroup}s exposed by the period.
   *
   * <p>This method is only called after the period has been prepared.
   *
   * @return The {@link TrackGroup}s.
   */
  TrackGroupArray getTrackGroups();

  /**
   * Returns a list of {@link StreamKey StreamKeys} which allow to filter the media in this period
   * to load only the parts needed to play the provided {@link TrackSelection TrackSelections}.
   *
   * <p>This method is only called after the period has been prepared.
   *
   * @param trackSelections The {@link TrackSelection TrackSelections} describing the tracks for
   *     which stream keys are requested.
   * @return The corresponding {@link StreamKey StreamKeys} for the selected tracks, or an empty
   *     list if filtering is not possible and the entire media needs to be loaded to play the
   *     selected tracks.
   */
  default List<StreamKey> getStreamKeys(List<TrackSelection> trackSelections) {
    return Collections.emptyList();
  }

  /**
   * Performs a track selection.
   *
   * <p>The call receives track {@code selections} for each renderer, {@code mayRetainStreamFlags}
   * indicating whether the existing {@link SampleStream} can be retained for each selection, and
   * the existing {@code stream}s themselves. The call will update {@code streams} to reflect the
   * provided selections, clearing, setting and replacing entries as required. If an existing sample
   * stream is retained but with the requirement that the consuming renderer be reset, then the
   * corresponding flag in {@code streamResetFlags} will be set to true. This flag will also be set
   * if a new sample stream is created.
   *
   * <p>Note that previously received {@link TrackSelection TrackSelections} are no longer valid and
   * references need to be replaced even if the corresponding {@link SampleStream} is kept.
   *
   * <p>This method is only called after the period has been prepared.
   *
   * @param selections The renderer track selections.
   * @param mayRetainStreamFlags Flags indicating whether the existing sample stream can be retained
   *     for each selection. A {@code true} value indicates that the selection is unchanged, and
   *     that the caller does not require that the sample stream be recreated.
   * @param streams The existing sample streams, which will be updated to reflect the provided
   *     selections.
   * @param streamResetFlags Will be updated to indicate new sample streams, and sample streams that
   *     have been retained but with the requirement that the consuming renderer be reset.
   * @param positionUs The current playback position in microseconds. If playback of this period has
   *     not yet started, the value will be the starting position.
   * @return The actual position at which the tracks were enabled, in microseconds.
   */
  long selectTracks(
      @NullableType TrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs);

  /**
   * Discards buffered media up to the specified position.
   *
   * <p>This method is only called after the period has been prepared.
   *
   * @param positionUs The position in microseconds.
   * @param toKeyframe If true then for each track discards samples up to the keyframe before or at
   *     the specified position, rather than any sample before or at that position.
   */
  void discardBuffer(long positionUs, boolean toKeyframe);

  /**
   * Attempts to read a discontinuity.
   *
   * <p>After this method has returned a value other than {@link C#TIME_UNSET}, all {@link
   * SampleStream}s provided by the period are guaranteed to start from a key frame.
   *
   * <p>This method is only called after the period has been prepared and before reading from any
   * {@link SampleStream}s provided by the period.
   *
   * @return If a discontinuity was read then the playback position in microseconds after the
   *     discontinuity. Else {@link C#TIME_UNSET}.
   */
  long readDiscontinuity();

  /**
   * Attempts to seek to the specified position in microseconds.
   *
   * <p>After this method has been called, all {@link SampleStream}s provided by the period are
   * guaranteed to start from a key frame.
   *
   * <p>This method is only called when at least one track is selected.
   *
   * @param positionUs The seek position in microseconds.
   * @return The actual position to which the period was seeked, in microseconds.
   */
  long seekToUs(long positionUs);

  /**
   * Returns the position to which a seek will be performed, given the specified seek position and
   * {@link SeekParameters}.
   *
   * <p>This method is only called after the period has been prepared.
   *
   * @param positionUs The seek position in microseconds.
   * @param seekParameters Parameters that control how the seek is performed. Implementations may
   *     apply seek parameters on a best effort basis.
   * @return The actual position to which a seek will be performed, in microseconds.
   */
  long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters);

  // SequenceableLoader interface. Overridden to provide more specific documentation.

  /**
   * Returns an estimate of the position up to which data is buffered for the enabled tracks.
   *
   * <p>This method is only called when at least one track is selected.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   *     {@link C#TIME_END_OF_SOURCE} if the track is fully buffered.
   */
  @Override
  long getBufferedPositionUs();

  /**
   * Returns the next load time, or {@link C#TIME_END_OF_SOURCE} if loading has finished.
   *
   * <p>This method is only called after the period has been prepared. It may be called when no
   * tracks are selected.
   */
  @Override
  long getNextLoadPositionUs();

  /**
   * Attempts to continue loading.
   *
   * <p>This method may be called both during and after the period has been prepared.
   *
   * <p>A period may call {@link Callback#onContinueLoadingRequested(SequenceableLoader)} on the
   * {@link Callback} passed to {@link #prepare(Callback, long)} to request that this method be
   * called when the period is permitted to continue loading data. A period may do this both during
   * and after preparation.
   *
   * @param positionUs The current playback position in microseconds. If playback of this period has
   *     not yet started, the value will be the starting position in this period minus the duration
   *     of any media in previous periods still to be played.
   * @return True if progress was made, meaning that {@link #getNextLoadPositionUs()} will return a
   *     different value than prior to the call. False otherwise.
   */
  @Override
  boolean continueLoading(long positionUs);

  /**
   * Re-evaluates the buffer given the playback position.
   *
   * <p>This method is only called after the period has been prepared.
   *
   * <p>A period may choose to discard buffered media so that it can be re-buffered in a different
   * quality.
   *
   * @param positionUs The current playback position in microseconds. If playback of this period has
   *     not yet started, the value will be the starting position in this period minus the duration
   *     of any media in previous periods still to be played.
   */
  @Override
  void reevaluateBuffer(long positionUs);
}
