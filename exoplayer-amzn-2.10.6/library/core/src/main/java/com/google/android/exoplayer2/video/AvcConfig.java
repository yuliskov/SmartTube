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

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.NalUnitUtil.SpsData;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.List;

/**
 * AVC configuration data.
 */
public final class AvcConfig {

  public final List<byte[]> initializationData;
  public final int nalUnitLengthFieldLength;
  public final int width;
  public final int height;
  public final float pixelWidthAspectRatio;

  /**
   * Parses AVC configuration data.
   *
   * @param data A {@link ParsableByteArray}, whose position is set to the start of the AVC
   *     configuration data to parse.
   * @return A parsed representation of the HEVC configuration data.
   * @throws ParserException If an error occurred parsing the data.
   */
  public static AvcConfig parse(ParsableByteArray data) throws ParserException {
    try {
      data.skipBytes(4); // Skip to the AVCDecoderConfigurationRecord (defined in 14496-15)
      int nalUnitLengthFieldLength = (data.readUnsignedByte() & 0x3) + 1;
      if (nalUnitLengthFieldLength == 3) {
        throw new IllegalStateException();
      }
      List<byte[]> initializationData = new ArrayList<>();
      int numSequenceParameterSets = data.readUnsignedByte() & 0x1F;
      for (int j = 0; j < numSequenceParameterSets; j++) {
        initializationData.add(buildNalUnitForChild(data));
      }
      int numPictureParameterSets = data.readUnsignedByte();
      for (int j = 0; j < numPictureParameterSets; j++) {
        initializationData.add(buildNalUnitForChild(data));
      }

      int width = Format.NO_VALUE;
      int height = Format.NO_VALUE;
      float pixelWidthAspectRatio = 1;
      if (numSequenceParameterSets > 0) {
        byte[] sps = initializationData.get(0);
        SpsData spsData = NalUnitUtil.parseSpsNalUnit(initializationData.get(0),
            nalUnitLengthFieldLength, sps.length);
        width = spsData.width;
        height = spsData.height;
        pixelWidthAspectRatio = spsData.pixelWidthAspectRatio;
      }
      return new AvcConfig(initializationData, nalUnitLengthFieldLength, width, height,
          pixelWidthAspectRatio);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new ParserException("Error parsing AVC config", e);
    }
  }

  private AvcConfig(List<byte[]> initializationData, int nalUnitLengthFieldLength,
      int width, int height, float pixelWidthAspectRatio) {
    this.initializationData = initializationData;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    this.width = width;
    this.height = height;
    this.pixelWidthAspectRatio = pixelWidthAspectRatio;
  }

  private static byte[] buildNalUnitForChild(ParsableByteArray data) {
    int length = data.readUnsignedShort();
    int offset = data.getPosition();
    data.skipBytes(length);
    return CodecSpecificDataUtil.buildNalUnit(data.data, offset, length);
  }

}
