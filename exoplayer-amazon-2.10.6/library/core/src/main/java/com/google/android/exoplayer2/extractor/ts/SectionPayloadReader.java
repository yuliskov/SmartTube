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

import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;

/**
 * Reads section data.
 */
public interface SectionPayloadReader {

  /**
   * Initializes the section payload reader.
   *
   * @param timestampAdjuster A timestamp adjuster for offsetting and scaling sample timestamps.
   * @param extractorOutput The {@link ExtractorOutput} that receives the extracted data.
   * @param idGenerator A {@link PesReader.TrackIdGenerator} that generates unique track ids for the
   *     {@link TrackOutput}s.
   */
  void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
      TrackIdGenerator idGenerator);

  /**
   * Called by a {@link SectionReader} when a full section is received.
   *
   * @param sectionData The data belonging to a section starting from the table_id. If
   *     section_syntax_indicator is set to '1', {@code sectionData} excludes the CRC_32 field.
   *     Otherwise, all bytes belonging to the table section are included.
   */
  void consume(ParsableByteArray sectionData);

}
