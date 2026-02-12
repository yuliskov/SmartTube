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
package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * A {@link TrackSelection} consisting of a single track.
 */
public final class FixedTrackSelection extends BaseTrackSelection {

  /**
   * @deprecated Don't use as adaptive track selection factory as it will throw when multiple tracks
   *     are selected. If you would like to disable adaptive selection in {@link
   *     DefaultTrackSelector}, enable the {@link
   *     DefaultTrackSelector.Parameters#forceHighestSupportedBitrate} flag instead.
   */
  @Deprecated
  public static final class Factory implements TrackSelection.Factory {

    private final int reason;
    private final @Nullable Object data;

    public Factory() {
      this.reason = C.SELECTION_REASON_UNKNOWN;
      this.data = null;
    }

    /**
     * @param reason A reason for the track selection.
     * @param data Optional data associated with the track selection.
     */
    public Factory(int reason, @Nullable Object data) {
      this.reason = reason;
      this.data = data;
    }

    @Override
    public @NullableType TrackSelection[] createTrackSelections(
        @NullableType Definition[] definitions, BandwidthMeter bandwidthMeter) {
      return TrackSelectionUtil.createTrackSelectionsForDefinitions(
          definitions,
          definition ->
              new FixedTrackSelection(definition.group, definition.tracks[0], reason, data));
    }
  }

  private final int reason;
  private final @Nullable Object data;

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param track The index of the selected track within the {@link TrackGroup}.
   */
  public FixedTrackSelection(TrackGroup group, int track) {
    this(group, track, C.SELECTION_REASON_UNKNOWN, null);
  }

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param track The index of the selected track within the {@link TrackGroup}.
   * @param reason A reason for the track selection.
   * @param data Optional data associated with the track selection.
   */
  public FixedTrackSelection(TrackGroup group, int track, int reason, @Nullable Object data) {
    super(group, track);
    this.reason = reason;
    this.data = data;
  }

  @Override
  public void updateSelectedTrack(
      long playbackPositionUs,
      long bufferedDurationUs,
      long availableDurationUs,
      List<? extends MediaChunk> queue,
      MediaChunkIterator[] mediaChunkIterators) {
    // Do nothing.
  }

  @Override
  public int getSelectedIndex() {
    return 0;
  }

  @Override
  public int getSelectionReason() {
    return reason;
  }

  @Override
  public @Nullable Object getSelectionData() {
    return data;
  }

}
