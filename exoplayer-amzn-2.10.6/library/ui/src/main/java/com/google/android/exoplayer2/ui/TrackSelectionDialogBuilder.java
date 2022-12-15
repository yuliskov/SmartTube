/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelectionUtil;
import com.google.android.exoplayer2.util.Assertions;
import java.util.Collections;
import java.util.List;

/** Builder for a dialog with a {@link TrackSelectionView}. */
public final class TrackSelectionDialogBuilder {

  /** Callback which is invoked when a track selection has been made. */
  public interface DialogCallback {

    /**
     * Called when tracks are selected.
     *
     * @param isDisabled Whether the renderer is disabled.
     * @param overrides List of selected track selection overrides for the renderer.
     */
    void onTracksSelected(boolean isDisabled, List<SelectionOverride> overrides);
  }

  private final Context context;
  private final CharSequence title;
  private final MappedTrackInfo mappedTrackInfo;
  private final int rendererIndex;
  private final DialogCallback callback;

  private boolean allowAdaptiveSelections;
  private boolean allowMultipleOverrides;
  private boolean showDisableOption;
  @Nullable private TrackNameProvider trackNameProvider;
  private boolean isDisabled;
  private List<SelectionOverride> overrides;

  /**
   * Creates a builder for a track selection dialog.
   *
   * @param context The context of the dialog.
   * @param title The title of the dialog.
   * @param mappedTrackInfo The {@link MappedTrackInfo} containing the track information.
   * @param rendererIndex The renderer index in the {@code mappedTrackInfo} for which the track
   *     selection is shown.
   * @param callback The {@link DialogCallback} invoked when a track selection has been made.
   */
  public TrackSelectionDialogBuilder(
      Context context,
      CharSequence title,
      MappedTrackInfo mappedTrackInfo,
      int rendererIndex,
      DialogCallback callback) {
    this.context = context;
    this.title = title;
    this.mappedTrackInfo = mappedTrackInfo;
    this.rendererIndex = rendererIndex;
    this.callback = callback;
    overrides = Collections.emptyList();
  }

  /**
   * Creates a builder for a track selection dialog which automatically updates a {@link
   * DefaultTrackSelector}.
   *
   * @param context The context of the dialog.
   * @param title The title of the dialog.
   * @param trackSelector A {@link DefaultTrackSelector} whose current selection is used to set up
   *     the dialog and which is updated when new tracks are selected in the dialog.
   * @param rendererIndex The renderer index in the {@code trackSelector} for which the track
   *     selection is shown.
   */
  public TrackSelectionDialogBuilder(
      Context context, CharSequence title, DefaultTrackSelector trackSelector, int rendererIndex) {
    this.context = context;
    this.title = title;
    this.mappedTrackInfo = Assertions.checkNotNull(trackSelector.getCurrentMappedTrackInfo());
    this.rendererIndex = rendererIndex;

    TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
    DefaultTrackSelector.Parameters selectionParameters = trackSelector.getParameters();
    isDisabled = selectionParameters.getRendererDisabled(rendererIndex);
    SelectionOverride override =
        selectionParameters.getSelectionOverride(rendererIndex, rendererTrackGroups);
    overrides = override == null ? Collections.emptyList() : Collections.singletonList(override);

    this.callback =
        (newIsDisabled, newOverrides) ->
            trackSelector.setParameters(
                TrackSelectionUtil.updateParametersWithOverride(
                    selectionParameters,
                    rendererIndex,
                    rendererTrackGroups,
                    newIsDisabled,
                    newOverrides.isEmpty() ? null : newOverrides.get(0)));
  }

  /**
   * Sets whether the selection is initially shown as disabled.
   *
   * @param isDisabled Whether the selection is initially shown as disabled.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setIsDisabled(boolean isDisabled) {
    this.isDisabled = isDisabled;
    return this;
  }

  /**
   * Sets the initial selection override to show.
   *
   * @param override The initial override to show, or null for no override.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setOverride(@Nullable SelectionOverride override) {
    return setOverrides(
        override == null ? Collections.emptyList() : Collections.singletonList(override));
  }

  /**
   * Sets the list of initial selection overrides to show.
   *
   * <p>Note that only the first override will be used unless {@link
   * #setAllowMultipleOverrides(boolean)} is set to {@code true}.
   *
   * @param overrides The list of initial overrides to show. There must be at most one override for
   *     each track group.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setOverrides(List<SelectionOverride> overrides) {
    this.overrides = overrides;
    return this;
  }

  /**
   * Sets whether adaptive selections (consisting of more than one track) can be made.
   *
   * <p>For the selection view to enable adaptive selection it is necessary both for this feature to
   * be enabled, and for the target renderer to support adaptation between the available tracks.
   *
   * @param allowAdaptiveSelections Whether adaptive selection is enabled.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setAllowAdaptiveSelections(boolean allowAdaptiveSelections) {
    this.allowAdaptiveSelections = allowAdaptiveSelections;
    return this;
  }

  /**
   * Sets whether multiple overrides can be set and selected, i.e. tracks from multiple track groups
   * can be selected.
   *
   * @param allowMultipleOverrides Whether multiple track selection overrides are allowed.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setAllowMultipleOverrides(boolean allowMultipleOverrides) {
    this.allowMultipleOverrides = allowMultipleOverrides;
    return this;
  }

  /**
   * Sets whether an option is available for disabling the renderer.
   *
   * @param showDisableOption Whether the disable option is shown.
   * @return This builder, for convenience.
   */
  public TrackSelectionDialogBuilder setShowDisableOption(boolean showDisableOption) {
    this.showDisableOption = showDisableOption;
    return this;
  }

  /**
   * Sets the {@link TrackNameProvider} used to generate the user visible name of each track and
   * updates the view with track names queried from the specified provider.
   *
   * @param trackNameProvider The {@link TrackNameProvider} to use, or null to use the default.
   */
  public TrackSelectionDialogBuilder setTrackNameProvider(
      @Nullable TrackNameProvider trackNameProvider) {
    this.trackNameProvider = trackNameProvider;
    return this;
  }

  /** Builds the dialog. */
  public AlertDialog build() {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);

    // Inflate with the builder's context to ensure the correct style is used.
    LayoutInflater dialogInflater = LayoutInflater.from(builder.getContext());
    View dialogView = dialogInflater.inflate(R.layout.exo_track_selection_dialog, /* root= */ null);

    TrackSelectionView selectionView = dialogView.findViewById(R.id.exo_track_selection_view);
    selectionView.setAllowMultipleOverrides(allowMultipleOverrides);
    selectionView.setAllowAdaptiveSelections(allowAdaptiveSelections);
    selectionView.setShowDisableOption(showDisableOption);
    if (trackNameProvider != null) {
      selectionView.setTrackNameProvider(trackNameProvider);
    }
    selectionView.init(mappedTrackInfo, rendererIndex, isDisabled, overrides, /* listener= */ null);
    Dialog.OnClickListener okClickListener =
        (dialog, which) ->
            callback.onTracksSelected(selectionView.getIsDisabled(), selectionView.getOverrides());

    return builder
        .setTitle(title)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok, okClickListener)
        .setNegativeButton(android.R.string.cancel, null)
        .create();
  }
}
