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
package com.google.android.exoplayer2.mediacodec;

import android.media.MediaCodec;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import java.util.List;

/**
 * Selector of {@link MediaCodec} instances.
 */
public interface MediaCodecSelector {

  /**
   * Default implementation of {@link MediaCodecSelector}, which returns the preferred decoder for
   * the given format.
   */
  MediaCodecSelector DEFAULT =
      new MediaCodecSelector() {
        @Override
        public List<MediaCodecInfo> getDecoderInfos(
            String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder)
            throws DecoderQueryException {
          return MediaCodecUtil.getDecoderInfos(
              mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
        }

        @Override
        public @Nullable MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
          return MediaCodecUtil.getPassthroughDecoderInfo();
        }
      };

  /**
   * Returns a list of decoders that can decode media in the specified MIME type, in priority order.
   *
   * @param mimeType The MIME type for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @param requiresTunnelingDecoder Whether a tunneling decoder is required.
   * @return An unmodifiable list of {@link MediaCodecInfo}s corresponding to decoders. May be
   *     empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  List<MediaCodecInfo> getDecoderInfos(
      String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder)
      throws DecoderQueryException;

  /**
   * Selects a decoder to instantiate for audio passthrough.
   *
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder exists.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  @Nullable
  MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException;
}
