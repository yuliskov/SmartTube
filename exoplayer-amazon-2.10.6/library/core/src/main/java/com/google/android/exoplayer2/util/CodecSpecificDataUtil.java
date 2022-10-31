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
package com.google.android.exoplayer2.util;

import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides static utility methods for manipulating various types of codec specific data.
 */
public final class CodecSpecificDataUtil {

  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  private static final int AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY = 0xF;

  private static final int[] AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE = new int[] {
    96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
  };

  private static final int AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID = -1;
  /**
   * In the channel configurations below, <A> indicates a single channel element; (A, B) indicates a
   * channel pair element; and [A] indicates a low-frequency effects element.
   * The speaker mapping short forms used are:
   * - FC: front center
   * - BC: back center
   * - FL/FR: front left/right
   * - FCL/FCR: front center left/right
   * - FTL/FTR: front top left/right
   * - SL/SR: back surround left/right
   * - BL/BR: back left/right
   * - LFE: low frequency effects
   */
  private static final int[] AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE =
      new int[] {
        0,
        1, /* mono: <FC> */
        2, /* stereo: (FL, FR) */
        3, /* 3.0: <FC>, (FL, FR) */
        4, /* 4.0: <FC>, (FL, FR), <BC> */
        5, /* 5.0 back: <FC>, (FL, FR), (SL, SR) */
        6, /* 5.1 back: <FC>, (FL, FR), (SL, SR), <BC>, [LFE] */
        8, /* 7.1 wide back: <FC>, (FCL, FCR), (FL, FR), (SL, SR), [LFE] */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        7, /* 6.1: <FC>, (FL, FR), (SL, SR), <RC>, [LFE] */
        8, /* 7.1: <FC>, (FL, FR), (SL, SR), (BL, BR), [LFE] */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        8, /* 7.1 top: <FC>, (FL, FR), (SL, SR), [LFE], (FTL, FTR) */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID
      };

  // Advanced Audio Coding Low-Complexity profile.
  private static final int AUDIO_OBJECT_TYPE_AAC_LC = 2;
  // Spectral Band Replication.
  private static final int AUDIO_OBJECT_TYPE_SBR = 5;
  // Error Resilient Bit-Sliced Arithmetic Coding.
  private static final int AUDIO_OBJECT_TYPE_ER_BSAC = 22;
  // Parametric Stereo.
  private static final int AUDIO_OBJECT_TYPE_PS = 29;
  // Escape code for extended audio object types.
  private static final int AUDIO_OBJECT_TYPE_ESCAPE = 31;

  private CodecSpecificDataUtil() {}

  /**
   * Parses an AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioSpecificConfig A byte array containing the AudioSpecificConfig to parse.
   * @return A pair consisting of the sample rate in Hz and the channel count.
   * @throws ParserException If the AudioSpecificConfig cannot be parsed as it's not supported.
   */
  public static Pair<Integer, Integer> parseAacAudioSpecificConfig(byte[] audioSpecificConfig)
      throws ParserException {
    return parseAacAudioSpecificConfig(new ParsableBitArray(audioSpecificConfig), false);
  }

