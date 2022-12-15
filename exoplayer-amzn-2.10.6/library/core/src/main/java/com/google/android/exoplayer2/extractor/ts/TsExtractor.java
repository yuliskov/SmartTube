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

import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_PAYLOAD_UNIT_START_INDICATOR;

import androidx.annotation.IntDef;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory.Flags;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.DvbSubtitleInfo;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.EsInfo;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Extracts data from the MPEG-2 TS container format.
 */
public final class TsExtractor implements Extractor {

  /** Factory for {@link TsExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new TsExtractor()};

  /**
   * Modes for the extractor. One of {@link #MODE_MULTI_PMT}, {@link #MODE_SINGLE_PMT} or {@link
   * #MODE_HLS}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MODE_MULTI_PMT, MODE_SINGLE_PMT, MODE_HLS})
  public @interface Mode {}

  /**
   * Behave as defined in ISO/IEC 13818-1.
   */
  public static final int MODE_MULTI_PMT = 0;
  /**
   * Assume only one PMT will be contained in the stream, even if more are declared by the PAT.
   */
  public static final int MODE_SINGLE_PMT = 1;
  /**
   * Enable single PMT mode, map {@link TrackOutput}s by their type (instead of PID) and ignore
   * continuity counters.
   */
  public static final int MODE_HLS = 2;

  public static final int TS_STREAM_TYPE_MPA = 0x03;
  public static final int TS_STREAM_TYPE_MPA_LSF = 0x04;
  public static final int TS_STREAM_TYPE_AAC_ADTS = 0x0F;
  public static final int TS_STREAM_TYPE_AAC_LATM = 0x11;
  public static final int TS_STREAM_TYPE_AC3 = 0x81;
  public static final int TS_STREAM_TYPE_DTS = 0x8A;
  public static final int TS_STREAM_TYPE_HDMV_DTS = 0x82;
  public static final int TS_STREAM_TYPE_E_AC3 = 0x87;
  public static final int TS_STREAM_TYPE_AC4 = 0xAC; // DVB/ATSC AC-4 Descriptor
  public static final int TS_STREAM_TYPE_H262 = 0x02;
  public static final int TS_STREAM_TYPE_H264 = 0x1B;
  public static final int TS_STREAM_TYPE_H265 = 0x24;
  public static final int TS_STREAM_TYPE_ID3 = 0x15;
  public static final int TS_STREAM_TYPE_SPLICE_INFO = 0x86;
  public static final int TS_STREAM_TYPE_DVBSUBS = 0x59;

  public static final int TS_PACKET_SIZE = 188;
  public static final int TS_SYNC_BYTE = 0x47; // First byte of each TS packet.

  private static final int TS_PAT_PID = 0;
  private static final int MAX_PID_PLUS_ONE = 0x2000;

  private static final long AC3_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("AC-3");
  private static final long E_AC3_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("EAC3");
  private static final long AC4_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("AC-4");
  private static final long HEVC_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("HEVC");

  private static final int BUFFER_SIZE = TS_PACKET_SIZE * 50;
  private static final int SNIFF_TS_PACKET_COUNT = 5;

  private final @Mode int mode;
  private final List<TimestampAdjuster> timestampAdjusters;
  private final ParsableByteArray tsPacketBuffer;
  private final SparseIntArray continuityCounters;
  private final TsPayloadReader.Factory payloadReaderFactory;
  private final SparseArray<TsPayloadReader> tsPayloadReaders; // Indexed by pid
  private final SparseBooleanArray trackIds;
  private final SparseBooleanArray trackPids;
  private final TsDurationReader durationReader;

  // Accessed only by the loading thread.
  private TsBinarySearchSeeker tsBinarySearchSeeker;
  private ExtractorOutput output;
  private int remainingPmts;
  private boolean tracksEnded;
  private boolean hasOutputSeekMap;
  private boolean pendingSeekToStart;
  private TsPayloadReader id3Reader;
  private int bytesSinceLastSync;
  private int pcrPid;

