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
package com.google.android.exoplayer2.text.webvtt;

import android.text.SpannableStringBuilder;
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
 * A representation of a WebVTT subtitle.
 */
/* package */ final class WebvttSubtitle implements Subtitle {

  private final List<WebvttCue> cues;
  private final int numCues;
  private final long[] cueTimesUs;
  private final long[] sortedCueTimesUs;

  /**
   * @param cues A list of the cues in this subtitle.
   */
  public WebvttSubtitle(List<WebvttCue> cues) {
    this.cues = cues;
    numCues = cues.size();
    cueTimesUs = new long[2 * numCues];
    for (int cueIndex = 0; cueIndex < numCues; cueIndex++) {
      WebvttCue cue = cues.get(cueIndex);
      int arrayIndex = cueIndex * 2;
      cueTimesUs[arrayIndex] = cue.startTime;
      cueTimesUs[arrayIndex + 1] = cue.endTime;
    }
    sortedCueTimesUs = Arrays.copyOf(cueTimesUs, cueTimesUs.length);
    Arrays.sort(sortedCueTimesUs);
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    int index = Util.binarySearchCeil(sortedCueTimesUs, timeUs, false, false);
    return index < sortedCueTimesUs.length ? index : C.INDEX_UNSET;
  }

  @Override
  public int getEventTimeCount() {
    return sortedCueTimesUs.length;
  }

  @Override
  public long getEventTime(int index) {
    Assertions.checkArgument(index >= 0);
    Assertions.checkArgument(index < sortedCueTimesUs.length);
    return sortedCueTimesUs[index];
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    ArrayList<Cue> list = null;
    WebvttCue firstNormalCue = null;
    SpannableStringBuilder normalCueTextBuilder = null;

    for (int i = 0; i < numCues; i++) {
      if ((cueTimesUs[i * 2] <= timeUs) && (timeUs < cueTimesUs[i * 2 + 1])) {
        if (list == null) {
          list = new ArrayList<>();
        }
        WebvttCue cue = cues.get(i);
        if (cue.isNormalCue()) {
          // we want to merge all of the normal cues into a single cue to ensure they are drawn
          // correctly (i.e. don't overlap) and to emulate roll-up, but only if there are multiple
          // normal cues, otherwise we can just append the single normal cue
          if (firstNormalCue == null) {
            firstNormalCue = cue;
          } else if (normalCueTextBuilder == null) {
            normalCueTextBuilder = new SpannableStringBuilder();
            normalCueTextBuilder.append(firstNormalCue.text).append("\n").append(cue.text);
          } else {
            normalCueTextBuilder.append("\n").append(cue.text);
          }
        } else {
          list.add(cue);
        }
      }
    }
    if (normalCueTextBuilder != null) {
      // there were multiple normal cues, so create a new cue with all of the text
      list.add(new WebvttCue(normalCueTextBuilder));
    } else if (firstNormalCue != null) {
      // there was only a single normal cue, so just add it to the list
      list.add(firstNormalCue);
    }

    if (list != null) {
      return list;
    } else {
      return Collections.emptyList();
    }
  }

}