  /**
   * Parses an AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param bitArray A {@link ParsableBitArray} containing the AudioSpecificConfig to parse. The
   *     position is advanced to the end of the AudioSpecificConfig.
   * @param forceReadToEnd Whether the entire AudioSpecificConfig should be read. Required for
   *     knowing the length of the configuration payload.
   * @return A pair consisting of the sample rate in Hz and the channel count.
   * @throws ParserException If the AudioSpecificConfig cannot be parsed as it's not supported.
   */
  public static Pair<Integer, Integer> parseAacAudioSpecificConfig(ParsableBitArray bitArray,
      boolean forceReadToEnd) throws ParserException {
    int audioObjectType = getAacAudioObjectType(bitArray);
    int sampleRate = getAacSamplingFrequency(bitArray);
    int channelConfiguration = bitArray.readBits(4);
    if (audioObjectType == AUDIO_OBJECT_TYPE_SBR || audioObjectType == AUDIO_OBJECT_TYPE_PS) {
      // For an AAC bitstream using spectral band replication (SBR) or parametric stereo (PS) with
      // explicit signaling, we return the extension sampling frequency as the sample rate of the
      // content; this is identical to the sample rate of the decoded output but may differ from
      // the sample rate set above.
      // Use the extensionSamplingFrequencyIndex.
      sampleRate = getAacSamplingFrequency(bitArray);
      audioObjectType = getAacAudioObjectType(bitArray);
      if (audioObjectType == AUDIO_OBJECT_TYPE_ER_BSAC) {
        // Use the extensionChannelConfiguration.
        channelConfiguration = bitArray.readBits(4);
      }
    }

    if (forceReadToEnd) {
      switch (audioObjectType) {
        case 1:
        case 2:
        case 3:
        case 4:
        case 6:
        case 7:
        case 17:
        case 19:
        case 20:
        case 21:
        case 22:
        case 23:
          parseGaSpecificConfig(bitArray, audioObjectType, channelConfiguration);
          break;
        default:
          throw new ParserException("Unsupported audio object type: " + audioObjectType);
      }
      switch (audioObjectType) {
        case 17:
        case 19:
        case 20:
        case 21:
        case 22:
        case 23:
          int epConfig = bitArray.readBits(2);
          if (epConfig == 2 || epConfig == 3) {
            throw new ParserException("Unsupported epConfig: " + epConfig);
          }
          break;
      }
    }
    // For supported containers, bits_to_decode() is always 0.
    int channelCount = AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[channelConfiguration];
    Assertions.checkArgument(channelCount != AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID);
    return Pair.create(sampleRate, channelCount);
  }

  /**
   * Builds a simple HE-AAC LC AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param sampleRate The sample rate in Hz.
   * @param numChannels The number of channels.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAacLcAudioSpecificConfig(int sampleRate, int numChannels) {
    int sampleRateIndex = C.INDEX_UNSET;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; ++i) {
      if (sampleRate == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
        sampleRateIndex = i;
      }
    }
    int channelConfig = C.INDEX_UNSET;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE.length; ++i) {
      if (numChannels == AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[i]) {
        channelConfig = i;
      }
    }
    if (sampleRate == C.INDEX_UNSET || channelConfig == C.INDEX_UNSET) {
      throw new IllegalArgumentException("Invalid sample rate or number of channels: "
          + sampleRate + ", " + numChannels);
    }
    return buildAacAudioSpecificConfig(AUDIO_OBJECT_TYPE_AAC_LC, sampleRateIndex, channelConfig);
  }

  /**
   * Builds a simple AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioObjectType The audio object type.
   * @param sampleRateIndex The sample rate index.
   * @param channelConfig The channel configuration.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAacAudioSpecificConfig(int audioObjectType, int sampleRateIndex,
      int channelConfig) {
    byte[] specificConfig = new byte[2];
    specificConfig[0] = (byte) (((audioObjectType << 3) & 0xF8) | ((sampleRateIndex >> 1) & 0x07));
    specificConfig[1] = (byte) (((sampleRateIndex << 7) & 0x80) | ((channelConfig << 3) & 0x78));
    return specificConfig;
  }

  /**
   * Builds an RFC 6381 AVC codec string using the provided parameters.
   *
   * @param profileIdc The encoding profile.
   * @param constraintsFlagsAndReservedZero2Bits The constraint flags followed by the reserved zero
   *     2 bits, all contained in the least significant byte of the integer.
   * @param levelIdc The encoding level.
   * @return An RFC 6381 AVC codec string built using the provided parameters.
   */
  public static String buildAvcCodecString(
      int profileIdc, int constraintsFlagsAndReservedZero2Bits, int levelIdc) {
    return String.format(
        "avc1.%02X%02X%02X", profileIdc, constraintsFlagsAndReservedZero2Bits, levelIdc);
  }

  /**
   * Constructs a NAL unit consisting of the NAL start code followed by the specified data.
   *
   * @param data An array containing the data that should follow the NAL start code.
   * @param offset The start offset into {@code data}.
   * @param length The number of bytes to copy from {@code data}
   * @return The constructed NAL unit.
   */
  public static byte[] buildNalUnit(byte[] data, int offset, int length) {
    byte[] nalUnit = new byte[length + NAL_START_CODE.length];
    System.arraycopy(NAL_START_CODE, 0, nalUnit, 0, NAL_START_CODE.length);
    System.arraycopy(data, offset, nalUnit, NAL_START_CODE.length, length);
    return nalUnit;
  }

