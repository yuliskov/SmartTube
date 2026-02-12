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
package com.google.android.exoplayer2.decoder;

/**
 * Maintains decoder event counts, for debugging purposes only.
 * <p>
 * Counters should be written from the playback thread only. Counters may be read from any thread.
 * To ensure that the counter values are made visible across threads, users of this class should
 * invoke {@link #ensureUpdated()} prior to reading and after writing.
 */
public final class DecoderCounters {

  /**
   * The number of times a decoder has been initialized.
   */
  public int decoderInitCount;
  /**
   * The number of times a decoder has been released.
   */
  public int decoderReleaseCount;
  /**
   * The number of queued input buffers.
   */
  public int inputBufferCount;
  /**
   * The number of skipped input buffers.
   * <p>
   * A skipped input buffer is an input buffer that was deliberately not sent to the decoder.
   */
  public int skippedInputBufferCount;
  /**
   * The number of rendered output buffers.
   */
  public int renderedOutputBufferCount;
  /**
   * The number of skipped output buffers.
   * <p>
   * A skipped output buffer is an output buffer that was deliberately not rendered.
   */
  public int skippedOutputBufferCount;
  /**
   * The number of dropped buffers.
   * <p>
   * A dropped buffer is an buffer that was supposed to be decoded/rendered, but was instead
   * dropped because it could not be rendered in time.
   */
  public int droppedBufferCount;
  /**
   * The maximum number of dropped buffers without an interleaving rendered output buffer.
   * <p>
   * Skipped output buffers are ignored for the purposes of calculating this value.
   */
  public int maxConsecutiveDroppedBufferCount;
  /**
   * The number of times all buffers to a keyframe were dropped.
   * <p>
   * Each time buffers to a keyframe are dropped, this counter is increased by one, and the dropped
   * buffer counters are increased by one (for the current output buffer) plus the number of buffers
   * dropped from the source to advance to the keyframe.
   */
  public int droppedToKeyframeCount;

  /**
   * Should be called to ensure counter values are made visible across threads. The playback thread
   * should call this method after updating the counter values. Any other thread should call this
   * method before reading the counters.
   */
  public synchronized void ensureUpdated() {
    // Do nothing. The use of synchronized ensures a memory barrier should another thread also
    // call this method.
  }

  /**
   * Merges the counts from {@code other} into this instance.
   *
   * @param other The {@link DecoderCounters} to merge into this instance.
   */
  public void merge(DecoderCounters other) {
    decoderInitCount += other.decoderInitCount;
    decoderReleaseCount += other.decoderReleaseCount;
    inputBufferCount += other.inputBufferCount;
    skippedInputBufferCount += other.skippedInputBufferCount;
    renderedOutputBufferCount += other.renderedOutputBufferCount;
    skippedOutputBufferCount += other.skippedOutputBufferCount;
    droppedBufferCount += other.droppedBufferCount;
    maxConsecutiveDroppedBufferCount = Math.max(maxConsecutiveDroppedBufferCount,
        other.maxConsecutiveDroppedBufferCount);
    droppedToKeyframeCount += other.droppedToKeyframeCount;
  }

}
