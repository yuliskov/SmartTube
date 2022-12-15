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
package com.google.android.exoplayer2.extractor.ts;

import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link AdtsReader}. */
@RunWith(AndroidJUnit4.class)
public class AdtsReaderTest {

  public static final byte[] ID3_DATA_1 =
      TestUtil.createByteArray(
          0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3d, 0x54, 0x58, 0x58, 0x58, 0x00,
          0x00, 0x00, 0x33, 0x00, 0x00, 0x03, 0x00, 0x20, 0x2a, 0x2a, 0x2a, 0x20, 0x54, 0x48, 0x49,
          0x53, 0x20, 0x49, 0x53, 0x20, 0x54, 0x69, 0x6d, 0x65, 0x64, 0x20, 0x4d, 0x65, 0x74, 0x61,
          0x44, 0x61, 0x74, 0x61, 0x20, 0x40, 0x20, 0x2d, 0x2d, 0x20, 0x30, 0x30, 0x3a, 0x30, 0x30,
          0x3a, 0x30, 0x30, 0x2e, 0x30, 0x20, 0x2a, 0x2a, 0x2a, 0x20, 0x00);

  public static final byte[] ID3_DATA_2 =
      TestUtil.createByteArray(
          0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3f, 0x50, 0x52, 0x49, 0x56, 0x00,
          0x00, 0x00, 0x35, 0x00, 0x00, 0x63, 0x6f, 0x6d, 0x2e, 0x61, 0x70, 0x70, 0x6c, 0x65, 0x2e,
          0x73, 0x74, 0x72, 0x65, 0x61, 0x6d, 0x69, 0x6e, 0x67, 0x2e, 0x74, 0x72, 0x61, 0x6e, 0x73,
          0x70, 0x6f, 0x72, 0x74, 0x53, 0x74, 0x72, 0x65, 0x61, 0x6d, 0x54, 0x69, 0x6d, 0x65, 0x73,
          0x74, 0x61, 0x6d, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0d, 0xbb, 0xa0);

  public static final byte[] ADTS_HEADER =
      TestUtil.createByteArray(0xff, 0xf1, 0x50, 0x80, 0x01, 0xdf, 0xfc);

  public static final byte[] ADTS_CONTENT =
      TestUtil.createByteArray(0x20, 0x00, 0x20, 0x00, 0x00, 0x80, 0x0e);

  private static final byte[] TEST_DATA =
      TestUtil.joinByteArrays(ID3_DATA_1, ID3_DATA_2, ADTS_HEADER, ADTS_CONTENT);

  private static final long ADTS_SAMPLE_DURATION = 23219L;

  private FakeTrackOutput adtsOutput;
  private FakeTrackOutput id3Output;
  private AdtsReader adtsReader;
  private ParsableByteArray data;
  private boolean firstFeed;

  @Before
  public void setUp() throws Exception {
    FakeExtractorOutput fakeExtractorOutput = new FakeExtractorOutput();
    adtsOutput = fakeExtractorOutput.track(0, C.TRACK_TYPE_AUDIO);
    id3Output = fakeExtractorOutput.track(1, C.TRACK_TYPE_METADATA);
    adtsReader = new AdtsReader(true);
    TrackIdGenerator idGenerator = new TrackIdGenerator(0, 1);
    adtsReader.createTracks(fakeExtractorOutput, idGenerator);
    data = new ParsableByteArray(TEST_DATA);
    firstFeed = true;
  }

  @Test
  public void testSkipToNextSample() throws Exception {
    for (int i = 1; i <= ID3_DATA_1.length + ID3_DATA_2.length; i++) {
      data.setPosition(i);
      feed();
      // Once the data position set to ID3_DATA_1.length, no more id3 samples are read
      int id3SampleCount = Math.min(i, ID3_DATA_1.length);
      assertSampleCounts(id3SampleCount, i);
    }
  }

