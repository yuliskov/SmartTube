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
package com.google.android.exoplayer2.source.smoothstreaming;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A SmoothStreaming {@link MediaPeriod}. */
/* package */ final class SsMediaPeriod
    implements MediaPeriod, SequenceableLoader.Callback<ChunkSampleStream<SsChunkSource>> {

  private final SsChunkSource.Factory chunkSourceFactory;
  private final @Nullable TransferListener transferListener;
  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final EventDispatcher eventDispatcher;
  private final Allocator allocator;
  private final TrackGroupArray trackGroups;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;

  private @Nullable Callback callback;
  private SsManifest manifest;
  private ChunkSampleStream<SsChunkSource>[] sampleStreams;
  private SequenceableLoader compositeSequenceableLoader;
  private boolean notifiedReadingStarted;

  public SsMediaPeriod(
      SsManifest manifest,
      SsChunkSource.Factory chunkSourceFactory,
      @Nullable TransferListener transferListener,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher,
      LoaderErrorThrower manifestLoaderErrorThrower,
      Allocator allocator) {
    this.manifest = manifest;
    this.chunkSourceFactory = chunkSourceFactory;
    this.transferListener = transferListener;
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.eventDispatcher = eventDispatcher;
    this.allocator = allocator;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    trackGroups = buildTrackGroups(manifest);
    sampleStreams = newSampleStreamArray(0);
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(sampleStreams);
    eventDispatcher.mediaPeriodCreated();
  }

  public void updateManifest(SsManifest manifest) {
    this.manifest = manifest;
    for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
      sampleStream.getChunkSource().updateManifest(manifest);
    }
    callback.onContinueLoadingRequested(this);
  }

  public void release() {
    for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
      sampleStream.release();
    }
    callback = null;
    eventDispatcher.mediaPeriodReleased();
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
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    ArrayList<ChunkSampleStream<SsChunkSource>> sampleStreamsList = new ArrayList<>();
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null) {
        @SuppressWarnings("unchecked")
        ChunkSampleStream<SsChunkSource> stream = (ChunkSampleStream<SsChunkSource>) streams[i];
        if (selections[i] == null || !mayRetainStreamFlags[i]) {
          stream.release();
          streams[i] = null;
        } else {
          stream.getChunkSource().updateTrackSelection(selections[i]);
          sampleStreamsList.add(stream);
        }
      }
      if (streams[i] == null && selections[i] != null) {
        ChunkSampleStream<SsChunkSource> stream = buildSampleStream(selections[i], positionUs);
        sampleStreamsList.add(stream);
        streams[i] = stream;
        streamResetFlags[i] = true;
      }
    }
    sampleStreams = newSampleStreamArray(sampleStreamsList.size());
    sampleStreamsList.toArray(sampleStreams);
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(sampleStreams);
    return positionUs;
  }

  @Override
  public List<StreamKey> getStreamKeys(List<TrackSelection> trackSelections) {
    List<StreamKey> streamKeys = new ArrayList<>();
    for (int selectionIndex = 0; selectionIndex < trackSelections.size(); selectionIndex++) {
      TrackSelection trackSelection = trackSelections.get(selectionIndex);
      int streamElementIndex = trackGroups.indexOf(trackSelection.getTrackGroup());
      for (int i = 0; i < trackSelection.length(); i++) {
        streamKeys.add(new StreamKey(streamElementIndex, trackSelection.getIndexInTrackGroup(i)));
      }
    }
    return streamKeys;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
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
    for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    for (ChunkSampleStream<SsChunkSource> sampleStream : sampleStreams) {
      if (sampleStream.primaryTrackType == C.TRACK_TYPE_VIDEO) {
        return sampleStream.getAdjustedSeekPositionUs(positionUs, seekParameters);
      }
    }
    return positionUs;
  }

  // SequenceableLoader.Callback implementation.

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<SsChunkSource> sampleStream) {
    callback.onContinueLoadingRequested(this);
  }

  // Private methods.

  private ChunkSampleStream<SsChunkSource> buildSampleStream(TrackSelection selection,
      long positionUs) {
    int streamElementIndex = trackGroups.indexOf(selection.getTrackGroup());
    SsChunkSource chunkSource =
        chunkSourceFactory.createChunkSource(
            manifestLoaderErrorThrower,
            manifest,
            streamElementIndex,
            selection,
            transferListener);
    return new ChunkSampleStream<>(
        manifest.streamElements[streamElementIndex].type,
        null,
        null,
        chunkSource,
        this,
        allocator,
        positionUs,
        loadErrorHandlingPolicy,
        eventDispatcher);
  }

  private static TrackGroupArray buildTrackGroups(SsManifest manifest) {
    TrackGroup[] trackGroups = new TrackGroup[manifest.streamElements.length];
    for (int i = 0; i < manifest.streamElements.length; i++) {
      trackGroups[i] = new TrackGroup(manifest.streamElements[i].formats);
    }
    return new TrackGroupArray(trackGroups);
  }

  @SuppressWarnings("unchecked")
  private static ChunkSampleStream<SsChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }
}
