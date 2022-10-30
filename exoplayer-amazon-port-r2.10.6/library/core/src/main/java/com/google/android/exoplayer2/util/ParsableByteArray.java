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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Wraps a byte array, providing a set of methods for parsing data from it. Numerical values are
 * parsed with the assumption that their constituent bytes are in big endian order.
 */
public final class ParsableByteArray {

  public byte[] data;

  private int position;
  private int limit;

  /** Creates a new instance that initially has no backing data. */
  public ParsableByteArray() {
    data = Util.EMPTY_BYTE_ARRAY;
  }

  /**
   * Creates a new instance with {@code limit} bytes and sets the limit.
   *
   * @param limit The limit to set.
   */
  public ParsableByteArray(int limit) {
    this.data = new byte[limit];
    this.limit = limit;
  }

  /**
   * Creates a new instance wrapping {@code data}, and sets the limit to {@code data.length}.
   *
   * @param data The array to wrap.
   */
  public ParsableByteArray(byte[] data) {
    this.data = data;
    limit = data.length;
  }

  /**
   * Creates a new instance that wraps an existing array.
   *
   * @param data The data to wrap.
   * @param limit The limit to set.
   */
  public ParsableByteArray(byte[] data, int limit) {
    this.data = data;
    this.limit = limit;
  }

  /** Sets the position and limit to zero. */
  public void reset() {
    position = 0;
    limit = 0;
  }

  /**
   * Resets the position to zero and the limit to the specified value. If the limit exceeds the
   * capacity, {@code data} is replaced with a new array of sufficient size.
   *
   * @param limit The limit to set.
   */
  public void reset(int limit) {
    reset(capacity() < limit ? new byte[limit] : data, limit);
  }

  /**
   * Updates the instance to wrap {@code data}, and resets the position to zero and the limit to
   * {@code data.length}.
   *
   * @param data The array to wrap.
   */
  public void reset(byte[] data) {
    reset(data, data.length);
  }

  /**
   * Updates the instance to wrap {@code data}, and resets the position to zero.
   *
   * @param data The array to wrap.
   * @param limit The limit to set.
   */
  public void reset(byte[] data, int limit) {
    this.data = data;
    this.limit = limit;
    position = 0;
  }

  /**
   * Returns the number of bytes yet to be read.
   */
  public int bytesLeft() {
    return limit - position;
  }

  /**
   * Returns the limit.
   */
  public int limit() {
    return limit;
  }

  /**
   * Sets the limit.
   *
   * @param limit The limit to set.
   */
  public void setLimit(int limit) {
    Assertions.checkArgument(limit >= 0 && limit <= data.length);
    this.limit = limit;
  }

  /**
   * Returns the current offset in the array, in bytes.
   */
  public int getPosition() {
    return position;
  }

  /**
   * Returns the capacity of the array, which may be larger than the limit.
   */
  public int capacity() {
    return data.length;
  }

  /**
   * Sets the reading offset in the array.
   *
   * @param position Byte offset in the array from which to read.
   * @throws IllegalArgumentException Thrown if the new position is neither in nor at the end of the
   *     array.
   */
  public void setPosition(int position) {
    // It is fine for position to be at the end of the array.
    Assertions.checkArgument(position >= 0 && position <= limit);
    this.position = position;
  }

  /**
   * Moves the reading offset by {@code bytes}.
   *
   * @param bytes The number of bytes to skip.
   * @throws IllegalArgumentException Thrown if the new position is neither in nor at the end of the
   *     array.
   */
  public void skipBytes(int bytes) {
    setPosition(position + bytes);
  }

  /**
   * Reads the next {@code length} bytes into {@code bitArray}, and resets the position of
   * {@code bitArray} to zero.
   *
   * @param bitArray The {@link ParsableBitArray} into which the bytes should be read.
   * @param length The number of bytes to write.
   */
  public void readBytes(ParsableBitArray bitArray, int length) {
    readBytes(bitArray.data, 0, length);
    bitArray.setPosition(0);
  }

  /**
   * Reads the next {@code length} bytes into {@code buffer} at {@code offset}.
   *
   * @see System#arraycopy(Object, int, Object, int, int)
   * @param buffer The array into which the read data should be written.
   * @param offset The offset in {@code buffer} at which the read data should be written.
   * @param length The number of bytes to read.
   */
  public void readBytes(byte[] buffer, int offset, int length) {
    System.arraycopy(data, position, buffer, offset, length);
    position += length;
  }

  /**
   * Reads the next {@code length} bytes into {@code buffer}.
   *
   * @see ByteBuffer#put(byte[], int, int)
   * @param buffer The {@link ByteBuffer} into which the read data should be written.
   * @param length The number of bytes to read.
   */
  public void readBytes(ByteBuffer buffer, int length) {
    buffer.put(data, position, length);
    position += length;
  }

  /**
   * Peeks at the next byte as an unsigned value.
   */
  public int peekUnsignedByte() {
    return (data[position] & 0xFF);
  }

