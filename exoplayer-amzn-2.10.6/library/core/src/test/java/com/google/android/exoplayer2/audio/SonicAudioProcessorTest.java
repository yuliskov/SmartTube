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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SonicAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class SonicAudioProcessorTest {

  private SonicAudioProcessor sonicAudioProcessor;

  @Before
  public void setUp() {
    sonicAudioProcessor = new SonicAudioProcessor();
  }

  @Test
  public void testReconfigureWithSameSampleRate() throws Exception {
    // When configured for resampling from 44.1 kHz to 48 kHz, the output sample rate is correct.
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    assertThat(sonicAudioProcessor.getOutputSampleRateHz()).isEqualTo(48000);
    assertThat(sonicAudioProcessor.isActive()).isTrue();
    // When reconfigured with 48 kHz input, there is no resampling.
    sonicAudioProcessor.configure(48000, 2, C.ENCODING_PCM_16BIT);
    assertThat(sonicAudioProcessor.getOutputSampleRateHz()).isEqualTo(48000);
    assertThat(sonicAudioProcessor.isActive()).isFalse();
    // When reconfigure with 44.1 kHz input, resampling is enabled again.
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    assertThat(sonicAudioProcessor.getOutputSampleRateHz()).isEqualTo(48000);
    assertThat(sonicAudioProcessor.isActive()).isTrue();
  }

  @Test
  public void testNoSampleRateChange() throws Exception {
    // Configure for resampling 44.1 kHz to 48 kHz.
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    // Reconfigure to not modify the sample rate.
    sonicAudioProcessor.setOutputSampleRateHz(SonicAudioProcessor.SAMPLE_RATE_NO_CHANGE);
    sonicAudioProcessor.configure(22050, 2, C.ENCODING_PCM_16BIT);
    // The sample rate is unmodified, and the audio processor is not active.
    assertThat(sonicAudioProcessor.getOutputSampleRateHz()).isEqualTo(22050);
    assertThat(sonicAudioProcessor.isActive()).isFalse();
  }

  @Test
  public void testBecomesActiveAfterConfigure() throws Exception {
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    // Set a new sample rate.
    sonicAudioProcessor.setOutputSampleRateHz(22050);
    // The new sample rate is not active yet.
    assertThat(sonicAudioProcessor.isActive()).isFalse();
    assertThat(sonicAudioProcessor.getOutputSampleRateHz()).isEqualTo(44100);
  }

  @Test
  public void testSampleRateChangeBecomesActiveAfterConfigure() throws Exception {
    // Configure for resampling 44.1 kHz to 48 kHz.
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    // Set a new sample rate, which isn't active yet.
    sonicAudioProcessor.setOutputSampleRateHz(22050);
    assertThat(sonicAudioProcessor.getOutputSampleRateHz()).isEqualTo(48000);
    // The new sample rate takes effect on reconfiguration.
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    assertThat(sonicAudioProcessor.getOutputSampleRateHz()).isEqualTo(22050);
  }

  @Test
  public void testIsActiveWithSpeedChange() throws Exception {
    sonicAudioProcessor.setSpeed(1.5f);
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    sonicAudioProcessor.flush();
    assertThat(sonicAudioProcessor.isActive()).isTrue();
  }

  @Test
  public void testIsActiveWithPitchChange() throws Exception {
    sonicAudioProcessor.setPitch(1.5f);
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    sonicAudioProcessor.flush();
    assertThat(sonicAudioProcessor.isActive()).isTrue();
  }

  @Test
  public void testIsNotActiveWithNoChange() throws Exception {
    sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_16BIT);
    assertThat(sonicAudioProcessor.isActive()).isFalse();
  }

  @Test
  public void testDoesNotSupportNon16BitInput() throws Exception {
    try {
      sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_8BIT);
      fail();
    } catch (AudioProcessor.UnhandledFormatException e) {
      // Expected.
    }
    try {
      sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_24BIT);
      fail();
    } catch (AudioProcessor.UnhandledFormatException e) {
      // Expected.
    }
    try {
      sonicAudioProcessor.configure(44100, 2, C.ENCODING_PCM_32BIT);
      fail();
    } catch (AudioProcessor.UnhandledFormatException e) {
      // Expected.
    }
  }

}
