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
 * A media decoder.
 *
 * @param <I> The type of buffer input to the decoder.
 * @param <O> The type of buffer output from the decoder.
 * @param <E> The type of exception thrown from the decoder.
 */
public interface Decoder<I, O, E extends Exception> {

  /**
   * Returns the name of the decoder.
   *
   * @return The name of the decoder.
   */
  String getName();

  /**
   * Dequeues the next input buffer to be filled and queued to the decoder.
   *
   * @return The input buffer, which will have been cleared, or null if a buffer isn't available.
   * @throws E If a decoder error has occurred.
   */
  I dequeueInputBuffer() throws E;

  /**
   * Queues an input buffer to the decoder.
   *
   * @param inputBuffer The input buffer.
   * @throws E If a decoder error has occurred.
   */
  void queueInputBuffer(I inputBuffer) throws E;

  /**
   * Dequeues the next output buffer from the decoder.
   *
   * @return The output buffer, or null if an output buffer isn't available.
   * @throws E If a decoder error has occurred.
   */
  O dequeueOutputBuffer() throws E;

  /**
   * Flushes the decoder. Ownership of dequeued input buffers is returned to the decoder. The caller
   * is still responsible for releasing any dequeued output buffers.
   */
  void flush();

  /**
   * Releases the decoder. Must be called when the decoder is no longer needed.
   */
  void release();

}
