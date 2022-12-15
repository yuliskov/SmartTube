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
package com.google.android.exoplayer2.ext.ffmpeg;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.SimpleDecoderAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.Collections;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Decodes and renders audio using FFmpeg.
 */
public final class FfmpegAudioRenderer extends SimpleDecoderAudioRenderer {

  /** The number of input and output buffers. */
  private static final int NUM_BUFFERS = 16;
  /** The default input buffer size. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 960 * 6;

  private final boolean enableFloatOutput;

  private @MonotonicNonNull FfmpegDecoder decoder;

  public FfmpegAudioRenderer() {
    this(/* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public FfmpegAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    this(
        eventHandler,
        eventListener,
        new DefaultAudioSink(/* audioCapabilities= */ null, audioProcessors),
        /* enableFloatOutput= */ false);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   * @param enableFloatOutput Whether to enable 32-bit float audio format, if supported on the
   *     device/build and if the input format may have bit depth higher than 16-bit. When using
   *     32-bit float output, any audio processing will be disabled, including playback speed/pitch
   *     adjustment.
   */
  public FfmpegAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink,
      boolean enableFloatOutput) {
    super(
        eventHandler,
        eventListener,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false,
        audioSink);
    this.enableFloatOutput = enableFloatOutput;
  }

  @Override
  protected int supportsFormatInternal(DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      Format format) {
    Assertions.checkNotNull(format.sampleMimeType);
    if (!FfmpegLibrary.isAvailable()) {
      return FORMAT_UNSUPPORTED_TYPE;
    } else if (!FfmpegLibrary.supportsFormat(format.sampleMimeType, format.pcmEncoding)
        || !isOutputSupported(format)) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    } else if (!supportsFormatDrm(drmSessionManager, format.drmInitData)) {
      return FORMAT_UNSUPPORTED_DRM;
    } else {
      return FORMAT_HANDLED;
    }
  }

  @Override
  public final int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  protected FfmpegDecoder createDecoder(Format format, ExoMediaCrypto mediaCrypto)
      throws FfmpegDecoderException {
    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    decoder =
        new FfmpegDecoder(
            NUM_BUFFERS, NUM_BUFFERS, initialInputBufferSize, format, shouldUseFloatOutput(format));
    return decoder;
  }

  @Override
  public Format getOutputFormat() {
    Assertions.checkNotNull(decoder);
    int channelCount = decoder.getChannelCount();
    int sampleRate = decoder.getSampleRate();
    @C.PcmEncoding int encoding = decoder.getEncoding();
    return Format.createAudioSampleFormat(
        /* id= */ null,
        MimeTypes.AUDIO_RAW,
        /* codecs= */ null,
        Format.NO_VALUE,
        Format.NO_VALUE,
        channelCount,
        sampleRate,
        encoding,
        Collections.emptyList(),
        /* drmInitData= */ null,
        /* selectionFlags= */ 0,
        /* language= */ null);
  }

  private boolean isOutputSupported(Format inputFormat) {
    return shouldUseFloatOutput(inputFormat)
        || supportsOutput(inputFormat.channelCount, C.ENCODING_PCM_16BIT);
  }

  private boolean shouldUseFloatOutput(Format inputFormat) {
    Assertions.checkNotNull(inputFormat.sampleMimeType);
    if (!enableFloatOutput || !supportsOutput(inputFormat.channelCount, C.ENCODING_PCM_FLOAT)) {
      return false;
    }
    switch (inputFormat.sampleMimeType) {
      case MimeTypes.AUDIO_RAW:
        // For raw audio, output in 32-bit float encoding if the bit depth is > 16-bit.
        return inputFormat.pcmEncoding == C.ENCODING_PCM_24BIT
            || inputFormat.pcmEncoding == C.ENCODING_PCM_32BIT
            || inputFormat.pcmEncoding == C.ENCODING_PCM_FLOAT;
      case MimeTypes.AUDIO_AC3:
        // AC-3 is always 16-bit, so there is no point outputting in 32-bit float encoding.
        return false;
      default:
        // For all other formats, assume that it's worth using 32-bit float encoding.
        return true;
    }
  }

}
