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
import com.google.android.exoplayer2.util.ParsableByteArray;

/** Utilities method for extracting MPEG-TS streams. */
public final class TsUtil {
  /**
   * Returns the position of the first TS_SYNC_BYTE within the range [startPosition, limitPosition)
   * from the provided data array, or returns limitPosition if sync byte could not be found.
   */
  public static int findSyncBytePosition(byte[] data, int startPosition, int limitPosition) {
    int position = startPosition;
    while (position < limitPosition && data[position] != TsExtractor.TS_SYNC_BYTE) {
      position++;
    }
    return position;
  }

  /**
   * Returns the PCR value read from a given TS packet.
   *
   * @param packetBuffer The buffer that holds the packet.
   * @param startOfPacket The starting position of the packet in the buffer.
   * @param pcrPid The PID for valid packets that contain PCR values.
   * @return The PCR value read from the packet, if its PID is equal to {@code pcrPid} and it
   *     contains a valid PCR value. Returns {@link C#TIME_UNSET} otherwise.
   */
  public static long readPcrFromPacket(
      ParsableByteArray packetBuffer, int startOfPacket, int pcrPid) {
    packetBuffer.setPosition(startOfPacket);
    if (packetBuffer.bytesLeft() < 5) {
      // Header = 4 bytes, adaptationFieldLength = 1 byte.
      return C.TIME_UNSET;
    }
    // Note: See ISO/IEC 13818-1, section 2.4.3.2 for details of the header format.
    int tsPacketHeader = packetBuffer.readInt();
    if ((tsPacketHeader & 0x800000) != 0) {
      // transport_error_indicator != 0 means there are uncorrectable errors in this packet.
      return C.TIME_UNSET;
    }
    int pid = (tsPacketHeader & 0x1FFF00) >> 8;
    if (pid != pcrPid) {
      return C.TIME_UNSET;
    }
    boolean adaptationFieldExists = (tsPacketHeader & 0x20) != 0;
    if (!adaptationFieldExists) {
      return C.TIME_UNSET;
    }

    int adaptationFieldLength = packetBuffer.readUnsignedByte();
    if (adaptationFieldLength >= 7 && packetBuffer.bytesLeft() >= 7) {
      int flags = packetBuffer.readUnsignedByte();
      boolean pcrFlagSet = (flags & 0x10) == 0x10;
      if (pcrFlagSet) {
        byte[] pcrBytes = new byte[6];
        packetBuffer.readBytes(pcrBytes, /* offset= */ 0, pcrBytes.length);
        return readPcrValueFromPcrBytes(pcrBytes);
      }
    }
    return C.TIME_UNSET;
  }

  /**
   * Returns the value of PCR base - first 33 bits in big endian order from the PCR bytes.
   *
   * <p>We ignore PCR Ext, because it's too small to have any significance.
   */
  private static long readPcrValueFromPcrBytes(byte[] pcrBytes) {
    return (pcrBytes[0] & 0xFFL) << 25
        | (pcrBytes[1] & 0xFFL) << 17
        | (pcrBytes[2] & 0xFFL) << 9
        | (pcrBytes[3] & 0xFFL) << 1
        | (pcrBytes[4] & 0xFFL) >> 7;
  }

  private TsUtil() {
    // Prevent instantiation.
  }
}
