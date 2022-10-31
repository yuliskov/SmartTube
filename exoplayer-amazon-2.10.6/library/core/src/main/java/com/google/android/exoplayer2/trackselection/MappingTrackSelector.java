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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Base class for {@link TrackSelector}s that first establish a mapping between {@link TrackGroup}s
 * and {@link Renderer}s, and then from that mapping create a {@link TrackSelection} for each
 * renderer.
 */
public abstract class MappingTrackSelector extends TrackSelector {

  /**
   * Provides mapped track information for each renderer.
   */
  public static final class MappedTrackInfo {

    /**
     * Levels of renderer support. Higher numerical values indicate higher levels of support. One of
     * {@link #RENDERER_SUPPORT_NO_TRACKS}, {@link #RENDERER_SUPPORT_UNSUPPORTED_TRACKS}, {@link
     * #RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS} or {@link #RENDERER_SUPPORT_PLAYABLE_TRACKS}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      RENDERER_SUPPORT_NO_TRACKS,
      RENDERER_SUPPORT_UNSUPPORTED_TRACKS,
      RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS,
      RENDERER_SUPPORT_PLAYABLE_TRACKS
    })
    @interface RendererSupport {}
    /** The renderer does not have any associated tracks. */
    public static final int RENDERER_SUPPORT_NO_TRACKS = 0;
    /**
     * The renderer has tracks mapped to it, but all are unsupported. In other words, {@link
     * #getTrackSupport(int, int, int)} returns {@link RendererCapabilities#FORMAT_UNSUPPORTED_DRM},
     * {@link RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE} or {@link
     * RendererCapabilities#FORMAT_UNSUPPORTED_TYPE} for all tracks mapped to the renderer.
     */
    public static final int RENDERER_SUPPORT_UNSUPPORTED_TRACKS = 1;
    /**
     * The renderer has tracks mapped to it and at least one is of a supported type, but all such
     * tracks exceed the renderer's capabilities. In other words, {@link #getTrackSupport(int, int,
     * int)} returns {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES} for at least one
     * track mapped to the renderer, but does not return {@link
     * RendererCapabilities#FORMAT_HANDLED} for any tracks mapped to the renderer.
     */
    public static final int RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS = 2;
    /**
     * The renderer has tracks mapped to it, and at least one such track is playable. In other
     * words, {@link #getTrackSupport(int, int, int)} returns {@link
     * RendererCapabilities#FORMAT_HANDLED} for at least one track mapped to the renderer.
     */
    public static final int RENDERER_SUPPORT_PLAYABLE_TRACKS = 3;

    /** @deprecated Use {@link #getRendererCount()}. */
    @Deprecated public final int length;

    private final int rendererCount;
    private final int[] rendererTrackTypes;
    private final TrackGroupArray[] rendererTrackGroups;
    private final int[] rendererMixedMimeTypeAdaptiveSupports;
    private final int[][][] rendererFormatSupports;
    private final TrackGroupArray unmappedTrackGroups;

    /**
     * @param rendererTrackTypes The track type handled by each renderer.
     * @param rendererTrackGroups The {@link TrackGroup}s mapped to each renderer.
     * @param rendererMixedMimeTypeAdaptiveSupports The result of {@link
     *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
     * @param rendererFormatSupports The result of {@link RendererCapabilities#supportsFormat} for
     *     each mapped track, indexed by renderer, track group and track (in that order).
     * @param unmappedTrackGroups {@link TrackGroup}s not mapped to any renderer.
     */
    @SuppressWarnings("deprecation")
    /* package */ MappedTrackInfo(
        int[] rendererTrackTypes,
        TrackGroupArray[] rendererTrackGroups,
        int[] rendererMixedMimeTypeAdaptiveSupports,
        int[][][] rendererFormatSupports,
        TrackGroupArray unmappedTrackGroups) {
      this.rendererTrackTypes = rendererTrackTypes;
      this.rendererTrackGroups = rendererTrackGroups;
      this.rendererFormatSupports = rendererFormatSupports;
      this.rendererMixedMimeTypeAdaptiveSupports = rendererMixedMimeTypeAdaptiveSupports;
      this.unmappedTrackGroups = unmappedTrackGroups;
      this.rendererCount = rendererTrackTypes.length;
      this.length = rendererCount;
    }

    /** Returns the number of renderers. */
    public int getRendererCount() {
      return rendererCount;
    }

