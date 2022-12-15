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
 * A seeker that supports seeking within TS stream using binary search.
 *
 * <p>This seeker uses the first and last PCR values within the stream, as well as the stream
 * duration to interpolate the PCR value of the seeking position. Then it performs binary search
 * within the stream to find a packets whose PCR value is within {@link #SEEK_TOLERANCE_US} from the
 * target PCR.
 */
/* package */ final class TsBinarySearchSeeker extends BinarySearchSeeker {

  private static final long SEEK_TOLERANCE_US = 100_000;
  private static final int MINIMUM_SEARCH_RANGE_BYTES = 5 * TsExtractor.TS_PACKET_SIZE;
  private static final int TIMESTAMP_SEARCH_BYTES = 600 * TsExtractor.TS_PACKET_SIZE;

  public TsBinarySearchSeeker(
      TimestampAdjuster pcrTimestampAdjuster, long streamDurationUs, long inputLength, int pcrPid) {
    super(
        new DefaultSeekTimestampConverter(),
        new TsPcrSeeker(pcrPid, pcrTimestampAdjuster),
        streamDurationUs,
        /* floorTimePosition= */ 0,
        /* ceilingTimePosition= */ streamDurationUs + 1,
        /* floorBytePosition= */ 0,
        /* ceilingBytePosition= */ inputLength,
        /* approxBytesPerFrame= */ TsExtractor.TS_PACKET_SIZE,
        MINIMUM_SEARCH_RANGE_BYTES);
  }

  /**
   * A {@link TimestampSeeker} implementation that looks for a given PCR timestamp at a given
   * position in a TS stream.
   *
   * <p>Given a PCR timestamp, and a position within a TS stream, this seeker will peek up to {@link
   * #TIMESTAMP_SEARCH_BYTES} from that stream position, look for all packets with PID equal to
   * PCR_PID, and then compare the PCR timestamps (if available) of these packets to the target
   * timestamp.
   */
  private static final class TsPcrSeeker implements TimestampSeeker {

    private final TimestampAdjuster pcrTimestampAdjuster;
    private final ParsableByteArray packetBuffer;
    private final int pcrPid;

    public TsPcrSeeker(int pcrPid, TimestampAdjuster pcrTimestampAdjuster) {
      this.pcrPid = pcrPid;
      this.pcrTimestampAdjuster = pcrTimestampAdjuster;
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

      return searchForPcrValueInBuffer(packetBuffer, targetTimestamp, inputPosition);
    }

    private TimestampSearchResult searchForPcrValueInBuffer(
        ParsableByteArray packetBuffer, long targetPcrTimeUs, long bufferStartOffset) {
      int limit = packetBuffer.limit();

      long startOfLastPacketPosition = C.POSITION_UNSET;
      long endOfLastPacketPosition = C.POSITION_UNSET;
      long lastPcrTimeUsInRange = C.TIME_UNSET;

      while (packetBuffer.bytesLeft() >= TsExtractor.TS_PACKET_SIZE) {
        int startOfPacket =
            TsUtil.findSyncBytePosition(packetBuffer.data, packetBuffer.getPosition(), limit);
        int endOfPacket = startOfPacket + TsExtractor.TS_PACKET_SIZE;
        if (endOfPacket > limit) {
          break;
        }
        long pcrValue = TsUtil.readPcrFromPacket(packetBuffer, startOfPacket, pcrPid);
        if (pcrValue != C.TIME_UNSET) {
          long pcrTimeUs = pcrTimestampAdjuster.adjustTsTimestamp(pcrValue);
          if (pcrTimeUs > targetPcrTimeUs) {
            if (lastPcrTimeUsInRange == C.TIME_UNSET) {
              // First PCR timestamp is already over target.
              return TimestampSearchResult.overestimatedResult(pcrTimeUs, bufferStartOffset);
            } else {
              // Last PCR timestamp < target timestamp < this timestamp.
              return TimestampSearchResult.targetFoundResult(
                  bufferStartOffset + startOfLastPacketPosition);
            }
          } else if (pcrTimeUs + SEEK_TOLERANCE_US > targetPcrTimeUs) {
            long startOfPacketInStream = bufferStartOffset + startOfPacket;
            return TimestampSearchResult.targetFoundResult(startOfPacketInStream);
          }

          lastPcrTimeUsInRange = pcrTimeUs;
          startOfLastPacketPosition = startOfPacket;
        }
        packetBuffer.setPosition(endOfPacket);
        endOfLastPacketPosition = endOfPacket;
      }

      if (lastPcrTimeUsInRange != C.TIME_UNSET) {
        long endOfLastPacketPositionInStream = bufferStartOffset + endOfLastPacketPosition;
        return TimestampSearchResult.underestimatedResult(
            lastPcrTimeUsInRange, endOfLastPacketPositionInStream);
      } else {
        return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT;
      }
    }

    @Override
    public void onSeekFinished() {
      packetBuffer.reset(Util.EMPTY_BYTE_ARRAY);
    }
  }
}
