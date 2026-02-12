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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.Ac3Util.SyncFrameInfo.StreamType;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * Utility methods for parsing Dolby TrueHD and (E-)AC-3 syncframes. (E-)AC-3 parsing follows the
 * definition in ETSI TS 102 366 V1.2.1.
 */
public final class Ac3Util {

  /** Holds sample format information as presented by a syncframe header. */
  public static final class SyncFrameInfo {

    /**
     * AC3 stream types. See also ETSI TS 102 366 E.1.3.1.1. One of {@link #STREAM_TYPE_UNDEFINED},
     * {@link #STREAM_TYPE_TYPE0}, {@link #STREAM_TYPE_TYPE1} or {@link #STREAM_TYPE_TYPE2}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STREAM_TYPE_UNDEFINED, STREAM_TYPE_TYPE0, STREAM_TYPE_TYPE1, STREAM_TYPE_TYPE2})
    public @interface StreamType {}
    /** Undefined AC3 stream type. */
    public static final int STREAM_TYPE_UNDEFINED = -1;
    /** Type 0 AC3 stream type. */
    public static final int STREAM_TYPE_TYPE0 = 0;
    /** Type 1 AC3 stream type. */
    public static final int STREAM_TYPE_TYPE1 = 1;
    /** Type 2 AC3 stream type. */
    public static final int STREAM_TYPE_TYPE2 = 2;

    /**
     * The sample mime type of the bitstream. One of {@link MimeTypes#AUDIO_AC3} and {@link
     * MimeTypes#AUDIO_E_AC3}.
     */
    @Nullable public final String mimeType;
    /**
     * The type of the stream if {@link #mimeType} is {@link MimeTypes#AUDIO_E_AC3}, or {@link
     * #STREAM_TYPE_UNDEFINED} otherwise.
     */
    public final @StreamType int streamType;
    /**
     * The audio sampling rate in Hz.
     */
    public final int sampleRate;
    /**
     * The number of audio channels
     */
    public final int channelCount;
    /**
     * The size of the frame.
     */
    public final int frameSize;
    /**
     * Number of audio samples in the frame.
     */
    public final int sampleCount;

    private SyncFrameInfo(
        @Nullable String mimeType,
        @StreamType int streamType,
        int channelCount,
        int sampleRate,
        int frameSize,
        int sampleCount) {
      this.mimeType = mimeType;
      this.streamType = streamType;
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.frameSize = frameSize;
      this.sampleCount = sampleCount;
    }

  }

  /**
   * The number of samples to store in each output chunk when rechunking TrueHD streams. The number
   * of samples extracted from the container corresponding to one syncframe must be an integer
   * multiple of this value.
   */
  public static final int TRUEHD_RECHUNK_SAMPLE_COUNT = 16;
  /**
   * The number of bytes that must be parsed from a TrueHD syncframe to calculate the sample count.
   */
  public static final int TRUEHD_SYNCFRAME_PREFIX_LENGTH = 10;

  /**
   * The number of new samples per (E-)AC-3 audio block.
   */
  private static final int AUDIO_SAMPLES_PER_AUDIO_BLOCK = 256;
  /**
   * Each syncframe has 6 blocks that provide 256 new audio samples. See ETSI TS 102 366 4.1.
   */
  private static final int AC3_SYNCFRAME_AUDIO_SAMPLE_COUNT = 6 * AUDIO_SAMPLES_PER_AUDIO_BLOCK;
  /**
   * Number of audio blocks per E-AC-3 syncframe, indexed by numblkscod.
   */
  private static final int[] BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD = new int[] {1, 2, 3, 6};
  /**
   * Sample rates, indexed by fscod.
   */
  private static final int[] SAMPLE_RATE_BY_FSCOD = new int[] {48000, 44100, 32000};
  /**
   * Sample rates, indexed by fscod2 (E-AC-3).
   */
  private static final int[] SAMPLE_RATE_BY_FSCOD2 = new int[] {24000, 22050, 16000};
  /**
   * Channel counts, indexed by acmod.
   */
  private static final int[] CHANNEL_COUNT_BY_ACMOD = new int[] {2, 1, 2, 3, 3, 4, 4, 5};
  /**
   * Nominal bitrates in kbps, indexed by frmsizecod / 2. (See ETSI TS 102 366 table 4.13.)
   */
  private static final int[] BITRATE_BY_HALF_FRMSIZECOD = new int[] {32, 40, 48, 56, 64, 80, 96,
      112, 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640};
  /**
   * 16-bit words per syncframe, indexed by frmsizecod / 2. (See ETSI TS 102 366 table 4.13.)
   */
  private static final int[] SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1 = new int[] {69, 87, 104,
      121, 139, 174, 208, 243, 278, 348, 417, 487, 557, 696, 835, 975, 1114, 1253, 1393};

