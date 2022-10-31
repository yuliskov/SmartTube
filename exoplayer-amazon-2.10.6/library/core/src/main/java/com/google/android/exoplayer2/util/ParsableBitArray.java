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

/**
 * Wraps a byte array, providing methods that allow it to be read as a bitstream.
 */
public final class ParsableBitArray {

  public byte[] data;

  // The offset within the data, stored as the current byte offset, and the bit offset within that
  // byte (from 0 to 7).
  private int byteOffset;
  private int bitOffset;
  private int byteLimit;

  /** Creates a new instance that initially has no backing data. */
  public ParsableBitArray() {
    data = Util.EMPTY_BYTE_ARRAY;
  }

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data The data to wrap.
   */
  public ParsableBitArray(byte[] data) {
    this(data, data.length);
  }

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data The data to wrap.
   * @param limit The limit in bytes.
   */
  public ParsableBitArray(byte[] data, int limit) {
    this.data = data;
    byteLimit = limit;
  }

  /**
   * Updates the instance to wrap {@code data}, and resets the position to zero.
   *
   * @param data The array to wrap.
   */
  public void reset(byte[] data) {
    reset(data, data.length);
  }

  /**
   * Sets this instance's data, position and limit to match the provided {@code parsableByteArray}.
   * Any modifications to the underlying data array will be visible in both instances
   *
   * @param parsableByteArray The {@link ParsableByteArray}.
   */
  public void reset(ParsableByteArray parsableByteArray) {
    reset(parsableByteArray.data, parsableByteArray.limit());
    setPosition(parsableByteArray.getPosition() * 8);
  }

  /**
   * Updates the instance to wrap {@code data}, and resets the position to zero.
   *
   * @param data The array to wrap.
   * @param limit The limit in bytes.
   */
  public void reset(byte[] data, int limit) {
    this.data = data;
    byteOffset = 0;
    bitOffset = 0;
    byteLimit = limit;
  }

  /**
   * Returns the number of bits yet to be read.
   */
  public int bitsLeft() {
    return (byteLimit - byteOffset) * 8 - bitOffset;
  }

  /**
   * Returns the current bit offset.
   */
  public int getPosition() {
    return byteOffset * 8 + bitOffset;
  }

  /**
   * Returns the current byte offset. Must only be called when the position is byte aligned.
   *
   * @throws IllegalStateException If the position isn't byte aligned.
   */
  public int getBytePosition() {
    Assertions.checkState(bitOffset == 0);
    return byteOffset;
  }

  /**
   * Sets the current bit offset.
   *
   * @param position The position to set.
   */
  public void setPosition(int position) {
    byteOffset = position / 8;
    bitOffset = position - (byteOffset * 8);
    assertValidOffset();
  }

  /**
   * Skips a single bit.
   */
  public void skipBit() {
    if (++bitOffset == 8) {
      bitOffset = 0;
      byteOffset++;
    }
    assertValidOffset();
  }

  /**
   * Skips bits and moves current reading position forward.
   *
   * @param numBits The number of bits to skip.
   */
  public void skipBits(int numBits) {
    int numBytes = numBits / 8;
    byteOffset += numBytes;
    bitOffset += numBits - (numBytes * 8);
    if (bitOffset > 7) {
      byteOffset++;
      bitOffset -= 8;
    }
    assertValidOffset();
  }

  /**
   * Reads a single bit.
   *
   * @return Whether the bit is set.
   */
  public boolean readBit() {
    boolean returnValue = (data[byteOffset] & (0x80 >> bitOffset)) != 0;
    skipBit();
    return returnValue;
  }

  /**
   * Reads up to 32 bits.
   *
   * @param numBits The number of bits to read.
   * @return An integer whose bottom n bits hold the read data.
   */
  public int readBits(int numBits) {
    if (numBits == 0) {
      return 0;
    }
    int returnValue = 0;
    bitOffset += numBits;
    while (bitOffset > 8) {
      bitOffset -= 8;
      returnValue |= (data[byteOffset++] & 0xFF) << bitOffset;
    }
    returnValue |= (data[byteOffset] & 0xFF) >> (8 - bitOffset);
    returnValue &= 0xFFFFFFFF >>> (32 - numBits);
    if (bitOffset == 8) {
      bitOffset = 0;
      byteOffset++;
    }
    assertValidOffset();
    return returnValue;
  }

