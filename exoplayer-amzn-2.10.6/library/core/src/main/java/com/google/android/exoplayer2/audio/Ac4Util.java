/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.nio.ByteBuffer;

/** Utility methods for parsing AC-4 frames, which are access units in AC-4 bitstreams. */
public final class Ac4Util {

  /** Holds sample format information as presented by a syncframe header. */
  public static final class SyncFrameInfo {

    /** The bitstream version. */
    public final int bitstreamVersion;
    /** The audio sampling rate in Hz. */
    public final int sampleRate;
    /** The number of audio channels */
    public final int channelCount;
    /** The size of the frame. */
    public final int frameSize;
    /** Number of audio samples in the frame. */
    public final int sampleCount;

    private SyncFrameInfo(
        int bitstreamVersion, int channelCount, int sampleRate, int frameSize, int sampleCount) {
      this.bitstreamVersion = bitstreamVersion;
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.frameSize = frameSize;
      this.sampleCount = sampleCount;
    }
  }

  public static final int AC40_SYNCWORD = 0xAC40;
  public static final int AC41_SYNCWORD = 0xAC41;

  /** The channel count of AC-4 stream. */
  // TODO: Parse AC-4 stream channel count.
  private static final int CHANNEL_COUNT_2 = 2;
  /**
   * The header size for AC-4 parser. Only needs to be as big as we need to read, not the full
   * header size.
   */
  public static final int HEADER_SIZE_FOR_PARSER = 16;
  /**
   * Number of audio samples in the frame. Defined in IEC61937-14:2017 table 5 and 6. This table
   * provides the number of samples per frame at the playback sampling frequency of 48 kHz. For 44.1
   * kHz, only frame_rate_index(13) is valid and corresponding sample count is 2048.
   */
  private static final int[] SAMPLE_COUNT =
      new int[] {
        /* [ 0]  23.976 fps */ 2002,
        /* [ 1]  24     fps */ 2000,
        /* [ 2]  25     fps */ 1920,
        /* [ 3]  29.97  fps */ 1601, // 1601 | 1602 | 1601 | 1602 | 1602
        /* [ 4]  30     fps */ 1600,
        /* [ 5]  47.95  fps */ 1001,
        /* [ 6]  48     fps */ 1000,
        /* [ 7]  50     fps */ 960,
        /* [ 8]  59.94  fps */ 800, //  800 |  801 |  801 |  801 |  801
        /* [ 9]  60     fps */ 800,
        /* [10] 100     fps */ 480,
        /* [11] 119.88  fps */ 400, //  400 |  400 |  401 |  400 |  401
        /* [12] 120     fps */ 400,
        /* [13]  23.438 fps */ 2048
      };

  /**
   * Returns the AC-4 format given {@code data} containing the AC4SpecificBox according to ETSI TS
   * 103 190-1 Annex E. The reading position of {@code data} will be modified.
   *
   * @param data The AC4SpecificBox to parse.
   * @param trackId The track identifier to set on the format.
   * @param language The language to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The AC-4 format parsed from data in the header.
   */
  public static Format parseAc4AnnexEFormat(
      ParsableByteArray data, String trackId, String language, DrmInitData drmInitData) {
    data.skipBytes(1); // ac4_dsi_version, bitstream_version[0:5]
    int sampleRate = ((data.readUnsignedByte() & 0x20) >> 5 == 1) ? 48000 : 44100;
    return Format.createAudioSampleFormat(
        trackId,
        MimeTypes.AUDIO_AC4,
        /* codecs= */ null,
        /* bitrate= */ Format.NO_VALUE,
        /* maxInputSize= */ Format.NO_VALUE,
        CHANNEL_COUNT_2,
        sampleRate,
        /* initializationData= */ null,
        drmInitData,
        /* selectionFlags= */ 0,
        language);
  }

