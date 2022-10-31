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

import android.util.SparseArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.io.IOException;

/**
 * Extracts data from the MPEG-2 PS container format.
 */
public final class PsExtractor implements Extractor {

  /** Factory for {@link PsExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new PsExtractor()};

  /* package */ static final int PACK_START_CODE = 0x000001BA;
  /* package */ static final int SYSTEM_HEADER_START_CODE = 0x000001BB;
  /* package */ static final int PACKET_START_CODE_PREFIX = 0x000001;
  /* package */ static final int MPEG_PROGRAM_END_CODE = 0x000001B9;
  private static final int MAX_STREAM_ID_PLUS_ONE = 0x100;

  // Max search length for first audio and video track in input data.
  private static final long MAX_SEARCH_LENGTH = 1024 * 1024;
  // Max search length for additional audio and video tracks in input data after at least one audio
  // and video track has been found.
  private static final long MAX_SEARCH_LENGTH_AFTER_AUDIO_AND_VIDEO_FOUND = 8 * 1024;

  public static final int PRIVATE_STREAM_1 = 0xBD;
  public static final int AUDIO_STREAM = 0xC0;
  public static final int AUDIO_STREAM_MASK = 0xE0;
  public static final int VIDEO_STREAM = 0xE0;
  public static final int VIDEO_STREAM_MASK = 0xF0;

  private final TimestampAdjuster timestampAdjuster;
  private final SparseArray<PesReader> psPayloadReaders; // Indexed by pid
  private final ParsableByteArray psPacketBuffer;
  private final PsDurationReader durationReader;

  private boolean foundAllTracks;
  private boolean foundAudioTrack;
  private boolean foundVideoTrack;
  private long lastTrackPosition;

  // Accessed only by the loading thread.
  private PsBinarySearchSeeker psBinarySearchSeeker;
  private ExtractorOutput output;
  private boolean hasOutputSeekMap;

  public PsExtractor() {
    this(new TimestampAdjuster(0));
  }

  public PsExtractor(TimestampAdjuster timestampAdjuster) {
    this.timestampAdjuster = timestampAdjuster;
    psPacketBuffer = new ParsableByteArray(4096);
    psPayloadReaders = new SparseArray<>();
    durationReader = new PsDurationReader();
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    byte[] scratch = new byte[14];
    input.peekFully(scratch, 0, 14);

    // Verify the PACK_START_CODE for the first 4 bytes
    if (PACK_START_CODE != (((scratch[0] & 0xFF) << 24) | ((scratch[1] & 0xFF) << 16)
        | ((scratch[2] & 0xFF) << 8) | (scratch[3] & 0xFF))) {
      return false;
    }
    // Verify the 01xxx1xx marker on the 5th byte
    if ((scratch[4] & 0xC4) != 0x44) {
      return false;
    }
    // Verify the xxxxx1xx marker on the 7th byte
    if ((scratch[6] & 0x04) != 0x04) {
      return false;
    }
    // Verify the xxxxx1xx marker on the 9th byte
    if ((scratch[8] & 0x04) != 0x04) {
      return false;
    }
    // Verify the xxxxxxx1 marker on the 10th byte
    if ((scratch[9] & 0x01) != 0x01) {
      return false;
    }
    // Verify the xxxxxx11 marker on the 13th byte
    if ((scratch[12] & 0x03) != 0x03) {
      return false;
    }
    // Read the stuffing length from the 14th byte (last 3 bits)
    int packStuffingLength = scratch[13] & 0x07;
    input.advancePeekPosition(packStuffingLength);
    // Now check that the next 3 bytes are the beginning of an MPEG start code
    input.peekFully(scratch, 0, 3);
    return (PACKET_START_CODE_PREFIX == (((scratch[0] & 0xFF) << 16) | ((scratch[1] & 0xFF) << 8)
        | (scratch[2] & 0xFF)));
  }

  @Override
  public void init(ExtractorOutput output) {
    this.output = output;
  }

