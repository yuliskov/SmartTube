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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.testutil.TestUtil.createByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link NalUnitUtil}. */
@RunWith(AndroidJUnit4.class)
public final class NalUnitUtilTest {

  private static final int TEST_PARTIAL_NAL_POSITION = 4;
  private static final int TEST_NAL_POSITION = 10;
  private static final byte[] SPS_TEST_DATA = createByteArray(0x00, 0x00, 0x01, 0x67, 0x4D, 0x40,
      0x16, 0xEC, 0xA0, 0x50, 0x17, 0xFC, 0xB8, 0x08, 0x80, 0x00, 0x00, 0x03, 0x00, 0x80, 0x00,
      0x00, 0x0F, 0x47, 0x8B, 0x16, 0xCB);
  private static final int SPS_TEST_DATA_OFFSET = 3;

  @Test
  public void testFindNalUnit() {
    byte[] data = buildTestData();

    // Should find NAL unit.
    int result = NalUnitUtil.findNalUnit(data, 0, data.length, null);
    assertThat(result).isEqualTo(TEST_NAL_POSITION);
    // Should find NAL unit whose prefix ends one byte before the limit.
    result = NalUnitUtil.findNalUnit(data, 0, TEST_NAL_POSITION + 4, null);
    assertThat(result).isEqualTo(TEST_NAL_POSITION);
    // Shouldn't find NAL unit whose prefix ends at the limit (since the limit is exclusive).
    result = NalUnitUtil.findNalUnit(data, 0, TEST_NAL_POSITION + 3, null);
    assertThat(result).isEqualTo(TEST_NAL_POSITION + 3);
    // Should find NAL unit whose prefix starts at the offset.
    result = NalUnitUtil.findNalUnit(data, TEST_NAL_POSITION, data.length, null);
    assertThat(result).isEqualTo(TEST_NAL_POSITION);
    // Shouldn't find NAL unit whose prefix starts one byte past the offset.
    result = NalUnitUtil.findNalUnit(data, TEST_NAL_POSITION + 1, data.length, null);
    assertThat(result).isEqualTo(data.length);
  }

  @Test
  public void testFindNalUnitWithPrefix() {
    byte[] data = buildTestData();

    // First byte of NAL unit in data1, rest in data2.
    boolean[] prefixFlags = new boolean[3];
    byte[] data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    byte[] data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, data.length);
    int result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(-1);
    assertPrefixFlagsCleared(prefixFlags);

    // First three bytes of NAL unit in data1, rest in data2.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 3);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 3, data.length);
    result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(-3);
    assertPrefixFlagsCleared(prefixFlags);

