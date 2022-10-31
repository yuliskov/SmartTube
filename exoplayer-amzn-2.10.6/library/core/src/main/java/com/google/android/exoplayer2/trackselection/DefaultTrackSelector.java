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

import android.content.Context;
import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * A default {@link TrackSelector} suitable for most use cases. Track selections are made according
 * to configurable {@link Parameters}, which can be set by calling {@link
 * #setParameters(Parameters)}.
 *
 * <h3>Modifying parameters</h3>
 *
 * To modify only some aspects of the parameters currently used by a selector, it's possible to
 * obtain a {@link ParametersBuilder} initialized with the current {@link Parameters}. The desired
 * modifications can be made on the builder, and the resulting {@link Parameters} can then be built
 * and set on the selector. For example the following code modifies the parameters to restrict video
 * track selections to SD, and to select a German audio track if there is one:
 *
 * <pre>{@code
 * // Build on the current parameters.
 * Parameters currentParameters = trackSelector.getParameters();
 * // Build the resulting parameters.
 * Parameters newParameters = currentParameters
 *     .buildUpon()
 *     .setMaxVideoSizeSd()
 *     .setPreferredAudioLanguage("deu")
 *     .build();
 * // Set the new parameters.
 * trackSelector.setParameters(newParameters);
 * }</pre>
 *
 * Convenience methods and chaining allow this to be written more concisely as:
 *
 * <pre>{@code
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setMaxVideoSizeSd()
 *         .setPreferredAudioLanguage("deu"));
 * }</pre>
 *
 * Selection {@link Parameters} support many different options, some of which are described below.
 *
 * <h3>Selecting specific tracks</h3>
 *
 * Track selection overrides can be used to select specific tracks. To specify an override for a
 * renderer, it's first necessary to obtain the tracks that have been mapped to it:
 *
 * <pre>{@code
 * MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
 * TrackGroupArray rendererTrackGroups = mappedTrackInfo == null ? null
 *     : mappedTrackInfo.getTrackGroups(rendererIndex);
 * }</pre>
 *
 * If {@code rendererTrackGroups} is null then there aren't any currently mapped tracks, and so
 * setting an override isn't possible. Note that a {@link Player.EventListener} registered on the
 * player can be used to determine when the current tracks (and therefore the mapping) changes. If
 * {@code rendererTrackGroups} is non-null then an override can be set. The next step is to query
 * the properties of the available tracks to determine the {@code groupIndex} and the {@code
 * trackIndices} within the group it that should be selected. The override can then be specified
 * using {@link ParametersBuilder#setSelectionOverride}:
 *
 * <pre>{@code
 * SelectionOverride selectionOverride = new SelectionOverride(groupIndex, trackIndices);
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setSelectionOverride(rendererIndex, rendererTrackGroups, selectionOverride));
 * }</pre>
 *
 * <h3>Constraint based track selection</h3>
 *
 * Whilst track selection overrides make it possible to select specific tracks, the recommended way
 * of controlling which tracks are selected is by specifying constraints. For example consider the
 * case of wanting to restrict video track selections to SD, and preferring German audio tracks.
 * Track selection overrides could be used to select specific tracks meeting these criteria, however
 * a simpler and more flexible approach is to specify these constraints directly:
 *
 * <pre>{@code
 * trackSelector.setParameters(
 *     trackSelector
 *         .buildUponParameters()
 *         .setMaxVideoSizeSd()
 *         .setPreferredAudioLanguage("deu"));
 * }</pre>
 *
 * There are several benefits to using constraint based track selection instead of specific track
 * overrides:
 *
 * <ul>
 *   <li>You can specify constraints before knowing what tracks the media provides. This can
 *       simplify track selection code (e.g. you don't have to listen for changes in the available
 *       tracks before configuring the selector).
 *   <li>Constraints can be applied consistently across all periods in a complex piece of media,
 *       even if those periods contain different tracks. In contrast, a specific track override is
 *       only applied to periods whose tracks match those for which the override was set.
 * </ul>
 *
 * <h3>Disabling renderers</h3>
 *
 * Renderers can be disabled using {@link ParametersBuilder#setRendererDisabled}. Disabling a
 * renderer differs from setting a {@code null} override because the renderer is disabled
 * unconditionally, whereas a {@code null} override is applied only when the track groups available
 * to the renderer match the {@link TrackGroupArray} for which it was specified.
 *
 * <h3>Tunneling</h3>
 *
 * Tunneled playback can be enabled in cases where the combination of renderers and selected tracks
 * support it. Tunneled playback is enabled by passing an audio session ID to {@link
 * ParametersBuilder#setTunnelingAudioSessionId(int)}.
 */
public class DefaultTrackSelector extends MappingTrackSelector {

  /**
   * A builder for {@link Parameters}. See the {@link Parameters} documentation for explanations of
   * the parameters that can be configured using this builder.
   */
  public static final class ParametersBuilder extends TrackSelectionParameters.Builder {

    // Video
    private int maxVideoWidth;
    private int maxVideoHeight;
    private int maxVideoFrameRate;
    private int maxVideoBitrate;
    private boolean exceedVideoConstraintsIfNecessary;
    private boolean allowVideoMixedMimeTypeAdaptiveness;
    private boolean allowVideoNonSeamlessAdaptiveness;
    private int viewportWidth;
    private int viewportHeight;
    private boolean viewportOrientationMayChange;
    // Audio
    private int maxAudioChannelCount;
    private int maxAudioBitrate;
    private boolean exceedAudioConstraintsIfNecessary;
    private boolean allowAudioMixedMimeTypeAdaptiveness;
    private boolean allowAudioMixedSampleRateAdaptiveness;
    private boolean allowAudioMixedChannelCountAdaptiveness;
    // General
    private boolean forceLowestBitrate;
    private boolean forceHighestSupportedBitrate;
    private boolean exceedRendererCapabilitiesIfNecessary;
    private int tunnelingAudioSessionId;

    private final SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides;
    private final SparseBooleanArray rendererDisabledFlags;

    /** Creates a builder with default initial values. */
    public ParametersBuilder() {
      this(Parameters.DEFAULT);
    }

    /**
     * @param initialValues The {@link Parameters} from which the initial values of the builder are
     *     obtained.
     */
    private ParametersBuilder(Parameters initialValues) {
      super(initialValues);
      // Video
      maxVideoWidth = initialValues.maxVideoWidth;
      maxVideoHeight = initialValues.maxVideoHeight;
      maxVideoFrameRate = initialValues.maxVideoFrameRate;
      maxVideoBitrate = initialValues.maxVideoBitrate;
      exceedVideoConstraintsIfNecessary = initialValues.exceedVideoConstraintsIfNecessary;
      allowVideoMixedMimeTypeAdaptiveness = initialValues.allowVideoMixedMimeTypeAdaptiveness;
      allowVideoNonSeamlessAdaptiveness = initialValues.allowVideoNonSeamlessAdaptiveness;
      viewportWidth = initialValues.viewportWidth;
      viewportHeight = initialValues.viewportHeight;
      viewportOrientationMayChange = initialValues.viewportOrientationMayChange;
      // Audio
      maxAudioChannelCount = initialValues.maxAudioChannelCount;
      maxAudioBitrate = initialValues.maxAudioBitrate;
      exceedAudioConstraintsIfNecessary = initialValues.exceedAudioConstraintsIfNecessary;
      allowAudioMixedMimeTypeAdaptiveness = initialValues.allowAudioMixedMimeTypeAdaptiveness;
      allowAudioMixedSampleRateAdaptiveness = initialValues.allowAudioMixedSampleRateAdaptiveness;
      allowAudioMixedChannelCountAdaptiveness =
          initialValues.allowAudioMixedChannelCountAdaptiveness;
      // General
      forceLowestBitrate = initialValues.forceLowestBitrate;
      forceHighestSupportedBitrate = initialValues.forceHighestSupportedBitrate;
      exceedRendererCapabilitiesIfNecessary = initialValues.exceedRendererCapabilitiesIfNecessary;
      tunnelingAudioSessionId = initialValues.tunnelingAudioSessionId;
      // Overrides
      selectionOverrides = cloneSelectionOverrides(initialValues.selectionOverrides);
      rendererDisabledFlags = initialValues.rendererDisabledFlags.clone();
    }

    // Video

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(1279, 719)}.
     *
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoSizeSd() {
      return setMaxVideoSize(1279, 719);
    }

    /**
     * Equivalent to {@link #setMaxVideoSize setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)}.
     *
     * @return This builder.
     */
    public ParametersBuilder clearVideoSizeConstraints() {
      return setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Sets the maximum allowed video width and height.
     *
     * @param maxVideoWidth Maximum allowed video width in pixels.
     * @param maxVideoHeight Maximum allowed video height in pixels.
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      return this;
    }

    /**
     * Sets the maximum allowed video frame rate.
     *
     * @param maxVideoFrameRate Maximum allowed video frame rate in hertz.
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoFrameRate(int maxVideoFrameRate) {
      this.maxVideoFrameRate = maxVideoFrameRate;
      return this;
    }

    /**
     * Sets the maximum allowed video bitrate.
     *
     * @param maxVideoBitrate Maximum allowed video bitrate in bits per second.
     * @return This builder.
     */
    public ParametersBuilder setMaxVideoBitrate(int maxVideoBitrate) {
      this.maxVideoBitrate = maxVideoBitrate;
      return this;
    }

    /**
     * Sets whether to exceed the {@link #setMaxVideoSize(int, int)} and {@link
     * #setMaxAudioBitrate(int)} constraints when no selection can be made otherwise.
     *
     * @param exceedVideoConstraintsIfNecessary Whether to exceed video constraints when no
     *     selection can be made otherwise.
     * @return This builder.
     */
    public ParametersBuilder setExceedVideoConstraintsIfNecessary(
        boolean exceedVideoConstraintsIfNecessary) {
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      return this;
    }

    /**
     * Sets whether to allow adaptive video selections containing mixed MIME types.
     *
     * <p>Adaptations between different MIME types may not be completely seamless, in which case
     * {@link #setAllowVideoNonSeamlessAdaptiveness(boolean)} also needs to be {@code true} for
     * mixed MIME type selections to be made.
     *
     * @param allowVideoMixedMimeTypeAdaptiveness Whether to allow adaptive video selections
     *     containing mixed MIME types.
     * @return This builder.
     */
    public ParametersBuilder setAllowVideoMixedMimeTypeAdaptiveness(
        boolean allowVideoMixedMimeTypeAdaptiveness) {
      this.allowVideoMixedMimeTypeAdaptiveness = allowVideoMixedMimeTypeAdaptiveness;
      return this;
    }

    /**
     * Sets whether to allow adaptive video selections where adaptation may not be completely
     * seamless.
     *
     * @param allowVideoNonSeamlessAdaptiveness Whether to allow adaptive video selections where
     *     adaptation may not be completely seamless.
     * @return This builder.
     */
    public ParametersBuilder setAllowVideoNonSeamlessAdaptiveness(
        boolean allowVideoNonSeamlessAdaptiveness) {
      this.allowVideoNonSeamlessAdaptiveness = allowVideoNonSeamlessAdaptiveness;
      return this;
    }

    /**
     * Equivalent to calling {@link #setViewportSize(int, int, boolean)} with the viewport size
     * obtained from {@link Util#getPhysicalDisplaySize(Context)}.
     *
     * @param context Any context.
     * @param viewportOrientationMayChange Whether the viewport orientation may change during
     *     playback.
     * @return This builder.
     */
    public ParametersBuilder setViewportSizeToPhysicalDisplaySize(
        Context context, boolean viewportOrientationMayChange) {
      // Assume the viewport is fullscreen.
      Point viewportSize = Util.getPhysicalDisplaySize(context);
      return setViewportSize(viewportSize.x, viewportSize.y, viewportOrientationMayChange);
    }

    /**
     * Equivalent to {@link #setViewportSize setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE,
     * true)}.
     *
     * @return This builder.
     */
    public ParametersBuilder clearViewportSizeConstraints() {
      return setViewportSize(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    /**
     * Sets the viewport size to constrain adaptive video selections so that only tracks suitable
     * for the viewport are selected.
     *
     * @param viewportWidth Viewport width in pixels.
     * @param viewportHeight Viewport height in pixels.
     * @param viewportOrientationMayChange Whether the viewport orientation may change during
     *     playback.
     * @return This builder.
     */
    public ParametersBuilder setViewportSize(
        int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange) {
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      return this;
    }

    // Audio

    @Override
    public ParametersBuilder setPreferredAudioLanguage(@Nullable String preferredAudioLanguage) {
      super.setPreferredAudioLanguage(preferredAudioLanguage);
      return this;
    }

    /**
     * Sets the maximum allowed audio channel count.
     *
     * @param maxAudioChannelCount Maximum allowed audio channel count.
     * @return This builder.
     */
    public ParametersBuilder setMaxAudioChannelCount(int maxAudioChannelCount) {
      this.maxAudioChannelCount = maxAudioChannelCount;
      return this;
    }

    /**
     * Sets the maximum allowed audio bitrate.
     *
     * @param maxAudioBitrate Maximum allowed audio bitrate in bits per second.
     * @return This builder.
     */
    public ParametersBuilder setMaxAudioBitrate(int maxAudioBitrate) {
      this.maxAudioBitrate = maxAudioBitrate;
      return this;
    }

    /**
     * Sets whether to exceed the {@link #setMaxAudioChannelCount(int)} and {@link
     * #setMaxAudioBitrate(int)} constraints when no selection can be made otherwise.
     *
     * @param exceedAudioConstraintsIfNecessary Whether to exceed audio constraints when no
     *     selection can be made otherwise.
     * @return This builder.
     */
    public ParametersBuilder setExceedAudioConstraintsIfNecessary(
        boolean exceedAudioConstraintsIfNecessary) {
      this.exceedAudioConstraintsIfNecessary = exceedAudioConstraintsIfNecessary;
      return this;
    }

    /**
     * Sets whether to allow adaptive audio selections containing mixed MIME types.
     *
     * <p>Adaptations between different MIME types may not be completely seamless.
     *
     * @param allowAudioMixedMimeTypeAdaptiveness Whether to allow adaptive audio selections
     *     containing mixed MIME types.
     * @return This builder.
     */
    public ParametersBuilder setAllowAudioMixedMimeTypeAdaptiveness(
        boolean allowAudioMixedMimeTypeAdaptiveness) {
      this.allowAudioMixedMimeTypeAdaptiveness = allowAudioMixedMimeTypeAdaptiveness;
      return this;
    }

    /**
     * Sets whether to allow adaptive audio selections containing mixed sample rates.
     *
     * <p>Adaptations between different sample rates may not be completely seamless.
     *
     * @param allowAudioMixedSampleRateAdaptiveness Whether to allow adaptive audio selections
     *     containing mixed sample rates.
     * @return This builder.
     */
    public ParametersBuilder setAllowAudioMixedSampleRateAdaptiveness(
        boolean allowAudioMixedSampleRateAdaptiveness) {
      this.allowAudioMixedSampleRateAdaptiveness = allowAudioMixedSampleRateAdaptiveness;
      return this;
    }

    /**
     * Sets whether to allow adaptive audio selections containing mixed channel counts.
     *
     * <p>Adaptations between different channel counts may not be completely seamless.
     *
     * @param allowAudioMixedChannelCountAdaptiveness Whether to allow adaptive audio selections
     *     containing mixed channel counts.
     * @return This builder.
     */
    public ParametersBuilder setAllowAudioMixedChannelCountAdaptiveness(
        boolean allowAudioMixedChannelCountAdaptiveness) {
      this.allowAudioMixedChannelCountAdaptiveness = allowAudioMixedChannelCountAdaptiveness;
      return this;
    }

    // Text

    @Override
    public ParametersBuilder setPreferredTextLanguage(@Nullable String preferredTextLanguage) {
      super.setPreferredTextLanguage(preferredTextLanguage);
      return this;
    }

    @Override
    public ParametersBuilder setPreferredTextRoleFlags(@C.RoleFlags int preferredTextRoleFlags) {
      super.setPreferredTextRoleFlags(preferredTextRoleFlags);
      return this;
    }

    @Override
    public ParametersBuilder setSelectUndeterminedTextLanguage(
        boolean selectUndeterminedTextLanguage) {
      super.setSelectUndeterminedTextLanguage(selectUndeterminedTextLanguage);
      return this;
    }

    @Override
    public ParametersBuilder setDisabledTextTrackSelectionFlags(
        @C.SelectionFlags int disabledTextTrackSelectionFlags) {
      super.setDisabledTextTrackSelectionFlags(disabledTextTrackSelectionFlags);
      return this;
    }
    // General

    /**
     * Sets whether to force selection of the single lowest bitrate audio and video tracks that
     * comply with all other constraints.
     *
     * @param forceLowestBitrate Whether to force selection of the single lowest bitrate audio and
     *     video tracks.
     * @return This builder.
     */
    public ParametersBuilder setForceLowestBitrate(boolean forceLowestBitrate) {
      this.forceLowestBitrate = forceLowestBitrate;
      return this;
    }

    /**
     * Sets whether to force selection of the highest bitrate audio and video tracks that comply
     * with all other constraints.
     *
     * @param forceHighestSupportedBitrate Whether to force selection of the highest bitrate audio
     *     and video tracks.
     * @return This builder.
     */
    public ParametersBuilder setForceHighestSupportedBitrate(boolean forceHighestSupportedBitrate) {
      this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
      return this;
    }

    /**
     * @deprecated Use {@link #setAllowVideoMixedMimeTypeAdaptiveness(boolean)} and {@link
     *     #setAllowAudioMixedMimeTypeAdaptiveness(boolean)}.
     */
    @Deprecated
    public ParametersBuilder setAllowMixedMimeAdaptiveness(boolean allowMixedMimeAdaptiveness) {
      setAllowAudioMixedMimeTypeAdaptiveness(allowMixedMimeAdaptiveness);
      setAllowVideoMixedMimeTypeAdaptiveness(allowMixedMimeAdaptiveness);
      return this;
    }

    /** @deprecated Use {@link #setAllowVideoNonSeamlessAdaptiveness(boolean)} */
    @Deprecated
    public ParametersBuilder setAllowNonSeamlessAdaptiveness(boolean allowNonSeamlessAdaptiveness) {
      return setAllowVideoNonSeamlessAdaptiveness(allowNonSeamlessAdaptiveness);
    }

    /**
     * Sets whether to exceed renderer capabilities when no selection can be made otherwise.
     *
     * <p>This parameter applies when all of the tracks available for a renderer exceed the
     * renderer's reported capabilities. If the parameter is {@code true} then the lowest quality
     * track will still be selected. Playback may succeed if the renderer has under-reported its
     * true capabilities. If {@code false} then no track will be selected.
     *
     * @param exceedRendererCapabilitiesIfNecessary Whether to exceed renderer capabilities when no
     *     selection can be made otherwise.
     * @return This builder.
     */
    public ParametersBuilder setExceedRendererCapabilitiesIfNecessary(
        boolean exceedRendererCapabilitiesIfNecessary) {
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
      return this;
    }

    /**
     * Sets the audio session id to use when tunneling.
     *
     * <p>Enables or disables tunneling. To enable tunneling, pass an audio session id to use when
     * in tunneling mode. Session ids can be generated using {@link
     * C#generateAudioSessionIdV21(Context)}. To disable tunneling pass {@link
     * C#AUDIO_SESSION_ID_UNSET}. Tunneling will only be activated if it's both enabled and
     * supported by the audio and video renderers for the selected tracks.
     *
     * @param tunnelingAudioSessionId The audio session id to use when tunneling, or {@link
     *     C#AUDIO_SESSION_ID_UNSET} to disable tunneling.
     * @return This builder.
     */
    public ParametersBuilder setTunnelingAudioSessionId(int tunnelingAudioSessionId) {
      this.tunnelingAudioSessionId = tunnelingAudioSessionId;
      return this;
    }

    // Overrides

    /**
     * Sets whether the renderer at the specified index is disabled. Disabling a renderer prevents
     * the selector from selecting any tracks for it.
     *
     * @param rendererIndex The renderer index.
     * @param disabled Whether the renderer is disabled.
     * @return This builder.
     */
    public final ParametersBuilder setRendererDisabled(int rendererIndex, boolean disabled) {
      if (rendererDisabledFlags.get(rendererIndex) == disabled) {
        // The disabled flag is unchanged.
        return this;
      }
      // Only true values are placed in the array to make it easier to check for equality.
      if (disabled) {
        rendererDisabledFlags.put(rendererIndex, true);
      } else {
        rendererDisabledFlags.delete(rendererIndex);
      }
      return this;
    }

    /**
     * Overrides the track selection for the renderer at the specified index.
     *
     * <p>When the {@link TrackGroupArray} mapped to the renderer matches the one provided, the
     * override is applied. When the {@link TrackGroupArray} does not match, the override has no
     * effect. The override replaces any previous override for the specified {@link TrackGroupArray}
     * for the specified {@link Renderer}.
     *
     * <p>Passing a {@code null} override will cause the renderer to be disabled when the {@link
     * TrackGroupArray} mapped to it matches the one provided. When the {@link TrackGroupArray} does
     * not match a {@code null} override has no effect. Hence a {@code null} override differs from
     * disabling the renderer using {@link #setRendererDisabled(int, boolean)} because the renderer
     * is disabled conditionally on the {@link TrackGroupArray} mapped to it, where-as {@link
     * #setRendererDisabled(int, boolean)} disables the renderer unconditionally.
     *
     * <p>To remove overrides use {@link #clearSelectionOverride(int, TrackGroupArray)}, {@link
     * #clearSelectionOverrides(int)} or {@link #clearSelectionOverrides()}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray} for which the override should be applied.
     * @param override The override.
     * @return This builder.
     */
    public final ParametersBuilder setSelectionOverride(
        int rendererIndex, TrackGroupArray groups, SelectionOverride override) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      if (overrides == null) {
        overrides = new HashMap<>();
        selectionOverrides.put(rendererIndex, overrides);
      }
      if (overrides.containsKey(groups) && Util.areEqual(overrides.get(groups), override)) {
        // The override is unchanged.
        return this;
      }
      overrides.put(groups, override);
      return this;
    }

    /**
     * Clears a track selection override for the specified renderer and {@link TrackGroupArray}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray} for which the override should be cleared.
     * @return This builder.
     */
    public final ParametersBuilder clearSelectionOverride(
        int rendererIndex, TrackGroupArray groups) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      if (overrides == null || !overrides.containsKey(groups)) {
        // Nothing to clear.
        return this;
      }
      overrides.remove(groups);
      if (overrides.isEmpty()) {
        selectionOverrides.remove(rendererIndex);
      }
      return this;
    }

    /**
     * Clears all track selection overrides for the specified renderer.
     *
     * @param rendererIndex The renderer index.
     * @return This builder.
     */
    public final ParametersBuilder clearSelectionOverrides(int rendererIndex) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      if (overrides == null || overrides.isEmpty()) {
        // Nothing to clear.
        return this;
      }
      selectionOverrides.remove(rendererIndex);
      return this;
    }

    /**
     * Clears all track selection overrides for all renderers.
     *
     * @return This builder.
     */
    public final ParametersBuilder clearSelectionOverrides() {
      if (selectionOverrides.size() == 0) {
        // Nothing to clear.
        return this;
      }
      selectionOverrides.clear();
      return this;
    }

    /**
     * Builds a {@link Parameters} instance with the selected values.
     */
    public Parameters build() {
      return new Parameters(
          // Video
          maxVideoWidth,
          maxVideoHeight,
          maxVideoFrameRate,
          maxVideoBitrate,
          exceedVideoConstraintsIfNecessary,
          allowVideoMixedMimeTypeAdaptiveness,
          allowVideoNonSeamlessAdaptiveness,
          viewportWidth,
          viewportHeight,
          viewportOrientationMayChange,
          // Audio
          preferredAudioLanguage,
          maxAudioChannelCount,
          maxAudioBitrate,
          exceedAudioConstraintsIfNecessary,
          allowAudioMixedMimeTypeAdaptiveness,
          allowAudioMixedSampleRateAdaptiveness,
          allowAudioMixedChannelCountAdaptiveness,
          // Text
          preferredTextLanguage,
          preferredTextRoleFlags,
          selectUndeterminedTextLanguage,
          disabledTextTrackSelectionFlags,
          // General
          forceLowestBitrate,
          forceHighestSupportedBitrate,
          exceedRendererCapabilitiesIfNecessary,
          tunnelingAudioSessionId,
          selectionOverrides,
          rendererDisabledFlags);
    }

    private static SparseArray<Map<TrackGroupArray, SelectionOverride>> cloneSelectionOverrides(
        SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides) {
      SparseArray<Map<TrackGroupArray, SelectionOverride>> clone = new SparseArray<>();
      for (int i = 0; i < selectionOverrides.size(); i++) {
        clone.put(selectionOverrides.keyAt(i), new HashMap<>(selectionOverrides.valueAt(i)));
      }
      return clone;
    }
  }

  /**
   * Extends {@link TrackSelectionParameters} by adding fields that are specific to {@link
   * DefaultTrackSelector}.
   */
  public static final class Parameters extends TrackSelectionParameters {

    /** An instance with default values. */
    public static final Parameters DEFAULT = new Parameters();

    // Video
    /**
     * Maximum allowed video width in pixels. The default value is {@link Integer#MAX_VALUE} (i.e.
     * no constraint).
     *
     * <p>To constrain adaptive video track selections to be suitable for a given viewport (the
     * region of the display within which video will be played), use ({@link #viewportWidth}, {@link
     * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
     */
    public final int maxVideoWidth;
    /**
     * Maximum allowed video height in pixels. The default value is {@link Integer#MAX_VALUE} (i.e.
     * no constraint).
     *
     * <p>To constrain adaptive video track selections to be suitable for a given viewport (the
     * region of the display within which video will be played), use ({@link #viewportWidth}, {@link
     * #viewportHeight} and {@link #viewportOrientationMayChange}) instead.
     */
    public final int maxVideoHeight;
    /**
     * Maximum allowed video frame rate in hertz. The default value is {@link Integer#MAX_VALUE}
     * (i.e. no constraint).
     */
    public final int maxVideoFrameRate;
    /**
     * Maximum allowed video bitrate in bits per second. The default value is {@link
     * Integer#MAX_VALUE} (i.e. no constraint).
     */
    public final int maxVideoBitrate;
    /**
     * Whether to exceed the {@link #maxVideoWidth}, {@link #maxVideoHeight} and {@link
     * #maxVideoBitrate} constraints when no selection can be made otherwise. The default value is
     * {@code true}.
     */
    public final boolean exceedVideoConstraintsIfNecessary;
    /**
     * Whether to allow adaptive video selections containing mixed MIME types. Adaptations between
     * different MIME types may not be completely seamless, in which case {@link
     * #allowVideoNonSeamlessAdaptiveness} also needs to be {@code true} for mixed MIME type
     * selections to be made. The default value is {@code false}.
     */
    public final boolean allowVideoMixedMimeTypeAdaptiveness;
    /**
     * Whether to allow adaptive video selections where adaptation may not be completely seamless.
     * The default value is {@code true}.
     */
    public final boolean allowVideoNonSeamlessAdaptiveness;
    /**
     * Viewport width in pixels. Constrains video track selections for adaptive content so that only
     * tracks suitable for the viewport are selected. The default value is {@link Integer#MAX_VALUE}
     * (i.e. no constraint).
     */
    public final int viewportWidth;
    /**
     * Viewport height in pixels. Constrains video track selections for adaptive content so that
     * only tracks suitable for the viewport are selected. The default value is {@link
     * Integer#MAX_VALUE} (i.e. no constraint).
     */
    public final int viewportHeight;
    /**
     * Whether the viewport orientation may change during playback. Constrains video track
     * selections for adaptive content so that only tracks suitable for the viewport are selected.
     * The default value is {@code true}.
     */
    public final boolean viewportOrientationMayChange;
    // Audio
    /**
     * Maximum allowed audio channel count. The default value is {@link Integer#MAX_VALUE} (i.e. no
     * constraint).
     */
    public final int maxAudioChannelCount;
    /**
     * Maximum allowed audio bitrate in bits per second. The default value is {@link
     * Integer#MAX_VALUE} (i.e. no constraint).
     */
    public final int maxAudioBitrate;
    /**
     * Whether to exceed the {@link #maxAudioChannelCount} and {@link #maxAudioBitrate} constraints
     * when no selection can be made otherwise. The default value is {@code true}.
     */
    public final boolean exceedAudioConstraintsIfNecessary;
    /**
     * Whether to allow adaptive audio selections containing mixed MIME types. Adaptations between
     * different MIME types may not be completely seamless. The default value is {@code false}.
     */
    public final boolean allowAudioMixedMimeTypeAdaptiveness;
    /**
     * Whether to allow adaptive audio selections containing mixed sample rates. Adaptations between
     * different sample rates may not be completely seamless. The default value is {@code false}.
     */
    public final boolean allowAudioMixedSampleRateAdaptiveness;
    /**
     * Whether to allow adaptive audio selections containing mixed channel counts. Adaptations
     * between different channel counts may not be completely seamless. The default value is {@code
     * false}.
     */
    public final boolean allowAudioMixedChannelCountAdaptiveness;

    // General
    /**
     * Whether to force selection of the single lowest bitrate audio and video tracks that comply
     * with all other constraints. The default value is {@code false}.
     */
    public final boolean forceLowestBitrate;
    /**
     * Whether to force selection of the highest bitrate audio and video tracks that comply with all
     * other constraints. The default value is {@code false}.
     */
    public final boolean forceHighestSupportedBitrate;
    /**
     * @deprecated Use {@link #allowVideoMixedMimeTypeAdaptiveness} and {@link
     *     #allowAudioMixedMimeTypeAdaptiveness}.
     */
    @Deprecated public final boolean allowMixedMimeAdaptiveness;
    /** @deprecated Use {@link #allowVideoNonSeamlessAdaptiveness}. */
    @Deprecated public final boolean allowNonSeamlessAdaptiveness;
    /**
     * Whether to exceed renderer capabilities when no selection can be made otherwise.
     *
     * <p>This parameter applies when all of the tracks available for a renderer exceed the
     * renderer's reported capabilities. If the parameter is {@code true} then the lowest quality
     * track will still be selected. Playback may succeed if the renderer has under-reported its
     * true capabilities. If {@code false} then no track will be selected. The default value is
     * {@code true}.
     */
    public final boolean exceedRendererCapabilitiesIfNecessary;
    /**
     * The audio session id to use when tunneling, or {@link C#AUDIO_SESSION_ID_UNSET} if tunneling
     * is disabled. The default value is {@link C#AUDIO_SESSION_ID_UNSET} (i.e. tunneling is
     * disabled).
     */
    public final int tunnelingAudioSessionId;

    // Overrides
    private final SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides;
    private final SparseBooleanArray rendererDisabledFlags;

    private Parameters() {
      this(
          // Video
          /* maxVideoWidth= */ Integer.MAX_VALUE,
          /* maxVideoHeight= */ Integer.MAX_VALUE,
          /* maxVideoFrameRate= */ Integer.MAX_VALUE,
          /* maxVideoBitrate= */ Integer.MAX_VALUE,
          /* exceedVideoConstraintsIfNecessary= */ true,
          /* allowVideoMixedMimeTypeAdaptiveness= */ false,
          /* allowVideoNonSeamlessAdaptiveness= */ true,
          /* viewportWidth= */ Integer.MAX_VALUE,
          /* viewportHeight= */ Integer.MAX_VALUE,
          /* viewportOrientationMayChange= */ true,
          // Audio
          TrackSelectionParameters.DEFAULT.preferredAudioLanguage,
          /* maxAudioChannelCount= */ Integer.MAX_VALUE,
          /* maxAudioBitrate= */ Integer.MAX_VALUE,
          /* exceedAudioConstraintsIfNecessary= */ true,
          /* allowAudioMixedMimeTypeAdaptiveness= */ false,
          /* allowAudioMixedSampleRateAdaptiveness= */ false,
          /* allowAudioMixedChannelCountAdaptiveness= */ false,
          // Text
          TrackSelectionParameters.DEFAULT.preferredTextLanguage,
          TrackSelectionParameters.DEFAULT.preferredTextRoleFlags,
          TrackSelectionParameters.DEFAULT.selectUndeterminedTextLanguage,
          TrackSelectionParameters.DEFAULT.disabledTextTrackSelectionFlags,
          // General
          /* forceLowestBitrate= */ false,
          /* forceHighestSupportedBitrate= */ false,
          /* exceedRendererCapabilitiesIfNecessary= */ true,
          /* tunnelingAudioSessionId= */ C.AUDIO_SESSION_ID_UNSET,
          new SparseArray<>(),
          new SparseBooleanArray());
    }

    /* package */ Parameters(
        // Video
        int maxVideoWidth,
        int maxVideoHeight,
        int maxVideoFrameRate,
        int maxVideoBitrate,
        boolean exceedVideoConstraintsIfNecessary,
        boolean allowVideoMixedMimeTypeAdaptiveness,
        boolean allowVideoNonSeamlessAdaptiveness,
        int viewportWidth,
        int viewportHeight,
        boolean viewportOrientationMayChange,
        // Audio
        @Nullable String preferredAudioLanguage,
        int maxAudioChannelCount,
        int maxAudioBitrate,
        boolean exceedAudioConstraintsIfNecessary,
        boolean allowAudioMixedMimeTypeAdaptiveness,
        boolean allowAudioMixedSampleRateAdaptiveness,
        boolean allowAudioMixedChannelCountAdaptiveness,
        // Text
        @Nullable String preferredTextLanguage,
        @C.RoleFlags int preferredTextRoleFlags,
        boolean selectUndeterminedTextLanguage,
        @C.SelectionFlags int disabledTextTrackSelectionFlags,
        // General
        boolean forceLowestBitrate,
        boolean forceHighestSupportedBitrate,
        boolean exceedRendererCapabilitiesIfNecessary,
        int tunnelingAudioSessionId,
        // Overrides
        SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides,
        SparseBooleanArray rendererDisabledFlags) {
      super(
          preferredAudioLanguage,
          preferredTextLanguage,
          preferredTextRoleFlags,
          selectUndeterminedTextLanguage,
          disabledTextTrackSelectionFlags);
      // Video
      this.maxVideoWidth = maxVideoWidth;
      this.maxVideoHeight = maxVideoHeight;
      this.maxVideoFrameRate = maxVideoFrameRate;
      this.maxVideoBitrate = maxVideoBitrate;
      this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
      this.allowVideoMixedMimeTypeAdaptiveness = allowVideoMixedMimeTypeAdaptiveness;
      this.allowVideoNonSeamlessAdaptiveness = allowVideoNonSeamlessAdaptiveness;
      this.viewportWidth = viewportWidth;
      this.viewportHeight = viewportHeight;
      this.viewportOrientationMayChange = viewportOrientationMayChange;
      // Audio
      this.maxAudioChannelCount = maxAudioChannelCount;
      this.maxAudioBitrate = maxAudioBitrate;
      this.exceedAudioConstraintsIfNecessary = exceedAudioConstraintsIfNecessary;
      this.allowAudioMixedMimeTypeAdaptiveness = allowAudioMixedMimeTypeAdaptiveness;
      this.allowAudioMixedSampleRateAdaptiveness = allowAudioMixedSampleRateAdaptiveness;
      this.allowAudioMixedChannelCountAdaptiveness = allowAudioMixedChannelCountAdaptiveness;
      // General
      this.forceLowestBitrate = forceLowestBitrate;
      this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
      this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
      this.tunnelingAudioSessionId = tunnelingAudioSessionId;
      // Deprecated fields.
      this.allowMixedMimeAdaptiveness = allowVideoMixedMimeTypeAdaptiveness;
      this.allowNonSeamlessAdaptiveness = allowVideoNonSeamlessAdaptiveness;
      // Overrides
      this.selectionOverrides = selectionOverrides;
      this.rendererDisabledFlags = rendererDisabledFlags;
    }

    /* package */
    Parameters(Parcel in) {
      super(in);
      // Video
      this.maxVideoWidth = in.readInt();
      this.maxVideoHeight = in.readInt();
      this.maxVideoFrameRate = in.readInt();
      this.maxVideoBitrate = in.readInt();
      this.exceedVideoConstraintsIfNecessary = Util.readBoolean(in);
      this.allowVideoMixedMimeTypeAdaptiveness = Util.readBoolean(in);
      this.allowVideoNonSeamlessAdaptiveness = Util.readBoolean(in);
      this.viewportWidth = in.readInt();
      this.viewportHeight = in.readInt();
      this.viewportOrientationMayChange = Util.readBoolean(in);
      // Audio
      this.maxAudioChannelCount = in.readInt();
      this.maxAudioBitrate = in.readInt();
      this.exceedAudioConstraintsIfNecessary = Util.readBoolean(in);
      this.allowAudioMixedMimeTypeAdaptiveness = Util.readBoolean(in);
      this.allowAudioMixedSampleRateAdaptiveness = Util.readBoolean(in);
      this.allowAudioMixedChannelCountAdaptiveness = Util.readBoolean(in);
      // General
      this.forceLowestBitrate = Util.readBoolean(in);
      this.forceHighestSupportedBitrate = Util.readBoolean(in);
      this.exceedRendererCapabilitiesIfNecessary = Util.readBoolean(in);
      this.tunnelingAudioSessionId = in.readInt();
      // Overrides
      this.selectionOverrides = readSelectionOverrides(in);
      this.rendererDisabledFlags = Util.castNonNull(in.readSparseBooleanArray());
      // Deprecated fields.
      this.allowMixedMimeAdaptiveness = allowVideoMixedMimeTypeAdaptiveness;
      this.allowNonSeamlessAdaptiveness = allowVideoNonSeamlessAdaptiveness;
    }

    /**
     * Returns whether the renderer is disabled.
     *
     * @param rendererIndex The renderer index.
     * @return Whether the renderer is disabled.
     */
    public final boolean getRendererDisabled(int rendererIndex) {
      return rendererDisabledFlags.get(rendererIndex);
    }

    /**
     * Returns whether there is an override for the specified renderer and {@link TrackGroupArray}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray}.
     * @return Whether there is an override.
     */
    public final boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      return overrides != null && overrides.containsKey(groups);
    }

    /**
     * Returns the override for the specified renderer and {@link TrackGroupArray}.
     *
     * @param rendererIndex The renderer index.
     * @param groups The {@link TrackGroupArray}.
     * @return The override, or null if no override exists.
     */
    @Nullable
    public final SelectionOverride getSelectionOverride(int rendererIndex, TrackGroupArray groups) {
      Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.get(rendererIndex);
      return overrides != null ? overrides.get(groups) : null;
    }

    /** Creates a new {@link ParametersBuilder}, copying the initial values from this instance. */
    @Override
    public ParametersBuilder buildUpon() {
      return new ParametersBuilder(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Parameters other = (Parameters) obj;
      return super.equals(obj)
          // Video
          && maxVideoWidth == other.maxVideoWidth
          && maxVideoHeight == other.maxVideoHeight
          && maxVideoFrameRate == other.maxVideoFrameRate
          && maxVideoBitrate == other.maxVideoBitrate
          && exceedVideoConstraintsIfNecessary == other.exceedVideoConstraintsIfNecessary
          && allowVideoMixedMimeTypeAdaptiveness == other.allowVideoMixedMimeTypeAdaptiveness
          && allowVideoNonSeamlessAdaptiveness == other.allowVideoNonSeamlessAdaptiveness
          && viewportOrientationMayChange == other.viewportOrientationMayChange
          && viewportWidth == other.viewportWidth
          && viewportHeight == other.viewportHeight
          // Audio
          && maxAudioChannelCount == other.maxAudioChannelCount
          && maxAudioBitrate == other.maxAudioBitrate
          && exceedAudioConstraintsIfNecessary == other.exceedAudioConstraintsIfNecessary
          && allowAudioMixedMimeTypeAdaptiveness == other.allowAudioMixedMimeTypeAdaptiveness
          && allowAudioMixedSampleRateAdaptiveness == other.allowAudioMixedSampleRateAdaptiveness
          && allowAudioMixedChannelCountAdaptiveness
              == other.allowAudioMixedChannelCountAdaptiveness
          // General
          && forceLowestBitrate == other.forceLowestBitrate
          && forceHighestSupportedBitrate == other.forceHighestSupportedBitrate
          && exceedRendererCapabilitiesIfNecessary == other.exceedRendererCapabilitiesIfNecessary
          && tunnelingAudioSessionId == other.tunnelingAudioSessionId
          // Overrides
          && areRendererDisabledFlagsEqual(rendererDisabledFlags, other.rendererDisabledFlags)
          && areSelectionOverridesEqual(selectionOverrides, other.selectionOverrides);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      // Video
      result = 31 * result + maxVideoWidth;
      result = 31 * result + maxVideoHeight;
      result = 31 * result + maxVideoFrameRate;
      result = 31 * result + maxVideoBitrate;
      result = 31 * result + (exceedVideoConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (allowVideoMixedMimeTypeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowVideoNonSeamlessAdaptiveness ? 1 : 0);
      result = 31 * result + (viewportOrientationMayChange ? 1 : 0);
      result = 31 * result + viewportWidth;
      result = 31 * result + viewportHeight;
      // Audio
      result = 31 * result + maxAudioChannelCount;
      result = 31 * result + maxAudioBitrate;
      result = 31 * result + (exceedAudioConstraintsIfNecessary ? 1 : 0);
      result = 31 * result + (allowAudioMixedMimeTypeAdaptiveness ? 1 : 0);
      result = 31 * result + (allowAudioMixedSampleRateAdaptiveness ? 1 : 0);
      result = 31 * result + (allowAudioMixedChannelCountAdaptiveness ? 1 : 0);
      // General
      result = 31 * result + (forceLowestBitrate ? 1 : 0);
      result = 31 * result + (forceHighestSupportedBitrate ? 1 : 0);
      result = 31 * result + (exceedRendererCapabilitiesIfNecessary ? 1 : 0);
      result = 31 * result + tunnelingAudioSessionId;
      // Overrides (omitted from hashCode).
      return result;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      super.writeToParcel(dest, flags);
      // Video
      dest.writeInt(maxVideoWidth);
      dest.writeInt(maxVideoHeight);
      dest.writeInt(maxVideoFrameRate);
      dest.writeInt(maxVideoBitrate);
      Util.writeBoolean(dest, exceedVideoConstraintsIfNecessary);
      Util.writeBoolean(dest, allowVideoMixedMimeTypeAdaptiveness);
      Util.writeBoolean(dest, allowVideoNonSeamlessAdaptiveness);
      dest.writeInt(viewportWidth);
      dest.writeInt(viewportHeight);
      Util.writeBoolean(dest, viewportOrientationMayChange);
      // Audio
      dest.writeInt(maxAudioChannelCount);
      dest.writeInt(maxAudioBitrate);
      Util.writeBoolean(dest, exceedAudioConstraintsIfNecessary);
      Util.writeBoolean(dest, allowAudioMixedMimeTypeAdaptiveness);
      Util.writeBoolean(dest, allowAudioMixedSampleRateAdaptiveness);
      Util.writeBoolean(dest, allowAudioMixedChannelCountAdaptiveness);
      // General
      Util.writeBoolean(dest, forceLowestBitrate);
      Util.writeBoolean(dest, forceHighestSupportedBitrate);
      Util.writeBoolean(dest, exceedRendererCapabilitiesIfNecessary);
      dest.writeInt(tunnelingAudioSessionId);
      // Overrides
      writeSelectionOverridesToParcel(dest, selectionOverrides);
      dest.writeSparseBooleanArray(rendererDisabledFlags);
    }

    public static final Parcelable.Creator<Parameters> CREATOR =
        new Parcelable.Creator<Parameters>() {

          @Override
          public Parameters createFromParcel(Parcel in) {
            return new Parameters(in);
          }

          @Override
          public Parameters[] newArray(int size) {
            return new Parameters[size];
          }
        };

    // Static utility methods.

    private static SparseArray<Map<TrackGroupArray, SelectionOverride>> readSelectionOverrides(
        Parcel in) {
      int renderersWithOverridesCount = in.readInt();
      SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides =
          new SparseArray<>(renderersWithOverridesCount);
      for (int i = 0; i < renderersWithOverridesCount; i++) {
        int rendererIndex = in.readInt();
        int overrideCount = in.readInt();
        Map<TrackGroupArray, SelectionOverride> overrides = new HashMap<>(overrideCount);
        for (int j = 0; j < overrideCount; j++) {
          TrackGroupArray trackGroups = in.readParcelable(TrackGroupArray.class.getClassLoader());
          SelectionOverride override = in.readParcelable(SelectionOverride.class.getClassLoader());
          overrides.put(trackGroups, override);
        }
        selectionOverrides.put(rendererIndex, overrides);
      }
      return selectionOverrides;
    }

    private static void writeSelectionOverridesToParcel(
        Parcel dest, SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides) {
      int renderersWithOverridesCount = selectionOverrides.size();
      dest.writeInt(renderersWithOverridesCount);
      for (int i = 0; i < renderersWithOverridesCount; i++) {
        int rendererIndex = selectionOverrides.keyAt(i);
        Map<TrackGroupArray, SelectionOverride> overrides = selectionOverrides.valueAt(i);
        int overrideCount = overrides.size();
        dest.writeInt(rendererIndex);
        dest.writeInt(overrideCount);
        for (Map.Entry<TrackGroupArray, SelectionOverride> override : overrides.entrySet()) {
          dest.writeParcelable(override.getKey(), /* parcelableFlags= */ 0);
          dest.writeParcelable(override.getValue(), /* parcelableFlags= */ 0);
        }
      }
    }

    private static boolean areRendererDisabledFlagsEqual(
        SparseBooleanArray first, SparseBooleanArray second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      // Only true values are put into rendererDisabledFlags, so we don't need to compare values.
      for (int indexInFirst = 0; indexInFirst < firstSize; indexInFirst++) {
        if (second.indexOfKey(first.keyAt(indexInFirst)) < 0) {
          return false;
        }
      }
      return true;
    }

    private static boolean areSelectionOverridesEqual(
        SparseArray<Map<TrackGroupArray, SelectionOverride>> first,
        SparseArray<Map<TrackGroupArray, SelectionOverride>> second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      for (int indexInFirst = 0; indexInFirst < firstSize; indexInFirst++) {
        int indexInSecond = second.indexOfKey(first.keyAt(indexInFirst));
        if (indexInSecond < 0
            || !areSelectionOverridesEqual(
                first.valueAt(indexInFirst), second.valueAt(indexInSecond))) {
          return false;
        }
      }
      return true;
    }

    private static boolean areSelectionOverridesEqual(
        Map<TrackGroupArray, SelectionOverride> first,
        Map<TrackGroupArray, SelectionOverride> second) {
      int firstSize = first.size();
      if (second.size() != firstSize) {
        return false;
      }
      for (Map.Entry<TrackGroupArray, SelectionOverride> firstEntry : first.entrySet()) {
        TrackGroupArray key = firstEntry.getKey();
        if (!second.containsKey(key) || !Util.areEqual(firstEntry.getValue(), second.get(key))) {
          return false;
        }
      }
      return true;
    }
  }

  /** A track selection override. */
  public static final class SelectionOverride implements Parcelable {

    public final int groupIndex;
    public final int[] tracks;
    public final int length;
    public final int reason;
    public final int data;

    /**
     * @param groupIndex The overriding track group index.
     * @param tracks The overriding track indices within the track group.
     */
    public SelectionOverride(int groupIndex, int... tracks) {
      this(groupIndex, tracks, C.SELECTION_REASON_MANUAL, /* data= */ 0);
    }

    /**
     * @param groupIndex The overriding track group index.
     * @param tracks The overriding track indices within the track group.
     * @param reason The reason for the override. One of the {@link C} SELECTION_REASON_ constants.
     * @param data Optional data associated with this override.
     */
    public SelectionOverride(int groupIndex, int[] tracks, int reason, int data) {
      this.groupIndex = groupIndex;
      this.tracks = Arrays.copyOf(tracks, tracks.length);
      this.length = tracks.length;
      this.reason = reason;
      this.data = data;
      Arrays.sort(this.tracks);
    }

    /* package */ SelectionOverride(Parcel in) {
      groupIndex = in.readInt();
      length = in.readByte();
      tracks = new int[length];
      in.readIntArray(tracks);
      reason = in.readInt();
      data = in.readInt();
    }

    /** Returns whether this override contains the specified track index. */
    public boolean containsTrack(int track) {
      for (int overrideTrack : tracks) {
        if (overrideTrack == track) {
          return true;
        }
      }
      return false;
    }

    @Override
    public int hashCode() {
      int hash = 31 * groupIndex + Arrays.hashCode(tracks);
      hash = 31 * hash + reason;
      return 31 * hash + data;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      SelectionOverride other = (SelectionOverride) obj;
      return groupIndex == other.groupIndex
          && Arrays.equals(tracks, other.tracks)
          && reason == other.reason
          && data == other.data;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeInt(groupIndex);
      dest.writeInt(tracks.length);
      dest.writeIntArray(tracks);
      dest.writeInt(reason);
      dest.writeInt(data);
    }

    public static final Parcelable.Creator<SelectionOverride> CREATOR =
        new Parcelable.Creator<SelectionOverride>() {

          @Override
          public SelectionOverride createFromParcel(Parcel in) {
            return new SelectionOverride(in);
          }

          @Override
          public SelectionOverride[] newArray(int size) {
            return new SelectionOverride[size];
          }
        };
  }

  /**
   * If a dimension (i.e. width or height) of a video is greater or equal to this fraction of the
   * corresponding viewport dimension, then the video is considered as filling the viewport (in that
   * dimension).
   */
  private static final float FRACTION_TO_CONSIDER_FULLSCREEN = 0.98f;
  private static final int[] NO_TRACKS = new int[0];
  private static final int WITHIN_RENDERER_CAPABILITIES_BONUS = 1000;

  private final TrackSelection.Factory trackSelectionFactory;
  private final AtomicReference<Parameters> parametersReference;

  private boolean allowMultipleAdaptiveSelections;

  public DefaultTrackSelector() {
    this(new AdaptiveTrackSelection.Factory());
  }

  /**
   * @deprecated Use {@link #DefaultTrackSelector()} instead. Custom bandwidth meter should be
   *     directly passed to the player in {@link ExoPlayerFactory}.
   */
  @Deprecated
  @SuppressWarnings("deprecation")
  public DefaultTrackSelector(BandwidthMeter bandwidthMeter) {
    this(new AdaptiveTrackSelection.Factory(bandwidthMeter));
  }

  /** @param trackSelectionFactory A factory for {@link TrackSelection}s. */
  public DefaultTrackSelector(TrackSelection.Factory trackSelectionFactory) {
    this.trackSelectionFactory = trackSelectionFactory;
    parametersReference = new AtomicReference<>(Parameters.DEFAULT);
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param parameters The parameters for track selection.
   */
  public void setParameters(Parameters parameters) {
    Assertions.checkNotNull(parameters);
    if (!parametersReference.getAndSet(parameters).equals(parameters)) {
      invalidate();
    }
  }

  /**
   * Atomically sets the provided parameters for track selection.
   *
   * @param parametersBuilder A builder from which to obtain the parameters for track selection.
   */
  public void setParameters(ParametersBuilder parametersBuilder) {
    setParameters(parametersBuilder.build());
  }

  /**
   * Gets the current selection parameters.
   *
   * @return The current selection parameters.
   */
  public Parameters getParameters() {
    return parametersReference.get();
  }

  /** Returns a new {@link ParametersBuilder} initialized with the current selection parameters. */
  public ParametersBuilder buildUponParameters() {
    return getParameters().buildUpon();
  }

  /** @deprecated Use {@link ParametersBuilder#setRendererDisabled(int, boolean)}. */
  @Deprecated
  public final void setRendererDisabled(int rendererIndex, boolean disabled) {
    setParameters(buildUponParameters().setRendererDisabled(rendererIndex, disabled));
  }

  /** @deprecated Use {@link Parameters#getRendererDisabled(int)}. * */
  @Deprecated
  public final boolean getRendererDisabled(int rendererIndex) {
    return getParameters().getRendererDisabled(rendererIndex);
  }

  /**
   * @deprecated Use {@link ParametersBuilder#setSelectionOverride(int, TrackGroupArray,
   *     SelectionOverride)}.
   */
  @Deprecated
  public final void setSelectionOverride(
      int rendererIndex, TrackGroupArray groups, SelectionOverride override) {
    setParameters(buildUponParameters().setSelectionOverride(rendererIndex, groups, override));
  }

  /** @deprecated Use {@link Parameters#hasSelectionOverride(int, TrackGroupArray)}. * */
  @Deprecated
  public final boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    return getParameters().hasSelectionOverride(rendererIndex, groups);
  }

  /** @deprecated Use {@link Parameters#getSelectionOverride(int, TrackGroupArray)}. */
  @Deprecated
  @Nullable
  public final SelectionOverride getSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    return getParameters().getSelectionOverride(rendererIndex, groups);
  }

  /** @deprecated Use {@link ParametersBuilder#clearSelectionOverride(int, TrackGroupArray)}. */
  @Deprecated
  public final void clearSelectionOverride(int rendererIndex, TrackGroupArray groups) {
    setParameters(buildUponParameters().clearSelectionOverride(rendererIndex, groups));
  }

  /** @deprecated Use {@link ParametersBuilder#clearSelectionOverrides(int)}. */
  @Deprecated
  public final void clearSelectionOverrides(int rendererIndex) {
    setParameters(buildUponParameters().clearSelectionOverrides(rendererIndex));
  }

  /** @deprecated Use {@link ParametersBuilder#clearSelectionOverrides()}. */
  @Deprecated
  public final void clearSelectionOverrides() {
    setParameters(buildUponParameters().clearSelectionOverrides());
  }

  /** @deprecated Use {@link ParametersBuilder#setTunnelingAudioSessionId(int)}. */
  @Deprecated
  public void setTunnelingAudioSessionId(int tunnelingAudioSessionId) {
    setParameters(buildUponParameters().setTunnelingAudioSessionId(tunnelingAudioSessionId));
  }

  /**
   * Allows the creation of multiple adaptive track selections.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   */
  public void experimental_allowMultipleAdaptiveSelections() {
    this.allowMultipleAdaptiveSelections = true;
  }

  // MappingTrackSelector implementation.

  @Override
  protected final Pair<@NullableType RendererConfiguration[], @NullableType TrackSelection[]>
      selectTracks(
          MappedTrackInfo mappedTrackInfo,
          int[][][] rendererFormatSupports,
          int[] rendererMixedMimeTypeAdaptationSupports)
          throws ExoPlaybackException {
    Parameters params = parametersReference.get();
    int rendererCount = mappedTrackInfo.getRendererCount();
    TrackSelection.@NullableType Definition[] definitions =
        selectAllTracks(
            mappedTrackInfo,
            rendererFormatSupports,
            rendererMixedMimeTypeAdaptationSupports,
            params);

    // Apply track disabling and overriding.
    for (int i = 0; i < rendererCount; i++) {
      if (params.getRendererDisabled(i)) {
        definitions[i] = null;
        continue;
      }
      TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(i);
      if (params.hasSelectionOverride(i, rendererTrackGroups)) {
        SelectionOverride override = params.getSelectionOverride(i, rendererTrackGroups);
        definitions[i] =
            override == null
                ? null
                : new TrackSelection.Definition(
                    rendererTrackGroups.get(override.groupIndex),
                    override.tracks,
                    override.reason,
                    override.data);
      }
    }

    @NullableType
    TrackSelection[] rendererTrackSelections =
        trackSelectionFactory.createTrackSelections(definitions, getBandwidthMeter());

    // Initialize the renderer configurations to the default configuration for all renderers with
    // selections, and null otherwise.
    @NullableType RendererConfiguration[] rendererConfigurations =
        new RendererConfiguration[rendererCount];
    for (int i = 0; i < rendererCount; i++) {
      boolean forceRendererDisabled = params.getRendererDisabled(i);
      boolean rendererEnabled =
          !forceRendererDisabled
              && (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_NONE
                  || rendererTrackSelections[i] != null);
      rendererConfigurations[i] = rendererEnabled ? RendererConfiguration.DEFAULT : null;
    }

    // Configure audio and video renderers to use tunneling if appropriate.
    maybeConfigureRenderersForTunneling(
        mappedTrackInfo,
        rendererFormatSupports,
        rendererConfigurations,
        rendererTrackSelections,
        params.tunnelingAudioSessionId);

    return Pair.create(rendererConfigurations, rendererTrackSelections);
  }

  // Track selection prior to overrides and disabled flags being applied.

  /**
   * Called from {@link #selectTracks(MappedTrackInfo, int[][][], int[])} to make a track selection
   * for each renderer, prior to overrides and disabled flags being applied.
   *
   * <p>The implementation should not account for overrides and disabled flags. Track selections
   * generated by this method will be overridden to account for these properties.
   *
   * @param mappedTrackInfo Mapped track information.
   * @param rendererFormatSupports The result of {@link RendererCapabilities#supportsFormat} for
   *     each mapped track, indexed by renderer, track group and track (in that order).
   * @param rendererMixedMimeTypeAdaptationSupports The result of {@link
   *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
   * @return The {@link TrackSelection.Definition}s for the renderers. A null entry indicates no
   *     selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected TrackSelection.@NullableType Definition[] selectAllTracks(
      MappedTrackInfo mappedTrackInfo,
      int[][][] rendererFormatSupports,
      int[] rendererMixedMimeTypeAdaptationSupports,
      Parameters params)
      throws ExoPlaybackException {
    int rendererCount = mappedTrackInfo.getRendererCount();
    TrackSelection.@NullableType Definition[] definitions =
        new TrackSelection.Definition[rendererCount];

    boolean seenVideoRendererWithMappedTracks = false;
    boolean selectedVideoTracks = false;
    for (int i = 0; i < rendererCount; i++) {
      if (C.TRACK_TYPE_VIDEO == mappedTrackInfo.getRendererType(i)) {
        if (!selectedVideoTracks) {
          definitions[i] =
              selectVideoTrack(
                  mappedTrackInfo.getTrackGroups(i),
                  rendererFormatSupports[i],
                  rendererMixedMimeTypeAdaptationSupports[i],
                  params,
                  /* enableAdaptiveTrackSelection= */ true);
          selectedVideoTracks = definitions[i] != null;
        }
        seenVideoRendererWithMappedTracks |= mappedTrackInfo.getTrackGroups(i).length > 0;
      }
    }

    AudioTrackScore selectedAudioTrackScore = null;
    String selectedAudioLanguage = null;
    int selectedAudioRendererIndex = C.INDEX_UNSET;
    for (int i = 0; i < rendererCount; i++) {
      if (C.TRACK_TYPE_AUDIO == mappedTrackInfo.getRendererType(i)) {
        boolean enableAdaptiveTrackSelection =
            allowMultipleAdaptiveSelections || !seenVideoRendererWithMappedTracks;
        Pair<TrackSelection.Definition, AudioTrackScore> audioSelection =
            selectAudioTrack(
                mappedTrackInfo.getTrackGroups(i),
                rendererFormatSupports[i],
                rendererMixedMimeTypeAdaptationSupports[i],
                params,
                enableAdaptiveTrackSelection);
        if (audioSelection != null
            && (selectedAudioTrackScore == null
                || audioSelection.second.compareTo(selectedAudioTrackScore) > 0)) {
          if (selectedAudioRendererIndex != C.INDEX_UNSET) {
            // We've already made a selection for another audio renderer, but it had a lower
            // score. Clear the selection for that renderer.
            definitions[selectedAudioRendererIndex] = null;
          }
          TrackSelection.Definition definition = audioSelection.first;
          definitions[i] = definition;
          // We assume that audio tracks in the same group have matching language.
          selectedAudioLanguage = definition.group.getFormat(definition.tracks[0]).language;
          selectedAudioTrackScore = audioSelection.second;
          selectedAudioRendererIndex = i;
        }
      }
    }

    TextTrackScore selectedTextTrackScore = null;
    int selectedTextRendererIndex = C.INDEX_UNSET;
    for (int i = 0; i < rendererCount; i++) {
      int trackType = mappedTrackInfo.getRendererType(i);
      switch (trackType) {
        case C.TRACK_TYPE_VIDEO:
        case C.TRACK_TYPE_AUDIO:
          // Already done. Do nothing.
          break;
        case C.TRACK_TYPE_TEXT:
          Pair<TrackSelection.Definition, TextTrackScore> textSelection =
              selectTextTrack(
                  mappedTrackInfo.getTrackGroups(i),
                  rendererFormatSupports[i],
                  params,
                  selectedAudioLanguage);
          if (textSelection != null
              && (selectedTextTrackScore == null
                  || textSelection.second.compareTo(selectedTextTrackScore) > 0)) {
            if (selectedTextRendererIndex != C.INDEX_UNSET) {
              // We've already made a selection for another text renderer, but it had a lower score.
              // Clear the selection for that renderer.
              definitions[selectedTextRendererIndex] = null;
            }
            definitions[i] = textSelection.first;
            selectedTextTrackScore = textSelection.second;
            selectedTextRendererIndex = i;
          }
          break;
        default:
          definitions[i] =
              selectOtherTrack(
                  trackType, mappedTrackInfo.getTrackGroups(i), rendererFormatSupports[i], params);
          break;
      }
    }

    return definitions;
  }

  // Video track selection implementation.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link TrackSelection} for a video renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupports The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param mixedMimeTypeAdaptationSupports The result of {@link
   *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for the renderer.
   * @param params The selector's current constraint parameters.
   * @param enableAdaptiveTrackSelection Whether adaptive track selection is allowed.
   * @return The {@link TrackSelection.Definition} for the renderer, or null if no selection was
   *     made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  @Nullable
  protected TrackSelection.Definition selectVideoTrack(
      TrackGroupArray groups,
      int[][] formatSupports,
      int mixedMimeTypeAdaptationSupports,
      Parameters params,
      boolean enableAdaptiveTrackSelection)
      throws ExoPlaybackException {
    TrackSelection.Definition definition = null;
    if (!params.forceHighestSupportedBitrate
        && !params.forceLowestBitrate
        && enableAdaptiveTrackSelection) {
      definition =
          selectAdaptiveVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params);
    }
    if (definition == null) {
      definition = selectFixedVideoTrack(groups, formatSupports, params);
    }
    return definition;
  }

  @Nullable
  private static TrackSelection.Definition selectAdaptiveVideoTrack(
      TrackGroupArray groups,
      int[][] formatSupport,
      int mixedMimeTypeAdaptationSupports,
      Parameters params) {
    int requiredAdaptiveSupport =
        params.allowVideoNonSeamlessAdaptiveness
            ? (RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.ADAPTIVE_SEAMLESS)
            : RendererCapabilities.ADAPTIVE_SEAMLESS;
    boolean allowMixedMimeTypes =
        params.allowVideoMixedMimeTypeAdaptiveness
            && (mixedMimeTypeAdaptationSupports & requiredAdaptiveSupport) != 0;
    for (int i = 0; i < groups.length; i++) {
      TrackGroup group = groups.get(i);
      int[] adaptiveTracks =
          getAdaptiveVideoTracksForGroup(
              group,
              formatSupport[i],
              allowMixedMimeTypes,
              requiredAdaptiveSupport,
              params.maxVideoWidth,
              params.maxVideoHeight,
              params.maxVideoFrameRate,
              params.maxVideoBitrate,
              params.viewportWidth,
              params.viewportHeight,
              params.viewportOrientationMayChange);
      if (adaptiveTracks.length > 0) {
        return new TrackSelection.Definition(group, adaptiveTracks);
      }
    }
    return null;
  }

  private static int[] getAdaptiveVideoTracksForGroup(
      TrackGroup group,
      int[] formatSupport,
      boolean allowMixedMimeTypes,
      int requiredAdaptiveSupport,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoFrameRate,
      int maxVideoBitrate,
      int viewportWidth,
      int viewportHeight,
      boolean viewportOrientationMayChange) {
    if (group.length < 2) {
      return NO_TRACKS;
    }

    List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(group, viewportWidth,
        viewportHeight, viewportOrientationMayChange);
    if (selectedTrackIndices.size() < 2) {
      return NO_TRACKS;
    }

    String selectedMimeType = null;
    if (!allowMixedMimeTypes) {
      // Select the mime type for which we have the most adaptive tracks.
      HashSet<@NullableType String> seenMimeTypes = new HashSet<>();
      int selectedMimeTypeTrackCount = 0;
      for (int i = 0; i < selectedTrackIndices.size(); i++) {
        int trackIndex = selectedTrackIndices.get(i);
        String sampleMimeType = group.getFormat(trackIndex).sampleMimeType;
        if (seenMimeTypes.add(sampleMimeType)) {
          int countForMimeType =
              getAdaptiveVideoTrackCountForMimeType(
                  group,
                  formatSupport,
                  requiredAdaptiveSupport,
                  sampleMimeType,
                  maxVideoWidth,
                  maxVideoHeight,
                  maxVideoFrameRate,
                  maxVideoBitrate,
                  selectedTrackIndices);
          if (countForMimeType > selectedMimeTypeTrackCount) {
            selectedMimeType = sampleMimeType;
            selectedMimeTypeTrackCount = countForMimeType;
          }
        }
      }
    }

    // Filter by the selected mime type.
    filterAdaptiveVideoTrackCountForMimeType(
        group,
        formatSupport,
        requiredAdaptiveSupport,
        selectedMimeType,
        maxVideoWidth,
        maxVideoHeight,
        maxVideoFrameRate,
        maxVideoBitrate,
        selectedTrackIndices);

    return selectedTrackIndices.size() < 2 ? NO_TRACKS : Util.toArray(selectedTrackIndices);
  }

  private static int getAdaptiveVideoTrackCountForMimeType(
      TrackGroup group,
      int[] formatSupport,
      int requiredAdaptiveSupport,
      @Nullable String mimeType,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoFrameRate,
      int maxVideoBitrate,
      List<Integer> selectedTrackIndices) {
    int adaptiveTrackCount = 0;
    for (int i = 0; i < selectedTrackIndices.size(); i++) {
      int trackIndex = selectedTrackIndices.get(i);
      if (isSupportedAdaptiveVideoTrack(
          group.getFormat(trackIndex),
          mimeType,
          formatSupport[trackIndex],
          requiredAdaptiveSupport,
          maxVideoWidth,
          maxVideoHeight,
          maxVideoFrameRate,
          maxVideoBitrate)) {
        adaptiveTrackCount++;
      }
    }
    return adaptiveTrackCount;
  }

  private static void filterAdaptiveVideoTrackCountForMimeType(
      TrackGroup group,
      int[] formatSupport,
      int requiredAdaptiveSupport,
      @Nullable String mimeType,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoFrameRate,
      int maxVideoBitrate,
      List<Integer> selectedTrackIndices) {
    for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
      int trackIndex = selectedTrackIndices.get(i);
      if (!isSupportedAdaptiveVideoTrack(
          group.getFormat(trackIndex),
          mimeType,
          formatSupport[trackIndex],
          requiredAdaptiveSupport,
          maxVideoWidth,
          maxVideoHeight,
          maxVideoFrameRate,
          maxVideoBitrate)) {
        selectedTrackIndices.remove(i);
      }
    }
  }

  private static boolean isSupportedAdaptiveVideoTrack(
      Format format,
      @Nullable String mimeType,
      int formatSupport,
      int requiredAdaptiveSupport,
      int maxVideoWidth,
      int maxVideoHeight,
      int maxVideoFrameRate,
      int maxVideoBitrate) {
    return isSupported(formatSupport, false)
        && ((formatSupport & requiredAdaptiveSupport) != 0)
        && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType))
        && (format.width == Format.NO_VALUE || format.width <= maxVideoWidth)
        && (format.height == Format.NO_VALUE || format.height <= maxVideoHeight)
        && (format.frameRate == Format.NO_VALUE || format.frameRate <= maxVideoFrameRate)
        && (format.bitrate == Format.NO_VALUE || format.bitrate <= maxVideoBitrate);
  }

  @Nullable
  private static TrackSelection.Definition selectFixedVideoTrack(
      TrackGroupArray groups, int[][] formatSupports, Parameters params) {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    int selectedBitrate = Format.NO_VALUE;
    int selectedPixelCount = Format.NO_VALUE;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(trackGroup,
          params.viewportWidth, params.viewportHeight, params.viewportOrientationMayChange);
      int[] trackFormatSupport = formatSupports[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isWithinConstraints =
              selectedTrackIndices.contains(trackIndex)
                  && (format.width == Format.NO_VALUE || format.width <= params.maxVideoWidth)
                  && (format.height == Format.NO_VALUE || format.height <= params.maxVideoHeight)
                  && (format.frameRate == Format.NO_VALUE
                      || format.frameRate <= params.maxVideoFrameRate)
                  && (format.bitrate == Format.NO_VALUE
                      || format.bitrate <= params.maxVideoBitrate);
          if (!isWithinConstraints && !params.exceedVideoConstraintsIfNecessary) {
            // Track should not be selected.
            continue;
          }
          int trackScore = isWithinConstraints ? 2 : 1;
          boolean isWithinCapabilities = isSupported(trackFormatSupport[trackIndex], false);
          if (isWithinCapabilities) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
          }
          boolean selectTrack = trackScore > selectedTrackScore;
          if (trackScore == selectedTrackScore) {
            int bitrateComparison = compareFormatValues(format.bitrate, selectedBitrate);
            if (params.forceLowestBitrate && bitrateComparison != 0) {
              // Use bitrate as a tie breaker, preferring the lower bitrate.
              selectTrack = bitrateComparison < 0;
            } else {
              // Use the pixel count as a tie breaker (or bitrate if pixel counts are tied). If
              // we're within constraints prefer a higher pixel count (or bitrate), else prefer a
              // lower count (or bitrate). If still tied then prefer the first track (i.e. the one
              // that's already selected).
              int formatPixelCount = format.getPixelCount();
              int comparisonResult = formatPixelCount != selectedPixelCount
                  ? compareFormatValues(formatPixelCount, selectedPixelCount)
                  : compareFormatValues(format.bitrate, selectedBitrate);
              selectTrack = isWithinCapabilities && isWithinConstraints
                  ? comparisonResult > 0 : comparisonResult < 0;
            }
          }
          if (selectTrack) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
            selectedBitrate = format.bitrate;
            selectedPixelCount = format.getPixelCount();
          }
        }
      }
    }
    return selectedGroup == null
        ? null
        : new TrackSelection.Definition(selectedGroup, selectedTrackIndex);
  }

  // Audio track selection implementation.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link TrackSelection} for an audio renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupports The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param mixedMimeTypeAdaptationSupports The result of {@link
   *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for the renderer.
   * @param params The selector's current constraint parameters.
   * @param enableAdaptiveTrackSelection Whether adaptive track selection is allowed.
   * @return The {@link TrackSelection.Definition} and corresponding {@link AudioTrackScore}, or
   *     null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  @SuppressWarnings("unused")
  @Nullable
  protected Pair<TrackSelection.Definition, AudioTrackScore> selectAudioTrack(
      TrackGroupArray groups,
      int[][] formatSupports,
      int mixedMimeTypeAdaptationSupports,
      Parameters params,
      boolean enableAdaptiveTrackSelection)
      throws ExoPlaybackException {
    int selectedTrackIndex = C.INDEX_UNSET;
    int selectedGroupIndex = C.INDEX_UNSET;
    AudioTrackScore selectedTrackScore = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupports[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          AudioTrackScore trackScore =
              new AudioTrackScore(format, params, trackFormatSupport[trackIndex]);
          if (!trackScore.isWithinConstraints && !params.exceedAudioConstraintsIfNecessary) {
            // Track should not be selected.
            continue;
          }
          if (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0) {
            selectedGroupIndex = groupIndex;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }

    if (selectedGroupIndex == C.INDEX_UNSET) {
      return null;
    }

    TrackGroup selectedGroup = groups.get(selectedGroupIndex);

    TrackSelection.Definition definition = null;
    if (!params.forceHighestSupportedBitrate
        && !params.forceLowestBitrate
        && enableAdaptiveTrackSelection) {
      // If the group of the track with the highest score allows it, try to enable adaptation.
      int[] adaptiveTracks =
          getAdaptiveAudioTracks(
              selectedGroup,
              formatSupports[selectedGroupIndex],
              params.maxAudioBitrate,
              params.allowAudioMixedMimeTypeAdaptiveness,
              params.allowAudioMixedSampleRateAdaptiveness,
              params.allowAudioMixedChannelCountAdaptiveness);
      if (adaptiveTracks.length > 0) {
        definition = new TrackSelection.Definition(selectedGroup, adaptiveTracks);
      }
    }
    if (definition == null) {
      // We didn't make an adaptive selection, so make a fixed one instead.
      definition = new TrackSelection.Definition(selectedGroup, selectedTrackIndex);
    }

    return Pair.create(definition, Assertions.checkNotNull(selectedTrackScore));
  }

  private static int[] getAdaptiveAudioTracks(
      TrackGroup group,
      int[] formatSupport,
      int maxAudioBitrate,
      boolean allowMixedMimeTypeAdaptiveness,
      boolean allowMixedSampleRateAdaptiveness,
      boolean allowAudioMixedChannelCountAdaptiveness) {
    int selectedConfigurationTrackCount = 0;
    AudioConfigurationTuple selectedConfiguration = null;
    HashSet<AudioConfigurationTuple> seenConfigurationTuples = new HashSet<>();
    for (int i = 0; i < group.length; i++) {
      Format format = group.getFormat(i);
      AudioConfigurationTuple configuration =
          new AudioConfigurationTuple(
              format.channelCount, format.sampleRate, format.sampleMimeType);
      if (seenConfigurationTuples.add(configuration)) {
        int configurationCount =
            getAdaptiveAudioTrackCount(
                group,
                formatSupport,
                configuration,
                maxAudioBitrate,
                allowMixedMimeTypeAdaptiveness,
                allowMixedSampleRateAdaptiveness,
                allowAudioMixedChannelCountAdaptiveness);
        if (configurationCount > selectedConfigurationTrackCount) {
          selectedConfiguration = configuration;
          selectedConfigurationTrackCount = configurationCount;
        }
      }
    }

    if (selectedConfigurationTrackCount > 1) {
      Assertions.checkNotNull(selectedConfiguration);
      int[] adaptiveIndices = new int[selectedConfigurationTrackCount];
      int index = 0;
      for (int i = 0; i < group.length; i++) {
        Format format = group.getFormat(i);
        if (isSupportedAdaptiveAudioTrack(
            format,
            formatSupport[i],
            selectedConfiguration,
            maxAudioBitrate,
            allowMixedMimeTypeAdaptiveness,
            allowMixedSampleRateAdaptiveness,
            allowAudioMixedChannelCountAdaptiveness)) {
          adaptiveIndices[index++] = i;
        }
      }
      return adaptiveIndices;
    }
    return NO_TRACKS;
  }

  private static int getAdaptiveAudioTrackCount(
      TrackGroup group,
      int[] formatSupport,
      AudioConfigurationTuple configuration,
      int maxAudioBitrate,
      boolean allowMixedMimeTypeAdaptiveness,
      boolean allowMixedSampleRateAdaptiveness,
      boolean allowAudioMixedChannelCountAdaptiveness) {
    int count = 0;
    for (int i = 0; i < group.length; i++) {
      if (isSupportedAdaptiveAudioTrack(
          group.getFormat(i),
          formatSupport[i],
          configuration,
          maxAudioBitrate,
          allowMixedMimeTypeAdaptiveness,
          allowMixedSampleRateAdaptiveness,
          allowAudioMixedChannelCountAdaptiveness)) {
        count++;
      }
    }
    return count;
  }

  private static boolean isSupportedAdaptiveAudioTrack(
      Format format,
      int formatSupport,
      AudioConfigurationTuple configuration,
      int maxAudioBitrate,
      boolean allowMixedMimeTypeAdaptiveness,
      boolean allowMixedSampleRateAdaptiveness,
      boolean allowAudioMixedChannelCountAdaptiveness) {
    return isSupported(formatSupport, false)
        && (format.bitrate == Format.NO_VALUE || format.bitrate <= maxAudioBitrate)
        && (allowAudioMixedChannelCountAdaptiveness
            || (format.channelCount != Format.NO_VALUE
                && format.channelCount == configuration.channelCount))
        && (allowMixedMimeTypeAdaptiveness
            || (format.sampleMimeType != null
                && TextUtils.equals(format.sampleMimeType, configuration.mimeType)))
        && (allowMixedSampleRateAdaptiveness
            || (format.sampleRate != Format.NO_VALUE
                && format.sampleRate == configuration.sampleRate));
  }

  // Text track selection implementation.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link TrackSelection} for a text renderer.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param params The selector's current constraint parameters.
   * @param selectedAudioLanguage The language of the selected audio track. May be null if the
   *     selected text track declares no language or no text track was selected.
   * @return The {@link TrackSelection.Definition} and corresponding {@link TextTrackScore}, or null
   *     if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  @Nullable
  protected Pair<TrackSelection.Definition, TextTrackScore> selectTextTrack(
      TrackGroupArray groups,
      int[][] formatSupport,
      Parameters params,
      @Nullable String selectedAudioLanguage)
      throws ExoPlaybackException {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = C.INDEX_UNSET;
    TextTrackScore selectedTrackScore = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          TextTrackScore trackScore =
              new TextTrackScore(
                  format, params, trackFormatSupport[trackIndex], selectedAudioLanguage);
          if (trackScore.isWithinConstraints
              && (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0)) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null
        ? null
        : Pair.create(
            new TrackSelection.Definition(selectedGroup, selectedTrackIndex),
            Assertions.checkNotNull(selectedTrackScore));
  }

  // General track selection methods.

  /**
   * Called by {@link #selectAllTracks(MappedTrackInfo, int[][][], int[], Parameters)} to create a
   * {@link TrackSelection} for a renderer whose type is neither video, audio or text.
   *
   * @param trackType The type of the renderer.
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The result of {@link RendererCapabilities#supportsFormat} for each mapped
   *     track, indexed by track group index and track index (in that order).
   * @param params The selector's current constraint parameters.
   * @return The {@link TrackSelection} for the renderer, or null if no selection was made.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  @Nullable
  protected TrackSelection.Definition selectOtherTrack(
      int trackType, TrackGroupArray groups, int[][] formatSupport, Parameters params)
      throws ExoPlaybackException {
    TrackGroup selectedGroup = null;
    int selectedTrackIndex = 0;
    int selectedTrackScore = 0;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      int[] trackFormatSupport = formatSupport[groupIndex];
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        if (isSupported(trackFormatSupport[trackIndex],
            params.exceedRendererCapabilitiesIfNecessary)) {
          Format format = trackGroup.getFormat(trackIndex);
          boolean isDefault = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
          int trackScore = isDefault ? 2 : 1;
          if (isSupported(trackFormatSupport[trackIndex], false)) {
            trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
          }
          if (trackScore > selectedTrackScore) {
            selectedGroup = trackGroup;
            selectedTrackIndex = trackIndex;
            selectedTrackScore = trackScore;
          }
        }
      }
    }
    return selectedGroup == null
        ? null
        : new TrackSelection.Definition(selectedGroup, selectedTrackIndex);
  }

  // Utility methods.

  /**
   * Determines whether tunneling should be enabled, replacing {@link RendererConfiguration}s in
   * {@code rendererConfigurations} with configurations that enable tunneling on the appropriate
   * renderers if so.
   *
   * @param mappedTrackInfo Mapped track information.
   * @param rendererConfigurations The renderer configurations. Configurations may be replaced with
   *     ones that enable tunneling as a result of this call.
   * @param trackSelections The renderer track selections.
   * @param tunnelingAudioSessionId The audio session id to use when tunneling, or {@link
   *     C#AUDIO_SESSION_ID_UNSET} if tunneling should not be enabled.
   */
  private static void maybeConfigureRenderersForTunneling(
      MappedTrackInfo mappedTrackInfo,
      int[][][] renderererFormatSupports,
      @NullableType RendererConfiguration[] rendererConfigurations,
      @NullableType TrackSelection[] trackSelections,
      int tunnelingAudioSessionId) {
    if (tunnelingAudioSessionId == C.AUDIO_SESSION_ID_UNSET) {
      return;
    }
    // Check whether we can enable tunneling. To enable tunneling we require exactly one audio and
    // one video renderer to support tunneling and have a selection.
    int tunnelingAudioRendererIndex = -1;
    int tunnelingVideoRendererIndex = -1;
    boolean enableTunneling = true;
    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
      int rendererType = mappedTrackInfo.getRendererType(i);
      TrackSelection trackSelection = trackSelections[i];
      if ((rendererType == C.TRACK_TYPE_AUDIO || rendererType == C.TRACK_TYPE_VIDEO)
          && trackSelection != null) {
        if (rendererSupportsTunneling(
            renderererFormatSupports[i], mappedTrackInfo.getTrackGroups(i), trackSelection)) {
          if (rendererType == C.TRACK_TYPE_AUDIO) {
            if (tunnelingAudioRendererIndex != -1) {
              enableTunneling = false;
              break;
            } else {
              tunnelingAudioRendererIndex = i;
            }
          } else {
            if (tunnelingVideoRendererIndex != -1) {
              enableTunneling = false;
              break;
            } else {
              tunnelingVideoRendererIndex = i;
            }
          }
        }
      }
    }
    enableTunneling &= tunnelingAudioRendererIndex != -1 && tunnelingVideoRendererIndex != -1;
    if (enableTunneling) {
      RendererConfiguration tunnelingRendererConfiguration =
          new RendererConfiguration(tunnelingAudioSessionId);
      rendererConfigurations[tunnelingAudioRendererIndex] = tunnelingRendererConfiguration;
      rendererConfigurations[tunnelingVideoRendererIndex] = tunnelingRendererConfiguration;
    }
  }

  /**
   * Returns whether a renderer supports tunneling for a {@link TrackSelection}.
   *
   * @param formatSupports The result of {@link RendererCapabilities#supportsFormat} for each track,
   *     indexed by group index and track index (in that order).
   * @param trackGroups The {@link TrackGroupArray}s for the renderer.
   * @param selection The track selection.
   * @return Whether the renderer supports tunneling for the {@link TrackSelection}.
   */
  private static boolean rendererSupportsTunneling(
      int[][] formatSupports, TrackGroupArray trackGroups, TrackSelection selection) {
    if (selection == null) {
      return false;
    }
    int trackGroupIndex = trackGroups.indexOf(selection.getTrackGroup());
    for (int i = 0; i < selection.length(); i++) {
      int trackFormatSupport = formatSupports[trackGroupIndex][selection.getIndexInTrackGroup(i)];
      if ((trackFormatSupport & RendererCapabilities.TUNNELING_SUPPORT_MASK)
          != RendererCapabilities.TUNNELING_SUPPORTED) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compares two format values for order. A known value is considered greater than {@link
   * Format#NO_VALUE}.
   *
   * @param first The first value.
   * @param second The second value.
   * @return A negative integer if the first value is less than the second. Zero if they are equal.
   *     A positive integer if the first value is greater than the second.
   */
  private static int compareFormatValues(int first, int second) {
    return first == Format.NO_VALUE
        ? (second == Format.NO_VALUE ? 0 : -1)
        : (second == Format.NO_VALUE ? 1 : (first - second));
  }

  /**
   * Applies the {@link RendererCapabilities#FORMAT_SUPPORT_MASK} to a value obtained from
   * {@link RendererCapabilities#supportsFormat(Format)}, returning true if the result is
   * {@link RendererCapabilities#FORMAT_HANDLED} or if {@code allowExceedsCapabilities} is set
   * and the result is {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   *
   * @param formatSupport A value obtained from {@link RendererCapabilities#supportsFormat(Format)}.
   * @param allowExceedsCapabilities Whether to return true if the format support component of the
   *     value is {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   * @return True if the format support component is {@link RendererCapabilities#FORMAT_HANDLED}, or
   *     if {@code allowExceedsCapabilities} is set and the format support component is
   *     {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}.
   */
  protected static boolean isSupported(int formatSupport, boolean allowExceedsCapabilities) {
    int maskedSupport = formatSupport & RendererCapabilities.FORMAT_SUPPORT_MASK;
    return maskedSupport == RendererCapabilities.FORMAT_HANDLED || (allowExceedsCapabilities
        && maskedSupport == RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES);
  }

  /**
   * Normalizes the input string to null if it does not define a language, or returns it otherwise.
   *
   * @param language The string.
   * @return The string, optionally normalized to null if it does not define a language.
   */
  @Nullable
  protected static String normalizeUndeterminedLanguageToNull(@Nullable String language) {
    return TextUtils.isEmpty(language) || TextUtils.equals(language, C.LANGUAGE_UNDETERMINED)
        ? null
        : language;
  }

  /**
   * Returns a score for how well a language specified in a {@link Format} matches a given language.
   *
   * @param format The {@link Format}.
   * @param language The language, or null.
   * @param allowUndeterminedFormatLanguage Whether matches with an empty or undetermined format
   *     language tag are allowed.
   * @return A score of 4 if the languages match fully, a score of 3 if the languages match partly,
   *     a score of 2 if the languages don't match but belong to the same main language, a score of
   *     1 if the format language is undetermined and such a match is allowed, and a score of 0 if
   *     the languages don't match at all.
   */
  protected static int getFormatLanguageScore(
      Format format, @Nullable String language, boolean allowUndeterminedFormatLanguage) {
    if (!TextUtils.isEmpty(language) && language.equals(format.language)) {
      // Full literal match of non-empty languages, including matches of an explicit "und" query.
      return 4;
    }
    language = normalizeUndeterminedLanguageToNull(language);
    String formatLanguage = normalizeUndeterminedLanguageToNull(format.language);
    if (formatLanguage == null || language == null) {
      // At least one of the languages is undetermined.
      return allowUndeterminedFormatLanguage && formatLanguage == null ? 1 : 0;
    }
    if (formatLanguage.startsWith(language) || language.startsWith(formatLanguage)) {
      // Partial match where one language is a subset of the other (e.g. "zh-hans" and "zh-hans-hk")
      return 3;
    }
    String formatMainLanguage = Util.splitAtFirst(formatLanguage, "-")[0];
    String queryMainLanguage = Util.splitAtFirst(language, "-")[0];
    if (formatMainLanguage.equals(queryMainLanguage)) {
      // Partial match where only the main language tag is the same (e.g. "fr-fr" and "fr-ca")
      return 2;
    }
    return 0;
  }

  private static List<Integer> getViewportFilteredTrackIndices(TrackGroup group, int viewportWidth,
      int viewportHeight, boolean orientationMayChange) {
    // Initially include all indices.
    ArrayList<Integer> selectedTrackIndices = new ArrayList<>(group.length);
    for (int i = 0; i < group.length; i++) {
      selectedTrackIndices.add(i);
    }

    if (viewportWidth == Integer.MAX_VALUE || viewportHeight == Integer.MAX_VALUE) {
      // Viewport dimensions not set. Return the full set of indices.
      return selectedTrackIndices;
    }

    int maxVideoPixelsToRetain = Integer.MAX_VALUE;
    for (int i = 0; i < group.length; i++) {
      Format format = group.getFormat(i);
      // Keep track of the number of pixels of the selected format whose resolution is the
      // smallest to exceed the maximum size at which it can be displayed within the viewport.
      // We'll discard formats of higher resolution.
      if (format.width > 0 && format.height > 0) {
        Point maxVideoSizeInViewport = getMaxVideoSizeInViewport(orientationMayChange,
            viewportWidth, viewportHeight, format.width, format.height);
        int videoPixels = format.width * format.height;
        if (format.width >= (int) (maxVideoSizeInViewport.x * FRACTION_TO_CONSIDER_FULLSCREEN)
            && format.height >= (int) (maxVideoSizeInViewport.y * FRACTION_TO_CONSIDER_FULLSCREEN)
            && videoPixels < maxVideoPixelsToRetain) {
          maxVideoPixelsToRetain = videoPixels;
        }
      }
    }

    // Filter out formats that exceed maxVideoPixelsToRetain. These formats have an unnecessarily
    // high resolution given the size at which the video will be displayed within the viewport. Also
    // filter out formats with unknown dimensions, since we have some whose dimensions are known.
    if (maxVideoPixelsToRetain != Integer.MAX_VALUE) {
      for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
        Format format = group.getFormat(selectedTrackIndices.get(i));
        int pixelCount = format.getPixelCount();
        if (pixelCount == Format.NO_VALUE || pixelCount > maxVideoPixelsToRetain) {
          selectedTrackIndices.remove(i);
        }
      }
    }

    return selectedTrackIndices;
  }

  /**
   * Given viewport dimensions and video dimensions, computes the maximum size of the video as it
   * will be rendered to fit inside of the viewport.
   */
  private static Point getMaxVideoSizeInViewport(boolean orientationMayChange, int viewportWidth,
      int viewportHeight, int videoWidth, int videoHeight) {
    if (orientationMayChange && (videoWidth > videoHeight) != (viewportWidth > viewportHeight)) {
      // Rotation is allowed, and the video will be larger in the rotated viewport.
      int tempViewportWidth = viewportWidth;
      viewportWidth = viewportHeight;
      viewportHeight = tempViewportWidth;
    }

    if (videoWidth * viewportHeight >= videoHeight * viewportWidth) {
      // Horizontal letter-boxing along top and bottom.
      return new Point(viewportWidth, Util.ceilDivide(viewportWidth * videoHeight, videoWidth));
    } else {
      // Vertical letter-boxing along edges.
      return new Point(Util.ceilDivide(viewportHeight * videoWidth, videoHeight), viewportHeight);
    }
  }

  /**
   * Compares two integers in a safe way avoiding potential overflow.
   *
   * @param first The first value.
   * @param second The second value.
   * @return A negative integer if the first value is less than the second. Zero if they are equal.
   *     A positive integer if the first value is greater than the second.
   */
  private static int compareInts(int first, int second) {
    return first > second ? 1 : (second > first ? -1 : 0);
  }

  /** Represents how well an audio track matches the selection {@link Parameters}. */
  protected static final class AudioTrackScore implements Comparable<AudioTrackScore> {

    /**
     * Whether the provided format is within the parameter constraints. If {@code false}, the format
     * should not be selected.
     */
    public final boolean isWithinConstraints;

    @Nullable private final String language;
    private final Parameters parameters;
    private final boolean isWithinRendererCapabilities;
    private final int preferredLanguageScore;
    private final int localeLanguageMatchIndex;
    private final int localeLanguageScore;
    private final boolean isDefaultSelectionFlag;
    private final int channelCount;
    private final int sampleRate;
    private final int bitrate;

    public AudioTrackScore(Format format, Parameters parameters, int formatSupport) {
      this.parameters = parameters;
      this.language = normalizeUndeterminedLanguageToNull(format.language);
      isWithinRendererCapabilities = isSupported(formatSupport, false);
      preferredLanguageScore =
          getFormatLanguageScore(
              format,
              parameters.preferredAudioLanguage,
              /* allowUndeterminedFormatLanguage= */ false);
      isDefaultSelectionFlag = (format.selectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
      channelCount = format.channelCount;
      sampleRate = format.sampleRate;
      bitrate = format.bitrate;
      isWithinConstraints =
          (format.bitrate == Format.NO_VALUE || format.bitrate <= parameters.maxAudioBitrate)
              && (format.channelCount == Format.NO_VALUE
                  || format.channelCount <= parameters.maxAudioChannelCount);
      String[] localeLanguages = Util.getSystemLanguageCodes();
      int bestMatchIndex = Integer.MAX_VALUE;
      int bestMatchScore = 0;
      for (int i = 0; i < localeLanguages.length; i++) {
        int score =
            getFormatLanguageScore(
                format, localeLanguages[i], /* allowUndeterminedFormatLanguage= */ false);
        if (score > 0) {
          bestMatchIndex = i;
          bestMatchScore = score;
          break;
        }
      }
      localeLanguageMatchIndex = bestMatchIndex;
      localeLanguageScore = bestMatchScore;
    }

    /**
     * Compares this score with another.
     *
     * @param other The other score to compare to.
     * @return A positive integer if this score is better than the other. Zero if they are equal. A
     *     negative integer if this score is worse than the other.
     */
    @Override
    public int compareTo(AudioTrackScore other) {
      if (this.isWithinRendererCapabilities != other.isWithinRendererCapabilities) {
        return this.isWithinRendererCapabilities ? 1 : -1;
      }
      if (this.preferredLanguageScore != other.preferredLanguageScore) {
        return compareInts(this.preferredLanguageScore, other.preferredLanguageScore);
      }
      if (this.isWithinConstraints != other.isWithinConstraints) {
        return this.isWithinConstraints ? 1 : -1;
      }
      if (parameters.forceLowestBitrate) {
        int bitrateComparison = compareFormatValues(bitrate, other.bitrate);
        if (bitrateComparison != 0) {
          return bitrateComparison > 0 ? -1 : 1;
        }
      }
      if (this.isDefaultSelectionFlag != other.isDefaultSelectionFlag) {
        return this.isDefaultSelectionFlag ? 1 : -1;
      }
      if (this.localeLanguageMatchIndex != other.localeLanguageMatchIndex) {
        return -compareInts(this.localeLanguageMatchIndex, other.localeLanguageMatchIndex);
      }
      if (this.localeLanguageScore != other.localeLanguageScore) {
        return compareInts(this.localeLanguageScore, other.localeLanguageScore);
      }
      // If the formats are within constraints and renderer capabilities then prefer higher values
      // of channel count, sample rate and bit rate in that order. Otherwise, prefer lower values.
      int resultSign = isWithinConstraints && isWithinRendererCapabilities ? 1 : -1;
      if (this.channelCount != other.channelCount) {
        return resultSign * compareInts(this.channelCount, other.channelCount);
      }
      if (this.sampleRate != other.sampleRate) {
        return resultSign * compareInts(this.sampleRate, other.sampleRate);
      }
      if (Util.areEqual(this.language, other.language)) {
        // Only compare bit rates of tracks with the same or unknown language.
        return resultSign * compareInts(this.bitrate, other.bitrate);
      }
      return 0;
    }
  }

  private static final class AudioConfigurationTuple {

    public final int channelCount;
    public final int sampleRate;
    @Nullable public final String mimeType;

    public AudioConfigurationTuple(int channelCount, int sampleRate, @Nullable String mimeType) {
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.mimeType = mimeType;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      AudioConfigurationTuple other = (AudioConfigurationTuple) obj;
      return channelCount == other.channelCount && sampleRate == other.sampleRate
          && TextUtils.equals(mimeType, other.mimeType);
    }

    @Override
    public int hashCode() {
      int result = channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
      return result;
    }

  }

  /** Represents how well a text track matches the selection {@link Parameters}. */
  protected static final class TextTrackScore implements Comparable<TextTrackScore> {

    /**
     * Whether the provided format is within the parameter constraints. If {@code false}, the format
     * should not be selected.
     */
    public final boolean isWithinConstraints;

    private final boolean isWithinRendererCapabilities;
    private final boolean isDefault;
    private final boolean hasPreferredIsForcedFlag;
    private final int preferredLanguageScore;
    private final int preferredRoleFlagsScore;
    private final int selectedAudioLanguageScore;
    private final boolean hasCaptionRoleFlags;

    public TextTrackScore(
        Format format,
        Parameters parameters,
        int trackFormatSupport,
        @Nullable String selectedAudioLanguage) {
      isWithinRendererCapabilities =
          isSupported(trackFormatSupport, /* allowExceedsCapabilities= */ false);
      int maskedSelectionFlags =
          format.selectionFlags & ~parameters.disabledTextTrackSelectionFlags;
      isDefault = (maskedSelectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
      boolean isForced = (maskedSelectionFlags & C.SELECTION_FLAG_FORCED) != 0;
      preferredLanguageScore =
          getFormatLanguageScore(
              format, parameters.preferredTextLanguage, parameters.selectUndeterminedTextLanguage);
      preferredRoleFlagsScore =
          Integer.bitCount(format.roleFlags & parameters.preferredTextRoleFlags);
      hasCaptionRoleFlags =
          (format.roleFlags & (C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND)) != 0;
      // Prefer non-forced to forced if a preferred text language has been matched. Where both are
      // provided the non-forced track will usually contain the forced subtitles as a subset.
      // Otherwise, prefer a forced track.
      hasPreferredIsForcedFlag =
          (preferredLanguageScore > 0 && !isForced) || (preferredLanguageScore == 0 && isForced);
      boolean selectedAudioLanguageUndetermined =
          normalizeUndeterminedLanguageToNull(selectedAudioLanguage) == null;
      selectedAudioLanguageScore =
          getFormatLanguageScore(format, selectedAudioLanguage, selectedAudioLanguageUndetermined);
      isWithinConstraints =
          preferredLanguageScore > 0
              || (parameters.preferredTextLanguage == null && preferredRoleFlagsScore > 0)
              || isDefault
              || (isForced && selectedAudioLanguageScore > 0);
    }

    /**
     * Compares this score with another.
     *
     * @param other The other score to compare to.
     * @return A positive integer if this score is better than the other. Zero if they are equal. A
     *     negative integer if this score is worse than the other.
     */
    @Override
    public int compareTo(TextTrackScore other) {
      if (this.isWithinRendererCapabilities != other.isWithinRendererCapabilities) {
        return this.isWithinRendererCapabilities ? 1 : -1;
      }
      if (this.preferredLanguageScore != other.preferredLanguageScore) {
        return compareInts(this.preferredLanguageScore, other.preferredLanguageScore);
      }
      if (this.preferredRoleFlagsScore != other.preferredRoleFlagsScore) {
        return compareInts(this.preferredRoleFlagsScore, other.preferredRoleFlagsScore);
      }
      if (this.isDefault != other.isDefault) {
        return this.isDefault ? 1 : -1;
      }
      if (this.hasPreferredIsForcedFlag != other.hasPreferredIsForcedFlag) {
        return this.hasPreferredIsForcedFlag ? 1 : -1;
      }
      if (this.selectedAudioLanguageScore != other.selectedAudioLanguageScore) {
        return compareInts(this.selectedAudioLanguageScore, other.selectedAudioLanguageScore);
      }
      if (preferredRoleFlagsScore == 0 && this.hasCaptionRoleFlags != other.hasCaptionRoleFlags) {
        return this.hasCaptionRoleFlags ? -1 : 1;
      }
      return 0;
    }
  }
}
