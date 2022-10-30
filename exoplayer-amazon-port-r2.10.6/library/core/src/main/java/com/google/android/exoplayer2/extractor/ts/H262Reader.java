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
package com.google.android.exoplayer2.extractor.ts;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Arrays;
import java.util.Collections;

/**
 * Parses a continuous H262 byte stream and extracts individual frames.
 */
public final class H262Reader implements ElementaryStreamReader {

  private static final int START_PICTURE = 0x00;
  private static final int START_SEQUENCE_HEADER = 0xB3;
  private static final int START_EXTENSION = 0xB5;
  private static final int START_GROUP = 0xB8;
  private static final int START_USER_DATA = 0xB2;

  private String formatId;
  private TrackOutput output;

  // Maps (frame_rate_code - 1) indices to values, as defined in ITU-T H.262 Table 6-4.
  private static final double[] FRAME_RATE_VALUES = new double[] {
      24000d / 1001, 24, 25, 30000d / 1001, 30, 50, 60000d / 1001, 60};

  // State that should not be reset on seek.
  private boolean hasOutputFormat;
  private long frameDurationUs;

  private final UserDataReader userDataReader;
  private final ParsableByteArray userDataParsable;

  // State that should be reset on seek.
  private final boolean[] prefixFlags;
  private final CsdBuffer csdBuffer;
  private final NalUnitTargetBuffer userData;
  private long totalBytesWritten;
  private boolean startedFirstSample;

  // Per packet state that gets reset at the start of each packet.
  private long pesTimeUs;

  // Per sample state that gets reset at the start of each sample.
  private long samplePosition;
  private long sampleTimeUs;
  private boolean sampleIsKeyframe;
  private boolean sampleHasPicture;

  public H262Reader() {
    this(null);
  }

  public H262Reader(UserDataReader userDataReader) {
    this.userDataReader = userDataReader;
    prefixFlags = new boolean[4];
    csdBuffer = new CsdBuffer(128);
    if (userDataReader != null) {
      userData = new NalUnitTargetBuffer(START_USER_DATA, 128);
      userDataParsable = new ParsableByteArray();
    } else {
      userData = null;
      userDataParsable = null;
    }
  }

