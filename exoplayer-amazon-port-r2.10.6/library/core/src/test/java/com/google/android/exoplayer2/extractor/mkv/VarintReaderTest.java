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
package com.google.android.exoplayer2.extractor.mkv;

import static com.google.android.exoplayer2.C.RESULT_END_OF_INPUT;
import static com.google.android.exoplayer2.C.RESULT_MAX_LENGTH_EXCEEDED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorInput.SimulatedIOException;
import java.io.EOFException;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link VarintReader}. */
@RunWith(AndroidJUnit4.class)
public final class VarintReaderTest {

  private static final byte MAX_BYTE = (byte) 0xFF;

  private static final byte[] DATA_1_BYTE_0 = new byte[] {(byte) 0x80};
  private static final byte[] DATA_2_BYTE_0 = new byte[] {0x40, 0};
  private static final byte[] DATA_3_BYTE_0 = new byte[] {0x20, 0, 0};
  private static final byte[] DATA_4_BYTE_0 = new byte[] {0x10, 0, 0, 0};
  private static final byte[] DATA_5_BYTE_0 = new byte[] {0x08, 0, 0, 0, 0};
  private static final byte[] DATA_6_BYTE_0 = new byte[] {0x04, 0, 0, 0, 0, 0};
  private static final byte[] DATA_7_BYTE_0 = new byte[] {0x02, 0, 0, 0, 0, 0, 0};
  private static final byte[] DATA_8_BYTE_0 = new byte[] {0x01, 0, 0, 0, 0, 0, 0, 0};

  private static final byte[] DATA_1_BYTE_64 = new byte[] {(byte) 0xC0};
  private static final byte[] DATA_2_BYTE_64 = new byte[] {0x40, 0x40};
  private static final byte[] DATA_3_BYTE_64 = new byte[] {0x20, 0, 0x40};
  private static final byte[] DATA_4_BYTE_64 = new byte[] {0x10, 0, 0, 0x40};
  private static final byte[] DATA_5_BYTE_64 = new byte[] {0x08, 0, 0, 0, 0x40};
  private static final byte[] DATA_6_BYTE_64 = new byte[] {0x04, 0, 0, 0, 0, 0x40};
  private static final byte[] DATA_7_BYTE_64 = new byte[] {0x02, 0, 0, 0, 0, 0, 0x40};
  private static final byte[] DATA_8_BYTE_64 = new byte[] {0x01, 0, 0, 0, 0, 0, 0, 0x40};

  private static final byte[] DATA_1_BYTE_MAX = new byte[] {MAX_BYTE};
  private static final byte[] DATA_2_BYTE_MAX = new byte[] {0x7F, MAX_BYTE};
  private static final byte[] DATA_3_BYTE_MAX = new byte[] {0x3F, MAX_BYTE, MAX_BYTE};
  private static final byte[] DATA_4_BYTE_MAX = new byte[] {0x1F, MAX_BYTE, MAX_BYTE, MAX_BYTE};
  private static final byte[] DATA_5_BYTE_MAX =
      new byte[] {0x0F, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE};
  private static final byte[] DATA_6_BYTE_MAX =
      new byte[] {0x07, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE};
  private static final byte[] DATA_7_BYTE_MAX =
      new byte[] {0x03, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE};
  private static final byte[] DATA_8_BYTE_MAX =
      new byte[] {0x01, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE, MAX_BYTE};

  private static final long VALUE_1_BYTE_MAX = 0x7F;
  private static final long VALUE_1_BYTE_MAX_WITH_MASK = 0xFF;
  private static final long VALUE_2_BYTE_MAX = 0x3FFF;
  private static final long VALUE_2_BYTE_MAX_WITH_MASK = 0x7FFF;
  private static final long VALUE_3_BYTE_MAX = 0x1FFFFF;
  private static final long VALUE_3_BYTE_MAX_WITH_MASK = 0x3FFFFF;
  private static final long VALUE_4_BYTE_MAX = 0xFFFFFFF;
  private static final long VALUE_4_BYTE_MAX_WITH_MASK = 0x1FFFFFFF;
  private static final long VALUE_5_BYTE_MAX = 0x7FFFFFFFFL;
  private static final long VALUE_5_BYTE_MAX_WITH_MASK = 0xFFFFFFFFFL;
  private static final long VALUE_6_BYTE_MAX = 0x3FFFFFFFFFFL;
  private static final long VALUE_6_BYTE_MAX_WITH_MASK = 0x7FFFFFFFFFFL;
  private static final long VALUE_7_BYTE_MAX = 0x1FFFFFFFFFFFFL;
  private static final long VALUE_7_BYTE_MAX_WITH_MASK = 0x3FFFFFFFFFFFFL;
  private static final long VALUE_8_BYTE_MAX = 0xFFFFFFFFFFFFFFL;
  private static final long VALUE_8_BYTE_MAX_WITH_MASK = 0x1FFFFFFFFFFFFFFL;

