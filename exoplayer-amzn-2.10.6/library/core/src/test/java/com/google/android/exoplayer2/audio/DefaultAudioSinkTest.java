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

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.annotation.Config.NEWEST_SDK;
import static org.robolectric.annotation.Config.OLDEST_SDK;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link DefaultAudioSink}.
 *
 * <p>Note: the Robolectric-provided AudioTrack instantiated in the audio sink uses only the Java
 * part of AudioTrack with a {@code ShadowPlayerBase} underneath. This means it will not consume
 * data (i.e., the {@link android.media.AudioTrack#write} methods just return 0), so these tests are
 * currently limited to verifying behavior that doesn't rely on consuming data, and the position
 * will stay at its initial value. For example, we can't verify {@link
 * AudioSink#handleBuffer(ByteBuffer, long)} handling a complete buffer, or queueing audio then
 * draining to the end of the stream. This could be worked around by having a test-only mode where
 * {@link DefaultAudioSink} automatically treats audio as consumed.
 */
@RunWith(AndroidJUnit4.class)
public final class DefaultAudioSinkTest {

  private static final int CHANNEL_COUNT_MONO = 1;
  private static final int CHANNEL_COUNT_STEREO = 2;
  private static final int BYTES_PER_FRAME_16_BIT = 2;
  private static final int SAMPLE_RATE_44_1 = 44100;
  private static final int TRIM_100_MS_FRAME_COUNT = 4410;
  private static final int TRIM_10_MS_FRAME_COUNT = 441;

  private DefaultAudioSink defaultAudioSink;
  private ArrayAudioBufferSink arrayAudioBufferSink;

  @Before
  public void setUp() {
    // For capturing output.
    arrayAudioBufferSink = new ArrayAudioBufferSink();
    TeeAudioProcessor teeAudioProcessor = new TeeAudioProcessor(arrayAudioBufferSink);
    defaultAudioSink =
        new DefaultAudioSink(
            AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES,
            new DefaultAudioSink.DefaultAudioProcessorChain(teeAudioProcessor),
            /* enableConvertHighResIntPcmToFloat= */ false);
  }

  @Test
  public void handlesSpecializedAudioProcessorArray() {
    defaultAudioSink =
        new DefaultAudioSink(
            AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES, new TeeAudioProcessor[0]);
  }

  @Test
  public void handlesBufferAfterReset() throws Exception {
    configureDefaultAudioSink(CHANNEL_COUNT_STEREO);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);

    // After reset and re-configure we can successfully queue more input.
    defaultAudioSink.reset();
    configureDefaultAudioSink(CHANNEL_COUNT_STEREO);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);
  }

  @Test
  public void handlesBufferAfterReset_withPlaybackParameters() throws Exception {
    PlaybackParameters playbackParameters = new PlaybackParameters(1.5f);
    defaultAudioSink.setPlaybackParameters(playbackParameters);
    configureDefaultAudioSink(CHANNEL_COUNT_STEREO);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);

    // After reset and re-configure we can successfully queue more input.
    defaultAudioSink.reset();
    configureDefaultAudioSink(CHANNEL_COUNT_STEREO);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);
    assertThat(defaultAudioSink.getPlaybackParameters()).isEqualTo(playbackParameters);
  }

  @Test
  public void handlesBufferAfterReset_withFormatChange() throws Exception {
    configureDefaultAudioSink(CHANNEL_COUNT_STEREO);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);

    // After reset and re-configure we can successfully queue more input.
    defaultAudioSink.reset();
    configureDefaultAudioSink(CHANNEL_COUNT_MONO);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);
  }

  @Test
  public void handlesBufferAfterReset_withFormatChangeAndPlaybackParameters() throws Exception {
    PlaybackParameters playbackParameters = new PlaybackParameters(1.5f);
    defaultAudioSink.setPlaybackParameters(playbackParameters);
    configureDefaultAudioSink(CHANNEL_COUNT_STEREO);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);

    // After reset and re-configure we can successfully queue more input.
    defaultAudioSink.reset();
    configureDefaultAudioSink(CHANNEL_COUNT_MONO);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);
    assertThat(defaultAudioSink.getPlaybackParameters()).isEqualTo(playbackParameters);
  }

  @Test
  public void trimsStartFrames() throws Exception {
    configureDefaultAudioSink(
        CHANNEL_COUNT_STEREO,
        /* trimStartFrames= */ TRIM_100_MS_FRAME_COUNT,
        /* trimEndFrames= */ 0);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);

    assertThat(arrayAudioBufferSink.output)
        .hasLength(
            (BYTES_PER_FRAME_16_BIT
                * CHANNEL_COUNT_STEREO
                * (SAMPLE_RATE_44_1 - TRIM_100_MS_FRAME_COUNT)));
  }

  @Test
  public void trimsEndFrames() throws Exception {
    configureDefaultAudioSink(
        CHANNEL_COUNT_STEREO,
        /* trimStartFrames= */ 0,
        /* trimEndFrames= */ TRIM_10_MS_FRAME_COUNT);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);

    assertThat(arrayAudioBufferSink.output)
        .hasLength(
            (BYTES_PER_FRAME_16_BIT
                * CHANNEL_COUNT_STEREO
                * (SAMPLE_RATE_44_1 - TRIM_10_MS_FRAME_COUNT)));
  }

  @Test
  public void trimsStartAndEndFrames() throws Exception {
    configureDefaultAudioSink(
        CHANNEL_COUNT_STEREO,
        /* trimStartFrames= */ TRIM_100_MS_FRAME_COUNT,
        /* trimEndFrames= */ TRIM_10_MS_FRAME_COUNT);
    defaultAudioSink.handleBuffer(createDefaultSilenceBuffer(), /* presentationTimeUs= */ 0);

    assertThat(arrayAudioBufferSink.output)
        .hasLength(
            (BYTES_PER_FRAME_16_BIT
                * CHANNEL_COUNT_STEREO
                * (SAMPLE_RATE_44_1 - TRIM_100_MS_FRAME_COUNT - TRIM_10_MS_FRAME_COUNT)));
  }

  @Test
  public void getCurrentPosition_returnsPositionFromFirstBuffer() throws Exception {
    configureDefaultAudioSink(CHANNEL_COUNT_STEREO);
    defaultAudioSink.handleBuffer(
        createDefaultSilenceBuffer(), /* presentationTimeUs= */ 5 * C.MICROS_PER_SECOND);
    assertThat(defaultAudioSink.getCurrentPositionUs(/* sourceEnded= */ false))
        .isEqualTo(5 * C.MICROS_PER_SECOND);

    defaultAudioSink.reset();
    configureDefaultAudioSink(CHANNEL_COUNT_STEREO);
    defaultAudioSink.handleBuffer(
        createDefaultSilenceBuffer(), /* presentationTimeUs= */ 8 * C.MICROS_PER_SECOND);
    assertThat(defaultAudioSink.getCurrentPositionUs(/* sourceEnded= */ false))
        .isEqualTo(8 * C.MICROS_PER_SECOND);
  }

  @Config(minSdk = OLDEST_SDK, maxSdk = 20)
  @Test
  public void doesNotSupportFloatOutputBeforeApi21() {
    assertThat(defaultAudioSink.supportsOutput(CHANNEL_COUNT_STEREO, C.ENCODING_PCM_FLOAT))
        .isFalse();
  }

  @Config(minSdk = 21, maxSdk = NEWEST_SDK)
  @Test
  public void supportsFloatOutputFromApi21() {
    assertThat(defaultAudioSink.supportsOutput(CHANNEL_COUNT_STEREO, C.ENCODING_PCM_FLOAT))
        .isTrue();
  }

  private void configureDefaultAudioSink(int channelCount) throws AudioSink.ConfigurationException {
    configureDefaultAudioSink(channelCount, /* trimStartFrames= */ 0, /* trimEndFrames= */ 0);
  }

  private void configureDefaultAudioSink(int channelCount, int trimStartFrames, int trimEndFrames)
      throws AudioSink.ConfigurationException {
    defaultAudioSink.configure(
        C.ENCODING_PCM_16BIT,
        channelCount,
        SAMPLE_RATE_44_1,
        /* specifiedBufferSize= */ 0,
        /* outputChannels= */ null,
        /* trimStartFrames= */ trimStartFrames,
        /* trimEndFrames= */ trimEndFrames);
  }

  /** Creates a one second silence buffer for 44.1 kHz stereo 16-bit audio. */
  private static ByteBuffer createDefaultSilenceBuffer() {
    return ByteBuffer.allocateDirect(
            SAMPLE_RATE_44_1 * CHANNEL_COUNT_STEREO * BYTES_PER_FRAME_16_BIT)
        .order(ByteOrder.nativeOrder());
  }

  private static final class ArrayAudioBufferSink implements TeeAudioProcessor.AudioBufferSink {

    private byte[] output;

    public ArrayAudioBufferSink() {
      output = new byte[0];
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding) {
      output = new byte[0];
    }

    @Override
    public void handleBuffer(ByteBuffer buffer) {
      int position = buffer.position();
      int remaining = buffer.remaining();
      output = Arrays.copyOf(output, output.length + remaining);
      buffer.get(output, 0, remaining);
      buffer.position(position);
    }
  }
}
