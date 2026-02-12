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
package com.google.android.exoplayer2.source.chunk;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;

/**
 * An abstract base class for {@link Chunk}s that contain media samples.
 */
public abstract class MediaChunk extends Chunk {

  /** The chunk index, or {@link C#INDEX_UNSET} if it is not known. */
  public final long chunkIndex;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param trackFormat See {@link #trackFormat}.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param startTimeUs The start time of the media contained by the chunk, in microseconds.
   * @param endTimeUs The end time of the media contained by the chunk, in microseconds.
   * @param chunkIndex The index of the chunk, or {@link C#INDEX_UNSET} if it is not known.
   */
  public MediaChunk(
      DataSource dataSource,
      DataSpec dataSpec,
      Format trackFormat,
      int trackSelectionReason,
      Object trackSelectionData,
      long startTimeUs,
      long endTimeUs,
      long chunkIndex) {
    super(dataSource, dataSpec, C.DATA_TYPE_MEDIA, trackFormat, trackSelectionReason,
        trackSelectionData, startTimeUs, endTimeUs);
    Assertions.checkNotNull(trackFormat);
    this.chunkIndex = chunkIndex;
  }

  /** Returns the next chunk index or {@link C#INDEX_UNSET} if it is not known. */
  public long getNextChunkIndex() {
    return chunkIndex != C.INDEX_UNSET ? chunkIndex + 1 : C.INDEX_UNSET;
  }

  /**
   * Returns whether the chunk has been fully loaded.
   */
  public abstract boolean isLoadCompleted();

}
