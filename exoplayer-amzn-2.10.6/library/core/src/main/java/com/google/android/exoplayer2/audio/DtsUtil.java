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
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Utility methods for parsing DTS frames.
 */
public final class DtsUtil {

  private static final int SYNC_VALUE_BE = 0x7FFE8001;
  private static final int SYNC_VALUE_14B_BE = 0x1FFFE800;
  private static final int SYNC_VALUE_LE = 0xFE7F0180;
  private static final int SYNC_VALUE_14B_LE = 0xFF1F00E8;
  private static final byte FIRST_BYTE_BE = (byte) (SYNC_VALUE_BE >>> 24);
  private static final byte FIRST_BYTE_14B_BE = (byte) (SYNC_VALUE_14B_BE >>> 24);
  private static final byte FIRST_BYTE_LE = (byte) (SYNC_VALUE_LE >>> 24);
  private static final byte FIRST_BYTE_14B_LE = (byte) (SYNC_VALUE_14B_LE >>> 24);

  /**
   * Maps AMODE to the number of channels. See ETSI TS 102 114 table 5.4.
   */
  private static final int[] CHANNELS_BY_AMODE = new int[] {1, 2, 2, 2, 2, 3, 3, 4, 4, 5, 6, 6, 6,
      7, 8, 8};

  /**
   * Maps SFREQ to the sampling frequency in Hz. See ETSI TS 102 144 table 5.5.
   */
  private static final int[] SAMPLE_RATE_BY_SFREQ = new int[] {-1, 8000, 16000, 32000, -1, -1,
      11025, 22050, 44100, -1, -1, 12000, 24000, 48000, -1, -1};

  /**
   * Maps RATE to 2 * bitrate in kbit/s. See ETSI TS 102 144 table 5.7.
   */
  private static final int[] TWICE_BITRATE_KBPS_BY_RATE = new int[] {64, 112, 128, 192, 224, 256,
      384, 448, 512, 640, 768, 896, 1024, 1152, 1280, 1536, 1920, 2048, 2304, 2560, 2688, 2816,
      2823, 2944, 3072, 3840, 4096, 6144, 7680};

  /**
   * Returns whether a given integer matches a DTS sync word. Synchronization and storage modes are
   * defined in ETSI TS 102 114 V1.1.1 (2002-08), Section 5.3.
   *
   * @param word An integer.
   * @return Whether a given integer matches a DTS sync word.
   */
  public static boolean isSyncWord(int word) {
    return word == SYNC_VALUE_BE
        || word == SYNC_VALUE_LE
        || word == SYNC_VALUE_14B_BE
        || word == SYNC_VALUE_14B_LE;
  }

  /**
   * Returns the DTS format given {@code data} containing the DTS frame according to ETSI TS 102 114
   * subsections 5.3/5.4.
   *
   * @param frame The DTS frame to parse.
   * @param trackId The track identifier to set on the format.
   * @param language The language to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The DTS format parsed from data in the header.
   */
  public static Format parseDtsFormat(
      byte[] frame, String trackId, String language, DrmInitData drmInitData) {
    ParsableBitArray frameBits = getNormalizedFrameHeader(frame);
    frameBits.skipBits(32 + 1 + 5 + 1 + 7 + 14); // SYNC, FTYPE, SHORT, CPF, NBLKS, FSIZE
    int amode = frameBits.readBits(6);
    int channelCount = CHANNELS_BY_AMODE[amode];
    int sfreq = frameBits.readBits(4);
    int sampleRate = SAMPLE_RATE_BY_SFREQ[sfreq];
    int rate = frameBits.readBits(5);
    int bitrate = rate >= TWICE_BITRATE_KBPS_BY_RATE.length ? Format.NO_VALUE
        : TWICE_BITRATE_KBPS_BY_RATE[rate] * 1000 / 2;
    frameBits.skipBits(10); // MIX, DYNF, TIMEF, AUXF, HDCD, EXT_AUDIO_ID, EXT_AUDIO, ASPF
    channelCount += frameBits.readBits(2) > 0 ? 1 : 0; // LFF
    return Format.createAudioSampleFormat(trackId, MimeTypes.AUDIO_DTS, null, bitrate,
        Format.NO_VALUE, channelCount, sampleRate, null, drmInitData, 0, language);
  }

  /**
   * Returns the number of audio samples represented by the given DTS frame.
   *
   * @param data The frame to parse.
   * @return The number of audio samples represented by the frame.
   */
  public static int parseDtsAudioSampleCount(byte[] data) {
    int nblks;
    switch (data[0]) {
      case FIRST_BYTE_LE:
        nblks = ((data[5] & 0x01) << 6) | ((data[4] & 0xFC) >> 2);
        break;
      case FIRST_BYTE_14B_LE:
        nblks = ((data[4] & 0x07) << 4) | ((data[7] & 0x3C) >> 2);
        break;
      case FIRST_BYTE_14B_BE:
        nblks = ((data[5] & 0x07) << 4) | ((data[6] & 0x3C) >> 2);
        break;
      default:
        // We blindly assume FIRST_BYTE_BE if none of the others match.
        nblks = ((data[4] & 0x01) << 6) | ((data[5] & 0xFC) >> 2);
    }
    return (nblks + 1) * 32;
  }

