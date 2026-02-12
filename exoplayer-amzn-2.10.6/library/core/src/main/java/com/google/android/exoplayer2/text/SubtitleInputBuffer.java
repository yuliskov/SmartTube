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
package com.google.android.exoplayer2.text;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

/** A {@link DecoderInputBuffer} for a {@link SubtitleDecoder}. */
public class SubtitleInputBuffer extends DecoderInputBuffer {

  /**
   * An offset that must be added to the subtitle's event times after it's been decoded, or
   * {@link Format#OFFSET_SAMPLE_RELATIVE} if {@link #timeUs} should be added.
   */
  public long subsampleOffsetUs;

  public SubtitleInputBuffer() {
    super(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

}
