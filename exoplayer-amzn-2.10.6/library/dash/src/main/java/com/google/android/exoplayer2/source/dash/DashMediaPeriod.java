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
package com.google.android.exoplayer2.source.dash;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.util.Pair;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.EmptySampleStream;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream.EmbeddedSampleStream;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Descriptor;
import com.google.android.exoplayer2.source.dash.manifest.EventStream;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A DASH {@link MediaPeriod}. */
/* package */ final class DashMediaPeriod
    implements MediaPeriod,
        SequenceableLoader.Callback<ChunkSampleStream<DashChunkSource>>,
        ChunkSampleStream.ReleaseCallback<DashChunkSource> {

  private static final Pattern CEA608_SERVICE_DESCRIPTOR_REGEX = Pattern.compile("CC([1-4])=(.+)");

  /* package */ final int id;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final @Nullable TransferListener transferListener;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final long elapsedRealtimeOffsetMs;
  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final Allocator allocator;
  private final TrackGroupArray trackGroups;
  private final TrackGroupInfo[] trackGroupInfos;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final PlayerEmsgHandler playerEmsgHandler;
  private final IdentityHashMap<ChunkSampleStream<DashChunkSource>, PlayerTrackEmsgHandler>
      trackEmsgHandlerBySampleStream;
  private final EventDispatcher eventDispatcher;

  private @Nullable Callback callback;
  private ChunkSampleStream<DashChunkSource>[] sampleStreams;
  private EventSampleStream[] eventSampleStreams;
  private SequenceableLoader compositeSequenceableLoader;
  private DashManifest manifest;
  private int periodIndex;
  private List<EventStream> eventStreams;
  private boolean notifiedReadingStarted;

  public DashMediaPeriod(
      int id,
      DashManifest manifest,
      int periodIndex,
      DashChunkSource.Factory chunkSourceFactory,
      @Nullable TransferListener transferListener,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher,
      long elapsedRealtimeOffsetMs,
      LoaderErrorThrower manifestLoaderErrorThrower,
      Allocator allocator,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      PlayerEmsgCallback playerEmsgCallback) {
    this.id = id;
    this.manifest = manifest;
    this.periodIndex = periodIndex;
    this.chunkSourceFactory = chunkSourceFactory;
    this.transferListener = transferListener;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.eventDispatcher = eventDispatcher;
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.allocator = allocator;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    playerEmsgHandler = new PlayerEmsgHandler(manifest, playerEmsgCallback, allocator);
    sampleStreams = newSampleStreamArray(0);
    eventSampleStreams = new EventSampleStream[0];
    trackEmsgHandlerBySampleStream = new IdentityHashMap<>();
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(sampleStreams);
    Period period = manifest.getPeriod(periodIndex);
    eventStreams = period.eventStreams;
    Pair<TrackGroupArray, TrackGroupInfo[]> result = buildTrackGroups(period.adaptationSets,
        eventStreams);
    trackGroups = result.first;
    trackGroupInfos = result.second;
    eventDispatcher.mediaPeriodCreated();
  }

  /**
   * Updates the {@link DashManifest} and the index of this period in the manifest.
   *
   * @param manifest The updated manifest.
   * @param periodIndex the new index of this period in the updated manifest.
   */
  public void updateManifest(DashManifest manifest, int periodIndex) {
    this.manifest = manifest;
    this.periodIndex = periodIndex;
    playerEmsgHandler.updateManifest(manifest);
    if (sampleStreams != null) {
      for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
        sampleStream.getChunkSource().updateManifest(manifest, periodIndex);
      }
      callback.onContinueLoadingRequested(this);
    }
    eventStreams = manifest.getPeriod(periodIndex).eventStreams;
    for (EventSampleStream eventSampleStream : eventSampleStreams) {
      for (EventStream eventStream : eventStreams) {
        if (eventStream.id().equals(eventSampleStream.eventStreamId())) {
          int lastPeriodIndex = manifest.getPeriodCount() - 1;
          eventSampleStream.updateEventStream(
              eventStream,
              /* eventStreamAppendable= */ manifest.dynamic && periodIndex == lastPeriodIndex);
          break;
        }
      }
    }
  }

  public void release() {
    playerEmsgHandler.release();
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.release(this);
    }
    callback = null;
    eventDispatcher.mediaPeriodReleased();
  }

  // ChunkSampleStream.ReleaseCallback implementation.

  @Override
  public synchronized void onSampleStreamReleased(ChunkSampleStream<DashChunkSource> stream) {
    PlayerTrackEmsgHandler trackEmsgHandler = trackEmsgHandlerBySampleStream.remove(stream);
    if (trackEmsgHandler != null) {
      trackEmsgHandler.release();
    }
  }

  // MediaPeriod implementation.

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    manifestLoaderErrorThrower.maybeThrowError();
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public List<StreamKey> getStreamKeys(List<TrackSelection> trackSelections) {
    List<AdaptationSet> manifestAdaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    List<StreamKey> streamKeys = new ArrayList<>();
    for (TrackSelection trackSelection : trackSelections) {
      int trackGroupIndex = trackGroups.indexOf(trackSelection.getTrackGroup());
      TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
      if (trackGroupInfo.trackGroupCategory != TrackGroupInfo.CATEGORY_PRIMARY) {
        // Ignore non-primary tracks.
        continue;
      }
      int[] adaptationSetIndices = trackGroupInfo.adaptationSetIndices;
      int[] trackIndices = new int[trackSelection.length()];
      for (int i = 0; i < trackSelection.length(); i++) {
        trackIndices[i] = trackSelection.getIndexInTrackGroup(i);
      }
      Arrays.sort(trackIndices);

      int currentAdaptationSetIndex = 0;
      int totalTracksInPreviousAdaptationSets = 0;
      int tracksInCurrentAdaptationSet =
          manifestAdaptationSets.get(adaptationSetIndices[0]).representations.size();
      for (int i = 0; i < trackIndices.length; i++) {
        while (trackIndices[i]
            >= totalTracksInPreviousAdaptationSets + tracksInCurrentAdaptationSet) {
          currentAdaptationSetIndex++;
          totalTracksInPreviousAdaptationSets += tracksInCurrentAdaptationSet;
          tracksInCurrentAdaptationSet =
              manifestAdaptationSets
                  .get(adaptationSetIndices[currentAdaptationSetIndex])
                  .representations
                  .size();
        }
        streamKeys.add(
            new StreamKey(
                periodIndex,
                adaptationSetIndices[currentAdaptationSetIndex],
                trackIndices[i] - totalTracksInPreviousAdaptationSets));
      }
    }
    return streamKeys;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    int[] streamIndexToTrackGroupIndex = getStreamIndexToTrackGroupIndex(selections);
    releaseDisabledStreams(selections, mayRetainStreamFlags, streams);
    releaseOrphanEmbeddedStreams(selections, streams, streamIndexToTrackGroupIndex);
    selectNewStreams(
        selections, streams, streamResetFlags, positionUs, streamIndexToTrackGroupIndex);

    ArrayList<ChunkSampleStream<DashChunkSource>> sampleStreamList = new ArrayList<>();
    ArrayList<EventSampleStream> eventSampleStreamList = new ArrayList<>();
    for (SampleStream sampleStream : streams) {
      if (sampleStream instanceof ChunkSampleStream) {
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream =
            (ChunkSampleStream<DashChunkSource>) sampleStream;
        sampleStreamList.add(stream);
      } else if (sampleStream instanceof EventSampleStream) {
        eventSampleStreamList.add((EventSampleStream) sampleStream);
      }
    }
    sampleStreams = newSampleStreamArray(sampleStreamList.size());
    sampleStreamList.toArray(sampleStreams);
    eventSampleStreams = new EventSampleStream[eventSampleStreamList.size()];
    eventSampleStreamList.toArray(eventSampleStreams);

    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(sampleStreams);
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    compositeSequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return compositeSequenceableLoader.continueLoading(positionUs);
  }

  @Override
  public long getNextLoadPositionUs() {
    return compositeSequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    if (!notifiedReadingStarted) {
      eventDispatcher.readingStarted();
      notifiedReadingStarted = true;
    }
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    return compositeSequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    for (EventSampleStream sampleStream : eventSampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      if (sampleStream.primaryTrackType == C.TRACK_TYPE_VIDEO) {
        return sampleStream.getAdjustedSeekPositionUs(positionUs, seekParameters);
      }
    }
    return positionUs;
  }

  // SequenceableLoader.Callback implementation.

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream) {
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private int[] getStreamIndexToTrackGroupIndex(TrackSelection[] selections) {
    int[] streamIndexToTrackGroupIndex = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      if (selections[i] != null) {
        streamIndexToTrackGroupIndex[i] = trackGroups.indexOf(selections[i].getTrackGroup());
      } else {
        streamIndexToTrackGroupIndex[i] = C.INDEX_UNSET;
      }
    }
    return streamIndexToTrackGroupIndex;
  }

  private void releaseDisabledStreams(
      TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams) {
    for (int i = 0; i < selections.length; i++) {
      if (selections[i] == null || !mayRetainStreamFlags[i]) {
        if (streams[i] instanceof ChunkSampleStream) {
          @SuppressWarnings("unchecked")
          ChunkSampleStream<DashChunkSource> stream =
              (ChunkSampleStream<DashChunkSource>) streams[i];
          stream.release(this);
        } else if (streams[i] instanceof EmbeddedSampleStream) {
          ((EmbeddedSampleStream) streams[i]).release();
        }
        streams[i] = null;
      }
    }
  }

  private void releaseOrphanEmbeddedStreams(
      TrackSelection[] selections, SampleStream[] streams, int[] streamIndexToTrackGroupIndex) {
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] instanceof EmptySampleStream || streams[i] instanceof EmbeddedSampleStream) {
        // We need to release an embedded stream if the corresponding primary stream is released.
        int primaryStreamIndex = getPrimaryStreamIndex(i, streamIndexToTrackGroupIndex);
        boolean mayRetainStream;
        if (primaryStreamIndex == C.INDEX_UNSET) {
          // If the corresponding primary stream is not selected, we may retain an existing
          // EmptySampleStream.
          mayRetainStream = streams[i] instanceof EmptySampleStream;
        } else {
          // If the corresponding primary stream is selected, we may retain the embedded stream if
          // the stream's parent still matches.
          mayRetainStream =
              (streams[i] instanceof EmbeddedSampleStream)
                  && ((EmbeddedSampleStream) streams[i]).parent == streams[primaryStreamIndex];
        }
        if (!mayRetainStream) {
          if (streams[i] instanceof EmbeddedSampleStream) {
            ((EmbeddedSampleStream) streams[i]).release();
          }
          streams[i] = null;
        }
      }
    }
  }

  private void selectNewStreams(
      TrackSelection[] selections,
      SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs,
      int[] streamIndexToTrackGroupIndex) {
    // Create newly selected primary and event streams.
    for (int i = 0; i < selections.length; i++) {
      TrackSelection selection = selections[i];
      if (selection == null) {
        continue;
      }
      if (streams[i] == null) {
        // Create new stream for selection.
        streamResetFlags[i] = true;
        int trackGroupIndex = streamIndexToTrackGroupIndex[i];
        TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
        if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_PRIMARY) {
          streams[i] = buildSampleStream(trackGroupInfo, selection, positionUs);
        } else if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_MANIFEST_EVENTS) {
          EventStream eventStream = eventStreams.get(trackGroupInfo.eventStreamGroupIndex);
          Format format = selection.getTrackGroup().getFormat(0);
          streams[i] = new EventSampleStream(eventStream, format, manifest.dynamic);
        }
      } else if (streams[i] instanceof ChunkSampleStream) {
        // Update selection in existing stream.
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream = (ChunkSampleStream<DashChunkSource>) streams[i];
        stream.getChunkSource().updateTrackSelection(selection);
      }
    }
    // Create newly selected embedded streams from the corresponding primary stream. Note that this
    // second pass is needed because the primary stream may not have been created yet in a first
    // pass if the index of the primary stream is greater than the index of the embedded stream.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] == null && selections[i] != null) {
        int trackGroupIndex = streamIndexToTrackGroupIndex[i];
        TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
        if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_EMBEDDED) {
          int primaryStreamIndex = getPrimaryStreamIndex(i, streamIndexToTrackGroupIndex);
          if (primaryStreamIndex == C.INDEX_UNSET) {
            // If an embedded track is selected without the corresponding primary track, create an
            // empty sample stream instead.
            streams[i] = new EmptySampleStream();
          } else {
            streams[i] =
                ((ChunkSampleStream) streams[primaryStreamIndex])
                    .selectEmbeddedTrack(positionUs, trackGroupInfo.trackType);
          }
        }
      }
    }
  }

  private int getPrimaryStreamIndex(int embeddedStreamIndex, int[] streamIndexToTrackGroupIndex) {
    int embeddedTrackGroupIndex = streamIndexToTrackGroupIndex[embeddedStreamIndex];
    if (embeddedTrackGroupIndex == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    int primaryTrackGroupIndex = trackGroupInfos[embeddedTrackGroupIndex].primaryTrackGroupIndex;
    for (int i = 0; i < streamIndexToTrackGroupIndex.length; i++) {
      int trackGroupIndex = streamIndexToTrackGroupIndex[i];
      if (trackGroupIndex == primaryTrackGroupIndex
          && trackGroupInfos[trackGroupIndex].trackGroupCategory
              == TrackGroupInfo.CATEGORY_PRIMARY) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  private static Pair<TrackGroupArray, TrackGroupInfo[]> buildTrackGroups(
      List<AdaptationSet> adaptationSets, List<EventStream> eventStreams) {
    int[][] groupedAdaptationSetIndices = getGroupedAdaptationSetIndices(adaptationSets);

    int primaryGroupCount = groupedAdaptationSetIndices.length;
    boolean[] primaryGroupHasEventMessageTrackFlags = new boolean[primaryGroupCount];
    Format[][] primaryGroupCea608TrackFormats = new Format[primaryGroupCount][];
    int totalEmbeddedTrackGroupCount =
        identifyEmbeddedTracks(
            primaryGroupCount,
            adaptationSets,
            groupedAdaptationSetIndices,
            primaryGroupHasEventMessageTrackFlags,
            primaryGroupCea608TrackFormats);

    int totalGroupCount = primaryGroupCount + totalEmbeddedTrackGroupCount + eventStreams.size();
    TrackGroup[] trackGroups = new TrackGroup[totalGroupCount];
    TrackGroupInfo[] trackGroupInfos = new TrackGroupInfo[totalGroupCount];

    int trackGroupCount =
        buildPrimaryAndEmbeddedTrackGroupInfos(
            adaptationSets,
            groupedAdaptationSetIndices,
            primaryGroupCount,
            primaryGroupHasEventMessageTrackFlags,
            primaryGroupCea608TrackFormats,
            trackGroups,
            trackGroupInfos);

    buildManifestEventTrackGroupInfos(eventStreams, trackGroups, trackGroupInfos, trackGroupCount);

    return Pair.create(new TrackGroupArray(trackGroups), trackGroupInfos);
  }

  private static int[][] getGroupedAdaptationSetIndices(List<AdaptationSet> adaptationSets) {
    int adaptationSetCount = adaptationSets.size();
    SparseIntArray idToIndexMap = new SparseIntArray(adaptationSetCount);
    for (int i = 0; i < adaptationSetCount; i++) {
      idToIndexMap.put(adaptationSets.get(i).id, i);
    }

    int[][] groupedAdaptationSetIndices = new int[adaptationSetCount][];
    boolean[] adaptationSetUsedFlags = new boolean[adaptationSetCount];

    int groupCount = 0;
    for (int i = 0; i < adaptationSetCount; i++) {
      if (adaptationSetUsedFlags[i]) {
        // This adaptation set has already been included in a group.
        continue;
      }
      adaptationSetUsedFlags[i] = true;
      Descriptor adaptationSetSwitchingProperty = findAdaptationSetSwitchingProperty(
          adaptationSets.get(i).supplementalProperties);
      if (adaptationSetSwitchingProperty == null) {
        groupedAdaptationSetIndices[groupCount++] = new int[] {i};
      } else {
        String[] extraAdaptationSetIds = Util.split(adaptationSetSwitchingProperty.value, ",");
        int[] adaptationSetIndices = new int[1 + extraAdaptationSetIds.length];
        adaptationSetIndices[0] = i;
        int outputIndex = 1;
        for (int j = 0; j < extraAdaptationSetIds.length; j++) {
          int extraIndex =
              idToIndexMap.get(
                  Integer.parseInt(extraAdaptationSetIds[j]), /* valueIfKeyNotFound= */ -1);
          if (extraIndex != -1) {
            adaptationSetUsedFlags[extraIndex] = true;
            adaptationSetIndices[outputIndex] = extraIndex;
            outputIndex++;
          }
        }
        if (outputIndex < adaptationSetIndices.length) {
          adaptationSetIndices = Arrays.copyOf(adaptationSetIndices, outputIndex);
        }
        groupedAdaptationSetIndices[groupCount++] = adaptationSetIndices;
      }
    }

    return groupCount < adaptationSetCount
        ? Arrays.copyOf(groupedAdaptationSetIndices, groupCount) : groupedAdaptationSetIndices;
  }

  /**
   * Iterates through list of primary track groups and identifies embedded tracks.
   *
   * @param primaryGroupCount The number of primary track groups.
   * @param adaptationSets The list of {@link AdaptationSet} of the current DASH period.
   * @param groupedAdaptationSetIndices The indices of {@link AdaptationSet} that belongs to the
   *     same primary group, grouped in primary track groups order.
   * @param primaryGroupHasEventMessageTrackFlags An output array to be filled with flags indicating
   *     whether each of the primary track groups contains an embedded event message track.
   * @param primaryGroupCea608TrackFormats An output array to be filled with track formats for
   *     CEA-608 tracks embedded in each of the primary track groups.
   * @return Total number of embedded track groups.
   */
  private static int identifyEmbeddedTracks(
      int primaryGroupCount,
      List<AdaptationSet> adaptationSets,
      int[][] groupedAdaptationSetIndices,
      boolean[] primaryGroupHasEventMessageTrackFlags,
      Format[][] primaryGroupCea608TrackFormats) {
    int numEmbeddedTrackGroups = 0;
    for (int i = 0; i < primaryGroupCount; i++) {
      if (hasEventMessageTrack(adaptationSets, groupedAdaptationSetIndices[i])) {
        primaryGroupHasEventMessageTrackFlags[i] = true;
        numEmbeddedTrackGroups++;
      }
      primaryGroupCea608TrackFormats[i] =
          getCea608TrackFormats(adaptationSets, groupedAdaptationSetIndices[i]);
      if (primaryGroupCea608TrackFormats[i].length != 0) {
        numEmbeddedTrackGroups++;
      }
    }
    return numEmbeddedTrackGroups;
  }

  private static int buildPrimaryAndEmbeddedTrackGroupInfos(
      List<AdaptationSet> adaptationSets,
      int[][] groupedAdaptationSetIndices,
      int primaryGroupCount,
      boolean[] primaryGroupHasEventMessageTrackFlags,
      Format[][] primaryGroupCea608TrackFormats,
      TrackGroup[] trackGroups,
      TrackGroupInfo[] trackGroupInfos) {
    int trackGroupCount = 0;
    for (int i = 0; i < primaryGroupCount; i++) {
      int[] adaptationSetIndices = groupedAdaptationSetIndices[i];
      List<Representation> representations = new ArrayList<>();
      for (int adaptationSetIndex : adaptationSetIndices) {
        representations.addAll(adaptationSets.get(adaptationSetIndex).representations);
      }
      Format[] formats = new Format[representations.size()];
      for (int j = 0; j < formats.length; j++) {
        formats[j] = representations.get(j).format;
      }

      AdaptationSet firstAdaptationSet = adaptationSets.get(adaptationSetIndices[0]);
      int primaryTrackGroupIndex = trackGroupCount++;
      int eventMessageTrackGroupIndex =
          primaryGroupHasEventMessageTrackFlags[i] ? trackGroupCount++ : C.INDEX_UNSET;
      int cea608TrackGroupIndex =
          primaryGroupCea608TrackFormats[i].length != 0 ? trackGroupCount++ : C.INDEX_UNSET;

      trackGroups[primaryTrackGroupIndex] = new TrackGroup(formats);
      trackGroupInfos[primaryTrackGroupIndex] =
          TrackGroupInfo.primaryTrack(
              firstAdaptationSet.type,
              adaptationSetIndices,
              primaryTrackGroupIndex,
              eventMessageTrackGroupIndex,
              cea608TrackGroupIndex);
      if (eventMessageTrackGroupIndex != C.INDEX_UNSET) {
        Format format = Format.createSampleFormat(firstAdaptationSet.id + ":emsg",
            MimeTypes.APPLICATION_EMSG, null, Format.NO_VALUE, null);
        trackGroups[eventMessageTrackGroupIndex] = new TrackGroup(format);
        trackGroupInfos[eventMessageTrackGroupIndex] =
            TrackGroupInfo.embeddedEmsgTrack(adaptationSetIndices, primaryTrackGroupIndex);
      }
      if (cea608TrackGroupIndex != C.INDEX_UNSET) {
        trackGroups[cea608TrackGroupIndex] = new TrackGroup(primaryGroupCea608TrackFormats[i]);
        trackGroupInfos[cea608TrackGroupIndex] =
            TrackGroupInfo.embeddedCea608Track(adaptationSetIndices, primaryTrackGroupIndex);
      }
    }
    return trackGroupCount;
  }

  private static void buildManifestEventTrackGroupInfos(List<EventStream> eventStreams,
      TrackGroup[] trackGroups, TrackGroupInfo[] trackGroupInfos, int existingTrackGroupCount) {
    for (int i = 0; i < eventStreams.size(); i++) {
      EventStream eventStream = eventStreams.get(i);
      Format format = Format.createSampleFormat(eventStream.id(), MimeTypes.APPLICATION_EMSG, null,
          Format.NO_VALUE, null);
      trackGroups[existingTrackGroupCount] = new TrackGroup(format);
      trackGroupInfos[existingTrackGroupCount++] = TrackGroupInfo.mpdEventTrack(i);
    }
  }

  private ChunkSampleStream<DashChunkSource> buildSampleStream(TrackGroupInfo trackGroupInfo,
      TrackSelection selection, long positionUs) {
    int embeddedTrackCount = 0;
    boolean enableEventMessageTrack =
        trackGroupInfo.embeddedEventMessageTrackGroupIndex != C.INDEX_UNSET;
    TrackGroup embeddedEventMessageTrackGroup = null;
    if (enableEventMessageTrack) {
      embeddedEventMessageTrackGroup =
          trackGroups.get(trackGroupInfo.embeddedEventMessageTrackGroupIndex);
      embeddedTrackCount++;
    }
    boolean enableCea608Tracks = trackGroupInfo.embeddedCea608TrackGroupIndex != C.INDEX_UNSET;
    TrackGroup embeddedCea608TrackGroup = null;
    if (enableCea608Tracks) {
      embeddedCea608TrackGroup = trackGroups.get(trackGroupInfo.embeddedCea608TrackGroupIndex);
      embeddedTrackCount += embeddedCea608TrackGroup.length;
    }

    Format[] embeddedTrackFormats = new Format[embeddedTrackCount];
    int[] embeddedTrackTypes = new int[embeddedTrackCount];
    embeddedTrackCount = 0;
    if (enableEventMessageTrack) {
      embeddedTrackFormats[embeddedTrackCount] = embeddedEventMessageTrackGroup.getFormat(0);
      embeddedTrackTypes[embeddedTrackCount] = C.TRACK_TYPE_METADATA;
      embeddedTrackCount++;
    }
    List<Format> embeddedCea608TrackFormats = new ArrayList<>();
    if (enableCea608Tracks) {
      for (int i = 0; i < embeddedCea608TrackGroup.length; i++) {
        embeddedTrackFormats[embeddedTrackCount] = embeddedCea608TrackGroup.getFormat(i);
        embeddedTrackTypes[embeddedTrackCount] = C.TRACK_TYPE_TEXT;
        embeddedCea608TrackFormats.add(embeddedTrackFormats[embeddedTrackCount]);
        embeddedTrackCount++;
      }
    }

    PlayerTrackEmsgHandler trackPlayerEmsgHandler =
        manifest.dynamic && enableEventMessageTrack
            ? playerEmsgHandler.newPlayerTrackEmsgHandler()
            : null;
    DashChunkSource chunkSource =
        chunkSourceFactory.createDashChunkSource(
            manifestLoaderErrorThrower,
            manifest,
            periodIndex,
            trackGroupInfo.adaptationSetIndices,
            selection,
            trackGroupInfo.trackType,
            elapsedRealtimeOffsetMs,
            enableEventMessageTrack,
            embeddedCea608TrackFormats,
            trackPlayerEmsgHandler,
            transferListener);
    ChunkSampleStream<DashChunkSource> stream =
        new ChunkSampleStream<>(
            trackGroupInfo.trackType,
            embeddedTrackTypes,
            embeddedTrackFormats,
            chunkSource,
            this,
            allocator,
            positionUs,
            loadErrorHandlingPolicy,
            eventDispatcher);
    synchronized (this) {
      // The map is also accessed on the loading thread so synchronize access.
      trackEmsgHandlerBySampleStream.put(stream, trackPlayerEmsgHandler);
    }
    return stream;
  }

  private static Descriptor findAdaptationSetSwitchingProperty(List<Descriptor> descriptors) {
    for (int i = 0; i < descriptors.size(); i++) {
      Descriptor descriptor = descriptors.get(i);
      if ("urn:mpeg:dash:adaptation-set-switching:2016".equals(descriptor.schemeIdUri)) {
        return descriptor;
      }
    }
    return null;
  }

  private static boolean hasEventMessageTrack(List<AdaptationSet> adaptationSets,
      int[] adaptationSetIndices) {
    for (int i : adaptationSetIndices) {
      List<Representation> representations = adaptationSets.get(i).representations;
      for (int j = 0; j < representations.size(); j++) {
        Representation representation = representations.get(j);
        if (!representation.inbandEventStreams.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private static Format[] getCea608TrackFormats(
      List<AdaptationSet> adaptationSets, int[] adaptationSetIndices) {
    for (int i : adaptationSetIndices) {
      AdaptationSet adaptationSet = adaptationSets.get(i);
      List<Descriptor> descriptors = adaptationSets.get(i).accessibilityDescriptors;
      for (int j = 0; j < descriptors.size(); j++) {
        Descriptor descriptor = descriptors.get(j);
        if ("urn:scte:dash:cc:cea-608:2015".equals(descriptor.schemeIdUri)) {
          String value = descriptor.value;
          if (value == null) {
            // There are embedded CEA-608 tracks, but service information is not declared.
            return new Format[] {buildCea608TrackFormat(adaptationSet.id)};
          }
          String[] services = Util.split(value, ";");
          Format[] formats = new Format[services.length];
          for (int k = 0; k < services.length; k++) {
            Matcher matcher = CEA608_SERVICE_DESCRIPTOR_REGEX.matcher(services[k]);
            if (!matcher.matches()) {
              // If we can't parse service information for all services, assume a single track.
              return new Format[] {buildCea608TrackFormat(adaptationSet.id)};
            }
            formats[k] =
                buildCea608TrackFormat(
                    adaptationSet.id,
                    /* language= */ matcher.group(2),
                    /* accessibilityChannel= */ Integer.parseInt(matcher.group(1)));
          }
          return formats;
        }
      }
    }
    return new Format[0];
  }

  private static Format buildCea608TrackFormat(int adaptationSetId) {
    return buildCea608TrackFormat(
        adaptationSetId, /* language= */ null, /* accessibilityChannel= */ Format.NO_VALUE);
  }

  private static Format buildCea608TrackFormat(
      int adaptationSetId, String language, int accessibilityChannel) {
    return Format.createTextSampleFormat(
        adaptationSetId
            + ":cea608"
            + (accessibilityChannel != Format.NO_VALUE ? ":" + accessibilityChannel : ""),
        MimeTypes.APPLICATION_CEA608,
        /* codecs= */ null,
        /* bitrate= */ Format.NO_VALUE,
        /* selectionFlags= */ 0,
        language,
        accessibilityChannel,
        /* drmInitData= */ null,
        Format.OFFSET_SAMPLE_RELATIVE,
        /* initializationData= */ null);
  }

  @SuppressWarnings("unchecked")
  private static ChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }

  private static final class TrackGroupInfo {

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({CATEGORY_PRIMARY, CATEGORY_EMBEDDED, CATEGORY_MANIFEST_EVENTS})
    public @interface TrackGroupCategory {}

    /**
     * A normal track group that has its samples drawn from the stream.
     * For example: a video Track Group or an audio Track Group.
     */
    private static final int CATEGORY_PRIMARY = 0;

    /**
     * A track group whose samples are embedded within one of the primary streams. For example: an
     * EMSG track has its sample embedded in emsg atoms in one of the primary streams.
     */
    private static final int CATEGORY_EMBEDDED = 1;

    /**
     * A track group that has its samples listed explicitly in the DASH manifest file.
     * For example: an EventStream track has its sample (Events) included directly in the DASH
     * manifest file.
     */
    private static final int CATEGORY_MANIFEST_EVENTS = 2;

    public final int[] adaptationSetIndices;
    public final int trackType;
    @TrackGroupCategory public final int trackGroupCategory;

    public final int eventStreamGroupIndex;
    public final int primaryTrackGroupIndex;
    public final int embeddedEventMessageTrackGroupIndex;
    public final int embeddedCea608TrackGroupIndex;

    public static TrackGroupInfo primaryTrack(
        int trackType,
        int[] adaptationSetIndices,
        int primaryTrackGroupIndex,
        int embeddedEventMessageTrackGroupIndex,
        int embeddedCea608TrackGroupIndex) {
      return new TrackGroupInfo(
          trackType,
          CATEGORY_PRIMARY,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          embeddedEventMessageTrackGroupIndex,
          embeddedCea608TrackGroupIndex,
          /* eventStreamGroupIndex= */ -1);
    }

    public static TrackGroupInfo embeddedEmsgTrack(int[] adaptationSetIndices,
        int primaryTrackGroupIndex) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_METADATA,
          CATEGORY_EMBEDDED,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          /* eventStreamGroupIndex= */ -1);
    }

    public static TrackGroupInfo embeddedCea608Track(int[] adaptationSetIndices,
        int primaryTrackGroupIndex) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_TEXT,
          CATEGORY_EMBEDDED,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          /* eventStreamGroupIndex= */ -1);
    }

    public static TrackGroupInfo mpdEventTrack(int eventStreamIndex) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_METADATA,
          CATEGORY_MANIFEST_EVENTS,
          new int[0],
          /* primaryTrackGroupIndex= */ -1,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          eventStreamIndex);
    }

    private TrackGroupInfo(
        int trackType,
        @TrackGroupCategory int trackGroupCategory,
        int[] adaptationSetIndices,
        int primaryTrackGroupIndex,
        int embeddedEventMessageTrackGroupIndex,
        int embeddedCea608TrackGroupIndex,
        int eventStreamGroupIndex) {
      this.trackType = trackType;
      this.adaptationSetIndices = adaptationSetIndices;
      this.trackGroupCategory = trackGroupCategory;
      this.primaryTrackGroupIndex = primaryTrackGroupIndex;
      this.embeddedEventMessageTrackGroupIndex = embeddedEventMessageTrackGroupIndex;
      this.embeddedCea608TrackGroupIndex = embeddedCea608TrackGroupIndex;
      this.eventStreamGroupIndex = eventStreamGroupIndex;
    }
  }

}
