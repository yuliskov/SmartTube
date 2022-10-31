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

import static com.google.android.exoplayer2.RendererCapabilities.ADAPTIVE_NOT_SEAMLESS;
import static com.google.android.exoplayer2.RendererCapabilities.FORMAT_HANDLED;
import static com.google.android.exoplayer2.RendererCapabilities.TUNNELING_NOT_SUPPORTED;
import static com.google.android.exoplayer2.RendererCapabilities.TUNNELING_SUPPORTED;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.testutil.FakeSampleStream;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;

/** Unit test for {@link SimpleDecoderAudioRenderer}. */
@RunWith(AndroidJUnit4.class)
public class SimpleDecoderAudioRendererTest {

  private static final Format FORMAT = Format.createSampleFormat(null, MimeTypes.AUDIO_RAW, 0);

  @Mock private AudioSink mockAudioSink;
  private SimpleDecoderAudioRenderer audioRenderer;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    audioRenderer = new SimpleDecoderAudioRenderer(null, null, null, false, mockAudioSink) {
      @Override
      protected int supportsFormatInternal(DrmSessionManager<ExoMediaCrypto> drmSessionManager,
          Format format) {
        return FORMAT_HANDLED;
      }

      @Override
      protected SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer,
          ? extends AudioDecoderException> createDecoder(Format format, ExoMediaCrypto mediaCrypto)
          throws AudioDecoderException {
        return new FakeDecoder();
      }
    };
  }

  @Config(sdk = 19)
  @Test
  public void testSupportsFormatAtApi19() {
    assertThat(audioRenderer.supportsFormat(FORMAT))
        .isEqualTo(ADAPTIVE_NOT_SEAMLESS | TUNNELING_NOT_SUPPORTED | FORMAT_HANDLED);
  }

  @Config(sdk = 21)
  @Test
  public void testSupportsFormatAtApi21() {
    // From API 21, tunneling is supported.
    assertThat(audioRenderer.supportsFormat(FORMAT))
        .isEqualTo(ADAPTIVE_NOT_SEAMLESS | TUNNELING_SUPPORTED | FORMAT_HANDLED);
  }

  @Test
  public void testImmediatelyReadEndOfStreamPlaysAudioSinkToEndOfStream() throws Exception {
    audioRenderer.enable(
        RendererConfiguration.DEFAULT,
        new Format[] {FORMAT},
        new FakeSampleStream(FORMAT, /* eventDispatcher= */ null, /* shouldOutputSample= */ false),
        0,
        false,
        0);
    audioRenderer.setCurrentStreamFinal();
    when(mockAudioSink.isEnded()).thenReturn(true);
    while (!audioRenderer.isEnded()) {
      audioRenderer.render(0, 0);
    }
    verify(mockAudioSink, times(1)).playToEndOfStream();
    audioRenderer.disable();
    audioRenderer.reset();
    verify(mockAudioSink, times(1)).reset();
  }

  private static final class FakeDecoder
      extends SimpleDecoder<DecoderInputBuffer, SimpleOutputBuffer, AudioDecoderException> {

    public FakeDecoder() {
      super(new DecoderInputBuffer[1], new SimpleOutputBuffer[1]);
    }

    @Override
    public String getName() {
      return "FakeDecoder";
    }

    @Override
    protected DecoderInputBuffer createInputBuffer() {
      return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    }

    @Override
    protected SimpleOutputBuffer createOutputBuffer() {
      return new SimpleOutputBuffer(this);
    }

    @Override
    protected AudioDecoderException createUnexpectedDecodeException(Throwable error) {
      return new AudioDecoderException("Unexpected decode error", error);
    }

    @Override
    protected AudioDecoderException decode(DecoderInputBuffer inputBuffer,
        SimpleOutputBuffer outputBuffer, boolean reset) {
      if (inputBuffer.isEndOfStream()) {
        outputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      }
      return null;
    }

  }

}
