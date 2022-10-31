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
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.testutil.FakeMediaChunk;
import com.google.android.exoplayer2.testutil.FakeMediaChunkIterator;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** {@link TrackSelectionUtil} tests. */
@RunWith(AndroidJUnit4.class)
public class TrackSelectionUtilTest {

  public static final long MAX_DURATION_US = 30 * C.MICROS_PER_SECOND;

  @Test
  public void getAverageBitrate_emptyIterator_returnsNoValue() {
    assertThat(TrackSelectionUtil.getAverageBitrate(MediaChunkIterator.EMPTY, MAX_DURATION_US))
        .isEqualTo(Format.NO_VALUE);
  }

  @Test
  public void getAverageBitrate_oneChunk_returnsChunkBitrate() {
    long[] chunkTimeBoundariesSec = {12, 17};
    long[] chunkLengths = {10};

    FakeMediaChunkIterator iterator =
        new FakeMediaChunkIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US)).isEqualTo(16);
  }

  @Test
  public void getAverageBitrate_multipleSameDurationChunks_returnsAverageChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 10};
    long[] chunkLengths = {10, 20};

    FakeMediaChunkIterator iterator =
        new FakeMediaChunkIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US)).isEqualTo(24);
  }

  @Test
  public void getAverageBitrate_multipleDifferentDurationChunks_returnsAverageChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {10, 20, 30};

    FakeMediaChunkIterator iterator =
        new FakeMediaChunkIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US)).isEqualTo(16);
  }

  @Test
  public void getAverageBitrate_firstChunkLengthUnset_returnsNoValue() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {C.LENGTH_UNSET, 20, 30};

    FakeMediaChunkIterator iterator =
        new FakeMediaChunkIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US))
        .isEqualTo(Format.NO_VALUE);
  }

  @Test
  public void getAverageBitrate_secondChunkLengthUnset_returnsFirstChunkBitrate() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 30};
    long[] chunkLengths = {10, C.LENGTH_UNSET, 30};

    FakeMediaChunkIterator iterator =
        new FakeMediaChunkIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, MAX_DURATION_US)).isEqualTo(16);
  }

  @Test
  public void
      getAverageBitrate_chunksExceedingMaxDuration_returnsAverageChunkBitrateUpToMaxDuration() {
    long[] chunkTimeBoundariesSec = {0, 5, 15, 45, 50};
    long[] chunkLengths = {10, 20, 30, 100};
    FakeMediaChunkIterator iterator =
        new FakeMediaChunkIterator(chunkTimeBoundariesSec, chunkLengths);

    long maxDurationUs = 30 * C.MICROS_PER_SECOND;
    int averageBitrate = TrackSelectionUtil.getAverageBitrate(iterator, maxDurationUs);

    assertThat(averageBitrate).isEqualTo(12);
  }

  @Test
  public void getAverageBitrate_zeroMaxDuration_returnsNoValue() {
    long[] chunkTimeBoundariesSec = {0, 5, 10};
    long[] chunkLengths = {10, 20};

    FakeMediaChunkIterator iterator =
        new FakeMediaChunkIterator(chunkTimeBoundariesSec, chunkLengths);

    assertThat(TrackSelectionUtil.getAverageBitrate(iterator, /* maxDurationUs= */ 0))
        .isEqualTo(Format.NO_VALUE);
  }

  @Test
  public void getBitratesUsingFutureInfo_noIterator_returnsEmptyArray() {
    assertThat(
            TrackSelectionUtil.getBitratesUsingFutureInfo(
                new MediaChunkIterator[0], new Format[0], MAX_DURATION_US, /* bitrates= */ null))
        .hasLength(0);
  }

  @Test
  public void getBitratesUsingFutureInfo_emptyIterator_returnsNoValue() {
    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingFutureInfo(
            new MediaChunkIterator[] {MediaChunkIterator.EMPTY},
            new Format[] {createFormatWithBitrate(10)},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(Format.NO_VALUE);
  }

  @Test
  public void getBitratesUsingFutureInfo_twoTracksZeroMaxDuration_returnsNoValue() {
    FakeMediaChunkIterator iterator1 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 10}, /* chunkLengths= */ new long[] {10});
    FakeMediaChunkIterator iterator2 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5, 15, 30},
            /* chunkLengths= */ new long[] {10, 20, 30});

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingFutureInfo(
            new MediaChunkIterator[] {iterator1, iterator2},
            new Format[] {createFormatWithBitrate(10), createFormatWithBitrate(20)},
            /* maxDurationUs= */ 0,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(Format.NO_VALUE, Format.NO_VALUE);
  }

  @Test
  public void getBitratesUsingFutureInfo_twoTracks_returnsBitrates() {
    FakeMediaChunkIterator iterator1 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 10}, /* chunkLengths= */ new long[] {10});
    FakeMediaChunkIterator iterator2 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5, 15, 30},
            /* chunkLengths= */ new long[] {10, 20, 30});

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingFutureInfo(
            new MediaChunkIterator[] {iterator1, iterator2},
            new Format[] {createFormatWithBitrate(10), createFormatWithBitrate(20)},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(8, 16).inOrder();
  }

  @Test
  public void getBitratesUsingFutureInfo_bitratesArrayGiven_returnsTheSameArray() {
    FakeMediaChunkIterator iterator1 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 10}, /* chunkLengths= */ new long[] {10});
    FakeMediaChunkIterator iterator2 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5, 15, 30},
            /* chunkLengths= */ new long[] {10, 20, 30});

    int[] bitratesArrayToUse = new int[2];
    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingFutureInfo(
            new MediaChunkIterator[] {iterator1, iterator2},
            new Format[] {createFormatWithBitrate(10), createFormatWithBitrate(20)},
            MAX_DURATION_US,
            bitratesArrayToUse);

    assertThat(bitrates).isSameAs(bitratesArrayToUse);
  }

  @Test
  public void getBitratesUsingFutureInfo_emptyIterator_returnsEstimationUsingClosest() {
    FakeMediaChunkIterator iterator1 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5}, /* chunkLengths= */ new long[] {10});
    Format format1 = createFormatWithBitrate(10);
    MediaChunkIterator iterator2 = MediaChunkIterator.EMPTY;
    Format format2 = createFormatWithBitrate(20);
    FakeMediaChunkIterator iterator3 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5}, /* chunkLengths= */ new long[] {50});
    Format format3 = createFormatWithBitrate(25);
    FakeMediaChunkIterator iterator4 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5}, /* chunkLengths= */ new long[] {20});
    Format format4 = createFormatWithBitrate(30);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingFutureInfo(
            new MediaChunkIterator[] {iterator1, iterator2, iterator3, iterator4},
            new Format[] {format1, format2, format3, format4},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(16, 64, 80, 32).inOrder();
  }

  @Test
  public void getBitratesUsingFutureInfo_formatWithoutBitrate_returnsNoValueForEmpty() {
    FakeMediaChunkIterator iterator1 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5}, /* chunkLengths= */ new long[] {10});
    Format format1 = createFormatWithBitrate(10);
    MediaChunkIterator iterator2 = MediaChunkIterator.EMPTY;
    Format format2 = createFormatWithBitrate(Format.NO_VALUE);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingFutureInfo(
            new MediaChunkIterator[] {iterator1, iterator2},
            new Format[] {format1, format2},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(16, Format.NO_VALUE).inOrder();
  }

  @Test
  public void getBitratesUsingPastInfo_noFormat_returnsEmptyArray() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(10),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk), new Format[0], MAX_DURATION_US, /* bitrates= */ null);

    assertThat(bitrates).hasLength(0);
  }

  @Test
  public void getBitratesUsingPastInfo_emptyQueue_returnsNoValue() {
    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.emptyList(),
            new Format[] {createFormatWithBitrate(10)},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(Format.NO_VALUE);
  }

  @Test
  public void getBitratesUsingPastInfo_oneChunkFormatNoBitrate_returnsNoValue() {
    Format format = createFormatWithBitrate(Format.NO_VALUE);
    FakeMediaChunk chunk =
        createChunk(format, /* length= */ 10, /* startTimeSec= */ 0, /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk),
            new Format[] {format},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(Format.NO_VALUE);
  }

  @Test
  public void getBitratesUsingPastInfo_oneChunkNoLength_returnsNoValue() {
    Format format = createFormatWithBitrate(10);
    FakeMediaChunk chunk =
        createChunk(
            format, /* length= */ C.LENGTH_UNSET, /* startTimeSec= */ 0, /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk),
            new Format[] {format},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(Format.NO_VALUE);
  }

  @Test
  public void getBitratesUsingPastInfo_oneChunkWithSameFormat_returnsBitrates() {
    Format format = createFormatWithBitrate(10);
    FakeMediaChunk chunk =
        createChunk(format, /* length= */ 10, /* startTimeSec= */ 0, /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk),
            new Format[] {format},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(8).inOrder();
  }

  @Test
  public void getBitratesUsingPastInfo_zeroMaxDuration_returnsNoValue() {
    Format format = createFormatWithBitrate(10);
    FakeMediaChunk chunk =
        createChunk(format, /* length= */ 10, /* startTimeSec= */ 0, /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk),
            new Format[] {format},
            /* maxDurationUs= */ 0,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(Format.NO_VALUE).inOrder();
  }

  @Test
  public void getBitratesUsingPastInfo_multipleChunkWithSameFormat_returnsAverageBitrate() {
    Format format = createFormatWithBitrate(10);
    FakeMediaChunk chunk =
        createChunk(format, /* length= */ 10, /* startTimeSec= */ 0, /* endTimeSec= */ 10);
    FakeMediaChunk chunk2 =
        createChunk(format, /* length= */ 20, /* startTimeSec= */ 10, /* endTimeSec= */ 20);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Arrays.asList(chunk, chunk2),
            new Format[] {format},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(12).inOrder();
  }

  @Test
  public void getBitratesUsingPastInfo_oneChunkWithDifferentFormat_returnsEstimationBitrate() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(10),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk),
            new Format[] {createFormatWithBitrate(20)},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(16).inOrder();
  }

  @Test
  public void getBitratesUsingPastInfo_trackFormatNoBitrate_returnsNoValue() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(10),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk),
            new Format[] {createFormatWithBitrate(Format.NO_VALUE)},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(Format.NO_VALUE);
  }

  @Test
  public void getBitratesUsingPastInfo_multipleTracks_returnsBitrates() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(10),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk),
            new Format[] {createFormatWithBitrate(20), createFormatWithBitrate(30)},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(16, 24).inOrder();
  }

  @Test
  public void getBitratesUsingPastInfo_bitratesArrayGiven_returnsTheSameArray() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(10),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);

    int[] bitratesArrayToUse = new int[2];
    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Collections.singletonList(chunk),
            new Format[] {createFormatWithBitrate(20), createFormatWithBitrate(30)},
            MAX_DURATION_US,
            bitratesArrayToUse);

    assertThat(bitrates).isSameAs(bitratesArrayToUse);
  }

  @Test
  public void
      getBitratesUsingPastInfo_multipleChunkExceedingMaxDuration_returnsAverageUntilMaxDuration() {
    Format format = createFormatWithBitrate(10);
    FakeMediaChunk chunk =
        createChunk(format, /* length= */ 10, /* startTimeSec= */ 0, /* endTimeSec= */ 20);
    FakeMediaChunk chunk2 =
        createChunk(format, /* length= */ 40, /* startTimeSec= */ 20, /* endTimeSec= */ 40);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Arrays.asList(chunk, chunk2),
            new Format[] {format},
            /* maxDurationUs= */ 30 * C.MICROS_PER_SECOND,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(12).inOrder();
  }

  @Test
  public void
      getBitratesUsingPastInfo_chunksWithDifferentFormats_returnsChunkAverageBitrateForLastFormat() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(10),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);
    FakeMediaChunk chunk2 =
        createChunk(
            createFormatWithBitrate(20),
            /* length= */ 40,
            /* startTimeSec= */ 10,
            /* endTimeSec= */ 20);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastInfo(
            Arrays.asList(chunk, chunk2),
            new Format[] {createFormatWithBitrate(10)},
            MAX_DURATION_US,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(16).inOrder();
  }

  @Test
  public void getBitratesUsingPastAndFutureInfo_noPastInfo_returnsBitratesUsingOnlyFutureInfo() {
    FakeMediaChunkIterator iterator1 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 10}, /* chunkLengths= */ new long[] {10});
    FakeMediaChunkIterator iterator2 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5, 15, 30},
            /* chunkLengths= */ new long[] {10, 20, 30});

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastAndFutureInfo(
            new Format[] {createFormatWithBitrate(10), createFormatWithBitrate(20)},
            Collections.emptyList(),
            MAX_DURATION_US,
            new MediaChunkIterator[] {iterator1, iterator2},
            MAX_DURATION_US,
            /* useFormatBitrateAsLowerBound= */ false,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(8, 16).inOrder();
  }

  @Test
  public void getBitratesUsingPastAndFutureInfo_noFutureInfo_returnsBitratesUsingOnlyPastInfo() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(10),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastAndFutureInfo(
            new Format[] {createFormatWithBitrate(20), createFormatWithBitrate(30)},
            Collections.singletonList(chunk),
            MAX_DURATION_US,
            new MediaChunkIterator[] {MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY},
            MAX_DURATION_US,
            /* useFormatBitrateAsLowerBound= */ false,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(16, 24).inOrder();
  }

  @Test
  public void
      getBitratesUsingPastAndFutureInfo_pastAndFutureInfo_returnsBitratesUsingOnlyFutureInfo() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(5),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);
    FakeMediaChunkIterator iterator1 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 10}, /* chunkLengths= */ new long[] {10});
    FakeMediaChunkIterator iterator2 =
        new FakeMediaChunkIterator(
            /* chunkTimeBoundariesSec= */ new long[] {0, 5, 15, 30},
            /* chunkLengths= */ new long[] {10, 20, 30});

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastAndFutureInfo(
            new Format[] {createFormatWithBitrate(10), createFormatWithBitrate(20)},
            Collections.singletonList(chunk),
            MAX_DURATION_US,
            new MediaChunkIterator[] {iterator1, iterator2},
            MAX_DURATION_US,
            /* useFormatBitrateAsLowerBound= */ false,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(8, 16).inOrder();
  }

  @Test
  public void getBitratesUsingPastAndFutureInfo_noPastAndFutureInfo_returnsBitratesOfFormats() {
    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastAndFutureInfo(
            new Format[] {createFormatWithBitrate(10), createFormatWithBitrate(20)},
            Collections.emptyList(),
            MAX_DURATION_US,
            new MediaChunkIterator[] {MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY},
            MAX_DURATION_US,
            /* useFormatBitrateAsLowerBound= */ false,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(10, 20).inOrder();
  }

  @Test
  public void
      getBitratesUsingPastAndFutureInfo_estimatesLowerAndUseFormatBitrateAsLowerBoundTrue_returnsBitratesOfFormats() {
    FakeMediaChunk chunk =
        createChunk(
            createFormatWithBitrate(10),
            /* length= */ 10,
            /* startTimeSec= */ 0,
            /* endTimeSec= */ 10);

    int[] bitrates =
        TrackSelectionUtil.getBitratesUsingPastAndFutureInfo(
            new Format[] {createFormatWithBitrate(20), createFormatWithBitrate(30)},
            Collections.singletonList(chunk),
            MAX_DURATION_US,
            new MediaChunkIterator[] {MediaChunkIterator.EMPTY, MediaChunkIterator.EMPTY},
            MAX_DURATION_US,
            /* useFormatBitrateAsLowerBound= */ true,
            /* bitrates= */ null);

    assertThat(bitrates).asList().containsExactly(20, 30).inOrder();
  }

  private static FakeMediaChunk createChunk(
      Format format, int length, int startTimeSec, int endTimeSec) {
    DataSpec dataSpec =
        new DataSpec(
            Uri.EMPTY, /* absoluteStreamPosition= */ 0, length, /* key= */ null, /* flags= */ 0);
    return new FakeMediaChunk(
        dataSpec, format, startTimeSec * C.MICROS_PER_SECOND, endTimeSec * C.MICROS_PER_SECOND);
  }

  private static Format createFormatWithBitrate(int bitrate) {
    return Format.createSampleFormat(
        /* id= */ null,
        /* sampleMimeType= */ null,
        /* codecs= */ null,
        bitrate,
        /* drmInitData= */ null);
  }
}
