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
package com.google.android.exoplayer2.ui;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.util.Assertions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A view for making track selections. */
public class TrackSelectionView extends LinearLayout {

  /** Listener for changes to the selected tracks. */
  public interface TrackSelectionListener {

    /**
     * Called when the selected tracks changed.
     *
     * @param isDisabled Whether the renderer is disabled.
     * @param overrides List of selected track selection overrides for the renderer.
     */
    void onTrackSelectionChanged(boolean isDisabled, List<SelectionOverride> overrides);
  }

  private final int selectableItemBackgroundResourceId;
  private final LayoutInflater inflater;
  private final CheckedTextView disableView;
  private final CheckedTextView defaultView;
  private final ComponentListener componentListener;
  private final SparseArray<SelectionOverride> overrides;

  private boolean allowAdaptiveSelections;
  private boolean allowMultipleOverrides;

  private TrackNameProvider trackNameProvider;
  private CheckedTextView[][] trackViews;

  @MonotonicNonNull private MappedTrackInfo mappedTrackInfo;
  private int rendererIndex;
  private TrackGroupArray trackGroups;
  private boolean isDisabled;
  @Nullable private TrackSelectionListener listener;

  /** Creates a track selection view. */
  public TrackSelectionView(Context context) {
    this(context, null);
  }

  /** Creates a track selection view. */
  public TrackSelectionView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  /** Creates a track selection view. */
  @SuppressWarnings("nullness")
  public TrackSelectionView(
      Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setOrientation(LinearLayout.VERTICAL);

    overrides = new SparseArray<>();

    // Don't save view hierarchy as it needs to be reinitialized with a call to init.
    setSaveFromParentEnabled(false);

    TypedArray attributeArray =
        context
            .getTheme()
            .obtainStyledAttributes(new int[] {android.R.attr.selectableItemBackground});
    selectableItemBackgroundResourceId = attributeArray.getResourceId(0, 0);
    attributeArray.recycle();

    inflater = LayoutInflater.from(context);
    componentListener = new ComponentListener();
    trackNameProvider = new DefaultTrackNameProvider(getResources());
    trackGroups = TrackGroupArray.EMPTY;

    // View for disabling the renderer.
    disableView =
        (CheckedTextView)
            inflater.inflate(android.R.layout.simple_list_item_single_choice, this, false);
    disableView.setBackgroundResource(selectableItemBackgroundResourceId);
    disableView.setText(R.string.exo_track_selection_none);
    disableView.setEnabled(false);
    disableView.setFocusable(true);
    disableView.setOnClickListener(componentListener);
    disableView.setVisibility(View.GONE);
    addView(disableView);
    // Divider view.
    addView(inflater.inflate(R.layout.exo_list_divider, this, false));
    // View for clearing the override to allow the selector to use its default selection logic.
    defaultView =
        (CheckedTextView)
            inflater.inflate(android.R.layout.simple_list_item_single_choice, this, false);
    defaultView.setBackgroundResource(selectableItemBackgroundResourceId);
    defaultView.setText(R.string.exo_track_selection_auto);
    defaultView.setEnabled(false);
    defaultView.setFocusable(true);
    defaultView.setOnClickListener(componentListener);
    addView(defaultView);
  }

  /**
   * Sets whether adaptive selections (consisting of more than one track) can be made using this
   * selection view.
   *
   * <p>For the view to enable adaptive selection it is necessary both for this feature to be
   * enabled, and for the target renderer to support adaptation between the available tracks.
   *
   * @param allowAdaptiveSelections Whether adaptive selection is enabled.
   */
  public void setAllowAdaptiveSelections(boolean allowAdaptiveSelections) {
    if (this.allowAdaptiveSelections != allowAdaptiveSelections) {
      this.allowAdaptiveSelections = allowAdaptiveSelections;
      updateViews();
    }
  }

  /**
   * Sets whether tracks from multiple track groups can be selected. This results in multiple {@link
   * SelectionOverride SelectionOverrides} to be returned by {@link #getOverrides()}.
   *
   * @param allowMultipleOverrides Whether multiple track selection overrides can be selected.
   */
  public void setAllowMultipleOverrides(boolean allowMultipleOverrides) {
    if (this.allowMultipleOverrides != allowMultipleOverrides) {
      this.allowMultipleOverrides = allowMultipleOverrides;
      if (!allowMultipleOverrides && overrides.size() > 1) {
        for (int i = overrides.size() - 1; i > 0; i--) {
          overrides.remove(i);
        }
      }
      updateViews();
    }
  }