  /**
   * Returns the AC-3 format given {@code data} containing the AC3SpecificBox according to ETSI TS
   * 102 366 Annex F. The reading position of {@code data} will be modified.
   *
   * @param data The AC3SpecificBox to parse.
   * @param trackId The track identifier to set on the format.
   * @param language The language to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The AC-3 format parsed from data in the header.
   */
  public static Format parseAc3AnnexFFormat(
      ParsableByteArray data, String trackId, String language, DrmInitData drmInitData) {
    int fscod = (data.readUnsignedByte() & 0xC0) >> 6;
    int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
    int nextByte = data.readUnsignedByte();
    int channelCount = CHANNEL_COUNT_BY_ACMOD[(nextByte & 0x38) >> 3];
    if ((nextByte & 0x04) != 0) { // lfeon
      channelCount++;
    }
    return Format.createAudioSampleFormat(
        trackId,
        MimeTypes.AUDIO_AC3,
        /* codecs= */ null,
        Format.NO_VALUE,
        Format.NO_VALUE,
        channelCount,
        sampleRate,
        /* initializationData= */ null,
        drmInitData,
        /* selectionFlags= */ 0,
        language);
  }

  /**
   * Returns the E-AC-3 format given {@code data} containing the EC3SpecificBox according to ETSI TS
   * 102 366 Annex F. The reading position of {@code data} will be modified.
   *
   * @param data The EC3SpecificBox to parse.
   * @param trackId The track identifier to set on the format.
   * @param language The language to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The E-AC-3 format parsed from data in the header.
   */
  public static Format parseEAc3AnnexFFormat(
      ParsableByteArray data, String trackId, String language, DrmInitData drmInitData) {
    data.skipBytes(2); // data_rate, num_ind_sub

    // Read the first independent substream.
    int fscod = (data.readUnsignedByte() & 0xC0) >> 6;
    int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
    int nextByte = data.readUnsignedByte();
    int channelCount = CHANNEL_COUNT_BY_ACMOD[(nextByte & 0x0E) >> 1];
    if ((nextByte & 0x01) != 0) { // lfeon
      channelCount++;
    }

    // Read the first dependent substream.
    nextByte = data.readUnsignedByte();
    int numDepSub = ((nextByte & 0x1E) >> 1);
    if (numDepSub > 0) {
      int lowByteChanLoc = data.readUnsignedByte();
      // Read Lrs/Rrs pair
      // TODO: Read other channel configuration
      if ((lowByteChanLoc & 0x02) != 0) {
        channelCount += 2;
      }
    }
    String mimeType = MimeTypes.AUDIO_E_AC3;
    if (data.bytesLeft() > 0) {
      nextByte = data.readUnsignedByte();
      if ((nextByte & 0x01) != 0) { // flag_ec3_extension_type_a
        mimeType = MimeTypes.AUDIO_E_AC3_JOC;
      }
    }
    return Format.createAudioSampleFormat(
        trackId,
        mimeType,
        /* codecs= */ null,
        Format.NO_VALUE,
        Format.NO_VALUE,
        channelCount,
        sampleRate,
        /* initializationData= */ null,
        drmInitData,
        /* selectionFlags= */ 0,
        language);
  }

