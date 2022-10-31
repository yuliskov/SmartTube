/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.dash.manifest;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DashManifest}. */
@RunWith(AndroidJUnit4.class)
public class DashManifestTest {

  private static final UtcTimingElement DUMMY_UTC_TIMING = new UtcTimingElement("", "");
  private static final SingleSegmentBase DUMMY_SEGMENT_BASE = new SingleSegmentBase();
  private static final Format DUMMY_FORMAT = Format.createSampleFormat("", "", 0);

  @Test
  public void testCopy() throws Exception {
    Representation[][][] representations = newRepresentations(3, 2, 3);
    DashManifest sourceManifest =
        newDashManifest(
            10,
            newPeriod(
                "1",
                1,
                newAdaptationSet(2, representations[0][0]),
                newAdaptationSet(3, representations[0][1])),
            newPeriod(
                "4",
                4,
                newAdaptationSet(5, representations[1][0]),
                newAdaptationSet(6, representations[1][1])),
            newPeriod(
                "7",
                7,
                newAdaptationSet(8, representations[2][0]),
                newAdaptationSet(9, representations[2][1])));

    List<StreamKey> keys =
        Arrays.asList(
            new StreamKey(0, 0, 0),
            new StreamKey(0, 0, 1),
            new StreamKey(0, 1, 2),
            new StreamKey(1, 0, 1),
            new StreamKey(1, 1, 0),
            new StreamKey(1, 1, 2),
            new StreamKey(2, 0, 1),
            new StreamKey(2, 0, 2),
            new StreamKey(2, 1, 0));
    // Keys don't need to be in any particular order
    Collections.shuffle(keys, new Random(0));

    DashManifest copyManifest = sourceManifest.copy(keys);

    DashManifest expectedManifest =
        newDashManifest(
            10,
            newPeriod(
                "1",
                1,
                newAdaptationSet(2, representations[0][0][0], representations[0][0][1]),
                newAdaptationSet(3, representations[0][1][2])),
            newPeriod(
                "4",
                4,
                newAdaptationSet(5, representations[1][0][1]),
                newAdaptationSet(6, representations[1][1][0], representations[1][1][2])),
            newPeriod(
                "7",
                7,
                newAdaptationSet(8, representations[2][0][1], representations[2][0][2]),
                newAdaptationSet(9, representations[2][1][0])));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  @Test
  public void testCopySameAdaptationIndexButDifferentPeriod() throws Exception {
    Representation[][][] representations = newRepresentations(2, 1, 1);
    DashManifest sourceManifest =
        newDashManifest(
            10,
            newPeriod("1", 1, newAdaptationSet(2, representations[0][0])),
            newPeriod("4", 4, newAdaptationSet(5, representations[1][0])));

    DashManifest copyManifest =
        sourceManifest.copy(Arrays.asList(new StreamKey(0, 0, 0), new StreamKey(1, 0, 0)));

    DashManifest expectedManifest =
        newDashManifest(
            10,
            newPeriod("1", 1, newAdaptationSet(2, representations[0][0])),
            newPeriod("4", 4, newAdaptationSet(5, representations[1][0])));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  @Test
  public void testCopySkipPeriod() throws Exception {
    Representation[][][] representations = newRepresentations(3, 2, 3);
    DashManifest sourceManifest =
        newDashManifest(
            10,
            newPeriod(
                "1",
                1,
                newAdaptationSet(2, representations[0][0]),
                newAdaptationSet(3, representations[0][1])),
            newPeriod(
                "4",
                4,
                newAdaptationSet(5, representations[1][0]),
                newAdaptationSet(6, representations[1][1])),
            newPeriod(
                "7",
                7,
                newAdaptationSet(8, representations[2][0]),
                newAdaptationSet(9, representations[2][1])));

    DashManifest copyManifest =
        sourceManifest.copy(
            Arrays.asList(
                new StreamKey(0, 0, 0),
                new StreamKey(0, 0, 1),
                new StreamKey(0, 1, 2),
                new StreamKey(2, 0, 1),
                new StreamKey(2, 0, 2),
                new StreamKey(2, 1, 0)));

    DashManifest expectedManifest =
        newDashManifest(
            7,
            newPeriod(
                "1",
                1,
                newAdaptationSet(2, representations[0][0][0], representations[0][0][1]),
                newAdaptationSet(3, representations[0][1][2])),
            newPeriod(
                "7",
                4,
                newAdaptationSet(8, representations[2][0][1], representations[2][0][2]),
                newAdaptationSet(9, representations[2][1][0])));
    assertManifestEquals(expectedManifest, copyManifest);
  }

  private static void assertManifestEquals(DashManifest expected, DashManifest actual) {
    assertThat(actual.availabilityStartTimeMs).isEqualTo(expected.availabilityStartTimeMs);
    assertThat(actual.durationMs).isEqualTo(expected.durationMs);
    assertThat(actual.minBufferTimeMs).isEqualTo(expected.minBufferTimeMs);
    assertThat(actual.dynamic).isEqualTo(expected.dynamic);
    assertThat(actual.minUpdatePeriodMs).isEqualTo(expected.minUpdatePeriodMs);
    assertThat(actual.timeShiftBufferDepthMs).isEqualTo(expected.timeShiftBufferDepthMs);
    assertThat(actual.suggestedPresentationDelayMs)
        .isEqualTo(expected.suggestedPresentationDelayMs);
    assertThat(actual.publishTimeMs).isEqualTo(expected.publishTimeMs);
    assertThat(actual.utcTiming).isEqualTo(expected.utcTiming);
    assertThat(actual.location).isEqualTo(expected.location);
    assertThat(actual.getPeriodCount()).isEqualTo(expected.getPeriodCount());
    for (int i = 0; i < expected.getPeriodCount(); i++) {
      Period expectedPeriod = expected.getPeriod(i);
      Period actualPeriod = actual.getPeriod(i);
      assertThat(actualPeriod.id).isEqualTo(expectedPeriod.id);
      assertThat(actualPeriod.startMs).isEqualTo(expectedPeriod.startMs);
      List<AdaptationSet> expectedAdaptationSets = expectedPeriod.adaptationSets;
      List<AdaptationSet> actualAdaptationSets = actualPeriod.adaptationSets;
      assertThat(actualAdaptationSets).hasSize(expectedAdaptationSets.size());
      for (int j = 0; j < expectedAdaptationSets.size(); j++) {
        AdaptationSet expectedAdaptationSet = expectedAdaptationSets.get(j);
        AdaptationSet actualAdaptationSet = actualAdaptationSets.get(j);
        assertThat(actualAdaptationSet.id).isEqualTo(expectedAdaptationSet.id);
        assertThat(actualAdaptationSet.type).isEqualTo(expectedAdaptationSet.type);
        assertThat(actualAdaptationSet.accessibilityDescriptors)
            .isEqualTo(expectedAdaptationSet.accessibilityDescriptors);
        assertThat(actualAdaptationSet.representations)
            .isEqualTo(expectedAdaptationSet.representations);
      }
    }
  }

  private static Representation[][][] newRepresentations(
      int periodCount, int adaptationSetCounts, int representationCounts) {
    Representation[][][] representations = new Representation[periodCount][][];
    for (int i = 0; i < periodCount; i++) {
      representations[i] = new Representation[adaptationSetCounts][];
      for (int j = 0; j < adaptationSetCounts; j++) {
        representations[i][j] = new Representation[representationCounts];
        for (int k = 0; k < representationCounts; k++) {
          representations[i][j][k] = newRepresentation();
        }
      }
    }
    return representations;
  }

  private static Representation newRepresentation() {
    return Representation.newInstance(
        /* revisionId= */ 0, DUMMY_FORMAT, /* baseUrl= */ "", DUMMY_SEGMENT_BASE);
  }

  private static DashManifest newDashManifest(int duration, Period... periods) {
    return new DashManifest(
        /* availabilityStartTimeMs= */ 0,
        duration,
        /* minBufferTimeMs= */ 1,
        /* dynamic= */ false,
        /* minUpdatePeriodMs= */ 2,
        /* timeShiftBufferDepthMs= */ 3,
        /* suggestedPresentationDelayMs= */ 4,
        /* publishTimeMs= */ 12345,
        /* programInformation= */ null,
        DUMMY_UTC_TIMING,
        Uri.EMPTY,
        Arrays.asList(periods));
  }

  private static Period newPeriod(String id, int startMs, AdaptationSet... adaptationSets) {
    return new Period(id, startMs, Arrays.asList(adaptationSets));
  }

  private static AdaptationSet newAdaptationSet(int seed, Representation... representations) {
    return new AdaptationSet(++seed, ++seed, Arrays.asList(representations), null, null);
  }
}
