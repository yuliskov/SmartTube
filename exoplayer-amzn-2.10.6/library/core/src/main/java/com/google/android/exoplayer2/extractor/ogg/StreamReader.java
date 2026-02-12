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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/** StreamReader abstract class. */
@SuppressWarnings("UngroupedOverloads")
/* package */ abstract class StreamReader {

  private static final int STATE_READ_HEADERS = 0;
  private static final int STATE_SKIP_HEADERS = 1;
  private static final int STATE_READ_PAYLOAD = 2;
  private static final int STATE_END_OF_INPUT = 3;

  static class SetupData {
    Format format;
    OggSeeker oggSeeker;
  }

  private final OggPacket oggPacket;

  private TrackOutput trackOutput;
  private ExtractorOutput extractorOutput;
  private OggSeeker oggSeeker;
  private long targetGranule;
  private long payloadStartPosition;
  private long currentGranule;
  private int state;
  private int sampleRate;
  private SetupData setupData;
  private long lengthOfReadPacket;
  private boolean seekMapSet;
  private boolean formatSet;

  public StreamReader() {
    oggPacket = new OggPacket();
  }

  void init(ExtractorOutput output, TrackOutput trackOutput) {
    this.extractorOutput = output;
    this.trackOutput = trackOutput;
    reset(true);
  }

  /**
   * Resets the state of the {@link StreamReader}.
   *
   * @param headerData Resets parsed header data too.
   */
  protected void reset(boolean headerData) {
    if (headerData) {
      setupData = new SetupData();
      payloadStartPosition = 0;
      state = STATE_READ_HEADERS;
    } else {
      state = STATE_SKIP_HEADERS;
    }
    targetGranule = -1;
    currentGranule = 0;
  }

  /**
   * @see Extractor#seek(long, long)
   */
  final void seek(long position, long timeUs) {
    oggPacket.reset();
    if (position == 0) {
      reset(!seekMapSet);
    } else {
      if (state != STATE_READ_HEADERS) {
        targetGranule = convertTimeToGranule(timeUs);
        oggSeeker.startSeek(targetGranule);
        state = STATE_READ_PAYLOAD;
      }
    }
  }

  /**
   * @see Extractor#read(ExtractorInput, PositionHolder)
   */
  final int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    switch (state) {
      case STATE_READ_HEADERS:
        return readHeaders(input);
      case STATE_SKIP_HEADERS:
        input.skipFully((int) payloadStartPosition);
        state = STATE_READ_PAYLOAD;
        return Extractor.RESULT_CONTINUE;
      case STATE_READ_PAYLOAD:
        return readPayload(input, seekPosition);
      default:
        // Never happens.
        throw new IllegalStateException();
    }
  }

  private int readHeaders(ExtractorInput input) throws IOException, InterruptedException {
    boolean readingHeaders = true;
    while (readingHeaders) {
      if (!oggPacket.populate(input)) {
        state = STATE_END_OF_INPUT;
        return Extractor.RESULT_END_OF_INPUT;
      }
      lengthOfReadPacket = input.getPosition() - payloadStartPosition;

      readingHeaders = readHeaders(oggPacket.getPayload(), payloadStartPosition, setupData);
      if (readingHeaders) {
        payloadStartPosition = input.getPosition();
      }
    }

    sampleRate = setupData.format.sampleRate;
    if (!formatSet) {
      trackOutput.format(setupData.format);
      formatSet = true;
    }

    if (setupData.oggSeeker != null) {
      oggSeeker = setupData.oggSeeker;
    } else if (input.getLength() == C.LENGTH_UNSET) {
      oggSeeker = new UnseekableOggSeeker();
    } else {
      OggPageHeader firstPayloadPageHeader = oggPacket.getPageHeader();
      boolean isLastPage = (firstPayloadPageHeader.type & 0x04) != 0; // Type 4 is end of stream.
      oggSeeker =
          new DefaultOggSeeker(
              this,
              payloadStartPosition,
              input.getLength(),
              firstPayloadPageHeader.headerSize + firstPayloadPageHeader.bodySize,
              firstPayloadPageHeader.granulePosition,
              isLastPage);
    }

    setupData = null;
    state = STATE_READ_PAYLOAD;
    // First payload packet. Trim the payload array of the ogg packet after headers have been read.
    oggPacket.trimPayload();
    return Extractor.RESULT_CONTINUE;
  }

  private int readPayload(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    long position = oggSeeker.read(input);
    if (position >= 0) {
      seekPosition.position = position;
      return Extractor.RESULT_SEEK;
    } else if (position < -1) {
      onSeekEnd(-(position + 2));
    }
    if (!seekMapSet) {
      SeekMap seekMap = oggSeeker.createSeekMap();
      extractorOutput.seekMap(seekMap);
      seekMapSet = true;
    }

    if (lengthOfReadPacket > 0 || oggPacket.populate(input)) {
      lengthOfReadPacket = 0;
      ParsableByteArray payload = oggPacket.getPayload();
      long granulesInPacket = preparePayload(payload);
      if (granulesInPacket >= 0 && currentGranule + granulesInPacket >= targetGranule) {
        // calculate time and send payload data to codec
        long timeUs = convertGranuleToTime(currentGranule);
        trackOutput.sampleData(payload, payload.limit());
        trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, payload.limit(), 0, null);
        targetGranule = -1;
      }
      currentGranule += granulesInPacket;
    } else {
      state = STATE_END_OF_INPUT;
      return Extractor.RESULT_END_OF_INPUT;
    }
    return Extractor.RESULT_CONTINUE;
  }

  /**
   * Converts granule value to time.
   *
   * @param granule The granule value.
   * @return Time in milliseconds.
   */
  protected long convertGranuleToTime(long granule) {
    return (granule * C.MICROS_PER_SECOND) / sampleRate;
  }

  /**
   * Converts time value to granule.
   *
   * @param timeUs Time in milliseconds.
   * @return The granule value.
   */
  protected long convertTimeToGranule(long timeUs) {
    return (sampleRate * timeUs) / C.MICROS_PER_SECOND;
  }

  /**
   * Prepares payload data in the packet for submitting to TrackOutput and returns number of
   * granules in the packet.
   *
   * @param packet Ogg payload data packet.
   * @return Number of granules in the packet or -1 if the packet doesn't contain payload data.
   */
  protected abstract long preparePayload(ParsableByteArray packet);

  /**
   * Checks if the given packet is a header packet and reads it.
   *
   * @param packet An ogg packet.
   * @param position Position of the given header packet.
   * @param setupData Setup data to be filled.
   * @return Whether the packet contains header data.
   */
  protected abstract boolean readHeaders(ParsableByteArray packet, long position,
      SetupData setupData) throws IOException, InterruptedException;

  /**
   * Called on end of seeking.
   *
   * @param currentGranule The granule at the current input position.
   */
  protected void onSeekEnd(long currentGranule) {
    this.currentGranule = currentGranule;
  }

  private static final class UnseekableOggSeeker implements OggSeeker {

    @Override
    public long read(ExtractorInput input) {
      return -1;
    }

    @Override
    public void startSeek(long targetGranule) {
      // Do nothing.
    }

    @Override
    public SeekMap createSeekMap() {
      return new SeekMap.Unseekable(C.TIME_UNSET);
    }

  }

}
