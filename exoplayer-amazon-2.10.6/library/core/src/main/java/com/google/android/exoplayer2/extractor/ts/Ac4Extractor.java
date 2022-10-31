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
package com.google.android.exoplayer2.extractor.ts;

import static com.google.android.exoplayer2.audio.Ac4Util.AC40_SYNCWORD;
import static com.google.android.exoplayer2.audio.Ac4Util.AC41_SYNCWORD;
import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.Ac4Util;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/** Extracts data from AC-4 bitstreams. */
public final class Ac4Extractor implements Extractor {

  /** Factory for {@link Ac4Extractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new Ac4Extractor()};

  /**
   * The maximum number of bytes to search when sniffing, excluding ID3 information, before giving
   * up.
   */
  private static final int MAX_SNIFF_BYTES = 8 * 1024;

  /**
   * The size of the reading buffer, in bytes. This value is determined based on the maximum frame
   * size used in broadcast applications.
   */
  private static final int READ_BUFFER_SIZE = 16384;

  /** The size of the frame header, in bytes. */
  private static final int FRAME_HEADER_SIZE = 7;

  private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");

  private final long firstSampleTimestampUs;
  private final Ac4Reader reader;
  private final ParsableByteArray sampleData;

  private boolean startedPacket;

  /** Creates a new extractor for AC-4 bitstreams. */
  public Ac4Extractor() {
    this(/* firstSampleTimestampUs= */ 0);
  }

  /** Creates a new extractor for AC-4 bitstreams, using the specified first sample timestamp. */
  public Ac4Extractor(long firstSampleTimestampUs) {
    this.firstSampleTimestampUs = firstSampleTimestampUs;
    reader = new Ac4Reader();
    sampleData = new ParsableByteArray(READ_BUFFER_SIZE);
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    // Skip any ID3 headers.
    ParsableByteArray scratch = new ParsableByteArray(10);
    int startPosition = 0;
    while (true) {
      input.peekFully(scratch.data, /* offset= */ 0, /* length= */ 10);
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() != ID3_TAG) {
        break;
      }
      scratch.skipBytes(3); // version, flags
      int length = scratch.readSynchSafeInt();
      startPosition += 10 + length;
      input.advancePeekPosition(length);
    }
    input.resetPeekPosition();
    input.advancePeekPosition(startPosition);

    int headerPosition = startPosition;
    int validFramesCount = 0;
    while (true) {
      input.peekFully(scratch.data, /* offset= */ 0, /* length= */ FRAME_HEADER_SIZE);
      scratch.setPosition(0);
      int syncBytes = scratch.readUnsignedShort();
      if (syncBytes != AC40_SYNCWORD && syncBytes != AC41_SYNCWORD) {
        validFramesCount = 0;
        input.resetPeekPosition();
        if (++headerPosition - startPosition >= MAX_SNIFF_BYTES) {
          return false;
        }
        input.advancePeekPosition(headerPosition);
      } else {
        if (++validFramesCount >= 4) {
          return true;
        }
        int frameSize = Ac4Util.parseAc4SyncframeSize(scratch.data, syncBytes);
        if (frameSize == C.LENGTH_UNSET) {
          return false;
        }
        input.advancePeekPosition(frameSize - FRAME_HEADER_SIZE);
      }
    }
  }

  @Override
  public void init(ExtractorOutput output) {
    reader.createTracks(
        output, new TrackIdGenerator(/* firstTrackId= */ 0, /* trackIdIncrement= */ 1));
    output.endTracks();
    output.seekMap(new SeekMap.Unseekable(/* durationUs= */ C.TIME_UNSET));
  }

  @Override
  public void seek(long position, long timeUs) {
    startedPacket = false;
    reader.seek();
  }

  @Override
  public void release() {
    // Do nothing.
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    int bytesRead = input.read(sampleData.data, /* offset= */ 0, /* length= */ READ_BUFFER_SIZE);
    if (bytesRead == C.RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }

    // Feed whatever data we have to the reader, regardless of whether the read finished or not.
    sampleData.setPosition(0);
    sampleData.setLimit(bytesRead);

    if (!startedPacket) {
      // Pass data to the reader as though it's contained within a single infinitely long packet.
      reader.packetStarted(firstSampleTimestampUs, FLAG_DATA_ALIGNMENT_INDICATOR);
      startedPacket = true;
    }
    // TODO: Make it possible for the reader to consume the dataSource directly, so that it becomes
    // unnecessary to copy the data through packetBuffer.
    reader.consume(sampleData);
    return RESULT_CONTINUE;
  }
}
