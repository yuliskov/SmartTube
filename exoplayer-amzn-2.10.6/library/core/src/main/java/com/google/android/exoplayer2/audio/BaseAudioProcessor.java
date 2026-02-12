/*
 * Copyright (C) 2019 The Android Open Source Project
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

import androidx.annotation.CallSuper;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Base class for audio processors that keep an output buffer and an internal buffer that is reused
 * whenever input is queued.
 */
public abstract class BaseAudioProcessor implements AudioProcessor {

  /** The configured input sample rate, in Hertz, or {@link Format#NO_VALUE} if not configured. */
  protected int sampleRateHz;
  /** The configured input channel count, or {@link Format#NO_VALUE} if not configured. */
  protected int channelCount;
  /** The configured input encoding, or {@link Format#NO_VALUE} if not configured. */
  @C.PcmEncoding protected int encoding;

  private ByteBuffer buffer;
  private ByteBuffer outputBuffer;
  private boolean inputEnded;

  public BaseAudioProcessor() {
    buffer = EMPTY_BUFFER;
    outputBuffer = EMPTY_BUFFER;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
    encoding = Format.NO_VALUE;
  }

  @Override
  public boolean isActive() {
    return sampleRateHz != Format.NO_VALUE;
  }

  @Override
  public int getOutputChannelCount() {
    return channelCount;
  }

  @Override
  public int getOutputEncoding() {
    return encoding;
  }

  @Override
  public int getOutputSampleRateHz() {
    return sampleRateHz;
  }

  @Override
  public final void queueEndOfStream() {
    inputEnded = true;
    onQueueEndOfStream();
  }

  @CallSuper
  @Override
  public ByteBuffer getOutput() {
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @CallSuper
  @SuppressWarnings("ReferenceEquality")
  @Override
  public boolean isEnded() {
    return inputEnded && outputBuffer == EMPTY_BUFFER;
  }

  @Override
  public final void flush() {
    outputBuffer = EMPTY_BUFFER;
    inputEnded = false;
    onFlush();
  }

  @Override
  public final void reset() {
    flush();
    buffer = EMPTY_BUFFER;
    sampleRateHz = Format.NO_VALUE;
    channelCount = Format.NO_VALUE;
    encoding = Format.NO_VALUE;
    onReset();
  }

  /** Sets the input format of this processor, returning whether the input format has changed. */
  protected final boolean setInputFormat(
      int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {
    if (sampleRateHz == this.sampleRateHz
        && channelCount == this.channelCount
        && encoding == this.encoding) {
      return false;
    }
    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;
    this.encoding = encoding;
    return true;
  }

  /**
   * Replaces the current output buffer with a buffer of at least {@code count} bytes and returns
   * it. Callers should write to the returned buffer then {@link ByteBuffer#flip()} it so it can be
   * read via {@link #getOutput()}.
   */
  protected final ByteBuffer replaceOutputBuffer(int count) {
    if (buffer.capacity() < count) {
      buffer = ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder());
    } else {
      buffer.clear();
    }
    outputBuffer = buffer;
    return buffer;
  }

  /** Returns whether the current output buffer has any data remaining. */
  protected final boolean hasPendingOutput() {
    return outputBuffer.hasRemaining();
  }

  /** Called when the end-of-stream is queued to the processor. */
  protected void onQueueEndOfStream() {
    // Do nothing.
  }

  /** Called when the processor is flushed, directly or as part of resetting. */
  protected void onFlush() {
    // Do nothing.
  }

  /** Called when the processor is reset. */
  protected void onReset() {
    // Do nothing.
  }
}