  public TsExtractor() {
    this(0);
  }

  /**
   * @param defaultTsPayloadReaderFlags A combination of {@link DefaultTsPayloadReaderFactory}
   *     {@code FLAG_*} values that control the behavior of the payload readers.
   */
  public TsExtractor(@Flags int defaultTsPayloadReaderFlags) {
    this(MODE_SINGLE_PMT, defaultTsPayloadReaderFlags);
  }

  /**
   * @param mode Mode for the extractor. One of {@link #MODE_MULTI_PMT}, {@link #MODE_SINGLE_PMT}
   *     and {@link #MODE_HLS}.
   * @param defaultTsPayloadReaderFlags A combination of {@link DefaultTsPayloadReaderFactory}
   *     {@code FLAG_*} values that control the behavior of the payload readers.
   */
  public TsExtractor(@Mode int mode, @Flags int defaultTsPayloadReaderFlags) {
    this(
        mode,
        new TimestampAdjuster(0),
        new DefaultTsPayloadReaderFactory(defaultTsPayloadReaderFlags));
  }

  /**
   * @param mode Mode for the extractor. One of {@link #MODE_MULTI_PMT}, {@link #MODE_SINGLE_PMT}
   *     and {@link #MODE_HLS}.
   * @param timestampAdjuster A timestamp adjuster for offsetting and scaling sample timestamps.
   * @param payloadReaderFactory Factory for injecting a custom set of payload readers.
   */
  public TsExtractor(
      @Mode int mode,
      TimestampAdjuster timestampAdjuster,
      TsPayloadReader.Factory payloadReaderFactory) {
    this.payloadReaderFactory = Assertions.checkNotNull(payloadReaderFactory);
    this.mode = mode;
    if (mode == MODE_SINGLE_PMT || mode == MODE_HLS) {
      timestampAdjusters = Collections.singletonList(timestampAdjuster);
    } else {
      timestampAdjusters = new ArrayList<>();
      timestampAdjusters.add(timestampAdjuster);
    }
    tsPacketBuffer = new ParsableByteArray(new byte[BUFFER_SIZE], 0);
    trackIds = new SparseBooleanArray();
    trackPids = new SparseBooleanArray();
    tsPayloadReaders = new SparseArray<>();
    continuityCounters = new SparseIntArray();
    durationReader = new TsDurationReader();
    pcrPid = -1;
    resetPayloadReaders();
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    byte[] buffer = tsPacketBuffer.data;
    input.peekFully(buffer, 0, TS_PACKET_SIZE * SNIFF_TS_PACKET_COUNT);
    for (int startPosCandidate = 0; startPosCandidate < TS_PACKET_SIZE; startPosCandidate++) {
      // Try to identify at least SNIFF_TS_PACKET_COUNT packets starting with TS_SYNC_BYTE.
      boolean isSyncBytePatternCorrect = true;
      for (int i = 0; i < SNIFF_TS_PACKET_COUNT; i++) {
        if (buffer[startPosCandidate + i * TS_PACKET_SIZE] != TS_SYNC_BYTE) {
          isSyncBytePatternCorrect = false;
          break;
        }
      }
      if (isSyncBytePatternCorrect) {
        input.skipFully(startPosCandidate);
        return true;
      }
    }
    return false;
  }

  @Override
  public void init(ExtractorOutput output) {
    this.output = output;
  }

