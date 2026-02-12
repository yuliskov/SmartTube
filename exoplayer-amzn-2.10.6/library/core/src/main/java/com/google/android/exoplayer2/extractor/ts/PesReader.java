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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;

/**
 * Parses PES packet data and extracts samples.
 */
public final class PesReader implements TsPayloadReader {

  private static final String TAG = "PesReader";

  private static final int STATE_FINDING_HEADER = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_HEADER_EXTENSION = 2;
  private static final int STATE_READING_BODY = 3;

  private static final int HEADER_SIZE = 9;
  private static final int MAX_HEADER_EXTENSION_SIZE = 10;
  private static final int PES_SCRATCH_SIZE = 10; // max(HEADER_SIZE, MAX_HEADER_EXTENSION_SIZE)

  private final ElementaryStreamReader reader;
  private final ParsableBitArray pesScratch;

  private int state;
  private int bytesRead;

  private TimestampAdjuster timestampAdjuster;
  private boolean ptsFlag;
  private boolean dtsFlag;
  private boolean seenFirstDts;
  private int extendedHeaderLength;
  private int payloadSize;
  private boolean dataAlignmentIndicator;
  private long timeUs;

  public PesReader(ElementaryStreamReader reader) {
    this.reader = reader;
    pesScratch = new ParsableBitArray(new byte[PES_SCRATCH_SIZE]);
    state = STATE_FINDING_HEADER;
  }