  /**
   * Reads {@code numBits} bits into {@code buffer}.
   *
   * @param buffer The array into which the read data should be written. The trailing
   *     {@code numBits % 8} bits are written into the most significant bits of the last modified
   *     {@code buffer} byte. The remaining ones are unmodified.
   * @param offset The offset in {@code buffer} at which the read data should be written.
   * @param numBits The number of bits to read.
   */
  public void readBits(byte[] buffer, int offset, int numBits) {
    // Whole bytes.
    int to = offset + (numBits >> 3) /* numBits / 8 */;
    for (int i = offset; i < to; i++) {
      buffer[i] = (byte) (data[byteOffset++] << bitOffset);
      buffer[i] = (byte) (buffer[i] | ((data[byteOffset] & 0xFF) >> (8 - bitOffset)));
    }
    // Trailing bits.
    int bitsLeft = numBits & 7 /* numBits % 8 */;
    if (bitsLeft == 0) {
      return;
    }
    // Set bits that are going to be overwritten to 0.
    buffer[to] = (byte) (buffer[to] & (0xFF >> bitsLeft));
    if (bitOffset + bitsLeft > 8) {
      // We read the rest of data[byteOffset] and increase byteOffset.
      buffer[to] = (byte) (buffer[to] | ((data[byteOffset++] & 0xFF) << bitOffset));
      bitOffset -= 8;
    }
    bitOffset += bitsLeft;
    int lastDataByteTrailingBits = (data[byteOffset] & 0xFF) >> (8 - bitOffset);
    buffer[to] |= (byte) (lastDataByteTrailingBits << (8 - bitsLeft));
    if (bitOffset == 8) {
      bitOffset = 0;
      byteOffset++;
    }
    assertValidOffset();
  }

  /**
   * Aligns the position to the next byte boundary. Does nothing if the position is already aligned.
   */
  public void byteAlign() {
    if (bitOffset == 0) {
      return;
    }
    bitOffset = 0;
    byteOffset++;
    assertValidOffset();
  }

  /**
   * Reads the next {@code length} bytes into {@code buffer}. Must only be called when the position
   * is byte aligned.
   *
   * @see System#arraycopy(Object, int, Object, int, int)
   * @param buffer The array into which the read data should be written.
   * @param offset The offset in {@code buffer} at which the read data should be written.
   * @param length The number of bytes to read.
   * @throws IllegalStateException If the position isn't byte aligned.
   */
  public void readBytes(byte[] buffer, int offset, int length) {
    Assertions.checkState(bitOffset == 0);
    System.arraycopy(data, byteOffset, buffer, offset, length);
    byteOffset += length;
    assertValidOffset();
  }

  /**
   * Skips the next {@code length} bytes. Must only be called when the position is byte aligned.
   *
   * @param length The number of bytes to read.
   * @throws IllegalStateException If the position isn't byte aligned.
   */
  public void skipBytes(int length) {
    Assertions.checkState(bitOffset == 0);
    byteOffset += length;
    assertValidOffset();
  }

  /**
   * Overwrites {@code numBits} from this array using the {@code numBits} least significant bits
   * from {@code value}. Bits are written in order from most significant to least significant. The
   * read position is advanced by {@code numBits}.
   *
   * @param value The integer whose {@code numBits} least significant bits are written into {@link
   *     #data}.
   * @param numBits The number of bits to write.
   */
  public void putInt(int value, int numBits) {
    int remainingBitsToRead = numBits;
    if (numBits < 32) {
      value &= (1 << numBits) - 1;
    }
    int firstByteReadSize = Math.min(8 - bitOffset, numBits);
    int firstByteRightPaddingSize = 8 - bitOffset - firstByteReadSize;
    int firstByteBitmask = (0xFF00 >> bitOffset) | ((1 << firstByteRightPaddingSize) - 1);
    data[byteOffset] = (byte) (data[byteOffset] & firstByteBitmask);
    int firstByteInputBits = value >>> (numBits - firstByteReadSize);
    data[byteOffset] =
        (byte) (data[byteOffset] | (firstByteInputBits << firstByteRightPaddingSize));
    remainingBitsToRead -= firstByteReadSize;
    int currentByteIndex = byteOffset + 1;
    while (remainingBitsToRead > 8) {
      data[currentByteIndex++] = (byte) (value >>> (remainingBitsToRead - 8));
      remainingBitsToRead -= 8;
    }
    int lastByteRightPaddingSize = 8 - remainingBitsToRead;
    data[currentByteIndex] =
        (byte) (data[currentByteIndex] & ((1 << lastByteRightPaddingSize) - 1));
    int lastByteInput = value & ((1 << remainingBitsToRead) - 1);
    data[currentByteIndex] =
        (byte) (data[currentByteIndex] | (lastByteInput << lastByteRightPaddingSize));
    skipBits(numBits);
    assertValidOffset();
  }

  private void assertValidOffset() {
    // It is fine for position to be at the end of the array, but no further.
    Assertions.checkState(byteOffset >= 0
        && (byteOffset < byteLimit || (byteOffset == byteLimit && bitOffset == 0)));
  }

}
