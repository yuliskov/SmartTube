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

package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import java.util.List;

/** Estimates track bitrate values. */
public interface TrackBitrateEstimator {

  /**
   * A {@link TrackBitrateEstimator} that returns the bitrate values defined in the track formats.
   */
  TrackBitrateEstimator DEFAULT =
      (formats, queue, iterators, bitrates) ->
          TrackSelectionUtil.getFormatBitrates(formats, bitrates);

  /**
   * Returns bitrate values for a set of tracks whose formats are given.
   *
   * @param formats The track formats.
   * @param queue The queue of already buffered {@link MediaChunk} instances. Must not be modified.
   * @param iterators An array of {@link MediaChunkIterator}s providing information about the
   *     sequence of upcoming media chunks for each track.
   * @param bitrates An array into which the bitrate values will be written. If non-null, this array
   *     is the one that will be returned.
   * @return Bitrate values for the tracks. As long as the format of a track has set bitrate, a
   *     bitrate value is set in the returned array. Otherwise it might be set to {@link
   *     Format#NO_VALUE}.
   */
  int[] getBitrates(
      Format[] formats,
      List<? extends MediaChunk> queue,
      MediaChunkIterator[] iterators,
      @Nullable int[] bitrates);
}