  /**
   * Peeks at the next char.
   */
  public char peekChar() {
    return (char) ((data[position] & 0xFF) << 8
        | (data[position + 1] & 0xFF));
  }

  /**
   * Reads the next byte as an unsigned value.
   */
  public int readUnsignedByte() {
    return (data[position++] & 0xFF);
  }

  /**
   * Reads the next two bytes as an unsigned value.
   */
  public int readUnsignedShort() {
    return (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
  }

  /**
   * Reads the next two bytes as an unsigned value.
   */
  public int readLittleEndianUnsignedShort() {
    return (data[position++] & 0xFF) | (data[position++] & 0xFF) << 8;
  }

  /**
   * Reads the next two bytes as a signed value.
   */
  public short readShort() {
    return (short) ((data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF));
  }

  /**
   * Reads the next two bytes as a signed value.
   */
  public short readLittleEndianShort() {
    return (short) ((data[position++] & 0xFF) | (data[position++] & 0xFF) << 8);
  }

  /**
   * Reads the next three bytes as an unsigned value.
   */
  public int readUnsignedInt24() {
    return (data[position++] & 0xFF) << 16
        | (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
  }

  /**
   * Reads the next three bytes as a signed value.
   */
  public int readInt24() {
    return ((data[position++] & 0xFF) << 24) >> 8
        | (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
  }

  /**
   * Reads the next three bytes as a signed value in little endian order.
   */
  public int readLittleEndianInt24() {
    return (data[position++] & 0xFF)
        | (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF) << 16;
  }

  /**
   * Reads the next three bytes as an unsigned value in little endian order.
   */
  public int readLittleEndianUnsignedInt24() {
    return (data[position++] & 0xFF)
        | (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF) << 16;
  }

  /**
   * Reads the next four bytes as an unsigned value.
   */
  public long readUnsignedInt() {
    return (data[position++] & 0xFFL) << 24
        | (data[position++] & 0xFFL) << 16
        | (data[position++] & 0xFFL) << 8
        | (data[position++] & 0xFFL);
  }

  /**
   * Reads the next four bytes as an unsigned value in little endian order.
   */
  public long readLittleEndianUnsignedInt() {
    return (data[position++] & 0xFFL)
        | (data[position++] & 0xFFL) << 8
        | (data[position++] & 0xFFL) << 16
        | (data[position++] & 0xFFL) << 24;
  }

  /**
   * Reads the next four bytes as a signed value
   */
  public int readInt() {
    return (data[position++] & 0xFF) << 24
        | (data[position++] & 0xFF) << 16
        | (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
  }

  /**
   * Reads the next four bytes as a signed value in little endian order.
   */
  public int readLittleEndianInt() {
    return (data[position++] & 0xFF)
        | (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF) << 16
        | (data[position++] & 0xFF) << 24;
  }

  /**
   * Reads the next eight bytes as a signed value.
   */
  public long readLong() {
    return (data[position++] & 0xFFL) << 56
        | (data[position++] & 0xFFL) << 48
        | (data[position++] & 0xFFL) << 40
        | (data[position++] & 0xFFL) << 32
        | (data[position++] & 0xFFL) << 24
        | (data[position++] & 0xFFL) << 16
        | (data[position++] & 0xFFL) << 8
        | (data[position++] & 0xFFL);
  }

  /**
   * Reads the next eight bytes as a signed value in little endian order.
   */
  public long readLittleEndianLong() {
    return (data[position++] & 0xFFL)
        | (data[position++] & 0xFFL) << 8
        | (data[position++] & 0xFFL) << 16
        | (data[position++] & 0xFFL) << 24
        | (data[position++] & 0xFFL) << 32
        | (data[position++] & 0xFFL) << 40
        | (data[position++] & 0xFFL) << 48
        | (data[position++] & 0xFFL) << 56;
  }

  /**
   * Reads the next four bytes, returning the integer portion of the fixed point 16.16 integer.
   */
  public int readUnsignedFixedPoint1616() {
    int result = (data[position++] & 0xFF) << 8
        | (data[position++] & 0xFF);
    position += 2; // Skip the non-integer portion.
    return result;
  }

  /**
   * Reads a Synchsafe integer.
   * <p>
   * Synchsafe integers keep the highest bit of every byte zeroed. A 32 bit synchsafe integer can
   * store 28 bits of information.
   *
   * @return The parsed value.
   */
  public int readSynchSafeInt() {
    int b1 = readUnsignedByte();
    int b2 = readUnsignedByte();
    int b3 = readUnsignedByte();
    int b4 = readUnsignedByte();
    return (b1 << 21) | (b2 << 14) | (b3 << 7) | b4;
  }

  /**
   * Reads the next four bytes as an unsigned integer into an integer, if the top bit is a zero.
   *
   * @throws IllegalStateException Thrown if the top bit of the input data is set.
   */
  public int readUnsignedIntToInt() {
    int result = readInt();
    if (result < 0) {
      throw new IllegalStateException("Top bit not zero: " + result);
    }
    return result;
  }

  /**
   * Reads the next four bytes as a little endian unsigned integer into an integer, if the top bit
   * is a zero.
   *
   * @throws IllegalStateException Thrown if the top bit of the input data is set.
   */
  public int readLittleEndianUnsignedIntToInt() {
    int result = readLittleEndianInt();
    if (result < 0) {
      throw new IllegalStateException("Top bit not zero: " + result);
    }
    return result;
  }

  /**
   * Reads the next eight bytes as an unsigned long into a long, if the top bit is a zero.
   *
   * @throws IllegalStateException Thrown if the top bit of the input data is set.
   */
  public long readUnsignedLongToLong() {
    long result = readLong();
    if (result < 0) {
      throw new IllegalStateException("Top bit not zero: " + result);
    }
    return result;
  }

  /**
   * Reads the next four bytes as a 32-bit floating point value.
   */
  public float readFloat() {
    return Float.intBitsToFloat(readInt());
  }

  /**
   * Reads the next eight bytes as a 64-bit floating point value.
   */
  public double readDouble() {
    return Double.longBitsToDouble(readLong());
  }

  /**
   * Reads the next {@code length} bytes as UTF-8 characters.
   *
   * @param length The number of bytes to read.
   * @return The string encoded by the bytes.
   */
  public String readString(int length) {
    return readString(length, Charset.forName(C.UTF8_NAME));
  }

  /**
   * Reads the next {@code length} bytes as characters in the specified {@link Charset}.
   *
   * @param length The number of bytes to read.
   * @param charset The character set of the encoded characters.
   * @return The string encoded by the bytes in the specified character set.
   */
  public String readString(int length, Charset charset) {
    String result = new String(data, position, length, charset);
    position += length;
    return result;
  }

  /**
   * Reads the next {@code length} bytes as UTF-8 characters. A terminating NUL byte is discarded,
   * if present.
   *
   * @param length The number of bytes to read.
   * @return The string, not including any terminating NUL byte.
   */
  public String readNullTerminatedString(int length) {
    if (length == 0) {
      return "";
    }
    int stringLength = length;
    int lastIndex = position + length - 1;
    if (lastIndex < limit && data[lastIndex] == 0) {
      stringLength--;
    }
    String result = Util.fromUtf8Bytes(data, position, stringLength);
    position += length;
    return result;
  }

  /**
   * Reads up to the next NUL byte (or the limit) as UTF-8 characters.
   *
   * @return The string not including any terminating NUL byte, or null if the end of the data has
   *     already been reached.
   */
  public @Nullable String readNullTerminatedString() {
    if (bytesLeft() == 0) {
      return null;
    }
    int stringLimit = position;
    while (stringLimit < limit && data[stringLimit] != 0) {
      stringLimit++;
    }
    String string = Util.fromUtf8Bytes(data, position, stringLimit - position);
    position = stringLimit;
    if (position < limit) {
      position++;
    }
    return string;
  }

  /**
   * Reads a line of text.
   *
   * <p>A line is considered to be terminated by any one of a carriage return ('\r'), a line feed
   * ('\n'), or a carriage return followed immediately by a line feed ('\r\n'). The system's default
   * charset (UTF-8) is used. This method discards leading UTF-8 byte order marks, if present.
   *
   * @return The line not including any line-termination characters, or null if the end of the data
   *     has already been reached.
   */
  public @Nullable String readLine() {
    if (bytesLeft() == 0) {
      return null;
    }
    int lineLimit = position;
    while (lineLimit < limit && !Util.isLinebreak(data[lineLimit])) {
      lineLimit++;
    }
    if (lineLimit - position >= 3 && data[position] == (byte) 0xEF
        && data[position + 1] == (byte) 0xBB && data[position + 2] == (byte) 0xBF) {
      // There's a UTF-8 byte order mark at the start of the line. Discard it.
      position += 3;
    }
    String line = Util.fromUtf8Bytes(data, position, lineLimit - position);
    position = lineLimit;
    if (position == limit) {
      return line;
    }
    if (data[position] == '\r') {
      position++;
      if (position == limit) {
        return line;
      }
    }
    if (data[position] == '\n') {
      position++;
    }
    return line;
  }

  /**
   * Reads a long value encoded by UTF-8 encoding
   *
   * @throws NumberFormatException if there is a problem with decoding
   * @return Decoded long value
   */
  public long readUtf8EncodedLong() {
    int length = 0;
    long value = data[position];
    // find the high most 0 bit
    for (int j = 7; j >= 0; j--) {
      if ((value & (1 << j)) == 0) {
        if (j < 6) {
          value &= (1 << j) - 1;
          length = 7 - j;
        } else if (j == 7) {
          length = 1;
        }
        break;
      }
    }
    if (length == 0) {
      throw new NumberFormatException("Invalid UTF-8 sequence first byte: " + value);
    }
    for (int i = 1; i < length; i++) {
      int x = data[position + i];
      if ((x & 0xC0) != 0x80) { // if the high most 0 bit not 7th
        throw new NumberFormatException("Invalid UTF-8 sequence continuation byte: " + value);
      }
      value = (value << 6) | (x & 0x3F);
    }
    position += length;
    return value;
  }

}
