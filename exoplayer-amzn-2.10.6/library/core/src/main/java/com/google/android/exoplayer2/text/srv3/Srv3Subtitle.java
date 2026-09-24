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

  public Srv3Subtitle(List<Srv3Cue> cues) {
    this.cues = cues;

    // Collect all event times (start and end of each cue)
    long[] eventTimes = new long[cues.size() * 2];
    for (int i = 0; i < cues.size(); i++) {
      Srv3Cue cue = cues.get(i);
      eventTimes[i * 2] = cue.startTimeUs;
      eventTimes[i * 2 + 1] = cue.endTimeUs;
    }
    
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
    List<Cue> currentCues = new ArrayList<>();
    for (int i = 0; i < cues.size(); i++) {
      Srv3Cue cue = cues.get(i);
      if (cue.startTimeUs <= timeUs && timeUs < cue.endTimeUs) {
        currentCues.add(cue.cue);
      }
    }
    return currentCues.isEmpty() ? Collections.emptyList() : currentCues;
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