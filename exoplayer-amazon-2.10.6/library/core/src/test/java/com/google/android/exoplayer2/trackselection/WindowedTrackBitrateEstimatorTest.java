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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.testutil.FakeMediaChunk;
import com.google.android.exoplayer2.testutil.FakeMediaChunkIterator;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** {@link WindowedTrackBitrateEstimator} tests. */
@RunWith(AndroidJUnit4.class)
public class WindowedTrackBitrateEstimatorTest {

  private static final long MAX_DURATION_MS = 30_000;

  @Test
  public void getBitrates_zeroMaxDuration_returnsFormatBitrates() {
    WindowedTrackBitrateEstimator estimator =
        new WindowedTrackBitrateEstimator(
            /* maxPastDurationMs= */ 0,
            /* maxFutureDurationMs= */ 0,
            /* useFormatBitrateAsLowerBound= */ false);
    MediaChunk chunk = createMediaChunk(/* formatBitrate= */ 5, /* actualBitrate= */ 10);
    MediaChunkIterator iterator1 = createMediaChunkIteratorWithBitrate(8);
    MediaChunkIterator iterator2 = createMediaChunkIteratorWithBitrate(16);
    Format format1 = createFormatWithBitrate(10);
    Format format2 = createFormatWithBitrate(20);

    int[] bitrates =
        estimator.getBitrates(
            new Format[] {format1, format2},
            Collections.singletonList(chunk),
            new MediaChunkIterator[] {iterator1, iterator2},
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(10, 20).inOrder();
  }

  @Test
  public void getBitrates_futureMaxDurationSet_returnsEstimateUsingFutureChunks() {
    WindowedTrackBitrateEstimator estimator =
        new WindowedTrackBitrateEstimator(
            /* maxPastDurationMs= */ 0, MAX_DURATION_MS, /* useFormatBitrateAsLowerBound= */ false);
    MediaChunk chunk = createMediaChunk(/* formatBitrate= */ 5, /* actualBitrate= */ 10);
    MediaChunkIterator iterator1 = createMediaChunkIteratorWithBitrate(8);
    MediaChunkIterator iterator2 = createMediaChunkIteratorWithBitrate(16);
    Format format1 = createFormatWithBitrate(10);
    Format format2 = createFormatWithBitrate(20);

    int[] bitrates =
        estimator.getBitrates(
            new Format[] {format1, format2},
            Collections.singletonList(chunk),
            new MediaChunkIterator[] {iterator1, iterator2},
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(8, 16).inOrder();
  }

  @Test
  public void getBitrates_pastMaxDurationSet_returnsEstimateUsingPastChunks() {
    WindowedTrackBitrateEstimator estimator =
        new WindowedTrackBitrateEstimator(
            MAX_DURATION_MS,
            /* maxFutureDurationMs= */ 0,
            /* useFormatBitrateAsLowerBound= */ false);
    MediaChunk chunk = createMediaChunk(/* formatBitrate= */ 5, /* actualBitrate= */ 10);
    MediaChunkIterator iterator1 = createMediaChunkIteratorWithBitrate(8);
    MediaChunkIterator iterator2 = createMediaChunkIteratorWithBitrate(16);
    Format format1 = createFormatWithBitrate(10);
    Format format2 = createFormatWithBitrate(20);

    int[] bitrates =
        estimator.getBitrates(
            new Format[] {format1, format2},
            Collections.singletonList(chunk),
            new MediaChunkIterator[] {iterator1, iterator2},
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(16, 32).inOrder();
  }

  @Test
  public void
      getBitrates_useFormatBitrateAsLowerBoundSetTrue_returnsEstimateIfOnlyHigherThanFormat() {
    WindowedTrackBitrateEstimator estimator =
        new WindowedTrackBitrateEstimator(
            MAX_DURATION_MS, MAX_DURATION_MS, /* useFormatBitrateAsLowerBound= */ true);
    MediaChunk chunk = createMediaChunk(/* formatBitrate= */ 5, /* actualBitrate= */ 10);
    MediaChunkIterator iterator1 = createMediaChunkIteratorWithBitrate(80);
    MediaChunkIterator iterator2 = createMediaChunkIteratorWithBitrate(16);
    Format format1 = createFormatWithBitrate(10);
    Format format2 = createFormatWithBitrate(20);

    int[] bitrates =
        estimator.getBitrates(
            new Format[] {format1, format2},
            Collections.singletonList(chunk),
            new MediaChunkIterator[] {iterator1, iterator2},
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(80, 20).inOrder();
  }

  @Test
  public void getBitrates_bitratesArrayGiven_returnsTheSameArray() {
    WindowedTrackBitrateEstimator estimator =
        new WindowedTrackBitrateEstimator(
            MAX_DURATION_MS, MAX_DURATION_MS, /* useFormatBitrateAsLowerBound= */ true);
    MediaChunk chunk = createMediaChunk(/* formatBitrate= */ 5, /* actualBitrate= */ 10);
    MediaChunkIterator iterator1 = createMediaChunkIteratorWithBitrate(8);
    MediaChunkIterator iterator2 = createMediaChunkIteratorWithBitrate(16);
    Format format1 = createFormatWithBitrate(10);
    Format format2 = createFormatWithBitrate(20);

    int[] bitratesArrayToUse = new int[2];
    int[] bitrates =
        estimator.getBitrates(
            new Format[] {format1, format2},
            Collections.singletonList(chunk),
            new MediaChunkIterator[] {iterator1, iterator2},
            bitratesArrayToUse);

    assertThat(bitrates).isSameAs(bitratesArrayToUse);
  }

  private static MediaChunk createMediaChunk(int formatBitrate, int actualBitrate) {
    int length = actualBitrate / C.BITS_PER_BYTE;
    DataSpec dataSpec =
        new DataSpec(
            Uri.EMPTY, /* absoluteStreamPosition= */ 0, length, /* key= */ null, /* flags= */ 0);
    Format format = createFormatWithBitrate(formatBitrate);
    return new FakeMediaChunk(
        dataSpec, format, /* startTimeUs= */ 0L, /* endTimeUs= */ C.MICROS_PER_SECOND);
  }

  private static Format createFormatWithBitrate(int bitrate) {
    return Format.createSampleFormat(
        /* id= */ null,
        /* sampleMimeType= */ null,
        /* codecs= */ null,
        bitrate,
        /* drmInitData= */ null);
  }

  private static MediaChunkIterator createMediaChunkIteratorWithBitrate(int bitrate) {
    return new FakeMediaChunkIterator(
        /* chunkTimeBoundariesSec= */ new long[] {0, 1},
        /* chunkLengths= */ new long[] {bitrate / C.BITS_PER_BYTE});
  }
}
