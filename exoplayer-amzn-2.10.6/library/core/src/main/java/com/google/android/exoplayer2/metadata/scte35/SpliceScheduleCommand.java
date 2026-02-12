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
import android.os.Parcelable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a splice schedule command as defined in SCTE35, Section 9.3.2.
 */
public final class SpliceScheduleCommand extends SpliceCommand {

  /**
   * Represents a splice event as contained in a {@link SpliceScheduleCommand}.
   */
  public static final class Event {

    /**
     * The splice event id.
     */
    public final long spliceEventId;
    /**
     * True if the event with id {@link #spliceEventId} has been canceled.
     */
    public final boolean spliceEventCancelIndicator;
    /**
     * If true, the splice event is an opportunity to exit from the network feed. If false,
     * indicates an opportunity to return to the network feed.
     */
    public final boolean outOfNetworkIndicator;
    /**
     * Whether the splice mode is program splice mode, whereby all PIDs/components are to be
     * spliced. If false, splicing is done per PID/component.
     */
    public final boolean programSpliceFlag;
    /**
     * Represents the time of the signaled splice event as the number of seconds since 00 hours UTC,
     * January 6th, 1980, with the count of intervening leap seconds included.
     */
    public final long utcSpliceTime;
    /**
     * If {@link #programSpliceFlag} is false, a non-empty list containing the
     * {@link ComponentSplice}s. Otherwise, an empty list.
     */
    public final List<ComponentSplice> componentSpliceList;
    /**
     * If {@link #breakDurationUs} is not {@link C#TIME_UNSET}, defines whether
     * {@link #breakDurationUs} should be used to know when to return to the network feed. If
     * {@link #breakDurationUs} is {@link C#TIME_UNSET}, the value is undefined.
     */
    public final boolean autoReturn;
    /**
     * The duration of the splice in microseconds, or {@link C#TIME_UNSET} if no duration is
     * present.
     */
    public final long breakDurationUs;
    /**
     * The unique program id as defined in SCTE35, Section 9.3.2.
     */
    public final int uniqueProgramId;
    /**
     * Holds the value of {@code avail_num} as defined in SCTE35, Section 9.3.2.
     */
    public final int availNum;
    /**
     * Holds the value of {@code avails_expected} as defined in SCTE35, Section 9.3.2.
     */
    public final int availsExpected;

    private Event(long spliceEventId, boolean spliceEventCancelIndicator,
        boolean outOfNetworkIndicator, boolean programSpliceFlag,
        List<ComponentSplice> componentSpliceList, long utcSpliceTime, boolean autoReturn,
        long breakDurationUs, int uniqueProgramId, int availNum, int availsExpected) {
      this.spliceEventId = spliceEventId;
      this.spliceEventCancelIndicator = spliceEventCancelIndicator;
      this.outOfNetworkIndicator = outOfNetworkIndicator;
      this.programSpliceFlag = programSpliceFlag;
      this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
      this.utcSpliceTime = utcSpliceTime;
      this.autoReturn = autoReturn;
      this.breakDurationUs = breakDurationUs;
      this.uniqueProgramId = uniqueProgramId;
      this.availNum = availNum;
      this.availsExpected = availsExpected;
    }

    private Event(Parcel in) {
      this.spliceEventId = in.readLong();
      this.spliceEventCancelIndicator = in.readByte() == 1;
      this.outOfNetworkIndicator = in.readByte() == 1;
      this.programSpliceFlag = in.readByte() == 1;
      int componentSpliceListLength = in.readInt();
      ArrayList<ComponentSplice> componentSpliceList = new ArrayList<>(componentSpliceListLength);
      for (int i = 0; i < componentSpliceListLength; i++) {
        componentSpliceList.add(ComponentSplice.createFromParcel(in));
      }
      this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
      this.utcSpliceTime = in.readLong();
      this.autoReturn = in.readByte() == 1;
      this.breakDurationUs = in.readLong();
      this.uniqueProgramId = in.readInt();
      this.availNum = in.readInt();
      this.availsExpected = in.readInt();
    }