  @Override
  public void seek() {
    NalUnitUtil.clearPrefixFlags(prefixFlags);
    csdBuffer.reset();
    if (userDataReader != null) {
      userData.reset();
    }
    totalBytesWritten = 0;
    startedFirstSample = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_VIDEO);
    if (userDataReader != null) {
      userDataReader.createTracks(extractorOutput, idGenerator);
    }
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    // TODO (Internal b/32267012): Consider using random access indicator.
    this.pesTimeUs = pesTimeUs;
  }

  @Override
  public void consume(ParsableByteArray data) {
    int offset = data.getPosition();
    int limit = data.limit();
    byte[] dataArray = data.data;

    // Append the data to the buffer.
    totalBytesWritten += data.bytesLeft();
    output.sampleData(data, data.bytesLeft());

    while (true) {
      int startCodeOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, prefixFlags);

      if (startCodeOffset == limit) {
        // We've scanned to the end of the data without finding another start code.
        if (!hasOutputFormat) {
          csdBuffer.onData(dataArray, offset, limit);
        }
        if (userDataReader != null) {
          userData.appendToNalUnit(dataArray, offset, limit);
        }
        return;
      }

      // We've found a start code with the following value.
      int startCodeValue = data.data[startCodeOffset + 3] & 0xFF;
      // This is the number of bytes from the current offset to the start of the next start
      // code. It may be negative if the start code started in the previously consumed data.
      int lengthToStartCode = startCodeOffset - offset;

      if (!hasOutputFormat) {
        if (lengthToStartCode > 0) {
          csdBuffer.onData(dataArray, offset, startCodeOffset);
        }
        // This is the number of bytes belonging to the next start code that have already been
        // passed to csdBuffer.
        int bytesAlreadyPassed = lengthToStartCode < 0 ? -lengthToStartCode : 0;
        if (csdBuffer.onStartCode(startCodeValue, bytesAlreadyPassed)) {
          // The csd data is complete, so we can decode and output the media format.
          Pair<Format, Long> result = parseCsdBuffer(csdBuffer, formatId);
          output.format(result.first);
          frameDurationUs = result.second;
          hasOutputFormat = true;
        }
      }
      if (userDataReader != null) {
        int bytesAlreadyPassed = 0;
        if (lengthToStartCode > 0) {
          userData.appendToNalUnit(dataArray, offset, startCodeOffset);
        } else {
          bytesAlreadyPassed = -lengthToStartCode;
        }

        if (userData.endNalUnit(bytesAlreadyPassed)) {
          int unescapedLength = NalUnitUtil.unescapeStream(userData.nalData, userData.nalLength);
          userDataParsable.reset(userData.nalData, unescapedLength);
          userDataReader.consume(sampleTimeUs, userDataParsable);
        }

        if (startCodeValue == START_USER_DATA && data.data[startCodeOffset + 2] == 0x1) {
          userData.startNalUnit(startCodeValue);
        }
      }
      if (startCodeValue == START_PICTURE || startCodeValue == START_SEQUENCE_HEADER) {
        int bytesWrittenPastStartCode = limit - startCodeOffset;
        if (startedFirstSample && sampleHasPicture && hasOutputFormat) {
          // Output the sample.
          @C.BufferFlags int flags = sampleIsKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
          int size = (int) (totalBytesWritten - samplePosition) - bytesWrittenPastStartCode;
          output.sampleMetadata(sampleTimeUs, flags, size, bytesWrittenPastStartCode, null);
        }
        if (!startedFirstSample || sampleHasPicture) {
          // Start the next sample.
          samplePosition = totalBytesWritten - bytesWrittenPastStartCode;
          sampleTimeUs = pesTimeUs != C.TIME_UNSET ? pesTimeUs
              : (startedFirstSample ? (sampleTimeUs + frameDurationUs) : 0);
          sampleIsKeyframe = false;
          pesTimeUs = C.TIME_UNSET;
          startedFirstSample = true;
        }
        sampleHasPicture = startCodeValue == START_PICTURE;
      } else if (startCodeValue == START_GROUP) {
        sampleIsKeyframe = true;
      }

      offset = startCodeOffset + 3;
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Parses the {@link Format} and frame duration from a csd buffer.
   *
   * @param csdBuffer The csd buffer.
   * @param formatId The id for the generated format. May be null.
   * @return A pair consisting of the {@link Format} and the frame duration in microseconds, or
   *     0 if the duration could not be determined.
   */
  private static Pair<Format, Long> parseCsdBuffer(CsdBuffer csdBuffer, String formatId) {
    byte[] csdData = Arrays.copyOf(csdBuffer.data, csdBuffer.length);

    int firstByte = csdData[4] & 0xFF;
    int secondByte = csdData[5] & 0xFF;
    int thirdByte = csdData[6] & 0xFF;
    int width = (firstByte << 4) | (secondByte >> 4);
    int height = (secondByte & 0x0F) << 8 | thirdByte;

    float pixelWidthHeightRatio = 1f;
    int aspectRatioCode = (csdData[7] & 0xF0) >> 4;
    switch(aspectRatioCode) {
      case 2:
        pixelWidthHeightRatio = (4 * height) / (float) (3 * width);
        break;
      case 3:
        pixelWidthHeightRatio = (16 * height) / (float) (9 * width);
        break;
      case 4:
        pixelWidthHeightRatio = (121 * height) / (float) (100 * width);
        break;
      default:
        // Do nothing.
        break;
    }

    Format format = Format.createVideoSampleFormat(formatId, MimeTypes.VIDEO_MPEG2, null,
        Format.NO_VALUE, Format.NO_VALUE, width, height, Format.NO_VALUE,
        Collections.singletonList(csdData), Format.NO_VALUE, pixelWidthHeightRatio, null);

    long frameDurationUs = 0;
    int frameRateCodeMinusOne = (csdData[7] & 0x0F) - 1;
    if (0 <= frameRateCodeMinusOne && frameRateCodeMinusOne < FRAME_RATE_VALUES.length) {
      double frameRate = FRAME_RATE_VALUES[frameRateCodeMinusOne];
      int sequenceExtensionPosition = csdBuffer.sequenceExtensionPosition;
      int frameRateExtensionN = (csdData[sequenceExtensionPosition + 9] & 0x60) >> 5;
      int frameRateExtensionD = (csdData[sequenceExtensionPosition + 9] & 0x1F);
      if (frameRateExtensionN != frameRateExtensionD) {
        frameRate *= (frameRateExtensionN + 1d) / (frameRateExtensionD + 1);
      }
      frameDurationUs = (long) (C.MICROS_PER_SECOND / frameRate);
    }

    return Pair.create(format, frameDurationUs);
  }

  private static final class CsdBuffer {

    private static final byte[] START_CODE = new byte[] {0, 0, 1};

    private boolean isFilling;

    public int length;
    public int sequenceExtensionPosition;
    public byte[] data;

    public CsdBuffer(int initialCapacity) {
      data = new byte[initialCapacity];
    }

    /**
     * Resets the buffer, clearing any data that it holds.
     */
    public void reset() {
      isFilling = false;
      length = 0;
      sequenceExtensionPosition = 0;
    }

    /**
     * Called when a start code is encountered in the stream.
     *
     * @param startCodeValue The start code value.
     * @param bytesAlreadyPassed The number of bytes of the start code that have been passed to
     *     {@link #onData(byte[], int, int)}, or 0.
     * @return Whether the csd data is now complete. If true is returned, neither
     *     this method nor {@link #onData(byte[], int, int)} should be called again without an
     *     interleaving call to {@link #reset()}.
     */
    public boolean onStartCode(int startCodeValue, int bytesAlreadyPassed) {
      if (isFilling) {
        length -= bytesAlreadyPassed;
        if (sequenceExtensionPosition == 0 && startCodeValue == START_EXTENSION) {
          sequenceExtensionPosition = length;
        } else {
          isFilling = false;
          return true;
        }
      } else if (startCodeValue == START_SEQUENCE_HEADER) {
        isFilling = true;
      }
      onData(START_CODE, 0, START_CODE.length);
      return false;
    }

    /**
     * Called to pass stream data.
     *
     * @param newData Holds the data being passed.
     * @param offset The offset of the data in {@code data}.
     * @param limit The limit (exclusive) of the data in {@code data}.
     */
    public void onData(byte[] newData, int offset, int limit) {
      if (!isFilling) {
        return;
      }
      int readLength = limit - offset;
      if (data.length < length + readLength) {
        data = Arrays.copyOf(data, (length + readLength) * 2);
      }
      System.arraycopy(newData, offset, data, length, readLength);
      length += readLength;
    }

  }

}