  @Test
  public void testReadVarintEndOfInputAtStart() throws IOException, InterruptedException {
    VarintReader reader = new VarintReader();
    // Build an input with no data.
    ExtractorInput input = new FakeExtractorInput.Builder()
        .setSimulateUnknownLength(true)
        .build();
    // End of input allowed.
    long result = reader.readUnsignedVarint(input, true, false, 8);
    assertThat(result).isEqualTo(RESULT_END_OF_INPUT);
    // End of input not allowed.
    try {
      reader.readUnsignedVarint(input, false, false, 8);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  @Test
  public void testReadVarintExceedsMaximumAllowedLength() throws IOException, InterruptedException {
    VarintReader reader = new VarintReader();
    ExtractorInput input = new FakeExtractorInput.Builder()
        .setData(DATA_8_BYTE_0)
        .setSimulateUnknownLength(true)
        .build();
    long result = reader.readUnsignedVarint(input, false, true, 4);
    assertThat(result).isEqualTo(RESULT_MAX_LENGTH_EXCEEDED);
  }

  @Test
  public void testReadVarint() throws IOException, InterruptedException {
    VarintReader reader = new VarintReader();
    testReadVarint(reader, true, DATA_1_BYTE_0, 1, 0);
    testReadVarint(reader, true, DATA_2_BYTE_0, 2, 0);
    testReadVarint(reader, true, DATA_3_BYTE_0, 3, 0);
    testReadVarint(reader, true, DATA_4_BYTE_0, 4, 0);
    testReadVarint(reader, true, DATA_5_BYTE_0, 5, 0);
    testReadVarint(reader, true, DATA_6_BYTE_0, 6, 0);
    testReadVarint(reader, true, DATA_7_BYTE_0, 7, 0);
    testReadVarint(reader, true, DATA_8_BYTE_0, 8, 0);
    testReadVarint(reader, true, DATA_1_BYTE_64, 1, 64);
    testReadVarint(reader, true, DATA_2_BYTE_64, 2, 64);
    testReadVarint(reader, true, DATA_3_BYTE_64, 3, 64);
    testReadVarint(reader, true, DATA_4_BYTE_64, 4, 64);
    testReadVarint(reader, true, DATA_5_BYTE_64, 5, 64);
    testReadVarint(reader, true, DATA_6_BYTE_64, 6, 64);
    testReadVarint(reader, true, DATA_7_BYTE_64, 7, 64);
    testReadVarint(reader, true, DATA_8_BYTE_64, 8, 64);
    testReadVarint(reader, true, DATA_1_BYTE_MAX, 1, VALUE_1_BYTE_MAX);
    testReadVarint(reader, true, DATA_2_BYTE_MAX, 2, VALUE_2_BYTE_MAX);
    testReadVarint(reader, true, DATA_3_BYTE_MAX, 3, VALUE_3_BYTE_MAX);
    testReadVarint(reader, true, DATA_4_BYTE_MAX, 4, VALUE_4_BYTE_MAX);
    testReadVarint(reader, true, DATA_5_BYTE_MAX, 5, VALUE_5_BYTE_MAX);
    testReadVarint(reader, true, DATA_6_BYTE_MAX, 6, VALUE_6_BYTE_MAX);
    testReadVarint(reader, true, DATA_7_BYTE_MAX, 7, VALUE_7_BYTE_MAX);
    testReadVarint(reader, true, DATA_8_BYTE_MAX, 8, VALUE_8_BYTE_MAX);
    testReadVarint(reader, false, DATA_1_BYTE_MAX, 1, VALUE_1_BYTE_MAX_WITH_MASK);
    testReadVarint(reader, false, DATA_2_BYTE_MAX, 2, VALUE_2_BYTE_MAX_WITH_MASK);
    testReadVarint(reader, false, DATA_3_BYTE_MAX, 3, VALUE_3_BYTE_MAX_WITH_MASK);
    testReadVarint(reader, false, DATA_4_BYTE_MAX, 4, VALUE_4_BYTE_MAX_WITH_MASK);
    testReadVarint(reader, false, DATA_5_BYTE_MAX, 5, VALUE_5_BYTE_MAX_WITH_MASK);
    testReadVarint(reader, false, DATA_6_BYTE_MAX, 6, VALUE_6_BYTE_MAX_WITH_MASK);
    testReadVarint(reader, false, DATA_7_BYTE_MAX, 7, VALUE_7_BYTE_MAX_WITH_MASK);
    testReadVarint(reader, false, DATA_8_BYTE_MAX, 8, VALUE_8_BYTE_MAX_WITH_MASK);
  }

  @Test
  public void testReadVarintFlaky() throws IOException, InterruptedException {
    VarintReader reader = new VarintReader();
    testReadVarintFlaky(reader, true, DATA_1_BYTE_0, 1, 0);
    testReadVarintFlaky(reader, true, DATA_2_BYTE_0, 2, 0);
    testReadVarintFlaky(reader, true, DATA_3_BYTE_0, 3, 0);
    testReadVarintFlaky(reader, true, DATA_4_BYTE_0, 4, 0);
    testReadVarintFlaky(reader, true, DATA_5_BYTE_0, 5, 0);
    testReadVarintFlaky(reader, true, DATA_6_BYTE_0, 6, 0);
    testReadVarintFlaky(reader, true, DATA_7_BYTE_0, 7, 0);
    testReadVarintFlaky(reader, true, DATA_8_BYTE_0, 8, 0);
    testReadVarintFlaky(reader, true, DATA_1_BYTE_64, 1, 64);
    testReadVarintFlaky(reader, true, DATA_2_BYTE_64, 2, 64);
    testReadVarintFlaky(reader, true, DATA_3_BYTE_64, 3, 64);
    testReadVarintFlaky(reader, true, DATA_4_BYTE_64, 4, 64);
    testReadVarintFlaky(reader, true, DATA_5_BYTE_64, 5, 64);
    testReadVarintFlaky(reader, true, DATA_6_BYTE_64, 6, 64);
    testReadVarintFlaky(reader, true, DATA_7_BYTE_64, 7, 64);
    testReadVarintFlaky(reader, true, DATA_8_BYTE_64, 8, 64);
    testReadVarintFlaky(reader, true, DATA_1_BYTE_MAX, 1, VALUE_1_BYTE_MAX);
    testReadVarintFlaky(reader, true, DATA_2_BYTE_MAX, 2, VALUE_2_BYTE_MAX);
    testReadVarintFlaky(reader, true, DATA_3_BYTE_MAX, 3, VALUE_3_BYTE_MAX);
    testReadVarintFlaky(reader, true, DATA_4_BYTE_MAX, 4, VALUE_4_BYTE_MAX);
    testReadVarintFlaky(reader, true, DATA_5_BYTE_MAX, 5, VALUE_5_BYTE_MAX);
    testReadVarintFlaky(reader, true, DATA_6_BYTE_MAX, 6, VALUE_6_BYTE_MAX);
    testReadVarintFlaky(reader, true, DATA_7_BYTE_MAX, 7, VALUE_7_BYTE_MAX);
    testReadVarintFlaky(reader, true, DATA_8_BYTE_MAX, 8, VALUE_8_BYTE_MAX);
    testReadVarintFlaky(reader, false, DATA_1_BYTE_MAX, 1, VALUE_1_BYTE_MAX_WITH_MASK);
    testReadVarintFlaky(reader, false, DATA_2_BYTE_MAX, 2, VALUE_2_BYTE_MAX_WITH_MASK);
    testReadVarintFlaky(reader, false, DATA_3_BYTE_MAX, 3, VALUE_3_BYTE_MAX_WITH_MASK);
    testReadVarintFlaky(reader, false, DATA_4_BYTE_MAX, 4, VALUE_4_BYTE_MAX_WITH_MASK);
    testReadVarintFlaky(reader, false, DATA_5_BYTE_MAX, 5, VALUE_5_BYTE_MAX_WITH_MASK);
    testReadVarintFlaky(reader, false, DATA_6_BYTE_MAX, 6, VALUE_6_BYTE_MAX_WITH_MASK);
    testReadVarintFlaky(reader, false, DATA_7_BYTE_MAX, 7, VALUE_7_BYTE_MAX_WITH_MASK);
    testReadVarintFlaky(reader, false, DATA_8_BYTE_MAX, 8, VALUE_8_BYTE_MAX_WITH_MASK);
  }

  private static void testReadVarint(VarintReader reader, boolean removeMask, byte[] data,
      int expectedLength, long expectedValue) throws IOException, InterruptedException {
    ExtractorInput input = new FakeExtractorInput.Builder()
        .setData(data)
        .setSimulateUnknownLength(true)
        .build();
    long result = reader.readUnsignedVarint(input, false, removeMask, 8);
    assertThat(input.getPosition()).isEqualTo(expectedLength);
    assertThat(result).isEqualTo(expectedValue);
  }

  private static void testReadVarintFlaky(VarintReader reader, boolean removeMask, byte[] data,
      int expectedLength, long expectedValue) throws IOException, InterruptedException {
    ExtractorInput input = new FakeExtractorInput.Builder()
        .setData(data)
        .setSimulateUnknownLength(true)
        .setSimulateIOErrors(true)
        .setSimulatePartialReads(true)
        .build();
    long result = -1;
    while (result == -1) {
      try {
        result = reader.readUnsignedVarint(input, false, removeMask, 8);
        if (result == C.RESULT_END_OF_INPUT || result == C.RESULT_MAX_LENGTH_EXCEEDED) {
          // Unexpected.
          fail();
        }
      } catch (SimulatedIOException e) {
        // Expected.
      }
    }
    assertThat(input.getPosition()).isEqualTo(expectedLength);
    assertThat(result).isEqualTo(expectedValue);
  }

}