  /**
   * Splits an array of NAL units.
   *
   * <p>If the input consists of NAL start code delimited units, then the returned array consists of
   * the split NAL units, each of which is still prefixed with the NAL start code. For any other
   * input, null is returned.
   *
   * @param data An array of data.
   * @return The individual NAL units, or null if the input did not consist of NAL start code
   *     delimited units.
   */
  public static @Nullable byte[][] splitNalUnits(byte[] data) {
    if (!isNalStartCode(data, 0)) {
      // data does not consist of NAL start code delimited units.
      return null;
    }
    List<Integer> starts = new ArrayList<>();
    int nalUnitIndex = 0;
    do {
      starts.add(nalUnitIndex);
      nalUnitIndex = findNalStartCode(data, nalUnitIndex + NAL_START_CODE.length);
    } while (nalUnitIndex != C.INDEX_UNSET);
    byte[][] split = new byte[starts.size()][];
    for (int i = 0; i < starts.size(); i++) {
      int startIndex = starts.get(i);
      int endIndex = i < starts.size() - 1 ? starts.get(i + 1) : data.length;
      byte[] nal = new byte[endIndex - startIndex];
      System.arraycopy(data, startIndex, nal, 0, nal.length);
      split[i] = nal;
    }
    return split;
  }

  /**
   * Finds the next occurrence of the NAL start code from a given index.
   *
   * @param data The data in which to search.
   * @param index The first index to test.
   * @return The index of the first byte of the found start code, or {@link C#INDEX_UNSET}.
   */
  private static int findNalStartCode(byte[] data, int index) {
    int endIndex = data.length - NAL_START_CODE.length;
    for (int i = index; i <= endIndex; i++) {
      if (isNalStartCode(data, i)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * Tests whether there exists a NAL start code at a given index.
   *
   * @param data The data.
   * @param index The index to test.
   * @return Whether there exists a start code that begins at {@code index}.
   */
  private static boolean isNalStartCode(byte[] data, int index) {
    if (data.length - index <= NAL_START_CODE.length) {
      return false;
    }
    for (int j = 0; j < NAL_START_CODE.length; j++) {
      if (data[index + j] != NAL_START_CODE[j]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the AAC audio object type as specified in 14496-3 (2005) Table 1.14.
   *
   * @param bitArray The bit array containing the audio specific configuration.
   * @return The audio object type.
   */
  private static int getAacAudioObjectType(ParsableBitArray bitArray) {
    int audioObjectType = bitArray.readBits(5);
    if (audioObjectType == AUDIO_OBJECT_TYPE_ESCAPE) {
      audioObjectType = 32 + bitArray.readBits(6);
    }
    return audioObjectType;
  }

  /**
   * Returns the AAC sampling frequency (or extension sampling frequency) as specified in 14496-3
   * (2005) Table 1.13.
   *
   * @param bitArray The bit array containing the audio specific configuration.
   * @return The sampling frequency.
   */
  private static int getAacSamplingFrequency(ParsableBitArray bitArray) {
    int samplingFrequency;
    int frequencyIndex = bitArray.readBits(4);
    if (frequencyIndex == AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY) {
      samplingFrequency = bitArray.readBits(24);
    } else {
      Assertions.checkArgument(frequencyIndex < 13);
      samplingFrequency = AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[frequencyIndex];
    }
    return samplingFrequency;
  }

  private static void parseGaSpecificConfig(ParsableBitArray bitArray, int audioObjectType,
      int channelConfiguration) {
    bitArray.skipBits(1); // frameLengthFlag.
    boolean dependsOnCoreDecoder = bitArray.readBit();
    if (dependsOnCoreDecoder) {
      bitArray.skipBits(14); // coreCoderDelay.
    }
    boolean extensionFlag = bitArray.readBit();
    if (channelConfiguration == 0) {
      throw new UnsupportedOperationException(); // TODO: Implement programConfigElement();
    }
    if (audioObjectType == 6 || audioObjectType == 20) {
      bitArray.skipBits(3); // layerNr.
    }
    if (extensionFlag) {
      if (audioObjectType == 22) {
        bitArray.skipBits(16); // numOfSubFrame (5), layer_length(11).
      }
      if (audioObjectType == 17 || audioObjectType == 19 || audioObjectType == 20
          || audioObjectType == 23) {
        // aacSectionDataResilienceFlag, aacScalefactorDataResilienceFlag,
        // aacSpectralDataResilienceFlag.
        bitArray.skipBits(3);
      }
      bitArray.skipBits(1); // extensionFlag3.
    }
  }

}
