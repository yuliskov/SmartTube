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
package com.google.android.exoplayer2.source.chunk;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * A {@link Chunk} that uses an {@link Extractor} to decode initialization data for single track.
 */
public final class InitializationChunk extends Chunk {

  private static final PositionHolder DUMMY_POSITION_HOLDER = new PositionHolder();

  private final ChunkExtractorWrapper extractorWrapper;

  private long nextLoadPosition;
  private volatile boolean loadCanceled;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded.
   * @param trackFormat See {@link #trackFormat}.
   * @param trackSelectionReason See {@link #trackSelectionReason}.
   * @param trackSelectionData See {@link #trackSelectionData}.
   * @param extractorWrapper A wrapped extractor to use for parsing the initialization data.
   */
  public InitializationChunk(
      DataSource dataSource,
      DataSpec dataSpec,
      Format trackFormat,
      int trackSelectionReason,
      @Nullable Object trackSelectionData,
      ChunkExtractorWrapper extractorWrapper) {
    super(dataSource, dataSpec, C.DATA_TYPE_MEDIA_INITIALIZATION, trackFormat, trackSelectionReason,
        trackSelectionData, C.TIME_UNSET, C.TIME_UNSET);
    this.extractorWrapper = extractorWrapper;
  }

  // Loadable implementation.

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @SuppressWarnings("NonAtomicVolatileUpdate")
  @Override
  public void load() throws IOException, InterruptedException {
    DataSpec loadDataSpec = dataSpec.subrange(nextLoadPosition);
    try {
      // Create and open the input.
      ExtractorInput input = new DefaultExtractorInput(dataSource,
          loadDataSpec.absoluteStreamPosition, dataSource.open(loadDataSpec));
      if (nextLoadPosition == 0) {
        extractorWrapper.init(
            /* trackOutputProvider= */ null,
            /* startTimeUs= */ C.TIME_UNSET,
            /* endTimeUs= */ C.TIME_UNSET);
      }
      // Load and decode the initialization data.
      try {
        Extractor extractor = extractorWrapper.extractor;
        int result = Extractor.RESULT_CONTINUE;
        while (result == Extractor.RESULT_CONTINUE && !loadCanceled) {
          result = extractor.read(input, DUMMY_POSITION_HOLDER);
        }
        Assertions.checkState(result != Extractor.RESULT_SEEK);
      } finally {
        nextLoadPosition = input.getPosition() - dataSpec.absoluteStreamPosition;
      }
    } finally {
      Util.closeQuietly(dataSource);
    }
  }

}
