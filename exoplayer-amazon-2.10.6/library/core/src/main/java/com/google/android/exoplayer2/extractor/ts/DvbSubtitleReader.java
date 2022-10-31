/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.extractor.ts;

import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.DvbSubtitleInfo;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Collections;
import java.util.List;

/**
 * Parses DVB subtitle data and extracts individual frames.
 */
public final class DvbSubtitleReader implements ElementaryStreamReader {

  private final List<DvbSubtitleInfo> subtitleInfos;
  private final TrackOutput[] outputs;

  private boolean writingSample;
  private int bytesToCheck;
  private int sampleBytesWritten;
  private long sampleTimeUs;

  /**
   * @param subtitleInfos Information about the DVB subtitles associated to the stream.
   */
  public DvbSubtitleReader(List<DvbSubtitleInfo> subtitleInfos) {
    this.subtitleInfos = subtitleInfos;
    outputs = new TrackOutput[subtitleInfos.size()];
  }

  @Override
  public void seek() {
    writingSample = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    for (int i = 0; i < outputs.length; i++) {
      DvbSubtitleInfo subtitleInfo = subtitleInfos.get(i);
      idGenerator.generateNewId();
      TrackOutput output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_TEXT);
      output.format(
          Format.createImageSampleFormat(
              idGenerator.getFormatId(),
              MimeTypes.APPLICATION_DVBSUBS,
              null,
              Format.NO_VALUE,
              0,
              Collections.singletonList(subtitleInfo.initializationData),
              subtitleInfo.language,
              null));
      outputs[i] = output;
    }
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    if ((flags & FLAG_DATA_ALIGNMENT_INDICATOR) == 0) {
      return;
    }
    writingSample = true;
    sampleTimeUs = pesTimeUs;
    sampleBytesWritten = 0;
    bytesToCheck = 2;
  }

  @Override
  public void packetFinished() {
    if (writingSample) {
      for (TrackOutput output : outputs) {
        output.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleBytesWritten, 0, null);
      }
      writingSample = false;
    }
  }

  @Override
  public void consume(ParsableByteArray data) {
    if (writingSample) {
      if (bytesToCheck == 2 && !checkNextByte(data, 0x20)) {
        // Failed to check data_identifier
        return;
      }
      if (bytesToCheck == 1 && !checkNextByte(data, 0x00)) {
        // Check and discard the subtitle_stream_id
        return;
      }
      int dataPosition = data.getPosition();
      int bytesAvailable = data.bytesLeft();
      for (TrackOutput output : outputs) {
        data.setPosition(dataPosition);
        output.sampleData(data, bytesAvailable);
      }
      sampleBytesWritten += bytesAvailable;
    }
  }

  private boolean checkNextByte(ParsableByteArray data, int expectedValue) {
    if (data.bytesLeft() == 0) {
      return false;
    }
    if (data.readUnsignedByte() != expectedValue) {
      writingSample = false;
    }
    bytesToCheck--;
    return writingSample;
  }

}
