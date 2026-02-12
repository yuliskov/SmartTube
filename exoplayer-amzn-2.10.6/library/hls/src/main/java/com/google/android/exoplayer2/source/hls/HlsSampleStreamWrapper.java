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
package com.google.android.exoplayer2.source.hls;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.emsg.EventMessageDecoder;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.SampleQueue.UpstreamFormatChangedListener;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Loads {@link HlsMediaChunk}s obtained from a {@link HlsChunkSource}, and provides
 * {@link SampleStream}s from which the loaded media can be consumed.
 */
/* package */ final class HlsSampleStreamWrapper implements Loader.Callback<Chunk>,
    Loader.ReleaseCallback, SequenceableLoader, ExtractorOutput, UpstreamFormatChangedListener {

  /**
   * A callback to be notified of events.
   */
  public interface Callback extends SequenceableLoader.Callback<HlsSampleStreamWrapper> {

    /**
     * Called when the wrapper has been prepared.
     *
     * <p>Note: This method will be called on a later handler loop than the one on which either
     * {@link #prepareWithMasterPlaylistInfo} or {@link #continuePreparing} are invoked.
     */
    void onPrepared();

    /**
     * Called to schedule a {@link #continueLoading(long)} call when the playlist referred by the
     * given url changes.
     */
    void onPlaylistRefreshRequired(Uri playlistUrl);
  }

  private static final String TAG = "HlsSampleStreamWrapper";

  public static final int SAMPLE_QUEUE_INDEX_PENDING = -1;
  public static final int SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL = -2;
  public static final int SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL = -3;

  private static final Set<Integer> MAPPABLE_TYPES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_METADATA)));

  private final int trackType;
  private final Callback callback;
  private final HlsChunkSource chunkSource;
  private final Allocator allocator;
  private final Format muxedAudioFormat;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final Loader loader;
  private final EventDispatcher eventDispatcher;
  private final @HlsMetadataType int metadataType;
  private final HlsChunkSource.HlsChunkHolder nextChunkHolder;
  private final ArrayList<HlsMediaChunk> mediaChunks;
  private final List<HlsMediaChunk> readOnlyMediaChunks;
  private final Runnable maybeFinishPrepareRunnable;
  private final Runnable onTracksEndedRunnable;
  private final Handler handler;
  private final ArrayList<HlsSampleStream> hlsSampleStreams;
  private final Map<String, DrmInitData> overridingDrmInitData;

  private SampleQueue[] sampleQueues;
  private int[] sampleQueueTrackIds;
  private Set<Integer> sampleQueueMappingDoneByType;
  private SparseIntArray sampleQueueIndicesByType;
  private TrackOutput emsgUnwrappingTrackOutput;
  private int primarySampleQueueType;
  private int primarySampleQueueIndex;
  private boolean sampleQueuesBuilt;
  private boolean prepared;
  private int enabledTrackGroupCount;
  private Format upstreamTrackFormat;
  private Format downstreamTrackFormat;
  private boolean released;

  // Tracks are complicated in HLS. See documentation of buildTracksFromSampleStreams for details.
  // Indexed by track (as exposed by this source).
  private TrackGroupArray trackGroups;
  private TrackGroupArray optionalTrackGroups;
  // Indexed by track group.
  private int[] trackGroupToSampleQueueIndex;
  private int primaryTrackGroupIndex;
  private boolean haveAudioVideoSampleQueues;
  private boolean[] sampleQueuesEnabledStates;
  private boolean[] sampleQueueIsAudioVideoFlags;

  private long lastSeekPositionUs;
  private long pendingResetPositionUs;
  private boolean pendingResetUpstreamFormats;
  private boolean seenFirstTrackSelection;
  private boolean loadingFinished;

  // Accessed only by the loading thread.
  private boolean tracksEnded;
  private long sampleOffsetUs;
  private int chunkUid;

  /**
   * @param trackType The type of the track. One of the {@link C} {@code TRACK_TYPE_*} constants.
   * @param callback A callback for the wrapper.
   * @param chunkSource A {@link HlsChunkSource} from which chunks to load are obtained.
   * @param overridingDrmInitData Overriding {@link DrmInitData}, keyed by protection scheme type
   *     (i.e. {@link DrmInitData#schemeType}). If the stream has {@link DrmInitData} and uses a
   *     protection scheme type for which overriding {@link DrmInitData} is provided, then the
   *     stream's {@link DrmInitData} will be overridden.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param positionUs The position from which to start loading media.
   * @param muxedAudioFormat Optional muxed audio {@link Format} as defined by the master playlist.
   * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
   * @param eventDispatcher A dispatcher to notify of events.
   */
  public HlsSampleStreamWrapper(
      int trackType,
      Callback callback,
      HlsChunkSource chunkSource,
      Map<String, DrmInitData> overridingDrmInitData,
      Allocator allocator,
      long positionUs,
      Format muxedAudioFormat,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher,
      @HlsMetadataType int metadataType) {
    this.trackType = trackType;
    this.callback = callback;
    this.chunkSource = chunkSource;
    this.overridingDrmInitData = overridingDrmInitData;
    this.allocator = allocator;
    this.muxedAudioFormat = muxedAudioFormat;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.eventDispatcher = eventDispatcher;
    this.metadataType = metadataType;
    loader = new Loader("Loader:HlsSampleStreamWrapper");
    nextChunkHolder = new HlsChunkSource.HlsChunkHolder();
    sampleQueueTrackIds = new int[0];
    sampleQueueMappingDoneByType = new HashSet<>(MAPPABLE_TYPES.size());
    sampleQueueIndicesByType = new SparseIntArray(MAPPABLE_TYPES.size());
    sampleQueues = new SampleQueue[0];
    sampleQueueIsAudioVideoFlags = new boolean[0];
    sampleQueuesEnabledStates = new boolean[0];
    mediaChunks = new ArrayList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);
    hlsSampleStreams = new ArrayList<>();
    maybeFinishPrepareRunnable = this::maybeFinishPrepare;
    onTracksEndedRunnable = this::onTracksEnded;
    handler = new Handler();
    lastSeekPositionUs = positionUs;
    pendingResetPositionUs = positionUs;
  }

  public void continuePreparing() {
    if (!prepared) {
      continueLoading(lastSeekPositionUs);
    }
  }

  /**
   * Prepares the sample stream wrapper with master playlist information.
   *
   * @param trackGroups The {@link TrackGroupArray} to expose.
   * @param primaryTrackGroupIndex The index of the adaptive track group.
   * @param optionalTrackGroups A subset of {@code trackGroups} that should not trigger a failure if
   *     not found in the media playlist's segments.
   */
  public void prepareWithMasterPlaylistInfo(
      TrackGroupArray trackGroups,
      int primaryTrackGroupIndex,
      TrackGroupArray optionalTrackGroups) {
    prepared = true;
    this.trackGroups = trackGroups;
    this.optionalTrackGroups = optionalTrackGroups;
    this.primaryTrackGroupIndex = primaryTrackGroupIndex;
    handler.post(callback::onPrepared);
  }

  public void maybeThrowPrepareError() throws IOException {
    maybeThrowError();
  }

  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  public int getPrimaryTrackGroupIndex() {
    return primaryTrackGroupIndex;
  }

  public int bindSampleQueueToSampleStream(int trackGroupIndex) {
    int sampleQueueIndex = trackGroupToSampleQueueIndex[trackGroupIndex];
    if (sampleQueueIndex == C.INDEX_UNSET) {
      return optionalTrackGroups.indexOf(trackGroups.get(trackGroupIndex)) == C.INDEX_UNSET
          ? SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL
          : SAMPLE_QUEUE_INDEX_NO_MAPPING_NON_FATAL;
    }
    if (sampleQueuesEnabledStates[sampleQueueIndex]) {
      // This sample queue is already bound to a different sample stream.
      return SAMPLE_QUEUE_INDEX_NO_MAPPING_FATAL;
    }
    sampleQueuesEnabledStates[sampleQueueIndex] = true;
    return sampleQueueIndex;
  }

  public void unbindSampleQueue(int trackGroupIndex) {
    int sampleQueueIndex = trackGroupToSampleQueueIndex[trackGroupIndex];
    Assertions.checkState(sampleQueuesEnabledStates[sampleQueueIndex]);
    sampleQueuesEnabledStates[sampleQueueIndex] = false;
  }

  /**
   * Called by the parent {@link HlsMediaPeriod} when a track selection occurs.
   *
   * @param selections The renderer track selections.
   * @param mayRetainStreamFlags Flags indicating whether the existing sample stream can be retained
   *     for each selection. A {@code true} value indicates that the selection is unchanged, and
   *     that the caller does not require that the sample stream be recreated.
   * @param streams The existing sample streams, which will be updated to reflect the provided
   *     selections.
   * @param streamResetFlags Will be updated to indicate new sample streams, and sample streams that
   *     have been retained but with the requirement that the consuming renderer be reset.
   * @param positionUs The current playback position in microseconds.
   * @param forceReset If true then a reset is forced (i.e. a seek will be performed with in-buffer
   *     seeking disabled).
   * @return Whether this wrapper requires the parent {@link HlsMediaPeriod} to perform a seek as
   *     part of the track selection.
   */
  public boolean selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs, boolean forceReset) {
    Assertions.checkState(prepared);
    int oldEnabledTrackGroupCount = enabledTrackGroupCount;
    // Deselect old tracks.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        enabledTrackGroupCount--;
        ((HlsSampleStream) streams[i]).unbindSampleQueue();
        streams[i] = null;
      }
    }
    // We'll always need to seek if we're being forced to reset, or if this is a first selection to
    // a position other than the one we started preparing with, or if we're making a selection
    // having previously disabled all tracks.
    boolean seekRequired =
        forceReset
            || (seenFirstTrackSelection
                ? oldEnabledTrackGroupCount == 0
                : positionUs != lastSeekPositionUs);
    // Get the old (i.e. current before the loop below executes) primary track selection. The new
    // primary selection will equal the old one unless it's changed in the loop.
    TrackSelection oldPrimaryTrackSelection = chunkSource.getTrackSelection();
    TrackSelection primaryTrackSelection = oldPrimaryTrackSelection;
    // Select new tracks.
    for (int i = 0; i < selections.length; i++) {
      TrackSelection selection = selections[i];
      if (selection == null) {
        continue;
      }
      int trackGroupIndex = trackGroups.indexOf(selection.getTrackGroup());
      if (trackGroupIndex == primaryTrackGroupIndex) {
        primaryTrackSelection = selection;
        chunkSource.setTrackSelection(selection);
      }
      if (streams[i] == null) {
        enabledTrackGroupCount++;
        streams[i] = new HlsSampleStream(this, trackGroupIndex);
        streamResetFlags[i] = true;
        if (trackGroupToSampleQueueIndex != null) {
          ((HlsSampleStream) streams[i]).bindSampleQueue();
        }
        // If there's still a chance of avoiding a seek, try and seek within the sample queue.
        if (sampleQueuesBuilt && !seekRequired) {
          SampleQueue sampleQueue = sampleQueues[trackGroupToSampleQueueIndex[trackGroupIndex]];
          sampleQueue.rewind();
          // A seek can be avoided if we're able to advance to the current playback position in the
          // sample queue, or if we haven't read anything from the queue since the previous seek
          // (this case is common for sparse tracks such as metadata tracks). In all other cases a
          // seek is required.
          seekRequired = sampleQueue.advanceTo(positionUs, true, true) == SampleQueue.ADVANCE_FAILED
              && sampleQueue.getReadIndex() != 0;
        }
      }
    }

    if (enabledTrackGroupCount == 0) {
      chunkSource.reset();
      downstreamTrackFormat = null;
      pendingResetUpstreamFormats = true;
      mediaChunks.clear();
      if (loader.isLoading()) {
        if (sampleQueuesBuilt) {
          // Discard as much as we can synchronously.
          for (SampleQueue sampleQueue : sampleQueues) {
            sampleQueue.discardToEnd();
          }
        }
        loader.cancelLoading();
      } else {
        resetSampleQueues();
      }
    } else {
      if (!mediaChunks.isEmpty()
          && !Util.areEqual(primaryTrackSelection, oldPrimaryTrackSelection)) {
        // The primary track selection has changed and we have buffered media. The buffered media
        // may need to be discarded.
        boolean primarySampleQueueDirty = false;
        if (!seenFirstTrackSelection) {
          long bufferedDurationUs = positionUs < 0 ? -positionUs : 0;
          HlsMediaChunk lastMediaChunk = getLastMediaChunk();
          MediaChunkIterator[] mediaChunkIterators =
              chunkSource.createMediaChunkIterators(lastMediaChunk, positionUs);
          primaryTrackSelection.updateSelectedTrack(
              positionUs,
              bufferedDurationUs,
              C.TIME_UNSET,
              readOnlyMediaChunks,
              mediaChunkIterators);
          int chunkIndex = chunkSource.getTrackGroup().indexOf(lastMediaChunk.trackFormat);
          if (primaryTrackSelection.getSelectedIndexInTrackGroup() != chunkIndex) {
            // This is the first selection and the chunk loaded during preparation does not match
            // the initially selected format.
            primarySampleQueueDirty = true;
          }
        } else {
          // The primary sample queue contains media buffered for the old primary track selection.
          primarySampleQueueDirty = true;
        }
        if (primarySampleQueueDirty) {
          forceReset = true;
          seekRequired = true;
          pendingResetUpstreamFormats = true;
        }
      }
      if (seekRequired) {
        seekToUs(positionUs, forceReset);
        // We'll need to reset renderers consuming from all streams due to the seek.
        for (int i = 0; i < streams.length; i++) {
          if (streams[i] != null) {
            streamResetFlags[i] = true;
          }
        }
      }
    }

    updateSampleStreams(streams);
    seenFirstTrackSelection = true;
    return seekRequired;
  }

  public void discardBuffer(long positionUs, boolean toKeyframe) {
    if (!sampleQueuesBuilt || isPendingReset()) {
      return;
    }
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      sampleQueues[i].discardTo(positionUs, toKeyframe, sampleQueuesEnabledStates[i]);
    }
  }

  /**
   * Attempts to seek to the specified position in microseconds.
   *
   * @param positionUs The seek position in microseconds.
   * @param forceReset If true then a reset is forced (i.e. in-buffer seeking is disabled).
   * @return Whether the wrapper was reset, meaning the wrapped sample queues were reset. If false,
   *     an in-buffer seek was performed.
   */
  public boolean seekToUs(long positionUs, boolean forceReset) {
    lastSeekPositionUs = positionUs;
    if (isPendingReset()) {
      // A reset is already pending. We only need to update its position.
      pendingResetPositionUs = positionUs;
      return true;
    }

    // If we're not forced to reset, try and seek within the buffer.
    if (sampleQueuesBuilt && !forceReset && seekInsideBufferUs(positionUs)) {
      return false;
    }

    // We can't seek inside the buffer, and so need to reset.
    pendingResetPositionUs = positionUs;
    loadingFinished = false;
    mediaChunks.clear();
    if (loader.isLoading()) {
      loader.cancelLoading();
    } else {
      loader.clearFatalError();
      resetSampleQueues();
    }
    return true;
  }

  public void release() {
    if (prepared) {
      // Discard as much as we can synchronously. We only do this if we're prepared, since otherwise
      // sampleQueues may still be being modified by the loading thread.
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.discardToEnd();
      }
    }
    loader.release(this);
    handler.removeCallbacksAndMessages(null);
    released = true;
    hlsSampleStreams.clear();
  }

  @Override
  public void onLoaderReleased() {
    resetSampleQueues();
  }

  public void setIsTimestampMaster(boolean isTimestampMaster) {
    chunkSource.setIsTimestampMaster(isTimestampMaster);
  }

  public boolean onPlaylistError(Uri playlistUrl, long blacklistDurationMs) {
    return chunkSource.onPlaylistError(playlistUrl, blacklistDurationMs);
  }

  // SampleStream implementation.

  public boolean isReady(int sampleQueueIndex) {
    return loadingFinished || (!isPendingReset() && sampleQueues[sampleQueueIndex].hasNextSample());
  }

  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    chunkSource.maybeThrowError();
  }

  public int readData(int sampleQueueIndex, FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean requireFormat) {
    if (isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }

    // TODO: Split into discard (in discardBuffer) and format change (here and in skipData) steps.
    if (!mediaChunks.isEmpty()) {
      int discardToMediaChunkIndex = 0;
      while (discardToMediaChunkIndex < mediaChunks.size() - 1
          && finishedReadingChunk(mediaChunks.get(discardToMediaChunkIndex))) {
        discardToMediaChunkIndex++;
      }
      Util.removeRange(mediaChunks, 0, discardToMediaChunkIndex);
      HlsMediaChunk currentChunk = mediaChunks.get(0);
      Format trackFormat = currentChunk.trackFormat;
      if (!trackFormat.equals(downstreamTrackFormat)) {
        eventDispatcher.downstreamFormatChanged(trackType, trackFormat,
            currentChunk.trackSelectionReason, currentChunk.trackSelectionData,
            currentChunk.startTimeUs);
      }
      downstreamTrackFormat = trackFormat;
    }

    int result =
        sampleQueues[sampleQueueIndex].read(
            formatHolder, buffer, requireFormat, loadingFinished, lastSeekPositionUs);
    if (result == C.RESULT_FORMAT_READ) {
      Format format = formatHolder.format;
      if (sampleQueueIndex == primarySampleQueueIndex) {
        // Fill in primary sample format with information from the track format.
        int chunkUid = sampleQueues[sampleQueueIndex].peekSourceId();
        int chunkIndex = 0;
        while (chunkIndex < mediaChunks.size() && mediaChunks.get(chunkIndex).uid != chunkUid) {
          chunkIndex++;
        }
        Format trackFormat =
            chunkIndex < mediaChunks.size()
                ? mediaChunks.get(chunkIndex).trackFormat
                : upstreamTrackFormat;
        format = format.copyWithManifestFormatInfo(trackFormat);
      }
      if (format.drmInitData != null) {
        DrmInitData drmInitData = overridingDrmInitData.get(format.drmInitData.schemeType);
        if (drmInitData != null) {
          format = format.copyWithDrmInitData(drmInitData);
        }
      }
      formatHolder.format = format;
    }
    return result;
  }

  public int skipData(int sampleQueueIndex, long positionUs) {
    if (isPendingReset()) {
      return 0;
    }

    SampleQueue sampleQueue = sampleQueues[sampleQueueIndex];
    if (loadingFinished && positionUs > sampleQueue.getLargestQueuedTimestampUs()) {
      return sampleQueue.advanceToEnd();
    } else {
      int skipCount = sampleQueue.advanceTo(positionUs, true, true);
      return skipCount == SampleQueue.ADVANCE_FAILED ? 0 : skipCount;
    }
  }

  // SequenceableLoader implementation

  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = lastSeekPositionUs;
      HlsMediaChunk lastMediaChunk = getLastMediaChunk();
      HlsMediaChunk lastCompletedMediaChunk = lastMediaChunk.isLoadCompleted() ? lastMediaChunk
          : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = Math.max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      if (sampleQueuesBuilt) {
        for (SampleQueue sampleQueue : sampleQueues) {
          bufferedPositionUs =
              Math.max(bufferedPositionUs, sampleQueue.getLargestQueuedTimestampUs());
        }
      }
      return bufferedPositionUs;
    }
  }

  @Override
  public long getNextLoadPositionUs() {
    if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      return loadingFinished ? C.TIME_END_OF_SOURCE : getLastMediaChunk().endTimeUs;
    }
  }

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading() || loader.hasFatalError()) {
      return false;
    }

    List<HlsMediaChunk> chunkQueue;
    long loadPositionUs;
    if (isPendingReset()) {
      chunkQueue = Collections.emptyList();
      loadPositionUs = pendingResetPositionUs;
    } else {
      chunkQueue = readOnlyMediaChunks;
      HlsMediaChunk lastMediaChunk = getLastMediaChunk();
      loadPositionUs =
          lastMediaChunk.isLoadCompleted()
              ? lastMediaChunk.endTimeUs
              : Math.max(lastSeekPositionUs, lastMediaChunk.startTimeUs);
    }
    chunkSource.getNextChunk(positionUs, loadPositionUs, chunkQueue, nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    Chunk loadable = nextChunkHolder.chunk;
    Uri playlistUrlToLoad = nextChunkHolder.playlistUrl;
    nextChunkHolder.clear();

    if (endOfStream) {
      pendingResetPositionUs = C.TIME_UNSET;
      loadingFinished = true;
      return true;
    }

    if (loadable == null) {
      if (playlistUrlToLoad != null) {
        callback.onPlaylistRefreshRequired(playlistUrlToLoad);
      }
      return false;
    }

    if (isMediaChunk(loadable)) {
      pendingResetPositionUs = C.TIME_UNSET;
      HlsMediaChunk mediaChunk = (HlsMediaChunk) loadable;
      mediaChunk.init(this);
      mediaChunks.add(mediaChunk);
      upstreamTrackFormat = mediaChunk.trackFormat;
    }
    long elapsedRealtimeMs =
        loader.startLoading(
            loadable, this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type));
    eventDispatcher.loadStarted(
        loadable.dataSpec,
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        elapsedRealtimeMs);
    return true;
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
    chunkSource.onChunkLoadCompleted(loadable);
    eventDispatcher.loadCompleted(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
    if (!prepared) {
      continueLoading(lastSeekPositionUs);
    } else {
      callback.onContinueLoadingRequested(this);
    }
  }

  @Override
  public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    eventDispatcher.loadCanceled(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
    if (!released) {
      resetSampleQueues();
      if (enabledTrackGroupCount > 0) {
        callback.onContinueLoadingRequested(this);
      }
    }
  }

  @Override
  public LoadErrorAction onLoadError(
      Chunk loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    long bytesLoaded = loadable.bytesLoaded();
    boolean isMediaChunk = isMediaChunk(loadable);
    boolean blacklistSucceeded = false;
    LoadErrorAction loadErrorAction;

    long blacklistDurationMs =
        loadErrorHandlingPolicy.getBlacklistDurationMsFor(
            loadable.type, loadDurationMs, error, errorCount);
    if (blacklistDurationMs != C.TIME_UNSET) {
      blacklistSucceeded = chunkSource.maybeBlacklistTrack(loadable, blacklistDurationMs);
    }

    if (blacklistSucceeded) {
      if (isMediaChunk && bytesLoaded == 0) {
        HlsMediaChunk removed = mediaChunks.remove(mediaChunks.size() - 1);
        Assertions.checkState(removed == loadable);
        if (mediaChunks.isEmpty()) {
          pendingResetPositionUs = lastSeekPositionUs;
        }
      }
      loadErrorAction = Loader.DONT_RETRY;
    } else /* did not blacklist */ {
      long retryDelayMs =
          loadErrorHandlingPolicy.getRetryDelayMsFor(
              loadable.type, loadDurationMs, error, errorCount);
      loadErrorAction =
          retryDelayMs != C.TIME_UNSET
              ? Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs)
              : Loader.DONT_RETRY_FATAL;
    }

    eventDispatcher.loadError(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        trackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        elapsedRealtimeMs,
        loadDurationMs,
        bytesLoaded,
        error,
        /* wasCanceled= */ !loadErrorAction.isRetry());

    if (blacklistSucceeded) {
      if (!prepared) {
        continueLoading(lastSeekPositionUs);
      } else {
        callback.onContinueLoadingRequested(this);
      }
    }
    return loadErrorAction;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Initializes the wrapper for loading a chunk.
   *
   * @param chunkUid The chunk's uid.
   * @param shouldSpliceIn Whether the samples parsed from the chunk should be spliced into any
   *     samples already queued to the wrapper.
   * @param reusingExtractor Whether the extractor for the chunk has already been used for preceding
   *     chunks.
   */
  public void init(int chunkUid, boolean shouldSpliceIn, boolean reusingExtractor) {
    if (!reusingExtractor) {
      sampleQueueMappingDoneByType.clear();
    }
    this.chunkUid = chunkUid;
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.sourceId(chunkUid);
    }
    if (shouldSpliceIn) {
      for (SampleQueue sampleQueue : sampleQueues) {
        sampleQueue.splice();
      }
    }
  }

  // ExtractorOutput implementation. Called by the loading thread.

  @Override
  public TrackOutput track(int id, int type) {
    @Nullable TrackOutput trackOutput = null;
    if (MAPPABLE_TYPES.contains(type)) {
      // Track types in MAPPABLE_TYPES are handled manually to ignore IDs.
      trackOutput = getMappedTrackOutput(id, type);
    } else /* non-mappable type track */ {
      for (int i = 0; i < sampleQueues.length; i++) {
        if (sampleQueueTrackIds[i] == id) {
          trackOutput = sampleQueues[i];
          break;
        }
      }
    }

    if (trackOutput == null) {
      if (tracksEnded) {
        return createDummyTrackOutput(id, type);
      } else {
        // The relevant SampleQueue hasn't been constructed yet - so construct it.
        trackOutput = createSampleQueue(id, type);
      }
    }

    if (type == C.TRACK_TYPE_METADATA) {
      if (emsgUnwrappingTrackOutput == null) {
        emsgUnwrappingTrackOutput = new EmsgUnwrappingTrackOutput(trackOutput, metadataType);
      }
      return emsgUnwrappingTrackOutput;
    }
    return trackOutput;
  }

  /**
   * Returns the {@link TrackOutput} for the provided {@code type} and {@code id}, or null if none
   * has been created yet.
   *
   * <p>If a {@link SampleQueue} for {@code type} has been created and is mapped, but it has a
   * different ID, then return a {@link DummyTrackOutput} that does nothing.
   *
   * <p>If a {@link SampleQueue} for {@code type} has been created but is not mapped, then map it to
   * this {@code id} and return it. This situation can happen after a call to {@link #init} with
   * {@code reusingExtractor=false}.
   *
   * @param id The ID of the track.
   * @param type The type of the track, must be one of {@link #MAPPABLE_TYPES}.
   * @return The the mapped {@link TrackOutput}, or null if it's not been created yet.
   */
  @Nullable
  private TrackOutput getMappedTrackOutput(int id, int type) {
    Assertions.checkArgument(MAPPABLE_TYPES.contains(type));
    int sampleQueueIndex = sampleQueueIndicesByType.get(type, C.INDEX_UNSET);
    if (sampleQueueIndex == C.INDEX_UNSET) {
      return null;
    }

    if (sampleQueueMappingDoneByType.add(type)) {
      sampleQueueTrackIds[sampleQueueIndex] = id;
    }
    return sampleQueueTrackIds[sampleQueueIndex] == id
        ? sampleQueues[sampleQueueIndex]
        : createDummyTrackOutput(id, type);
  }

  private SampleQueue createSampleQueue(int id, int type) {
    int trackCount = sampleQueues.length;

    SampleQueue trackOutput = new PrivTimestampStrippingSampleQueue(allocator);
    trackOutput.setSampleOffsetUs(sampleOffsetUs);
    trackOutput.sourceId(chunkUid);
    trackOutput.setUpstreamFormatChangeListener(this);
    sampleQueueTrackIds = Arrays.copyOf(sampleQueueTrackIds, trackCount + 1);
    sampleQueueTrackIds[trackCount] = id;
    sampleQueues = Arrays.copyOf(sampleQueues, trackCount + 1);
    sampleQueues[trackCount] = trackOutput;
    sampleQueueIsAudioVideoFlags = Arrays.copyOf(sampleQueueIsAudioVideoFlags, trackCount + 1);
    sampleQueueIsAudioVideoFlags[trackCount] =
        type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO;
    haveAudioVideoSampleQueues |= sampleQueueIsAudioVideoFlags[trackCount];
    sampleQueueMappingDoneByType.add(type);
    sampleQueueIndicesByType.append(type, trackCount);
    if (getTrackTypeScore(type) > getTrackTypeScore(primarySampleQueueType)) {
      primarySampleQueueIndex = trackCount;
      primarySampleQueueType = type;
    }
    sampleQueuesEnabledStates = Arrays.copyOf(sampleQueuesEnabledStates, trackCount + 1);
    return trackOutput;
  }

  @Override
  public void endTracks() {
    tracksEnded = true;
    handler.post(onTracksEndedRunnable);
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    // Do nothing.
  }

  // UpstreamFormatChangedListener implementation. Called by the loading thread.

  @Override
  public void onUpstreamFormatChanged(Format format) {
    handler.post(maybeFinishPrepareRunnable);
  }

  // Called by the loading thread.

  public void setSampleOffsetUs(long sampleOffsetUs) {
    this.sampleOffsetUs = sampleOffsetUs;
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.setSampleOffsetUs(sampleOffsetUs);
    }
  }

  // Internal methods.

  private void updateSampleStreams(SampleStream[] streams) {
    hlsSampleStreams.clear();
    for (SampleStream stream : streams) {
      if (stream != null) {
        hlsSampleStreams.add((HlsSampleStream) stream);
      }
    }
  }

  private boolean finishedReadingChunk(HlsMediaChunk chunk) {
    int chunkUid = chunk.uid;
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      if (sampleQueuesEnabledStates[i] && sampleQueues[i].peekSourceId() == chunkUid) {
        return false;
      }
    }
    return true;
  }

  private void resetSampleQueues() {
    for (SampleQueue sampleQueue : sampleQueues) {
      sampleQueue.reset(pendingResetUpstreamFormats);
    }
    pendingResetUpstreamFormats = false;
  }

  private void onTracksEnded() {
    sampleQueuesBuilt = true;
    maybeFinishPrepare();
  }

  private void maybeFinishPrepare() {
    if (released || trackGroupToSampleQueueIndex != null || !sampleQueuesBuilt) {
      return;
    }
    for (SampleQueue sampleQueue : sampleQueues) {
      if (sampleQueue.getUpstreamFormat() == null) {
        return;
      }
    }
    if (trackGroups != null) {
      // The track groups were created with master playlist information. They only need to be mapped
      // to a sample queue.
      mapSampleQueuesToMatchTrackGroups();
    } else {
      // Tracks are created using media segment information.
      buildTracksFromSampleStreams();
      prepared = true;
      callback.onPrepared();
    }
  }

  private void mapSampleQueuesToMatchTrackGroups() {
    int trackGroupCount = trackGroups.length;
    trackGroupToSampleQueueIndex = new int[trackGroupCount];
    Arrays.fill(trackGroupToSampleQueueIndex, C.INDEX_UNSET);
    for (int i = 0; i < trackGroupCount; i++) {
      for (int queueIndex = 0; queueIndex < sampleQueues.length; queueIndex++) {
        SampleQueue sampleQueue = sampleQueues[queueIndex];
        if (formatsMatch(sampleQueue.getUpstreamFormat(), trackGroups.get(i).getFormat(0))) {
          trackGroupToSampleQueueIndex[i] = queueIndex;
          break;
        }
      }
    }
    for (HlsSampleStream sampleStream : hlsSampleStreams) {
      sampleStream.bindSampleQueue();
    }
  }

  /**
   * Builds tracks that are exposed by this {@link HlsSampleStreamWrapper} instance, as well as
   * internal data-structures required for operation.
   *
   * <p>Tracks in HLS are complicated. A HLS master playlist contains a number of "variants". Each
   * variant stream typically contains muxed video, audio and (possibly) additional audio, metadata
   * and caption tracks. We wish to allow the user to select between an adaptive track that spans
   * all variants, as well as each individual variant. If multiple audio tracks are present within
   * each variant then we wish to allow the user to select between those also.
   *
   * <p>To do this, tracks are constructed as follows. The {@link HlsChunkSource} exposes (N+1)
   * tracks, where N is the number of variants defined in the HLS master playlist. These consist of
   * one adaptive track defined to span all variants and a track for each individual variant. The
   * adaptive track is initially selected. The extractor is then prepared to discover the tracks
   * inside of each variant stream. The two sets of tracks are then combined by this method to
   * create a third set, which is the set exposed by this {@link HlsSampleStreamWrapper}:
   *
   * <ul>
   *   <li>The extractor tracks are inspected to infer a "primary" track type. If a video track is
   *       present then it is always the primary type. If not, audio is the primary type if present.
   *       Else text is the primary type if present. Else there is no primary type.
   *   <li>If there is exactly one extractor track of the primary type, it's expanded into (N+1)
   *       exposed tracks, all of which correspond to the primary extractor track and each of which
   *       corresponds to a different chunk source track. Selecting one of these tracks has the
   *       effect of switching the selected track on the chunk source.
   *   <li>All other extractor tracks are exposed directly. Selecting one of these tracks has the
   *       effect of selecting an extractor track, leaving the selected track on the chunk source
   *       unchanged.
   * </ul>
   */
  private void buildTracksFromSampleStreams() {
    // Iterate through the extractor tracks to discover the "primary" track type, and the index
    // of the single track of this type.
    int primaryExtractorTrackType = C.TRACK_TYPE_NONE;
    int primaryExtractorTrackIndex = C.INDEX_UNSET;
    int extractorTrackCount = sampleQueues.length;
    for (int i = 0; i < extractorTrackCount; i++) {
      String sampleMimeType = sampleQueues[i].getUpstreamFormat().sampleMimeType;
      int trackType;
      if (MimeTypes.isVideo(sampleMimeType)) {
        trackType = C.TRACK_TYPE_VIDEO;
      } else if (MimeTypes.isAudio(sampleMimeType)) {
        trackType = C.TRACK_TYPE_AUDIO;
      } else if (MimeTypes.isText(sampleMimeType)) {
        trackType = C.TRACK_TYPE_TEXT;
      } else {
        trackType = C.TRACK_TYPE_NONE;
      }
      if (getTrackTypeScore(trackType) > getTrackTypeScore(primaryExtractorTrackType)) {
        primaryExtractorTrackType = trackType;
        primaryExtractorTrackIndex = i;
      } else if (trackType == primaryExtractorTrackType
          && primaryExtractorTrackIndex != C.INDEX_UNSET) {
        // We have multiple tracks of the primary type. We only want an index if there only exists a
        // single track of the primary type, so unset the index again.
        primaryExtractorTrackIndex = C.INDEX_UNSET;
      }
    }

    TrackGroup chunkSourceTrackGroup = chunkSource.getTrackGroup();
    int chunkSourceTrackCount = chunkSourceTrackGroup.length;

    // Instantiate the necessary internal data-structures.
    primaryTrackGroupIndex = C.INDEX_UNSET;
    trackGroupToSampleQueueIndex = new int[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      trackGroupToSampleQueueIndex[i] = i;
    }

    // Construct the set of exposed track groups.
    TrackGroup[] trackGroups = new TrackGroup[extractorTrackCount];
    for (int i = 0; i < extractorTrackCount; i++) {
      Format sampleFormat = sampleQueues[i].getUpstreamFormat();
      if (i == primaryExtractorTrackIndex) {
        Format[] formats = new Format[chunkSourceTrackCount];
        if (chunkSourceTrackCount == 1) {
          formats[0] = sampleFormat.copyWithManifestFormatInfo(chunkSourceTrackGroup.getFormat(0));
        } else {
          for (int j = 0; j < chunkSourceTrackCount; j++) {
            formats[j] = deriveFormat(chunkSourceTrackGroup.getFormat(j), sampleFormat, true);
          }
        }
        trackGroups[i] = new TrackGroup(formats);
        primaryTrackGroupIndex = i;
      } else {
        Format trackFormat =
            primaryExtractorTrackType == C.TRACK_TYPE_VIDEO
                    && MimeTypes.isAudio(sampleFormat.sampleMimeType)
                ? muxedAudioFormat
                : null;
        trackGroups[i] = new TrackGroup(deriveFormat(trackFormat, sampleFormat, false));
      }
    }
    this.trackGroups = new TrackGroupArray(trackGroups);
    Assertions.checkState(optionalTrackGroups == null);
    optionalTrackGroups = TrackGroupArray.EMPTY;
  }

  private HlsMediaChunk getLastMediaChunk() {
    return mediaChunks.get(mediaChunks.size() - 1);
  }

  private boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  /**
   * Attempts to seek to the specified position within the sample queues.
   *
   * @param positionUs The seek position in microseconds.
   * @return Whether the in-buffer seek was successful.
   */
  private boolean seekInsideBufferUs(long positionUs) {
    int sampleQueueCount = sampleQueues.length;
    for (int i = 0; i < sampleQueueCount; i++) {
      SampleQueue sampleQueue = sampleQueues[i];
      sampleQueue.rewind();
      boolean seekInsideQueue = sampleQueue.advanceTo(positionUs, true, false)
          != SampleQueue.ADVANCE_FAILED;
      // If we have AV tracks then an in-queue seek is successful if the seek into every AV queue
      // is successful. We ignore whether seeks within non-AV queues are successful in this case, as
      // they may be sparse or poorly interleaved. If we only have non-AV tracks then a seek is
      // successful only if the seek into every queue succeeds.
      if (!seekInsideQueue && (sampleQueueIsAudioVideoFlags[i] || !haveAudioVideoSampleQueues)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Scores a track type. Where multiple tracks are muxed into a container, the track with the
   * highest score is the primary track.
   *
   * @param trackType The track type.
   * @return The score.
   */
  private static int getTrackTypeScore(int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_VIDEO:
        return 3;
      case C.TRACK_TYPE_AUDIO:
        return 2;
      case C.TRACK_TYPE_TEXT:
        return 1;
      default:
        return 0;
    }
  }

  /**
   * Derives a track sample format from the corresponding format in the master playlist, and a
   * sample format that may have been obtained from a chunk belonging to a different track.
   *
   * @param playlistFormat The format information obtained from the master playlist.
   * @param sampleFormat The format information obtained from the samples.
   * @param propagateBitrate Whether the bitrate from the playlist format should be included in the
   *     derived format.
   * @return The derived track format.
   */
  private static Format deriveFormat(
      Format playlistFormat, Format sampleFormat, boolean propagateBitrate) {
    if (playlistFormat == null) {
      return sampleFormat;
    }
    int bitrate = propagateBitrate ? playlistFormat.bitrate : Format.NO_VALUE;
    int channelCount =
        playlistFormat.channelCount != Format.NO_VALUE
            ? playlistFormat.channelCount
            : sampleFormat.channelCount;
    int sampleTrackType = MimeTypes.getTrackType(sampleFormat.sampleMimeType);
    String codecs = Util.getCodecsOfType(playlistFormat.codecs, sampleTrackType);
    String mimeType = MimeTypes.getMediaMimeType(codecs);
    if (mimeType == null) {
      mimeType = sampleFormat.sampleMimeType;
    }
    return sampleFormat.copyWithContainerInfo(
        playlistFormat.id,
        playlistFormat.label,
        mimeType,
        codecs,
        playlistFormat.metadata,
        bitrate,
        playlistFormat.width,
        playlistFormat.height,
        playlistFormat.frameRate, // MOD: add fps to hls format info
        channelCount,
        playlistFormat.selectionFlags,
        playlistFormat.language);
  }

  private static boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof HlsMediaChunk;
  }

  private static boolean formatsMatch(Format manifestFormat, Format sampleFormat) {
    String manifestFormatMimeType = manifestFormat.sampleMimeType;
    String sampleFormatMimeType = sampleFormat.sampleMimeType;
    int manifestFormatTrackType = MimeTypes.getTrackType(manifestFormatMimeType);
    if (manifestFormatTrackType != C.TRACK_TYPE_TEXT) {
      return manifestFormatTrackType == MimeTypes.getTrackType(sampleFormatMimeType);
    } else if (!Util.areEqual(manifestFormatMimeType, sampleFormatMimeType)) {
      return false;
    }
    if (MimeTypes.APPLICATION_CEA608.equals(manifestFormatMimeType)
        || MimeTypes.APPLICATION_CEA708.equals(manifestFormatMimeType)) {
      return manifestFormat.accessibilityChannel == sampleFormat.accessibilityChannel;
    }
    return true;
  }

  private static DummyTrackOutput createDummyTrackOutput(int id, int type) {
    Log.w(TAG, "Unmapped track with id " + id + " of type " + type);
    return new DummyTrackOutput();
  }

  private static final class PrivTimestampStrippingSampleQueue extends SampleQueue {

    public PrivTimestampStrippingSampleQueue(Allocator allocator) {
      super(allocator);
    }

    @Override
    public void format(Format format) {
      super.format(format.copyWithMetadata(getAdjustedMetadata(format.metadata)));
    }

    /**
     * Strips the private timestamp frame from metadata, if present. See:
     * https://github.com/google/ExoPlayer/issues/5063
     */
    @Nullable
    private Metadata getAdjustedMetadata(@Nullable Metadata metadata) {
      if (metadata == null) {
        return null;
      }
      int length = metadata.length();
      int transportStreamTimestampMetadataIndex = C.INDEX_UNSET;
      for (int i = 0; i < length; i++) {
        Metadata.Entry metadataEntry = metadata.get(i);
        if (metadataEntry instanceof PrivFrame) {
          PrivFrame privFrame = (PrivFrame) metadataEntry;
          if (HlsMediaChunk.PRIV_TIMESTAMP_FRAME_OWNER.equals(privFrame.owner)) {
            transportStreamTimestampMetadataIndex = i;
            break;
          }
        }
      }
      if (transportStreamTimestampMetadataIndex == C.INDEX_UNSET) {
        return metadata;
      }
      if (length == 1) {
        return null;
      }
      Metadata.Entry[] newMetadataEntries = new Metadata.Entry[length - 1];
      for (int i = 0; i < length; i++) {
        if (i != transportStreamTimestampMetadataIndex) {
          int newIndex = i < transportStreamTimestampMetadataIndex ? i : i - 1;
          newMetadataEntries[newIndex] = metadata.get(i);
        }
      }
      return new Metadata(newMetadataEntries);
    }
  }

  private static class EmsgUnwrappingTrackOutput implements TrackOutput {

    private static final String TAG = "EmsgUnwrappingTrackOutput";

    // TODO(ibaker): Create a Formats util class with common constants like this.
    private static final Format ID3_FORMAT =
        Format.createSampleFormat(
            /* id= */ null, MimeTypes.APPLICATION_ID3, Format.OFFSET_SAMPLE_RELATIVE);
    private static final Format EMSG_FORMAT =
        Format.createSampleFormat(
            /* id= */ null, MimeTypes.APPLICATION_EMSG, Format.OFFSET_SAMPLE_RELATIVE);

    private final EventMessageDecoder emsgDecoder;
    private final TrackOutput delegate;
    private final Format delegateFormat;
    @MonotonicNonNull private Format format;

    private byte[] buffer;
    private int bufferPosition;

    public EmsgUnwrappingTrackOutput(TrackOutput delegate, @HlsMetadataType int metadataType) {
      this.emsgDecoder = new EventMessageDecoder();
      this.delegate = delegate;
      switch (metadataType) {
        case HlsMetadataType.ID3:
          delegateFormat = ID3_FORMAT;
          break;
        case HlsMetadataType.EMSG:
          delegateFormat = EMSG_FORMAT;
          break;
        default:
          throw new IllegalArgumentException("Unknown metadataType: " + metadataType);
      }

      this.buffer = new byte[0];
      this.bufferPosition = 0;
    }

    @Override
    public void format(Format format) {
      this.format = format;
      delegate.format(delegateFormat);
    }

    @Override
    public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
        throws IOException, InterruptedException {
      ensureBufferCapacity(bufferPosition + length);
      int numBytesRead = input.read(buffer, bufferPosition, length);
      if (numBytesRead == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput) {
          return C.RESULT_END_OF_INPUT;
        } else {
          throw new EOFException();
        }
      }
      bufferPosition += numBytesRead;
      return numBytesRead;
    }

    @Override
    public void sampleData(ParsableByteArray buffer, int length) {
      ensureBufferCapacity(bufferPosition + length);
      buffer.readBytes(this.buffer, bufferPosition, length);
      bufferPosition += length;
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      Assertions.checkState(format != null);
      ParsableByteArray sample = getSampleAndTrimBuffer(size, offset);
      ParsableByteArray sampleForDelegate;
      if (Util.areEqual(format.sampleMimeType, delegateFormat.sampleMimeType)) {
        // Incoming format matches delegate track's format, so pass straight through.
        sampleForDelegate = sample;
      } else if (MimeTypes.APPLICATION_EMSG.equals(format.sampleMimeType)) {
        // Incoming sample is EMSG, and delegate track is not expecting EMSG, so try unwrapping.
        EventMessage emsg = emsgDecoder.decode(sample);
        if (!emsgContainsExpectedWrappedFormat(emsg)) {
          Log.w(
              TAG,
              String.format(
                  "Ignoring EMSG. Expected it to contain wrapped %s but actual wrapped format: %s",
                  delegateFormat.sampleMimeType, emsg.getWrappedMetadataFormat()));
          return;
        }
        sampleForDelegate =
            new ParsableByteArray(Assertions.checkNotNull(emsg.getWrappedMetadataBytes()));
      } else {
        Log.w(TAG, "Ignoring sample for unsupported format: " + format.sampleMimeType);
        return;
      }

      int sampleSize = sampleForDelegate.bytesLeft();

      delegate.sampleData(sampleForDelegate, sampleSize);
      delegate.sampleMetadata(timeUs, flags, sampleSize, offset, cryptoData);
    }

    private boolean emsgContainsExpectedWrappedFormat(EventMessage emsg) {
      @Nullable Format wrappedMetadataFormat = emsg.getWrappedMetadataFormat();
      return wrappedMetadataFormat != null
          && Util.areEqual(delegateFormat.sampleMimeType, wrappedMetadataFormat.sampleMimeType);
    }

    private void ensureBufferCapacity(int requiredLength) {
      if (buffer.length < requiredLength) {
        buffer = Arrays.copyOf(buffer, requiredLength + requiredLength / 2);
      }
    }

    /**
     * Removes a complete sample from the {@link #buffer} field & reshuffles the tail data skipped
     * by {@code offset} to the head of the array.
     *
     * @param size see {@code size} param of {@link #sampleMetadata}.
     * @param offset see {@code offset} param of {@link #sampleMetadata}.
     * @return A {@link ParsableByteArray} containing the sample removed from {@link #buffer}.
     */
    private ParsableByteArray getSampleAndTrimBuffer(int size, int offset) {
      int sampleEnd = bufferPosition - offset;
      int sampleStart = sampleEnd - size;

      byte[] sampleBytes = Arrays.copyOfRange(buffer, sampleStart, sampleEnd);
      ParsableByteArray sample = new ParsableByteArray(sampleBytes);

      System.arraycopy(buffer, sampleEnd, buffer, 0, offset);
      bufferPosition = offset;
      return sample;
    }
  }
}
