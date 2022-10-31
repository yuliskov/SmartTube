/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.text.dvb;

import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.List;

/** A {@link SimpleSubtitleDecoder} for DVB subtitles. */
public final class DvbDecoder extends SimpleSubtitleDecoder {

  private final DvbParser parser;

  /**
   * @param initializationData The initialization data for the decoder. The initialization data
   *     must consist of a single byte array containing 5 bytes: flag_pes_stripped (1),
   *     composition_page (2), ancillary_page (2).
   */
  public DvbDecoder(List<byte[]> initializationData) {
    super("DvbDecoder");
    ParsableByteArray data = new ParsableByteArray(initializationData.get(0));
    int subtitleCompositionPage = data.readUnsignedShort();
    int subtitleAncillaryPage = data.readUnsignedShort();
    parser = new DvbParser(subtitleCompositionPage, subtitleAncillaryPage);
  }

  @Override
  protected DvbSubtitle decode(byte[] data, int length, boolean reset) {
    if (reset) {
      parser.reset();
    }
    return new DvbSubtitle(parser.decode(data, length));
  }

}
