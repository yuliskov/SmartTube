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
package com.google.android.exoplayer2.source.chunk;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link SampleStream} that loads media in {@link Chunk}s, obtained from a {@link ChunkSource}.
 * May also be configured to expose additional embedded {@link SampleStream}s.
 */
public class ChunkSampleStream<T extends ChunkSource> implements SampleStream, SequenceableLoader,
    Loader.Callback<Chunk>, Loader.ReleaseCallback {

  /** A callback to be notified when a sample stream has finished being released. */
  public interface ReleaseCallback<T extends ChunkSource> {

    /**
     * Called when the {@link ChunkSampleStream} has finished being released.
     *
     * @param chunkSampleStream The released sample stream.
     */
    void onSampleStreamReleased(ChunkSampleStream<T> chunkSampleStream);
  }

  private static final String TAG = "ChunkSampleStream";

  public final int primaryTrackType;

  private final int[] embeddedTrackTypes;
  private final Format[] embeddedTrackFormats;
  private final boolean[] embeddedTracksSelected;
  private final T chunkSource;
  private final SequenceableLoader.Callback<ChunkSampleStream<T>> callback;
  private final EventDispatcher eventDispatcher;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final Loader loader;
  private final ChunkHolder nextChunkHolder;
  private final ArrayList<BaseMediaChunk> mediaChunks;
  private final List<BaseMediaChunk> readOnlyMediaChunks;
  private final SampleQueue primarySampleQueue;
  private final SampleQueue[] embeddedSampleQueues;
  private final BaseMediaChunkOutput mediaChunkOutput;

  private Format primaryDownstreamTrackFormat;
  private @Nullable ReleaseCallback<T> releaseCallback;
  private long pendingResetPositionUs;
  private long lastSeekPositionUs;
  private int nextNotifyPrimaryFormatMediaChunkIndex;

  /* package */ long decodeOnlyUntilPositionUs;
  /* package */ boolean loadingFinished;

  /**
   * Constructs an instance.
   *
   * @param primaryTrackType The type of the primary track. One of the {@link C} {@code
   *     TRACK_TYPE_*} constants.
   * @param embeddedTrackTypes The types of any embedded tracks, or null.
   * @param embeddedTrackFormats The formats of the embedded tracks, or null.
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param callback An {@link Callback} for the stream.
   * @param allocator An {@link Allocator} from which allocations can be obtained.
   * @param positionUs The position from which to start loading media.
   * @param minLoadableRetryCount The minimum number of times that the source should retry a load
   *     before propagating an error.
   * @param eventDispatcher A dispatcher to notify of events.
   * @deprecated Use {@link #ChunkSampleStream(int, int[], Format[], ChunkSource, Callback,
   *     Allocator, long, LoadErrorHandlingPolicy, EventDispatcher)} instead.
   */
  @Deprecated
  public ChunkSampleStream(
      int primaryTrackType,
      int[] embeddedTrackTypes,
      Format[] embeddedTrackFormats,
      T chunkSource,
      Callback<ChunkSampleStream<T>> callback,
      Allocator allocator,
      long positionUs,
      int minLoadableRetryCount,
      EventDispatcher eventDispatcher) {
    this(
        primaryTrackType,
        embeddedTrackTypes,
        embeddedTrackFormats,
        chunkSource,
        callback,
        allocator,
        positionUs,
        new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount),
        eventDispatcher);
  }

  /**
   * Constructs an instance.
   *
   * @param primaryTrackType The type of the primary track. One of the {@link C} {@code
   *     TRACK_TYPE_*} constants.
   * @param embeddedTrackTypes The types of any embedded tracks, or null.
   * @param embeddedTrackFormats The formats of the embedded tracks, or null.
   * @param chunkSource A {@link ChunkSource} from which chunks to load are obtained.
   * @param callback An {@link Callback} for the stream.
   * @param allocator An {@link Allocator} from which allocations can be obtained.
   * @param positionUs The position from which to start loading media.
   * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy}.
   * @param eventDispatcher A dispatcher to notify of events.
   */
  public ChunkSampleStream(
      int primaryTrackType,
      int[] embeddedTrackTypes,
      Format[] embeddedTrackFormats,
      T chunkSource,
      Callback<ChunkSampleStream<T>> callback,
      Allocator allocator,
      long positionUs,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher) {
    this.primaryTrackType = primaryTrackType;
    this.embeddedTrackTypes = embeddedTrackTypes;
    this.embeddedTrackFormats = embeddedTrackFormats;
    this.chunkSource = chunkSource;
    this.callback = callback;
    this.eventDispatcher = eventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    loader = new Loader("Loader:ChunkSampleStream");
    nextChunkHolder = new ChunkHolder();
    mediaChunks = new ArrayList<>();
    readOnlyMediaChunks = Collections.unmodifiableList(mediaChunks);

    int embeddedTrackCount = embeddedTrackTypes == null ? 0 : embeddedTrackTypes.length;
    embeddedSampleQueues = new SampleQueue[embeddedTrackCount];
    embeddedTracksSelected = new boolean[embeddedTrackCount];
    int[] trackTypes = new int[1 + embeddedTrackCount];
    SampleQueue[] sampleQueues = new SampleQueue[1 + embeddedTrackCount];

    primarySampleQueue = new SampleQueue(allocator);
    trackTypes[0] = primaryTrackType;
    sampleQueues[0] = primarySampleQueue;

    for (int i = 0; i < embeddedTrackCount; i++) {
      SampleQueue sampleQueue = new SampleQueue(allocator);
      embeddedSampleQueues[i] = sampleQueue;
      sampleQueues[i + 1] = sampleQueue;
      trackTypes[i + 1] = embeddedTrackTypes[i];
    }

    mediaChunkOutput = new BaseMediaChunkOutput(trackTypes, sampleQueues);
    pendingResetPositionUs = positionUs;
    lastSeekPositionUs = positionUs;
  }

  /**
   * Discards buffered media up to the specified position.
   *
   * @param positionUs The position to discard up to, in microseconds.
   * @param toKeyframe If true then for each track discards samples up to the keyframe before or at
   *     the specified position, rather than any sample before or at that position.
   */
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    if (isPendingReset()) {
      return;
    }
    int oldFirstSampleIndex = primarySampleQueue.getFirstIndex();
    primarySampleQueue.discardTo(positionUs, toKeyframe, true);
    int newFirstSampleIndex = primarySampleQueue.getFirstIndex();
    if (newFirstSampleIndex > oldFirstSampleIndex) {
      long discardToUs = primarySampleQueue.getFirstTimestampUs();
      for (int i = 0; i < embeddedSampleQueues.length; i++) {
        embeddedSampleQueues[i].discardTo(discardToUs, toKeyframe, embeddedTracksSelected[i]);
      }
    }
    discardDownstreamMediaChunks(newFirstSampleIndex);
  }

  /**
   * Selects the embedded track, returning a new {@link EmbeddedSampleStream} from which the track's
   * samples can be consumed. {@link EmbeddedSampleStream#release()} must be called on the returned
   * stream when the track is no longer required, and before calling this method again to obtain
   * another stream for the same track.
   *
   * @param positionUs The current playback position in microseconds.
   * @param trackType The type of the embedded track to enable.
   * @return The {@link EmbeddedSampleStream} for the embedded track.
   */
  public EmbeddedSampleStream selectEmbeddedTrack(long positionUs, int trackType) {
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      if (embeddedTrackTypes[i] == trackType) {
        Assertions.checkState(!embeddedTracksSelected[i]);
        embeddedTracksSelected[i] = true;
        embeddedSampleQueues[i].rewind();
        embeddedSampleQueues[i].advanceTo(positionUs, true, true);
        return new EmbeddedSampleStream(this, embeddedSampleQueues[i], i);
      }
    }
    // Should never happen.
    throw new IllegalStateException();
  }

  /**
   * Returns the {@link ChunkSource} used by this stream.
   */
  public T getChunkSource() {
    return chunkSource;
  }

  /**
   * Returns an estimate of the position up to which data is buffered.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   *     {@link C#TIME_END_OF_SOURCE} if the track is fully buffered.
   */
  @Override
  public long getBufferedPositionUs() {
    if (loadingFinished) {
      return C.TIME_END_OF_SOURCE;
    } else if (isPendingReset()) {
      return pendingResetPositionUs;
    } else {
      long bufferedPositionUs = lastSeekPositionUs;
      BaseMediaChunk lastMediaChunk = getLastMediaChunk();
      BaseMediaChunk lastCompletedMediaChunk = lastMediaChunk.isLoadCompleted() ? lastMediaChunk
          : mediaChunks.size() > 1 ? mediaChunks.get(mediaChunks.size() - 2) : null;
      if (lastCompletedMediaChunk != null) {
        bufferedPositionUs = Math.max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
      }
      return Math.max(bufferedPositionUs, primarySampleQueue.getLargestQueuedTimestampUs());
    }
  }

  /**
   * Adjusts a seek position given the specified {@link SeekParameters}. Chunk boundaries are used
   * as sync points.
   *
   * @param positionUs The seek position in microseconds.
   * @param seekParameters Parameters that control how the seek is performed.
   * @return The adjusted seek position, in microseconds.
   */
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return chunkSource.getAdjustedSeekPositionUs(positionUs, seekParameters);
  }

  /**
   * Seeks to the specified position in microseconds.
   *
   * @param positionUs The seek position in microseconds.
   */
  public void seekToUs(long positionUs) {
    lastSeekPositionUs = positionUs;
    if (isPendingReset()) {
      // A reset is already pending. We only need to update its position.
      pendingResetPositionUs = positionUs;
      return;
    }

    // Detect whether the seek is to the start of a chunk that's at least partially buffered.
    BaseMediaChunk seekToMediaChunk = null;
    for (int i = 0; i < mediaChunks.size(); i++) {
      BaseMediaChunk mediaChunk = mediaChunks.get(i);
      long mediaChunkStartTimeUs = mediaChunk.startTimeUs;
      if (mediaChunkStartTimeUs == positionUs && mediaChunk.clippedStartTimeUs == C.TIME_UNSET) {
        seekToMediaChunk = mediaChunk;
        break;
      } else if (mediaChunkStartTimeUs > positionUs) {
        // We're not going to find a chunk with a matching start time.
        break;
      }
    }

    // See if we can seek inside the primary sample queue.
    boolean seekInsideBuffer;
    primarySampleQueue.rewind();
    if (seekToMediaChunk != null) {
      // When seeking to the start of a chunk we use the index of the first sample in the chunk
      // rather than the seek position. This ensures we seek to the keyframe at the start of the
      // chunk even if the sample timestamps are slightly offset from the chunk start times.
      seekInsideBuffer =
          primarySampleQueue.setReadPosition(seekToMediaChunk.getFirstSampleIndex(0));
      decodeOnlyUntilPositionUs = 0;
    } else {
      seekInsideBuffer =
          primarySampleQueue.advanceTo(
                  positionUs,
                  /* toKeyframe= */ true,
                  /* allowTimeBeyondBuffer= */ positionUs < getNextLoadPositionUs())
              != SampleQueue.ADVANCE_FAILED;
      decodeOnlyUntilPositionUs = lastSeekPositionUs;
    }

    if (seekInsideBuffer) {
      // We can seek inside the buffer.
      nextNotifyPrimaryFormatMediaChunkIndex =
          primarySampleIndexToMediaChunkIndex(
              primarySampleQueue.getReadIndex(), /* minChunkIndex= */ 0);
      // Advance the embedded sample queues to the seek position.
      for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
        embeddedSampleQueue.rewind();
        embeddedSampleQueue.advanceTo(positionUs, true, false);
      }
    } else {
      // We can't seek inside the buffer, and so need to reset.
      pendingResetPositionUs = positionUs;
      loadingFinished = false;
      mediaChunks.clear();
      nextNotifyPrimaryFormatMediaChunkIndex = 0;
      if (loader.isLoading()) {
        loader.cancelLoading();
      } else {
        loader.clearFatalError();
        primarySampleQueue.reset();
        for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
          embeddedSampleQueue.reset();
        }
      }
    }
  }

  /**
   * Releases the stream.
   *
   * <p>This method should be called when the stream is no longer required. Either this method or
   * {@link #release(ReleaseCallback)} can be used to release this stream.
   */
  public void release() {
    release(null);
  }

  /**
   * Releases the stream.
   *
   * <p>This method should be called when the stream is no longer required. Either this method or
   * {@link #release()} can be used to release this stream.
   *
   * @param callback An optional callback to be called on the loading thread once the loader has
   *     been released.
   */
  public void release(@Nullable ReleaseCallback<T> callback) {
    this.releaseCallback = callback;
    // Discard as much as we can synchronously.
    primarySampleQueue.discardToEnd();
    for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
      embeddedSampleQueue.discardToEnd();
    }
    loader.release(this);
  }

  @Override
  public void onLoaderReleased() {
    primarySampleQueue.reset();
    for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
      embeddedSampleQueue.reset();
    }
    if (releaseCallback != null) {
      releaseCallback.onSampleStreamReleased(this);
    }
  }

  // SampleStream implementation.

  @Override
  public boolean isReady() {
    return loadingFinished || (!isPendingReset() && primarySampleQueue.hasNextSample());
  }

  @Override
  public void maybeThrowError() throws IOException {
    loader.maybeThrowError();
    if (!loader.isLoading()) {
      chunkSource.maybeThrowError();
    }
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean formatRequired) {
    if (isPendingReset()) {
      return C.RESULT_NOTHING_READ;
    }
    maybeNotifyPrimaryTrackFormatChanged();
    return primarySampleQueue.read(
        formatHolder, buffer, formatRequired, loadingFinished, decodeOnlyUntilPositionUs);
  }

  @Override
  public int skipData(long positionUs) {
    if (isPendingReset()) {
      return 0;
    }
    int skipCount;
    if (loadingFinished && positionUs > primarySampleQueue.getLargestQueuedTimestampUs()) {
      skipCount = primarySampleQueue.advanceToEnd();
    } else {
      skipCount = primarySampleQueue.advanceTo(positionUs, true, true);
      if (skipCount == SampleQueue.ADVANCE_FAILED) {
        skipCount = 0;
      }
    }
    maybeNotifyPrimaryTrackFormatChanged();
    return skipCount;
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
        primaryTrackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
    callback.onContinueLoadingRequested(this);
  }

  @Override
  public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    eventDispatcher.loadCanceled(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        primaryTrackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
    if (!released) {
      primarySampleQueue.reset();
      for (SampleQueue embeddedSampleQueue : embeddedSampleQueues) {
        embeddedSampleQueue.reset();
      }
      callback.onContinueLoadingRequested(this);
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
    int lastChunkIndex = mediaChunks.size() - 1;
    boolean cancelable =
        bytesLoaded == 0 || !isMediaChunk || !haveReadFromMediaChunk(lastChunkIndex);
    long blacklistDurationMs =
        cancelable
            ? loadErrorHandlingPolicy.getBlacklistDurationMsFor(
                loadable.type, loadDurationMs, error, errorCount)
            : C.TIME_UNSET;
    LoadErrorAction loadErrorAction = null;
    if (chunkSource.onChunkLoadError(loadable, cancelable, error, blacklistDurationMs)) {
      if (cancelable) {
        loadErrorAction = Loader.DONT_RETRY;
        if (isMediaChunk) {
          BaseMediaChunk removed = discardUpstreamMediaChunksFromIndex(lastChunkIndex);
          Assertions.checkState(removed == loadable);
          if (mediaChunks.isEmpty()) {
            pendingResetPositionUs = lastSeekPositionUs;
          }
        }
      } else {
        Log.w(TAG, "Ignoring attempt to cancel non-cancelable load.");
      }
    }

    if (loadErrorAction == null) {
      // The load was not cancelled. Either the load must be retried or the error propagated.
      long retryDelayMs =
          loadErrorHandlingPolicy.getRetryDelayMsFor(
              loadable.type, loadDurationMs, error, errorCount);
      loadErrorAction =
          retryDelayMs != C.TIME_UNSET
              ? Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs)
              : Loader.DONT_RETRY_FATAL;
    }

    boolean canceled = !loadErrorAction.isRetry();
    eventDispatcher.loadError(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        loadable.type,
        primaryTrackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        elapsedRealtimeMs,
        loadDurationMs,
        bytesLoaded,
        error,
        canceled);
    if (canceled) {
      callback.onContinueLoadingRequested(this);
    }
    return loadErrorAction;
  }

  // SequenceableLoader implementation

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading() || loader.hasFatalError()) {
      return false;
    }

    boolean pendingReset = isPendingReset();
    List<BaseMediaChunk> chunkQueue;
    long loadPositionUs;
    if (pendingReset) {
      chunkQueue = Collections.emptyList();
      loadPositionUs = pendingResetPositionUs;
    } else {
      chunkQueue = readOnlyMediaChunks;
      loadPositionUs = getLastMediaChunk().endTimeUs;
    }
    chunkSource.getNextChunk(positionUs, loadPositionUs, chunkQueue, nextChunkHolder);
    boolean endOfStream = nextChunkHolder.endOfStream;
    Chunk loadable = nextChunkHolder.chunk;
    nextChunkHolder.clear();

    if (endOfStream) {
      pendingResetPositionUs = C.TIME_UNSET;
      loadingFinished = true;
      return true;
    }

    if (loadable == null) {
      return false;
    }

    if (isMediaChunk(loadable)) {
      BaseMediaChunk mediaChunk = (BaseMediaChunk) loadable;
      if (pendingReset) {
        boolean resetToMediaChunk = mediaChunk.startTimeUs == pendingResetPositionUs;
        // Only enable setting of the decode only flag if we're not resetting to a chunk boundary.
        decodeOnlyUntilPositionUs = resetToMediaChunk ? 0 : pendingResetPositionUs;
        pendingResetPositionUs = C.TIME_UNSET;
      }
      mediaChunk.init(mediaChunkOutput);
      mediaChunks.add(mediaChunk);
    }
    long elapsedRealtimeMs =
        loader.startLoading(
            loadable, this, loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type));
    eventDispatcher.loadStarted(
        loadable.dataSpec,
        loadable.type,
        primaryTrackType,
        loadable.trackFormat,
        loadable.trackSelectionReason,
        loadable.trackSelectionData,
        loadable.startTimeUs,
        loadable.endTimeUs,
        elapsedRealtimeMs);
    return true;
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
  public void reevaluateBuffer(long positionUs) {
    if (loader.isLoading() || loader.hasFatalError() || isPendingReset()) {
      return;
    }

    int currentQueueSize = mediaChunks.size();
    int preferredQueueSize = chunkSource.getPreferredQueueSize(positionUs, readOnlyMediaChunks);
    if (currentQueueSize <= preferredQueueSize) {
      return;
    }

    int newQueueSize = currentQueueSize;
    for (int i = preferredQueueSize; i < currentQueueSize; i++) {
      if (!haveReadFromMediaChunk(i)) {
        newQueueSize = i;
        break;
      }
    }
    if (newQueueSize == currentQueueSize) {
      return;
    }

    long endTimeUs = getLastMediaChunk().endTimeUs;
    BaseMediaChunk firstRemovedChunk = discardUpstreamMediaChunksFromIndex(newQueueSize);
    if (mediaChunks.isEmpty()) {
      pendingResetPositionUs = lastSeekPositionUs;
    }
    loadingFinished = false;
    eventDispatcher.upstreamDiscarded(primaryTrackType, firstRemovedChunk.startTimeUs, endTimeUs);
  }

  // Internal methods

  private boolean isMediaChunk(Chunk chunk) {
    return chunk instanceof BaseMediaChunk;
  }

  /** Returns whether samples have been read from media chunk at given index. */
  private boolean haveReadFromMediaChunk(int mediaChunkIndex) {
    BaseMediaChunk mediaChunk = mediaChunks.get(mediaChunkIndex);
    if (primarySampleQueue.getReadIndex() > mediaChunk.getFirstSampleIndex(0)) {
      return true;
    }
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      if (embeddedSampleQueues[i].getReadIndex() > mediaChunk.getFirstSampleIndex(i + 1)) {
        return true;
      }
    }
    return false;
  }

  /* package */ boolean isPendingReset() {
    return pendingResetPositionUs != C.TIME_UNSET;
  }

  private void discardDownstreamMediaChunks(int discardToSampleIndex) {
    int discardToMediaChunkIndex =
        primarySampleIndexToMediaChunkIndex(discardToSampleIndex, /* minChunkIndex= */ 0);
    // Don't discard any chunks that we haven't reported the primary format change for yet.
    discardToMediaChunkIndex =
        Math.min(discardToMediaChunkIndex, nextNotifyPrimaryFormatMediaChunkIndex);
    if (discardToMediaChunkIndex > 0) {
      Util.removeRange(mediaChunks, /* fromIndex= */ 0, /* toIndex= */ discardToMediaChunkIndex);
      nextNotifyPrimaryFormatMediaChunkIndex -= discardToMediaChunkIndex;
    }
  }

  private void maybeNotifyPrimaryTrackFormatChanged() {
    int readSampleIndex = primarySampleQueue.getReadIndex();
    int notifyToMediaChunkIndex =
        primarySampleIndexToMediaChunkIndex(
            readSampleIndex, /* minChunkIndex= */ nextNotifyPrimaryFormatMediaChunkIndex - 1);
    while (nextNotifyPrimaryFormatMediaChunkIndex <= notifyToMediaChunkIndex) {
      maybeNotifyPrimaryTrackFormatChanged(nextNotifyPrimaryFormatMediaChunkIndex++);
    }
  }

  private void maybeNotifyPrimaryTrackFormatChanged(int mediaChunkReadIndex) {
    BaseMediaChunk currentChunk = mediaChunks.get(mediaChunkReadIndex);
    Format trackFormat = currentChunk.trackFormat;
    if (!trackFormat.equals(primaryDownstreamTrackFormat)) {
      eventDispatcher.downstreamFormatChanged(primaryTrackType, trackFormat,
          currentChunk.trackSelectionReason, currentChunk.trackSelectionData,
          currentChunk.startTimeUs);
    }
    primaryDownstreamTrackFormat = trackFormat;
  }

  /**
   * Returns the media chunk index corresponding to a given primary sample index.
   *
   * @param primarySampleIndex The primary sample index for which the corresponding media chunk
   *     index is required.
   * @param minChunkIndex A minimum chunk index from which to start searching, or -1 if no hint can
   *     be provided.
   * @return The index of the media chunk corresponding to the sample index, or -1 if the list of
   *     media chunks is empty, or {@code minChunkIndex} if the sample precedes the first chunk in
   *     the search (i.e. the chunk at {@code minChunkIndex}, or at index 0 if {@code minChunkIndex}
   *     is -1.
   */
  private int primarySampleIndexToMediaChunkIndex(int primarySampleIndex, int minChunkIndex) {
    for (int i = minChunkIndex + 1; i < mediaChunks.size(); i++) {
      if (mediaChunks.get(i).getFirstSampleIndex(0) > primarySampleIndex) {
        return i - 1;
      }
    }
    return mediaChunks.size() - 1;
  }

  private BaseMediaChunk getLastMediaChunk() {
    return mediaChunks.get(mediaChunks.size() - 1);
  }

  /**
   * Discard upstream media chunks from {@code chunkIndex} and corresponding samples from sample
   * queues.
   *
   * @param chunkIndex The index of the first chunk to discard.
   * @return The chunk at given index.
   */
  private BaseMediaChunk discardUpstreamMediaChunksFromIndex(int chunkIndex) {
    BaseMediaChunk firstRemovedChunk = mediaChunks.get(chunkIndex);
    Util.removeRange(mediaChunks, /* fromIndex= */ chunkIndex, /* toIndex= */ mediaChunks.size());
    nextNotifyPrimaryFormatMediaChunkIndex =
        Math.max(nextNotifyPrimaryFormatMediaChunkIndex, mediaChunks.size());
    primarySampleQueue.discardUpstreamSamples(firstRemovedChunk.getFirstSampleIndex(0));
    for (int i = 0; i < embeddedSampleQueues.length; i++) {
      embeddedSampleQueues[i].discardUpstreamSamples(firstRemovedChunk.getFirstSampleIndex(i + 1));
    }
    return firstRemovedChunk;
  }

  /**
   * A {@link SampleStream} embedded in a {@link ChunkSampleStream}.
   */
  public final class EmbeddedSampleStream implements SampleStream {

    public final ChunkSampleStream<T> parent;

    private final SampleQueue sampleQueue;
    private final int index;

    private boolean notifiedDownstreamFormat;

    public EmbeddedSampleStream(ChunkSampleStream<T> parent, SampleQueue sampleQueue, int index) {
      this.parent = parent;
      this.sampleQueue = sampleQueue;
      this.index = index;
    }

    @Override
    public boolean isReady() {
      return loadingFinished || (!isPendingReset() && sampleQueue.hasNextSample());
    }

    @Override
    public int skipData(long positionUs) {
      if (isPendingReset()) {
        return 0;
      }
      maybeNotifyDownstreamFormat();
      int skipCount;
      if (loadingFinished && positionUs > sampleQueue.getLargestQueuedTimestampUs()) {
        skipCount = sampleQueue.advanceToEnd();
      } else {
        skipCount = sampleQueue.advanceTo(positionUs, true, true);
        if (skipCount == SampleQueue.ADVANCE_FAILED) {
          skipCount = 0;
        }
      }
      return skipCount;
    }

    @Override
    public void maybeThrowError() throws IOException {
      // Do nothing. Errors will be thrown from the primary stream.
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
        boolean formatRequired) {
      if (isPendingReset()) {
        return C.RESULT_NOTHING_READ;
      }
      maybeNotifyDownstreamFormat();
      return sampleQueue.read(
          formatHolder, buffer, formatRequired, loadingFinished, decodeOnlyUntilPositionUs);
    }

    public void release() {
      Assertions.checkState(embeddedTracksSelected[index]);
      embeddedTracksSelected[index] = false;
    }

    private void maybeNotifyDownstreamFormat() {
      if (!notifiedDownstreamFormat) {
        eventDispatcher.downstreamFormatChanged(
            embeddedTrackTypes[index],
            embeddedTrackFormats[index],
            C.SELECTION_REASON_UNKNOWN,
            /* trackSelectionData= */ null,
            lastSeekPositionUs);
        notifiedDownstreamFormat = true;
      }
    }
  }

}
