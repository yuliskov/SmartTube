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
package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.source.chunk.MediaChunkListIterator;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.google.android.exoplayer2.util.Assertions;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Track selection related utility methods. */
public final class TrackSelectionUtil {

  private TrackSelectionUtil() {}

  /** Functional interface to create a single adaptive track selection. */
  public interface AdaptiveTrackSelectionFactory {

    /**
     * Creates an adaptive track selection for the provided track selection definition.
     *
     * @param trackSelectionDefinition A {@link Definition} for the track selection.
     * @return The created track selection.
     */
    TrackSelection createAdaptiveTrackSelection(Definition trackSelectionDefinition);
  }

  /**
   * Creates track selections for an array of track selection definitions, with at most one
   * multi-track adaptive selection.
   *
   * @param definitions The list of track selection {@link Definition definitions}. May include null
   *     values.
   * @param adaptiveTrackSelectionFactory A factory for the multi-track adaptive track selection.
   * @return The array of created track selection. For null entries in {@code definitions} returns
   *     null values.
   */
  public static @NullableType TrackSelection[] createTrackSelectionsForDefinitions(
      @NullableType Definition[] definitions,
      AdaptiveTrackSelectionFactory adaptiveTrackSelectionFactory) {
    TrackSelection[] selections = new TrackSelection[definitions.length];
    boolean createdAdaptiveTrackSelection = false;
    for (int i = 0; i < definitions.length; i++) {
      Definition definition = definitions[i];
      if (definition == null) {
        continue;
      }
      if (definition.tracks.length > 1 && !createdAdaptiveTrackSelection) {
        createdAdaptiveTrackSelection = true;
        selections[i] = adaptiveTrackSelectionFactory.createAdaptiveTrackSelection(definition);
      } else {
        selections[i] =
            new FixedTrackSelection(
                definition.group, definition.tracks[0], definition.reason, definition.data);
      }
    }
    return selections;
  }

  /**
   * Updates {@link DefaultTrackSelector.Parameters} with an override.
   *
   * @param parameters The current {@link DefaultTrackSelector.Parameters} to build upon.
   * @param rendererIndex The renderer index to update.
   * @param trackGroupArray The {@link TrackGroupArray} of the renderer.
   * @param isDisabled Whether the renderer should be set disabled.
   * @param override An optional override for the renderer. If null, no override will be set and an
   *     existing override for this renderer will be cleared.
   * @return The updated {@link DefaultTrackSelector.Parameters}.
   */
  public static DefaultTrackSelector.Parameters updateParametersWithOverride(
      DefaultTrackSelector.Parameters parameters,
      int rendererIndex,
      TrackGroupArray trackGroupArray,
      boolean isDisabled,
      @Nullable SelectionOverride override) {
    DefaultTrackSelector.ParametersBuilder builder =
        parameters
            .buildUpon()
            .clearSelectionOverrides(rendererIndex)
            .setRendererDisabled(rendererIndex, isDisabled);
    if (override != null) {
      builder.setSelectionOverride(rendererIndex, trackGroupArray, override);
    }
    return builder.build();
  }

  /**
   * Returns average bitrate for chunks in bits per second. Chunks are included in average until
   * {@code maxDurationMs} or the first unknown length chunk.
   *
   * @param iterator Iterator for media chunk sequences.
   * @param maxDurationUs Maximum duration of chunks to be included in average bitrate, in
   *     microseconds.
   * @return Average bitrate for chunks in bits per second, or {@link Format#NO_VALUE} if there are
   *     no chunks or the first chunk length is unknown.
   */
  public static int getAverageBitrate(MediaChunkIterator iterator, long maxDurationUs) {
    long totalDurationUs = 0;
    long totalLength = 0;
    while (iterator.next()) {
      long chunkLength = iterator.getDataSpec().length;
      if (chunkLength == C.LENGTH_UNSET) {
        break;
      }
      long chunkDurationUs = iterator.getChunkEndTimeUs() - iterator.getChunkStartTimeUs();
      if (totalDurationUs + chunkDurationUs >= maxDurationUs) {
        totalLength += chunkLength * (maxDurationUs - totalDurationUs) / chunkDurationUs;
        totalDurationUs = maxDurationUs;
        break;
      }
      totalDurationUs += chunkDurationUs;
      totalLength += chunkLength;
    }
    return totalDurationUs == 0
        ? Format.NO_VALUE
        : (int) (totalLength * C.BITS_PER_BYTE * C.MICROS_PER_SECOND / totalDurationUs);
  }

