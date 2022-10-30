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
package com.google.android.exoplayer2.ext.opus;

import android.os.Handler;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.SimpleDecoderAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * Decodes and renders audio using the native Opus decoder.
 */
public final class LibopusAudioRenderer extends SimpleDecoderAudioRenderer {

  /** The number of input and output buffers. */
  private static final int NUM_BUFFERS = 16;
  /** The default input buffer size. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 960 * 6;

  private OpusDecoder decoder;

  public LibopusAudioRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public LibopusAudioRenderer(
      Handler eventHandler,
      AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    super(eventHandler, eventListener, audioProcessors);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param drmSessionManager For use with encrypted media. May be null if support for encrypted
   *     media is not required.
   * @param playClearSamplesWithoutKeys Encrypted media may contain clear (un-encrypted) regions.
   *     For example a media file may start with a short clear region so as to allow playback to
   *     begin in parallel with key acquisition. This parameter specifies whether the renderer is
   *     permitted to play clear regions of encrypted media files before {@code drmSessionManager}
   *     has obtained the keys necessary to decrypt encrypted regions of the media.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public LibopusAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener,
      DrmSessionManager<ExoMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
      AudioProcessor... audioProcessors) {
    super(eventHandler, eventListener, null, drmSessionManager, playClearSamplesWithoutKeys,
        audioProcessors);
  }

  @Override
  protected int supportsFormatInternal(DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      Format format) {
    if (!OpusLibrary.isAvailable()
        || !MimeTypes.AUDIO_OPUS.equalsIgnoreCase(format.sampleMimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    } else if (!supportsOutput(format.channelCount, C.ENCODING_PCM_16BIT)) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    } else if (!supportsFormatDrm(drmSessionManager, format.drmInitData)) {
      return FORMAT_UNSUPPORTED_DRM;
    } else {
      return FORMAT_HANDLED;
    }
  }

  @Override
  protected OpusDecoder createDecoder(Format format, ExoMediaCrypto mediaCrypto)
      throws OpusDecoderException {
    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    decoder =
        new OpusDecoder(
            NUM_BUFFERS,
            NUM_BUFFERS,
            initialInputBufferSize,
            format.initializationData,
            mediaCrypto);
    return decoder;
  }

  @Override
  protected Format getOutputFormat() {
    return Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW, null, Format.NO_VALUE,
        Format.NO_VALUE, decoder.getChannelCount(), decoder.getSampleRate(), C.ENCODING_PCM_16BIT,
        null, null, 0, null);
  }

}