  /**
   * Sets whether an option is available for disabling the renderer.
   *
   * @param showDisableOption Whether the disable option is shown.
   */
  public void setShowDisableOption(boolean showDisableOption) {
    disableView.setVisibility(showDisableOption ? View.VISIBLE : View.GONE);
  }

  /**
   * Sets the {@link TrackNameProvider} used to generate the user visible name of each track and
   * updates the view with track names queried from the specified provider.
   *
   * @param trackNameProvider The {@link TrackNameProvider} to use.
   */
  public void setTrackNameProvider(TrackNameProvider trackNameProvider) {
    this.trackNameProvider = Assertions.checkNotNull(trackNameProvider);
    updateViews();
  }

  /**
   * Initialize the view to select tracks for a specified renderer using {@link MappedTrackInfo} and
   * a set of {@link DefaultTrackSelector.Parameters}.
   *
   * @param mappedTrackInfo The {@link MappedTrackInfo}.
   * @param rendererIndex The index of the renderer.
   * @param isDisabled Whether the renderer should be initially shown as disabled.
   * @param overrides List of initial overrides to be shown for this renderer. There must be at most
   *     one override for each track group. If {@link #setAllowMultipleOverrides(boolean)} hasn't
   *     been set to {@code true}, only the first override is used.
   * @param listener An optional listener for track selection updates.
   */
  public void init(
      MappedTrackInfo mappedTrackInfo,
      int rendererIndex,
      boolean isDisabled,
      List<SelectionOverride> overrides,
      @Nullable TrackSelectionListener listener) {
    this.mappedTrackInfo = mappedTrackInfo;
    this.rendererIndex = rendererIndex;
    this.isDisabled = isDisabled;
    this.listener = listener;
    int maxOverrides = allowMultipleOverrides ? overrides.size() : Math.min(overrides.size(), 1);
    for (int i = 0; i < maxOverrides; i++) {
      SelectionOverride override = overrides.get(i);
      this.overrides.put(override.groupIndex, override);
    }
    updateViews();
  }

  /** Returns whether the renderer is disabled. */
  public boolean getIsDisabled() {
    return isDisabled;
  }

  /**
   * Returns the list of selected track selection overrides. There will be at most one override for
   * each track group.
   */
  public List<SelectionOverride> getOverrides() {
    List<SelectionOverride> overrideList = new ArrayList<>(overrides.size());
    for (int i = 0; i < overrides.size(); i++) {
      overrideList.add(overrides.valueAt(i));
    }
    return overrideList;
  }

  // Private methods.

  private void updateViews() {
    // Remove previous per-track views.
    for (int i = getChildCount() - 1; i >= 3; i--) {
      removeViewAt(i);
    }

    if (mappedTrackInfo == null) {
      // The view is not initialized.
      disableView.setEnabled(false);
      defaultView.setEnabled(false);
      return;
    }
    disableView.setEnabled(true);
    defaultView.setEnabled(true);

    trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);

