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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * {@link SampleStream} for a particular sample queue in HLS.
 */
/* package */ final class HlsSampleStream implements SampleStream {

  private final int trackGroupIndex;
  private final HlsSampleStreamWrapper sampleStreamWrapper;
  private int sampleQueueIndex;

  public HlsSampleStream(HlsSampleStreamWrapper sampleStreamWrapper, int trackGroupIndex) {
    this.sampleStreamWrapper = sampleStreamWrapper;
    this.trackGroupIndex = trackGroupIndex;
    sampleQueueIndex = HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING;
  }

  public void bindSampleQueue() {
    Assertions.checkArgument(sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING);
    sampleQueueIndex = sampleStreamWrapper.bindSampleQueueToSampleStream(trackGroupIndex);
  }

  public void unbindSampleQueue() {
    if (sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING) {
      sampleStreamWrapper.unbindSampleQueue(trackGroupIndex);
      sampleQueueIndex = HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING;
    }
  }

  // SampleStream implementation.

  @Override
  public boolean isReady() {
    return sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL
        || (hasValidSampleQueueIndex() && sampleStreamWrapper.isReady(sampleQueueIndex));
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL) {
      throw new SampleQueueMappingException(
          sampleStreamWrapper.getTrackGroups().get(trackGroupIndex).getFormat(0).sampleMimeType);
    }
    sampleStreamWrapper.maybeThrowError();
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean requireFormat) {
    if (sampleQueueIndex == HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL) {
      buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      return C.RESULT_BUFFER_READ;
    }
    return hasValidSampleQueueIndex()
        ? sampleStreamWrapper.readData(sampleQueueIndex, formatHolder, buffer, requireFormat)
        : C.RESULT_NOTHING_READ;
  }

  @Override
  public int skipData(long positionUs) {
    return hasValidSampleQueueIndex()
        ? sampleStreamWrapper.skipData(sampleQueueIndex, positionUs)
        : 0;
  }

  // Internal methods.

  private boolean hasValidSampleQueueIndex() {
    return sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_PENDING
        && sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL
        && sampleQueueIndex != HlsSampleStreamWrapper.SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL;
  }
}
