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
package com.google.android.exoplayer2.extractor.ts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Seeking tests for {@link PsExtractor}. */
@RunWith(AndroidJUnit4.class)
public final class PsExtractorSeekTest {

  private static final String PS_FILE_PATH = "ts/elephants_dream.mpg";
  private static final int DURATION_US = 30436333;
  private static final int VIDEO_TRACK_ID = 224;
  private static final long DELTA_TIMESTAMP_THRESHOLD_US = 500_000L;
  private static final Random random = new Random(1234L);

  private FakeExtractorOutput expectedOutput;
  private FakeTrackOutput expectedTrackOutput;

  private DefaultDataSource dataSource;
  private PositionHolder positionHolder;
  private long totalInputLength;

  @Before
  public void setUp() throws IOException, InterruptedException {
    expectedOutput = new FakeExtractorOutput();
    positionHolder = new PositionHolder();
    extractAllSamplesFromFileToExpectedOutput(
        ApplicationProvider.getApplicationContext(), PS_FILE_PATH);
    expectedTrackOutput = expectedOutput.trackOutputs.get(VIDEO_TRACK_ID);

    dataSource =
        new DefaultDataSourceFactory(ApplicationProvider.getApplicationContext(), "UserAgent")
            .createDataSource();
    totalInputLength = readInputLength();
  }

