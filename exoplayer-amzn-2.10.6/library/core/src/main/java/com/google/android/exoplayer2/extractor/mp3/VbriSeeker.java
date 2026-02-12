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
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

/** MP3 seeker that uses metadata from a VBRI header. */
/* package */ final class VbriSeeker implements Seeker {

  private static final String TAG = "VbriSeeker";

  /**
   * Returns a {@link VbriSeeker} for seeking in the stream, if required information is present.
   * Returns {@code null} if not. On returning, {@code frame}'s position is not specified so the
   * caller should reset it.
   *
   * @param inputLength The length of the stream in bytes, or {@link C#LENGTH_UNSET} if unknown.
   * @param position The position of the start of this frame in the stream.
   * @param mpegAudioHeader The MPEG audio header associated with the frame.
   * @param frame The data in this audio frame, with its position set to immediately after the
   *     'VBRI' tag.
   * @return A {@link VbriSeeker} for seeking in the stream, or {@code null} if the required
   *     information is not present.
   */
  public static @Nullable VbriSeeker create(
      long inputLength, long position, MpegAudioHeader mpegAudioHeader, ParsableByteArray frame) {
    frame.skipBytes(10);
    int numFrames = frame.readInt();
    if (numFrames <= 0) {
      return null;
    }
    int sampleRate = mpegAudioHeader.sampleRate;
    long durationUs = Util.scaleLargeTimestamp(numFrames,
        C.MICROS_PER_SECOND * (sampleRate >= 32000 ? 1152 : 576), sampleRate);
    int entryCount = frame.readUnsignedShort();
    int scale = frame.readUnsignedShort();
    int entrySize = frame.readUnsignedShort();
    frame.skipBytes(2);

    long minPosition = position + mpegAudioHeader.frameSize;
    // Read table of contents entries.
    long[] timesUs = new long[entryCount];
    long[] positions = new long[entryCount];
    for (int index = 0; index < entryCount; index++) {
      timesUs[index] = (index * durationUs) / entryCount;
      // Ensure positions do not fall within the frame containing the VBRI header. This constraint
      // will normally only apply to the first entry in the table.
      positions[index] = Math.max(position, minPosition);
      int segmentSize;
      switch (entrySize) {
        case 1:
          segmentSize = frame.readUnsignedByte();
          break;
        case 2:
          segmentSize = frame.readUnsignedShort();
          break;
        case 3:
          segmentSize = frame.readUnsignedInt24();
          break;
        case 4:
          segmentSize = frame.readUnsignedIntToInt();
          break;
        default:
          return null;
      }
      position += segmentSize * scale;
    }
    if (inputLength != C.LENGTH_UNSET && inputLength != position) {
      Log.w(TAG, "VBRI data size mismatch: " + inputLength + ", " + position);
    }
    return new VbriSeeker(timesUs, positions, durationUs, /* dataEndPosition= */ position);
  }

  private final long[] timesUs;
  private final long[] positions;
  private final long durationUs;
  private final long dataEndPosition;

  private VbriSeeker(long[] timesUs, long[] positions, long durationUs, long dataEndPosition) {
    this.timesUs = timesUs;
    this.positions = positions;
    this.durationUs = durationUs;
    this.dataEndPosition = dataEndPosition;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    int tableIndex = Util.binarySearchFloor(timesUs, timeUs, true, true);
    SeekPoint seekPoint = new SeekPoint(timesUs[tableIndex], positions[tableIndex]);
    if (seekPoint.timeUs >= timeUs || tableIndex == timesUs.length - 1) {
      return new SeekPoints(seekPoint);
    } else {
      SeekPoint nextSeekPoint = new SeekPoint(timesUs[tableIndex + 1], positions[tableIndex + 1]);
      return new SeekPoints(seekPoint, nextSeekPoint);
    }
  }

  @Override
  public long getTimeUs(long position) {
    return timesUs[Util.binarySearchFloor(positions, position, true, true)];
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public long getDataEndPosition() {
    return dataEndPosition;
  }
}
