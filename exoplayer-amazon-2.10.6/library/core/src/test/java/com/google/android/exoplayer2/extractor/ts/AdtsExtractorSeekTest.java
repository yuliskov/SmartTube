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
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link AdtsExtractor}. */
@RunWith(AndroidJUnit4.class)
public final class AdtsExtractorSeekTest {

  private static final Random random = new Random(1234L);

  private static final String TEST_FILE = "ts/sample.adts";
  private static final int FILE_DURATION_US = 3_356_772;
  private static final long DELTA_TIMESTAMP_THRESHOLD_US = 200_000;

  private FakeTrackOutput expectedTrackOutput;
  private DefaultDataSource dataSource;

  @Before
  public void setUp() {
    dataSource =
        new DefaultDataSourceFactory(ApplicationProvider.getApplicationContext(), "UserAgent")
            .createDataSource();
  }

  @Test
  public void testAdtsExtractorReads_returnSeekableSeekMap()
      throws IOException, InterruptedException {
    String fileName = TEST_FILE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    expectedTrackOutput =
        TestUtil.extractAllSamplesFromFile(
                createAdtsExtractor(), ApplicationProvider.getApplicationContext(), fileName)
            .trackOutputs
            .get(0);

    AdtsExtractor extractor = createAdtsExtractor();
    SeekMap seekMap =
        TestUtil.extractSeekMap(extractor, new FakeExtractorOutput(), dataSource, fileUri);

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(FILE_DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void testSeeking_handlesSeekingToPositionInFile_extractsCorrectSample()
      throws IOException, InterruptedException {
    String fileName = TEST_FILE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    expectedTrackOutput =
        TestUtil.extractAllSamplesFromFile(
                createAdtsExtractor(), ApplicationProvider.getApplicationContext(), fileName)
            .trackOutputs
            .get(0);

    AdtsExtractor extractor = createAdtsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = 980_000;
    int extractedSampleIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedSampleIndex).isNotEqualTo(-1);
    assertFirstSampleAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedSampleIndex);
  }

  @Test
  public void testSeeking_handlesSeekToEoF_extractsLastSample()
      throws IOException, InterruptedException {
    String fileName = TEST_FILE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    expectedTrackOutput =
        TestUtil.extractAllSamplesFromFile(
                createAdtsExtractor(), ApplicationProvider.getApplicationContext(), fileName)
            .trackOutputs
            .get(0);
    AdtsExtractor extractor = createAdtsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = seekMap.getDurationUs();

    int extractedSampleIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedSampleIndex).isNotEqualTo(-1);
    assertFirstSampleAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedSampleIndex);
  }

  @Test
  public void testSeeking_handlesSeekingBackward_extractsCorrectSamples()
      throws IOException, InterruptedException {
    String fileName = TEST_FILE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    expectedTrackOutput =
        TestUtil.extractAllSamplesFromFile(
                createAdtsExtractor(), ApplicationProvider.getApplicationContext(), fileName)
            .trackOutputs
            .get(0);
    AdtsExtractor extractor = createAdtsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 980_000;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);

    long targetSeekTimeUs = 0;
    int extractedSampleIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedSampleIndex).isNotEqualTo(-1);
    assertFirstSampleAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedSampleIndex);
  }

  @Test
  public void testSeeking_handlesSeekingForward_extractsCorrectSamples()
      throws IOException, InterruptedException {
    String fileName = TEST_FILE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    expectedTrackOutput =
        TestUtil.extractAllSamplesFromFile(
                createAdtsExtractor(), ApplicationProvider.getApplicationContext(), fileName)
            .trackOutputs
            .get(0);
    AdtsExtractor extractor = createAdtsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 980_000;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);

    long targetSeekTimeUs = 1_200_000;
    int extractedSampleIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedSampleIndex).isNotEqualTo(-1);
    assertFirstSampleAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedSampleIndex);
  }

  @Test
  public void testSeeking_handlesRandomSeeks_extractsCorrectSamples()
      throws IOException, InterruptedException {
    String fileName = TEST_FILE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    expectedTrackOutput =
        TestUtil.extractAllSamplesFromFile(
                createAdtsExtractor(), ApplicationProvider.getApplicationContext(), fileName)
            .trackOutputs
            .get(0);
    AdtsExtractor extractor = createAdtsExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long numSeek = 100;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = random.nextInt(FILE_DURATION_US + 1);
      int extractedSampleIndex =
          TestUtil.seekToTimeUs(
              extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

      assertThat(extractedSampleIndex).isNotEqualTo(-1);
      assertFirstSampleAfterSeekContainTargetSeekTime(
          trackOutput, targetSeekTimeUs, extractedSampleIndex);
    }
  }

  // Internal methods

  private static AdtsExtractor createAdtsExtractor() {
    return new AdtsExtractor(
        /* firstStreamSampleTimestampUs= */ 0,
        /* flags= */ AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);
  }

  private void assertFirstSampleAfterSeekContainTargetSeekTime(
      FakeTrackOutput trackOutput, long seekTimeUs, int firstSampleIndexAfterSeek) {
    long outputSampleTimeUs = trackOutput.getSampleTimeUs(firstSampleIndexAfterSeek);
    int expectedSampleIndex =
        findOutputSampleInExpectedOutput(trackOutput.getSampleData(firstSampleIndexAfterSeek));
    // Assert that after seeking, the first sample written to output exists in the sample list
    assertThat(expectedSampleIndex).isNotEqualTo(-1);
    // Assert that the timestamp output for first sample after seek is near the seek point.
    // For ADTS seeking, unfortunately we can't guarantee exact sample seeking, since most ADTS
    // stream use VBR.
    assertThat(Math.abs(outputSampleTimeUs - seekTimeUs)).isLessThan(DELTA_TIMESTAMP_THRESHOLD_US);
    assertThat(
            Math.abs(outputSampleTimeUs - expectedTrackOutput.getSampleTimeUs(expectedSampleIndex)))
        .isLessThan(DELTA_TIMESTAMP_THRESHOLD_US);
    trackOutput.assertSample(
        firstSampleIndexAfterSeek,
        expectedTrackOutput.getSampleData(expectedSampleIndex),
        outputSampleTimeUs,
        expectedTrackOutput.getSampleFlags(expectedSampleIndex),
        expectedTrackOutput.getSampleCryptoData(expectedSampleIndex));
  }

  private int findOutputSampleInExpectedOutput(byte[] sampleData) {
    for (int i = 0; i < expectedTrackOutput.getSampleCount(); i++) {
      byte[] currentSampleData = expectedTrackOutput.getSampleData(i);
      if (Arrays.equals(currentSampleData, sampleData)) {
        return i;
      }
    }
    return -1;
  }
}