  /**
   * Returns bitrate values for a set of tracks whose upcoming media chunk iterators and formats are
   * given.
   *
   * <p>If an average bitrate can't be calculated, an estimation is calculated using average bitrate
   * of another track and the ratio of the bitrate values defined in the formats of the two tracks.
   *
   * @param iterators An array of {@link MediaChunkIterator}s providing information about the
   *     sequence of upcoming media chunks for each track.
   * @param formats The track formats.
   * @param maxDurationUs Maximum duration of chunks to be included in average bitrate values, in
   *     microseconds.
   * @param bitrates If not null, stores bitrate values in this array.
   * @return Average bitrate values for the tracks. If for a track, an average bitrate or an
   *     estimation can't be calculated, {@link Format#NO_VALUE} is set.
   * @see #getAverageBitrate(MediaChunkIterator, long)
   */
  @VisibleForTesting
  /* package */ static int[] getBitratesUsingFutureInfo(
      MediaChunkIterator[] iterators,
      Format[] formats,
      long maxDurationUs,
      @Nullable int[] bitrates) {
    int trackCount = iterators.length;
    Assertions.checkArgument(trackCount == formats.length);
    if (trackCount == 0) {
      return new int[0];
    }
    if (bitrates == null) {
      bitrates = new int[trackCount];
    }
    if (maxDurationUs == 0) {
      Arrays.fill(bitrates, Format.NO_VALUE);
      return bitrates;
    }

    int[] formatBitrates = new int[trackCount];
    float[] bitrateRatios = new float[trackCount];
    boolean needEstimateBitrate = false;
    boolean canEstimateBitrate = false;
    for (int i = 0; i < trackCount; i++) {
      int bitrate = getAverageBitrate(iterators[i], maxDurationUs);
      if (bitrate != Format.NO_VALUE) {
        int formatBitrate = formats[i].bitrate;
        formatBitrates[i] = formatBitrate;
        if (formatBitrate != Format.NO_VALUE) {
          bitrateRatios[i] = ((float) bitrate) / formatBitrate;
          canEstimateBitrate = true;
        }
      } else {
        needEstimateBitrate = true;
        formatBitrates[i] = Format.NO_VALUE;
      }
      bitrates[i] = bitrate;
    }

    if (needEstimateBitrate && canEstimateBitrate) {
      estimateBitrates(bitrates, formats, formatBitrates, bitrateRatios);
    }
    return bitrates;
  }

  /**
   * Returns bitrate values for a set of tracks whose formats are given, using the given queue of
   * already buffered {@link MediaChunk} instances.
   *
   * @param queue The queue of already buffered {@link MediaChunk} instances. Must not be modified.
   * @param formats The track formats.
   * @param maxDurationUs Maximum duration of chunks to be included in average bitrate values, in
   *     microseconds.
   * @param bitrates If not null, calculates bitrate values only for indexes set to Format.NO_VALUE
   *     and stores result in this array.
   * @return Bitrate values for the tracks. If for a track, a bitrate value can't be calculated,
   *     {@link Format#NO_VALUE} is set.
   * @see #getBitratesUsingFutureInfo(MediaChunkIterator[], Format[], long, int[])
   */
  @VisibleForTesting
  /* package */ static int[] getBitratesUsingPastInfo(
      List<? extends MediaChunk> queue,
      Format[] formats,
      long maxDurationUs,
      @Nullable int[] bitrates) {
    if (bitrates == null) {
      bitrates = new int[formats.length];
      Arrays.fill(bitrates, Format.NO_VALUE);
    }
    if (maxDurationUs == 0) {
      return bitrates;
    }
    int queueAverageBitrate = getAverageQueueBitrate(queue, maxDurationUs);
    if (queueAverageBitrate == Format.NO_VALUE) {
      return bitrates;
    }
    int queueFormatBitrate = queue.get(queue.size() - 1).trackFormat.bitrate;
    if (queueFormatBitrate != Format.NO_VALUE) {
      float queueBitrateRatio = ((float) queueAverageBitrate) / queueFormatBitrate;
      estimateBitrates(
          bitrates, formats, new int[] {queueFormatBitrate}, new float[] {queueBitrateRatio});
    }
    return bitrates;
  }

