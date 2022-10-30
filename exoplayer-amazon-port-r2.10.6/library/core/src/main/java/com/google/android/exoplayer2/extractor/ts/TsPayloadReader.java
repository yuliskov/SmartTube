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
import android.util.SparseArray;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

/**
 * Parses TS packet payload data.
 */
public interface TsPayloadReader {

  /**
   * Factory of {@link TsPayloadReader} instances.
   */
  interface Factory {

    /**
     * Returns the initial mapping from PIDs to payload readers.
     * <p>
     * This method allows the injection of payload readers for reserved PIDs, excluding PID 0.
     *
     * @return A {@link SparseArray} that maps PIDs to payload readers.
     */
    SparseArray<TsPayloadReader> createInitialPayloadReaders();

    /**
     * Returns a {@link TsPayloadReader} for a given stream type and elementary stream information.
     * May return null if the stream type is not supported.
     *
     * @param streamType Stream type value as defined in the PMT entry or associated descriptors.
     * @param esInfo Information associated to the elementary stream provided in the PMT.
     * @return A {@link TsPayloadReader} for the packet stream carried by the provided pid.
     *     {@code null} if the stream is not supported.
     */
    TsPayloadReader createPayloadReader(int streamType, EsInfo esInfo);

  }

  /**
   * Holds information associated with a PMT entry.
   */
  final class EsInfo {

    public final int streamType;
    public final String language;
    public final List<DvbSubtitleInfo> dvbSubtitleInfos;
    public final byte[] descriptorBytes;

    /**
     * @param streamType The type of the stream as defined by the
     *     {@link TsExtractor}{@code .TS_STREAM_TYPE_*}.
     * @param language The language of the stream, as defined by ISO/IEC 13818-1, section 2.6.18.
     * @param dvbSubtitleInfos Information about DVB subtitles associated to the stream.
     * @param descriptorBytes The descriptor bytes associated to the stream.
     */
    public EsInfo(int streamType, String language, List<DvbSubtitleInfo> dvbSubtitleInfos,
        byte[] descriptorBytes) {
      this.streamType = streamType;
      this.language = language;
      this.dvbSubtitleInfos =
          dvbSubtitleInfos == null
              ? Collections.emptyList()
              : Collections.unmodifiableList(dvbSubtitleInfos);
      this.descriptorBytes = descriptorBytes;
    }

  }

  /**
   * Holds information about a DVB subtitle, as defined in ETSI EN 300 468 V1.11.1 section 6.2.41.
   */
  final class DvbSubtitleInfo {

    public final String language;
    public final int type;
    public final byte[] initializationData;

    /**
     * @param language The ISO 639-2 three-letter language code.
     * @param type The subtitling type.
     * @param initializationData The composition and ancillary page ids.
     */
    public DvbSubtitleInfo(String language, int type, byte[] initializationData) {
      this.language = language;
      this.type = type;
      this.initializationData = initializationData;
    }

  }

  /**
   * Generates track ids for initializing {@link TsPayloadReader}s' {@link TrackOutput}s.
   */
  final class TrackIdGenerator {

    private static final int ID_UNSET = Integer.MIN_VALUE;

    private final String formatIdPrefix;
    private final int firstTrackId;
    private final int trackIdIncrement;
    private int trackId;
    private String formatId;

    public TrackIdGenerator(int firstTrackId, int trackIdIncrement) {
      this(ID_UNSET, firstTrackId, trackIdIncrement);
    }

    public TrackIdGenerator(int programNumber, int firstTrackId, int trackIdIncrement) {
      this.formatIdPrefix = programNumber != ID_UNSET ? programNumber + "/" : "";
      this.firstTrackId = firstTrackId;
      this.trackIdIncrement = trackIdIncrement;
      trackId = ID_UNSET;
    }

    /**
     * Generates a new set of track and track format ids. Must be called before {@code get*}
     * methods.
     */
    public void generateNewId() {
      trackId = trackId == ID_UNSET ? firstTrackId : trackId + trackIdIncrement;
      formatId = formatIdPrefix + trackId;
    }

    /**
     * Returns the last generated track id. Must be called after the first {@link #generateNewId()}
     * call.
     *
     * @return The last generated track id.
     */
    public int getTrackId() {
      maybeThrowUninitializedError();
      return trackId;
    }

    /**
     * Returns the last generated format id, with the format {@code "programNumber/trackId"}. If no
     * {@code programNumber} was provided, the {@code trackId} alone is used as format id. Must be
     * called after the first {@link #generateNewId()} call.
     *
     * @return The last generated format id, with the format {@code "programNumber/trackId"}. If no
     *     {@code programNumber} was provided, the {@code trackId} alone is used as
     *     format id.
     */
    public String getFormatId() {
      maybeThrowUninitializedError();
      return formatId;
    }

    private void maybeThrowUninitializedError() {
      if (trackId == ID_UNSET) {
        throw new IllegalStateException("generateNewId() must be called before retrieving ids.");
      }
    }

  }

  /**
   * Contextual flags indicating the presence of indicators in the TS packet or PES packet headers.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {
        FLAG_PAYLOAD_UNIT_START_INDICATOR,
        FLAG_RANDOM_ACCESS_INDICATOR,
        FLAG_DATA_ALIGNMENT_INDICATOR
      })
  @interface Flags {}

  /** Indicates the presence of the payload_unit_start_indicator in the TS packet header. */
  int FLAG_PAYLOAD_UNIT_START_INDICATOR = 1;
  /**
   * Indicates the presence of the random_access_indicator in the TS packet header adaptation field.
   */
  int FLAG_RANDOM_ACCESS_INDICATOR = 1 << 1;
  /** Indicates the presence of the data_alignment_indicator in the PES header. */
  int FLAG_DATA_ALIGNMENT_INDICATOR = 1 << 2;

  /**
   * Initializes the payload reader.
   *
   * @param timestampAdjuster A timestamp adjuster for offsetting and scaling sample timestamps.
   * @param extractorOutput The {@link ExtractorOutput} that receives the extracted data.
   * @param idGenerator A {@link PesReader.TrackIdGenerator} that generates unique track ids for the
   *     {@link TrackOutput}s.
   */
  void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
      TrackIdGenerator idGenerator);

  /**
   * Notifies the reader that a seek has occurred.
   *
   * <p>Following a call to this method, the data passed to the next invocation of {@link #consume}
   * will not be a continuation of the data that was previously passed. Hence the reader should
   * reset any internal state.
   */
  void seek();

  /**
   * Consumes the payload of a TS packet.
   *
   * @param data The TS packet. The position will be set to the start of the payload.
   * @param flags See {@link Flags}.
   * @throws ParserException If the payload could not be parsed.
   */
  void consume(ParsableByteArray data, @Flags int flags) throws ParserException;
}
