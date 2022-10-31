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
package com.google.android.exoplayer2.source.dash;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer2.source.chunk.InitializationChunk;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.List;

/**
 * Utility methods for DASH streams.
 */
public final class DashUtil {

  /**
   * Loads a DASH manifest.
   *
   * @param dataSource The {@link HttpDataSource} from which the manifest should be read.
   * @param uri The {@link Uri} of the manifest to be read.
   * @return An instance of {@link DashManifest}.
   * @throws IOException Thrown when there is an error while loading.
   */
  public static DashManifest loadManifest(DataSource dataSource, Uri uri)
      throws IOException {
    return ParsingLoadable.load(dataSource, new DashManifestParser(), uri, C.DATA_TYPE_MANIFEST);
  }

  /**
   * Loads {@link DrmInitData} for a given period in a DASH manifest.
   *
   * @param dataSource The {@link HttpDataSource} from which data should be loaded.
   * @param period The {@link Period}.
   * @return The loaded {@link DrmInitData}, or null if none is defined.
   * @throws IOException Thrown when there is an error while loading.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public static @Nullable DrmInitData loadDrmInitData(DataSource dataSource, Period period)
      throws IOException, InterruptedException {
    int primaryTrackType = C.TRACK_TYPE_VIDEO;
    Representation representation = getFirstRepresentation(period, primaryTrackType);
    if (representation == null) {
      primaryTrackType = C.TRACK_TYPE_AUDIO;
      representation = getFirstRepresentation(period, primaryTrackType);
      if (representation == null) {
        return null;
      }
    }
    Format manifestFormat = representation.format;
    Format sampleFormat = DashUtil.loadSampleFormat(dataSource, primaryTrackType, representation);
    return sampleFormat == null
        ? manifestFormat.drmInitData
        : sampleFormat.copyWithManifestFormatInfo(manifestFormat).drmInitData;
  }

  /**
   * Loads initialization data for the {@code representation} and returns the sample {@link Format}.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param trackType The type of the representation. Typically one of the {@link
   *     com.google.android.exoplayer2.C} {@code TRACK_TYPE_*} constants.
   * @param representation The representation which initialization chunk belongs to.
   * @return the sample {@link Format} of the given representation.
   * @throws IOException Thrown when there is an error while loading.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public static @Nullable Format loadSampleFormat(
      DataSource dataSource, int trackType, Representation representation)
      throws IOException, InterruptedException {
    ChunkExtractorWrapper extractorWrapper = loadInitializationData(dataSource, trackType,
        representation, false);
    return extractorWrapper == null ? null : extractorWrapper.getSampleFormats()[0];
  }

  /**
   * Loads initialization and index data for the {@code representation} and returns the {@link
   * ChunkIndex}.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param trackType The type of the representation. Typically one of the {@link
   *     com.google.android.exoplayer2.C} {@code TRACK_TYPE_*} constants.
   * @param representation The representation which initialization chunk belongs to.
   * @return The {@link ChunkIndex} of the given representation, or null if no initialization or
   *     index data exists.
   * @throws IOException Thrown when there is an error while loading.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public static @Nullable ChunkIndex loadChunkIndex(
      DataSource dataSource, int trackType, Representation representation)
      throws IOException, InterruptedException {
    ChunkExtractorWrapper extractorWrapper = loadInitializationData(dataSource, trackType,
        representation, true);
    return extractorWrapper == null ? null : (ChunkIndex) extractorWrapper.getSeekMap();
  }

  /**
   * Loads initialization data for the {@code representation} and optionally index data then returns
   * a {@link ChunkExtractorWrapper} which contains the output.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param trackType The type of the representation. Typically one of the {@link
   *     com.google.android.exoplayer2.C} {@code TRACK_TYPE_*} constants.
   * @param representation The representation which initialization chunk belongs to.
   * @param loadIndex Whether to load index data too.
   * @return A {@link ChunkExtractorWrapper} for the {@code representation}, or null if no
   *     initialization or (if requested) index data exists.
   * @throws IOException Thrown when there is an error while loading.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  private static @Nullable ChunkExtractorWrapper loadInitializationData(
      DataSource dataSource, int trackType, Representation representation, boolean loadIndex)
      throws IOException, InterruptedException {
    RangedUri initializationUri = representation.getInitializationUri();
    if (initializationUri == null) {
      return null;
    }
    ChunkExtractorWrapper extractorWrapper = newWrappedExtractor(trackType, representation.format);
    RangedUri requestUri;
    if (loadIndex) {
      RangedUri indexUri = representation.getIndexUri();
      if (indexUri == null) {
        return null;
      }
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      requestUri = initializationUri.attemptMerge(indexUri, representation.baseUrl);
      if (requestUri == null) {
        loadInitializationData(dataSource, representation, extractorWrapper, initializationUri);
        requestUri = indexUri;
      }
    } else {
      requestUri = initializationUri;
    }
    loadInitializationData(dataSource, representation, extractorWrapper, requestUri);
    return extractorWrapper;
  }

  private static void loadInitializationData(DataSource dataSource,
      Representation representation, ChunkExtractorWrapper extractorWrapper, RangedUri requestUri)
      throws IOException, InterruptedException {
    DataSpec dataSpec = new DataSpec(requestUri.resolveUri(representation.baseUrl),
        requestUri.start, requestUri.length, representation.getCacheKey());
    InitializationChunk initializationChunk = new InitializationChunk(dataSource, dataSpec,
        representation.format, C.SELECTION_REASON_UNKNOWN, null /* trackSelectionData */,
        extractorWrapper);
    initializationChunk.load();
  }

  private static ChunkExtractorWrapper newWrappedExtractor(int trackType, Format format) {
    String mimeType = format.containerMimeType;
    boolean isWebm =
        mimeType != null
            && (mimeType.startsWith(MimeTypes.VIDEO_WEBM)
                || mimeType.startsWith(MimeTypes.AUDIO_WEBM));
    Extractor extractor = isWebm ? new MatroskaExtractor() : new FragmentedMp4Extractor();
    return new ChunkExtractorWrapper(extractor, trackType, format);
  }

  private static @Nullable Representation getFirstRepresentation(Period period, int type) {
    int index = period.getAdaptationSetIndex(type);
    if (index == C.INDEX_UNSET) {
      return null;
    }
    List<Representation> representations = period.adaptationSets.get(index).representations;
    return representations.isEmpty() ? null : representations.get(0);
  }

  private DashUtil() {}

}
