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

import androidx.annotation.Nullable;
import java.util.Arrays;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** An array of {@link TrackSelection}s. */
public final class TrackSelectionArray {

  /** The length of this array. */
  public final int length;

  private final @NullableType TrackSelection[] trackSelections;

  // Lazily initialized hashcode.
  private int hashCode;

  /** @param trackSelections The selections. Must not be null, but may contain null elements. */
  public TrackSelectionArray(@NullableType TrackSelection... trackSelections) {
    this.trackSelections = trackSelections;
    this.length = trackSelections.length;
  }

  /**
   * Returns the selection at a given index.
   *
   * @param index The index of the selection.
   * @return The selection.
   */
  public @Nullable TrackSelection get(int index) {
    return trackSelections[index];
  }

  /** Returns the selections in a newly allocated array. */
  public @NullableType TrackSelection[] getAll() {
    return trackSelections.clone();
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + Arrays.hashCode(trackSelections);
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    TrackSelectionArray other = (TrackSelectionArray) obj;
    return Arrays.equals(trackSelections, other.trackSelections);
  }

}