    // Add per-track views.
    trackViews = new CheckedTextView[trackGroups.length][];
    boolean enableMultipleChoiceForMultipleOverrides = shouldEnableMultiGroupSelection();
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup group = trackGroups.get(groupIndex);
      boolean enableMultipleChoiceForAdaptiveSelections = shouldEnableAdaptiveSelection(groupIndex);
      trackViews[groupIndex] = new CheckedTextView[group.length];
      for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
        if (trackIndex == 0) {
          addView(inflater.inflate(R.layout.exo_list_divider, this, false));
        }
        int trackViewLayoutId =
            enableMultipleChoiceForAdaptiveSelections || enableMultipleChoiceForMultipleOverrides
                ? android.R.layout.simple_list_item_multiple_choice
                : android.R.layout.simple_list_item_single_choice;
        CheckedTextView trackView =
            (CheckedTextView) inflater.inflate(trackViewLayoutId, this, false);
        trackView.setBackgroundResource(selectableItemBackgroundResourceId);
        trackView.setText(trackNameProvider.getTrackName(group.getFormat(trackIndex)));
        if (mappedTrackInfo.getTrackSupport(rendererIndex, groupIndex, trackIndex)
            == RendererCapabilities.FORMAT_HANDLED) {
          trackView.setFocusable(true);
          trackView.setTag(Pair.create(groupIndex, trackIndex));
          trackView.setOnClickListener(componentListener);
        } else {
          trackView.setFocusable(false);
          trackView.setEnabled(false);
        }
        trackViews[groupIndex][trackIndex] = trackView;
        addView(trackView);
      }
    }

    updateViewStates();
  }

  private void updateViewStates() {
    disableView.setChecked(isDisabled);
    defaultView.setChecked(!isDisabled && overrides.size() == 0);
    for (int i = 0; i < trackViews.length; i++) {
      SelectionOverride override = overrides.get(i);
      for (int j = 0; j < trackViews[i].length; j++) {
        trackViews[i][j].setChecked(override != null && override.containsTrack(j));
      }
    }
  }

  private void onClick(View view) {
    if (view == disableView) {
      onDisableViewClicked();
    } else if (view == defaultView) {
      onDefaultViewClicked();
    } else {
      onTrackViewClicked(view);
    }
    updateViewStates();
    if (listener != null) {
      listener.onTrackSelectionChanged(getIsDisabled(), getOverrides());
    }
  }

  private void onDisableViewClicked() {
    isDisabled = true;
    overrides.clear();
  }

  private void onDefaultViewClicked() {
    isDisabled = false;
    overrides.clear();
  }

  private void onTrackViewClicked(View view) {
    isDisabled = false;
    @SuppressWarnings("unchecked")
    Pair<Integer, Integer> tag = (Pair<Integer, Integer>) view.getTag();
    int groupIndex = tag.first;
    int trackIndex = tag.second;
    SelectionOverride override = overrides.get(groupIndex);
    Assertions.checkNotNull(mappedTrackInfo);
    if (override == null) {
      // Start new override.
      if (!allowMultipleOverrides && overrides.size() > 0) {
        // Removed other overrides if we don't allow multiple overrides.
        overrides.clear();
      }
      overrides.put(groupIndex, new SelectionOverride(groupIndex, trackIndex));
    } else {
      // An existing override is being modified.
      int overrideLength = override.length;
      int[] overrideTracks = override.tracks;
      boolean isCurrentlySelected = ((CheckedTextView) view).isChecked();
      boolean isAdaptiveAllowed = shouldEnableAdaptiveSelection(groupIndex);
      boolean isUsingCheckBox = isAdaptiveAllowed || shouldEnableMultiGroupSelection();
      if (isCurrentlySelected && isUsingCheckBox) {
        // Remove the track from the override.
        if (overrideLength == 1) {
          // The last track is being removed, so the override becomes empty.
          overrides.remove(groupIndex);
        } else {
          int[] tracks = getTracksRemoving(overrideTracks, trackIndex);
          overrides.put(groupIndex, new SelectionOverride(groupIndex, tracks));
        }
      } else if (!isCurrentlySelected) {
        if (isAdaptiveAllowed) {
          // Add new track to adaptive override.
          int[] tracks = getTracksAdding(overrideTracks, trackIndex);
          overrides.put(groupIndex, new SelectionOverride(groupIndex, tracks));
        } else {
          // Replace existing track in override.
          overrides.put(groupIndex, new SelectionOverride(groupIndex, trackIndex));
        }
      }
    }
  }

  @RequiresNonNull("mappedTrackInfo")
  private boolean shouldEnableAdaptiveSelection(int groupIndex) {
    return allowAdaptiveSelections
        && trackGroups.get(groupIndex).length > 1
        && mappedTrackInfo.getAdaptiveSupport(rendererIndex, groupIndex, false)
            != RendererCapabilities.ADAPTIVE_NOT_SUPPORTED;
  }

  private boolean shouldEnableMultiGroupSelection() {
    return allowMultipleOverrides && trackGroups.length > 1;
  }

  private static int[] getTracksAdding(int[] tracks, int addedTrack) {
    tracks = Arrays.copyOf(tracks, tracks.length + 1);
    tracks[tracks.length - 1] = addedTrack;
    return tracks;
  }

  private static int[] getTracksRemoving(int[] tracks, int removedTrack) {
    int[] newTracks = new int[tracks.length - 1];
    int trackCount = 0;
    for (int track : tracks) {
      if (track != removedTrack) {
        newTracks[trackCount++] = track;
      }
    }
    return newTracks;
  }

  // Internal classes.

  private class ComponentListener implements OnClickListener {

    @Override
    public void onClick(View view) {
      TrackSelectionView.this.onClick(view);
    }
  }
}
