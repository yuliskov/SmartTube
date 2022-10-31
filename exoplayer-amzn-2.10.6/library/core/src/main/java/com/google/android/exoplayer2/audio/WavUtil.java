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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;

/** Utilities for handling WAVE files. */
public final class WavUtil {

  /** Four character code for "RIFF". */
  public static final int RIFF_FOURCC = Util.getIntegerCodeForString("RIFF");
  /** Four character code for "WAVE". */
  public static final int WAVE_FOURCC = Util.getIntegerCodeForString("WAVE");
  /** Four character code for "fmt ". */
  public static final int FMT_FOURCC = Util.getIntegerCodeForString("fmt ");
  /** Four character code for "data". */
  public static final int DATA_FOURCC = Util.getIntegerCodeForString("data");

  /** WAVE type value for integer PCM audio data. */
  private static final int TYPE_PCM = 0x0001;
  /** WAVE type value for float PCM audio data. */
  private static final int TYPE_FLOAT = 0x0003;
  /** WAVE type value for 8-bit ITU-T G.711 A-law audio data. */
  private static final int TYPE_A_LAW = 0x0006;
  /** WAVE type value for 8-bit ITU-T G.711 mu-law audio data. */
  private static final int TYPE_MU_LAW = 0x0007;
  /** WAVE type value for extended WAVE format. */
  private static final int TYPE_WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

  /** Returns the WAVE type value for the given {@code encoding}. */
  public static int getTypeForEncoding(@C.PcmEncoding int encoding) {
    switch (encoding) {
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_24BIT:
      case C.ENCODING_PCM_32BIT:
        return TYPE_PCM;
      case C.ENCODING_PCM_A_LAW:
        return TYPE_A_LAW;
      case C.ENCODING_PCM_MU_LAW:
        return TYPE_MU_LAW;
      case C.ENCODING_PCM_FLOAT:
        return TYPE_FLOAT;
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalArgumentException();
    }
  }

  /** Returns the PCM encoding for the given WAVE {@code type} value. */
  public static @C.PcmEncoding int getEncodingForType(int type, int bitsPerSample) {
    switch (type) {
      case TYPE_PCM:
      case TYPE_WAVE_FORMAT_EXTENSIBLE:
        return Util.getPcmEncoding(bitsPerSample);
      case TYPE_FLOAT:
        return bitsPerSample == 32 ? C.ENCODING_PCM_FLOAT : C.ENCODING_INVALID;
      case TYPE_A_LAW:
        return C.ENCODING_PCM_A_LAW;
      case TYPE_MU_LAW:
        return C.ENCODING_PCM_MU_LAW;
      default:
        return C.ENCODING_INVALID;
    }
  }

  private WavUtil() {
    // Prevent instantiation.
  }
}
