/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.android.exoplayer2.upstream.DataSpec;
import java.util.List;

/** A {@link MediaChunkIterator} which iterates over a {@link List} of {@link MediaChunk}s. */
public final class MediaChunkListIterator extends BaseMediaChunkIterator {

  private final List<? extends MediaChunk> chunks;
  private final boolean reverseOrder;

  /**
   * Creates iterator.
   *
   * @param chunks The list of chunks to iterate over.
   * @param reverseOrder Whether to iterate in reverse order.
   */
  public MediaChunkListIterator(List<? extends MediaChunk> chunks, boolean reverseOrder) {
    super(0, chunks.size() - 1);
    this.chunks = chunks;
    this.reverseOrder = reverseOrder;
  }

  @Override
  public DataSpec getDataSpec() {
    return getCurrentChunk().dataSpec;
  }

  @Override
  public long getChunkStartTimeUs() {
    return getCurrentChunk().startTimeUs;
  }

  @Override
  public long getChunkEndTimeUs() {
    return getCurrentChunk().endTimeUs;
  }

  private MediaChunk getCurrentChunk() {
    int index = (int) super.getCurrentIndex();
    if (reverseOrder) {
      index = chunks.size() - 1 - index;
    }
    return chunks.get(index);
  }
}