  @Override
  public void seek(long position, long timeUs) {
    Assertions.checkState(mode != MODE_HLS);
    int timestampAdjustersCount = timestampAdjusters.size();
    for (int i = 0; i < timestampAdjustersCount; i++) {
      TimestampAdjuster timestampAdjuster = timestampAdjusters.get(i);
      boolean hasNotEncounteredFirstTimestamp =
          timestampAdjuster.getTimestampOffsetUs() == C.TIME_UNSET;
      if (hasNotEncounteredFirstTimestamp
          || (timestampAdjuster.getTimestampOffsetUs() != 0
              && timestampAdjuster.getFirstSampleTimestampUs() != timeUs)) {
        // - If a track in the TS stream has not encountered any sample, it's going to treat the
        // first sample encountered as timestamp 0, which is incorrect. So we have to set the first
        // sample timestamp for that track manually.
        // - If the timestamp adjuster has its timestamp set manually before, and now we seek to a
        // different position, we need to set the first sample timestamp manually again.
        timestampAdjuster.reset();
        timestampAdjuster.setFirstSampleTimestampUs(timeUs);
      }
    }
    if (timeUs != 0 && tsBinarySearchSeeker != null) {
      tsBinarySearchSeeker.setSeekTargetUs(timeUs);
    }
    tsPacketBuffer.reset();
    continuityCounters.clear();
    for (int i = 0; i < tsPayloadReaders.size(); i++) {
      tsPayloadReaders.valueAt(i).seek();
    }
    bytesSinceLastSync = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    long inputLength = input.getLength();
    if (tracksEnded) {
      boolean canReadDuration = inputLength != C.LENGTH_UNSET && mode != MODE_HLS;
      if (canReadDuration && !durationReader.isDurationReadFinished()) {
        return durationReader.readDuration(input, seekPosition, pcrPid);
      }
      maybeOutputSeekMap(inputLength);

      if (pendingSeekToStart) {
        pendingSeekToStart = false;
        seek(/* position= */ 0, /* timeUs= */ 0);
        if (input.getPosition() != 0) {
          seekPosition.position = 0;
          return RESULT_SEEK;
        }
      }

      if (tsBinarySearchSeeker != null && tsBinarySearchSeeker.isSeeking()) {
        return tsBinarySearchSeeker.handlePendingSeek(
            input, seekPosition, /* outputFrameHolder= */ null);
      }
    }

    if (!fillBufferWithAtLeastOnePacket(input)) {
      return RESULT_END_OF_INPUT;
    }

    int endOfPacket = findEndOfFirstTsPacketInBuffer();
    int limit = tsPacketBuffer.limit();
    if (endOfPacket > limit) {
      return RESULT_CONTINUE;
    }

    @TsPayloadReader.Flags int packetHeaderFlags = 0;

    // Note: See ISO/IEC 13818-1, section 2.4.3.2 for details of the header format.
    int tsPacketHeader = tsPacketBuffer.readInt();
    if ((tsPacketHeader & 0x800000) != 0) { // transport_error_indicator
      // There are uncorrectable errors in this packet.
      tsPacketBuffer.setPosition(endOfPacket);
      return RESULT_CONTINUE;
    }
    packetHeaderFlags |= (tsPacketHeader & 0x400000) != 0 ? FLAG_PAYLOAD_UNIT_START_INDICATOR : 0;
    // Ignoring transport_priority (tsPacketHeader & 0x200000)
    int pid = (tsPacketHeader & 0x1FFF00) >> 8;
    // Ignoring transport_scrambling_control (tsPacketHeader & 0xC0)
    boolean adaptationFieldExists = (tsPacketHeader & 0x20) != 0;
    boolean payloadExists = (tsPacketHeader & 0x10) != 0;

    TsPayloadReader payloadReader = payloadExists ? tsPayloadReaders.get(pid) : null;
    if (payloadReader == null) {
      tsPacketBuffer.setPosition(endOfPacket);
      return RESULT_CONTINUE;
    }

    // Discontinuity check.
    if (mode != MODE_HLS) {
      int continuityCounter = tsPacketHeader & 0xF;
      int previousCounter = continuityCounters.get(pid, continuityCounter - 1);
      continuityCounters.put(pid, continuityCounter);
      if (previousCounter == continuityCounter) {
        // Duplicate packet found.
        tsPacketBuffer.setPosition(endOfPacket);
        return RESULT_CONTINUE;
      } else if (continuityCounter != ((previousCounter + 1) & 0xF)) {
        // Discontinuity found.
        payloadReader.seek();
      }
    }

    // Skip the adaptation field.
    if (adaptationFieldExists) {
      int adaptationFieldLength = tsPacketBuffer.readUnsignedByte();
      int adaptationFieldFlags = tsPacketBuffer.readUnsignedByte();

      packetHeaderFlags |=
          (adaptationFieldFlags & 0x40) != 0 // random_access_indicator.
              ? TsPayloadReader.FLAG_RANDOM_ACCESS_INDICATOR
              : 0;
      tsPacketBuffer.skipBytes(adaptationFieldLength - 1 /* flags */);
    }

    // Read the payload.
    boolean wereTracksEnded = tracksEnded;
    if (shouldConsumePacketPayload(pid)) {
      tsPacketBuffer.setLimit(endOfPacket);
      payloadReader.consume(tsPacketBuffer, packetHeaderFlags);
      tsPacketBuffer.setLimit(limit);
    }
    if (mode != MODE_HLS && !wereTracksEnded && tracksEnded && inputLength != C.LENGTH_UNSET) {
      // We have read all tracks from all PMTs in this non-live stream. Now seek to the beginning
      // and read again to make sure we output all media, including any contained in packets prior
      // to those containing the track information.
      pendingSeekToStart = true;
    }

    tsPacketBuffer.setPosition(endOfPacket);
    return RESULT_CONTINUE;
  }

