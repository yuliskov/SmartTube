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

import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ConstantBitrateSeekMap;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Extracts data from AAC bit streams with ADTS framing.
 */
public final class AdtsExtractor implements Extractor {

  /** Factory for {@link AdtsExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new AdtsExtractor()};

  /**
   * Flags controlling the behavior of the extractor. Possible flag value is {@link
   * #FLAG_ENABLE_CONSTANT_BITRATE_SEEKING}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {FLAG_ENABLE_CONSTANT_BITRATE_SEEKING})
  public @interface Flags {}
  /**
   * Flag to force enable seeking using a constant bitrate assumption in cases where seeking would
   * otherwise not be possible.
   *
   * <p>Note that this approach may result in approximated stream duration and seek position that
   * are not precise, especially when the stream bitrate varies a lot.
   */
  public static final int FLAG_ENABLE_CONSTANT_BITRATE_SEEKING = 1;

  private static final int MAX_PACKET_SIZE = 2 * 1024;
  private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
  /**
   * The maximum number of bytes to search when sniffing, excluding the header, before giving up.
   * Frame sizes are represented by 13-bit fields, so expect a valid frame in the first 8192 bytes.
   */
  private static final int MAX_SNIFF_BYTES = 8 * 1024;
  /**
   * The maximum number of frames to use when calculating the average frame size for constant
   * bitrate seeking.
   */
  private static final int NUM_FRAMES_FOR_AVERAGE_FRAME_SIZE = 1000;

  private final @Flags int flags;

  private final AdtsReader reader;
  private final ParsableByteArray packetBuffer;
  private final ParsableByteArray scratch;
  private final ParsableBitArray scratchBits;
  private final long firstStreamSampleTimestampUs;

  private @Nullable ExtractorOutput extractorOutput;

  private long firstSampleTimestampUs;
  private long firstFramePosition;
  private int averageFrameSize;
  private boolean hasCalculatedAverageFrameSize;
  private boolean startedPacket;
  private boolean hasOutputSeekMap;

  public AdtsExtractor() {
    this(0);
  }

  public AdtsExtractor(long firstStreamSampleTimestampUs) {
    this(/* firstStreamSampleTimestampUs= */ firstStreamSampleTimestampUs, /* flags= */ 0);
  }

  /**
   * @param firstStreamSampleTimestampUs The timestamp to be used for the first sample of the stream
   *     output from this extractor.
   * @param flags Flags that control the extractor's behavior.
   */
  public AdtsExtractor(long firstStreamSampleTimestampUs, @Flags int flags) {
    this.firstStreamSampleTimestampUs = firstStreamSampleTimestampUs;
    this.firstSampleTimestampUs = firstStreamSampleTimestampUs;
    this.flags = flags;
    reader = new AdtsReader(true);
    packetBuffer = new ParsableByteArray(MAX_PACKET_SIZE);
    averageFrameSize = C.LENGTH_UNSET;
    firstFramePosition = C.POSITION_UNSET;
    scratch = new ParsableByteArray(10);
    scratchBits = new ParsableBitArray(scratch.data);
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    // Skip any ID3 headers.
    int startPosition = peekId3Header(input);

    // Try to find four or more consecutive AAC audio frames, exceeding the MPEG TS packet size.
    int headerPosition = startPosition;
    int totalValidFramesSize = 0;
    int validFramesCount = 0;
    while (true) {
      input.peekFully(scratch.data, 0, 2);
      scratch.setPosition(0);
      int syncBytes = scratch.readUnsignedShort();
      if (!AdtsReader.isAdtsSyncWord(syncBytes)) {
        validFramesCount = 0;
        totalValidFramesSize = 0;
        input.resetPeekPosition();
        if (++headerPosition - startPosition >= MAX_SNIFF_BYTES) {
          return false;
        }
        input.advancePeekPosition(headerPosition);
      } else {
        if (++validFramesCount >= 4 && totalValidFramesSize > TsExtractor.TS_PACKET_SIZE) {
          return true;
        }

        // Skip the frame.
        input.peekFully(scratch.data, 0, 4);
        scratchBits.setPosition(14);
        int frameSize = scratchBits.readBits(13);
        // Either the stream is malformed OR we're not parsing an ADTS stream.
        if (frameSize <= 6) {
          return false;
        }
        input.advancePeekPosition(frameSize - 6);
        totalValidFramesSize += frameSize;
      }
    }
  }

  @Override
  public void init(ExtractorOutput output) {
    this.extractorOutput = output;
    reader.createTracks(output, new TrackIdGenerator(0, 1));
    output.endTracks();
  }