    /**
     * Returns the track type that the renderer at a given index handles.
     *
     * @see Renderer#getTrackType()
     * @param rendererIndex The renderer index.
     * @return One of the {@code TRACK_TYPE_*} constants defined in {@link C}.
     */
    public int getRendererType(int rendererIndex) {
      return rendererTrackTypes[rendererIndex];
    }

    /**
     * Returns the {@link TrackGroup}s mapped to the renderer at the specified index.
     *
     * @param rendererIndex The renderer index.
     * @return The corresponding {@link TrackGroup}s.
     */
    public TrackGroupArray getTrackGroups(int rendererIndex) {
      return rendererTrackGroups[rendererIndex];
    }

    /**
     * Returns the extent to which a renderer can play the tracks that are mapped to it.
     *
     * @param rendererIndex The renderer index.
     * @return One of {@link #RENDERER_SUPPORT_PLAYABLE_TRACKS}, {@link
     *     #RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS}, {@link
     *     #RENDERER_SUPPORT_UNSUPPORTED_TRACKS} and {@link #RENDERER_SUPPORT_NO_TRACKS}.
     */
    public @RendererSupport int getRendererSupport(int rendererIndex) {
      int bestRendererSupport = RENDERER_SUPPORT_NO_TRACKS;
      int[][] rendererFormatSupport = rendererFormatSupports[rendererIndex];
      for (int i = 0; i < rendererFormatSupport.length; i++) {
        for (int j = 0; j < rendererFormatSupport[i].length; j++) {
          int trackRendererSupport;
          switch (rendererFormatSupport[i][j] & RendererCapabilities.FORMAT_SUPPORT_MASK) {
            case RendererCapabilities.FORMAT_HANDLED:
              return RENDERER_SUPPORT_PLAYABLE_TRACKS;
            case RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES:
              trackRendererSupport = RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS;
              break;
            default:
              trackRendererSupport = RENDERER_SUPPORT_UNSUPPORTED_TRACKS;
              break;
          }
          bestRendererSupport = Math.max(bestRendererSupport, trackRendererSupport);
        }
      }
      return bestRendererSupport;
    }

    /** @deprecated Use {@link #getTypeSupport(int)}. */
    @Deprecated
    public @RendererSupport int getTrackTypeRendererSupport(int trackType) {
      return getTypeSupport(trackType);
    }

    /**
     * Returns the extent to which tracks of a specified type are supported. This is the best level
     * of support obtained from {@link #getRendererSupport(int)} for all renderers that handle the
     * specified type. If no such renderers exist then {@link #RENDERER_SUPPORT_NO_TRACKS} is
     * returned.
     *
     * @param trackType The track type. One of the {@link C} {@code TRACK_TYPE_*} constants.
     * @return One of {@link #RENDERER_SUPPORT_PLAYABLE_TRACKS}, {@link
     *     #RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS}, {@link
     *     #RENDERER_SUPPORT_UNSUPPORTED_TRACKS} and {@link #RENDERER_SUPPORT_NO_TRACKS}.
     */
    public @RendererSupport int getTypeSupport(int trackType) {
      int bestRendererSupport = RENDERER_SUPPORT_NO_TRACKS;
      for (int i = 0; i < rendererCount; i++) {
        if (rendererTrackTypes[i] == trackType) {
          bestRendererSupport = Math.max(bestRendererSupport, getRendererSupport(i));
        }
      }
      return bestRendererSupport;
    }

    /** @deprecated Use {@link #getTrackSupport(int, int, int)}. */
    @Deprecated
    public int getTrackFormatSupport(int rendererIndex, int groupIndex, int trackIndex) {
      return getTrackSupport(rendererIndex, groupIndex, trackIndex);
    }

    /**
     * Returns the extent to which an individual track is supported by the renderer.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group to which the track belongs.
     * @param trackIndex The index of the track within the track group.
     * @return One of {@link RendererCapabilities#FORMAT_HANDLED}, {@link
     *     RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES}, {@link
     *     RendererCapabilities#FORMAT_UNSUPPORTED_DRM}, {@link
     *     RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE} and {@link
     *     RendererCapabilities#FORMAT_UNSUPPORTED_TYPE}.
     */
    public int getTrackSupport(int rendererIndex, int groupIndex, int trackIndex) {
      return rendererFormatSupports[rendererIndex][groupIndex][trackIndex]
          & RendererCapabilities.FORMAT_SUPPORT_MASK;
    }