    private static Event parseFromSection(ParsableByteArray sectionData) {
      long spliceEventId = sectionData.readUnsignedInt();
      // splice_event_cancel_indicator(1), reserved(7).
      boolean spliceEventCancelIndicator = (sectionData.readUnsignedByte() & 0x80) != 0;
      boolean outOfNetworkIndicator = false;
      boolean programSpliceFlag = false;
      long utcSpliceTime = C.TIME_UNSET;
      ArrayList<ComponentSplice> componentSplices = new ArrayList<>();
      int uniqueProgramId = 0;
      int availNum = 0;
      int availsExpected = 0;
      boolean autoReturn = false;
      long breakDurationUs = C.TIME_UNSET;
      if (!spliceEventCancelIndicator) {
        int headerByte = sectionData.readUnsignedByte();
        outOfNetworkIndicator = (headerByte & 0x80) != 0;
        programSpliceFlag = (headerByte & 0x40) != 0;
        boolean durationFlag = (headerByte & 0x20) != 0;
        if (programSpliceFlag) {
          utcSpliceTime = sectionData.readUnsignedInt();
        }
        if (!programSpliceFlag) {
          int componentCount = sectionData.readUnsignedByte();
          componentSplices = new ArrayList<>(componentCount);
          for (int i = 0; i < componentCount; i++) {
            int componentTag = sectionData.readUnsignedByte();
            long componentUtcSpliceTime = sectionData.readUnsignedInt();
            componentSplices.add(new ComponentSplice(componentTag, componentUtcSpliceTime));
          }
        }
        if (durationFlag) {
          long firstByte = sectionData.readUnsignedByte();
          autoReturn = (firstByte & 0x80) != 0;
          long breakDuration90khz = ((firstByte & 0x01) << 32) | sectionData.readUnsignedInt();
          breakDurationUs = breakDuration90khz * 1000 / 90;
        }
        uniqueProgramId = sectionData.readUnsignedShort();
        availNum = sectionData.readUnsignedByte();
        availsExpected = sectionData.readUnsignedByte();
      }
      return new Event(spliceEventId, spliceEventCancelIndicator, outOfNetworkIndicator,
          programSpliceFlag, componentSplices, utcSpliceTime, autoReturn, breakDurationUs,
          uniqueProgramId, availNum, availsExpected);
    }

    private void writeToParcel(Parcel dest) {
      dest.writeLong(spliceEventId);
      dest.writeByte((byte) (spliceEventCancelIndicator ? 1 : 0));
      dest.writeByte((byte) (outOfNetworkIndicator ? 1 : 0));
      dest.writeByte((byte) (programSpliceFlag ? 1 : 0));
      int componentSpliceListSize = componentSpliceList.size();
      dest.writeInt(componentSpliceListSize);
      for (int i = 0; i < componentSpliceListSize; i++) {
        componentSpliceList.get(i).writeToParcel(dest);
      }
      dest.writeLong(utcSpliceTime);
      dest.writeByte((byte) (autoReturn ? 1 : 0));
      dest.writeLong(breakDurationUs);
      dest.writeInt(uniqueProgramId);
      dest.writeInt(availNum);
      dest.writeInt(availsExpected);
    }

    private static Event createFromParcel(Parcel in) {
      return new Event(in);
    }

  }

  /**
   * Holds splicing information for specific splice schedule command components.
   */
  public static final class ComponentSplice {

    public final int componentTag;
    public final long utcSpliceTime;

    private ComponentSplice(int componentTag, long utcSpliceTime) {
      this.componentTag = componentTag;
      this.utcSpliceTime = utcSpliceTime;
    }

    private static ComponentSplice createFromParcel(Parcel in) {
      return new ComponentSplice(in.readInt(), in.readLong());
    }

    private void writeToParcel(Parcel dest) {
      dest.writeInt(componentTag);
      dest.writeLong(utcSpliceTime);
    }

  }

  /**
   * The list of scheduled events.
   */
  public final List<Event> events;

  private SpliceScheduleCommand(List<Event> events) {
    this.events = Collections.unmodifiableList(events);
  }

  private SpliceScheduleCommand(Parcel in) {
    int eventsSize = in.readInt();
    ArrayList<Event> events = new ArrayList<>(eventsSize);
    for (int i = 0; i < eventsSize; i++) {
      events.add(Event.createFromParcel(in));
    }
    this.events = Collections.unmodifiableList(events);
  }

  /* package */ static SpliceScheduleCommand parseFromSection(ParsableByteArray sectionData) {
    int spliceCount = sectionData.readUnsignedByte();
    ArrayList<Event> events = new ArrayList<>(spliceCount);
    for (int i = 0; i < spliceCount; i++) {
      events.add(Event.parseFromSection(sectionData));
    }
    return new SpliceScheduleCommand(events);
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    int eventsSize = events.size();
    dest.writeInt(eventsSize);
    for (int i = 0; i < eventsSize; i++) {
      events.get(i).writeToParcel(dest);
    }
  }

  public static final Parcelable.Creator<SpliceScheduleCommand> CREATOR =
      new Parcelable.Creator<SpliceScheduleCommand>() {

    @Override
    public SpliceScheduleCommand createFromParcel(Parcel in) {
      return new SpliceScheduleCommand(in);
    }

    @Override
    public SpliceScheduleCommand[] newArray(int size) {
      return new SpliceScheduleCommand[size];
    }

  };

}
