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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.EOFException;
import java.io.IOException;

/**
 * Reads EBML variable-length integers (varints) from an {@link ExtractorInput}.
 */
/* package */ final class VarintReader {

  private static final int STATE_BEGIN_READING = 0;
  private static final int STATE_READ_CONTENTS = 1;

  /**
   * The first byte of a variable-length integer (varint) will have one of these bit masks
   * indicating the total length in bytes.
   *
   * <p>{@code 0x80} is a one-byte integer, {@code 0x40} is two bytes, and so on up to eight bytes.
   */
  private static final long[] VARINT_LENGTH_MASKS = new long[] {
    0x80L, 0x40L, 0x20L, 0x10L, 0x08L, 0x04L, 0x02L, 0x01L
  };

  private final byte[] scratch;

  private int state;
  private int length;

  public VarintReader() {
    scratch = new byte[8];
  }

  /**
   * Resets the reader to start reading a new variable-length integer.
   */
  public void reset() {
    state = STATE_BEGIN_READING;
    length = 0;
  }

  /**
   * Reads an EBML variable-length integer (varint) from an {@link ExtractorInput} such that
   * reading can be resumed later if an error occurs having read only some of it.
   * <p>
   * If an value is successfully read, then the reader will automatically reset itself ready to
   * read another value.
   * <p>
   * If an {@link IOException} or {@link InterruptedException} is throw, the read can be resumed
   * later by calling this method again, passing an {@link ExtractorInput} providing data starting
   * where the previous one left off.
   *
   * @param input The {@link ExtractorInput} from which the integer should be read.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@link C#RESULT_END_OF_INPUT} being returned. False if it
   *     should be considered an error, causing an {@link EOFException} to be thrown.
   * @param removeLengthMask Removes the variable-length integer length mask from the value.
   * @param maximumAllowedLength Maximum allowed length of the variable integer to be read.
   * @return The read value, or {@link C#RESULT_END_OF_INPUT} if {@code allowEndOfStream} is true
   *     and the end of the input was encountered, or {@link C#RESULT_MAX_LENGTH_EXCEEDED} if the
   *     length of the varint exceeded maximumAllowedLength.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  public long readUnsignedVarint(ExtractorInput input, boolean allowEndOfInput,
      boolean removeLengthMask, int maximumAllowedLength) throws IOException, InterruptedException {
    if (state == STATE_BEGIN_READING) {
      // Read the first byte to establish the length.
      if (!input.readFully(scratch, 0, 1, allowEndOfInput)) {
        return C.RESULT_END_OF_INPUT;
      }
      int firstByte = scratch[0] & 0xFF;
      length = parseUnsignedVarintLength(firstByte);
      if (length == C.LENGTH_UNSET) {
        throw new IllegalStateException("No valid varint length mask found");
      }
      state = STATE_READ_CONTENTS;
    }

    if (length > maximumAllowedLength) {
      state = STATE_BEGIN_READING;
      return C.RESULT_MAX_LENGTH_EXCEEDED;
    }

    if (length != 1) {
      // Read the remaining bytes.
      input.readFully(scratch, 1, length - 1);
    }

    state = STATE_BEGIN_READING;
    return assembleVarint(scratch, length, removeLengthMask);
  }

  /**
   * Returns the number of bytes occupied by the most recently parsed varint.
   */
  public int getLastLength() {
    return length;
  }

  /**
   * Parses and the length of the varint given the first byte.
   *
   * @param firstByte First byte of the varint.
   * @return Length of the varint beginning with the given byte if it was valid,
   *     {@link C#LENGTH_UNSET} otherwise.
   */
  public static int parseUnsignedVarintLength(int firstByte) {
    int varIntLength = C.LENGTH_UNSET;
    for (int i = 0; i < VARINT_LENGTH_MASKS.length; i++) {
      if ((VARINT_LENGTH_MASKS[i] & firstByte) != 0) {
        varIntLength = i + 1;
        break;
      }
    }
    return varIntLength;
  }

  /**
   * Assemble a varint from the given byte array.
   *
   * @param varintBytes Bytes that make up the varint.
   * @param varintLength Length of the varint to assemble.
   * @param removeLengthMask Removes the variable-length integer length mask from the value.
   * @return Parsed and assembled varint.
   */
  public static long assembleVarint(byte[] varintBytes, int varintLength,
      boolean removeLengthMask) {
    long varint = varintBytes[0] & 0xFFL;
    if (removeLengthMask) {
      varint &= ~VARINT_LENGTH_MASKS[varintLength - 1];
    }
    for (int i = 1; i < varintLength; i++) {
      varint = (varint << 8) | (varintBytes[i] & 0xFFL);
    }
    return varint;
  }

}
