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
package com.google.android.exoplayer2.ext.flac;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
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
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.runner.RunWith;

/** Seeking tests for {@link FlacExtractor} when the FLAC stream does not have a SEEKTABLE. */
@RunWith(AndroidJUnit4.class)
public final class FlacExtractorSeekTest {

  private static final String NO_SEEKTABLE_FLAC = "bear_no_seek.flac";
  private static final int DURATION_US = 2_741_000;
  private static final Uri FILE_URI = Uri.parse("file:///android_asset/" + NO_SEEKTABLE_FLAC);
  private static final Random RANDOM = new Random(1234L);

  private FakeExtractorOutput expectedOutput;
  private FakeTrackOutput expectedTrackOutput;

  private DefaultDataSource dataSource;
  private PositionHolder positionHolder;
  private long totalInputLength;

  @Before
  public void setUp() throws Exception {
    if (!FlacLibrary.isAvailable()) {
      fail("Flac library not available.");
    }
    expectedOutput = new FakeExtractorOutput();
    extractAllSamplesFromFileToExpectedOutput(
        ApplicationProvider.getApplicationContext(), NO_SEEKTABLE_FLAC);
    expectedTrackOutput = expectedOutput.trackOutputs.get(0);

    dataSource =
        new DefaultDataSourceFactory(ApplicationProvider.getApplicationContext(), "UserAgent")
            .createDataSource();
    totalInputLength = readInputLength();
    positionHolder = new PositionHolder();
  }

  public void testFlacExtractorReads_nonSeekTableFile_returnSeekableSeekMap()
      throws IOException, InterruptedException {
    FlacExtractor extractor = new FlacExtractor();

    SeekMap seekMap = extractSeekMap(extractor, new FakeExtractorOutput());

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  public void testHandlePendingSeek_handlesSeekingToPositionInFile_extractsCorrectFrame()
      throws IOException, InterruptedException {
    FlacExtractor extractor = new FlacExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = 987_000;
    int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  public void testHandlePendingSeek_handlesSeekToEoF_extractsLastFrame()
      throws IOException, InterruptedException {
    FlacExtractor extractor = new FlacExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = seekMap.getDurationUs();

    int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  public void testHandlePendingSeek_handlesSeekingBackward_extractsCorrectFrame()
      throws IOException, InterruptedException {
    FlacExtractor extractor = new FlacExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 987_000;
    seekToTimeUs(extractor, seekMap, firstSeekTimeUs, trackOutput);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  public void testHandlePendingSeek_handlesSeekingForward_extractsCorrectFrame()
      throws IOException, InterruptedException {
    FlacExtractor extractor = new FlacExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 987_000;
    seekToTimeUs(extractor, seekMap, firstSeekTimeUs, trackOutput);

    long targetSeekTimeUs = 1_234_000;
    int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  public void testHandlePendingSeek_handlesRandomSeeks_extractsCorrectFrame()
      throws IOException, InterruptedException {
    FlacExtractor extractor = new FlacExtractor();

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long numSeek = 100;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = RANDOM.nextInt(DURATION_US + 1);
      int extractedFrameIndex = seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput);

      assertThat(extractedFrameIndex).isNotEqualTo(-1);
      assertFirstFrameAfterSeekContainTargetSeekTime(
          trackOutput, targetSeekTimeUs, extractedFrameIndex);
    }
  }

  // Internal methods

  private long readInputLength() throws IOException {
    DataSpec dataSpec = new DataSpec(FILE_URI, 0, C.LENGTH_UNSET, null);
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
      FlacExtractor flacExtractor, SeekMap seekMap, long seekTimeUs, FakeTrackOutput trackOutput)
      throws IOException, InterruptedException {
    int numSampleBeforeSeek = trackOutput.getSampleCount();
    SeekMap.SeekPoints seekPoints = seekMap.getSeekPoints(seekTimeUs);

    long initialSeekLoadPosition = seekPoints.first.position;
    flacExtractor.seek(initialSeekLoadPosition, seekTimeUs);

    positionHolder.position = C.POSITION_UNSET;
    ExtractorInput extractorInput = getExtractorInputFromPosition(initialSeekLoadPosition);
    int extractorReadResult = Extractor.RESULT_CONTINUE;
    while (true) {
      try {
        // Keep reading until we can read at least one frame after seek
        while (extractorReadResult == Extractor.RESULT_CONTINUE
            && trackOutput.getSampleCount() == numSampleBeforeSeek) {
          extractorReadResult = flacExtractor.read(extractorInput, positionHolder);
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

  private @Nullable SeekMap extractSeekMap(FlacExtractor extractor, FakeExtractorOutput output)
      throws IOException, InterruptedException {
    try {
      ExtractorInput input = getExtractorInputFromPosition(0);
      extractor.init(output);
      while (output.seekMap == null) {
        extractor.read(input, positionHolder);
      }
    } finally {
      Util.closeQuietly(dataSource);
    }
    return output.seekMap;
  }

  private void assertFirstFrameAfterSeekContainTargetSeekTime(
      FakeTrackOutput trackOutput, long seekTimeUs, int firstFrameIndexAfterSeek) {
    int expectedSampleIndex = findTargetFrameInExpectedOutput(seekTimeUs);
    // Assert that after seeking, the first sample frame written to output contains the sample
    // at seek time.
    trackOutput.assertSample(
        firstFrameIndexAfterSeek,
        expectedTrackOutput.getSampleData(expectedSampleIndex),
        expectedTrackOutput.getSampleTimeUs(expectedSampleIndex),
        expectedTrackOutput.getSampleFlags(expectedSampleIndex),
        expectedTrackOutput.getSampleCryptoData(expectedSampleIndex));
  }

  private int findTargetFrameInExpectedOutput(long seekTimeUs) {
    List<Long> sampleTimes = expectedTrackOutput.getSampleTimesUs();
    for (int i = 0; i < sampleTimes.size() - 1; i++) {
      long currentSampleTime = sampleTimes.get(i);
      long nextSampleTime = sampleTimes.get(i + 1);
      if (currentSampleTime <= seekTimeUs && nextSampleTime > seekTimeUs) {
        return i;
      }
    }
    return sampleTimes.size() - 1;
  }

  private ExtractorInput getExtractorInputFromPosition(long position) throws IOException {
    DataSpec dataSpec = new DataSpec(FILE_URI, position, totalInputLength, /* key= */ null);
    dataSource.open(dataSpec);
    return new DefaultExtractorInput(dataSource, position, totalInputLength);
  }

  private void extractAllSamplesFromFileToExpectedOutput(Context context, String fileName)
      throws IOException, InterruptedException {
    byte[] data = TestUtil.getByteArray(context, fileName);

    FlacExtractor extractor = new FlacExtractor();
    extractor.init(expectedOutput);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();

    while (extractor.read(input, new PositionHolder()) != Extractor.RESULT_END_OF_INPUT) {}
  }
}
