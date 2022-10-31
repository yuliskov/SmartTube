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
package com.google.android.exoplayer2.extractor.wav;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;

/**
 * Extracts data from WAV byte streams.
 */
public final class WavExtractor implements Extractor {

  /** Factory for {@link WavExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new WavExtractor()};

  /** Arbitrary maximum input size of 32KB, which is ~170ms of 16-bit stereo PCM audio at 48KHz. */
  private static final int MAX_INPUT_SIZE = 32 * 1024;

  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;
  private WavHeader wavHeader;
  private int bytesPerFrame;
  private int pendingBytes;

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return WavHeaderReader.peek(input) != null;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(0, C.TRACK_TYPE_AUDIO);
    wavHeader = null;
    output.endTracks();
  }

  @Override
  public void seek(long position, long timeUs) {
    pendingBytes = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (wavHeader == null) {
      wavHeader = WavHeaderReader.peek(input);
      if (wavHeader == null) {
        // Should only happen if the media wasn't sniffed.
        throw new ParserException("Unsupported or unrecognized wav header.");
      }
      Format format = Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW, null,
          wavHeader.getBitrate(), MAX_INPUT_SIZE, wavHeader.getNumChannels(),
          wavHeader.getSampleRateHz(), wavHeader.getEncoding(), null, null, 0, null);
      trackOutput.format(format);
      bytesPerFrame = wavHeader.getBytesPerFrame();
    }

    if (!wavHeader.hasDataBounds()) {
      WavHeaderReader.skipToData(input, wavHeader);
      extractorOutput.seekMap(wavHeader);
    } else if (input.getPosition() == 0) {
      input.skipFully(wavHeader.getDataStartPosition());
    }

    long dataEndPosition = wavHeader.getDataEndPosition();
    Assertions.checkState(dataEndPosition != C.POSITION_UNSET);

    long bytesLeft = dataEndPosition - input.getPosition();
    if (bytesLeft <= 0) {
      return Extractor.RESULT_END_OF_INPUT;
    }

    int maxBytesToRead = (int) Math.min(MAX_INPUT_SIZE - pendingBytes, bytesLeft);
    int bytesAppended = trackOutput.sampleData(input, maxBytesToRead, true);
    if (bytesAppended != RESULT_END_OF_INPUT) {
      pendingBytes += bytesAppended;
    }

    // Samples must consist of a whole number of frames.
    int pendingFrames = pendingBytes / bytesPerFrame;
    if (pendingFrames > 0) {
      long timeUs = wavHeader.getTimeUs(input.getPosition() - pendingBytes);
      int size = pendingFrames * bytesPerFrame;
      pendingBytes -= size;
      trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, size, pendingBytes, null);
    }

    return bytesAppended == RESULT_END_OF_INPUT ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
  }

}
