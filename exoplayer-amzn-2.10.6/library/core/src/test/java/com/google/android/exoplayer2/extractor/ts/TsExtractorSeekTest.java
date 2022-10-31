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

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Seeking tests for {@link TsExtractor}. */
@RunWith(AndroidJUnit4.class)
public final class TsExtractorSeekTest {

  private static final String TEST_FILE = "ts/bbb_2500ms.ts";
  private static final int DURATION_US = 2_500_000;
  private static final int AUDIO_TRACK_ID = 257;
  private static final long MAXIMUM_TIMESTAMP_DELTA_US = 500_000L;

  private static final Random random = new Random(1234L);

  private FakeTrackOutput expectedTrackOutput;
  private DefaultDataSource dataSource;
  private PositionHolder positionHolder;

  @Before
  public void setUp() throws IOException, InterruptedException {
    positionHolder = new PositionHolder();
    expectedTrackOutput =
        TestUtil.extractAllSamplesFromFile(
                new TsExtractor(), ApplicationProvider.getApplicationContext(), TEST_FILE)
            .trackOutputs
            .get(AUDIO_TRACK_ID);

    dataSource =
        new DefaultDataSourceFactory(ApplicationProvider.getApplicationContext(), "UserAgent")
            .createDataSource();
  }

  @Test
  public void testTsExtractorReads_nonSeekTableFile_returnSeekableSeekMap()
      throws IOException, InterruptedException {
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE);
    TsExtractor extractor = new TsExtractor();

    SeekMap seekMap =
        TestUtil.extractSeekMap(extractor, new FakeExtractorOutput(), dataSource, fileUri);

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void testHandlePendingSeek_handlesSeekingToPositionInFile_extractsCorrectFrame()
      throws IOException, InterruptedException {
    TsExtractor extractor = new TsExtractor();
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(AUDIO_TRACK_ID);

    long targetSeekTimeUs = 987_000;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testHandlePendingSeek_handlesSeekToEoF_extractsLastFrame()
      throws IOException, InterruptedException {
    TsExtractor extractor = new TsExtractor();
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(AUDIO_TRACK_ID);

    long targetSeekTimeUs = seekMap.getDurationUs();

    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testHandlePendingSeek_handlesSeekingBackward_extractsCorrectFrame()
      throws IOException, InterruptedException {
    TsExtractor extractor = new TsExtractor();
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(AUDIO_TRACK_ID);

    long firstSeekTimeUs = 987_000;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testHandlePendingSeek_handlesSeekingForward_extractsCorrectFrame()
      throws IOException, InterruptedException {
    TsExtractor extractor = new TsExtractor();
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(AUDIO_TRACK_ID);

    long firstSeekTimeUs = 987_000;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);

    long targetSeekTimeUs = 1_234_000;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testHandlePendingSeek_handlesRandomSeeks_extractsCorrectFrame()
      throws IOException, InterruptedException {
    TsExtractor extractor = new TsExtractor();
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(AUDIO_TRACK_ID);

    long numSeek = 100;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = random.nextInt(DURATION_US + 1);
      int extractedFrameIndex =
          TestUtil.seekToTimeUs(
              extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

      assertThat(extractedFrameIndex).isNotEqualTo(-1);
      assertFirstFrameAfterSeekContainTargetSeekTime(
          trackOutput, targetSeekTimeUs, extractedFrameIndex);
    }
  }

  @Test
  public void testHandlePendingSeek_handlesRandomSeeksAfterReadingFileOnce_extractsCorrectFrame()
      throws IOException, InterruptedException {
    TsExtractor extractor = new TsExtractor();
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    readInputFileOnce(extractor, extractorOutput, fileUri);
    SeekMap seekMap = extractorOutput.seekMap;
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(AUDIO_TRACK_ID);

    long numSeek = 100;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = random.nextInt(DURATION_US + 1);
      int extractedFrameIndex =
          TestUtil.seekToTimeUs(
              extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

      assertThat(extractedFrameIndex).isNotEqualTo(-1);
      assertFirstFrameAfterSeekContainTargetSeekTime(
          trackOutput, targetSeekTimeUs, extractedFrameIndex);
    }
  }

  // Internal methods

  private void readInputFileOnce(
      TsExtractor extractor, FakeExtractorOutput extractorOutput, Uri fileUri)
      throws IOException, InterruptedException {
    extractor.init(extractorOutput);
    int readResult = Extractor.RESULT_CONTINUE;
    ExtractorInput input = TestUtil.getExtractorInputFromPosition(dataSource, 0, fileUri);
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      try {
        while (readResult == Extractor.RESULT_CONTINUE) {
          readResult = extractor.read(input, positionHolder);
        }
      } finally {
        Util.closeQuietly(dataSource);
      }
      if (readResult == Extractor.RESULT_SEEK) {
        input =
            TestUtil.getExtractorInputFromPosition(dataSource, positionHolder.position, fileUri);
        readResult = Extractor.RESULT_CONTINUE;
      }
    }
  }

  private void assertFirstFrameAfterSeekContainTargetSeekTime(
      FakeTrackOutput trackOutput, long seekTimeUs, int firstFrameIndexAfterSeek) {
    long outputSampleTimeUs = trackOutput.getSampleTimeUs(firstFrameIndexAfterSeek);
    int expectedSampleIndex =
        findOutputFrameInExpectedOutput(trackOutput.getSampleData(firstFrameIndexAfterSeek));
    // Assert that after seeking, the first sample frame written to output exists in the sample list
    assertThat(expectedSampleIndex).isNotEqualTo(-1);
    // Assert that the timestamp output for first sample after seek is near the seek point.
    // For Ts seeking, unfortunately we can't guarantee exact frame seeking, since PID timestamp is
    // not too reliable.
    assertThat(Math.abs(outputSampleTimeUs - seekTimeUs)).isLessThan(MAXIMUM_TIMESTAMP_DELTA_US);
    // Assert that the timestamp output for first sample after seek is near the actual sample
    // at seek point.
    // Note that the timestamp output for first sample after seek might *NOT* be equal to the
    // timestamp of that same sample when reading from the beginning, because if first timestamp in
    // the stream was not read before the seek, then the timestamp of the first sample after the
    // seek is just approximated from the seek point.
    assertThat(
            Math.abs(outputSampleTimeUs - expectedTrackOutput.getSampleTimeUs(expectedSampleIndex)))
        .isLessThan(MAXIMUM_TIMESTAMP_DELTA_US);
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
    return -1;
  }
}