  /**
   * Returns AC-4 format information given {@code data} containing a syncframe. The reading position
   * of {@code data} will be modified.
   *
   * @param data The data to parse, positioned at the start of the syncframe.
   * @return The AC-4 format data parsed from the header.
   */
  public static SyncFrameInfo parseAc4SyncframeInfo(ParsableBitArray data) {
    int headerSize = 0;
    int syncWord = data.readBits(16);
    headerSize += 2;
    int frameSize = data.readBits(16);
    headerSize += 2;
    if (frameSize == 0xFFFF) {
      frameSize = data.readBits(24);
      headerSize += 3; // Extended frame_size
    }
    frameSize += headerSize;
    if (syncWord == AC41_SYNCWORD) {
      frameSize += 2; // crc_word
    }
    int bitstreamVersion = data.readBits(2);
    if (bitstreamVersion == 3) {
      bitstreamVersion += readVariableBits(data, /* bitsPerRead= */ 2);
    }
    int sequenceCounter = data.readBits(10);
    if (data.readBit()) { // b_wait_frames
      if (data.readBits(3) > 0) { // wait_frames
        data.skipBits(2); // reserved
      }
    }
    int sampleRate = data.readBit() ? 48000 : 44100;
    int frameRateIndex = data.readBits(4);
    int sampleCount = 0;
    if (sampleRate == 44100 && frameRateIndex == 13) {
      sampleCount = SAMPLE_COUNT[frameRateIndex];
    } else if (sampleRate == 48000 && frameRateIndex < SAMPLE_COUNT.length) {
      sampleCount = SAMPLE_COUNT[frameRateIndex];
      switch (sequenceCounter % 5) {
        case 1: // fall through
        case 3:
          if (frameRateIndex == 3 || frameRateIndex == 8) {
            sampleCount++;
          }
          break;
        case 2:
          if (frameRateIndex == 8 || frameRateIndex == 11) {
            sampleCount++;
          }
          break;
        case 4:
          if (frameRateIndex == 3 || frameRateIndex == 8 || frameRateIndex == 11) {
            sampleCount++;
          }
          break;
        default:
          break;
      }
    }
    return new SyncFrameInfo(bitstreamVersion, CHANNEL_COUNT_2, sampleRate, frameSize, sampleCount);
  }

  /**
   * Returns the size in bytes of the given AC-4 syncframe.
   *
   * @param data The syncframe to parse.
   * @param syncword The syncword value for the syncframe.
   * @return The syncframe size in bytes, or {@link C#LENGTH_UNSET} if the input is invalid.
   */
  public static int parseAc4SyncframeSize(byte[] data, int syncword) {
    if (data.length < 7) {
      return C.LENGTH_UNSET;
    }
    int headerSize = 2; // syncword
    int frameSize = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
    headerSize += 2;
    if (frameSize == 0xFFFF) {
      frameSize = ((data[4] & 0xFF) << 16) | ((data[5] & 0xFF) << 8) | (data[6] & 0xFF);
      headerSize += 3;
    }
    if (syncword == AC41_SYNCWORD) {
      headerSize += 2;
    }
    frameSize += headerSize;
    return frameSize;
  }

  /**
   * Reads the number of audio samples represented by the given AC-4 syncframe. The buffer's
   * position is not modified.
   *
   * @param buffer The {@link ByteBuffer} from which to read the syncframe.
   * @return The number of audio samples represented by the syncframe.
   */
  public static int parseAc4SyncframeAudioSampleCount(ByteBuffer buffer) {
    byte[] bufferBytes = new byte[HEADER_SIZE_FOR_PARSER];
    int position = buffer.position();
    buffer.get(bufferBytes);
    buffer.position(position);
    return parseAc4SyncframeInfo(new ParsableBitArray(bufferBytes)).sampleCount;
  }

  /** Populates {@code buffer} with an AC-4 sample header for a sample of the specified size. */
  public static void getAc4SampleHeader(int size, ParsableByteArray buffer) {
    // See ETSI TS 103 190-1 V1.3.1, Annex G.
    buffer.reset(/* limit= */ 7);
    buffer.data[0] = (byte) 0xAC;
    buffer.data[1] = 0x40;
    buffer.data[2] = (byte) 0xFF;
    buffer.data[3] = (byte) 0xFF;
    buffer.data[4] = (byte) ((size >> 16) & 0xFF);
    buffer.data[5] = (byte) ((size >> 8) & 0xFF);
    buffer.data[6] = (byte) (size & 0xFF);
  }

  private static int readVariableBits(ParsableBitArray data, int bitsPerRead) {
    int value = 0;
    while (true) {
      value += data.readBits(bitsPerRead);
      if (!data.readBit()) {
        break;
      }
      value++;
      value <<= bitsPerRead;
    }
    return value;
  }

  private Ac4Util() {}
}
