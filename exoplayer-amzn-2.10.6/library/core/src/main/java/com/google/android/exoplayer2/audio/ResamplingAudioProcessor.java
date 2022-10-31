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
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import java.nio.ByteBuffer;

/**
 * An {@link AudioProcessor} that converts 8-bit, 24-bit and 32-bit integer PCM audio to 16-bit
 * integer PCM audio.
 */
/* package */ final class ResamplingAudioProcessor extends BaseAudioProcessor {

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding)
      throws UnhandledFormatException {
    if (encoding != C.ENCODING_PCM_8BIT && encoding != C.ENCODING_PCM_16BIT
        && encoding != C.ENCODING_PCM_24BIT && encoding != C.ENCODING_PCM_32BIT) {
      throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
    }
    return setInputFormat(sampleRateHz, channelCount, encoding);
  }

  @Override
  public boolean isActive() {
    return encoding != C.ENCODING_INVALID && encoding != C.ENCODING_PCM_16BIT;
  }

  @Override
  public int getOutputEncoding() {
    return C.ENCODING_PCM_16BIT;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    // Prepare the output buffer.
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int size = limit - position;
    int resampledSize;
    switch (encoding) {
      case C.ENCODING_PCM_8BIT:
        resampledSize = size * 2;
        break;
      case C.ENCODING_PCM_24BIT:
        resampledSize = (size / 3) * 2;
        break;
      case C.ENCODING_PCM_32BIT:
        resampledSize = size / 2;
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_PCM_A_LAW:
      case C.ENCODING_PCM_MU_LAW:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalStateException();
    }

    // Resample the little endian input and update the input/output buffers.
    ByteBuffer buffer = replaceOutputBuffer(resampledSize);
    switch (encoding) {
      case C.ENCODING_PCM_8BIT:
        // 8->16 bit resampling. Shift each byte from [0, 256) to [-128, 128) and scale up.
        for (int i = position; i < limit; i++) {
          buffer.put((byte) 0);
          buffer.put((byte) ((inputBuffer.get(i) & 0xFF) - 128));
        }
        break;
      case C.ENCODING_PCM_24BIT:
        // 24->16 bit resampling. Drop the least significant byte.
        for (int i = position; i < limit; i += 3) {
          buffer.put(inputBuffer.get(i + 1));
          buffer.put(inputBuffer.get(i + 2));
        }
        break;
      case C.ENCODING_PCM_32BIT:
        // 32->16 bit resampling. Drop the two least significant bytes.
        for (int i = position; i < limit; i += 4) {
          buffer.put(inputBuffer.get(i + 2));
          buffer.put(inputBuffer.get(i + 3));
        }
        break;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_PCM_A_LAW:
      case C.ENCODING_PCM_MU_LAW:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        // Never happens.
        throw new IllegalStateException();
    }
    inputBuffer.position(inputBuffer.limit());
    buffer.flip();
  }

}
