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
package com.google.android.exoplayer2.video;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Collections;
import java.util.List;

/**
 * HEVC configuration data.
 */
public final class HevcConfig {

  public final @Nullable List<byte[]> initializationData;
  public final int nalUnitLengthFieldLength;

  /**
   * Parses HEVC configuration data.
   *
   * @param data A {@link ParsableByteArray}, whose position is set to the start of the HEVC
   *     configuration data to parse.
   * @return A parsed representation of the HEVC configuration data.
   * @throws ParserException If an error occurred parsing the data.
   */
  public static HevcConfig parse(ParsableByteArray data) throws ParserException {
    try {
      data.skipBytes(21); // Skip to the NAL unit length size field.
      int lengthSizeMinusOne = data.readUnsignedByte() & 0x03;

      // Calculate the combined size of all VPS/SPS/PPS bitstreams.
      int numberOfArrays = data.readUnsignedByte();
      int csdLength = 0;
      int csdStartPosition = data.getPosition();
      for (int i = 0; i < numberOfArrays; i++) {
        data.skipBytes(1); // completeness (1), nal_unit_type (7)
        int numberOfNalUnits = data.readUnsignedShort();
        for (int j = 0; j < numberOfNalUnits; j++) {
          int nalUnitLength = data.readUnsignedShort();
          csdLength += 4 + nalUnitLength; // Start code and NAL unit.
          data.skipBytes(nalUnitLength);
        }
      }

      // Concatenate the codec-specific data into a single buffer.
      data.setPosition(csdStartPosition);
      byte[] buffer = new byte[csdLength];
      int bufferPosition = 0;
      for (int i = 0; i < numberOfArrays; i++) {
        data.skipBytes(1); // completeness (1), nal_unit_type (7)
        int numberOfNalUnits = data.readUnsignedShort();
        for (int j = 0; j < numberOfNalUnits; j++) {
          int nalUnitLength = data.readUnsignedShort();
          System.arraycopy(NalUnitUtil.NAL_START_CODE, 0, buffer, bufferPosition,
              NalUnitUtil.NAL_START_CODE.length);
          bufferPosition += NalUnitUtil.NAL_START_CODE.length;
          System
              .arraycopy(data.data, data.getPosition(), buffer, bufferPosition, nalUnitLength);
          bufferPosition += nalUnitLength;
          data.skipBytes(nalUnitLength);
        }
      }

      List<byte[]> initializationData = csdLength == 0 ? null : Collections.singletonList(buffer);
      return new HevcConfig(initializationData, lengthSizeMinusOne + 1);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new ParserException("Error parsing HEVC config", e);
    }
  }

  private HevcConfig(@Nullable List<byte[]> initializationData, int nalUnitLengthFieldLength) {
    this.initializationData = initializationData;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
  }

}
