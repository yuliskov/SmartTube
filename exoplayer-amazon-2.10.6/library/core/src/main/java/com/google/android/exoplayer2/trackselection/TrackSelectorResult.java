/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * The result of a {@link TrackSelector} operation.
 */
public final class TrackSelectorResult {

  /** The number of selections in the result. Greater than or equal to zero. */
  public final int length;
  /**
   * A {@link RendererConfiguration} for each renderer. A null entry indicates the corresponding
   * renderer should be disabled.
   */
  public final @NullableType RendererConfiguration[] rendererConfigurations;
  /**
   * A {@link TrackSelectionArray} containing the track selection for each renderer.
   */
  public final TrackSelectionArray selections;
  /**
   * An opaque object that will be returned to {@link TrackSelector#onSelectionActivated(Object)}
   * should the selections be activated.
   */
  public final Object info;

  /**
   * @param rendererConfigurations A {@link RendererConfiguration} for each renderer. A null entry
   *     indicates the corresponding renderer should be disabled.
   * @param selections A {@link TrackSelectionArray} containing the selection for each renderer.
   * @param info An opaque object that will be returned to {@link
   *     TrackSelector#onSelectionActivated(Object)} should the selection be activated.
   */
  public TrackSelectorResult(
      @NullableType RendererConfiguration[] rendererConfigurations,
      @NullableType TrackSelection[] selections,
      Object info) {
    this.rendererConfigurations = rendererConfigurations;
    this.selections = new TrackSelectionArray(selections);
    this.info = info;
    length = rendererConfigurations.length;
  }

  /** Returns whether the renderer at the specified index is enabled. */
  public boolean isRendererEnabled(int index) {
    return rendererConfigurations[index] != null;
  }

  /**
   * Returns whether this result is equivalent to {@code other} for all renderers.
   *
   * @param other The other {@link TrackSelectorResult}. May be null, in which case {@code false}
   *     will be returned.
   * @return Whether this result is equivalent to {@code other} for all renderers.
   */
  public boolean isEquivalent(@Nullable TrackSelectorResult other) {
    if (other == null || other.selections.length != selections.length) {
      return false;
    }
    for (int i = 0; i < selections.length; i++) {
      if (!isEquivalent(other, i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns whether this result is equivalent to {@code other} for the renderer at the given index.
   * The results are equivalent if they have equal renderersEnabled array, track selections, and
   * configurations for the renderer.
   *
   * @param other The other {@link TrackSelectorResult}. May be null, in which case {@code false}
   *     will be returned.
   * @param index The renderer index to check for equivalence.
   * @return Whether this result is equivalent to {@code other} for the renderer at the specified
   *     index.
   */
  public boolean isEquivalent(@Nullable TrackSelectorResult other, int index) {
    if (other == null) {
      return false;
    }
    return Util.areEqual(rendererConfigurations[index], other.rendererConfigurations[index])
        && Util.areEqual(selections.get(index), other.selections.get(index));
  }

}
