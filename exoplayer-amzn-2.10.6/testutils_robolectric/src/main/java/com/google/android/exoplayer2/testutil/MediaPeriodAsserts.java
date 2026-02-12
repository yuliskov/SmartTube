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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.FilterableManifest;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaPeriod.Callback;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Assertion methods for {@link MediaPeriod}. */
public final class MediaPeriodAsserts {

  /**
   * Interface to create media periods for testing based on a {@link FilterableManifest}.
   *
   * @param <T> The type of {@link FilterableManifest}.
   */
  public interface FilterableManifestMediaPeriodFactory<T extends FilterableManifest<T>> {

    /** Returns media period based on the provided filterable manifest. */
    MediaPeriod createMediaPeriod(T manifest, int periodIndex);
  }

  private MediaPeriodAsserts() {}

  /**
   * Asserts that the values returns by {@link MediaPeriod#getStreamKeys(List)} are compatible with
   * a {@link FilterableManifest} using these stream keys.
   *
   * @param mediaPeriodFactory A factory to create a {@link MediaPeriod} based on a manifest.
   * @param manifest The manifest which is to be tested.
   */
  public static <T extends FilterableManifest<T>>
      void assertGetStreamKeysAndManifestFilterIntegration(
          FilterableManifestMediaPeriodFactory<T> mediaPeriodFactory, T manifest) {
    assertGetStreamKeysAndManifestFilterIntegration(
        mediaPeriodFactory, manifest, /* periodIndex= */ 0, /* ignoredMimeType= */ null);
  }

  /**
   * Asserts that the values returns by {@link MediaPeriod#getStreamKeys(List)} are compatible with
   * a {@link FilterableManifest} using these stream keys.
   *
   * @param mediaPeriodFactory A factory to create a {@link MediaPeriod} based on a manifest.
   * @param manifest The manifest which is to be tested.
   * @param periodIndex The index of period in the manifest.
   * @param ignoredMimeType Optional mime type whose existence in the filtered track groups is not
   *     asserted.
   */
  public static <T extends FilterableManifest<T>>
      void assertGetStreamKeysAndManifestFilterIntegration(
          FilterableManifestMediaPeriodFactory<T> mediaPeriodFactory,
          T manifest,
          int periodIndex,
          @Nullable String ignoredMimeType) {
    MediaPeriod mediaPeriod = mediaPeriodFactory.createMediaPeriod(manifest, periodIndex);
    TrackGroupArray trackGroupArray = getTrackGroups(mediaPeriod);

    // Create test vector of query test selections:
    //  - One selection with one track per group, two tracks or all tracks.
    //  - Two selections with tracks from multiple groups, or tracks from a single group.
    //  - Multiple selections with tracks from all groups.
    List<List<TrackSelection>> testSelections = new ArrayList<>();
    for (int i = 0; i < trackGroupArray.length; i++) {
      TrackGroup trackGroup = trackGroupArray.get(i);
      for (int j = 0; j < trackGroup.length; j++) {
        testSelections.add(Collections.singletonList(new TestTrackSelection(trackGroup, j)));
      }
      if (trackGroup.length > 1) {
        testSelections.add(Collections.singletonList(new TestTrackSelection(trackGroup, 0, 1)));
        testSelections.add(
            Arrays.asList(
                new TrackSelection[] {
                  new TestTrackSelection(trackGroup, 0), new TestTrackSelection(trackGroup, 1)
                }));
      }
      if (trackGroup.length > 2) {
        int[] allTracks = new int[trackGroup.length];
        for (int j = 0; j < trackGroup.length; j++) {
          allTracks[j] = j;
        }
        testSelections.add(
            Collections.singletonList(new TestTrackSelection(trackGroup, allTracks)));
      }
    }
    if (trackGroupArray.length > 1) {
      for (int i = 0; i < trackGroupArray.length - 1; i++) {
        for (int j = i + 1; j < trackGroupArray.length; j++) {
          testSelections.add(
              Arrays.asList(
                  new TrackSelection[] {
                    new TestTrackSelection(trackGroupArray.get(i), 0),
                    new TestTrackSelection(trackGroupArray.get(j), 0)
                  }));
        }
      }
    }
    if (trackGroupArray.length > 2) {
      List<TrackSelection> selectionsFromAllGroups = new ArrayList<>();
      for (int i = 0; i < trackGroupArray.length; i++) {
        selectionsFromAllGroups.add(new TestTrackSelection(trackGroupArray.get(i), 0));
      }
      testSelections.add(selectionsFromAllGroups);
    }

    // Verify for each case that stream keys can be used to create filtered tracks which still
    // contain at least all requested formats.
    for (List<TrackSelection> testSelection : testSelections) {
      List<StreamKey> streamKeys = mediaPeriod.getStreamKeys(testSelection);
      if (streamKeys.isEmpty()) {
        // Manifests won't be filtered if stream key is empty.
        continue;
      }
      T filteredManifest = manifest.copy(streamKeys);
      // The filtered manifest should only have one period left.
      MediaPeriod filteredMediaPeriod =
          mediaPeriodFactory.createMediaPeriod(filteredManifest, /* periodIndex= */ 0);
      TrackGroupArray filteredTrackGroupArray = getTrackGroups(filteredMediaPeriod);
      for (TrackSelection trackSelection : testSelection) {
        if (ignoredMimeType != null
            && ignoredMimeType.equals(trackSelection.getFormat(0).sampleMimeType)) {
          continue;
        }
        Format[] expectedFormats = new Format[trackSelection.length()];
        for (int k = 0; k < trackSelection.length(); k++) {
          expectedFormats[k] = trackSelection.getFormat(k);
        }
        assertOneTrackGroupContainsFormats(filteredTrackGroupArray, expectedFormats);
      }
    }
  }

