package com.google.android.exoplayer2.text.srv3;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A representation of a SRV3 subtitle.
 */
/* package */ final class Srv3Subtitle implements Subtitle {

  private final List<Srv3Cue> cues;
  private final long[] eventTimesUs;
  private final long[] cueStartTimesUs;
  private final long maxDurationUs;
  private final List<Srv3Cue> indefiniteCues;

  public Srv3Subtitle(List<Srv3Cue> cues) {
    // Sort cues chronologically by start time
    Collections.sort(cues, (a, b) -> Long.compare(a.startTimeUs, b.startTimeUs));
    this.cues = cues;

    // Collect all event times (start and end of each cue)
    long[] eventTimes = new long[cues.size() * 2];
    cueStartTimesUs = new long[cues.size()];
    long maxDur = 20_000_000L; // default at least 20 seconds
    List<Srv3Cue> undef = null;

    for (int i = 0; i < cues.size(); i++) {
      Srv3Cue cue = cues.get(i);
      cueStartTimesUs[i] = cue.startTimeUs;
      eventTimes[i * 2] = cue.startTimeUs;
      eventTimes[i * 2 + 1] = cue.endTimeUs;

      if (cue.endTimeUs == Long.MAX_VALUE) {
        if (undef == null) {
          undef = new ArrayList<>();
        }
        undef.add(cue);
      } else {
        long dur = cue.endTimeUs - cue.startTimeUs;
        if (dur > maxDur && dur <= 60_000_000L) {
          maxDur = dur;
        }
      }
    }

    this.maxDurationUs = maxDur;
    this.indefiniteCues = undef;

    // Sort and remove duplicates
    Arrays.sort(eventTimes);

    int count = 0;
    for (int i = 0; i < eventTimes.length; i++) {
      if (i == 0 || eventTimes[i] != eventTimes[i - 1]) {
        eventTimes[count++] = eventTimes[i];
      }
    }
    eventTimesUs = Arrays.copyOf(eventTimes, count);
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    int index = Util.binarySearchCeil(eventTimesUs, timeUs, false, false);
    return index < eventTimesUs.length ? index : C.INDEX_UNSET;
  }

  @Override
  public int getEventTimeCount() {
    return eventTimesUs.length;
  }

  @Override
  public long getEventTime(int index) {
    Assertions.checkArgument(index >= 0);
    Assertions.checkArgument(index < eventTimesUs.length);
    return eventTimesUs[index];
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    int right = Util.binarySearchFloor(cueStartTimesUs, timeUs, true, false);
    if (right < 0 && indefiniteCues == null) {
      return Collections.emptyList();
    }

    List<Cue> currentCues = null;

    if (right >= 0) {
      long minStartTimeUs = Math.max(0, timeUs - maxDurationUs);
      int left = Util.binarySearchFloor(cueStartTimesUs, minStartTimeUs, true, false);
      if (left < 0) {
        left = 0;
      }
      for (int i = left; i <= right; i++) {
        Srv3Cue cue = cues.get(i);
        if (cue.startTimeUs <= timeUs && timeUs < cue.endTimeUs) {
          if (currentCues == null) {
            currentCues = new ArrayList<>();
          }
          currentCues.add(cue.cue);
        }
      }
    }

    if (indefiniteCues != null) {
      for (int i = 0; i < indefiniteCues.size(); i++) {
        Srv3Cue cue = indefiniteCues.get(i);
        if (cue.startTimeUs <= timeUs && timeUs < cue.endTimeUs) {
          if (currentCues == null) {
            currentCues = new ArrayList<>();
          }
          if (!currentCues.contains(cue.cue)) {
            currentCues.add(cue.cue);
          }
        }
      }
    }

    return currentCues != null ? currentCues : Collections.emptyList();
  }

  /* package */ static final class Srv3Cue {
    public final Cue cue;
    public final long startTimeUs;
    public final long endTimeUs;

    public Srv3Cue(Cue cue, long startTimeUs, long endTimeUs) {
      this.cue = cue;
      this.startTimeUs = startTimeUs;
      this.endTimeUs = endTimeUs;
    }
  }
}