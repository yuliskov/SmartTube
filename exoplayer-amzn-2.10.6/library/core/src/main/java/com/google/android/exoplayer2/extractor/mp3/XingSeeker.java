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
package com.google.android.exoplayer2.extractor.mp3;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.MpegAudioHeader;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

/** MP3 seeker that uses metadata from a Xing header. */
/* package */ final class XingSeeker implements Seeker {

  private static final String TAG = "XingSeeker";

  /**
   * Returns a {@link XingSeeker} for seeking in the stream, if required information is present.
   * Returns {@code null} if not. On returning, {@code frame}'s position is not specified so the
   * caller should reset it.
   *
   * @param inputLength The length of the stream in bytes, or {@link C#LENGTH_UNSET} if unknown.
   * @param position The position of the start of this frame in the stream.
   * @param mpegAudioHeader The MPEG audio header associated with the frame.
   * @param frame The data in this audio frame, with its position set to immediately after the
   *     'Xing' or 'Info' tag.
   * @return A {@link XingSeeker} for seeking in the stream, or {@code null} if the required
   *     information is not present.
   */
  public static @Nullable XingSeeker create(
      long inputLength, long position, MpegAudioHeader mpegAudioHeader, ParsableByteArray frame) {
    int samplesPerFrame = mpegAudioHeader.samplesPerFrame;
    int sampleRate = mpegAudioHeader.sampleRate;

    int flags = frame.readInt();
    int frameCount;
    if ((flags & 0x01) != 0x01 || (frameCount = frame.readUnsignedIntToInt()) == 0) {
      // If the frame count is missing/invalid, the header can't be used to determine the duration.
      return null;
    }
    long durationUs = Util.scaleLargeTimestamp(frameCount, samplesPerFrame * C.MICROS_PER_SECOND,
        sampleRate);
    if ((flags & 0x06) != 0x06) {
      // If the size in bytes or table of contents is missing, the stream is not seekable.
      return new XingSeeker(position, mpegAudioHeader.frameSize, durationUs);
    }

    long dataSize = frame.readUnsignedIntToInt();
    long[] tableOfContents = new long[100];
    for (int i = 0; i < 100; i++) {
      tableOfContents[i] = frame.readUnsignedByte();
    }

    // TODO: Handle encoder delay and padding in 3 bytes offset by xingBase + 213 bytes:
    // delay = (frame.readUnsignedByte() << 4) + (frame.readUnsignedByte() >> 4);
    // padding = ((frame.readUnsignedByte() & 0x0F) << 8) + frame.readUnsignedByte();

    if (inputLength != C.LENGTH_UNSET && inputLength != position + dataSize) {
      Log.w(TAG, "XING data size mismatch: " + inputLength + ", " + (position + dataSize));
    }
    return new XingSeeker(
        position, mpegAudioHeader.frameSize, durationUs, dataSize, tableOfContents);
  }

  private final long dataStartPosition;
  private final int xingFrameSize;
  private final long durationUs;
  /** Data size, including the XING frame. */
  private final long dataSize;

  private final long dataEndPosition;
  /**
   * Entries are in the range [0, 255], but are stored as long integers for convenience. Null if the
   * table of contents was missing from the header, in which case seeking is not be supported.
   */
  private final @Nullable long[] tableOfContents;

  private XingSeeker(long dataStartPosition, int xingFrameSize, long durationUs) {
    this(
        dataStartPosition,
        xingFrameSize,
        durationUs,
        /* dataSize= */ C.LENGTH_UNSET,
        /* tableOfContents= */ null);
  }

  private XingSeeker(
      long dataStartPosition,
      int xingFrameSize,
      long durationUs,
      long dataSize,
      @Nullable long[] tableOfContents) {
    this.dataStartPosition = dataStartPosition;
    this.xingFrameSize = xingFrameSize;
    this.durationUs = durationUs;
    this.tableOfContents = tableOfContents;
    this.dataSize = dataSize;
    dataEndPosition = dataSize == C.LENGTH_UNSET ? C.POSITION_UNSET : dataStartPosition + dataSize;
  }

  @Override
  public boolean isSeekable() {
    return tableOfContents != null;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    if (!isSeekable()) {
      return new SeekPoints(new SeekPoint(0, dataStartPosition + xingFrameSize));
    }
    timeUs = Util.constrainValue(timeUs, 0, durationUs);
    double percent = (timeUs * 100d) / durationUs;
    double scaledPosition;
    if (percent <= 0) {
      scaledPosition = 0;
    } else if (percent >= 100) {
      scaledPosition = 256;
    } else {
      int prevTableIndex = (int) percent;
      long[] tableOfContents = Assertions.checkNotNull(this.tableOfContents);
      double prevScaledPosition = tableOfContents[prevTableIndex];
      double nextScaledPosition = prevTableIndex == 99 ? 256 : tableOfContents[prevTableIndex + 1];
      // Linearly interpolate between the two scaled positions.
      double interpolateFraction = percent - prevTableIndex;
      scaledPosition = prevScaledPosition
          + (interpolateFraction * (nextScaledPosition - prevScaledPosition));
    }
    long positionOffset = Math.round((scaledPosition / 256) * dataSize);
    // Ensure returned positions skip the frame containing the XING header.
    positionOffset = Util.constrainValue(positionOffset, xingFrameSize, dataSize - 1);
    return new SeekPoints(new SeekPoint(timeUs, dataStartPosition + positionOffset));
  }

  @Override
  public long getTimeUs(long position) {
    long positionOffset = position - dataStartPosition;
    if (!isSeekable() || positionOffset <= xingFrameSize) {
      return 0L;
    }
    long[] tableOfContents = Assertions.checkNotNull(this.tableOfContents);
    double scaledPosition = (positionOffset * 256d) / dataSize;
    int prevTableIndex = Util.binarySearchFloor(tableOfContents, (long) scaledPosition, true, true);
    long prevTimeUs = getTimeUsForTableIndex(prevTableIndex);
    long prevScaledPosition = tableOfContents[prevTableIndex];
    long nextTimeUs = getTimeUsForTableIndex(prevTableIndex + 1);
    long nextScaledPosition = prevTableIndex == 99 ? 256 : tableOfContents[prevTableIndex + 1];
    // Linearly interpolate between the two table entries.
    double interpolateFraction = prevScaledPosition == nextScaledPosition ? 0
        : ((scaledPosition - prevScaledPosition) / (nextScaledPosition - prevScaledPosition));
    return prevTimeUs + Math.round(interpolateFraction * (nextTimeUs - prevTimeUs));
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public long getDataEndPosition() {
    return dataEndPosition;
  }

  /**
   * Returns the time in microseconds for a given table index.
   *
   * @param tableIndex A table index in the range [0, 100].
   * @return The corresponding time in microseconds.
   */
  private long getTimeUsForTableIndex(int tableIndex) {
    return (durationUs * tableIndex) / 100;
  }

}
