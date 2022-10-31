/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ParsableBitArray}. */
@RunWith(AndroidJUnit4.class)
public final class ParsableBitArrayTest {

  private static final byte[] TEST_DATA = new byte[] {0x3C, (byte) 0xD2, (byte) 0x5F, (byte) 0x01,
      (byte) 0xFF, (byte) 0x14, (byte) 0x60, (byte) 0x99};

  private ParsableBitArray testArray;

  @Before
  public void setUp() {
    testArray = new ParsableBitArray(TEST_DATA);
  }

  @Test
  public void testReadAllBytes() {
    byte[] bytesRead = new byte[TEST_DATA.length];
    testArray.readBytes(bytesRead, 0, TEST_DATA.length);
    assertThat(bytesRead).isEqualTo(TEST_DATA);
    assertThat(testArray.getPosition()).isEqualTo(TEST_DATA.length * 8);
    assertThat(testArray.getBytePosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void testReadBit() {
    assertReadBitsToEnd(0);
  }

  @Test
  public void testReadBits() {
    assertThat(testArray.readBits(5)).isEqualTo(getTestDataBits(0, 5));
    assertThat(testArray.readBits(0)).isEqualTo(getTestDataBits(5, 0));
    assertThat(testArray.readBits(3)).isEqualTo(getTestDataBits(5, 3));
    assertThat(testArray.readBits(16)).isEqualTo(getTestDataBits(8, 16));
    assertThat(testArray.readBits(3)).isEqualTo(getTestDataBits(24, 3));
    assertThat(testArray.readBits(18)).isEqualTo(getTestDataBits(27, 18));
    assertThat(testArray.readBits(5)).isEqualTo(getTestDataBits(45, 5));
    assertThat(testArray.readBits(14)).isEqualTo(getTestDataBits(50, 14));
  }

  @Test
  public void testReadBitsToByteArray() {
    byte[] result = new byte[TEST_DATA.length];
    // Test read within byte boundaries.
    testArray.readBits(result, 0, 6);
    assertThat(result[0]).isEqualTo((byte) (TEST_DATA[0] & 0xFC));
    // Test read across byte boundaries.
    testArray.readBits(result, 0, 8);
    assertThat(result[0]).isEqualTo(
        (byte) (((TEST_DATA[0] & 0x03) << 6) | ((TEST_DATA[1] & 0xFC) >> 2)));
    // Test reading across multiple bytes.
    testArray.readBits(result, 1, 50);
    for (int i = 1; i < 7; i++) {
      assertThat(result[i])
          .isEqualTo((byte) (((TEST_DATA[i] & 0x03) << 6) | ((TEST_DATA[i + 1] & 0xFC) >> 2)));
    }
    assertThat(result[7]).isEqualTo((byte) ((TEST_DATA[7] & 0x03) << 6));
    assertThat(testArray.bitsLeft()).isEqualTo(0);
    // Test read last buffer byte across input data bytes.
    testArray.setPosition(31);
    result[3] = 0;
    testArray.readBits(result, 3, 3);
    assertThat(result[3]).isEqualTo((byte) 0xE0);
    // Test read bits in the middle of a input data byte.
    result[0] = 0;
    assertThat(testArray.getPosition()).isEqualTo(34);
    testArray.readBits(result, 0, 3);
    assertThat(result[0]).isEqualTo((byte) 0xE0);
    // Test read 0 bits.
    testArray.setPosition(32);
    result[1] = 0;
    testArray.readBits(result, 1, 0);
    assertThat(result[1]).isEqualTo((byte) 0);
    // Test reading a number of bits divisible by 8.
    testArray.setPosition(0);
    testArray.readBits(result, 0, 16);
    assertThat(result[0]).isEqualTo(TEST_DATA[0]);
    assertThat(result[1]).isEqualTo(TEST_DATA[1]);
    // Test least significant bits are unmodified.
    result[1] = (byte) 0xFF;
    testArray.readBits(result, 0, 9);
    assertThat(result[0]).isEqualTo((byte) 0x5F);
    assertThat(result[1]).isEqualTo((byte) 0x7F);
  }

  @Test
  public void testRead32BitsByteAligned() {
    assertThat(testArray.readBits(32)).isEqualTo(getTestDataBits(0, 32));
    assertThat(testArray.readBits(32)).isEqualTo(getTestDataBits(32, 32));
  }

  @Test
  public void testRead32BitsNonByteAligned() {
    assertThat(testArray.readBits(5)).isEqualTo(getTestDataBits(0, 5));
    assertThat(testArray.readBits(32)).isEqualTo(getTestDataBits(5, 32));
  }

  @Test
  public void testSkipBytes() {
    testArray.skipBytes(2);
    assertReadBitsToEnd(16);
  }

  @Test
  public void testSkipBitsByteAligned() {
    testArray.skipBits(16);
    assertReadBitsToEnd(16);
  }

  @Test
  public void testSkipBitsNonByteAligned() {
    testArray.skipBits(5);
    assertReadBitsToEnd(5);
  }

  @Test
  public void testSetPositionByteAligned() {
    testArray.setPosition(16);
    assertReadBitsToEnd(16);
  }

  @Test
  public void testSetPositionNonByteAligned() {
    testArray.setPosition(5);
    assertReadBitsToEnd(5);
  }

  @Test
  public void testByteAlignFromNonByteAligned() {
    testArray.setPosition(11);
    testArray.byteAlign();
    assertThat(testArray.getBytePosition()).isEqualTo(2);
    assertThat(testArray.getPosition()).isEqualTo(16);
    assertReadBitsToEnd(16);
  }

  @Test
  public void testByteAlignFromByteAligned() {
    testArray.setPosition(16);
    testArray.byteAlign(); // Should be a no-op.
    assertThat(testArray.getBytePosition()).isEqualTo(2);
    assertThat(testArray.getPosition()).isEqualTo(16);
    assertReadBitsToEnd(16);
  }

  @Test
  public void testPutBitsWithinByte() {
    ParsableBitArray output = new ParsableBitArray(new byte[4]);
    output.skipBits(1);

    output.putInt(0x3F, 5);

    output.setPosition(0);
    assertThat(output.readBits(8)).isEqualTo(0x1F << 2); // Check that only 5 bits are modified.
  }

  @Test
  public void testPutBitsAcrossTwoBytes() {
    ParsableBitArray output = new ParsableBitArray(new byte[4]);
    output.setPosition(12);

    output.putInt(0xFF, 8);
    output.setPosition(8);

    assertThat(output.readBits(16)).isEqualTo(0x0FF0);
  }

  @Test
  public void testPutBitsAcrossMultipleBytes() {
    ParsableBitArray output = new ParsableBitArray(new byte[8]);
    output.setPosition(31); // Writing starts at 31 to test the 30th bit is not modified.

    output.putInt(0xFF146098, 30); // Write only 30 to test the 61st bit is not modified.

    output.setPosition(30);
    assertThat(output.readBits(32)).isEqualTo(0x3F146098 << 1);
  }

  @Test
  public void testPut32Bits() {
    ParsableBitArray output = new ParsableBitArray(new byte[5]);
    output.setPosition(4);

    output.putInt(0xFF146098, 32);

    output.setPosition(4);
    assertThat(output.readBits(32)).isEqualTo(0xFF146098);
  }

  @Test
  public void testPutFullBytes() {
    ParsableBitArray output = new ParsableBitArray(new byte[2]);

    output.putInt(0x81, 8);

    output.setPosition(0);
    assertThat(output.readBits(8)).isEqualTo(0x81);
  }

  @Test
  public void testNoOverwriting() {
    ParsableBitArray output =
        new ParsableBitArray(
            new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
    output.setPosition(1);

    output.putInt(0, 30);

    output.setPosition(0);
    assertThat(output.readBits(32)).isEqualTo(0x80000001);
  }

  private void assertReadBitsToEnd(int expectedStartPosition) {
    int position = testArray.getPosition();
    assertThat(position).isEqualTo(expectedStartPosition);
    for (int i = position; i < TEST_DATA.length * 8; i++) {
      assertThat(testArray.readBit()).isEqualTo(getTestDataBit(i));
      assertThat(testArray.getPosition()).isEqualTo(i + 1);
    }
  }

  private static int getTestDataBits(int bitPosition, int length) {
    int result = 0;
    for (int i = 0; i < length; i++) {
      result = result << 1;
      if (getTestDataBit(bitPosition++)) {
        result |= 0x1;
      }
    }
    return result;
  }

  private static boolean getTestDataBit(int bitPosition) {
    return (TEST_DATA[bitPosition / 8] & (0x80 >>> (bitPosition % 8))) != 0;
  }

}
