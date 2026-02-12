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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;

/**
 * Data object to store header information.
 */
/* package */  final class OggPageHeader {

  public static final int EMPTY_PAGE_HEADER_SIZE = 27;
  public static final int MAX_SEGMENT_COUNT = 255;
  public static final int MAX_PAGE_PAYLOAD = 255 * 255;
  public static final int MAX_PAGE_SIZE = EMPTY_PAGE_HEADER_SIZE + MAX_SEGMENT_COUNT
      + MAX_PAGE_PAYLOAD;

  private static final int TYPE_OGGS = Util.getIntegerCodeForString("OggS");

  public int revision;
  public int type;
  /**
   * The absolute granule position of the page. This is the total number of samples from the start
   * of the file up to the <em>end</em> of the page. Samples partially in the page that continue on
   * the next page do not count.
   */
  public long granulePosition;

  public long streamSerialNumber;
  public long pageSequenceNumber;
  public long pageChecksum;
  public int pageSegmentCount;
  public int headerSize;
  public int bodySize;
  /**
   * Be aware that {@code laces.length} is always {@link #MAX_SEGMENT_COUNT}. Instead use
   * {@link #pageSegmentCount} to iterate.
   */
  public final int[] laces = new int[MAX_SEGMENT_COUNT];

  private final ParsableByteArray scratch = new ParsableByteArray(MAX_SEGMENT_COUNT);

  /**
   * Resets all primitive member fields to zero.
   */
  public void reset() {
    revision = 0;
    type = 0;
    granulePosition = 0;
    streamSerialNumber = 0;
    pageSequenceNumber = 0;
    pageChecksum = 0;
    pageSegmentCount = 0;
    headerSize = 0;
    bodySize = 0;
  }

  /**
   * Peeks an Ogg page header and updates this {@link OggPageHeader}.
   *
   * @param input The {@link ExtractorInput} to read from.
   * @param quiet Whether to return {@code false} rather than throwing an exception if the header
   *     cannot be populated.
   * @return Whether the read was successful. The read fails if the end of the input is encountered
   *     without reading data.
   * @throws IOException If reading data fails or the stream is invalid.
   * @throws InterruptedException If the thread is interrupted.
   */
  public boolean populate(ExtractorInput input, boolean quiet)
      throws IOException, InterruptedException {
    scratch.reset();
    reset();
    boolean hasEnoughBytes = input.getLength() == C.LENGTH_UNSET
        || input.getLength() - input.getPeekPosition() >= EMPTY_PAGE_HEADER_SIZE;
    if (!hasEnoughBytes || !input.peekFully(scratch.data, 0, EMPTY_PAGE_HEADER_SIZE, true)) {
      if (quiet) {
        return false;
      } else {
        throw new EOFException();
      }
    }
    if (scratch.readUnsignedInt() != TYPE_OGGS) {
      if (quiet) {
        return false;
      } else {
        throw new ParserException("expected OggS capture pattern at begin of page");
      }
    }

    revision = scratch.readUnsignedByte();
    if (revision != 0x00) {
      if (quiet) {
        return false;
      } else {
        throw new ParserException("unsupported bit stream revision");
      }
    }
    type = scratch.readUnsignedByte();

    granulePosition = scratch.readLittleEndianLong();
    streamSerialNumber = scratch.readLittleEndianUnsignedInt();
    pageSequenceNumber = scratch.readLittleEndianUnsignedInt();
    pageChecksum = scratch.readLittleEndianUnsignedInt();
    pageSegmentCount = scratch.readUnsignedByte();
    headerSize = EMPTY_PAGE_HEADER_SIZE + pageSegmentCount;

    // calculate total size of header including laces
    scratch.reset();
    input.peekFully(scratch.data, 0, pageSegmentCount);
    for (int i = 0; i < pageSegmentCount; i++) {
      laces[i] = scratch.readUnsignedByte();
      bodySize += laces[i];
    }

    return true;
  }
}
