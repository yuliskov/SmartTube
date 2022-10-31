/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.metadata.id3.MlltFrame;
import com.google.android.exoplayer2.util.Util;

/** MP3 seeker that uses metadata from an {@link MlltFrame}. */
/* package */ final class MlltSeeker implements Seeker {

  /**
   * Returns an {@link MlltSeeker} for seeking in the stream.
   *
   * @param firstFramePosition The position of the start of the first frame in the stream.
   * @param mlltFrame The MLLT frame with seeking metadata.
   * @return An {@link MlltSeeker} for seeking in the stream.
   */
  public static MlltSeeker create(long firstFramePosition, MlltFrame mlltFrame) {
    int referenceCount = mlltFrame.bytesDeviations.length;
    long[] referencePositions = new long[1 + referenceCount];
    long[] referenceTimesMs = new long[1 + referenceCount];
    referencePositions[0] = firstFramePosition;
    referenceTimesMs[0] = 0;
    long position = firstFramePosition;
    long timeMs = 0;
    for (int i = 1; i <= referenceCount; i++) {
      position += mlltFrame.bytesBetweenReference + mlltFrame.bytesDeviations[i - 1];
      timeMs += mlltFrame.millisecondsBetweenReference + mlltFrame.millisecondsDeviations[i - 1];
      referencePositions[i] = position;
      referenceTimesMs[i] = timeMs;
    }
    return new MlltSeeker(referencePositions, referenceTimesMs);
  }

  private final long[] referencePositions;
  private final long[] referenceTimesMs;
  private final long durationUs;

  private MlltSeeker(long[] referencePositions, long[] referenceTimesMs) {
    this.referencePositions = referencePositions;
    this.referenceTimesMs = referenceTimesMs;
    // Use the last reference point as the duration, as extrapolating variable bitrate at the end of
    // the stream may give a large error.
    durationUs = C.msToUs(referenceTimesMs[referenceTimesMs.length - 1]);
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    timeUs = Util.constrainValue(timeUs, 0, durationUs);
    Pair<Long, Long> timeMsAndPosition =
        linearlyInterpolate(C.usToMs(timeUs), referenceTimesMs, referencePositions);
    timeUs = C.msToUs(timeMsAndPosition.first);
    long position = timeMsAndPosition.second;
    return new SeekPoints(new SeekPoint(timeUs, position));
  }

  @Override
  public long getTimeUs(long position) {
    Pair<Long, Long> positionAndTimeMs =
        linearlyInterpolate(position, referencePositions, referenceTimesMs);
    return C.msToUs(positionAndTimeMs.second);
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  /**
   * Given a set of reference points as coordinates in {@code xReferences} and {@code yReferences}
   * and an x-axis value, linearly interpolates between corresponding reference points to give a
   * y-axis value.
   *
   * @param x The x-axis value for which a y-axis value is needed.
   * @param xReferences x coordinates of reference points.
   * @param yReferences y coordinates of reference points.
   * @return The linearly interpolated y-axis value.
   */
  private static Pair<Long, Long> linearlyInterpolate(
      long x, long[] xReferences, long[] yReferences) {
    int previousReferenceIndex =
        Util.binarySearchFloor(xReferences, x, /* inclusive= */ true, /* stayInBounds= */ true);
    long xPreviousReference = xReferences[previousReferenceIndex];
    long yPreviousReference = yReferences[previousReferenceIndex];
    int nextReferenceIndex = previousReferenceIndex + 1;
    if (nextReferenceIndex == xReferences.length) {
      return Pair.create(xPreviousReference, yPreviousReference);
    } else {
      long xNextReference = xReferences[nextReferenceIndex];
      long yNextReference = yReferences[nextReferenceIndex];
      double proportion =
          xNextReference == xPreviousReference
              ? 0.0
              : ((double) x - xPreviousReference) / (xNextReference - xPreviousReference);
      long y = (long) (proportion * (yNextReference - yPreviousReference)) + yPreviousReference;
      return Pair.create(x, y);
    }
  }

  @Override
  public long getDataEndPosition() {
    return C.POSITION_UNSET;
  }
}
