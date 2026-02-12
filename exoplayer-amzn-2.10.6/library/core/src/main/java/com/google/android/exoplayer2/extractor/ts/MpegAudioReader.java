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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.MpegAudioHeader;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Parses a continuous MPEG Audio byte stream and extracts individual frames.
 */
public final class MpegAudioReader implements ElementaryStreamReader {

  private static final int STATE_FINDING_HEADER = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_FRAME = 2;

  private static final int HEADER_SIZE = 4;

  private final ParsableByteArray headerScratch;
  private final MpegAudioHeader header;
  private final String language;

  private String formatId;
  private TrackOutput output;

  private int state;
  private int frameBytesRead;
  private boolean hasOutputFormat;

  // Used when finding the frame header.
  private boolean lastByteWasFF;

  // Parsed from the frame header.
  private long frameDurationUs;
  private int frameSize;

  // The timestamp to attach to the next sample in the current packet.
  private long timeUs;

  public MpegAudioReader() {
    this(null);
  }

  public MpegAudioReader(String language) {
    state = STATE_FINDING_HEADER;
    // The first byte of an MPEG Audio frame header is always 0xFF.
    headerScratch = new ParsableByteArray(4);
    headerScratch.data[0] = (byte) 0xFF;
    header = new MpegAudioHeader();
    this.language = language;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_HEADER;
    frameBytesRead = 0;
    lastByteWasFF = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    timeUs = pesTimeUs;
  }

  @Override
  public void consume(ParsableByteArray data) {
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_HEADER:
          findHeader(data);
          break;
        case STATE_READING_HEADER:
          readHeaderRemainder(data);
          break;
        case STATE_READING_FRAME:
          readFrameRemainder(data);
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
   * Attempts to locate the start of the next frame header.
   * <p>
   * If a frame header is located then the state is changed to {@link #STATE_READING_HEADER}, the
   * first two bytes of the header are written into {@link #headerScratch}, and the position of the
   * source is advanced to the byte that immediately follows these two bytes.
   * <p>
   * If a frame header is not located then the position of the source is advanced to the limit, and
   * the method should be called again with the next source to continue the search.
   *
   * @param source The source from which to read.
   */
  private void findHeader(ParsableByteArray source) {
    byte[] data = source.data;
    int startOffset = source.getPosition();
    int endOffset = source.limit();
    for (int i = startOffset; i < endOffset; i++) {
      boolean byteIsFF = (data[i] & 0xFF) == 0xFF;
      boolean found = lastByteWasFF && (data[i] & 0xE0) == 0xE0;
      lastByteWasFF = byteIsFF;
      if (found) {
        source.setPosition(i + 1);
        // Reset lastByteWasFF for next time.
        lastByteWasFF = false;
        headerScratch.data[1] = data[i];
        frameBytesRead = 2;
        state = STATE_READING_HEADER;
        return;
      }
    }
    source.setPosition(endOffset);
  }

  /**
   * Attempts to read the remaining two bytes of the frame header.
   * <p>
   * If a frame header is read in full then the state is changed to {@link #STATE_READING_FRAME},
   * the media format is output if this has not previously occurred, the four header bytes are
   * output as sample data, and the position of the source is advanced to the byte that immediately
   * follows the header.
   * <p>
   * If a frame header is read in full but cannot be parsed then the state is changed to
   * {@link #STATE_READING_HEADER}.
   * <p>
   * If a frame header is not read in full then the position of the source is advanced to the limit,
   * and the method should be called again with the next source to continue the read.
   *
   * @param source The source from which to read.
   */
  private void readHeaderRemainder(ParsableByteArray source) {
    int bytesToRead = Math.min(source.bytesLeft(), HEADER_SIZE - frameBytesRead);
    source.readBytes(headerScratch.data, frameBytesRead, bytesToRead);
    frameBytesRead += bytesToRead;
    if (frameBytesRead < HEADER_SIZE) {
      // We haven't read the whole header yet.
      return;
    }

    headerScratch.setPosition(0);
    boolean parsedHeader = MpegAudioHeader.populateHeader(headerScratch.readInt(), header);
    if (!parsedHeader) {
      // We thought we'd located a frame header, but we hadn't.
      frameBytesRead = 0;
      state = STATE_READING_HEADER;
      return;
    }

    frameSize = header.frameSize;
    if (!hasOutputFormat) {
      frameDurationUs = (C.MICROS_PER_SECOND * header.samplesPerFrame) / header.sampleRate;
      Format format = Format.createAudioSampleFormat(formatId, header.mimeType, null,
          Format.NO_VALUE, MpegAudioHeader.MAX_FRAME_SIZE_BYTES, header.channels, header.sampleRate,
          null, null, 0, language);
      output.format(format);
      hasOutputFormat = true;
    }

    headerScratch.setPosition(0);
    output.sampleData(headerScratch, HEADER_SIZE);
    state = STATE_READING_FRAME;
  }

  /**
   * Attempts to read the remainder of the frame.
   * <p>
   * If a frame is read in full then true is returned. The frame will have been output, and the
   * position of the source will have been advanced to the byte that immediately follows the end of
   * the frame.
   * <p>
   * If a frame is not read in full then the position of the source will have been advanced to the
   * limit, and the method should be called again with the next source to continue the read.
   *
   * @param source The source from which to read.
   */
  private void readFrameRemainder(ParsableByteArray source) {
    int bytesToRead = Math.min(source.bytesLeft(), frameSize - frameBytesRead);
    output.sampleData(source, bytesToRead);
    frameBytesRead += bytesToRead;
    if (frameBytesRead < frameSize) {
      // We haven't read the whole of the frame yet.
      return;
    }

    output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, frameSize, 0, null);
    timeUs += frameDurationUs;
    frameBytesRead = 0;
    state = STATE_FINDING_HEADER;
  }

}
