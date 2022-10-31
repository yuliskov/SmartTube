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
package com.google.android.exoplayer2.extractor.mp4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;

/**
 * Rechunks fixed sample size media in which every sample is a key frame (e.g. uncompressed audio).
 */
/* package */ final class FixedSampleSizeRechunker {

  /**
   * The result of a rechunking operation.
   */
  public static final class Results {

    public final long[] offsets;
    public final int[] sizes;
    public final int maximumSize;
    public final long[] timestamps;
    public final int[] flags;
    public final long duration;

    private Results(
        long[] offsets,
        int[] sizes,
        int maximumSize,
        long[] timestamps,
        int[] flags,
        long duration) {
      this.offsets = offsets;
      this.sizes = sizes;
      this.maximumSize = maximumSize;
      this.timestamps = timestamps;
      this.flags = flags;
      this.duration = duration;
    }

  }

  /**
   * Maximum number of bytes for each buffer in rechunked output.
   */
  private static final int MAX_SAMPLE_SIZE = 8 * 1024;

  /**
   * Rechunk the given fixed sample size input to produce a new sequence of samples.
   *
   * @param fixedSampleSize Size in bytes of each sample.
   * @param chunkOffsets Chunk offsets in the MP4 stream to rechunk.
   * @param chunkSampleCounts Sample counts for each of the MP4 stream's chunks.
   * @param timestampDeltaInTimeUnits Timestamp delta between each sample in time units.
   */
  public static Results rechunk(int fixedSampleSize, long[] chunkOffsets, int[] chunkSampleCounts,
      long timestampDeltaInTimeUnits) {
    int maxSampleCount = MAX_SAMPLE_SIZE / fixedSampleSize;

    // Count the number of new, rechunked buffers.
    int rechunkedSampleCount = 0;
    for (int chunkSampleCount : chunkSampleCounts) {
      rechunkedSampleCount += Util.ceilDivide(chunkSampleCount, maxSampleCount);
    }

    long[] offsets = new long[rechunkedSampleCount];
    int[] sizes = new int[rechunkedSampleCount];
    int maximumSize = 0;
    long[] timestamps = new long[rechunkedSampleCount];
    int[] flags = new int[rechunkedSampleCount];

    int originalSampleIndex = 0;
    int newSampleIndex = 0;
    for (int chunkIndex = 0; chunkIndex < chunkSampleCounts.length; chunkIndex++) {
      int chunkSamplesRemaining = chunkSampleCounts[chunkIndex];
      long sampleOffset = chunkOffsets[chunkIndex];

      while (chunkSamplesRemaining > 0) {
        int bufferSampleCount = Math.min(maxSampleCount, chunkSamplesRemaining);

        offsets[newSampleIndex] = sampleOffset;
        sizes[newSampleIndex] = fixedSampleSize * bufferSampleCount;
        maximumSize = Math.max(maximumSize, sizes[newSampleIndex]);
        timestamps[newSampleIndex] = (timestampDeltaInTimeUnits * originalSampleIndex);
        flags[newSampleIndex] = C.BUFFER_FLAG_KEY_FRAME;

        sampleOffset += sizes[newSampleIndex];
        originalSampleIndex += bufferSampleCount;

        chunkSamplesRemaining -= bufferSampleCount;
        newSampleIndex++;
      }
    }
    long duration = timestampDeltaInTimeUnits * originalSampleIndex;

    return new Results(offsets, sizes, maximumSize, timestamps, flags, duration);
  }

  private FixedSampleSizeRechunker() {
    // Prevent instantiation.
  }
}
