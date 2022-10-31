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
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;

/** Audio processor for trimming samples from the start/end of data. */
/* package */ final class TrimmingAudioProcessor extends BaseAudioProcessor {

  @C.PcmEncoding private static final int OUTPUT_ENCODING = C.ENCODING_PCM_16BIT;

  private boolean isActive;
  private int trimStartFrames;
  private int trimEndFrames;
  private int bytesPerFrame;
  private boolean receivedInputSinceConfigure;

  private int pendingTrimStartBytes;
  private byte[] endBuffer;
  private int endBufferSize;
  private long trimmedFrameCount;

  /** Creates a new audio processor for trimming samples from the start/end of data. */
  public TrimmingAudioProcessor() {
    endBuffer = Util.EMPTY_BYTE_ARRAY;
  }

  /**
   * Sets the number of audio frames to trim from the start and end of audio passed to this
   * processor. After calling this method, call {@link #configure(int, int, int)} to apply the new
   * trimming frame counts.
   *
   * @param trimStartFrames The number of audio frames to trim from the start of audio.
   * @param trimEndFrames The number of audio frames to trim from the end of audio.
   * @see AudioSink#configure(int, int, int, int, int[], int, int)
   */
  public void setTrimFrameCount(int trimStartFrames, int trimEndFrames) {
    this.trimStartFrames = trimStartFrames;
    this.trimEndFrames = trimEndFrames;
  }

  /** Sets the trimmed frame count returned by {@link #getTrimmedFrameCount()} to zero. */
  public void resetTrimmedFrameCount() {
    trimmedFrameCount = 0;
  }

  /**
   * Returns the number of audio frames trimmed since the last call to {@link
   * #resetTrimmedFrameCount()}.
   */
  public long getTrimmedFrameCount() {
    return trimmedFrameCount;
  }

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding)
      throws UnhandledFormatException {
    if (encoding != OUTPUT_ENCODING) {
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    if (endBufferSize > 0) {
      trimmedFrameCount += endBufferSize / bytesPerFrame;
    }
    bytesPerFrame = Util.getPcmFrameSize(OUTPUT_ENCODING, channelCount);
    endBuffer = new byte[trimEndFrames * bytesPerFrame];
    endBufferSize = 0;
    pendingTrimStartBytes = trimStartFrames * bytesPerFrame;
    boolean wasActive = isActive;
    isActive = trimStartFrames != 0 || trimEndFrames != 0;
    receivedInputSinceConfigure = false;
    setInputFormat(sampleRateHz, channelCount, encoding);
    return wasActive != isActive;
  }

  @Override
  public boolean isActive() {
    return isActive;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int remaining = limit - position;

    if (remaining == 0) {
      return;
    }
    receivedInputSinceConfigure = true;

    // Trim any pending start bytes from the input buffer.
    int trimBytes = Math.min(remaining, pendingTrimStartBytes);
    trimmedFrameCount += trimBytes / bytesPerFrame;
    pendingTrimStartBytes -= trimBytes;
    inputBuffer.position(position + trimBytes);
    if (pendingTrimStartBytes > 0) {
      // Nothing to output yet.
      return;
    }
    remaining -= trimBytes;

    // endBuffer must be kept as full as possible, so that we trim the right amount of media if we
    // don't receive any more input. After taking into account the number of bytes needed to keep
    // endBuffer as full as possible, the output should be any surplus bytes currently in endBuffer
    // followed by any surplus bytes in the new inputBuffer.
    int remainingBytesToOutput = endBufferSize + remaining - endBuffer.length;
    ByteBuffer buffer = replaceOutputBuffer(remainingBytesToOutput);

    // Output from endBuffer.
    int endBufferBytesToOutput = Util.constrainValue(remainingBytesToOutput, 0, endBufferSize);
    buffer.put(endBuffer, 0, endBufferBytesToOutput);
    remainingBytesToOutput -= endBufferBytesToOutput;

    // Output from inputBuffer, restoring its limit afterwards.
    int inputBufferBytesToOutput = Util.constrainValue(remainingBytesToOutput, 0, remaining);
    inputBuffer.limit(inputBuffer.position() + inputBufferBytesToOutput);
    buffer.put(inputBuffer);
    inputBuffer.limit(limit);
    remaining -= inputBufferBytesToOutput;

    // Compact endBuffer, then repopulate it using the new input.
    endBufferSize -= endBufferBytesToOutput;
    System.arraycopy(endBuffer, endBufferBytesToOutput, endBuffer, 0, endBufferSize);
    inputBuffer.get(endBuffer, endBufferSize, remaining);
    endBufferSize += remaining;

    buffer.flip();
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public ByteBuffer getOutput() {
    if (super.isEnded() && endBufferSize > 0) {
      // Because audio processors may be drained in the middle of the stream we assume that the
      // contents of the end buffer need to be output. For gapless transitions, configure will be
      // always be called, which clears the end buffer as needed. When audio is actually ending we
      // play the padding data which is incorrect. This behavior can be fixed once we have the
      // timestamps associated with input buffers.
      replaceOutputBuffer(endBufferSize).put(endBuffer, 0, endBufferSize).flip();
      endBufferSize = 0;
    }
    return super.getOutput();
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public boolean isEnded() {
    return super.isEnded() && endBufferSize == 0;
  }

  @Override
  protected void onFlush() {
    if (receivedInputSinceConfigure) {
      // Audio processors are flushed after initial configuration, so we leave the pending trim
      // start byte count unmodified if the processor was just configured. Otherwise we (possibly
      // incorrectly) assume that this is a seek to a non-zero position. We should instead check the
      // timestamp of the first input buffer queued after flushing to decide whether to trim (see
      // also [Internal: b/77292509]).
      pendingTrimStartBytes = 0;
    }
    endBufferSize = 0;
  }

  @Override
  protected void onReset() {
    endBuffer = Util.EMPTY_BYTE_ARRAY;
  }

}
