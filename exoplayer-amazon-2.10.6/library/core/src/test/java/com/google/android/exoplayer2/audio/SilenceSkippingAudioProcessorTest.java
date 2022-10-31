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
package com.google.android.exoplayer2.audio;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledFormatException;
import com.google.android.exoplayer2.util.Assertions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SilenceSkippingAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class SilenceSkippingAudioProcessorTest {

  private static final int TEST_SIGNAL_SAMPLE_RATE_HZ = 1000;
  private static final int TEST_SIGNAL_CHANNEL_COUNT = 2;
  private static final int TEST_SIGNAL_SILENCE_DURATION_MS = 1000;
  private static final int TEST_SIGNAL_NOISE_DURATION_MS = 1000;
  private static final int TEST_SIGNAL_FRAME_COUNT = 100000;

  private static final int INPUT_BUFFER_SIZE = 100;

  private SilenceSkippingAudioProcessor silenceSkippingAudioProcessor;

  @Before
  public void setUp() {
    silenceSkippingAudioProcessor = new SilenceSkippingAudioProcessor();
  }

  @Test
  public void testEnabledProcessor_isActive() throws Exception {
    // Given an enabled processor.
    silenceSkippingAudioProcessor.setEnabled(true);

    // When configuring it.
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    silenceSkippingAudioProcessor.flush();

    // It's active.
    assertThat(reconfigured).isTrue();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
  }

  @Test
  public void testDisabledProcessor_isNotActive() throws Exception {
    // Given a disabled processor.
    silenceSkippingAudioProcessor.setEnabled(false);

    // When configuring it.
    silenceSkippingAudioProcessor.configure(
        TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);

    // It's not active.
    assertThat(silenceSkippingAudioProcessor.isActive()).isFalse();
  }

  @Test
  public void testDefaultProcessor_isNotEnabled() throws Exception {
    // Given a processor in its default state.
    // When reconfigured.
    silenceSkippingAudioProcessor.configure(
        TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);

    // It's not active.
    assertThat(silenceSkippingAudioProcessor.isActive()).isFalse();
  }

  @Test
  public void testChangingSampleRate_requiresReconfiguration() throws Exception {
    // Given an enabled processor and configured processor.
    silenceSkippingAudioProcessor.setEnabled(true);
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    if (reconfigured) {
      silenceSkippingAudioProcessor.flush();
    }

    // When reconfiguring it with a different sample rate.
    reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ * 2, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);

    // It's reconfigured.
    assertThat(reconfigured).isTrue();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
  }

  @Test
  public void testReconfiguringWithSameSampleRate_doesNotRequireReconfiguration() throws Exception {
    // Given an enabled processor and configured processor.
    silenceSkippingAudioProcessor.setEnabled(true);
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    assertThat(reconfigured).isTrue();
    silenceSkippingAudioProcessor.flush();

    // When reconfiguring it with the same sample rate.
    reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);

    // It's not reconfigured but it is active.
    assertThat(reconfigured).isFalse();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
  }

  @Test
  public void testSkipInSilentSignal_skipsEverything() throws Exception {
    // Given a signal with only noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SAMPLE_RATE_HZ,
            TEST_SIGNAL_CHANNEL_COUNT,
            TEST_SIGNAL_SILENCE_DURATION_MS,
            /* noiseDurationMs= */ 0,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal.
    silenceSkippingAudioProcessor.setEnabled(true);
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    silenceSkippingAudioProcessor.flush();
    assertThat(reconfigured).isTrue();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);

    // The entire signal is skipped.
    assertThat(totalOutputFrames).isEqualTo(0);
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(TEST_SIGNAL_FRAME_COUNT);
  }

  @Test
  public void testSkipInNoisySignal_skipsNothing() throws Exception {
    // Given a signal with only silence.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SAMPLE_RATE_HZ,
            TEST_SIGNAL_CHANNEL_COUNT,
            /* silenceDurationMs= */ 0,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    silenceSkippingAudioProcessor.flush();
    assertThat(reconfigured).isTrue();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);

    // None of the signal is skipped.
    assertThat(totalOutputFrames).isEqualTo(TEST_SIGNAL_FRAME_COUNT);
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(0);
  }

  @Test
  public void testSkipInAlternatingTestSignal_hasCorrectOutputAndSkippedFrameCounts()
      throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SAMPLE_RATE_HZ,
            TEST_SIGNAL_CHANNEL_COUNT,
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    silenceSkippingAudioProcessor.flush();
    assertThat(reconfigured).isTrue();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);

    // The right number of frames are skipped/output.
    assertThat(totalOutputFrames).isEqualTo(57980);
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(42020);
  }

  @Test
  public void testSkipWithSmallerInputBufferSize_hasCorrectOutputAndSkippedFrameCounts()
      throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SAMPLE_RATE_HZ,
            TEST_SIGNAL_CHANNEL_COUNT,
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal with a smaller input buffer size.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    silenceSkippingAudioProcessor.flush();
    assertThat(reconfigured).isTrue();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, /* inputBufferSize= */ 80);

    // The right number of frames are skipped/output.
    assertThat(totalOutputFrames).isEqualTo(57980);
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(42020);
  }

  @Test
  public void testSkipWithLargerInputBufferSize_hasCorrectOutputAndSkippedFrameCounts()
      throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SAMPLE_RATE_HZ,
            TEST_SIGNAL_CHANNEL_COUNT,
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal with a larger input buffer size.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    silenceSkippingAudioProcessor.flush();
    assertThat(reconfigured).isTrue();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    long totalOutputFrames =
        process(silenceSkippingAudioProcessor, inputBufferProvider, /* inputBufferSize= */ 120);

    // The right number of frames are skipped/output.
    assertThat(totalOutputFrames).isEqualTo(57980);
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(42020);
  }

  @Test
  public void testSkipThenFlush_resetsSkippedFrameCount() throws Exception {
    // Given a signal that alternates between silence and noise.
    InputBufferProvider inputBufferProvider =
        getInputBufferProviderForAlternatingSilenceAndNoise(
            TEST_SIGNAL_SAMPLE_RATE_HZ,
            TEST_SIGNAL_CHANNEL_COUNT,
            TEST_SIGNAL_SILENCE_DURATION_MS,
            TEST_SIGNAL_NOISE_DURATION_MS,
            TEST_SIGNAL_FRAME_COUNT);

    // When processing the entire signal then flushing.
    SilenceSkippingAudioProcessor silenceSkippingAudioProcessor =
        new SilenceSkippingAudioProcessor();
    silenceSkippingAudioProcessor.setEnabled(true);
    boolean reconfigured =
        silenceSkippingAudioProcessor.configure(
            TEST_SIGNAL_SAMPLE_RATE_HZ, TEST_SIGNAL_CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    silenceSkippingAudioProcessor.flush();
    assertThat(reconfigured).isTrue();
    assertThat(silenceSkippingAudioProcessor.isActive()).isTrue();
    process(silenceSkippingAudioProcessor, inputBufferProvider, INPUT_BUFFER_SIZE);
    silenceSkippingAudioProcessor.flush();

    // The skipped frame count is zero.
    assertThat(silenceSkippingAudioProcessor.getSkippedFrames()).isEqualTo(0);
  }

  /**
   * Processes the entire stream provided by {@code inputBufferProvider} in chunks of {@code
   * inputBufferSize} and returns the total number of output frames.
   */
  private static long process(
      SilenceSkippingAudioProcessor processor,
      InputBufferProvider inputBufferProvider,
      int inputBufferSize)
      throws UnhandledFormatException {
    processor.flush();
    long totalOutputFrames = 0;
    while (inputBufferProvider.hasRemaining()) {
      ByteBuffer inputBuffer = inputBufferProvider.getNextInputBuffer(inputBufferSize);
      while (inputBuffer.hasRemaining()) {
        processor.queueInput(inputBuffer);
        ByteBuffer outputBuffer = processor.getOutput();
        totalOutputFrames += outputBuffer.remaining() / (2 * processor.getOutputChannelCount());
        outputBuffer.clear();
      }
    }
    processor.queueEndOfStream();
    while (!processor.isEnded()) {
      ByteBuffer outputBuffer = processor.getOutput();
      totalOutputFrames += outputBuffer.remaining() / (2 * processor.getOutputChannelCount());
      outputBuffer.clear();
    }
    return totalOutputFrames;
  }

  /**
   * Returns an {@link InputBufferProvider} that provides input buffers for a stream that alternates
   * between silence/noise of the specified durations to fill {@code totalFrameCount}.
   */
  private static InputBufferProvider getInputBufferProviderForAlternatingSilenceAndNoise(
      int sampleRateHz,
      int channelCount,
      int silenceDurationMs,
      int noiseDurationMs,
      int totalFrameCount) {
    Pcm16BitAudioBuilder audioBuilder = new Pcm16BitAudioBuilder(channelCount, totalFrameCount);
    while (!audioBuilder.isFull()) {
      int silenceDurationFrames = (silenceDurationMs * sampleRateHz) / 1000;
      audioBuilder.appendFrames(/* count= */ silenceDurationFrames, /* channelLevels= */ (short) 0);
      int noiseDurationFrames = (noiseDurationMs * sampleRateHz) / 1000;
      audioBuilder.appendFrames(
          /* count= */ noiseDurationFrames, /* channelLevels= */ Short.MAX_VALUE);
    }
    return new InputBufferProvider(audioBuilder.build());
  }

  /**
   * Wraps a {@link ShortBuffer} and provides a sequence of {@link ByteBuffer}s of specified sizes
   * that contain copies of its data.
   */
  private static final class InputBufferProvider {

    private final ShortBuffer buffer;

    public InputBufferProvider(ShortBuffer buffer) {
      this.buffer = buffer;
    }

    /** Returns the next buffer with size up to {@code sizeBytes}. */
    public ByteBuffer getNextInputBuffer(int sizeBytes) {
      ByteBuffer inputBuffer = ByteBuffer.allocate(sizeBytes).order(ByteOrder.nativeOrder());
      ShortBuffer inputBufferAsShortBuffer = inputBuffer.asShortBuffer();
      int limit = buffer.limit();
      buffer.limit(Math.min(buffer.position() + sizeBytes / 2, limit));
      inputBufferAsShortBuffer.put(buffer);
      buffer.limit(limit);
      inputBuffer.limit(inputBufferAsShortBuffer.position() * 2);
      return inputBuffer;
    }

    /** Returns whether any more input can be provided via {@link #getNextInputBuffer(int)}. */
    public boolean hasRemaining() {
      return buffer.hasRemaining();
    }
  }

  /** Builder for {@link ShortBuffer}s that contain 16-bit PCM audio samples. */
  private static final class Pcm16BitAudioBuilder {

    private final int channelCount;
    private final ShortBuffer buffer;

    private boolean built;

    public Pcm16BitAudioBuilder(int channelCount, int frameCount) {
      this.channelCount = channelCount;
      buffer = ByteBuffer.allocate(frameCount * channelCount * 2).asShortBuffer();
    }

    /**
     * Appends {@code count} audio frames, using the specified {@code channelLevels} in each frame.
     */
    public void appendFrames(int count, short... channelLevels) {
      Assertions.checkState(!built);
      for (int i = 0; i < count; i += channelCount) {
        for (short channelLevel : channelLevels) {
          buffer.put(channelLevel);
        }
      }
    }

    /** Returns whether the buffer is full. */
    public boolean isFull() {
      Assertions.checkState(!built);
      return !buffer.hasRemaining();
    }

    /** Returns the built buffer. After calling this method the builder should not be reused. */
    public ShortBuffer build() {
      Assertions.checkState(!built);
      built = true;
      buffer.flip();
      return buffer;
    }
  }
}
