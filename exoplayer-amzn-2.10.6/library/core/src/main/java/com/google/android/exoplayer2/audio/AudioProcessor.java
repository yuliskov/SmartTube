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
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Interface for audio processors, which take audio data as input and transform it, potentially
 * modifying its channel count, encoding and/or sample rate.
 *
 * <p>Call {@link #configure(int, int, int)} to configure the processor to receive input audio, then
 * call {@link #isActive()} to determine whether the processor is active in the new configuration.
 * {@link #queueInput(ByteBuffer)}, {@link #getOutputChannelCount()}, {@link #getOutputEncoding()}
 * and {@link #getOutputSampleRateHz()} may only be called if the processor is active. Call {@link
 * #reset()} to reset the processor to its unconfigured state and release any resources.
 *
 * <p>In addition to being able to modify the format of audio, implementations may allow parameters
 * to be set that affect the output audio and whether the processor is active/inactive.
 */
public interface AudioProcessor {

  /** Exception thrown when a processor can't be configured for a given input audio format. */
  final class UnhandledFormatException extends Exception {

    public UnhandledFormatException(
        int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
      super("Unhandled format: " + sampleRateHz + " Hz, " + channelCount + " channels in encoding "
          + encoding);
    }

  }

  /** An empty, direct {@link ByteBuffer}. */
  ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

  /**
   * Configures the processor to process input audio with the specified format. After calling this
   * method, call {@link #isActive()} to determine whether the audio processor is active.
   *
   * <p>If the audio processor is active after configuration, call {@link #getOutputSampleRateHz()},
   * {@link #getOutputChannelCount()} and {@link #getOutputEncoding()} to get its new output format.
   *
   * <p>If this method returns {@code true}, it is necessary to {@link #flush()} the processor
   * before queueing more data, but you can (optionally) first drain output in the previous
   * configuration by calling {@link #queueEndOfStream()} and {@link #getOutput()}. If this method
   * returns {@code false}, it is safe to queue new input immediately.
   *
   * @param sampleRateHz The sample rate of input audio in Hz.
   * @param channelCount The number of interleaved channels in input audio.
   * @param encoding The encoding of input audio.
   * @return Whether the processor must be {@link #flush() flushed} before queueing more input.
   * @throws UnhandledFormatException Thrown if the specified format can't be handled as input.
   */
  boolean configure(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding)
      throws UnhandledFormatException;

  /** Returns whether the processor is configured and will process input buffers. */
  boolean isActive();

  /**
   * Returns the number of audio channels in the data output by the processor. The value may change
   * as a result of calling {@link #configure(int, int, int)}.
   */
  int getOutputChannelCount();

  /**
   * Returns the audio encoding used in the data output by the processor. The value may change as a
   * result of calling {@link #configure(int, int, int)}.
   */
  @C.PcmEncoding
  int getOutputEncoding();

  /**
   * Returns the sample rate of audio output by the processor, in hertz. The value may change as a
   * result of calling {@link #configure(int, int, int)}.
   */
  int getOutputSampleRateHz();

  /**
   * Queues audio data between the position and limit of the input {@code buffer} for processing.
   * {@code buffer} must be a direct byte buffer with native byte order. Its contents are treated as
   * read-only. Its position will be advanced by the number of bytes consumed (which may be zero).
   * The caller retains ownership of the provided buffer. Calling this method invalidates any
   * previous buffer returned by {@link #getOutput()}.
   *
   * @param buffer The input buffer to process.
   */
  void queueInput(ByteBuffer buffer);

  /**
   * Queues an end of stream signal. After this method has been called,
   * {@link #queueInput(ByteBuffer)} may not be called until after the next call to
   * {@link #flush()}. Calling {@link #getOutput()} will return any remaining output data. Multiple
   * calls may be required to read all of the remaining output data. {@link #isEnded()} will return
   * {@code true} once all remaining output data has been read.
   */
  void queueEndOfStream();

  /**
   * Returns a buffer containing processed output data between its position and limit. The buffer
   * will always be a direct byte buffer with native byte order. Calling this method invalidates any
   * previously returned buffer. The buffer will be empty if no output is available.
   *
   * @return A buffer containing processed output data between its position and limit.
   */
  ByteBuffer getOutput();

  /**
   * Returns whether this processor will return no more output from {@link #getOutput()} until it
   * has been {@link #flush()}ed and more input has been queued.
   */
  boolean isEnded();

  /**
   * Clears any buffered data and pending output. If the audio processor is active, also prepares
   * the audio processor to receive a new stream of input in the last configured (pending) format.
   */
  void flush();

  /** Resets the processor to its unconfigured state. */
  void reset();
}
