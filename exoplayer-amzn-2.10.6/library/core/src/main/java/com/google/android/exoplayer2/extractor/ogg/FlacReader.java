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

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link StreamReader} to extract Flac data out of Ogg byte stream.
 */
/* package */ final class FlacReader extends StreamReader {

  private static final byte AUDIO_PACKET_TYPE = (byte) 0xFF;
  private static final byte SEEKTABLE_PACKET_TYPE = 0x03;

  private static final int FRAME_HEADER_SAMPLE_NUMBER_OFFSET = 4;

  private FlacStreamMetadata streamMetadata;
  private FlacOggSeeker flacOggSeeker;

  public static boolean verifyBitstreamType(ParsableByteArray data) {
    return data.bytesLeft() >= 5 && data.readUnsignedByte() == 0x7F && // packet type
        data.readUnsignedInt() == 0x464C4143; // ASCII signature "FLAC"
  }

  @Override
  protected void reset(boolean headerData) {
    super.reset(headerData);
    if (headerData) {
      streamMetadata = null;
      flacOggSeeker = null;
    }
  }

  private static boolean isAudioPacket(byte[] data) {
    return data[0] == AUDIO_PACKET_TYPE;
  }

  @Override
  protected long preparePayload(ParsableByteArray packet) {
    if (!isAudioPacket(packet.data)) {
      return -1;
    }
    return getFlacFrameBlockSize(packet);
  }

  @Override
  protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData)
      throws IOException, InterruptedException {
    byte[] data = packet.data;
    if (streamMetadata == null) {
      streamMetadata = new FlacStreamMetadata(data, 17);
      byte[] metadata = Arrays.copyOfRange(data, 9, packet.limit());
      metadata[4] = (byte) 0x80; // Set the last metadata block flag, ignore the other blocks
      List<byte[]> initializationData = Collections.singletonList(metadata);
      setupData.format =
          Format.createAudioSampleFormat(
              null,
              MimeTypes.AUDIO_FLAC,
              null,
              Format.NO_VALUE,
              streamMetadata.bitRate(),
              streamMetadata.channels,
              streamMetadata.sampleRate,
              initializationData,
              null,
              0,
              null);
    } else if ((data[0] & 0x7F) == SEEKTABLE_PACKET_TYPE) {
      flacOggSeeker = new FlacOggSeeker();
      flacOggSeeker.parseSeekTable(packet);
    } else if (isAudioPacket(data)) {
      if (flacOggSeeker != null) {
        flacOggSeeker.setFirstFrameOffset(position);
        setupData.oggSeeker = flacOggSeeker;
      }
      return false;
    }
    return true;
  }

  private int getFlacFrameBlockSize(ParsableByteArray packet) {
    int blockSizeCode = (packet.data[2] & 0xFF) >> 4;
    switch (blockSizeCode) {
      case 1:
        return 192;
      case 2:
      case 3:
      case 4:
      case 5:
        return 576 << (blockSizeCode - 2);
      case 6:
      case 7:
        // skip the sample number
        packet.skipBytes(FRAME_HEADER_SAMPLE_NUMBER_OFFSET);
        packet.readUtf8EncodedLong();
        int value = blockSizeCode == 6 ? packet.readUnsignedByte() : packet.readUnsignedShort();
        packet.setPosition(0);
        return value + 1;
      case 8:
      case 9:
      case 10:
      case 11:
      case 12:
      case 13:
      case 14:
      case 15:
        return 256 << (blockSizeCode - 8);
      default:
        return -1;
    }
  }

  private class FlacOggSeeker implements OggSeeker, SeekMap {

    private static final int METADATA_LENGTH_OFFSET = 1;
    private static final int SEEK_POINT_SIZE = 18;

    private long[] seekPointGranules;
    private long[] seekPointOffsets;
    private long firstFrameOffset;
    private long pendingSeekGranule;

    public FlacOggSeeker() {
      firstFrameOffset = -1;
      pendingSeekGranule = -1;
    }

    public void setFirstFrameOffset(long firstFrameOffset) {
      this.firstFrameOffset = firstFrameOffset;
    }

    /**
     * Parses a FLAC file seek table metadata structure and initializes internal fields.
     *
     * @param data A {@link ParsableByteArray} including whole seek table metadata block. Its
     *     position should be set to the beginning of the block.
     * @see <a href="https://xiph.org/flac/format.html#metadata_block_seektable">FLAC format
     *     METADATA_BLOCK_SEEKTABLE</a>
     */
    public void parseSeekTable(ParsableByteArray data) {
      data.skipBytes(METADATA_LENGTH_OFFSET);
      int length = data.readUnsignedInt24();
      int numberOfSeekPoints = length / SEEK_POINT_SIZE;
      seekPointGranules = new long[numberOfSeekPoints];
      seekPointOffsets = new long[numberOfSeekPoints];
      for (int i = 0; i < numberOfSeekPoints; i++) {
        seekPointGranules[i] = data.readLong();
        seekPointOffsets[i] = data.readLong();
        data.skipBytes(2); // Skip "Number of samples in the target frame."
      }
    }

    @Override
    public long read(ExtractorInput input) throws IOException, InterruptedException {
      if (pendingSeekGranule >= 0) {
        long result = -(pendingSeekGranule + 2);
        pendingSeekGranule = -1;
        return result;
      }
      return -1;
    }

    @Override
    public void startSeek(long targetGranule) {
      int index = Util.binarySearchFloor(seekPointGranules, targetGranule, true, true);
      pendingSeekGranule = seekPointGranules[index];
    }

    @Override
    public SeekMap createSeekMap() {
      return this;
    }

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      long granule = convertTimeToGranule(timeUs);
      int index = Util.binarySearchFloor(seekPointGranules, granule, true, true);
      long seekTimeUs = convertGranuleToTime(seekPointGranules[index]);
      long seekPosition = firstFrameOffset + seekPointOffsets[index];
      SeekPoint seekPoint = new SeekPoint(seekTimeUs, seekPosition);
      if (seekTimeUs >= timeUs || index == seekPointGranules.length - 1) {
        return new SeekPoints(seekPoint);
      } else {
        long secondSeekTimeUs = convertGranuleToTime(seekPointGranules[index + 1]);
        long secondSeekPosition = firstFrameOffset + seekPointOffsets[index + 1];
        SeekPoint secondSeekPoint = new SeekPoint(secondSeekTimeUs, secondSeekPosition);
        return new SeekPoints(seekPoint, secondSeekPoint);
      }
    }

    @Override
    public long getDurationUs() {
      return streamMetadata.durationUs();
    }

  }

}
