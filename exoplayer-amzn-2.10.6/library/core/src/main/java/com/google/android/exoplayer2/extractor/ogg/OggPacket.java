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
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.Arrays;

/**
 * OGG packet class.
 */
/* package */ final class OggPacket {

  private final OggPageHeader pageHeader = new OggPageHeader();
  private final ParsableByteArray packetArray = new ParsableByteArray(
      new byte[OggPageHeader.MAX_PAGE_PAYLOAD], 0);

  private int currentSegmentIndex = C.INDEX_UNSET;
  private int segmentCount;
  private boolean populated;

  /**
   * Resets this reader.
   */
  public void reset() {
    pageHeader.reset();
    packetArray.reset();
    currentSegmentIndex = C.INDEX_UNSET;
    populated = false;
  }

  /**
   * Reads the next packet of the ogg stream. In case of an {@code IOException} the caller must make
   * sure to pass the same instance of {@code ParsableByteArray} to this method again so this reader
   * can resume properly from an error while reading a continued packet spanned across multiple
   * pages.
   *
   * @param input The {@link ExtractorInput} to read data from.
   * @return {@code true} if the read was successful. The read fails if the end of the input is
   *     encountered without reading data.
   * @throws IOException If reading from the input fails.
   * @throws InterruptedException If the thread is interrupted.
   */
  public boolean populate(ExtractorInput input) throws IOException, InterruptedException {
    Assertions.checkState(input != null);

    if (populated) {
      populated = false;
      packetArray.reset();
    }

    while (!populated) {
      if (currentSegmentIndex < 0) {
        // We're at the start of a page.
        if (!pageHeader.populate(input, true)) {
          return false;
        }
        int segmentIndex = 0;
        int bytesToSkip = pageHeader.headerSize;
        if ((pageHeader.type & 0x01) == 0x01 && packetArray.limit() == 0) {
          // After seeking, the first packet may be the remainder
          // part of a continued packet which has to be discarded.
          bytesToSkip += calculatePacketSize(segmentIndex);
          segmentIndex += segmentCount;
        }
        input.skipFully(bytesToSkip);
        currentSegmentIndex = segmentIndex;
      }

      int size = calculatePacketSize(currentSegmentIndex);
      int segmentIndex = currentSegmentIndex + segmentCount;
      if (size > 0) {
        if (packetArray.capacity() < packetArray.limit() + size) {
          packetArray.data = Arrays.copyOf(packetArray.data, packetArray.limit() + size);
        }
        input.readFully(packetArray.data, packetArray.limit(), size);
        packetArray.setLimit(packetArray.limit() + size);
        populated = pageHeader.laces[segmentIndex - 1] != 255;
      }
      // Advance now since we are sure reading didn't throw an exception.
      currentSegmentIndex = segmentIndex == pageHeader.pageSegmentCount ? C.INDEX_UNSET
          : segmentIndex;
    }
    return true;
  }

  /**
   * An OGG Packet may span multiple pages. Returns the {@link OggPageHeader} of the last page read,
   * or an empty header if the packet has yet to be populated.
   *
   * <p>Note that the returned {@link OggPageHeader} is mutable and may be updated during subsequent
   * calls to {@link #populate(ExtractorInput)}.
   *
   * @return the {@code PageHeader} of the last page read or an empty header if the packet has yet
   *     to be populated.
   */
  public OggPageHeader getPageHeader() {
    return pageHeader;
  }

  /**
   * Returns a {@link ParsableByteArray} containing the packet's payload.
   */
  public ParsableByteArray getPayload() {
    return packetArray;
  }

  /**
   * Trims the packet data array.
   */
  public void trimPayload() {
    if (packetArray.data.length == OggPageHeader.MAX_PAGE_PAYLOAD) {
      return;
    }
    packetArray.data = Arrays.copyOf(packetArray.data, Math.max(OggPageHeader.MAX_PAGE_PAYLOAD,
        packetArray.limit()));
  }

  /**
   * Calculates the size of the packet starting from {@code startSegmentIndex}.
   *
   * @param startSegmentIndex the index of the first segment of the packet.
   * @return Size of the packet.
   */
  private int calculatePacketSize(int startSegmentIndex) {
    segmentCount = 0;
    int size = 0;
    while (startSegmentIndex + segmentCount < pageHeader.pageSegmentCount) {
      int segmentLength = pageHeader.laces[startSegmentIndex + segmentCount++];
      size += segmentLength;
      if (segmentLength != 255) {
        // packets end at first lace < 255
        break;
      }
    }
    return size;
  }

}