  @Override
  public void seek(long position, long timeUs) {
    startedPacket = false;
    reader.seek();
    firstSampleTimestampUs = firstStreamSampleTimestampUs + timeUs;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    long inputLength = input.getLength();
    boolean canUseConstantBitrateSeeking =
        (flags & FLAG_ENABLE_CONSTANT_BITRATE_SEEKING) != 0 && inputLength != C.LENGTH_UNSET;
    if (canUseConstantBitrateSeeking) {
      calculateAverageFrameSize(input);
    }

    int bytesRead = input.read(packetBuffer.data, 0, MAX_PACKET_SIZE);
    boolean readEndOfStream = bytesRead == RESULT_END_OF_INPUT;
    maybeOutputSeekMap(inputLength, canUseConstantBitrateSeeking, readEndOfStream);
    if (readEndOfStream) {
      return RESULT_END_OF_INPUT;
    }

    // Feed whatever data we have to the reader, regardless of whether the read finished or not.
    packetBuffer.setPosition(0);
    packetBuffer.setLimit(bytesRead);

    if (!startedPacket) {
      // Pass data to the reader as though it's contained within a single infinitely long packet.
      reader.packetStarted(firstSampleTimestampUs, FLAG_DATA_ALIGNMENT_INDICATOR);
      startedPacket = true;
    }
    // TODO: Make it possible for reader to consume the dataSource directly, so that it becomes
    // unnecessary to copy the data through packetBuffer.
    reader.consume(packetBuffer);
    return RESULT_CONTINUE;
  }

  private int peekId3Header(ExtractorInput input) throws IOException, InterruptedException {
    int firstFramePosition = 0;
    while (true) {
      input.peekFully(scratch.data, 0, 10);
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() != ID3_TAG) {
        break;
      }
      scratch.skipBytes(3);
      int length = scratch.readSynchSafeInt();
      firstFramePosition += 10 + length;
      input.advancePeekPosition(length);
    }
    input.resetPeekPosition();
    input.advancePeekPosition(firstFramePosition);
    if (this.firstFramePosition == C.POSITION_UNSET) {
      this.firstFramePosition = firstFramePosition;
    }
    return firstFramePosition;
  }

  private void maybeOutputSeekMap(
      long inputLength, boolean canUseConstantBitrateSeeking, boolean readEndOfStream) {
    if (hasOutputSeekMap) {
      return;
    }
    boolean useConstantBitrateSeeking = canUseConstantBitrateSeeking && averageFrameSize > 0;
    if (useConstantBitrateSeeking
        && reader.getSampleDurationUs() == C.TIME_UNSET
        && !readEndOfStream) {
      // Wait for the sampleDurationUs to be available, or for the end of the stream to be reached,
      // before creating seek map.
      return;
    }

    ExtractorOutput extractorOutput = Assertions.checkNotNull(this.extractorOutput);
    if (useConstantBitrateSeeking && reader.getSampleDurationUs() != C.TIME_UNSET) {
      extractorOutput.seekMap(getConstantBitrateSeekMap(inputLength));
    } else {
      extractorOutput.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
    }
    hasOutputSeekMap = true;
  }

  private void calculateAverageFrameSize(ExtractorInput input)
      throws IOException, InterruptedException {
    if (hasCalculatedAverageFrameSize) {
      return;
    }
    averageFrameSize = C.LENGTH_UNSET;
    input.resetPeekPosition();
    if (input.getPosition() == 0) {
      // Skip any ID3 headers.
      peekId3Header(input);
    }

    int numValidFrames = 0;
    long totalValidFramesSize = 0;
    while (input.peekFully(
        scratch.data, /* offset= */ 0, /* length= */ 2, /* allowEndOfInput= */ true)) {
      scratch.setPosition(0);
      int syncBytes = scratch.readUnsignedShort();
      if (!AdtsReader.isAdtsSyncWord(syncBytes)) {
        // Invalid sync byte pattern.
        // Constant bit-rate seeking will probably fail for this stream.
        numValidFrames = 0;
        break;
      } else {
        // Read the frame size.
        if (!input.peekFully(
            scratch.data, /* offset= */ 0, /* length= */ 4, /* allowEndOfInput= */ true)) {
          break;
        }
        scratchBits.setPosition(14);
        int currentFrameSize = scratchBits.readBits(13);
        // Either the stream is malformed OR we're not parsing an ADTS stream.
        if (currentFrameSize <= 6) {
          hasCalculatedAverageFrameSize = true;
          throw new ParserException("Malformed ADTS stream");
        }
        totalValidFramesSize += currentFrameSize;
        if (++numValidFrames == NUM_FRAMES_FOR_AVERAGE_FRAME_SIZE) {
          break;
        }
        if (!input.advancePeekPosition(currentFrameSize - 6, /* allowEndOfInput= */ true)) {
          break;
        }
      }
    }
    input.resetPeekPosition();
    if (numValidFrames > 0) {
      averageFrameSize = (int) (totalValidFramesSize / numValidFrames);
    } else {
      averageFrameSize = C.LENGTH_UNSET;
    }
    hasCalculatedAverageFrameSize = true;
  }

  private SeekMap getConstantBitrateSeekMap(long inputLength) {
    int bitrate = getBitrateFromFrameSize(averageFrameSize, reader.getSampleDurationUs());
    return new ConstantBitrateSeekMap(inputLength, firstFramePosition, bitrate, averageFrameSize);
  }

  /**
   * Returns the stream bitrate, given a frame size and the duration of that frame in microseconds.
   *
   * @param frameSize The size of each frame in the stream.
   * @param durationUsPerFrame The duration of the given frame in microseconds.
   * @return The stream bitrate.
   */
  private static int getBitrateFromFrameSize(int frameSize, long durationUsPerFrame) {
    return (int) ((frameSize * C.BITS_PER_BYTE * C.MICROS_PER_SECOND) / durationUsPerFrame);
  }
}