  @Override
  public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
      TrackIdGenerator idGenerator) {
    this.timestampAdjuster = timestampAdjuster;
    reader.createTracks(extractorOutput, idGenerator);
  }

  // TsPayloadReader implementation.

  @Override
  public final void seek() {
    state = STATE_FINDING_HEADER;
    bytesRead = 0;
    seenFirstDts = false;
    reader.seek();
  }

  @Override
  public final void consume(ParsableByteArray data, @Flags int flags) throws ParserException {
    if ((flags & FLAG_PAYLOAD_UNIT_START_INDICATOR) != 0) {
      switch (state) {
        case STATE_FINDING_HEADER:
        case STATE_READING_HEADER:
          // Expected.
          break;
        case STATE_READING_HEADER_EXTENSION:
          Log.w(TAG, "Unexpected start indicator reading extended header");
          break;
        case STATE_READING_BODY:
          // If payloadSize == -1 then the length of the previous packet was unspecified, and so
          // we only know that it's finished now that we've seen the start of the next one. This
          // is expected. If payloadSize != -1, then the length of the previous packet was known,
          // but we didn't receive that amount of data. This is not expected.
          if (payloadSize != -1) {
            Log.w(TAG, "Unexpected start indicator: expected " + payloadSize + " more bytes");
          }
          // Either way, notify the reader that it has now finished.
          reader.packetFinished();
          break;
        default:
          throw new IllegalStateException();
      }
      setState(STATE_READING_HEADER);
    }

    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_HEADER:
          data.skipBytes(data.bytesLeft());
          break;
        case STATE_READING_HEADER:
          if (continueRead(data, pesScratch.data, HEADER_SIZE)) {
            setState(parseHeader() ? STATE_READING_HEADER_EXTENSION : STATE_FINDING_HEADER);
          }
          break;
        case STATE_READING_HEADER_EXTENSION:
          int readLength = Math.min(MAX_HEADER_EXTENSION_SIZE, extendedHeaderLength);
          // Read as much of the extended header as we're interested in, and skip the rest.
          if (continueRead(data, pesScratch.data, readLength)
              && continueRead(data, null, extendedHeaderLength)) {
            parseHeaderExtension();
            flags |= dataAlignmentIndicator ? FLAG_DATA_ALIGNMENT_INDICATOR : 0;
            reader.packetStarted(timeUs, flags);
            setState(STATE_READING_BODY);
          }
          break;
        case STATE_READING_BODY:
          readLength = data.bytesLeft();
          int padding = payloadSize == -1 ? 0 : readLength - payloadSize;
          if (padding > 0) {
            readLength -= padding;
            data.setLimit(data.getPosition() + readLength);
          }
          reader.consume(data);
          if (payloadSize != -1) {
            payloadSize -= readLength;
            if (payloadSize == 0) {
              reader.packetFinished();
              setState(STATE_READING_HEADER);
            }
          }
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  private void setState(int state) {
    this.state = state;
    bytesRead = 0;
  }

  /**
   * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
   * that the data should be written into {@code target} starting from an offset of zero.
   *
   * @param source The source from which to read.
   * @param target The target into which data is to be read, or {@code null} to skip.
   * @param targetLength The target length of the read.
   * @return Whether the target length has been reached.
   */
  private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
    int bytesToRead = Math.min(source.bytesLeft(), targetLength - bytesRead);
    if (bytesToRead <= 0) {
      return true;
    } else if (target == null) {
      source.skipBytes(bytesToRead);
    } else {
      source.readBytes(target, bytesRead, bytesToRead);
    }
    bytesRead += bytesToRead;
    return bytesRead == targetLength;
  }

  private boolean parseHeader() {
    // Note: see ISO/IEC 13818-1, section 2.4.3.6 for detailed information on the format of
    // the header.
    pesScratch.setPosition(0);
    int startCodePrefix = pesScratch.readBits(24);
    if (startCodePrefix != 0x000001) {
      Log.w(TAG, "Unexpected start code prefix: " + startCodePrefix);
      payloadSize = -1;
      return false;
    }

    pesScratch.skipBits(8); // stream_id.
    int packetLength = pesScratch.readBits(16);
    pesScratch.skipBits(5); // '10' (2), PES_scrambling_control (2), PES_priority (1)
    dataAlignmentIndicator = pesScratch.readBit();
    pesScratch.skipBits(2); // copyright (1), original_or_copy (1)
    ptsFlag = pesScratch.readBit();
    dtsFlag = pesScratch.readBit();
    // ESCR_flag (1), ES_rate_flag (1), DSM_trick_mode_flag (1),
    // additional_copy_info_flag (1), PES_CRC_flag (1), PES_extension_flag (1)
    pesScratch.skipBits(6);
    extendedHeaderLength = pesScratch.readBits(8);

    if (packetLength == 0) {
      payloadSize = -1;
    } else {
      payloadSize = packetLength + 6 /* packetLength does not include the first 6 bytes */
          - HEADER_SIZE - extendedHeaderLength;
    }
    return true;
  }

  private void parseHeaderExtension() {
    pesScratch.setPosition(0);
    timeUs = C.TIME_UNSET;
    if (ptsFlag) {
      pesScratch.skipBits(4); // '0010' or '0011'
      long pts = (long) pesScratch.readBits(3) << 30;
      pesScratch.skipBits(1); // marker_bit
      pts |= pesScratch.readBits(15) << 15;
      pesScratch.skipBits(1); // marker_bit
      pts |= pesScratch.readBits(15);
      pesScratch.skipBits(1); // marker_bit
      if (!seenFirstDts && dtsFlag) {
        pesScratch.skipBits(4); // '0011'
        long dts = (long) pesScratch.readBits(3) << 30;
        pesScratch.skipBits(1); // marker_bit
        dts |= pesScratch.readBits(15) << 15;
        pesScratch.skipBits(1); // marker_bit
        dts |= pesScratch.readBits(15);
        pesScratch.skipBits(1); // marker_bit
        // Subsequent PES packets may have earlier presentation timestamps than this one, but they
        // should all be greater than or equal to this packet's decode timestamp. We feed the
        // decode timestamp to the adjuster here so that in the case that this is the first to be
        // fed, the adjuster will be able to compute an offset to apply such that the adjusted
        // presentation timestamps of all future packets are non-negative.
        timestampAdjuster.adjustTsTimestamp(dts);
        seenFirstDts = true;
      }
      timeUs = timestampAdjuster.adjustTsTimestamp(pts);
    }
  }

}
