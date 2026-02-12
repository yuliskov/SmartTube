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
package com.google.android.exoplayer2.metadata;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.emsg.EventMessageDecoder;
import com.google.android.exoplayer2.metadata.icy.IcyDecoder;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.metadata.scte35.SpliceInfoDecoder;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * A factory for {@link MetadataDecoder} instances.
 */
public interface MetadataDecoderFactory {

  /**
   * Returns whether the factory is able to instantiate a {@link MetadataDecoder} for the given
   * {@link Format}.
   *
   * @param format The {@link Format}.
   * @return Whether the factory can instantiate a suitable {@link MetadataDecoder}.
   */
  boolean supportsFormat(Format format);

  /**
   * Creates a {@link MetadataDecoder} for the given {@link Format}.
   *
   * @param format The {@link Format}.
   * @return A new {@link MetadataDecoder}.
   * @throws IllegalArgumentException If the {@link Format} is not supported.
   */
  MetadataDecoder createDecoder(Format format);

  /**
   * Default {@link MetadataDecoder} implementation.
   *
   * <p>The formats supported by this factory are:
   *
   * <ul>
   *   <li>ID3 ({@link Id3Decoder})
   *   <li>EMSG ({@link EventMessageDecoder})
   *   <li>SCTE-35 ({@link SpliceInfoDecoder})
   *   <li>ICY ({@link IcyDecoder})
   * </ul>
   */
  MetadataDecoderFactory DEFAULT =
      new MetadataDecoderFactory() {

        @Override
        public boolean supportsFormat(Format format) {
          String mimeType = format.sampleMimeType;
          return MimeTypes.APPLICATION_ID3.equals(mimeType)
              || MimeTypes.APPLICATION_EMSG.equals(mimeType)
              || MimeTypes.APPLICATION_SCTE35.equals(mimeType)
              || MimeTypes.APPLICATION_ICY.equals(mimeType);
        }

        @Override
        public MetadataDecoder createDecoder(Format format) {
          switch (format.sampleMimeType) {
            case MimeTypes.APPLICATION_ID3:
              return new Id3Decoder();
            case MimeTypes.APPLICATION_EMSG:
              return new EventMessageDecoder();
            case MimeTypes.APPLICATION_SCTE35:
              return new SpliceInfoDecoder();
            case MimeTypes.APPLICATION_ICY:
              return new IcyDecoder();
            default:
              throw new IllegalArgumentException(
                  "Attempted to create decoder for unsupported format");
          }
        }
      };
}
