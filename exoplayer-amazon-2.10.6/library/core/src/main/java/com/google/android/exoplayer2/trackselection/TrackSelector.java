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
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;

/**
 * The component of an {@link ExoPlayer} responsible for selecting tracks to be consumed by each of
 * the player's {@link Renderer}s. The {@link DefaultTrackSelector} implementation should be
 * suitable for most use cases.
 *
 * <h3>Interactions with the player</h3>
 *
 * The following interactions occur between the player and its track selector during playback.
 *
 * <p>
 *
 * <ul>
 *   <li>When the player is created it will initialize the track selector by calling {@link
 *       #init(InvalidationListener, BandwidthMeter)}.
 *   <li>When the player needs to make a track selection it will call {@link
 *       #selectTracks(RendererCapabilities[], TrackGroupArray, MediaPeriodId, Timeline)}. This
 *       typically occurs at the start of playback, when the player starts to buffer a new period of
 *       the media being played, and when the track selector invalidates its previous selections.
 *   <li>The player may perform a track selection well in advance of the selected tracks becoming
 *       active, where active is defined to mean that the renderers are actually consuming media
 *       corresponding to the selection that was made. For example when playing media containing
 *       multiple periods, the track selection for a period is made when the player starts to buffer
 *       that period. Hence if the player's buffering policy is to maintain a 30 second buffer, the
 *       selection will occur approximately 30 seconds in advance of it becoming active. In fact the
 *       selection may never become active, for example if the user seeks to some other period of
 *       the media during the 30 second gap. The player indicates to the track selector when a
 *       selection it has previously made becomes active by calling {@link
 *       #onSelectionActivated(Object)}.
 *   <li>If the track selector wishes to indicate to the player that selections it has previously
 *       made are invalid, it can do so by calling {@link
 *       InvalidationListener#onTrackSelectionsInvalidated()} on the {@link InvalidationListener}
 *       that was passed to {@link #init(InvalidationListener, BandwidthMeter)}. A track selector
 *       may wish to do this if its configuration has changed, for example if it now wishes to
 *       prefer audio tracks in a particular language. This will trigger the player to make new
 *       track selections. Note that the player will have to re-buffer in the case that the new
 *       track selection for the currently playing period differs from the one that was invalidated.
 * </ul>
 *
 * <h3>Renderer configuration</h3>
 *
 * The {@link TrackSelectorResult} returned by {@link #selectTracks(RendererCapabilities[],
 * TrackGroupArray, MediaPeriodId, Timeline)} contains not only {@link TrackSelection}s for each
 * renderer, but also {@link RendererConfiguration}s defining configuration parameters that the
 * renderers should apply when consuming the corresponding media. Whilst it may seem counter-
 * intuitive for a track selector to also specify renderer configuration information, in practice
 * the two are tightly bound together. It may only be possible to play a certain combination tracks
 * if the renderers are configured in a particular way. Equally, it may only be possible to
 * configure renderers in a particular way if certain tracks are selected. Hence it makes sense to
 * determined the track selection and corresponding renderer configurations in a single step.
 *
 * <h3>Threading model</h3>
 *
 * All calls made by the player into the track selector are on the player's internal playback
 * thread. The track selector may call {@link InvalidationListener#onTrackSelectionsInvalidated()}
 * from any thread.
 */
public abstract class TrackSelector {

  /**
   * Notified when selections previously made by a {@link TrackSelector} are no longer valid.
   */
  public interface InvalidationListener {

    /**
     * Called by a {@link TrackSelector} to indicate that selections it has previously made are no
     * longer valid. May be called from any thread.
     */
    void onTrackSelectionsInvalidated();

  }

  private @Nullable InvalidationListener listener;
  private @Nullable BandwidthMeter bandwidthMeter;

  /**
   * Called by the player to initialize the selector.
   *
   * @param listener An invalidation listener that the selector can call to indicate that selections
   *     it has previously made are no longer valid.
   * @param bandwidthMeter A bandwidth meter which can be used by track selections to select tracks.
   */
  public final void init(InvalidationListener listener, BandwidthMeter bandwidthMeter) {
    this.listener = listener;
    this.bandwidthMeter = bandwidthMeter;
  }

  /**
   * Called by the player to perform a track selection.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers for which tracks
   *     are to be selected.
   * @param trackGroups The available track groups.
   * @param periodId The {@link MediaPeriodId} of the period for which tracks are to be selected.
   * @param timeline The {@link Timeline} holding the period for which tracks are to be selected.
   * @return A {@link TrackSelectorResult} describing the track selections.
   * @throws ExoPlaybackException If an error occurs selecting tracks.
   */
  public abstract TrackSelectorResult selectTracks(
      RendererCapabilities[] rendererCapabilities,
      TrackGroupArray trackGroups,
      MediaPeriodId periodId,
      Timeline timeline)
      throws ExoPlaybackException;

  /**
   * Called by the player when a {@link TrackSelectorResult} previously generated by {@link
   * #selectTracks(RendererCapabilities[], TrackGroupArray, MediaPeriodId, Timeline)} is activated.
   *
   * @param info The value of {@link TrackSelectorResult#info} in the activated selection.
   */
  public abstract void onSelectionActivated(Object info);

  /**
   * Calls {@link InvalidationListener#onTrackSelectionsInvalidated()} to invalidate all previously
   * generated track selections.
   */
  protected final void invalidate() {
    if (listener != null) {
      listener.onTrackSelectionsInvalidated();
    }
  }

  /**
   * Returns a bandwidth meter which can be used by track selections to select tracks. Must only be
   * called after {@link #init(InvalidationListener, BandwidthMeter)} has been called.
   */
  protected final BandwidthMeter getBandwidthMeter() {
    return Assertions.checkNotNull(bandwidthMeter);
  }
}
