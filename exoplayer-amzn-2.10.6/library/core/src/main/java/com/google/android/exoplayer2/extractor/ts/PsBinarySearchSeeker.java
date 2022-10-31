/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.BinarySearchSeeker;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * A seeker that supports seeking within PS stream using binary search.
 *
 * <p>This seeker uses the first and last SCR values within the stream, as well as the stream
 * duration to interpolate the SCR value of the seeking position. Then it performs binary search
 * within the stream to find a packets whose SCR value is with in {@link #SEEK_TOLERANCE_US} from
 * the target SCR.
 */
/* package */ final class PsBinarySearchSeeker extends BinarySearchSeeker {

  private static final long SEEK_TOLERANCE_US = 100_000;
  private static final int MINIMUM_SEARCH_RANGE_BYTES = 1000;
  private static final int TIMESTAMP_SEARCH_BYTES = 20000;

  public PsBinarySearchSeeker(
      TimestampAdjuster scrTimestampAdjuster, long streamDurationUs, long inputLength) {
    super(
        new DefaultSeekTimestampConverter(),
        new PsScrSeeker(scrTimestampAdjuster),
        streamDurationUs,
        /* floorTimePosition= */ 0,
        /* ceilingTimePosition= */ streamDurationUs + 1,
        /* floorBytePosition= */ 0,
        /* ceilingBytePosition= */ inputLength,
        /* approxBytesPerFrame= */ TsExtractor.TS_PACKET_SIZE,
        MINIMUM_SEARCH_RANGE_BYTES);
  }

  /**
   * A seeker that looks for a given SCR timestamp at a given position in a PS stream.
   *
   * <p>Given a SCR timestamp, and a position within a PS stream, this seeker will peek up to {@link
   * #TIMESTAMP_SEARCH_BYTES} bytes from that stream position, look for all packs in that range, and
   * then compare the SCR timestamps (if available) of these packets to the target timestamp.
   */
  private static final class PsScrSeeker implements TimestampSeeker {

    private final TimestampAdjuster scrTimestampAdjuster;
    private final ParsableByteArray packetBuffer;

    private PsScrSeeker(TimestampAdjuster scrTimestampAdjuster) {
      this.scrTimestampAdjuster = scrTimestampAdjuster;
      packetBuffer = new ParsableByteArray();
    }

    @Override
    public TimestampSearchResult searchForTimestamp(
        ExtractorInput input, long targetTimestamp, OutputFrameHolder outputFrameHolder)
        throws IOException, InterruptedException {
      long inputPosition = input.getPosition();
      int bytesToSearch = (int) Math.min(TIMESTAMP_SEARCH_BYTES, input.getLength() - inputPosition);

      packetBuffer.reset(bytesToSearch);
      input.peekFully(packetBuffer.data, /* offset= */ 0, bytesToSearch);

      return searchForScrValueInBuffer(packetBuffer, targetTimestamp, inputPosition);
    }

    @Override
    public void onSeekFinished() {
      packetBuffer.reset(Util.EMPTY_BYTE_ARRAY);
    }

    private TimestampSearchResult searchForScrValueInBuffer(
        ParsableByteArray packetBuffer, long targetScrTimeUs, long bufferStartOffset) {
      int startOfLastPacketPosition = C.POSITION_UNSET;
      int endOfLastPacketPosition = C.POSITION_UNSET;
      long lastScrTimeUsInRange = C.TIME_UNSET;

      while (packetBuffer.bytesLeft() >= 4) {
        int nextStartCode = peekIntAtPosition(packetBuffer.data, packetBuffer.getPosition());
        if (nextStartCode != PsExtractor.PACK_START_CODE) {
          packetBuffer.skipBytes(1);
          continue;
        } else {
          packetBuffer.skipBytes(4);
        }

        // We found a pack.
        long scrValue = PsDurationReader.readScrValueFromPack(packetBuffer);
        if (scrValue != C.TIME_UNSET) {
          long scrTimeUs = scrTimestampAdjuster.adjustTsTimestamp(scrValue);
          if (scrTimeUs > targetScrTimeUs) {
            if (lastScrTimeUsInRange == C.TIME_UNSET) {
              // First SCR timestamp is already over target.
              return TimestampSearchResult.overestimatedResult(scrTimeUs, bufferStartOffset);
            } else {
              // Last SCR timestamp < target timestamp < this timestamp.
              return TimestampSearchResult.targetFoundResult(
                  bufferStartOffset + startOfLastPacketPosition);
            }
          } else if (scrTimeUs + SEEK_TOLERANCE_US > targetScrTimeUs) {
            long startOfPacketInStream = bufferStartOffset + packetBuffer.getPosition();
            return TimestampSearchResult.targetFoundResult(startOfPacketInStream);
          }

          lastScrTimeUsInRange = scrTimeUs;
          startOfLastPacketPosition = packetBuffer.getPosition();
        }
        skipToEndOfCurrentPack(packetBuffer);
        endOfLastPacketPosition = packetBuffer.getPosition();
      }

      if (lastScrTimeUsInRange != C.TIME_UNSET) {
        long endOfLastPacketPositionInStream = bufferStartOffset + endOfLastPacketPosition;
        return TimestampSearchResult.underestimatedResult(
            lastScrTimeUsInRange, endOfLastPacketPositionInStream);
      } else {
        return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT;
      }
    }

    /**
     * Skips the buffer position to the position after the end of the current PS pack in the buffer,
     * given the byte position right after the {@link PsExtractor#PACK_START_CODE} of the pack in
     * the buffer. If the pack ends after the end of the buffer, skips to the end of the buffer.
     */
    private static void skipToEndOfCurrentPack(ParsableByteArray packetBuffer) {
      int limit = packetBuffer.limit();

      if (packetBuffer.bytesLeft() < 10) {
        // We require at least 9 bytes for pack header to read SCR value + 1 byte for pack_stuffing
        // length.
        packetBuffer.setPosition(limit);
        return;
      }
      packetBuffer.skipBytes(9);

      int packStuffingLength = packetBuffer.readUnsignedByte() & 0x07;
      if (packetBuffer.bytesLeft() < packStuffingLength) {
        packetBuffer.setPosition(limit);
        return;
      }
      packetBuffer.skipBytes(packStuffingLength);

      if (packetBuffer.bytesLeft() < 4) {
        packetBuffer.setPosition(limit);
        return;
      }

      int nextStartCode = peekIntAtPosition(packetBuffer.data, packetBuffer.getPosition());
      if (nextStartCode == PsExtractor.SYSTEM_HEADER_START_CODE) {
        packetBuffer.skipBytes(4);
        int systemHeaderLength = packetBuffer.readUnsignedShort();
        if (packetBuffer.bytesLeft() < systemHeaderLength) {
          packetBuffer.setPosition(limit);
          return;
        }
        packetBuffer.skipBytes(systemHeaderLength);
      }

      // Find the position of the next PACK_START_CODE or MPEG_PROGRAM_END_CODE, which is right
      // after the end position of this pack.
      // If we couldn't find these codes within the buffer, return the buffer limit, or return
      // the first position which PES packets pattern does not match (some malformed packets).
      while (packetBuffer.bytesLeft() >= 4) {
        nextStartCode = peekIntAtPosition(packetBuffer.data, packetBuffer.getPosition());
        if (nextStartCode == PsExtractor.PACK_START_CODE
            || nextStartCode == PsExtractor.MPEG_PROGRAM_END_CODE) {
          break;
        }
        if (nextStartCode >>> 8 != PsExtractor.PACKET_START_CODE_PREFIX) {
          break;
        }
        packetBuffer.skipBytes(4);

        if (packetBuffer.bytesLeft() < 2) {
          // 2 bytes for PES_packet length.
          packetBuffer.setPosition(limit);
          return;
        }
        int pesPacketLength = packetBuffer.readUnsignedShort();
        packetBuffer.setPosition(
            Math.min(packetBuffer.limit(), packetBuffer.getPosition() + pesPacketLength));
      }
    }
  }

  private static int peekIntAtPosition(byte[] data, int position) {
    return (data[position] & 0xFF) << 24
        | (data[position + 1] & 0xFF) << 16
        | (data[position + 2] & 0xFF) << 8
        | (data[position + 3] & 0xFF);
  }
}