    /**
     * Returns the extent to which a renderer supports adaptation between supported tracks in a
     * specified {@link TrackGroup}.
     *
     * <p>Tracks for which {@link #getTrackSupport(int, int, int)} returns {@link
     * RendererCapabilities#FORMAT_HANDLED} are always considered. Tracks for which {@link
     * #getTrackSupport(int, int, int)} returns {@link
     * RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES} are also considered if {@code
     * includeCapabilitiesExceededTracks} is set to {@code true}. Tracks for which {@link
     * #getTrackSupport(int, int, int)} returns {@link RendererCapabilities#FORMAT_UNSUPPORTED_DRM},
     * {@link RendererCapabilities#FORMAT_UNSUPPORTED_TYPE} or {@link
     * RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE} are never considered.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group.
     * @param includeCapabilitiesExceededTracks Whether tracks that exceed the capabilities of the
     *     renderer are included when determining support.
     * @return One of {@link RendererCapabilities#ADAPTIVE_SEAMLESS}, {@link
     *     RendererCapabilities#ADAPTIVE_NOT_SEAMLESS} and {@link
     *     RendererCapabilities#ADAPTIVE_NOT_SUPPORTED}.
     */
    public int getAdaptiveSupport(
        int rendererIndex, int groupIndex, boolean includeCapabilitiesExceededTracks) {
      int trackCount = rendererTrackGroups[rendererIndex].get(groupIndex).length;
      // Iterate over the tracks in the group, recording the indices of those to consider.
      int[] trackIndices = new int[trackCount];
      int trackIndexCount = 0;
      for (int i = 0; i < trackCount; i++) {
        int fixedSupport = getTrackSupport(rendererIndex, groupIndex, i);
        if (fixedSupport == RendererCapabilities.FORMAT_HANDLED
            || (includeCapabilitiesExceededTracks
            && fixedSupport == RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES)) {
          trackIndices[trackIndexCount++] = i;
        }
      }
      trackIndices = Arrays.copyOf(trackIndices, trackIndexCount);
      return getAdaptiveSupport(rendererIndex, groupIndex, trackIndices);
    }

    /**
     * Returns the extent to which a renderer supports adaptation between specified tracks within a
     * {@link TrackGroup}.
     *
     * @param rendererIndex The renderer index.
     * @param groupIndex The index of the track group.
     * @return One of {@link RendererCapabilities#ADAPTIVE_SEAMLESS}, {@link
     *     RendererCapabilities#ADAPTIVE_NOT_SEAMLESS} and {@link
     *     RendererCapabilities#ADAPTIVE_NOT_SUPPORTED}.
     */
    public int getAdaptiveSupport(int rendererIndex, int groupIndex, int[] trackIndices) {
      int handledTrackCount = 0;
      int adaptiveSupport = RendererCapabilities.ADAPTIVE_SEAMLESS;
      boolean multipleMimeTypes = false;
      String firstSampleMimeType = null;
      for (int i = 0; i < trackIndices.length; i++) {
        int trackIndex = trackIndices[i];
        String sampleMimeType =
            rendererTrackGroups[rendererIndex].get(groupIndex).getFormat(trackIndex).sampleMimeType;
        if (handledTrackCount++ == 0) {
          firstSampleMimeType = sampleMimeType;
        } else {
          multipleMimeTypes |= !Util.areEqual(firstSampleMimeType, sampleMimeType);
        }
        adaptiveSupport =
            Math.min(
                adaptiveSupport,
                rendererFormatSupports[rendererIndex][groupIndex][i]
                    & RendererCapabilities.ADAPTIVE_SUPPORT_MASK);
      }
      return multipleMimeTypes
          ? Math.min(adaptiveSupport, rendererMixedMimeTypeAdaptiveSupports[rendererIndex])
          : adaptiveSupport;
    }

    /** @deprecated Use {@link #getUnmappedTrackGroups()}. */
    @Deprecated
    public TrackGroupArray getUnassociatedTrackGroups() {
      return getUnmappedTrackGroups();
    }

    /** Returns {@link TrackGroup}s not mapped to any renderer. */
    public TrackGroupArray getUnmappedTrackGroups() {
      return unmappedTrackGroups;
    }

  }

  private @Nullable MappedTrackInfo currentMappedTrackInfo;

  /**
   * Returns the mapping information for the currently active track selection, or null if no
   * selection is currently active.
   */
  public final @Nullable MappedTrackInfo getCurrentMappedTrackInfo() {
    return currentMappedTrackInfo;
  }

  // TrackSelector implementation.