  /**
   * Like {@link #parseDtsAudioSampleCount(byte[])} but reads from a {@link ByteBuffer}. The
   * buffer's position is not modified.
   *
   * @param buffer The {@link ByteBuffer} from which to read.
   * @return The number of audio samples represented by the syncframe.
   */
  public static int parseDtsAudioSampleCount(ByteBuffer buffer) {
    // See ETSI TS 102 114 subsection 5.4.1.
    int position = buffer.position();
    int nblks;
    switch (buffer.get(position)) {
      case FIRST_BYTE_LE:
        nblks = ((buffer.get(position + 5) & 0x01) << 6) | ((buffer.get(position + 4) & 0xFC) >> 2);
        break;
      case FIRST_BYTE_14B_LE:
        nblks = ((buffer.get(position + 4) & 0x07) << 4) | ((buffer.get(position + 7) & 0x3C) >> 2);
        break;
      case FIRST_BYTE_14B_BE:
        nblks = ((buffer.get(position + 5) & 0x07) << 4) | ((buffer.get(position + 6) & 0x3C) >> 2);
        break;
      default:
        // We blindly assume FIRST_BYTE_BE if none of the others match.
        nblks = ((buffer.get(position + 4) & 0x01) << 6) | ((buffer.get(position + 5) & 0xFC) >> 2);
    }
    return (nblks + 1) * 32;
  }

  /**
   * Returns the size in bytes of the given DTS frame.
   *
   * @param data The frame to parse.
   * @return The frame's size in bytes.
   */
  public static int getDtsFrameSize(byte[] data) {
    int fsize;
    boolean uses14BitPerWord = false;
    switch (data[0]) {
      case FIRST_BYTE_14B_BE:
        fsize = (((data[6] & 0x03) << 12) | ((data[7] & 0xFF) << 4) | ((data[8] & 0x3C) >> 2)) + 1;
        uses14BitPerWord = true;
        break;
      case FIRST_BYTE_LE:
        fsize = (((data[4] & 0x03) << 12) | ((data[7] & 0xFF) << 4) | ((data[6] & 0xF0) >> 4)) + 1;
        break;
      case FIRST_BYTE_14B_LE:
        fsize = (((data[7] & 0x03) << 12) | ((data[6] & 0xFF) << 4) | ((data[9] & 0x3C) >> 2)) + 1;
        uses14BitPerWord = true;
        break;
      default:
        // We blindly assume FIRST_BYTE_BE if none of the others match.
        fsize = (((data[5] & 0x03) << 12) | ((data[6] & 0xFF) << 4) | ((data[7] & 0xF0) >> 4)) + 1;
    }

    // If the frame is stored in 14-bit mode, adjust the frame size to reflect the actual byte size.
    return uses14BitPerWord ? fsize * 16 / 14 : fsize;
  }

  private static ParsableBitArray getNormalizedFrameHeader(byte[] frameHeader) {
    if (frameHeader[0] == FIRST_BYTE_BE) {
      // The frame is already 16-bit mode, big endian.
      return new ParsableBitArray(frameHeader);
    }
    // Data is not normalized, but we don't want to modify frameHeader.
    frameHeader = Arrays.copyOf(frameHeader, frameHeader.length);
    if (isLittleEndianFrameHeader(frameHeader)) {
      // Change endianness.
      for (int i = 0; i < frameHeader.length - 1; i += 2) {
        byte temp = frameHeader[i];
        frameHeader[i] = frameHeader[i + 1];
        frameHeader[i + 1] = temp;
      }
    }
    ParsableBitArray frameBits = new ParsableBitArray(frameHeader);
    if (frameHeader[0] == (byte) (SYNC_VALUE_14B_BE >> 24)) {
      // Discard the 2 most significant bits of each 16 bit word.
      ParsableBitArray scratchBits = new ParsableBitArray(frameHeader);
      while (scratchBits.bitsLeft() >= 16) {
        scratchBits.skipBits(2);
        frameBits.putInt(scratchBits.readBits(14), 14);
      }
    }
    frameBits.reset(frameHeader);
    return frameBits;
  }

  private static boolean isLittleEndianFrameHeader(byte[] frameHeader) {
    return frameHeader[0] == FIRST_BYTE_LE || frameHeader[0] == FIRST_BYTE_14B_LE;
  }

  private DtsUtil() {}

}