  @Test
  public void testSkipToNextSampleResetsState() throws Exception {
    data =
        new ParsableByteArray(
            TestUtil.joinByteArrays(
                ADTS_HEADER,
                ADTS_CONTENT,
                ADTS_HEADER,
                ADTS_CONTENT,
                // Adts sample missing the first sync byte
                // The Reader should be able to read the next sample.
                Arrays.copyOfRange(ADTS_HEADER, 1, ADTS_HEADER.length),
                ADTS_CONTENT,
                ADTS_HEADER,
                ADTS_CONTENT));
    feed();
    assertSampleCounts(0, 3);
    for (int i = 0; i < 3; i++) {
      adtsOutput.assertSample(
          /* index= */ i,
          /* data= */ ADTS_CONTENT,
          /* timeUs= */ ADTS_SAMPLE_DURATION * i,
          /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
          /* cryptoData= */ null);
    }
  }

  @Test
  public void testNoData() throws Exception {
    feedLimited(0);
    assertSampleCounts(0, 0);
  }

  @Test
  public void testNotEnoughDataForIdentifier() throws Exception {
    feedLimited(3 - 1);
    assertSampleCounts(0, 0);
  }

  @Test
  public void testNotEnoughDataForHeader() throws Exception {
    feedLimited(10 - 1);
    assertSampleCounts(0, 0);
  }

  @Test
  public void testNotEnoughDataForWholeId3Packet() throws Exception {
    feedLimited(ID3_DATA_1.length - 1);
    assertSampleCounts(0, 0);
  }

  @Test
  public void testConsumeWholeId3Packet() throws Exception {
    feedLimited(ID3_DATA_1.length);
    assertSampleCounts(1, 0);
    id3Output.assertSample(0, ID3_DATA_1, 0, C.BUFFER_FLAG_KEY_FRAME, null);
  }

  @Test
  public void testMultiId3Packet() throws Exception {
    feedLimited(ID3_DATA_1.length + ID3_DATA_2.length - 1);
    assertSampleCounts(1, 0);
    id3Output.assertSample(0, ID3_DATA_1, 0, C.BUFFER_FLAG_KEY_FRAME, null);
  }

  @Test
  public void testMultiId3PacketConsumed() throws Exception {
    feedLimited(ID3_DATA_1.length + ID3_DATA_2.length);
    assertSampleCounts(2, 0);
    id3Output.assertSample(0, ID3_DATA_1, 0, C.BUFFER_FLAG_KEY_FRAME, null);
    id3Output.assertSample(1, ID3_DATA_2, 0, C.BUFFER_FLAG_KEY_FRAME, null);
  }

  @Test
  public void testMultiPacketConsumed() throws Exception {
    for (int i = 0; i < 10; i++) {
      data.setPosition(0);
      feed();

      long timeUs = ADTS_SAMPLE_DURATION * i;
      int j = i * 2;
      assertSampleCounts(j + 2, i + 1);

      id3Output.assertSample(j, ID3_DATA_1, timeUs, C.BUFFER_FLAG_KEY_FRAME, null);
      id3Output.assertSample(j + 1, ID3_DATA_2, timeUs, C.BUFFER_FLAG_KEY_FRAME, null);
      adtsOutput.assertSample(i, ADTS_CONTENT, timeUs, C.BUFFER_FLAG_KEY_FRAME, null);
    }
  }

  @Test
  public void testAdtsDataOnly() throws ParserException {
    data.setPosition(ID3_DATA_1.length + ID3_DATA_2.length);
    feed();
    assertSampleCounts(0, 1);
    adtsOutput.assertSample(0, ADTS_CONTENT, 0, C.BUFFER_FLAG_KEY_FRAME, null);
  }

  private void feedLimited(int limit) throws ParserException {
    maybeStartPacket();
    data.setLimit(limit);
    feed();
  }

  private void feed() throws ParserException {
    maybeStartPacket();
    adtsReader.consume(data);
  }

  private void maybeStartPacket() {
    if (firstFeed) {
      adtsReader.packetStarted(0, FLAG_DATA_ALIGNMENT_INDICATOR);
      firstFeed = false;
    }
  }

  private void assertSampleCounts(int id3SampleCount, int adtsSampleCount) {
    id3Output.assertSampleCount(id3SampleCount);
    adtsOutput.assertSampleCount(adtsSampleCount);
  }
}
