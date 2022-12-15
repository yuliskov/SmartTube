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
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;

/**
 * Parses splice info sections as defined by SCTE35.
 */
public final class SpliceInfoSectionReader implements SectionPayloadReader {

  private TimestampAdjuster timestampAdjuster;
  private TrackOutput output;
  private boolean formatDeclared;

  @Override
  public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
      TsPayloadReader.TrackIdGenerator idGenerator) {
    this.timestampAdjuster = timestampAdjuster;
    idGenerator.generateNewId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_METADATA);
    output.format(Format.createSampleFormat(idGenerator.getFormatId(), MimeTypes.APPLICATION_SCTE35,
        null, Format.NO_VALUE, null));
  }

  @Override
  public void consume(ParsableByteArray sectionData) {
    if (!formatDeclared) {
      if (timestampAdjuster.getTimestampOffsetUs() == C.TIME_UNSET) {
        // There is not enough information to initialize the timestamp adjuster.
        return;
      }
      output.format(Format.createSampleFormat(null, MimeTypes.APPLICATION_SCTE35,
          timestampAdjuster.getTimestampOffsetUs()));
      formatDeclared = true;
    }
    int sampleSize = sectionData.bytesLeft();
    output.sampleData(sectionData, sampleSize);
    output.sampleMetadata(timestampAdjuster.getLastAdjustedTimestampUs(), C.BUFFER_FLAG_KEY_FRAME,
        sampleSize, 0, null);
  }

}