  @Override
  public final void onSelectionActivated(Object info) {
    currentMappedTrackInfo = (MappedTrackInfo) info;
  }

  @Override
  public final TrackSelectorResult selectTracks(
      RendererCapabilities[] rendererCapabilities,
      TrackGroupArray trackGroups,
      MediaPeriodId periodId,
      Timeline timeline)
      throws ExoPlaybackException {
    // Structures into which data will be written during the selection. The extra item at the end
    // of each array is to store data associated with track groups that cannot be associated with
    // any renderer.
    int[] rendererTrackGroupCounts = new int[rendererCapabilities.length + 1];
    TrackGroup[][] rendererTrackGroups = new TrackGroup[rendererCapabilities.length + 1][];
    int[][][] rendererFormatSupports = new int[rendererCapabilities.length + 1][][];
    for (int i = 0; i < rendererTrackGroups.length; i++) {
      rendererTrackGroups[i] = new TrackGroup[trackGroups.length];
      rendererFormatSupports[i] = new int[trackGroups.length][];
    }

    // Determine the extent to which each renderer supports mixed mimeType adaptation.
    int[] rendererMixedMimeTypeAdaptationSupports =
        getMixedMimeTypeAdaptationSupports(rendererCapabilities);

    // Associate each track group to a preferred renderer, and evaluate the support that the
    // renderer provides for each track in the group.
    for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
      TrackGroup group = trackGroups.get(groupIndex);
      // Associate the group to a preferred renderer.
      int rendererIndex = findRenderer(rendererCapabilities, group);
      // Evaluate the support that the renderer provides for each track in the group.
      int[] rendererFormatSupport = rendererIndex == rendererCapabilities.length
          ? new int[group.length] : getFormatSupport(rendererCapabilities[rendererIndex], group);
      // Stash the results.
      int rendererTrackGroupCount = rendererTrackGroupCounts[rendererIndex];
      rendererTrackGroups[rendererIndex][rendererTrackGroupCount] = group;
      rendererFormatSupports[rendererIndex][rendererTrackGroupCount] = rendererFormatSupport;
      rendererTrackGroupCounts[rendererIndex]++;
    }

    // Create a track group array for each renderer, and trim each rendererFormatSupports entry.
    TrackGroupArray[] rendererTrackGroupArrays = new TrackGroupArray[rendererCapabilities.length];
    int[] rendererTrackTypes = new int[rendererCapabilities.length];
    for (int i = 0; i < rendererCapabilities.length; i++) {
      int rendererTrackGroupCount = rendererTrackGroupCounts[i];
      rendererTrackGroupArrays[i] =
          new TrackGroupArray(
              Util.nullSafeArrayCopy(rendererTrackGroups[i], rendererTrackGroupCount));
      rendererFormatSupports[i] =
          Util.nullSafeArrayCopy(rendererFormatSupports[i], rendererTrackGroupCount);
      rendererTrackTypes[i] = rendererCapabilities[i].getTrackType();
    }

    // Create a track group array for track groups not mapped to a renderer.
    int unmappedTrackGroupCount = rendererTrackGroupCounts[rendererCapabilities.length];
    TrackGroupArray unmappedTrackGroupArray =
        new TrackGroupArray(
            Util.nullSafeArrayCopy(
                rendererTrackGroups[rendererCapabilities.length], unmappedTrackGroupCount));

    // Package up the track information and selections.
    MappedTrackInfo mappedTrackInfo =
        new MappedTrackInfo(
            rendererTrackTypes,
            rendererTrackGroupArrays,
            rendererMixedMimeTypeAdaptationSupports,
            rendererFormatSupports,
            unmappedTrackGroupArray);