  /**
   * Returns (E-)AC-3 format information given {@code data} containing a syncframe. The reading
   * position of {@code data} will be modified.
   *
   * @param data The data to parse, positioned at the start of the syncframe.
   * @return The (E-)AC-3 format data parsed from the header.
   */
  public static SyncFrameInfo parseAc3SyncframeInfo(ParsableBitArray data) {
    int initialPosition = data.getPosition();
    data.skipBits(40);
    boolean isEac3 = data.readBits(5) == 16; // See bsid in subsection E.1.3.1.6.
    data.setPosition(initialPosition);
    String mimeType;
    @StreamType int streamType = SyncFrameInfo.STREAM_TYPE_UNDEFINED;
    int sampleRate;
    int acmod;
    int frameSize;
    int sampleCount;
    boolean lfeon;
    int channelCount;
    if (isEac3) {
      // Syntax from ETSI TS 102 366 V1.2.1 subsections E.1.2.1 and E.1.2.2.
      data.skipBits(16); // syncword
      switch (data.readBits(2)) { // strmtyp
        case 0:
          streamType = SyncFrameInfo.STREAM_TYPE_TYPE0;
          break;
        case 1:
          streamType = SyncFrameInfo.STREAM_TYPE_TYPE1;
          break;
        case 2:
          streamType = SyncFrameInfo.STREAM_TYPE_TYPE2;
          break;
        default:
          streamType = SyncFrameInfo.STREAM_TYPE_UNDEFINED;
          break;
      }
      data.skipBits(3); // substreamid
      frameSize = (data.readBits(11) + 1) * 2; // See frmsiz in subsection E.1.3.1.3.
      int fscod = data.readBits(2);
      int audioBlocks;
      int numblkscod;
      if (fscod == 3) {
        numblkscod = 3;
        sampleRate = SAMPLE_RATE_BY_FSCOD2[data.readBits(2)];
        audioBlocks = 6;
      } else {
        numblkscod = data.readBits(2);
        audioBlocks = BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD[numblkscod];
        sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
      }
      sampleCount = AUDIO_SAMPLES_PER_AUDIO_BLOCK * audioBlocks;
      acmod = data.readBits(3);
      lfeon = data.readBit();
      channelCount = CHANNEL_COUNT_BY_ACMOD[acmod] + (lfeon ? 1 : 0);
      data.skipBits(5 + 5); // bsid, dialnorm
      if (data.readBit()) { // compre
        data.skipBits(8); // compr
      }
      if (acmod == 0) {
        data.skipBits(5); // dialnorm2
        if (data.readBit()) { // compr2e
          data.skipBits(8); // compr2
        }
      }
      if (streamType == SyncFrameInfo.STREAM_TYPE_TYPE1 && data.readBit()) { // chanmape
        data.skipBits(16); // chanmap
      }
      if (data.readBit()) { // mixmdate
        if (acmod > 2) {
          data.skipBits(2); // dmixmod
        }
        if ((acmod & 0x01) != 0 && acmod > 2) {
          data.skipBits(3 + 3); // ltrtcmixlev, lorocmixlev
        }
        if ((acmod & 0x04) != 0) {
          data.skipBits(6); // ltrtsurmixlev, lorosurmixlev
        }
        if (lfeon && data.readBit()) { // lfemixlevcode
          data.skipBits(5); // lfemixlevcod
        }
        if (streamType == SyncFrameInfo.STREAM_TYPE_TYPE0) {
          if (data.readBit()) { // pgmscle
            data.skipBits(6); //pgmscl
          }
          if (acmod == 0 && data.readBit()) { // pgmscl2e
            data.skipBits(6); // pgmscl2
          }
          if (data.readBit()) { // extpgmscle
            data.skipBits(6); // extpgmscl
          }
          int mixdef = data.readBits(2);
          if (mixdef == 1) {
            data.skipBits(1 + 1 + 3); // premixcmpsel, drcsrc, premixcmpscl
          } else if (mixdef == 2) {
            data.skipBits(12); // mixdata
          } else if (mixdef == 3) {
            int mixdeflen = data.readBits(5);
            if (data.readBit()) { // mixdata2e
              data.skipBits(1 + 1 + 3); // premixcmpsel, drcsrc, premixcmpscl
              if (data.readBit()) { // extpgmlscle
                data.skipBits(4); // extpgmlscl
              }
              if (data.readBit()) { // extpgmcscle
                data.skipBits(4); // extpgmcscl
              }
              if (data.readBit()) { // extpgmrscle
                data.skipBits(4); // extpgmrscl
              }
              if (data.readBit()) { // extpgmlsscle
                data.skipBits(4); // extpgmlsscl
              }
              if (data.readBit()) { // extpgmrsscle
                data.skipBits(4); // extpgmrsscl
              }
              if (data.readBit()) { // extpgmlfescle
                data.skipBits(4); // extpgmlfescl
              }
              if (data.readBit()) { // dmixscle
                data.skipBits(4); // dmixscl
              }
              if (data.readBit()) { // addche
                if (data.readBit()) { // extpgmaux1scle
                  data.skipBits(4); // extpgmaux1scl
                }
                if (data.readBit()) { // extpgmaux2scle
                  data.skipBits(4); // extpgmaux2scl
                }
              }
            }
            if (data.readBit()) { // mixdata3e
              data.skipBits(5); // spchdat
              if (data.readBit()) { // addspchdate
                data.skipBits(5 + 2); // spchdat1, spchan1att
                if (data.readBit()) { // addspdat1e
                  data.skipBits(5 + 3); // spchdat2, spchan2att
                }
              }
            }
            data.skipBits(8 * (mixdeflen + 2)); // mixdata
            data.byteAlign(); // mixdatafill
          }
          if (acmod < 2) {
            if (data.readBit()) { // paninfoe
              data.skipBits(8 + 6); // panmean, paninfo
            }
            if (acmod == 0) {
              if (data.readBit()) { // paninfo2e
                data.skipBits(8 + 6); // panmean2, paninfo2
              }
            }
          }
          if (data.readBit()) { // frmmixcfginfoe
            if (numblkscod == 0) {
              data.skipBits(5); // blkmixcfginfo[0]
            } else {
              for (int blk = 0; blk < audioBlocks; blk++) {
                if (data.readBit()) { // blkmixcfginfoe
                  data.skipBits(5); // blkmixcfginfo[blk]
                }
              }
            }
          }
        }
      }
      if (data.readBit()) { // infomdate
        data.skipBits(3 + 1 + 1); // bsmod, copyrightb, origbs
        if (acmod == 2) {
          data.skipBits(2 + 2); // dsurmod, dheadphonmod
        }
        if (acmod >= 6) {
          data.skipBits(2); // dsurexmod
        }
        if (data.readBit()) { // audioprodie
          data.skipBits(5 + 2 + 1); // mixlevel, roomtyp, adconvtyp
        }
        if (acmod == 0 && data.readBit()) { // audioprodi2e
          data.skipBits(5 + 2 + 1); // mixlevel2, roomtyp2, adconvtyp2
        }
        if (fscod < 3) {
          data.skipBit(); // sourcefscod
        }
      }
      if (streamType == SyncFrameInfo.STREAM_TYPE_TYPE0 && numblkscod != 3) {
        data.skipBit(); // convsync
      }
      if (streamType == SyncFrameInfo.STREAM_TYPE_TYPE2
          && (numblkscod == 3 || data.readBit())) { // blkid
        data.skipBits(6); // frmsizecod
      }
      mimeType = MimeTypes.AUDIO_E_AC3;
      if (data.readBit()) { // addbsie
        int addbsil = data.readBits(6);
        if (addbsil == 1 && data.readBits(8) == 1) { // addbsi
          mimeType = MimeTypes.AUDIO_E_AC3_JOC;
        }
      }
    } else /* is AC-3 */ {
      mimeType = MimeTypes.AUDIO_AC3;
      data.skipBits(16 + 16); // syncword, crc1
      int fscod = data.readBits(2);
      if (fscod == 3) {
        // fscod '11' indicates that the decoder should not attempt to decode audio. We invalidate
        // the mime type to prevent association with a renderer.
        mimeType = null;
      }
      int frmsizecod = data.readBits(6);
      frameSize = getAc3SyncframeSize(fscod, frmsizecod);
      data.skipBits(5 + 3); // bsid, bsmod
      acmod = data.readBits(3);
      if ((acmod & 0x01) != 0 && acmod != 1) {
        data.skipBits(2); // cmixlev
      }
      if ((acmod & 0x04) != 0) {
        data.skipBits(2); // surmixlev
      }
      if (acmod == 2) {
        data.skipBits(2); // dsurmod
      }
      sampleRate =
          fscod < SAMPLE_RATE_BY_FSCOD.length ? SAMPLE_RATE_BY_FSCOD[fscod] : Format.NO_VALUE;
      sampleCount = AC3_SYNCFRAME_AUDIO_SAMPLE_COUNT;
      lfeon = data.readBit();
      channelCount = CHANNEL_COUNT_BY_ACMOD[acmod] + (lfeon ? 1 : 0);
    }
    return new SyncFrameInfo(
        mimeType, streamType, channelCount, sampleRate, frameSize, sampleCount);
  }

