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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.OggTestData;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link OggPacket}. */
@RunWith(AndroidJUnit4.class)
public final class OggPacketTest {

  private static final String TEST_FILE = "ogg/bear.opus";

  private Random random;
  private OggPacket oggPacket;

  @Before
  public void setUp() throws Exception {
    random = new Random(0);
    oggPacket = new OggPacket();
  }

  @Test
  public void testReadPacketsWithEmptyPage() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(8, random);
    byte[] secondPacket = TestUtil.buildTestData(272, random);
    byte[] thirdPacket = TestUtil.buildTestData(256, random);
    byte[] fourthPacket = TestUtil.buildTestData(271, random);

    FakeExtractorInput input =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                // First page with a single packet.
                OggTestData.buildOggHeader(0x02, 0, 1000, 0x01),
                TestUtil.createByteArray(0x08), // Laces
                firstPacket,
                // Second page with a single packet.
                OggTestData.buildOggHeader(0x00, 16, 1001, 0x02),
                TestUtil.createByteArray(0xFF, 0x11), // Laces
                secondPacket,
                // Third page with zero packets.
                OggTestData.buildOggHeader(0x00, 16, 1002, 0x00),
                // Fourth page with two packets.
                OggTestData.buildOggHeader(0x04, 128, 1003, 0x04),
                TestUtil.createByteArray(0xFF, 0x01, 0xFF, 0x10), // Laces
                thirdPacket,
                fourthPacket),
            true);

    assertReadPacket(input, firstPacket);
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isTrue();
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isFalse();
    assertThat(oggPacket.getPageHeader().type).isEqualTo(0x02);
    assertThat(oggPacket.getPageHeader().headerSize).isEqualTo(27 + 1);
    assertThat(oggPacket.getPageHeader().bodySize).isEqualTo(8);
    assertThat(oggPacket.getPageHeader().revision).isEqualTo(0x00);
    assertThat(oggPacket.getPageHeader().pageSegmentCount).isEqualTo(1);
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1000);
    assertThat(oggPacket.getPageHeader().streamSerialNumber).isEqualTo(4096);
    assertThat(oggPacket.getPageHeader().granulePosition).isEqualTo(0);

    assertReadPacket(input, secondPacket);
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isFalse();
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isFalse();
    assertThat(oggPacket.getPageHeader().type).isEqualTo(0);
    assertThat(oggPacket.getPageHeader().headerSize).isEqualTo(27 + 2);
    assertThat(oggPacket.getPageHeader().bodySize).isEqualTo(255 + 17);
    assertThat(oggPacket.getPageHeader().pageSegmentCount).isEqualTo(2);
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1001);
    assertThat(oggPacket.getPageHeader().granulePosition).isEqualTo(16);

    assertReadPacket(input, thirdPacket);
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isFalse();
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isTrue();
    assertThat(oggPacket.getPageHeader().type).isEqualTo(4);
    assertThat(oggPacket.getPageHeader().headerSize).isEqualTo(27 + 4);
    assertThat(oggPacket.getPageHeader().bodySize).isEqualTo(255 + 1 + 255 + 16);
    assertThat(oggPacket.getPageHeader().pageSegmentCount).isEqualTo(4);
    // Page 1002 is empty, so current page is 1003.
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1003);
    assertThat(oggPacket.getPageHeader().granulePosition).isEqualTo(128);

    assertReadPacket(input, fourthPacket);

    assertReadEof(input);
  }

  @Test
  public void testReadPacketWithZeroSizeTerminator() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(255, random);
    byte[] secondPacket = TestUtil.buildTestData(8, random);

    FakeExtractorInput input =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                OggTestData.buildOggHeader(0x06, 0, 1000, 0x04),
                TestUtil.createByteArray(0xFF, 0x00, 0x00, 0x08), // Laces.
                firstPacket,
                secondPacket),
            true);

    assertReadPacket(input, firstPacket);
    assertReadPacket(input, secondPacket);
    assertReadEof(input);
  }

  @Test
  public void testReadContinuedPacketOverTwoPages() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(518);

    FakeExtractorInput input =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                // First page.
                OggTestData.buildOggHeader(0x02, 0, 1000, 0x02),
                TestUtil.createByteArray(0xFF, 0xFF), // Laces.
                Arrays.copyOf(firstPacket, 510),
                // Second page (continued packet).
                OggTestData.buildOggHeader(0x05, 10, 1001, 0x01),
                TestUtil.createByteArray(0x08), // Laces.
                Arrays.copyOfRange(firstPacket, 510, 510 + 8)),
            true);

    assertReadPacket(input, firstPacket);
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isTrue();
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isFalse();
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1001);

    assertReadEof(input);
  }

  @Test
  public void testReadContinuedPacketOverFourPages() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(1028);

    FakeExtractorInput input =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                // First page.
                OggTestData.buildOggHeader(0x02, 0, 1000, 0x02),
                TestUtil.createByteArray(0xFF, 0xFF), // Laces.
                Arrays.copyOf(firstPacket, 510),
                // Second page (continued packet).
                OggTestData.buildOggHeader(0x01, 10, 1001, 0x01),
                TestUtil.createByteArray(0xFF), // Laces.
                Arrays.copyOfRange(firstPacket, 510, 510 + 255),
                // Third page (continued packet).
                OggTestData.buildOggHeader(0x01, 10, 1002, 0x01),
                TestUtil.createByteArray(0xFF), // Laces.
                Arrays.copyOfRange(firstPacket, 510 + 255, 510 + 255 + 255),
                // Fourth page (continued packet).
                OggTestData.buildOggHeader(0x05, 10, 1003, 0x01),
                TestUtil.createByteArray(0x08), // Laces.
                Arrays.copyOfRange(firstPacket, 510 + 255 + 255, 510 + 255 + 255 + 8)),
            true);

    assertReadPacket(input, firstPacket);
    assertThat((oggPacket.getPageHeader().type & 0x04) == 0x04).isTrue();
    assertThat((oggPacket.getPageHeader().type & 0x02) == 0x02).isFalse();
    assertThat(oggPacket.getPageHeader().pageSequenceNumber).isEqualTo(1003);

    assertReadEof(input);
  }

  @Test
  public void testReadDiscardContinuedPacketAtStart() throws Exception {
    byte[] pageBody = TestUtil.buildTestData(256 + 8);

    FakeExtractorInput input =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                // Page with a continued packet at start.
                OggTestData.buildOggHeader(0x01, 10, 1001, 0x03),
                TestUtil.createByteArray(255, 1, 8), // Laces.
                pageBody),
            true);

    // Expect the first partial packet to be discarded.
    assertReadPacket(input, Arrays.copyOfRange(pageBody, 256, 256 + 8));
    assertReadEof(input);
  }

  @Test
  public void testReadZeroSizedPacketsAtEndOfStream() throws Exception {
    byte[] firstPacket = TestUtil.buildTestData(8, random);
    byte[] secondPacket = TestUtil.buildTestData(8, random);
    byte[] thirdPacket = TestUtil.buildTestData(8, random);

    FakeExtractorInput input =
        OggTestData.createInput(
            TestUtil.joinByteArrays(
                OggTestData.buildOggHeader(0x02, 0, 1000, 0x01),
                TestUtil.createByteArray(0x08), // Laces.
                firstPacket,
                OggTestData.buildOggHeader(0x04, 0, 1001, 0x03),
                TestUtil.createByteArray(0x08, 0x00, 0x00), // Laces.
                secondPacket,
                OggTestData.buildOggHeader(0x04, 0, 1002, 0x03),
                TestUtil.createByteArray(0x08, 0x00, 0x00), // Laces.
                thirdPacket),
            true);

    assertReadPacket(input, firstPacket);
    assertReadPacket(input, secondPacket);
    assertReadPacket(input, thirdPacket);
    assertReadEof(input);
  }

  @Test
  public void testParseRealFile() throws IOException, InterruptedException {
    byte[] data = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), TEST_FILE);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    int packetCounter = 0;
    while (readPacket(input)) {
      packetCounter++;
    }
    assertThat(packetCounter).isEqualTo(277);
  }

  private void assertReadPacket(FakeExtractorInput extractorInput, byte[] expected)
      throws IOException, InterruptedException {
    assertThat(readPacket(extractorInput)).isTrue();
    ParsableByteArray payload = oggPacket.getPayload();
    assertThat(Arrays.copyOf(payload.data, payload.limit())).isEqualTo(expected);
  }

  private void assertReadEof(FakeExtractorInput extractorInput)
      throws IOException, InterruptedException {
    assertThat(readPacket(extractorInput)).isFalse();
  }

  private boolean readPacket(FakeExtractorInput input) throws InterruptedException, IOException {
    while (true) {
      try {
        return oggPacket.populate(input);
      } catch (FakeExtractorInput.SimulatedIOException e) {
        // Ignore.
      }
    }
  }
}
