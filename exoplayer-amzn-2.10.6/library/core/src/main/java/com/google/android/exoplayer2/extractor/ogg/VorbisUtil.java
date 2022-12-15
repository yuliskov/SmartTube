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

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Arrays;

/**
 * Utility methods for parsing vorbis streams.
 */
/* package */ final class VorbisUtil {

  private static final String TAG = "VorbisUtil";

  /**
   * Returns ilog(x), which is the index of the highest set bit in {@code x}.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-1190009.2.1">
   *     Vorbis spec</a>
   * @param x the value of which the ilog should be calculated.
   * @return ilog(x)
   */
  public static int iLog(int x) {
    int val = 0;
    while (x > 0) {
      val++;
      x >>>= 1;
    }
    return val;
  }

  /**
   * Reads a vorbis identification header from {@code headerData}.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-630004.2.2">Vorbis
   *     spec/Identification header</a>
   * @param headerData a {@link ParsableByteArray} wrapping the header data.
   * @return a {@link VorbisUtil.VorbisIdHeader} with meta data.
   * @throws ParserException thrown if invalid capture pattern is detected.
   */
  public static VorbisIdHeader readVorbisIdentificationHeader(ParsableByteArray headerData)
      throws ParserException {

    verifyVorbisHeaderCapturePattern(0x01, headerData, false);

    long version = headerData.readLittleEndianUnsignedInt();
    int channels = headerData.readUnsignedByte();
    long sampleRate = headerData.readLittleEndianUnsignedInt();
    int bitrateMax = headerData.readLittleEndianInt();
    int bitrateNominal = headerData.readLittleEndianInt();
    int bitrateMin = headerData.readLittleEndianInt();

    int blockSize = headerData.readUnsignedByte();
    int blockSize0 = (int) Math.pow(2, blockSize & 0x0F);
    int blockSize1 = (int) Math.pow(2, (blockSize & 0xF0) >> 4);

    boolean framingFlag = (headerData.readUnsignedByte() & 0x01) > 0;
    // raw data of vorbis setup header has to be passed to decoder as CSD buffer #1
    byte[] data = Arrays.copyOf(headerData.data, headerData.limit());

    return new VorbisIdHeader(version, channels, sampleRate, bitrateMax, bitrateNominal, bitrateMin,
        blockSize0, blockSize1, framingFlag, data);
  }

  /**
   * Reads a vorbis comment header.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-640004.2.3">
   *     Vorbis spec/Comment header</a>
   * @param headerData a {@link ParsableByteArray} wrapping the header data.
   * @return a {@link VorbisUtil.CommentHeader} with all the comments.
   * @throws ParserException thrown if invalid capture pattern is detected.
   */
  public static CommentHeader readVorbisCommentHeader(ParsableByteArray headerData)
      throws ParserException {

    verifyVorbisHeaderCapturePattern(0x03, headerData, false);
    int length = 7;

    int len = (int) headerData.readLittleEndianUnsignedInt();
    length += 4;
    String vendor = headerData.readString(len);
    length += vendor.length();

    long commentListLen = headerData.readLittleEndianUnsignedInt();
    String[] comments = new String[(int) commentListLen];
    length += 4;
    for (int i = 0; i < commentListLen; i++) {
      len = (int) headerData.readLittleEndianUnsignedInt();
      length += 4;
      comments[i] = headerData.readString(len);
      length += comments[i].length();
    }
    if ((headerData.readUnsignedByte() & 0x01) == 0) {
      throw new ParserException("framing bit expected to be set");
    }
    length += 1;
    return new CommentHeader(vendor, comments, length);
  }

  /**
   * Verifies whether the next bytes in {@code header} are a vorbis header of the given
   * {@code headerType}.
   *
   * @param headerType the type of the header expected.
   * @param header the alleged header bytes.
   * @param quiet if {@code true} no exceptions are thrown. Instead {@code false} is returned.
   * @return the number of bytes read.
   * @throws ParserException thrown if header type or capture pattern is not as expected.
   */
  public static boolean verifyVorbisHeaderCapturePattern(int headerType, ParsableByteArray header,
      boolean quiet)
      throws ParserException {
    if (header.bytesLeft() < 7) {
      if (quiet) {
        return false;
      } else {
        throw new ParserException("too short header: " + header.bytesLeft());
      }
    }

    if (header.readUnsignedByte() != headerType) {
      if (quiet) {
        return false;
      } else {
        throw new ParserException("expected header type " + Integer.toHexString(headerType));
      }
    }

    if (!(header.readUnsignedByte() == 'v'
        && header.readUnsignedByte() == 'o'
        && header.readUnsignedByte() == 'r'
        && header.readUnsignedByte() == 'b'
        && header.readUnsignedByte() == 'i'
        && header.readUnsignedByte() == 's')) {
      if (quiet) {
        return false;
      } else {
        throw new ParserException("expected characters 'vorbis'");
      }
    }
    return true;
  }

  /**
   * This method reads the modes which are located at the very end of the vorbis setup header.
   * That's why we need to partially decode or at least read the entire setup header to know
   * where to start reading the modes.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-650004.2.4">
   *     Vorbis spec/Setup header</a>
   * @param headerData a {@link ParsableByteArray} containing setup header data.
   * @param channels the number of channels.
   * @return an array of {@link Mode}s.
   * @throws ParserException thrown if bit stream is invalid.
   */
  public static Mode[] readVorbisModes(ParsableByteArray headerData, int channels)
      throws ParserException {

    verifyVorbisHeaderCapturePattern(0x05, headerData, false);

    int numberOfBooks = headerData.readUnsignedByte() + 1;

    VorbisBitArray bitArray  = new VorbisBitArray(headerData.data);
    bitArray.skipBits(headerData.getPosition() * 8);

    for (int i = 0; i < numberOfBooks; i++) {
      readBook(bitArray);
    }

    int timeCount = bitArray.readBits(6) + 1;
    for (int i = 0; i < timeCount; i++) {
      if (bitArray.readBits(16) != 0x00) {
        throw new ParserException("placeholder of time domain transforms not zeroed out");
      }
    }
    readFloors(bitArray);
    readResidues(bitArray);
    readMappings(channels, bitArray);

    Mode[] modes = readModes(bitArray);
    if (!bitArray.readBit()) {
      throw new ParserException("framing bit after modes not set as expected");
    }
    return modes;
  }

  private static Mode[] readModes(VorbisBitArray bitArray) {
    int modeCount = bitArray.readBits(6) + 1;
    Mode[] modes = new Mode[modeCount];
    for (int i = 0; i < modeCount; i++) {
      boolean blockFlag = bitArray.readBit();
      int windowType = bitArray.readBits(16);
      int transformType = bitArray.readBits(16);
      int mapping = bitArray.readBits(8);
      modes[i] = new Mode(blockFlag, windowType, transformType, mapping);
    }
    return modes;
  }

  private static void readMappings(int channels, VorbisBitArray bitArray)
      throws ParserException {
    int mappingsCount = bitArray.readBits(6) + 1;
    for (int i = 0; i < mappingsCount; i++) {
      int mappingType = bitArray.readBits(16);
      switch (mappingType) {
        case 0:
          int submaps;
          if (bitArray.readBit()) {
            submaps = bitArray.readBits(4) + 1;
          } else {
            submaps = 1;
          }
          int couplingSteps;
          if (bitArray.readBit()) {
            couplingSteps = bitArray.readBits(8) + 1;
            for (int j = 0; j < couplingSteps; j++) {
              bitArray.skipBits(iLog(channels - 1)); // magnitude
              bitArray.skipBits(iLog(channels - 1)); // angle
            }
          } /*else {
            couplingSteps = 0;
          }*/
          if (bitArray.readBits(2) != 0x00) {
            throw new ParserException("to reserved bits must be zero after mapping coupling steps");
          }
          if (submaps > 1) {
            for (int j = 0; j < channels; j++) {
              bitArray.skipBits(4); // mappingMux
            }
          }
          for (int j = 0; j < submaps; j++) {
            bitArray.skipBits(8); // discard
            bitArray.skipBits(8); // submapFloor
            bitArray.skipBits(8); // submapResidue
          }
          break;
        default:
          Log.e(TAG, "mapping type other than 0 not supported: " + mappingType);
      }
    }
  }

  private static void readResidues(VorbisBitArray bitArray) throws ParserException {
    int residueCount = bitArray.readBits(6) + 1;
    for (int i = 0; i < residueCount; i++) {
      int residueType = bitArray.readBits(16);
      if (residueType > 2) {
        throw new ParserException("residueType greater than 2 is not decodable");
      } else {
        bitArray.skipBits(24); // begin
        bitArray.skipBits(24); // end
        bitArray.skipBits(24); // partitionSize (add one)
        int classifications = bitArray.readBits(6) + 1;
        bitArray.skipBits(8); // classbook
        int[] cascade = new int[classifications];
        for (int j = 0; j < classifications; j++) {
          int highBits = 0;
          int lowBits = bitArray.readBits(3);
          if (bitArray.readBit()) {
            highBits = bitArray.readBits(5);
          }
          cascade[j] = highBits * 8 + lowBits;
        }
        for (int j = 0; j < classifications; j++) {
          for (int k = 0; k < 8; k++) {
            if ((cascade[j] & (0x01 << k)) != 0) {
              bitArray.skipBits(8); // discard
            }
          }
        }
      }
    }
  }

  private static void readFloors(VorbisBitArray bitArray) throws ParserException {
    int floorCount = bitArray.readBits(6) + 1;
    for (int i = 0; i < floorCount; i++) {
      int floorType = bitArray.readBits(16);
      switch (floorType) {
        case 0:
          bitArray.skipBits(8); //order
          bitArray.skipBits(16); // rate
          bitArray.skipBits(16); // barkMapSize
          bitArray.skipBits(6); // amplitudeBits
          bitArray.skipBits(8); // amplitudeOffset
          int floorNumberOfBooks = bitArray.readBits(4) + 1;
          for (int j = 0; j < floorNumberOfBooks; j++) {
            bitArray.skipBits(8);
          }
          break;
        case 1:
          int partitions = bitArray.readBits(5);
          int maximumClass = -1;
          int[] partitionClassList = new int[partitions];
          for (int j = 0; j < partitions; j++) {
            partitionClassList[j] = bitArray.readBits(4);
            if (partitionClassList[j] > maximumClass) {
              maximumClass = partitionClassList[j];
            }
          }
          int[] classDimensions = new int[maximumClass + 1];
          for (int j = 0; j < classDimensions.length; j++) {
            classDimensions[j] = bitArray.readBits(3) + 1;
            int classSubclasses = bitArray.readBits(2);
            if (classSubclasses > 0) {
              bitArray.skipBits(8); // classMasterbooks
            }
            for (int k = 0; k < (1 << classSubclasses); k++) {
              bitArray.skipBits(8); // subclassBook (subtract 1)
            }
          }
          bitArray.skipBits(2); // multiplier (add one)
          int rangeBits = bitArray.readBits(4);
          int count = 0;
          for (int j = 0, k = 0; j < partitions; j++) {
            int idx = partitionClassList[j];
            count += classDimensions[idx];
            for (; k < count; k++) {
              bitArray.skipBits(rangeBits); // floorValue
            }
          }
          break;
        default:
          throw new ParserException("floor type greater than 1 not decodable: " + floorType);
      }
    }
  }

  private static CodeBook readBook(VorbisBitArray bitArray) throws ParserException {
    if (bitArray.readBits(24) != 0x564342) {
      throw new ParserException("expected code book to start with [0x56, 0x43, 0x42] at "
          + bitArray.getPosition());
    }
    int dimensions = bitArray.readBits(16);
    int entries = bitArray.readBits(24);
    long[] lengthMap = new long[entries];

    boolean isOrdered = bitArray.readBit();
    if (!isOrdered) {
      boolean isSparse = bitArray.readBit();
      for (int i = 0; i < lengthMap.length; i++) {
        if (isSparse) {
          if (bitArray.readBit()) {
            lengthMap[i] = (long) (bitArray.readBits(5) + 1);
          } else { // entry unused
            lengthMap[i] = 0;
          }
        } else { // not sparse
          lengthMap[i] = (long) (bitArray.readBits(5) + 1);
        }
      }
    } else {
      int length = bitArray.readBits(5) + 1;
      for (int i = 0; i < lengthMap.length;) {
        int num = bitArray.readBits(iLog(entries - i));
        for (int j = 0; j < num && i < lengthMap.length; i++, j++) {
          lengthMap[i] = length;
        }
        length++;
      }
    }

    int lookupType = bitArray.readBits(4);
    if (lookupType > 2) {
      throw new ParserException("lookup type greater than 2 not decodable: " + lookupType);
    } else if (lookupType == 1 || lookupType == 2) {
      bitArray.skipBits(32); // minimumValue
      bitArray.skipBits(32); // deltaValue
      int valueBits = bitArray.readBits(4) + 1;
      bitArray.skipBits(1); // sequenceP
      long lookupValuesCount;
      if (lookupType == 1) {
        if (dimensions != 0) {
          lookupValuesCount = mapType1QuantValues(entries, dimensions);
        } else {
          lookupValuesCount = 0;
        }
      } else {
        lookupValuesCount = (long) entries * dimensions;
      }
      // discard (no decoding required yet)
      bitArray.skipBits((int) (lookupValuesCount * valueBits));
    }
    return new CodeBook(dimensions, entries, lengthMap, lookupType, isOrdered);
  }

  /**
   * @see <a href="http://svn.xiph.org/trunk/vorbis/lib/sharedbook.c">_book_maptype1_quantvals</a>
   */
  private static long mapType1QuantValues(long entries, long dimension) {
    return (long) Math.floor(Math.pow(entries, 1.d / dimension));
  }

  private VorbisUtil() {
    // Prevent instantiation.
  }

  public static final class CodeBook {

    public final int dimensions;
    public final int entries;
    public final long[] lengthMap;
    public final int lookupType;
    public final boolean isOrdered;

    public CodeBook(int dimensions, int entries, long[] lengthMap, int lookupType,
        boolean isOrdered) {
      this.dimensions = dimensions;
      this.entries = entries;
      this.lengthMap = lengthMap;
      this.lookupType = lookupType;
      this.isOrdered = isOrdered;
    }

  }

  public static final class CommentHeader {

    public final String vendor;
    public final String[] comments;
    public final int length;

    public CommentHeader(String vendor, String[] comments, int length) {
      this.vendor = vendor;
      this.comments = comments;
      this.length = length;
    }

  }

  public static final class VorbisIdHeader {

    public final long version;
    public final int channels;
    public final long sampleRate;
    public final int bitrateMax;
    public final int bitrateNominal;
    public final int bitrateMin;
    public final int blockSize0;
    public final int blockSize1;
    public final boolean framingFlag;
    public final byte[] data;

    public VorbisIdHeader(long version, int channels, long sampleRate, int bitrateMax,
        int bitrateNominal, int bitrateMin, int blockSize0, int blockSize1, boolean framingFlag,
        byte[] data) {
      this.version = version;
      this.channels = channels;
      this.sampleRate = sampleRate;
      this.bitrateMax = bitrateMax;
      this.bitrateNominal = bitrateNominal;
      this.bitrateMin = bitrateMin;
      this.blockSize0 = blockSize0;
      this.blockSize1 = blockSize1;
      this.framingFlag = framingFlag;
      this.data = data;
    }

    public int getApproximateBitrate() {
      return bitrateNominal == 0 ? (bitrateMin + bitrateMax) / 2 : bitrateNominal;
    }

  }

  public static final class Mode {

    public final boolean blockFlag;
    public final int windowType;
    public final int transformType;
    public final int mapping;

    public Mode(boolean blockFlag, int windowType, int transformType, int mapping) {
      this.blockFlag = blockFlag;
      this.windowType = windowType;
      this.transformType = transformType;
      this.mapping = mapping;
    }

  }

}
