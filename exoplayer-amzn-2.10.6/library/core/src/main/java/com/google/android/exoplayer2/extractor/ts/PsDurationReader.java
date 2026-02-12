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
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * A reader that can extract the approximate duration from a given MPEG program stream (PS).
 *
 * <p>This reader extracts the duration by reading system clock reference (SCR) values from the
 * header of a pack at the start and at the end of the stream, calculating the difference, and
 * converting that into stream duration. This reader also handles the case when a single SCR
 * wraparound takes place within the stream, which can make SCR values at the beginning of the
 * stream larger than SCR values at the end. This class can only be used once to read duration from
 * a given stream, and the usage of the class is not thread-safe, so all calls should be made from
 * the same thread.
 *
 * <p>Note: See ISO/IEC 13818-1, Table 2-33 for details of the SCR field in pack_header.
 */
/* package */ final class PsDurationReader {

  private static final int TIMESTAMP_SEARCH_BYTES = 20000;

  private final TimestampAdjuster scrTimestampAdjuster;
  private final ParsableByteArray packetBuffer;

  private boolean isDurationRead;
  private boolean isFirstScrValueRead;
  private boolean isLastScrValueRead;

  private long firstScrValue;
  private long lastScrValue;
  private long durationUs;

  /* package */ PsDurationReader() {
    scrTimestampAdjuster = new TimestampAdjuster(/* firstSampleTimestampUs= */ 0);
    firstScrValue = C.TIME_UNSET;
    lastScrValue = C.TIME_UNSET;
    durationUs = C.TIME_UNSET;
    packetBuffer = new ParsableByteArray();
  }

  /** Returns true if a PS duration has been read. */
  public boolean isDurationReadFinished() {
    return isDurationRead;
  }

  public TimestampAdjuster getScrTimestampAdjuster() {
    return scrTimestampAdjuster;
  }

  /**
   * Reads a PS duration from the input.
   *
   * <p>This reader reads the duration by reading SCR values from the header of a pack at the start
   * and at the end of the stream, calculating the difference, and converting that into stream
   * duration.
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @param seekPositionHolder If {@link Extractor#RESULT_SEEK} is returned, this holder is updated
   *     to hold the position of the required seek.
   * @return One of the {@code RESULT_} values defined in {@link Extractor}.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  public @Extractor.ReadResult int readDuration(
      ExtractorInput input, PositionHolder seekPositionHolder)
      throws IOException, InterruptedException {
    if (!isLastScrValueRead) {
      return readLastScrValue(input, seekPositionHolder);
    }
    if (lastScrValue == C.TIME_UNSET) {
      return finishReadDuration(input);
    }
    if (!isFirstScrValueRead) {
      return readFirstScrValue(input, seekPositionHolder);
    }
    if (firstScrValue == C.TIME_UNSET) {
      return finishReadDuration(input);
    }

    long minScrPositionUs = scrTimestampAdjuster.adjustTsTimestamp(firstScrValue);
    long maxScrPositionUs = scrTimestampAdjuster.adjustTsTimestamp(lastScrValue);
    durationUs = maxScrPositionUs - minScrPositionUs;
    return finishReadDuration(input);
  }

  /** Returns the duration last read from {@link #readDuration(ExtractorInput, PositionHolder)}. */
  public long getDurationUs() {
    return durationUs;
  }

  /**
   * Returns the SCR value read from the next pack in the stream, given the buffer at the pack
   * header start position (just behind the pack start code).
   */
  public static long readScrValueFromPack(ParsableByteArray packetBuffer) {
    int originalPosition = packetBuffer.getPosition();
    if (packetBuffer.bytesLeft() < 9) {
      // We require at 9 bytes for pack header to read scr value
      return C.TIME_UNSET;
    }
    byte[] scrBytes = new byte[9];
    packetBuffer.readBytes(scrBytes, /* offset= */ 0, scrBytes.length);
    packetBuffer.setPosition(originalPosition);
    if (!checkMarkerBits(scrBytes)) {
      return C.TIME_UNSET;
    }
    return readScrValueFromPackHeader(scrBytes);
  }

  private int finishReadDuration(ExtractorInput input) {
    packetBuffer.reset(Util.EMPTY_BYTE_ARRAY);
    isDurationRead = true;
    input.resetPeekPosition();
    return Extractor.RESULT_CONTINUE;
  }

  private int readFirstScrValue(ExtractorInput input, PositionHolder seekPositionHolder)
      throws IOException, InterruptedException {
    int bytesToSearch = (int) Math.min(TIMESTAMP_SEARCH_BYTES, input.getLength());
    int searchStartPosition = 0;
    if (input.getPosition() != searchStartPosition) {
      seekPositionHolder.position = searchStartPosition;
      return Extractor.RESULT_SEEK;
    }

    packetBuffer.reset(bytesToSearch);
    input.resetPeekPosition();
    input.peekFully(packetBuffer.data, /* offset= */ 0, bytesToSearch);

    firstScrValue = readFirstScrValueFromBuffer(packetBuffer);
    isFirstScrValueRead = true;
    return Extractor.RESULT_CONTINUE;
  }

  private long readFirstScrValueFromBuffer(ParsableByteArray packetBuffer) {
    int searchStartPosition = packetBuffer.getPosition();
    int searchEndPosition = packetBuffer.limit();
    for (int searchPosition = searchStartPosition;
        searchPosition < searchEndPosition - 3;
        searchPosition++) {
      int nextStartCode = peekIntAtPosition(packetBuffer.data, searchPosition);
      if (nextStartCode == PsExtractor.PACK_START_CODE) {
        packetBuffer.setPosition(searchPosition + 4);
        long scrValue = readScrValueFromPack(packetBuffer);
        if (scrValue != C.TIME_UNSET) {
          return scrValue;
        }
      }
    }
    return C.TIME_UNSET;
  }

  private int readLastScrValue(ExtractorInput input, PositionHolder seekPositionHolder)
      throws IOException, InterruptedException {
    long inputLength = input.getLength();
    int bytesToSearch = (int) Math.min(TIMESTAMP_SEARCH_BYTES, inputLength);
    long searchStartPosition = inputLength - bytesToSearch;
    if (input.getPosition() != searchStartPosition) {
      seekPositionHolder.position = searchStartPosition;
      return Extractor.RESULT_SEEK;
    }

    packetBuffer.reset(bytesToSearch);
    input.resetPeekPosition();
    input.peekFully(packetBuffer.data, /* offset= */ 0, bytesToSearch);

    lastScrValue = readLastScrValueFromBuffer(packetBuffer);
    isLastScrValueRead = true;
    return Extractor.RESULT_CONTINUE;
  }

  private long readLastScrValueFromBuffer(ParsableByteArray packetBuffer) {
    int searchStartPosition = packetBuffer.getPosition();
    int searchEndPosition = packetBuffer.limit();
    for (int searchPosition = searchEndPosition - 4;
        searchPosition >= searchStartPosition;
        searchPosition--) {
      int nextStartCode = peekIntAtPosition(packetBuffer.data, searchPosition);
      if (nextStartCode == PsExtractor.PACK_START_CODE) {
        packetBuffer.setPosition(searchPosition + 4);
        long scrValue = readScrValueFromPack(packetBuffer);
        if (scrValue != C.TIME_UNSET) {
          return scrValue;
        }
      }
    }
    return C.TIME_UNSET;
  }

  private int peekIntAtPosition(byte[] data, int position) {
    return (data[position] & 0xFF) << 24
        | (data[position + 1] & 0xFF) << 16
        | (data[position + 2] & 0xFF) << 8
        | (data[position + 3] & 0xFF);
  }

  private static boolean checkMarkerBits(byte[] scrBytes) {
    // Verify the 01xxx1xx marker on the 0th byte
    if ((scrBytes[0] & 0xC4) != 0x44) {
      return false;
    }
    // 1st byte belongs to scr field.
    // Verify the xxxxx1xx marker on the 2nd byte
    if ((scrBytes[2] & 0x04) != 0x04) {
      return false;
    }
    // 3rd byte belongs to scr field.
    // Verify the xxxxx1xx marker on the 4rd byte
    if ((scrBytes[4] & 0x04) != 0x04) {
      return false;
    }
    // Verify the xxxxxxx1 marker on the 5th byte
    if ((scrBytes[5] & 0x01) != 0x01) {
      return false;
    }
    // 6th and 7th bytes belongs to program_max_rate field.
    // Verify the xxxxxx11 marker on the 8th byte
    return (scrBytes[8] & 0x03) == 0x03;
  }

  /**
   * Returns the value of SCR base - 33 bits in big endian order from the PS pack header, ignoring
   * the marker bits. Note: See ISO/IEC 13818-1, Table 2-33 for details of the SCR field in
   * pack_header.
   *
   * <p>We ignore SCR Ext, because it's too small to have any significance.
   */
  private static long readScrValueFromPackHeader(byte[] scrBytes) {
    return ((scrBytes[0] & 0b00111000L) >> 3) << 30
        | (scrBytes[0] & 0b00000011L) << 28
        | (scrBytes[1] & 0xFFL) << 20
        | ((scrBytes[2] & 0b11111000L) >> 3) << 15
        | (scrBytes[2] & 0b00000011L) << 13
        | (scrBytes[3] & 0xFFL) << 5
        | (scrBytes[4] & 0b11111000L) >> 3;
  }
}