  /**
   * Returns the size in bytes of the given (E-)AC-3 syncframe.
   *
   * @param data The syncframe to parse.
   * @return The syncframe size in bytes. {@link C#LENGTH_UNSET} if the input is invalid.
   */
  public static int parseAc3SyncframeSize(byte[] data) {
    if (data.length < 6) {
      return C.LENGTH_UNSET;
    }
    boolean isEac3 = ((data[5] & 0xFF) >> 3) == 16; // See bsid in subsection E.1.3.1.6.
    if (isEac3) {
      int frmsiz = (data[2] & 0x07) << 8; // Most significant 3 bits.
      frmsiz |= data[3] & 0xFF; // Least significant 8 bits.
      return (frmsiz + 1) * 2; // See frmsiz in subsection E.1.3.1.3.
    } else {
      int fscod = (data[4] & 0xC0) >> 6;
      int frmsizecod = data[4] & 0x3F;
      return getAc3SyncframeSize(fscod, frmsizecod);
    }
  }

  /**
   * Returns the number of audio samples in an AC-3 syncframe.
   */
  public static int getAc3SyncframeAudioSampleCount() {
    return AC3_SYNCFRAME_AUDIO_SAMPLE_COUNT;
  }

  /**
   * Reads the number of audio samples represented by the given E-AC-3 syncframe. The buffer's
   * position is not modified.
   *
   * @param buffer The {@link ByteBuffer} from which to read the syncframe.
   * @return The number of audio samples represented by the syncframe.
   */
  public static int parseEAc3SyncframeAudioSampleCount(ByteBuffer buffer) {
    // See ETSI TS 102 366 subsection E.1.2.2.
    int fscod = (buffer.get(buffer.position() + 4) & 0xC0) >> 6;
    return AUDIO_SAMPLES_PER_AUDIO_BLOCK * (fscod == 0x03 ? 6
        : BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD[(buffer.get(buffer.position() + 4) & 0x30) >> 4]);
  }