  // Internals.

  private void maybeOutputSeekMap(long inputLength) {
    if (!hasOutputSeekMap) {
      hasOutputSeekMap = true;
      if (durationReader.getDurationUs() != C.TIME_UNSET) {
        tsBinarySearchSeeker =
            new TsBinarySearchSeeker(
                durationReader.getPcrTimestampAdjuster(),
                durationReader.getDurationUs(),
                inputLength,
                pcrPid);
        output.seekMap(tsBinarySearchSeeker.getSeekMap());
      } else {
        output.seekMap(new SeekMap.Unseekable(durationReader.getDurationUs()));
      }
    }
  }

  private boolean fillBufferWithAtLeastOnePacket(ExtractorInput input)
      throws IOException, InterruptedException {
    byte[] data = tsPacketBuffer.data;
    // Shift bytes to the start of the buffer if there isn't enough space left at the end.
    if (BUFFER_SIZE - tsPacketBuffer.getPosition() < TS_PACKET_SIZE) {
      int bytesLeft = tsPacketBuffer.bytesLeft();
      if (bytesLeft > 0) {
        System.arraycopy(data, tsPacketBuffer.getPosition(), data, 0, bytesLeft);
      }
      tsPacketBuffer.reset(data, bytesLeft);
    }
    // Read more bytes until we have at least one packet.
    while (tsPacketBuffer.bytesLeft() < TS_PACKET_SIZE) {
      int limit = tsPacketBuffer.limit();
      int read = input.read(data, limit, BUFFER_SIZE - limit);
      if (read == C.RESULT_END_OF_INPUT) {
        return false;
      }
      tsPacketBuffer.setLimit(limit + read);
    }
    return true;
  }

  /**
   * Returns the position of the end of the first TS packet (exclusive) in the packet buffer.
   *
   * <p>This may be a position beyond the buffer limit if the packet has not been read fully into
   * the buffer, or if no packet could be found within the buffer.
   */
  private int findEndOfFirstTsPacketInBuffer() throws ParserException {
    int searchStart = tsPacketBuffer.getPosition();
    int limit = tsPacketBuffer.limit();
    int syncBytePosition = TsUtil.findSyncBytePosition(tsPacketBuffer.data, searchStart, limit);
    // Discard all bytes before the sync byte.
    // If sync byte is not found, this means discard the whole buffer.
    tsPacketBuffer.setPosition(syncBytePosition);
    int endOfPacket = syncBytePosition + TS_PACKET_SIZE;
    if (endOfPacket > limit) {
      bytesSinceLastSync += syncBytePosition - searchStart;
      if (mode == MODE_HLS && bytesSinceLastSync > TS_PACKET_SIZE * 2) {
        throw new ParserException("Cannot find sync byte. Most likely not a Transport Stream.");
      }
    } else {
      // We have found a packet within the buffer.
      bytesSinceLastSync = 0;
    }
    return endOfPacket;
  }

