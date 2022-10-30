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
package com.google.android.exoplayer2.trackselection;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * An abstract base class suitable for most {@link TrackSelection} implementations.
 */
public abstract class BaseTrackSelection implements TrackSelection {

  /**
   * The selected {@link TrackGroup}.
   */
  protected final TrackGroup group;
  /**
   * The number of selected tracks within the {@link TrackGroup}. Always greater than zero.
   */
  protected final int length;
  /**
   * The indices of the selected tracks in {@link #group}, in order of decreasing bandwidth.
   */
  protected final int[] tracks;

  /**
   * The {@link Format}s of the selected tracks, in order of decreasing bandwidth.
   */
  private final Format[] formats;
  /**
   * Selected track blacklist timestamps, in order of decreasing bandwidth.
   */
  private final long[] blacklistUntilTimes;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * @param group The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     null or empty. May be in any order.
   */
  public BaseTrackSelection(TrackGroup group, int... tracks) {
    Assertions.checkState(tracks.length > 0);
    this.group = Assertions.checkNotNull(group);
    this.length = tracks.length;
    // Set the formats, sorted in order of decreasing bandwidth.
    formats = new Format[length];
    for (int i = 0; i < tracks.length; i++) {
      formats[i] = group.getFormat(tracks[i]);
    }
    Arrays.sort(formats, new DecreasingBandwidthComparator());
    // Set the format indices in the same order.
    this.tracks = new int[length];
    for (int i = 0; i < length; i++) {
      this.tracks[i] = group.indexOf(formats[i]);
    }
    blacklistUntilTimes = new long[length];
  }

  @Override
  public void enable() {
    // Do nothing.
  }

  @Override
  public void disable() {
    // Do nothing.
  }

  @Override
  public final TrackGroup getTrackGroup() {
    return group;
  }

  @Override
  public final int length() {
    return tracks.length;
  }

  @Override
  public final Format getFormat(int index) {
    return formats[index];
  }

  @Override
  public final int getIndexInTrackGroup(int index) {
    return tracks[index];
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public final int indexOf(Format format) {
    for (int i = 0; i < length; i++) {
      if (formats[i] == format) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public final int indexOf(int indexInTrackGroup) {
    for (int i = 0; i < length; i++) {
      if (tracks[i] == indexInTrackGroup) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  @Override
  public final Format getSelectedFormat() {
    return formats[getSelectedIndex()];
  }

  @Override
  public final int getSelectedIndexInTrackGroup() {
    return tracks[getSelectedIndex()];
  }

  @Override
  public void onPlaybackSpeed(float playbackSpeed) {
    // Do nothing.
  }

  @Override
  public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    return queue.size();
  }

  @Override
  public final boolean blacklist(int index, long blacklistDurationMs) {
    long nowMs = SystemClock.elapsedRealtime();
    boolean canBlacklist = isBlacklisted(index, nowMs);
    for (int i = 0; i < length && !canBlacklist; i++) {
      canBlacklist = i != index && !isBlacklisted(i, nowMs);
    }
    if (!canBlacklist) {
      return false;
    }
    blacklistUntilTimes[index] =
        Math.max(
            blacklistUntilTimes[index],
            Util.addWithOverflowDefault(nowMs, blacklistDurationMs, Long.MAX_VALUE));
    return true;
  }

  /**
   * Returns whether the track at the specified index in the selection is blacklisted.
   *
   * @param index The index of the track in the selection.
   * @param nowMs The current time in the timebase of {@link SystemClock#elapsedRealtime()}.
   */
  protected final boolean isBlacklisted(int index, long nowMs) {
    return blacklistUntilTimes[index] > nowMs;
  }

  // Object overrides.

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = 31 * System.identityHashCode(group) + Arrays.hashCode(tracks);
    }
    return hashCode;
  }

  // Track groups are compared by identity not value, as distinct groups may have the same value.
  @Override
  @SuppressWarnings({"ReferenceEquality", "EqualsGetClass"})
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BaseTrackSelection other = (BaseTrackSelection) obj;
    return group == other.group && Arrays.equals(tracks, other.tracks);
  }

  /**
   * Sorts {@link Format} objects in order of decreasing bandwidth.
   */
  private static final class DecreasingBandwidthComparator implements Comparator<Format> {

    @Override
    public int compare(Format a, Format b) {
      return b.bitrate - a.bitrate;
    }

  }

}