  /**
   * Returns the offset relative to the buffer's position of the start of a TrueHD syncframe, or
   * {@link C#INDEX_UNSET} if no syncframe was found. The buffer's position is not modified.
   *
   * @param buffer The {@link ByteBuffer} within which to find a syncframe.
   * @return The offset relative to the buffer's position of the start of a TrueHD syncframe, or
   *     {@link C#INDEX_UNSET} if no syncframe was found.
   */
  public static int findTrueHdSyncframeOffset(ByteBuffer buffer) {
    int startIndex = buffer.position();
    int endIndex = buffer.limit() - TRUEHD_SYNCFRAME_PREFIX_LENGTH;
    for (int i = startIndex; i <= endIndex; i++) {
      // The syncword ends 0xBA for TrueHD or 0xBB for MLP.
      if ((buffer.getInt(i + 4) & 0xFEFFFFFF) == 0xBA6F72F8) {
        return i - startIndex;
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * Returns the number of audio samples represented by the given TrueHD syncframe, or 0 if the
   * buffer is not the start of a syncframe.
   *
   * @param syncframe The bytes from which to read the syncframe. Must be at least {@link
   *     #TRUEHD_SYNCFRAME_PREFIX_LENGTH} bytes long.
   * @return The number of audio samples represented by the syncframe, or 0 if the buffer doesn't
   *     contain the start of a syncframe.
   */
  public static int parseTrueHdSyncframeAudioSampleCount(byte[] syncframe) {
    // TODO: Link to specification if available.
    // The syncword ends 0xBA for TrueHD or 0xBB for MLP.
    if (syncframe[4] != (byte) 0xF8
        || syncframe[5] != (byte) 0x72
        || syncframe[6] != (byte) 0x6F
        || (syncframe[7] & 0xFE) != 0xBA) {
      return 0;
    }
    boolean isMlp = (syncframe[7] & 0xFF) == 0xBB;
    return 40 << ((syncframe[isMlp ? 9 : 8] >> 4) & 0x07);
  }

  /**
   * Reads the number of audio samples represented by a TrueHD syncframe. The buffer's position is
   * not modified.
   *
   * @param buffer The {@link ByteBuffer} from which to read the syncframe.
   * @param offset The offset of the start of the syncframe relative to the buffer's position.
   * @return The number of audio samples represented by the syncframe.
   */
  public static int parseTrueHdSyncframeAudioSampleCount(ByteBuffer buffer, int offset) {
    // TODO: Link to specification if available.
    boolean isMlp = (buffer.get(buffer.position() + offset + 7) & 0xFF) == 0xBB;
    return 40 << ((buffer.get(buffer.position() + offset + (isMlp ? 9 : 8)) >> 4) & 0x07);
  }

  private static int getAc3SyncframeSize(int fscod, int frmsizecod) {
    int halfFrmsizecod = frmsizecod / 2;
    if (fscod < 0 || fscod >= SAMPLE_RATE_BY_FSCOD.length || frmsizecod < 0
        || halfFrmsizecod >= SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1.length) {
      // Invalid values provided.
      return C.LENGTH_UNSET;
    }
    int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
    if (sampleRate == 44100) {
      return 2 * (SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1[halfFrmsizecod] + (frmsizecod % 2));
    }
    int bitrate = BITRATE_BY_HALF_FRMSIZECOD[halfFrmsizecod];
    if (sampleRate == 32000) {
      return 6 * bitrate;
    } else { // sampleRate == 48000
      return 4 * bitrate;
    }
  }

  private Ac3Util() {}

}
