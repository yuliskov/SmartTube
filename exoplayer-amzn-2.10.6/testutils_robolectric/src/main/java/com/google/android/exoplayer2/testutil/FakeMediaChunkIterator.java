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

package com.google.android.exoplayer2.testutil;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkIterator;
import com.google.android.exoplayer2.upstream.DataSpec;

/** Fake {@link com.google.android.exoplayer2.source.chunk.MediaChunkIterator}. */
public final class FakeMediaChunkIterator extends BaseMediaChunkIterator {

  private final long[] chunkTimeBoundariesSec;
  private final long[] chunkLengths;

  /**
   * Creates a fake {@link com.google.android.exoplayer2.source.chunk.MediaChunkIterator}.
   *
   * @param chunkTimeBoundariesSec An array containing the time boundaries where one chunk ends and
   *     the next one starts. The first value is the start time of the first chunk and the last
   *     value is the end time of the last chunk. The array should be of length (chunk-count + 1).
   * @param chunkLengths An array which contains the length of each chunk, should be of length
   *     (chunk-count).
   */
  public FakeMediaChunkIterator(long[] chunkTimeBoundariesSec, long[] chunkLengths) {
    super(/* fromIndex= */ 0, /* toIndex= */ chunkTimeBoundariesSec.length - 2);
    this.chunkTimeBoundariesSec = chunkTimeBoundariesSec;
    this.chunkLengths = chunkLengths;
  }

  @Override
  public DataSpec getDataSpec() {
    checkInBounds();
    return new DataSpec(
        Uri.EMPTY,
        /* absoluteStreamPosition= */ 0,
        chunkLengths[(int) getCurrentIndex()],
        /* key= */ null);
  }

  @Override
  public long getChunkStartTimeUs() {
    checkInBounds();
    return chunkTimeBoundariesSec[(int) getCurrentIndex()] * C.MICROS_PER_SECOND;
  }

  @Override
  public long getChunkEndTimeUs() {
    checkInBounds();
    return chunkTimeBoundariesSec[(int) getCurrentIndex() + 1] * C.MICROS_PER_SECOND;
  }
}
