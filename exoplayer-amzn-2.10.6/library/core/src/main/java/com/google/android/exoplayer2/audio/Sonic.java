/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2010 Bill Cox, Sonic Library
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

import com.google.android.exoplayer2.util.Assertions;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * Sonic audio stream processor for time/pitch stretching.
 * <p>
 * Based on https://github.com/waywardgeek/sonic.
 */
/* package */ final class Sonic {

  private static final int MINIMUM_PITCH = 65;
  private static final int MAXIMUM_PITCH = 400;
  private static final int AMDF_FREQUENCY = 4000;
  private static final int BYTES_PER_SAMPLE = 2;

  private final int inputSampleRateHz;
  private final int channelCount;
  private final float speed;
  private final float pitch;
  private final float rate;
  private final int minPeriod;
  private final int maxPeriod;
  private final int maxRequiredFrameCount;
  private final short[] downSampleBuffer;

  private short[] inputBuffer;
  private int inputFrameCount;
  private short[] outputBuffer;
  private int outputFrameCount;
  private short[] pitchBuffer;
  private int pitchFrameCount;
  private int oldRatePosition;
  private int newRatePosition;
  private int remainingInputToCopyFrameCount;
  private int prevPeriod;
  private int prevMinDiff;
  private int minDiff;
  private int maxDiff;

  /**
   * Creates a new Sonic audio stream processor.
   *
   * @param inputSampleRateHz The sample rate of input audio, in hertz.
   * @param channelCount The number of channels in the input audio.
   * @param speed The speedup factor for output audio.
   * @param pitch The pitch factor for output audio.
   * @param outputSampleRateHz The sample rate for output audio, in hertz.
   */
  public Sonic(
      int inputSampleRateHz, int channelCount, float speed, float pitch, int outputSampleRateHz) {
    this.inputSampleRateHz = inputSampleRateHz;
    this.channelCount = channelCount;
    this.speed = speed;
    this.pitch = pitch;
    rate = (float) inputSampleRateHz / outputSampleRateHz;
    minPeriod = inputSampleRateHz / MAXIMUM_PITCH;
    maxPeriod = inputSampleRateHz / MINIMUM_PITCH;
    maxRequiredFrameCount = 2 * maxPeriod;
    downSampleBuffer = new short[maxRequiredFrameCount];
    inputBuffer = new short[maxRequiredFrameCount * channelCount];
    outputBuffer = new short[maxRequiredFrameCount * channelCount];
    pitchBuffer = new short[maxRequiredFrameCount * channelCount];
  }

  /**
   * Queues remaining data from {@code buffer}, and advances its position by the number of bytes
   * consumed.
   *
   * @param buffer A {@link ShortBuffer} containing input data between its position and limit.
   */
  public void queueInput(ShortBuffer buffer) {
    int framesToWrite = buffer.remaining() / channelCount;
    int bytesToWrite = framesToWrite * channelCount * 2;
    inputBuffer = ensureSpaceForAdditionalFrames(inputBuffer, inputFrameCount, framesToWrite);
    buffer.get(inputBuffer, inputFrameCount * channelCount, bytesToWrite / 2);
    inputFrameCount += framesToWrite;
    processStreamInput();
  }

  /**
   * Gets available output, outputting to the start of {@code buffer}. The buffer's position will be
   * advanced by the number of bytes written.
   *
   * @param buffer A {@link ShortBuffer} into which output will be written.
   */
  public void getOutput(ShortBuffer buffer) {
    int framesToRead = Math.min(buffer.remaining() / channelCount, outputFrameCount);
    buffer.put(outputBuffer, 0, framesToRead * channelCount);
    outputFrameCount -= framesToRead;
    System.arraycopy(
        outputBuffer,
        framesToRead * channelCount,
        outputBuffer,
        0,
        outputFrameCount * channelCount);
  }

  /**
   * Forces generating output using whatever data has been queued already. No extra delay will be
   * added to the output, but flushing in the middle of words could introduce distortion.
   */
  public void queueEndOfStream() {
    int remainingFrameCount = inputFrameCount;
    float s = speed / pitch;
    float r = rate * pitch;
    int expectedOutputFrames =
        outputFrameCount + (int) ((remainingFrameCount / s + pitchFrameCount) / r + 0.5f);

    // Add enough silence to flush both input and pitch buffers.
    inputBuffer =
        ensureSpaceForAdditionalFrames(
            inputBuffer, inputFrameCount, remainingFrameCount + 2 * maxRequiredFrameCount);
    for (int xSample = 0; xSample < 2 * maxRequiredFrameCount * channelCount; xSample++) {
      inputBuffer[remainingFrameCount * channelCount + xSample] = 0;
    }
    inputFrameCount += 2 * maxRequiredFrameCount;
    processStreamInput();
    // Throw away any extra frames we generated due to the silence we added.
    if (outputFrameCount > expectedOutputFrames) {
      outputFrameCount = expectedOutputFrames;
    }
    // Empty input and pitch buffers.
    inputFrameCount = 0;
    remainingInputToCopyFrameCount = 0;
    pitchFrameCount = 0;
  }

  /** Clears state in preparation for receiving a new stream of input buffers. */
  public void flush() {
    inputFrameCount = 0;
    outputFrameCount = 0;
    pitchFrameCount = 0;
    oldRatePosition = 0;
    newRatePosition = 0;
    remainingInputToCopyFrameCount = 0;
    prevPeriod = 0;
    prevMinDiff = 0;
    minDiff = 0;
    maxDiff = 0;
  }

  /** Returns the size of output that can be read with {@link #getOutput(ShortBuffer)}, in bytes. */
  public int getOutputSize() {
    return outputFrameCount * channelCount * BYTES_PER_SAMPLE;
  }

  // Internal methods.

  /**
   * Returns {@code buffer} or a copy of it, such that there is enough space in the returned buffer
   * to store {@code newFrameCount} additional frames.
   *
   * @param buffer The buffer.
   * @param frameCount The number of frames already in the buffer.
   * @param additionalFrameCount The number of additional frames that need to be stored in the
   *     buffer.
   * @return A buffer with enough space for the additional frames.
   */
  private short[] ensureSpaceForAdditionalFrames(
      short[] buffer, int frameCount, int additionalFrameCount) {
    int currentCapacityFrames = buffer.length / channelCount;
    if (frameCount + additionalFrameCount <= currentCapacityFrames) {
      return buffer;
    } else {
      int newCapacityFrames = 3 * currentCapacityFrames / 2 + additionalFrameCount;
      return Arrays.copyOf(buffer, newCapacityFrames * channelCount);
    }
  }

  private void removeProcessedInputFrames(int positionFrames) {
    int remainingFrames = inputFrameCount - positionFrames;
    System.arraycopy(
        inputBuffer, positionFrames * channelCount, inputBuffer, 0, remainingFrames * channelCount);
    inputFrameCount = remainingFrames;
  }

  private void copyToOutput(short[] samples, int positionFrames, int frameCount) {
    outputBuffer = ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, frameCount);
    System.arraycopy(
        samples,
        positionFrames * channelCount,
        outputBuffer,
        outputFrameCount * channelCount,
        frameCount * channelCount);
    outputFrameCount += frameCount;
  }

  private int copyInputToOutput(int positionFrames) {
    int frameCount = Math.min(maxRequiredFrameCount, remainingInputToCopyFrameCount);
    copyToOutput(inputBuffer, positionFrames, frameCount);
    remainingInputToCopyFrameCount -= frameCount;
    return frameCount;
  }

  private void downSampleInput(short[] samples, int position, int skip) {
    // If skip is greater than one, average skip samples together and write them to the down-sample
    // buffer. If channelCount is greater than one, mix the channels together as we down sample.
    int frameCount = maxRequiredFrameCount / skip;
    int samplesPerValue = channelCount * skip;
    position *= channelCount;
    for (int i = 0; i < frameCount; i++) {
      int value = 0;
      for (int j = 0; j < samplesPerValue; j++) {
        value += samples[position + i * samplesPerValue + j];
      }
      value /= samplesPerValue;
      downSampleBuffer[i] = (short) value;
    }
  }

  private int findPitchPeriodInRange(short[] samples, int position, int minPeriod, int maxPeriod) {
    // Find the best frequency match in the range, and given a sample skip multiple. For now, just
    // find the pitch of the first channel.
    int bestPeriod = 0;
    int worstPeriod = 255;
    int minDiff = 1;
    int maxDiff = 0;
    position *= channelCount;
    for (int period = minPeriod; period <= maxPeriod; period++) {
      int diff = 0;
      for (int i = 0; i < period; i++) {
        short sVal = samples[position + i];
        short pVal = samples[position + period + i];
        diff += Math.abs(sVal - pVal);
      }
      // Note that the highest number of samples we add into diff will be less than 256, since we
      // skip samples. Thus, diff is a 24 bit number, and we can safely multiply by numSamples
      // without overflow.
      if (diff * bestPeriod < minDiff * period) {
        minDiff = diff;
        bestPeriod = period;
      }
      if (diff * worstPeriod > maxDiff * period) {
        maxDiff = diff;
        worstPeriod = period;
      }
    }
    this.minDiff = minDiff / bestPeriod;
    this.maxDiff = maxDiff / worstPeriod;
    return bestPeriod;
  }

  /**
   * Returns whether the previous pitch period estimate is a better approximation, which can occur
   * at the abrupt end of voiced words.
   */
  private boolean previousPeriodBetter(int minDiff, int maxDiff) {
    if (minDiff == 0 || prevPeriod == 0) {
      return false;
    }
    if (maxDiff > minDiff * 3) {
      // Got a reasonable match this period.
      return false;
    }
    if (minDiff * 2 <= prevMinDiff * 3) {
      // Mismatch is not that much greater this period.
      return false;
    }
    return true;
  }

  private int findPitchPeriod(short[] samples, int position) {
    // Find the pitch period. This is a critical step, and we may have to try multiple ways to get a
    // good answer. This version uses AMDF. To improve speed, we down sample by an integer factor
    // get in the 11 kHz range, and then do it again with a narrower frequency range without down
    // sampling.
    int period;
    int retPeriod;
    int skip = inputSampleRateHz > AMDF_FREQUENCY ? inputSampleRateHz / AMDF_FREQUENCY : 1;
    if (channelCount == 1 && skip == 1) {
      period = findPitchPeriodInRange(samples, position, minPeriod, maxPeriod);
    } else {
      downSampleInput(samples, position, skip);
      period = findPitchPeriodInRange(downSampleBuffer, 0, minPeriod / skip, maxPeriod / skip);
      if (skip != 1) {
        period *= skip;
        int minP = period - (skip * 4);
        int maxP = period + (skip * 4);
        if (minP < minPeriod) {
          minP = minPeriod;
        }
        if (maxP > maxPeriod) {
          maxP = maxPeriod;
        }
        if (channelCount == 1) {
          period = findPitchPeriodInRange(samples, position, minP, maxP);
        } else {
          downSampleInput(samples, position, 1);
          period = findPitchPeriodInRange(downSampleBuffer, 0, minP, maxP);
        }
      }
    }
    if (previousPeriodBetter(minDiff, maxDiff)) {
      retPeriod = prevPeriod;
    } else {
      retPeriod = period;
    }
    prevMinDiff = minDiff;
    prevPeriod = period;
    return retPeriod;
  }

  private void moveNewSamplesToPitchBuffer(int originalOutputFrameCount) {
    int frameCount = outputFrameCount - originalOutputFrameCount;
    pitchBuffer = ensureSpaceForAdditionalFrames(pitchBuffer, pitchFrameCount, frameCount);
    System.arraycopy(
        outputBuffer,
        originalOutputFrameCount * channelCount,
        pitchBuffer,
        pitchFrameCount * channelCount,
        frameCount * channelCount);
    outputFrameCount = originalOutputFrameCount;
    pitchFrameCount += frameCount;
  }

  private void removePitchFrames(int frameCount) {
    if (frameCount == 0) {
      return;
    }
    System.arraycopy(
        pitchBuffer,
        frameCount * channelCount,
        pitchBuffer,
        0,
        (pitchFrameCount - frameCount) * channelCount);
    pitchFrameCount -= frameCount;
  }

  private short interpolate(short[] in, int inPos, int oldSampleRate, int newSampleRate) {
    short left = in[inPos];
    short right = in[inPos + channelCount];
    int position = newRatePosition * oldSampleRate;
    int leftPosition = oldRatePosition * newSampleRate;
    int rightPosition = (oldRatePosition + 1) * newSampleRate;
    int ratio = rightPosition - position;
    int width = rightPosition - leftPosition;
    return (short) ((ratio * left + (width - ratio) * right) / width);
  }

  private void adjustRate(float rate, int originalOutputFrameCount) {
    if (outputFrameCount == originalOutputFrameCount) {
      return;
    }
    int newSampleRate = (int) (inputSampleRateHz / rate);
    int oldSampleRate = inputSampleRateHz;
    // Set these values to help with the integer math.
    while (newSampleRate > (1 << 14) || oldSampleRate > (1 << 14)) {
      newSampleRate /= 2;
      oldSampleRate /= 2;
    }
    moveNewSamplesToPitchBuffer(originalOutputFrameCount);
    // Leave at least one pitch sample in the buffer.
    for (int position = 0; position < pitchFrameCount - 1; position++) {
      while ((oldRatePosition + 1) * newSampleRate > newRatePosition * oldSampleRate) {
        outputBuffer =
            ensureSpaceForAdditionalFrames(
                outputBuffer, outputFrameCount, /* additionalFrameCount= */ 1);
        for (int i = 0; i < channelCount; i++) {
          outputBuffer[outputFrameCount * channelCount + i] =
              interpolate(pitchBuffer, position * channelCount + i, oldSampleRate, newSampleRate);
        }
        newRatePosition++;
        outputFrameCount++;
      }
      oldRatePosition++;
      if (oldRatePosition == oldSampleRate) {
        oldRatePosition = 0;
        Assertions.checkState(newRatePosition == newSampleRate);
        newRatePosition = 0;
      }
    }
    removePitchFrames(pitchFrameCount - 1);
  }

  private int skipPitchPeriod(short[] samples, int position, float speed, int period) {
    // Skip over a pitch period, and copy period/speed samples to the output.
    int newFrameCount;
    if (speed >= 2.0f) {
      newFrameCount = (int) (period / (speed - 1.0f));
    } else {
      newFrameCount = period;
      remainingInputToCopyFrameCount = (int) (period * (2.0f - speed) / (speed - 1.0f));
    }
    outputBuffer = ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, newFrameCount);
    overlapAdd(
        newFrameCount,
        channelCount,
        outputBuffer,
        outputFrameCount,
        samples,
        position,
        samples,
        position + period);
    outputFrameCount += newFrameCount;
    return newFrameCount;
  }

  private int insertPitchPeriod(short[] samples, int position, float speed, int period) {
    // Insert a pitch period, and determine how much input to copy directly.
    int newFrameCount;
    if (speed < 0.5f) {
      newFrameCount = (int) (period * speed / (1.0f - speed));
    } else {
      newFrameCount = period;
      remainingInputToCopyFrameCount = (int) (period * (2.0f * speed - 1.0f) / (1.0f - speed));
    }
    outputBuffer =
        ensureSpaceForAdditionalFrames(outputBuffer, outputFrameCount, period + newFrameCount);
    System.arraycopy(
        samples,
        position * channelCount,
        outputBuffer,
        outputFrameCount * channelCount,
        period * channelCount);
    overlapAdd(
        newFrameCount,
        channelCount,
        outputBuffer,
        outputFrameCount + period,
        samples,
        position + period,
        samples,
        position);
    outputFrameCount += period + newFrameCount;
    return newFrameCount;
  }

  private void changeSpeed(float speed) {
    if (inputFrameCount < maxRequiredFrameCount) {
      return;
    }
    int frameCount = inputFrameCount;
    int positionFrames = 0;
    do {
      if (remainingInputToCopyFrameCount > 0) {
        positionFrames += copyInputToOutput(positionFrames);
      } else {
        int period = findPitchPeriod(inputBuffer, positionFrames);
        if (speed > 1.0) {
          positionFrames += period + skipPitchPeriod(inputBuffer, positionFrames, speed, period);
        } else {
          positionFrames += insertPitchPeriod(inputBuffer, positionFrames, speed, period);
        }
      }
    } while (positionFrames + maxRequiredFrameCount <= frameCount);
    removeProcessedInputFrames(positionFrames);
  }

  private void processStreamInput() {
    // Resample as many pitch periods as we have buffered on the input.
    int originalOutputFrameCount = outputFrameCount;
    float s = speed / pitch;
    float r = rate * pitch;
    if (s > 1.00001 || s < 0.99999) {
      changeSpeed(s);
    } else {
      copyToOutput(inputBuffer, 0, inputFrameCount);
      inputFrameCount = 0;
    }
    if (r != 1.0f) {
      adjustRate(r, originalOutputFrameCount);
    }
  }

  private static void overlapAdd(
      int frameCount,
      int channelCount,
      short[] out,
      int outPosition,
      short[] rampDown,
      int rampDownPosition,
      short[] rampUp,
      int rampUpPosition) {
    for (int i = 0; i < channelCount; i++) {
      int o = outPosition * channelCount + i;
      int u = rampUpPosition * channelCount + i;
      int d = rampDownPosition * channelCount + i;
      for (int t = 0; t < frameCount; t++) {
        out[o] = (short) ((rampDown[d] * (frameCount - t) + rampUp[u] * t) / frameCount);
        o += channelCount;
        d += channelCount;
        u += channelCount;
      }
    }
  }

}