  @Override
  public void seek(long position, long timeUs) {
    boolean hasNotEncounteredFirstTimestamp =
        timestampAdjuster.getTimestampOffsetUs() == C.TIME_UNSET;
    if (hasNotEncounteredFirstTimestamp
        || (timestampAdjuster.getFirstSampleTimestampUs() != 0
            && timestampAdjuster.getFirstSampleTimestampUs() != timeUs)) {
      // - If the timestamp adjuster in the PS stream has not encountered any sample, it's going to
      // treat the first timestamp encountered as sample time 0, which is incorrect. In this case,
      // we have to set the first sample timestamp manually.
      // - If the timestamp adjuster has its timestamp set manually before, and now we seek to a
      // different position, we need to set the first sample timestamp manually again.
      timestampAdjuster.reset();
      timestampAdjuster.setFirstSampleTimestampUs(timeUs);
    }

    if (psBinarySearchSeeker != null) {
      psBinarySearchSeeker.setSeekTargetUs(timeUs);
    }
    for (int i = 0; i < psPayloadReaders.size(); i++) {
      psPayloadReaders.valueAt(i).seek();
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {

    long inputLength = input.getLength();
    boolean canReadDuration = inputLength != C.LENGTH_UNSET;
    if (canReadDuration && !durationReader.isDurationReadFinished()) {
      return durationReader.readDuration(input, seekPosition);
    }
    maybeOutputSeekMap(inputLength);
    if (psBinarySearchSeeker != null && psBinarySearchSeeker.isSeeking()) {
      return psBinarySearchSeeker.handlePendingSeek(
          input, seekPosition, /* outputFrameHolder= */ null);
    }

    input.resetPeekPosition();
    long peekBytesLeft =
        inputLength != C.LENGTH_UNSET ? inputLength - input.getPeekPosition() : C.LENGTH_UNSET;
    if (peekBytesLeft != C.LENGTH_UNSET && peekBytesLeft < 4) {
      return RESULT_END_OF_INPUT;
    }
    // First peek and check what type of start code is next.
    if (!input.peekFully(psPacketBuffer.data, 0, 4, true)) {
      return RESULT_END_OF_INPUT;
    }

    psPacketBuffer.setPosition(0);
    int nextStartCode = psPacketBuffer.readInt();
    if (nextStartCode == MPEG_PROGRAM_END_CODE) {
      return RESULT_END_OF_INPUT;
    } else if (nextStartCode == PACK_START_CODE) {
      // Now peek the rest of the pack_header.
      input.peekFully(psPacketBuffer.data, 0, 10);

      // We only care about the pack_stuffing_length in here, skip the first 77 bits.
      psPacketBuffer.setPosition(9);

      // Last 3 bits is the length.
      int packStuffingLength = psPacketBuffer.readUnsignedByte() & 0x07;

      // Now skip the stuffing and the pack header.
      input.skipFully(packStuffingLength + 14);
      return RESULT_CONTINUE;
    } else if (nextStartCode == SYSTEM_HEADER_START_CODE) {
      // We just skip all this, but we need to get the length first.
      input.peekFully(psPacketBuffer.data, 0, 2);

      // Length is the next 2 bytes.
      psPacketBuffer.setPosition(0);
      int systemHeaderLength = psPacketBuffer.readUnsignedShort();
      input.skipFully(systemHeaderLength + 6);
      return RESULT_CONTINUE;
    } else if (((nextStartCode & 0xFFFFFF00) >> 8) != PACKET_START_CODE_PREFIX) {
      input.skipFully(1);  // Skip bytes until we see a valid start code again.
      return RESULT_CONTINUE;
    }

    // We're at the start of a regular PES packet now.
    // Get the stream ID off the last byte of the start code.
    int streamId = nextStartCode & 0xFF;

    // Check to see if we have this one in our map yet, and if not, then add it.
    PesReader payloadReader = psPayloadReaders.get(streamId);
    if (!foundAllTracks) {
      if (payloadReader == null) {
        ElementaryStreamReader elementaryStreamReader = null;
        if (streamId == PRIVATE_STREAM_1) {
          // Private stream, used for AC3 audio.
          // NOTE: This may need further parsing to determine if its DTS, but that's likely only
          // valid for DVDs.
          elementaryStreamReader = new Ac3Reader();
          foundAudioTrack = true;
          lastTrackPosition = input.getPosition();
        } else if ((streamId & AUDIO_STREAM_MASK) == AUDIO_STREAM) {
          elementaryStreamReader = new MpegAudioReader();
          foundAudioTrack = true;
          lastTrackPosition = input.getPosition();
        } else if ((streamId & VIDEO_STREAM_MASK) == VIDEO_STREAM) {
          elementaryStreamReader = new H262Reader();
          foundVideoTrack = true;
          lastTrackPosition = input.getPosition();
        }
        if (elementaryStreamReader != null) {
          TrackIdGenerator idGenerator = new TrackIdGenerator(streamId, MAX_STREAM_ID_PLUS_ONE);
          elementaryStreamReader.createTracks(output, idGenerator);
          payloadReader = new PesReader(elementaryStreamReader, timestampAdjuster);
          psPayloadReaders.put(streamId, payloadReader);
        }
      }
      long maxSearchPosition =
          foundAudioTrack && foundVideoTrack
              ? lastTrackPosition + MAX_SEARCH_LENGTH_AFTER_AUDIO_AND_VIDEO_FOUND
              : MAX_SEARCH_LENGTH;
      if (input.getPosition() > maxSearchPosition) {
        foundAllTracks = true;
        output.endTracks();
      }
    }

    // The next 2 bytes are the length. Once we have that we can consume the complete packet.
    input.peekFully(psPacketBuffer.data, 0, 2);
    psPacketBuffer.setPosition(0);
    int payloadLength = psPacketBuffer.readUnsignedShort();
    int pesLength = payloadLength + 6;

    if (payloadReader == null) {
      // Just skip this data.
      input.skipFully(pesLength);
    } else {
      psPacketBuffer.reset(pesLength);
      // Read the whole packet and the header for consumption.
      input.readFully(psPacketBuffer.data, 0, pesLength);
      psPacketBuffer.setPosition(6);
      payloadReader.consume(psPacketBuffer);
      psPacketBuffer.setLimit(psPacketBuffer.capacity());
    }

    return RESULT_CONTINUE;
  }

  // Internals.

  private void maybeOutputSeekMap(long inputLength) {
    if (!hasOutputSeekMap) {
      hasOutputSeekMap = true;
      if (durationReader.getDurationUs() != C.TIME_UNSET) {
        psBinarySearchSeeker =
            new PsBinarySearchSeeker(
                durationReader.getScrTimestampAdjuster(),
                durationReader.getDurationUs(),
                inputLength);
        output.seekMap(psBinarySearchSeeker.getSeekMap());
      } else {
        output.seekMap(new SeekMap.Unseekable(durationReader.getDurationUs()));
      }
    }
  }

  /**
   * Parses PES packet data and extracts samples.
   */
  private static final class PesReader {

    private static final int PES_SCRATCH_SIZE = 64;

    private final ElementaryStreamReader pesPayloadReader;
    private final TimestampAdjuster timestampAdjuster;
    private final ParsableBitArray pesScratch;

    private boolean ptsFlag;
    private boolean dtsFlag;
    private boolean seenFirstDts;
    private int extendedHeaderLength;
    private long timeUs;

    public PesReader(ElementaryStreamReader pesPayloadReader, TimestampAdjuster timestampAdjuster) {
      this.pesPayloadReader = pesPayloadReader;
      this.timestampAdjuster = timestampAdjuster;
      pesScratch = new ParsableBitArray(new byte[PES_SCRATCH_SIZE]);
    }

    /**
     * Notifies the reader that a seek has occurred.
     * <p>
     * Following a call to this method, the data passed to the next invocation of
     * {@link #consume(ParsableByteArray)} will not be a continuation of the data that was
     * previously passed. Hence the reader should reset any internal state.
     */
    public void seek() {
      seenFirstDts = false;
      pesPayloadReader.seek();
    }

    /**
     * Consumes the payload of a PS packet.
     *
     * @param data The PES packet. The position will be set to the start of the payload.
     * @throws ParserException If the payload could not be parsed.
     */
    public void consume(ParsableByteArray data) throws ParserException {
      data.readBytes(pesScratch.data, 0, 3);
      pesScratch.setPosition(0);
      parseHeader();
      data.readBytes(pesScratch.data, 0, extendedHeaderLength);
      pesScratch.setPosition(0);
      parseHeaderExtension();
      pesPayloadReader.packetStarted(timeUs, TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR);
      pesPayloadReader.consume(data);
      // We always have complete PES packets with program stream.
      pesPayloadReader.packetFinished();
    }

    private void parseHeader() {
      // Note: see ISO/IEC 13818-1, section 2.4.3.6 for detailed information on the format of
      // the header.
      // First 8 bits are skipped: '10' (2), PES_scrambling_control (2), PES_priority (1),
      // data_alignment_indicator (1), copyright (1), original_or_copy (1)
      pesScratch.skipBits(8);
      ptsFlag = pesScratch.readBit();
      dtsFlag = pesScratch.readBit();
      // ESCR_flag (1), ES_rate_flag (1), DSM_trick_mode_flag (1),
      // additional_copy_info_flag (1), PES_CRC_flag (1), PES_extension_flag (1)
      pesScratch.skipBits(6);
      extendedHeaderLength = pesScratch.readBits(8);
    }

    private void parseHeaderExtension() {
      timeUs = 0;
      if (ptsFlag) {
        pesScratch.skipBits(4); // '0010' or '0011'
        long pts = (long) pesScratch.readBits(3) << 30;
        pesScratch.skipBits(1); // marker_bit
        pts |= pesScratch.readBits(15) << 15;
        pesScratch.skipBits(1); // marker_bit
        pts |= pesScratch.readBits(15);
        pesScratch.skipBits(1); // marker_bit
        if (!seenFirstDts && dtsFlag) {
          pesScratch.skipBits(4); // '0011'
          long dts = (long) pesScratch.readBits(3) << 30;
          pesScratch.skipBits(1); // marker_bit
          dts |= pesScratch.readBits(15) << 15;
          pesScratch.skipBits(1); // marker_bit
          dts |= pesScratch.readBits(15);
          pesScratch.skipBits(1); // marker_bit
          // Subsequent PES packets may have earlier presentation timestamps than this one, but they
          // should all be greater than or equal to this packet's decode timestamp. We feed the
          // decode timestamp to the adjuster here so that in the case that this is the first to be
          // fed, the adjuster will be able to compute an offset to apply such that the adjusted
          // presentation timestamps of all future packets are non-negative.
          timestampAdjuster.adjustTsTimestamp(dts);
          seenFirstDts = true;
        }
        timeUs = timestampAdjuster.adjustTsTimestamp(pts);
      }
    }

  }

}
