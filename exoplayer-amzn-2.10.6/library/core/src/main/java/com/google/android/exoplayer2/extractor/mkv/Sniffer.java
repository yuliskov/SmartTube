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
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/**
 * Utility class that peeks from the input stream in order to determine whether it appears to be
 * compatible input for this extractor.
 */
/* package */ final class Sniffer {

  /**
   * The number of bytes to search for a valid header in {@link #sniff(ExtractorInput)}.
   */
  private static final int SEARCH_LENGTH = 1024;
  private static final int ID_EBML = 0x1A45DFA3;

  private final ParsableByteArray scratch;
  private int peekLength;

  public Sniffer() {
    scratch = new ParsableByteArray(8);
  }

  /**
   * @see com.google.android.exoplayer2.extractor.Extractor#sniff(ExtractorInput)
   */
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    long inputLength = input.getLength();
    int bytesToSearch = (int) (inputLength == C.LENGTH_UNSET || inputLength > SEARCH_LENGTH
        ? SEARCH_LENGTH : inputLength);
    // Find four bytes equal to ID_EBML near the start of the input.
    input.peekFully(scratch.data, 0, 4);
    long tag = scratch.readUnsignedInt();
    peekLength = 4;
    while (tag != ID_EBML) {
      if (++peekLength == bytesToSearch) {
        return false;
      }
      input.peekFully(scratch.data, 0, 1);
      tag = (tag << 8) & 0xFFFFFF00;
      tag |= scratch.data[0] & 0xFF;
    }

    // Read the size of the EBML header and make sure it is within the stream.
    long headerSize = readUint(input);
    long headerStart = peekLength;
    if (headerSize == Long.MIN_VALUE
        || (inputLength != C.LENGTH_UNSET && headerStart + headerSize >= inputLength)) {
      return false;
    }

    // Read the payload elements in the EBML header.
    while (peekLength < headerStart + headerSize) {
      long id = readUint(input);
      if (id == Long.MIN_VALUE) {
        return false;
      }
      long size = readUint(input);
      if (size < 0 || size > Integer.MAX_VALUE) {
        return false;
      }
      if (size != 0) {
        int sizeInt = (int) size;
        input.advancePeekPosition(sizeInt);
        peekLength += sizeInt;
      }
    }
    return peekLength == headerStart + headerSize;
  }

  /**
   * Peeks a variable-length unsigned EBML integer from the input.
   */
  private long readUint(ExtractorInput input) throws IOException, InterruptedException {
    input.peekFully(scratch.data, 0, 1);
    int value = scratch.data[0] & 0xFF;
    if (value == 0) {
      return Long.MIN_VALUE;
    }
    int mask = 0x80;
    int length = 0;
    while ((value & mask) == 0) {
      mask >>= 1;
      length++;
    }
    value &= ~mask;
    input.peekFully(scratch.data, 1, length);
    for (int i = 0; i < length; i++) {
      value <<= 8;
      value += scratch.data[i + 1] & 0xFF;
    }
    peekLength += length + 1;
    return value;
  }

}
