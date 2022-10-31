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

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.Ac3Util;
import com.google.android.exoplayer2.audio.Ac3Util.SyncFrameInfo;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Parses a continuous (E-)AC-3 byte stream and extracts individual samples.
 */
public final class Ac3Reader implements ElementaryStreamReader {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_FINDING_SYNC, STATE_READING_HEADER, STATE_READING_SAMPLE})
  private @interface State {}

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  private static final int HEADER_SIZE = 128;

  private final ParsableBitArray headerScratchBits;
  private final ParsableByteArray headerScratchBytes;
  private final String language;

  private String trackFormatId;
  private TrackOutput output;

  @State private int state;
  private int bytesRead;

  // Used to find the header.
  private boolean lastByteWas0B;

  // Used when parsing the header.
  private long sampleDurationUs;
  private Format format;
  private int sampleSize;

  // Used when reading the samples.
  private long timeUs;

  /**
   * Constructs a new reader for (E-)AC-3 elementary streams.
   */
  public Ac3Reader() {
    this(null);
  }

  /**
   * Constructs a new reader for (E-)AC-3 elementary streams.
   *
   * @param language Track language.
   */
  public Ac3Reader(String language) {
    headerScratchBits = new ParsableBitArray(new byte[HEADER_SIZE]);
    headerScratchBytes = new ParsableByteArray(headerScratchBits.data);
    state = STATE_FINDING_SYNC;
    this.language = language;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    lastByteWas0B = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator generator) {
    generator.generateNewId();
    trackFormatId = generator.getFormatId();
    output = extractorOutput.track(generator.getTrackId(), C.TRACK_TYPE_AUDIO);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    timeUs = pesTimeUs;
  }

  @Override
  public void consume(ParsableByteArray data) {
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC:
          if (skipToNextSync(data)) {
            state = STATE_READING_HEADER;
            headerScratchBytes.data[0] = 0x0B;
            headerScratchBytes.data[1] = 0x77;
            bytesRead = 2;
          }
          break;
        case STATE_READING_HEADER:
          if (continueRead(data, headerScratchBytes.data, HEADER_SIZE)) {
            parseHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, HEADER_SIZE);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_READING_SAMPLE:
          int bytesToRead = Math.min(data.bytesLeft(), sampleSize - bytesRead);
          output.sampleData(data, bytesToRead);
          bytesRead += bytesToRead;
          if (bytesRead == sampleSize) {
            output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
            timeUs += sampleDurationUs;
            state = STATE_FINDING_SYNC;
          }
          break;
        default:
          break;
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
   * that the data should be written into {@code target} starting from an offset of zero.
   *
   * @param source The source from which to read.
   * @param target The target into which data is to be read.
   * @param targetLength The target length of the read.
   * @return Whether the target length was reached.
   */
  private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
    int bytesToRead = Math.min(source.bytesLeft(), targetLength - bytesRead);
    source.readBytes(target, bytesRead, bytesToRead);
    bytesRead += bytesToRead;
    return bytesRead == targetLength;
  }

  /**
   * Locates the next syncword, advancing the position to the byte that immediately follows it. If a
   * syncword was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   * @return Whether a syncword position was found.
   */
  private boolean skipToNextSync(ParsableByteArray pesBuffer) {
    while (pesBuffer.bytesLeft() > 0) {
      if (!lastByteWas0B) {
        lastByteWas0B = pesBuffer.readUnsignedByte() == 0x0B;
        continue;
      }
      int secondByte = pesBuffer.readUnsignedByte();
      if (secondByte == 0x77) {
        lastByteWas0B = false;
        return true;
      } else {
        lastByteWas0B = secondByte == 0x0B;
      }
    }
    return false;
  }

  /**
   * Parses the sample header.
   */
  @SuppressWarnings("ReferenceEquality")
  private void parseHeader() {
    headerScratchBits.setPosition(0);
    SyncFrameInfo frameInfo = Ac3Util.parseAc3SyncframeInfo(headerScratchBits);
    if (format == null || frameInfo.channelCount != format.channelCount
        || frameInfo.sampleRate != format.sampleRate
        || frameInfo.mimeType != format.sampleMimeType) {
      format = Format.createAudioSampleFormat(trackFormatId, frameInfo.mimeType, null,
          Format.NO_VALUE, Format.NO_VALUE, frameInfo.channelCount, frameInfo.sampleRate, null,
          null, 0, language);
      output.format(format);
    }
    sampleSize = frameInfo.frameSize;
    // In this class a sample is an access unit (syncframe in AC-3), but Format#sampleRate
    // specifies the number of PCM audio samples per second.
    sampleDurationUs = C.MICROS_PER_SECOND * frameInfo.sampleCount / format.sampleRate;
  }

}
