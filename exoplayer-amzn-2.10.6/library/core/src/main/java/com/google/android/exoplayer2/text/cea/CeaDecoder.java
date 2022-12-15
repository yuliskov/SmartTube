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
package com.google.android.exoplayer2.text.cea;

import androidx.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import java.util.ArrayDeque;
import java.util.PriorityQueue;

/**
 * Base class for subtitle parsers for CEA captions.
 */
/* package */ abstract class CeaDecoder implements SubtitleDecoder {

  private static final int NUM_INPUT_BUFFERS = 10;
  private static final int NUM_OUTPUT_BUFFERS = 2;

  private final ArrayDeque<CeaInputBuffer> availableInputBuffers;
  private final ArrayDeque<SubtitleOutputBuffer> availableOutputBuffers;
  private final PriorityQueue<CeaInputBuffer> queuedInputBuffers;

  private CeaInputBuffer dequeuedInputBuffer;
  private long playbackPositionUs;
  private long queuedInputBufferCount;

  public CeaDecoder() {
    availableInputBuffers = new ArrayDeque<>();
    for (int i = 0; i < NUM_INPUT_BUFFERS; i++) {
      availableInputBuffers.add(new CeaInputBuffer());
    }
    availableOutputBuffers = new ArrayDeque<>();
    for (int i = 0; i < NUM_OUTPUT_BUFFERS; i++) {
      availableOutputBuffers.add(new CeaOutputBuffer());
    }
    queuedInputBuffers = new PriorityQueue<>();
  }

  @Override
  public abstract String getName();

  @Override
  public void setPositionUs(long positionUs) {
    playbackPositionUs = positionUs;
  }

  @Override
  public SubtitleInputBuffer dequeueInputBuffer() throws SubtitleDecoderException {
    Assertions.checkState(dequeuedInputBuffer == null);
    if (availableInputBuffers.isEmpty()) {
      return null;
    }
    dequeuedInputBuffer = availableInputBuffers.pollFirst();
    return dequeuedInputBuffer;
  }

  @Override
  public void queueInputBuffer(SubtitleInputBuffer inputBuffer) throws SubtitleDecoderException {
    Assertions.checkArgument(inputBuffer == dequeuedInputBuffer);
    if (inputBuffer.isDecodeOnly()) {
      // We can drop this buffer early (i.e. before it would be decoded) as the CEA formats allow
      // for decoding to begin mid-stream.
      releaseInputBuffer(dequeuedInputBuffer);
    } else {
      dequeuedInputBuffer.queuedInputBufferCount = queuedInputBufferCount++;
      queuedInputBuffers.add(dequeuedInputBuffer);
    }
    dequeuedInputBuffer = null;
  }

  @Override
  public SubtitleOutputBuffer dequeueOutputBuffer() throws SubtitleDecoderException {
    if (availableOutputBuffers.isEmpty()) {
      return null;
    }
    // iterate through all available input buffers whose timestamps are less than or equal
    // to the current playback position; processing input buffers for future content should
    // be deferred until they would be applicable
    while (!queuedInputBuffers.isEmpty()
        && queuedInputBuffers.peek().timeUs <= playbackPositionUs) {
      CeaInputBuffer inputBuffer = queuedInputBuffers.poll();

      // If the input buffer indicates we've reached the end of the stream, we can
      // return immediately with an output buffer propagating that
      if (inputBuffer.isEndOfStream()) {
        SubtitleOutputBuffer outputBuffer = availableOutputBuffers.pollFirst();
        outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        releaseInputBuffer(inputBuffer);
        return outputBuffer;
      }

      decode(inputBuffer);

      // check if we have any caption updates to report
      if (isNewSubtitleDataAvailable()) {
        // Even if the subtitle is decode-only; we need to generate it to consume the data so it
        // isn't accidentally prepended to the next subtitle
        Subtitle subtitle = createSubtitle();
        if (!inputBuffer.isDecodeOnly()) {
          SubtitleOutputBuffer outputBuffer = availableOutputBuffers.pollFirst();
          outputBuffer.setContent(inputBuffer.timeUs, subtitle, Format.OFFSET_SAMPLE_RELATIVE);
          releaseInputBuffer(inputBuffer);
          return outputBuffer;
        }
      }

      releaseInputBuffer(inputBuffer);
    }

    return null;
  }

  private void releaseInputBuffer(CeaInputBuffer inputBuffer) {
    inputBuffer.clear();
    availableInputBuffers.add(inputBuffer);
  }

  protected void releaseOutputBuffer(SubtitleOutputBuffer outputBuffer) {
    outputBuffer.clear();
    availableOutputBuffers.add(outputBuffer);
  }

  @Override
  public void flush() {
    queuedInputBufferCount = 0;
    playbackPositionUs = 0;
    while (!queuedInputBuffers.isEmpty()) {
      releaseInputBuffer(queuedInputBuffers.poll());
    }
    if (dequeuedInputBuffer != null) {
      releaseInputBuffer(dequeuedInputBuffer);
      dequeuedInputBuffer = null;
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  /**
   * Returns whether there is data available to create a new {@link Subtitle}.
   */
  protected abstract boolean isNewSubtitleDataAvailable();

  /**
   * Creates a {@link Subtitle} from the available data.
   */
  protected abstract Subtitle createSubtitle();

  /**
   * Filters and processes the raw data, providing {@link Subtitle}s via {@link #createSubtitle()}
   * when sufficient data has been processed.
   */
  protected abstract void decode(SubtitleInputBuffer inputBuffer);

  private static final class CeaInputBuffer extends SubtitleInputBuffer
      implements Comparable<CeaInputBuffer> {

    private long queuedInputBufferCount;

    @Override
    public int compareTo(@NonNull CeaInputBuffer other) {
      if (isEndOfStream() != other.isEndOfStream()) {
        return isEndOfStream() ? 1 : -1;
      }
      long delta = timeUs - other.timeUs;
      if (delta == 0) {
        delta = queuedInputBufferCount - other.queuedInputBufferCount;
        if (delta == 0) {
          return 0;
        }
      }
      return delta > 0 ? 1 : -1;
    }
  }

  private final class CeaOutputBuffer extends SubtitleOutputBuffer {

    @Override
    public final void release() {
      releaseOutputBuffer(this);
    }
  }
}
