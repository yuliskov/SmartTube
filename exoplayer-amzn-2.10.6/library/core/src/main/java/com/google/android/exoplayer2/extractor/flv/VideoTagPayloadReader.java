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
package com.google.android.exoplayer2.extractor.flv;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.video.AvcConfig;

/**
 * Parses video tags from an FLV stream and extracts H.264 nal units.
 */
/* package */ final class VideoTagPayloadReader extends TagPayloadReader {

  // Video codec.
  private static final int VIDEO_CODEC_AVC = 7;

  // Frame types.
  private static final int VIDEO_FRAME_KEYFRAME = 1;
  private static final int VIDEO_FRAME_VIDEO_INFO = 5;

  // Packet types.
  private static final int AVC_PACKET_TYPE_SEQUENCE_HEADER = 0;
  private static final int AVC_PACKET_TYPE_AVC_NALU = 1;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalLength;
  private int nalUnitLengthFieldLength;

  // State variables.
  private boolean hasOutputFormat;
  private boolean hasOutputKeyframe;
  private int frameType;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  public VideoTagPayloadReader(TrackOutput output) {
    super(output);
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalLength = new ParsableByteArray(4);
  }

  @Override
  public void seek() {
    hasOutputKeyframe = false;
  }

  @Override
  protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
    int header = data.readUnsignedByte();
    int frameType = (header >> 4) & 0x0F;
    int videoCodec = (header & 0x0F);
    // Support just H.264 encoded content.
    if (videoCodec != VIDEO_CODEC_AVC) {
      throw new UnsupportedFormatException("Video format not supported: " + videoCodec);
    }
    this.frameType = frameType;
    return (frameType != VIDEO_FRAME_VIDEO_INFO);
  }

  @Override
  protected boolean parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
    int packetType = data.readUnsignedByte();
    int compositionTimeMs = data.readInt24();

    timeUs += compositionTimeMs * 1000L;
    // Parse avc sequence header in case this was not done before.
    if (packetType == AVC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
      ParsableByteArray videoSequence = new ParsableByteArray(new byte[data.bytesLeft()]);
      data.readBytes(videoSequence.data, 0, data.bytesLeft());
      AvcConfig avcConfig = AvcConfig.parse(videoSequence);
      nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
      // Construct and output the format.
      Format format = Format.createVideoSampleFormat(null, MimeTypes.VIDEO_H264, null,
          Format.NO_VALUE, Format.NO_VALUE, avcConfig.width, avcConfig.height, Format.NO_VALUE,
          avcConfig.initializationData, Format.NO_VALUE, avcConfig.pixelWidthAspectRatio, null);
      output.format(format);
      hasOutputFormat = true;
      return false;
    } else if (packetType == AVC_PACKET_TYPE_AVC_NALU && hasOutputFormat) {
      boolean isKeyframe = frameType == VIDEO_FRAME_KEYFRAME;
      if (!hasOutputKeyframe && !isKeyframe) {
        return false;
      }
      // TODO: Deduplicate with Mp4Extractor.
      // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalLengthData = nalLength.data;
      nalLengthData[0] = 0;
      nalLengthData[1] = 0;
      nalLengthData[2] = 0;
      int nalUnitLengthFieldLengthDiff = 4 - nalUnitLengthFieldLength;
      // NAL units are length delimited, but the decoder requires start code delimited units.
      // Loop until we've written the sample to the track output, replacing length delimiters with
      // start codes as we encounter them.
      int bytesWritten = 0;
      int bytesToWrite;
      while (data.bytesLeft() > 0) {
        // Read the NAL length so that we know where we find the next one.
        data.readBytes(nalLength.data, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
        nalLength.setPosition(0);
        bytesToWrite = nalLength.readUnsignedIntToInt();

        // Write a start code for the current NAL unit.
        nalStartCode.setPosition(0);
        output.sampleData(nalStartCode, 4);
        bytesWritten += 4;

        // Write the payload of the NAL unit.
        output.sampleData(data, bytesToWrite);
        bytesWritten += bytesToWrite;
      }
      output.sampleMetadata(
          timeUs, isKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0, bytesWritten, 0, null);
      hasOutputKeyframe = true;
      return true;
    } else {
      return false;
    }
  }

}