  private static void assertOneTrackGroupContainsFormats(
      TrackGroupArray trackGroupArray, Format[] formats) {
    boolean foundSubset = false;
    for (int i = 0; i < trackGroupArray.length; i++) {
      if (containsFormats(trackGroupArray.get(i), formats)) {
        foundSubset = true;
        break;
      }
    }
    assertThat(foundSubset).isTrue();
  }

  private static boolean containsFormats(TrackGroup trackGroup, Format[] formats) {
    HashSet<Format> allFormats = new HashSet<>();
    for (int i = 0; i < trackGroup.length; i++) {
      allFormats.add(trackGroup.getFormat(i));
    }
    for (int i = 0; i < formats.length; i++) {
      if (!allFormats.remove(formats[i])) {
        return false;
      }
    }
    return true;
  }

  private static TrackGroupArray getTrackGroups(MediaPeriod mediaPeriod) {
    AtomicReference<TrackGroupArray> trackGroupArray = new AtomicReference<>(null);
    DummyMainThread dummyMainThread = new DummyMainThread();
    ConditionVariable preparedCondition = new ConditionVariable();
    dummyMainThread.runOnMainThread(
        () -> {
          mediaPeriod.prepare(
              new Callback() {
                @Override
                public void onPrepared(MediaPeriod mediaPeriod) {
                  trackGroupArray.set(mediaPeriod.getTrackGroups());
                  preparedCondition.open();
                }

                @Override
                public void onContinueLoadingRequested(MediaPeriod source) {
                  // Ignore.
                }
              },
              /* positionUs= */ 0);
        });
    try {
      preparedCondition.block();
    } catch (InterruptedException e) {
      // Ignore.
    }
    dummyMainThread.release();
    return trackGroupArray.get();
  }

  private static final class TestTrackSelection extends BaseTrackSelection {

    public TestTrackSelection(TrackGroup trackGroup, int... tracks) {
      super(trackGroup, tracks);
    }

    @Override
    public int getSelectedIndex() {
      return 0;
    }

    @Override
    public int getSelectionReason() {
      return C.SELECTION_REASON_UNKNOWN;
    }

    @Nullable
    @Override
    public Object getSelectionData() {
      return null;
    }
  }
}
