/*
 * Copyright (C) 2017 The Android Open Source Project
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

import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Collections;

/**
 * Parses and extracts samples from an AAC/LATM elementary stream.
 */
public final class LatmReader implements ElementaryStreamReader {

  private static final int STATE_FINDING_SYNC_1 = 0;
  private static final int STATE_FINDING_SYNC_2 = 1;
  private static final int STATE_READING_HEADER = 2;
  private static final int STATE_READING_SAMPLE = 3;

  private static final int INITIAL_BUFFER_SIZE = 1024;
  private static final int SYNC_BYTE_FIRST = 0x56;
  private static final int SYNC_BYTE_SECOND = 0xE0;

  private final String language;
  private final ParsableByteArray sampleDataBuffer;
  private final ParsableBitArray sampleBitArray;

  // Track output info.
  private TrackOutput output;
  private Format format;
  private String formatId;

  // Parser state info.
  private int state;
  private int bytesRead;
  private int sampleSize;
  private int secondHeaderByte;
  private long timeUs;

  // Container data.
  private boolean streamMuxRead;
  private int audioMuxVersionA;
  private int numSubframes;
  private int frameLengthType;
  private boolean otherDataPresent;
  private long otherDataLenBits;
  private int sampleRateHz;
  private long sampleDurationUs;
  private int channelCount;

  /**
   * @param language Track language.
   */
  public LatmReader(@Nullable String language) {
    this.language = language;
    sampleDataBuffer = new ParsableByteArray(INITIAL_BUFFER_SIZE);
    sampleBitArray = new ParsableBitArray(sampleDataBuffer.data);
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC_1;
    streamMuxRead = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
    formatId = idGenerator.getFormatId();
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    timeUs = pesTimeUs;
  }

