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
package com.google.android.exoplayer2.source.hls;

import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.text.webvtt.WebvttParserUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A special purpose extractor for WebVTT content in HLS.
 *
 * <p>This extractor passes through non-empty WebVTT files untouched, however derives the correct
 * sample timestamp for each by sniffing the X-TIMESTAMP-MAP header along with the start timestamp
 * of the first cue header. Empty WebVTT files are not passed through, since it's not possible to
 * derive a sample timestamp in this case.
 */
public final class WebvttExtractor implements Extractor {

  private static final Pattern LOCAL_TIMESTAMP = Pattern.compile("LOCAL:([^,]+)");
  private static final Pattern MEDIA_TIMESTAMP = Pattern.compile("MPEGTS:(\\d+)");
  private static final int HEADER_MIN_LENGTH = 6 /* "WEBVTT" */;
  private static final int HEADER_MAX_LENGTH = 3 /* optional Byte Order Mark */ + HEADER_MIN_LENGTH;

  private final String language;
  private final TimestampAdjuster timestampAdjuster;
  private final ParsableByteArray sampleDataWrapper;

  private ExtractorOutput output;

  private byte[] sampleData;
  private int sampleSize;

  public WebvttExtractor(String language, TimestampAdjuster timestampAdjuster) {
    this.language = language;
    this.timestampAdjuster = timestampAdjuster;
    this.sampleDataWrapper = new ParsableByteArray();
    sampleData = new byte[1024];
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    // Check whether there is a header without BOM.
    input.peekFully(
        sampleData, /* offset= */ 0, /* length= */ HEADER_MIN_LENGTH, /* allowEndOfInput= */ false);
    sampleDataWrapper.reset(sampleData, HEADER_MIN_LENGTH);
    if (WebvttParserUtil.isWebvttHeaderLine(sampleDataWrapper)) {
      return true;
    }
    // The header did not match, try including the BOM.
    input.peekFully(
        sampleData,
        /* offset= */ HEADER_MIN_LENGTH,
        HEADER_MAX_LENGTH - HEADER_MIN_LENGTH,
        /* allowEndOfInput= */ false);
    sampleDataWrapper.reset(sampleData, HEADER_MAX_LENGTH);
    return WebvttParserUtil.isWebvttHeaderLine(sampleDataWrapper);
  }

  @Override
  public void init(ExtractorOutput output) {
    this.output = output;
    output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
  }

  @Override
  public void seek(long position, long timeUs) {
    // This extractor is only used for the HLS use case, which should not call this method.
    throw new IllegalStateException();
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    int currentFileSize = (int) input.getLength();

    // Increase the size of sampleData if necessary.
    if (sampleSize == sampleData.length) {
      sampleData = Arrays.copyOf(sampleData,
          (currentFileSize != C.LENGTH_UNSET ? currentFileSize : sampleData.length) * 3 / 2);
    }

    // Consume to the input.
    int bytesRead = input.read(sampleData, sampleSize, sampleData.length - sampleSize);
    if (bytesRead != C.RESULT_END_OF_INPUT) {
      sampleSize += bytesRead;
      if (currentFileSize == C.LENGTH_UNSET || sampleSize != currentFileSize) {
        return Extractor.RESULT_CONTINUE;
      }
    }

    // We've reached the end of the input, which corresponds to the end of the current file.
    processSample();
    return Extractor.RESULT_END_OF_INPUT;
  }

  private void processSample() throws ParserException {
    ParsableByteArray webvttData = new ParsableByteArray(sampleData);

    // Validate the first line of the header.
    WebvttParserUtil.validateWebvttHeaderLine(webvttData);

    // Defaults to use if the header doesn't contain an X-TIMESTAMP-MAP header.
    long vttTimestampUs = 0;
    long tsTimestampUs = 0;

    // Parse the remainder of the header looking for X-TIMESTAMP-MAP.
    String line;
    while (!TextUtils.isEmpty(line = webvttData.readLine())) {
      if (line.startsWith("X-TIMESTAMP-MAP")) {
        Matcher localTimestampMatcher = LOCAL_TIMESTAMP.matcher(line);
        if (!localTimestampMatcher.find()) {
          throw new ParserException("X-TIMESTAMP-MAP doesn't contain local timestamp: " + line);
        }
        Matcher mediaTimestampMatcher = MEDIA_TIMESTAMP.matcher(line);
        if (!mediaTimestampMatcher.find()) {
          throw new ParserException("X-TIMESTAMP-MAP doesn't contain media timestamp: " + line);
        }
        vttTimestampUs = WebvttParserUtil.parseTimestampUs(localTimestampMatcher.group(1));
        tsTimestampUs = TimestampAdjuster.ptsToUs(Long.parseLong(mediaTimestampMatcher.group(1)));
      }
    }

    // Find the first cue header and parse the start time.
    Matcher cueHeaderMatcher = WebvttParserUtil.findNextCueHeader(webvttData);
    if (cueHeaderMatcher == null) {
      // No cues found. Don't output a sample, but still output a corresponding track.
      buildTrackOutput(0);
      return;
    }

    long firstCueTimeUs = WebvttParserUtil.parseTimestampUs(cueHeaderMatcher.group(1));
    long sampleTimeUs = timestampAdjuster.adjustTsTimestamp(
        TimestampAdjuster.usToPts(firstCueTimeUs + tsTimestampUs - vttTimestampUs));
    long subsampleOffsetUs = sampleTimeUs - firstCueTimeUs;
    // Output the track.
    TrackOutput trackOutput = buildTrackOutput(subsampleOffsetUs);
    // Output the sample.
    sampleDataWrapper.reset(sampleData, sampleSize);
    trackOutput.sampleData(sampleDataWrapper, sampleSize);
    trackOutput.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
  }

  private TrackOutput buildTrackOutput(long subsampleOffsetUs) {
    TrackOutput trackOutput = output.track(0, C.TRACK_TYPE_TEXT);
    trackOutput.format(Format.createTextSampleFormat(null, MimeTypes.TEXT_VTT, null,
        Format.NO_VALUE, 0, language, null, subsampleOffsetUs));
    output.endTracks();
    return trackOutput;
  }

}