  private boolean shouldConsumePacketPayload(int packetPid) {
    return mode == MODE_HLS
        || tracksEnded
        || !trackPids.get(packetPid, /* valueIfKeyNotFound= */ false); // It's a PSI packet
  }

  private void resetPayloadReaders() {
    trackIds.clear();
    tsPayloadReaders.clear();
    SparseArray<TsPayloadReader> initialPayloadReaders =
        payloadReaderFactory.createInitialPayloadReaders();
    int initialPayloadReadersSize = initialPayloadReaders.size();
    for (int i = 0; i < initialPayloadReadersSize; i++) {
      tsPayloadReaders.put(initialPayloadReaders.keyAt(i), initialPayloadReaders.valueAt(i));
    }
    tsPayloadReaders.put(TS_PAT_PID, new SectionReader(new PatReader()));
    id3Reader = null;
  }

  /**
   * Parses Program Association Table data.
   */
  private class PatReader implements SectionPayloadReader {

    private final ParsableBitArray patScratch;

    public PatReader() {
      patScratch = new ParsableBitArray(new byte[4]);
    }

    @Override
    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
        TrackIdGenerator idGenerator) {
      // Do nothing.
    }

    @Override
    public void consume(ParsableByteArray sectionData) {
      int tableId = sectionData.readUnsignedByte();
      if (tableId != 0x00 /* program_association_section */) {
        // See ISO/IEC 13818-1, section 2.4.4.4 for more information on table id assignment.
        return;
      }
      // section_syntax_indicator(1), '0'(1), reserved(2), section_length(12),
      // transport_stream_id (16), reserved (2), version_number (5), current_next_indicator (1),
      // section_number (8), last_section_number (8)
      sectionData.skipBytes(7);

      int programCount = sectionData.bytesLeft() / 4;
      for (int i = 0; i < programCount; i++) {
        sectionData.readBytes(patScratch, 4);
        int programNumber = patScratch.readBits(16);
        patScratch.skipBits(3); // reserved (3)
        if (programNumber == 0) {
          patScratch.skipBits(13); // network_PID (13)
        } else {
          int pid = patScratch.readBits(13);
          tsPayloadReaders.put(pid, new SectionReader(new PmtReader(pid)));
          remainingPmts++;
        }
      }
      if (mode != MODE_HLS) {
        tsPayloadReaders.remove(TS_PAT_PID);
      }
    }

  }

  /**
   * Parses Program Map Table.
   */
  private class PmtReader implements SectionPayloadReader {

    private static final int TS_PMT_DESC_REGISTRATION = 0x05;
    private static final int TS_PMT_DESC_ISO639_LANG = 0x0A;
    private static final int TS_PMT_DESC_AC3 = 0x6A;
    private static final int TS_PMT_DESC_EAC3 = 0x7A;
    private static final int TS_PMT_DESC_DTS = 0x7B;
    private static final int TS_PMT_DESC_DVB_EXT = 0x7F;
    private static final int TS_PMT_DESC_DVBSUBS = 0x59;

    private static final int TS_PMT_DESC_DVB_EXT_AC4 = 0x15;

    private final ParsableBitArray pmtScratch;
    private final SparseArray<TsPayloadReader> trackIdToReaderScratch;
    private final SparseIntArray trackIdToPidScratch;
    private final int pid;

    public PmtReader(int pid) {
      pmtScratch = new ParsableBitArray(new byte[5]);
      trackIdToReaderScratch = new SparseArray<>();
      trackIdToPidScratch = new SparseIntArray();
      this.pid = pid;
    }

    @Override
    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
        TrackIdGenerator idGenerator) {
      // Do nothing.
    }

    @Override
    public void consume(ParsableByteArray sectionData) {
      int tableId = sectionData.readUnsignedByte();
      if (tableId != 0x02 /* TS_program_map_section */) {
        // See ISO/IEC 13818-1, section 2.4.4.4 for more information on table id assignment.
        return;
      }
      // TimestampAdjuster assignment.
      TimestampAdjuster timestampAdjuster;
      if (mode == MODE_SINGLE_PMT || mode == MODE_HLS || remainingPmts == 1) {
        timestampAdjuster = timestampAdjusters.get(0);
      } else {
        timestampAdjuster = new TimestampAdjuster(
            timestampAdjusters.get(0).getFirstSampleTimestampUs());
        timestampAdjusters.add(timestampAdjuster);
      }

      // section_syntax_indicator(1), '0'(1), reserved(2), section_length(12)
      sectionData.skipBytes(2);
      int programNumber = sectionData.readUnsignedShort();

      // Skip 3 bytes (24 bits), including:
      // reserved (2), version_number (5), current_next_indicator (1), section_number (8),
      // last_section_number (8)
      sectionData.skipBytes(3);

      sectionData.readBytes(pmtScratch, 2);
      // reserved (3), PCR_PID (13)
      pmtScratch.skipBits(3);
      pcrPid = pmtScratch.readBits(13);

      // Read program_info_length.
      sectionData.readBytes(pmtScratch, 2);
      pmtScratch.skipBits(4);
      int programInfoLength = pmtScratch.readBits(12);

      // Skip the descriptors.
      sectionData.skipBytes(programInfoLength);

      if (mode == MODE_HLS && id3Reader == null) {
        // Setup an ID3 track regardless of whether there's a corresponding entry, in case one
        // appears intermittently during playback. See [Internal: b/20261500].
        EsInfo dummyEsInfo = new EsInfo(TS_STREAM_TYPE_ID3, null, null, Util.EMPTY_BYTE_ARRAY);
        id3Reader = payloadReaderFactory.createPayloadReader(TS_STREAM_TYPE_ID3, dummyEsInfo);
        id3Reader.init(timestampAdjuster, output,
            new TrackIdGenerator(programNumber, TS_STREAM_TYPE_ID3, MAX_PID_PLUS_ONE));
      }

      trackIdToReaderScratch.clear();
      trackIdToPidScratch.clear();
      int remainingEntriesLength = sectionData.bytesLeft();
      while (remainingEntriesLength > 0) {
        sectionData.readBytes(pmtScratch, 5);
        int streamType = pmtScratch.readBits(8);
        pmtScratch.skipBits(3); // reserved
        int elementaryPid = pmtScratch.readBits(13);
        pmtScratch.skipBits(4); // reserved
        int esInfoLength = pmtScratch.readBits(12); // ES_info_length.
        EsInfo esInfo = readEsInfo(sectionData, esInfoLength);
        if (streamType == 0x06) {
          streamType = esInfo.streamType;
        }
        remainingEntriesLength -= esInfoLength + 5;

        int trackId = mode == MODE_HLS ? streamType : elementaryPid;
        if (trackIds.get(trackId)) {
          continue;
        }

        TsPayloadReader reader = mode == MODE_HLS && streamType == TS_STREAM_TYPE_ID3 ? id3Reader
            : payloadReaderFactory.createPayloadReader(streamType, esInfo);
        if (mode != MODE_HLS
            || elementaryPid < trackIdToPidScratch.get(trackId, MAX_PID_PLUS_ONE)) {
          trackIdToPidScratch.put(trackId, elementaryPid);
          trackIdToReaderScratch.put(trackId, reader);
        }
      }

      int trackIdCount = trackIdToPidScratch.size();
      for (int i = 0; i < trackIdCount; i++) {
        int trackId = trackIdToPidScratch.keyAt(i);
        int trackPid = trackIdToPidScratch.valueAt(i);
        trackIds.put(trackId, true);
        trackPids.put(trackPid, true);
        TsPayloadReader reader = trackIdToReaderScratch.valueAt(i);
        if (reader != null) {
          if (reader != id3Reader) {
            reader.init(timestampAdjuster, output,
                new TrackIdGenerator(programNumber, trackId, MAX_PID_PLUS_ONE));
          }
          tsPayloadReaders.put(trackPid, reader);
        }
      }

      if (mode == MODE_HLS) {
        if (!tracksEnded) {
          output.endTracks();
          remainingPmts = 0;
          tracksEnded = true;
        }
      } else {
        tsPayloadReaders.remove(pid);
        remainingPmts = mode == MODE_SINGLE_PMT ? 0 : remainingPmts - 1;
        if (remainingPmts == 0) {
          output.endTracks();
          tracksEnded = true;
        }
      }
    }

    /**
     * Returns the stream info read from the available descriptors. Sets {@code data}'s position to
     * the end of the descriptors.
     *
     * @param data A buffer with its position set to the start of the first descriptor.
     * @param length The length of descriptors to read from the current position in {@code data}.
     * @return The stream info read from the available descriptors.
     */
    private EsInfo readEsInfo(ParsableByteArray data, int length) {
      int descriptorsStartPosition = data.getPosition();
      int descriptorsEndPosition = descriptorsStartPosition + length;
      int streamType = -1;
      String language = null;
      List<DvbSubtitleInfo> dvbSubtitleInfos = null;
      while (data.getPosition() < descriptorsEndPosition) {
        int descriptorTag = data.readUnsignedByte();
        int descriptorLength = data.readUnsignedByte();
        int positionOfNextDescriptor = data.getPosition() + descriptorLength;
        if (descriptorTag == TS_PMT_DESC_REGISTRATION) { // registration_descriptor
          long formatIdentifier = data.readUnsignedInt();
          if (formatIdentifier == AC3_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_AC3;
          } else if (formatIdentifier == E_AC3_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_E_AC3;
          } else if (formatIdentifier == AC4_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_AC4;
          } else if (formatIdentifier == HEVC_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_H265;
          }
        } else if (descriptorTag == TS_PMT_DESC_AC3) { // AC-3_descriptor in DVB (ETSI EN 300 468)
          streamType = TS_STREAM_TYPE_AC3;
        } else if (descriptorTag == TS_PMT_DESC_EAC3) { // enhanced_AC-3_descriptor
          streamType = TS_STREAM_TYPE_E_AC3;
        } else if (descriptorTag == TS_PMT_DESC_DVB_EXT) {
          // Extension descriptor in DVB (ETSI EN 300 468).
          int descriptorTagExt = data.readUnsignedByte();
          if (descriptorTagExt == TS_PMT_DESC_DVB_EXT_AC4) {
            // AC-4_descriptor in DVB (ETSI EN 300 468).
            streamType = TS_STREAM_TYPE_AC4;
          }
        } else if (descriptorTag == TS_PMT_DESC_DTS) { // DTS_descriptor
          streamType = TS_STREAM_TYPE_DTS;
        } else if (descriptorTag == TS_PMT_DESC_ISO639_LANG) {
          language = data.readString(3).trim();
          // Audio type is ignored.
        } else if (descriptorTag == TS_PMT_DESC_DVBSUBS) {
          streamType = TS_STREAM_TYPE_DVBSUBS;
          dvbSubtitleInfos = new ArrayList<>();
          while (data.getPosition() < positionOfNextDescriptor) {
            String dvbLanguage = data.readString(3).trim();
            int dvbSubtitlingType = data.readUnsignedByte();
            byte[] initializationData = new byte[4];
            data.readBytes(initializationData, 0, 4);
            dvbSubtitleInfos.add(new DvbSubtitleInfo(dvbLanguage, dvbSubtitlingType,
                initializationData));
          }
        }
        // Skip unused bytes of current descriptor.
        data.skipBytes(positionOfNextDescriptor - data.getPosition());
      }
      data.setPosition(descriptorsEndPosition);
      return new EsInfo(streamType, language, dvbSubtitleInfos,
          Arrays.copyOfRange(data.data, descriptorsStartPosition, descriptorsEndPosition));
    }

  }

}