  @Test
  public void testPsExtractorReads_nonSeekTableFile_returnSeekableSeekMap()
      throws IOException, InterruptedException {
    PsExtractor extractor = new PsExtractor();

    SeekMap seekMap = extractSeekMapAndTracks(extractor, new FakeExtractorOutput());

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void testHandlePendingSeek_handlesSeekingToPositionInFile_extractsCorrectFrame()
      throws IOException, InterruptedException {
    PsExtractor extractor = new PsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMapAndTracks(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(VIDEO_TRACK_ID);

    long targetSeekTimeUs = 987_000;
    int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainsTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testHandlePendingSeek_handlesSeekToEoF() throws IOException, InterruptedException {
    PsExtractor extractor = new PsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMapAndTracks(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(VIDEO_TRACK_ID);

    long targetSeekTimeUs = seekMap.getDurationUs();

    int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);
    // Assert that this seek will return a position at end of stream, without any frame.
    assertThat(extractedFrameIndex).isEqualTo(-1);
  }

  @Test
  public void testHandlePendingSeek_handlesSeekingBackward_extractsCorrectFrame()
      throws IOException, InterruptedException {
    PsExtractor extractor = new PsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMapAndTracks(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(VIDEO_TRACK_ID);

    long firstSeekTimeUs = 987_000;
    seekToTimeUs(extractor, seekMap, firstSeekTimeUs, trackOutput);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainsTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testHandlePendingSeek_handlesSeekingForward_extractsCorrectFrame()
      throws IOException, InterruptedException {
    PsExtractor extractor = new PsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMapAndTracks(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(VIDEO_TRACK_ID);

    long firstSeekTimeUs = 987_000;
    seekToTimeUs(extractor, seekMap, firstSeekTimeUs, trackOutput);

    long targetSeekTimeUs = 1_234_000;
    int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainsTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testHandlePendingSeek_handlesRandomSeeks_extractsCorrectFrame()
      throws IOException, InterruptedException {
    PsExtractor extractor = new PsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMapAndTracks(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(VIDEO_TRACK_ID);

    long numSeek = 100;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = random.nextInt(DURATION_US + 1);
      int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

      assertThat(extractedFrameIndex).isNotEqualTo(-1);
      assertFirstFrameAfterSeekContainsTargetSeekTime(
          trackOutput, targetSeekTimeUs, extractedFrameIndex);
    }
  }

  @Test
  public void testHandlePendingSeek_handlesRandomSeeksAfterReadingFileOnce_extractsCorrectFrame()
      throws IOException, InterruptedException {
    PsExtractor extractor = new PsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    readInputFileOnce(extractor, extractorOutput);
    SeekMap seekMap = extractorOutput.seekMap;
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(VIDEO_TRACK_ID);

    long numSeek = 100;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = random.nextInt(DURATION_US + 1);
      int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

      assertThat(extractedFrameIndex).isNotEqualTo(-1);
      assertFirstFrameAfterSeekContainsTargetSeekTime(
          trackOutput, targetSeekTimeUs, extractedFrameIndex);
    }
  }

  // Internal methods

  private long readInputLength() throws IOException {
    DataSpec dataSpec =
        new DataSpec(Uri.parse("asset:///" + PS_FILE_PATH), 0, C.LENGTH_UNSET, null);
    long totalInputLength = dataSource.open(dataSpec);
    Util.closeQuietly(dataSource);
    return totalInputLength;
  }

  /**
   * Seeks to the given seek time and keeps reading from input until we can extract at least one
   * frame from the seek position, or until end-of-input is reached.
   *
   * @return The index of the first extracted frame written to the given {@code trackOutput} after
   *     the seek is completed, or -1 if the seek is completed without any extracted frame.
   */
  private int seekToTimeUs(
      PsExtractor psExtractor, SeekMap seekMap, long seekTimeUs, FakeTrackOutput trackOutput)
      throws IOException, InterruptedException {
    int numSampleBeforeSeek = trackOutput.getSampleCount();
    SeekMap.SeekPoints seekPoints = seekMap.getSeekPoints(seekTimeUs);

    long initialSeekLoadPosition = seekPoints.first.position;
    psExtractor.seek(initialSeekLoadPosition, seekTimeUs);

    positionHolder.position = C.POSITION_UNSET;
    ExtractorInput extractorInput = getExtractorInputFromPosition(initialSeekLoadPosition);
    int extractorReadResult = Extractor.RESULT_CONTINUE;
    while (true) {
      try {
        // Keep reading until we can read at least one frame after seek
        while (extractorReadResult == Extractor.RESULT_CONTINUE
            && trackOutput.getSampleCount() == numSampleBeforeSeek) {
          extractorReadResult = psExtractor.read(extractorInput, positionHolder);
        }
      } finally {
        Util.closeQuietly(dataSource);
      }

      if (extractorReadResult == Extractor.RESULT_SEEK) {
        extractorInput = getExtractorInputFromPosition(positionHolder.position);
        extractorReadResult = Extractor.RESULT_CONTINUE;
      } else if (extractorReadResult == Extractor.RESULT_END_OF_INPUT) {
        return -1;
      } else if (trackOutput.getSampleCount() > numSampleBeforeSeek) {
        // First index after seek = num sample before seek.
        return numSampleBeforeSeek;
      }
    }
  }

  private SeekMap extractSeekMapAndTracks(PsExtractor extractor, FakeExtractorOutput output)
      throws IOException, InterruptedException {
    ExtractorInput input = getExtractorInputFromPosition(0);
    extractor.init(output);
    int readResult = Extractor.RESULT_CONTINUE;
    while (true) {
      try {
        // Keep reading until we can get the seek map
        while (readResult == Extractor.RESULT_CONTINUE
            && (output.seekMap == null || !output.tracksEnded)) {
          readResult = extractor.read(input, positionHolder);
        }
      } finally {
        Util.closeQuietly(dataSource);
      }

      if (readResult == Extractor.RESULT_SEEK) {
        input = getExtractorInputFromPosition(positionHolder.position);
        readResult = Extractor.RESULT_CONTINUE;
      } else if (readResult == Extractor.RESULT_END_OF_INPUT) {
        throw new IOException("EOF encountered without seekmap");
      }
      if (output.seekMap != null) {
        return output.seekMap;
      }
    }
  }

  private void readInputFileOnce(PsExtractor extractor, FakeExtractorOutput extractorOutput)
      throws IOException, InterruptedException {
    extractor.init(extractorOutput);
    int readResult = Extractor.RESULT_CONTINUE;
    ExtractorInput input = getExtractorInputFromPosition(0);
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      try {
        while (readResult == Extractor.RESULT_CONTINUE) {
          readResult = extractor.read(input, positionHolder);
        }
      } finally {
        Util.closeQuietly(dataSource);
      }
      if (readResult == Extractor.RESULT_SEEK) {
        input = getExtractorInputFromPosition(positionHolder.position);
        readResult = Extractor.RESULT_CONTINUE;
      }
    }
  }

  private void assertFirstFrameAfterSeekContainsTargetSeekTime(
      FakeTrackOutput trackOutput, long seekTimeUs, int firstFrameIndexAfterSeek) {
    long outputSampleTimeUs = trackOutput.getSampleTimeUs(firstFrameIndexAfterSeek);
    int expectedSampleIndex =
        findOutputFrameInExpectedOutput(trackOutput.getSampleData(firstFrameIndexAfterSeek));
    // Assert that after seeking, the first sample frame written to output exists in the sample list
    assertThat(expectedSampleIndex).isNotEqualTo(C.INDEX_UNSET);

    long sampleTimeUs = expectedTrackOutput.getSampleTimeUs(expectedSampleIndex);
    if (sampleTimeUs != 0) {
      // Assert that the timestamp output for first sample after seek is near the seek point.
      // For Ps seeking, unfortunately we can't guarantee exact frame seeking, since PID timestamp
      // is not too reliable.
      assertThat(Math.abs(outputSampleTimeUs - seekTimeUs))
          .isLessThan(DELTA_TIMESTAMP_THRESHOLD_US);
    }
    // Assert that the timestamp output for first sample after seek is near the actual sample
    // at seek point.
    // Note that the timestamp output for first sample after seek might *NOT* be equal to the
    // timestamp of that same sample when reading from the beginning, because if first timestamp
    // in the stream was not read before the seek, then the timestamp of the first sample after
    // the seek is just approximated from the seek point.
    assertThat(
            Math.abs(outputSampleTimeUs - expectedTrackOutput.getSampleTimeUs(expectedSampleIndex)))
        .isLessThan(DELTA_TIMESTAMP_THRESHOLD_US);
    trackOutput.assertSample(
        firstFrameIndexAfterSeek,
        expectedTrackOutput.getSampleData(expectedSampleIndex),
        outputSampleTimeUs,
        expectedTrackOutput.getSampleFlags(expectedSampleIndex),
        expectedTrackOutput.getSampleCryptoData(expectedSampleIndex));
  }

  private int findOutputFrameInExpectedOutput(byte[] sampleData) {
    for (int i = 0; i < expectedTrackOutput.getSampleCount(); i++) {
      byte[] currentSampleData = expectedTrackOutput.getSampleData(i);
      if (Arrays.equals(currentSampleData, sampleData)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  private ExtractorInput getExtractorInputFromPosition(long position) throws IOException {
    DataSpec dataSpec =
        new DataSpec(
            Uri.parse("asset:///" + PS_FILE_PATH), position, C.LENGTH_UNSET, /* key= */ null);
    dataSource.open(dataSpec);
    return new DefaultExtractorInput(dataSource, position, totalInputLength);
  }

  private void extractAllSamplesFromFileToExpectedOutput(Context context, String fileName)
      throws IOException, InterruptedException {
    byte[] data = TestUtil.getByteArray(context, fileName);

    PsExtractor extractor = new PsExtractor();
    extractor.init(expectedOutput);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = extractor.read(input, positionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        input.setPosition((int) positionHolder.position);
      }
    }
  }
}
