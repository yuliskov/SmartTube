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

import com.google.android.exoplayer2.util.Assertions;

/**
 * Wraps a byte array, providing methods that allow it to be read as a vorbis bitstream.
 *
 * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-360002">Vorbis bitpacking
 *     specification</a>
 */
/* package */ final class VorbisBitArray {

  private final byte[] data;
  private final int byteLimit;

  private int byteOffset;
  private int bitOffset;

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data the array to wrap.
   */
  public VorbisBitArray(byte[] data) {
    this.data = data;
    byteLimit = data.length;
  }

  /**
   * Resets the reading position to zero.
   */
  public void reset() {
    byteOffset = 0;
    bitOffset = 0;
  }

  /**
   * Reads a single bit.
   *
   * @return {@code true} if the bit is set, {@code false} otherwise.
   */
  public boolean readBit() {
    boolean returnValue = (((data[byteOffset] & 0xFF) >> bitOffset) & 0x01) == 1;
    skipBits(1);
    return returnValue;
  }

  /**
   * Reads up to 32 bits.
   *
   * @param numBits The number of bits to read.
   * @return An integer whose bottom {@code numBits} bits hold the read data.
   */
  public int readBits(int numBits) {
    int tempByteOffset = byteOffset;
    int bitsRead = Math.min(numBits, 8 - bitOffset);
    int returnValue = ((data[tempByteOffset++] & 0xFF) >> bitOffset) & (0xFF >> (8 - bitsRead));
    while (bitsRead < numBits) {
      returnValue |= (data[tempByteOffset++] & 0xFF) << bitsRead;
      bitsRead += 8;
    }
    returnValue &= 0xFFFFFFFF >>> (32 - numBits);
    skipBits(numBits);
    return returnValue;
  }

  /**
   * Skips {@code numberOfBits} bits.
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
   * Returns the reading position in bits.
   */
  public int getPosition() {
    return byteOffset * 8 + bitOffset;
  }

  /**
   * Sets the reading position in bits.
   *
   * @param position The new reading position in bits.
   */
  public void setPosition(int position) {
    byteOffset = position / 8;
    bitOffset = position - (byteOffset * 8);
    assertValidOffset();
  }

  /**
   * Returns the number of remaining bits.
   */
  public int bitsLeft() {
    return (byteLimit - byteOffset) * 8 - bitOffset;
  }

  private void assertValidOffset() {
    // It is fine for position to be at the end of the array, but no further.
    Assertions.checkState(byteOffset >= 0
        && (byteOffset < byteLimit || (byteOffset == byteLimit && bitOffset == 0)));
  }

}