    // First byte of NAL unit in data1, second byte in data2, rest in data3.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, TEST_NAL_POSITION + 2);
    byte[] data3 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, data.length);
    result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(data2.length);
    result = NalUnitUtil.findNalUnit(data3, 0, data3.length, prefixFlags);
    assertThat(result).isEqualTo(-2);
    assertPrefixFlagsCleared(prefixFlags);

    // NAL unit split with one byte in four arrays.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, TEST_NAL_POSITION + 2);
    data3 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, TEST_NAL_POSITION + 3);
    byte[] data4 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, data.length);
    result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(data2.length);
    result = NalUnitUtil.findNalUnit(data3, 0, data3.length, prefixFlags);
    assertThat(result).isEqualTo(data3.length);
    result = NalUnitUtil.findNalUnit(data4, 0, data4.length, prefixFlags);
    assertThat(result).isEqualTo(-3);
    assertPrefixFlagsCleared(prefixFlags);

    // NAL unit entirely in data2. data1 ends with partial prefix.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_PARTIAL_NAL_POSITION + 2);
    data2 = Arrays.copyOfRange(data, TEST_PARTIAL_NAL_POSITION + 2, data.length);
    result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(4);
    assertPrefixFlagsCleared(prefixFlags);
  }

  @Test
  public void testParseSpsNalUnit() {
    NalUnitUtil.SpsData data = NalUnitUtil.parseSpsNalUnit(SPS_TEST_DATA, SPS_TEST_DATA_OFFSET,
        SPS_TEST_DATA.length);
    assertThat(data.width).isEqualTo(640);
    assertThat(data.height).isEqualTo(360);
    assertThat(data.deltaPicOrderAlwaysZeroFlag).isFalse();
    assertThat(data.frameMbsOnlyFlag).isTrue();
    assertThat(data.frameNumLength).isEqualTo(4);
    assertThat(data.picOrderCntLsbLength).isEqualTo(6);
    assertThat(data.seqParameterSetId).isEqualTo(0);
    assertThat(data.pixelWidthAspectRatio).isEqualTo(1.0f);
    assertThat(data.picOrderCountType).isEqualTo(0);
    assertThat(data.separateColorPlaneFlag).isFalse();
  }

  @Test
  public void testUnescapeDoesNotModifyBuffersWithoutStartCodes() {
    assertUnescapeDoesNotModify("");
    assertUnescapeDoesNotModify("0000");
    assertUnescapeDoesNotModify("172BF38A3C");
    assertUnescapeDoesNotModify("000004");
  }

  @Test
  public void testUnescapeModifiesBuffersWithStartCodes() {
    assertUnescapeMatchesExpected("00000301", "000001");
    assertUnescapeMatchesExpected("0000030200000300", "000002000000");
  }

  @Test
  public void testDiscardToSps() {
    assertDiscardToSpsMatchesExpected("", "");
    assertDiscardToSpsMatchesExpected("00", "");
    assertDiscardToSpsMatchesExpected("FFFF000001", "");
    assertDiscardToSpsMatchesExpected("00000001", "");
    assertDiscardToSpsMatchesExpected("00000001FF67", "");
    assertDiscardToSpsMatchesExpected("00000001000167", "");
    assertDiscardToSpsMatchesExpected("0000000167", "0000000167");
    assertDiscardToSpsMatchesExpected("0000000167FF", "0000000167FF");
    assertDiscardToSpsMatchesExpected("0000000167FF", "0000000167FF");
    assertDiscardToSpsMatchesExpected("0000000167FF000000016700", "0000000167FF000000016700");
    assertDiscardToSpsMatchesExpected("000000000167FF", "0000000167FF");
    assertDiscardToSpsMatchesExpected("0001670000000167FF", "0000000167FF");
    assertDiscardToSpsMatchesExpected("FF00000001660000000167FF", "0000000167FF");
  }

  private static byte[] buildTestData() {
    byte[] data = new byte[20];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) 0xFF;
    }
    // Insert an incomplete NAL unit start code.
    data[TEST_PARTIAL_NAL_POSITION] = 0;
    data[TEST_PARTIAL_NAL_POSITION + 1] = 0;
    // Insert a complete NAL unit start code.
    data[TEST_NAL_POSITION] = 0;
    data[TEST_NAL_POSITION + 1] = 0;
    data[TEST_NAL_POSITION + 2] = 1;
    data[TEST_NAL_POSITION + 3] = 5;
    return data;
  }

  private static void assertPrefixFlagsCleared(boolean[] flags) {
    assertThat(flags[0] || flags[1] || flags[2]).isEqualTo(false);
  }

  private static void assertUnescapeDoesNotModify(String input) {
    assertUnescapeMatchesExpected(input, input);
  }

  private static void assertUnescapeMatchesExpected(String input, String expectedOutput) {
    byte[] bitstream = Util.getBytesFromHexString(input);
    byte[] expectedOutputBitstream = Util.getBytesFromHexString(expectedOutput);
    int count = NalUnitUtil.unescapeStream(bitstream, bitstream.length);
    assertThat(count).isEqualTo(expectedOutputBitstream.length);
    byte[] outputBitstream = new byte[count];
    System.arraycopy(bitstream, 0, outputBitstream, 0, count);
    assertThat(outputBitstream).isEqualTo(expectedOutputBitstream);
  }

  private static void assertDiscardToSpsMatchesExpected(String input, String expectedOutput) {
    byte[] bitstream = Util.getBytesFromHexString(input);
    byte[] expectedOutputBitstream = Util.getBytesFromHexString(expectedOutput);
    ByteBuffer buffer = ByteBuffer.wrap(bitstream);
    buffer.position(buffer.limit());
    NalUnitUtil.discardToSps(buffer);
    assertThat(Arrays.copyOf(buffer.array(), buffer.position())).isEqualTo(expectedOutputBitstream);
  }

}
