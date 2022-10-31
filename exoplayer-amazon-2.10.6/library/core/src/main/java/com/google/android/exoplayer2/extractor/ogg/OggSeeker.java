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
package com.google.android.exoplayer2.extractor.ogg;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import java.io.IOException;

/**
 * Used to seek in an Ogg stream. OggSeeker implementation may do direct seeking or progressive
 * seeking. OggSeeker works together with a {@link SeekMap} instance to capture the queried position
 * and start the seeking with an initial estimated position.
 */
/* package */ interface OggSeeker {

  /**
   * Returns a {@link SeekMap} that returns an initial estimated position for progressive seeking
   * or the final position for direct seeking. Returns null if {@link #read} has yet to return -1.
   */
  SeekMap createSeekMap();

  /**
   * Starts a seek operation.
   *
   * @param targetGranule The target granule position.
   */
  void startSeek(long targetGranule);

  /**
   * Reads data from the {@link ExtractorInput} to build the {@link SeekMap} or to continue a seek.
   * <p/>
   * If more data is required or if the position of the input needs to be modified then a position
   * from which data should be provided is returned. Else a negative value is returned. If a seek
   * has been completed then the value returned is -(currentGranule + 2). Else it is -1.
   *
   * @param input The {@link ExtractorInput} to read from.
   * @return A non-negative position to seek the {@link ExtractorInput} to, or -(currentGranule + 2)
   *     if the progressive seek has completed, or -1 otherwise.
   * @throws IOException If reading from the {@link ExtractorInput} fails.
   * @throws InterruptedException If the thread is interrupted.
   */
  long read(ExtractorInput input) throws IOException, InterruptedException;

}
