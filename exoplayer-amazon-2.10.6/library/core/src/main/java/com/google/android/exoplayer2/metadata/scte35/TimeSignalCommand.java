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
package com.google.android.exoplayer2.metadata.scte35;

import android.os.Parcel;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;

/**
 * Represents a time signal command as defined in SCTE35, Section 9.3.4.
 */
public final class TimeSignalCommand extends SpliceCommand {

  /**
   * A PTS value, as defined in SCTE35, Section 9.3.4.
   */
  public final long ptsTime;
  /**
   * Equivalent to {@link #ptsTime} but in the playback timebase.
   */
  public final long playbackPositionUs;

  private TimeSignalCommand(long ptsTime, long playbackPositionUs) {
    this.ptsTime = ptsTime;
    this.playbackPositionUs = playbackPositionUs;
  }

  /* package */ static TimeSignalCommand parseFromSection(ParsableByteArray sectionData,
      long ptsAdjustment, TimestampAdjuster timestampAdjuster) {
    long ptsTime = parseSpliceTime(sectionData, ptsAdjustment);
    long playbackPositionUs = timestampAdjuster.adjustTsTimestamp(ptsTime);
    return new TimeSignalCommand(ptsTime, playbackPositionUs);
  }

  /**
   * Parses pts_time from splice_time(), defined in Section 9.4.1. Returns {@link C#TIME_UNSET}, if
   * time_specified_flag is false.
   *
   * @param sectionData The section data from which the pts_time is parsed.
   * @param ptsAdjustment The pts adjustment provided by the splice info section header.
   * @return The pts_time defined by splice_time(), or {@link C#TIME_UNSET}, if time_specified_flag
   *     is false.
   */
  /* package */ static long parseSpliceTime(ParsableByteArray sectionData, long ptsAdjustment) {
    long firstByte = sectionData.readUnsignedByte();
    long ptsTime = C.TIME_UNSET;
    if ((firstByte & 0x80) != 0 /* time_specified_flag */) {
      // See SCTE35 9.2.1 for more information about pts adjustment.
      ptsTime = (firstByte & 0x01) << 32 | sectionData.readUnsignedInt();
      ptsTime += ptsAdjustment;
      ptsTime &= 0x1FFFFFFFFL;
    }
    return ptsTime;
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(ptsTime);
    dest.writeLong(playbackPositionUs);
  }

  public static final Creator<TimeSignalCommand> CREATOR =
      new Creator<TimeSignalCommand>() {

    @Override
    public TimeSignalCommand createFromParcel(Parcel in) {
      return new TimeSignalCommand(in.readLong(), in.readLong());
    }

    @Override
    public TimeSignalCommand[] newArray(int size) {
      return new TimeSignalCommand[size];
    }

  };

}
