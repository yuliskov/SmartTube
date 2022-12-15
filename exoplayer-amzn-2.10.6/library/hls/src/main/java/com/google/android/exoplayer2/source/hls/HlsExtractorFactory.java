/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.net.Uri;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Factory for HLS media chunk extractors.
 */
public interface HlsExtractorFactory {

  /** Holds an {@link Extractor} and associated parameters. */
  final class Result {

    /** The created extractor; */
    public final Extractor extractor;
    /** Whether the segments for which {@link #extractor} is created are packed audio segments. */
    public final boolean isPackedAudioExtractor;
    /**
     * Whether {@link #extractor} may be reused for following continuous (no immediately preceding
     * discontinuities) segments of the same variant.
     */
    public final boolean isReusable;

    /**
     * Creates a result.
     *
     * @param extractor See {@link #extractor}.
     * @param isPackedAudioExtractor See {@link #isPackedAudioExtractor}.
     * @param isReusable See {@link #isReusable}.
     */
    public Result(Extractor extractor, boolean isPackedAudioExtractor, boolean isReusable) {
      this.extractor = extractor;
      this.isPackedAudioExtractor = isPackedAudioExtractor;
      this.isReusable = isReusable;
    }
  }

  HlsExtractorFactory DEFAULT = new DefaultHlsExtractorFactory();

  /**
   * Creates an {@link Extractor} for extracting HLS media chunks.
   *
   * @param previousExtractor A previously used {@link Extractor} which can be reused if the current
   *     chunk is a continuation of the previously extracted chunk, or null otherwise. It is the
   *     responsibility of implementers to only reuse extractors that are suited for reusage.
   * @param uri The URI of the media chunk.
   * @param format A {@link Format} associated with the chunk to extract.
   * @param muxedCaptionFormats List of muxed caption {@link Format}s. Null if no closed caption
   *     information is available in the master playlist.
   * @param drmInitData {@link DrmInitData} associated with the chunk.
   * @param timestampAdjuster Adjuster corresponding to the provided discontinuity sequence number.
   * @param responseHeaders The HTTP response headers associated with the media segment or
   *     initialization section to extract.
   * @param sniffingExtractorInput The first extractor input that will be passed to the returned
   *     extractor's {@link Extractor#read(ExtractorInput, PositionHolder)}. Must only be used to
   *     call {@link Extractor#sniff(ExtractorInput)}.
   * @return A {@link Result}.
   * @throws InterruptedException If the thread is interrupted while sniffing.
   * @throws IOException If an I/O error is encountered while sniffing.
   */
  Result createExtractor(
      Extractor previousExtractor,
      Uri uri,
      Format format,
      List<Format> muxedCaptionFormats,
      DrmInitData drmInitData,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders,
      ExtractorInput sniffingExtractorInput)
      throws InterruptedException, IOException;
}