  /**
   * Returns bitrate values for a set of tracks whose formats are given, using the given upcoming
   * media chunk iterators and the queue of already buffered {@link MediaChunk}s.
   *
   * @param formats The track formats.
   * @param queue The queue of already buffered {@link MediaChunk}s. Must not be modified.
   * @param maxPastDurationUs Maximum duration of past chunks to be included in average bitrate
   *     values, in microseconds.
   * @param iterators An array of {@link MediaChunkIterator}s providing information about the
   *     sequence of upcoming media chunks for each track.
   * @param maxFutureDurationUs Maximum duration of future chunks to be included in average bitrate
   *     values, in microseconds.
   * @param useFormatBitrateAsLowerBound Whether to return the estimated bitrate only if it's higher
   *     than the bitrate of the track's format.
   * @param bitrates An array into which the bitrate values will be written. If non-null, this array
   *     is the one that will be returned.
   * @return Bitrate values for the tracks. As long as the format of a track has set bitrate, a
   *     bitrate value is set in the returned array. Otherwise it might be set to {@link
   *     Format#NO_VALUE}.
   */
  public static int[] getBitratesUsingPastAndFutureInfo(
      Format[] formats,
      List<? extends MediaChunk> queue,
      long maxPastDurationUs,
      MediaChunkIterator[] iterators,
      long maxFutureDurationUs,
      boolean useFormatBitrateAsLowerBound,
      @Nullable int[] bitrates) {
    bitrates = getBitratesUsingFutureInfo(iterators, formats, maxFutureDurationUs, bitrates);
    getBitratesUsingPastInfo(queue, formats, maxPastDurationUs, bitrates);
    for (int i = 0; i < bitrates.length; i++) {
      int bitrate = bitrates[i];
      if (bitrate == Format.NO_VALUE
          || (useFormatBitrateAsLowerBound
              && formats[i].bitrate != Format.NO_VALUE
              && bitrate < formats[i].bitrate)) {
        bitrates[i] = formats[i].bitrate;
      }
    }
    return bitrates;
  }

  /**
   * Returns an array containing {@link Format#bitrate} values for given each format in order.
   *
   * @param formats The format array to copy {@link Format#bitrate} values.
   * @param bitrates If not null, stores bitrate values in this array.
   * @return An array containing {@link Format#bitrate} values for given each format in order.
   */
  public static int[] getFormatBitrates(Format[] formats, @Nullable int[] bitrates) {
    int trackCount = formats.length;
    if (bitrates == null) {
      bitrates = new int[trackCount];
    }
    for (int i = 0; i < trackCount; i++) {
      bitrates[i] = formats[i].bitrate;
    }
    return bitrates;
  }

  /**
   * Fills missing values in the given {@code bitrates} array by calculates an estimation using the
   * closest reference bitrate value.
   *
   * @param bitrates An array of bitrates to be filled with estimations. Missing values are set to
   *     {@link Format#NO_VALUE}.
   * @param formats An array of formats, one for each bitrate.
   * @param referenceBitrates An array of reference bitrates which are used to calculate
   *     estimations.
   * @param referenceBitrateRatios An array containing ratio of reference bitrates to their bitrate
   *     estimates.
   */
  private static void estimateBitrates(
      int[] bitrates, Format[] formats, int[] referenceBitrates, float[] referenceBitrateRatios) {
    for (int i = 0; i < bitrates.length; i++) {
      if (bitrates[i] == Format.NO_VALUE) {
        int formatBitrate = formats[i].bitrate;
        if (formatBitrate != Format.NO_VALUE) {
          int closestReferenceBitrateIndex =
              getClosestBitrateIndex(formatBitrate, referenceBitrates);
          bitrates[i] =
              (int) (referenceBitrateRatios[closestReferenceBitrateIndex] * formatBitrate);
        }
      }
    }
  }

  private static int getAverageQueueBitrate(List<? extends MediaChunk> queue, long maxDurationUs) {
    if (queue.isEmpty()) {
      return Format.NO_VALUE;
    }
    MediaChunkListIterator iterator =
        new MediaChunkListIterator(getSingleFormatSubQueue(queue), /* reverseOrder= */ true);
    return getAverageBitrate(iterator, maxDurationUs);
  }

  private static List<? extends MediaChunk> getSingleFormatSubQueue(
      List<? extends MediaChunk> queue) {
    Format queueFormat = queue.get(queue.size() - 1).trackFormat;
    int queueSize = queue.size();
    for (int i = queueSize - 2; i >= 0; i--) {
      if (!queue.get(i).trackFormat.equals(queueFormat)) {
        return queue.subList(i + 1, queueSize);
      }
    }
    return queue;
  }

  private static int getClosestBitrateIndex(int formatBitrate, int[] formatBitrates) {
    int closestDistance = Integer.MAX_VALUE;
    int closestFormat = C.INDEX_UNSET;
    for (int j = 0; j < formatBitrates.length; j++) {
      if (formatBitrates[j] != Format.NO_VALUE) {
        int distance = Math.abs(formatBitrates[j] - formatBitrate);
        if (distance < closestDistance) {
          closestDistance = distance;
          closestFormat = j;
        }
      }
    }
    return closestFormat;
  }
}