    Pair<@NullableType RendererConfiguration[], @NullableType TrackSelection[]> result =
        selectTracks(
            mappedTrackInfo, rendererFormatSupports, rendererMixedMimeTypeAdaptationSupports);
    return new TrackSelectorResult(result.first, result.second, mappedTrackInfo);
  }

  /**
   * Given mapped track information, returns a track selection and configuration for each renderer.
   *
   * @param mappedTrackInfo Mapped track information.
   * @param rendererFormatSupports The result of {@link RendererCapabilities#supportsFormat} for
   *     each mapped track, indexed by renderer, track group and track (in that order).
   * @param rendererMixedMimeTypeAdaptationSupport The result of {@link
   *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
   * @return A pair consisting of the track selections and configurations for each renderer. A null
   *     configuration indicates the renderer should be disabled, in which case the track selection
   *     will also be null. A track selection may also be null for a non-disabled renderer if {@link
   *     RendererCapabilities#getTrackType()} is {@link C#TRACK_TYPE_NONE}.
   * @throws ExoPlaybackException If an error occurs while selecting the tracks.
   */
  protected abstract Pair<@NullableType RendererConfiguration[], @NullableType TrackSelection[]>
      selectTracks(
          MappedTrackInfo mappedTrackInfo,
          int[][][] rendererFormatSupports,
          int[] rendererMixedMimeTypeAdaptationSupport)
          throws ExoPlaybackException;

  /**
   * Finds the renderer to which the provided {@link TrackGroup} should be mapped.
   * <p>
   * A {@link TrackGroup} is mapped to the renderer that reports the highest of (listed in
   * decreasing order of support) {@link RendererCapabilities#FORMAT_HANDLED},
   * {@link RendererCapabilities#FORMAT_EXCEEDS_CAPABILITIES},
   * {@link RendererCapabilities#FORMAT_UNSUPPORTED_DRM} and
   * {@link RendererCapabilities#FORMAT_UNSUPPORTED_SUBTYPE}. In the case that two or more renderers
   * report the same level of support, the renderer with the lowest index is associated.
   * <p>
   * If all renderers report {@link RendererCapabilities#FORMAT_UNSUPPORTED_TYPE} for all of the
   * tracks in the group, then {@code renderers.length} is returned to indicate that the group was
   * not mapped to any renderer.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers.
   * @param group The track group to map to a renderer.
   * @return The index of the renderer to which the track group was mapped, or
   *     {@code renderers.length} if it was not mapped to any renderer.
   * @throws ExoPlaybackException If an error occurs finding a renderer.
   */
  private static int findRenderer(RendererCapabilities[] rendererCapabilities, TrackGroup group)
      throws ExoPlaybackException {
    int bestRendererIndex = rendererCapabilities.length;
    int bestFormatSupportLevel = RendererCapabilities.FORMAT_UNSUPPORTED_TYPE;
    for (int rendererIndex = 0; rendererIndex < rendererCapabilities.length; rendererIndex++) {
      RendererCapabilities rendererCapability = rendererCapabilities[rendererIndex];
      for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
        int formatSupportLevel = rendererCapability.supportsFormat(group.getFormat(trackIndex))
            & RendererCapabilities.FORMAT_SUPPORT_MASK;
        if (formatSupportLevel > bestFormatSupportLevel) {
          bestRendererIndex = rendererIndex;
          bestFormatSupportLevel = formatSupportLevel;
          if (bestFormatSupportLevel == RendererCapabilities.FORMAT_HANDLED) {
            // We can't do better.
            return bestRendererIndex;
          }
        }
      }
    }
    return bestRendererIndex;
  }

  /**
   * Calls {@link RendererCapabilities#supportsFormat} for each track in the specified
   * {@link TrackGroup}, returning the results in an array.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderer.
   * @param group The track group to evaluate.
   * @return An array containing the result of calling
   *     {@link RendererCapabilities#supportsFormat} for each track in the group.
   * @throws ExoPlaybackException If an error occurs determining the format support.
   */
  private static int[] getFormatSupport(RendererCapabilities rendererCapabilities, TrackGroup group)
      throws ExoPlaybackException {
    int[] formatSupport = new int[group.length];
    for (int i = 0; i < group.length; i++) {
      formatSupport[i] = rendererCapabilities.supportsFormat(group.getFormat(i));
    }
    return formatSupport;
  }

  /**
   * Calls {@link RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer,
   * returning the results in an array.
   *
   * @param rendererCapabilities The {@link RendererCapabilities} of the renderers.
   * @return An array containing the result of calling {@link
   *     RendererCapabilities#supportsMixedMimeTypeAdaptation()} for each renderer.
   * @throws ExoPlaybackException If an error occurs determining the adaptation support.
   */
  private static int[] getMixedMimeTypeAdaptationSupports(
      RendererCapabilities[] rendererCapabilities) throws ExoPlaybackException {
    int[] mixedMimeTypeAdaptationSupport = new int[rendererCapabilities.length];
    for (int i = 0; i < mixedMimeTypeAdaptationSupport.length; i++) {
      mixedMimeTypeAdaptationSupport[i] = rendererCapabilities[i].supportsMixedMimeTypeAdaptation();
    }
    return mixedMimeTypeAdaptationSupport;
  }

}
