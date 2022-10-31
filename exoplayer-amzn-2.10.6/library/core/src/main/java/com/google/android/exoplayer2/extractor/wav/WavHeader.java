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
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.Util;

/** Header for a WAV file. */
/* package */ final class WavHeader implements SeekMap {

  /** Number of audio chanels. */
  private final int numChannels;
  /** Sample rate in Hertz. */
  private final int sampleRateHz;
  /** Average bytes per second for the sample data. */
  private final int averageBytesPerSecond;
  /** Alignment for frames of audio data; should equal {@code numChannels * bitsPerSample / 8}. */
  private final int blockAlignment;
  /** Bits per sample for the audio data. */
  private final int bitsPerSample;
  /** The PCM encoding. */
  @C.PcmEncoding private final int encoding;

  /** Position of the start of the sample data, in bytes. */
  private int dataStartPosition;
  /** Position of the end of the sample data (exclusive), in bytes. */
  private long dataEndPosition;

  public WavHeader(
      int numChannels,
      int sampleRateHz,
      int averageBytesPerSecond,
      int blockAlignment,
      int bitsPerSample,
      @C.PcmEncoding int encoding) {
    this.numChannels = numChannels;
    this.sampleRateHz = sampleRateHz;
    this.averageBytesPerSecond = averageBytesPerSecond;
    this.blockAlignment = blockAlignment;
    this.bitsPerSample = bitsPerSample;
    this.encoding = encoding;
    dataStartPosition = C.POSITION_UNSET;
    dataEndPosition = C.POSITION_UNSET;
  }

  // Data bounds.

  /**
   * Sets the data start position and size in bytes of sample data in this WAV.
   *
   * @param dataStartPosition The position of the start of the sample data, in bytes.
   * @param dataEndPosition The position of the end of the sample data (exclusive), in bytes.
   */
  public void setDataBounds(int dataStartPosition, long dataEndPosition) {
    this.dataStartPosition = dataStartPosition;
    this.dataEndPosition = dataEndPosition;
  }

  /**
   * Returns the position of the start of the sample data, in bytes, or {@link C#POSITION_UNSET} if
   * the data bounds have not been set.
   */
  public int getDataStartPosition() {
    return dataStartPosition;
  }

  /**
   * Returns the position of the end of the sample data (exclusive), in bytes, or {@link
   * C#POSITION_UNSET} if the data bounds have not been set.
   */
  public long getDataEndPosition() {
    return dataEndPosition;
  }

  /** Returns whether the data start position and size have been set. */
  public boolean hasDataBounds() {
    return dataStartPosition != C.POSITION_UNSET;
  }

  // SeekMap implementation.

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    long numFrames = (dataEndPosition - dataStartPosition) / blockAlignment;
    return (numFrames * C.MICROS_PER_SECOND) / sampleRateHz;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    long dataSize = dataEndPosition - dataStartPosition;
    long positionOffset = (timeUs * averageBytesPerSecond) / C.MICROS_PER_SECOND;
    // Constrain to nearest preceding frame offset.
    positionOffset = (positionOffset / blockAlignment) * blockAlignment;
    positionOffset = Util.constrainValue(positionOffset, 0, dataSize - blockAlignment);
    long seekPosition = dataStartPosition + positionOffset;
    long seekTimeUs = getTimeUs(seekPosition);
    SeekPoint seekPoint = new SeekPoint(seekTimeUs, seekPosition);
    if (seekTimeUs >= timeUs || positionOffset == dataSize - blockAlignment) {
      return new SeekPoints(seekPoint);
    } else {
      long secondSeekPosition = seekPosition + blockAlignment;
      long secondSeekTimeUs = getTimeUs(secondSeekPosition);
      SeekPoint secondSeekPoint = new SeekPoint(secondSeekTimeUs, secondSeekPosition);
      return new SeekPoints(seekPoint, secondSeekPoint);
    }
  }

  // Misc getters.

  /**
   * Returns the time in microseconds for the given position in bytes.
   *
   * @param position The position in bytes.
   */
  public long getTimeUs(long position) {
    long positionOffset = Math.max(0, position - dataStartPosition);
    return (positionOffset * C.MICROS_PER_SECOND) / averageBytesPerSecond;
  }

  /** Returns the bytes per frame of this WAV. */
  public int getBytesPerFrame() {
    return blockAlignment;
  }

  /** Returns the bitrate of this WAV. */
  public int getBitrate() {
    return sampleRateHz * bitsPerSample * numChannels;
  }

  /** Returns the sample rate in Hertz of this WAV. */
  public int getSampleRateHz() {
    return sampleRateHz;
  }

  /** Returns the number of audio channels in this WAV. */
  public int getNumChannels() {
    return numChannels;
  }

  /** Returns the PCM encoding. **/
  public @C.PcmEncoding int getEncoding() {
    return encoding;
  }

}