  @Override
  public void consume(ParsableByteArray data) throws ParserException {
    int bytesToRead;
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC_1:
          if (data.readUnsignedByte() == SYNC_BYTE_FIRST) {
            state = STATE_FINDING_SYNC_2;
          }
          break;
        case STATE_FINDING_SYNC_2:
          int secondByte = data.readUnsignedByte();
          if ((secondByte & SYNC_BYTE_SECOND) == SYNC_BYTE_SECOND) {
            secondHeaderByte = secondByte;
            state = STATE_READING_HEADER;
          } else if (secondByte != SYNC_BYTE_FIRST) {
            state = STATE_FINDING_SYNC_1;
          }
          break;
        case STATE_READING_HEADER:
          sampleSize = ((secondHeaderByte & ~SYNC_BYTE_SECOND) << 8) | data.readUnsignedByte();
          if (sampleSize > sampleDataBuffer.data.length) {
            resetBufferForSize(sampleSize);
          }
          bytesRead = 0;
          state = STATE_READING_SAMPLE;
          break;
        case STATE_READING_SAMPLE:
          bytesToRead = Math.min(data.bytesLeft(), sampleSize - bytesRead);
          data.readBytes(sampleBitArray.data, bytesRead, bytesToRead);
          bytesRead += bytesToRead;
          if (bytesRead == sampleSize) {
            sampleBitArray.setPosition(0);
            parseAudioMuxElement(sampleBitArray);
            state = STATE_FINDING_SYNC_1;
          }
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Parses an AudioMuxElement as defined in 14496-3:2009, Section 1.7.3.1, Table 1.41.
   *
   * @param data A {@link ParsableBitArray} containing the AudioMuxElement's bytes.
   */
  private void parseAudioMuxElement(ParsableBitArray data) throws ParserException {
    boolean useSameStreamMux = data.readBit();
    if (!useSameStreamMux) {
      streamMuxRead = true;
      parseStreamMuxConfig(data);
    } else if (!streamMuxRead) {
      return; // Parsing cannot continue without StreamMuxConfig information.
    }

    if (audioMuxVersionA == 0) {
      if (numSubframes != 0) {
        throw new ParserException();
      }
      int muxSlotLengthBytes = parsePayloadLengthInfo(data);
      parsePayloadMux(data, muxSlotLengthBytes);
      if (otherDataPresent) {
        data.skipBits((int) otherDataLenBits);
      }
    } else {
      throw new ParserException(); // Not defined by ISO/IEC 14496-3:2009.
    }
  }

  /**
   * Parses a StreamMuxConfig as defined in ISO/IEC 14496-3:2009 Section 1.7.3.1, Table 1.42.
   */
  private void parseStreamMuxConfig(ParsableBitArray data) throws ParserException {
    int audioMuxVersion = data.readBits(1);
    audioMuxVersionA = audioMuxVersion == 1 ? data.readBits(1) : 0;
    if (audioMuxVersionA == 0) {
      if (audioMuxVersion == 1) {
        latmGetValue(data); // Skip taraBufferFullness.
      }
      if (!data.readBit()) {
        throw new ParserException();
      }
      numSubframes = data.readBits(6);
      int numProgram = data.readBits(4);
      int numLayer = data.readBits(3);
      if (numProgram != 0 || numLayer != 0) {
        throw new ParserException();
      }
      if (audioMuxVersion == 0) {
        int startPosition = data.getPosition();
        int readBits = parseAudioSpecificConfig(data);
        data.setPosition(startPosition);
        byte[] initData = new byte[(readBits + 7) / 8];
        data.readBits(initData, 0, readBits);
        Format format = Format.createAudioSampleFormat(formatId, MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, channelCount, sampleRateHz,
            Collections.singletonList(initData), null, 0, language);
        if (!format.equals(this.format)) {
          this.format = format;
          sampleDurationUs = (C.MICROS_PER_SECOND * 1024) / format.sampleRate;
          output.format(format);
        }
      } else {
        int ascLen = (int) latmGetValue(data);
        int bitsRead = parseAudioSpecificConfig(data);
        data.skipBits(ascLen - bitsRead); // fillBits.
      }
      parseFrameLength(data);
      otherDataPresent = data.readBit();
      otherDataLenBits = 0;
      if (otherDataPresent) {
        if (audioMuxVersion == 1) {
          otherDataLenBits = latmGetValue(data);
        } else {
          boolean otherDataLenEsc;
          do {
            otherDataLenEsc = data.readBit();
            otherDataLenBits = (otherDataLenBits << 8) + data.readBits(8);
          } while (otherDataLenEsc);
        }
      }
      boolean crcCheckPresent = data.readBit();
      if (crcCheckPresent) {
        data.skipBits(8); // crcCheckSum.
      }
    } else {
      throw new ParserException(); // This is not defined by ISO/IEC 14496-3:2009.
    }
  }

  private void parseFrameLength(ParsableBitArray data) {
    frameLengthType = data.readBits(3);
    switch (frameLengthType) {
      case 0:
        data.skipBits(8); // latmBufferFullness.
        break;
      case 1:
        data.skipBits(9); // frameLength.
        break;
      case 3:
      case 4:
      case 5:
        data.skipBits(6); // CELPframeLengthTableIndex.
        break;
      case 6:
      case 7:
        data.skipBits(1); // HVXCframeLengthTableIndex.
        break;
      default:
        throw new IllegalStateException();
    }
  }

  private int parseAudioSpecificConfig(ParsableBitArray data) throws ParserException {
    int bitsLeft = data.bitsLeft();
    Pair<Integer, Integer> config = CodecSpecificDataUtil.parseAacAudioSpecificConfig(data, true);
    sampleRateHz = config.first;
    channelCount = config.second;
    return bitsLeft - data.bitsLeft();
  }

  private int parsePayloadLengthInfo(ParsableBitArray data) throws ParserException {
    int muxSlotLengthBytes = 0;
    // Assuming single program and single layer.
    if (frameLengthType == 0) {
      int tmp;
      do {
        tmp = data.readBits(8);
        muxSlotLengthBytes += tmp;
      } while (tmp == 255);
      return muxSlotLengthBytes;
    } else {
      throw new ParserException();
    }
  }

  private void parsePayloadMux(ParsableBitArray data, int muxLengthBytes) {
    // The start of sample data in
    int bitPosition = data.getPosition();
    if ((bitPosition & 0x07) == 0) {
      // Sample data is byte-aligned. We can output it directly.
      sampleDataBuffer.setPosition(bitPosition >> 3);
    } else {
      // Sample data is not byte-aligned and we need align it ourselves before outputting.
      // Byte alignment is needed because LATM framing is not supported by MediaCodec.
      data.readBits(sampleDataBuffer.data, 0, muxLengthBytes * 8);
      sampleDataBuffer.setPosition(0);
    }
    output.sampleData(sampleDataBuffer, muxLengthBytes);
    output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, muxLengthBytes, 0, null);
    timeUs += sampleDurationUs;
  }

  private void resetBufferForSize(int newSize) {
    sampleDataBuffer.reset(newSize);
    sampleBitArray.reset(sampleDataBuffer.data);
  }

  private static long latmGetValue(ParsableBitArray data) {
    int bytesForValue = data.readBits(2);
    return data.readBits((bytesForValue + 1) * 8);
  }

}
