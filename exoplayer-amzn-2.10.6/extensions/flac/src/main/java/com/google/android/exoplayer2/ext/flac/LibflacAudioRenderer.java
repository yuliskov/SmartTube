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
package com.google.android.exoplayer2.ext.flac;

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
 * Decodes and renders audio using the native Flac decoder.
 */
public class LibflacAudioRenderer extends SimpleDecoderAudioRenderer {

  private static final int NUM_BUFFERS = 16;

  public LibflacAudioRenderer() {
    this(null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public LibflacAudioRenderer(
      Handler eventHandler,
      AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    super(eventHandler, eventListener, audioProcessors);
  }

  @Override
  protected int supportsFormatInternal(DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      Format format) {
    if (!FlacLibrary.isAvailable()
        || !MimeTypes.AUDIO_FLAC.equalsIgnoreCase(format.sampleMimeType)) {
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
  protected FlacDecoder createDecoder(Format format, ExoMediaCrypto mediaCrypto)
      throws FlacDecoderException {
    return new FlacDecoder(
        NUM_BUFFERS, NUM_BUFFERS, format.maxInputSize, format.initializationData);
  }

}
