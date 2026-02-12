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
package com.google.android.exoplayer2.extractor;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;

/**
 * A dummy {@link TrackOutput} implementation.
 */
public final class DummyTrackOutput implements TrackOutput {

  @Override
  public void format(Format format) {
    // Do nothing.
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    int bytesSkipped = input.skip(length);
    if (bytesSkipped == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    return bytesSkipped;
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    data.skipBytes(length);
  }

  @Override
  public void sampleMetadata(
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable CryptoData cryptoData) {
    // Do nothing.
  }
}
