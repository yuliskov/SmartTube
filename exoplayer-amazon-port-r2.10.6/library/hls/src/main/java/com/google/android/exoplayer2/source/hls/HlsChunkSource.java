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
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkIterator;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.DataChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.UriUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source of Hls (possibly adaptive) chunks.
 */
/* package */ class HlsChunkSource {

  /**
   * Chunk holder that allows the scheduling of retries.
   */
  public static final class HlsChunkHolder {

    public HlsChunkHolder() {
      clear();
    }

    /**
     * The chunk to be loaded next.
     */
    public Chunk chunk;

    /**
     * Indicates that the end of the stream has been reached.
     */
    public boolean endOfStream;

    /** Indicates that the chunk source is waiting for the referred playlist to be refreshed. */
    public Uri playlistUrl;

    /**
     * Clears the holder.
     */
    public void clear() {
      chunk = null;
      endOfStream = false;
      playlistUrl = null;
    }

  }

  /**
   * The maximum number of keys that the key cache can hold. This value must be 2 or greater in
   * order to hold initialization segment and media segment keys simultaneously.
   */
  private static final int KEY_CACHE_SIZE = 4;

  private final HlsExtractorFactory extractorFactory;
  private final DataSource mediaDataSource;
  private final DataSource encryptionDataSource;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final Uri[] playlistUrls;
  private final Format[] playlistFormats;
  private final HlsPlaylistTracker playlistTracker;
  private final TrackGroup trackGroup;
  private final List<Format> muxedCaptionFormats;
  private final FullSegmentEncryptionKeyCache keyCache;

  private boolean isTimestampMaster;
  private byte[] scratchSpace;
  private IOException fatalError;
  private Uri expectedPlaylistUrl;
  private boolean independentSegments;

  // Note: The track group in the selection is typically *not* equal to trackGroup. This is due to
  // the way in which HlsSampleStreamWrapper generates track groups. Use only index based methods
  // in TrackSelection to avoid unexpected behavior.
  private TrackSelection trackSelection;
  private long liveEdgeInPeriodTimeUs;
  private boolean seenExpectedPlaylistError;

  /**
   * @param extractorFactory An {@link HlsExtractorFactory} from which to obtain the extractors for
   *     media chunks.
   * @param playlistTracker The {@link HlsPlaylistTracker} from which to obtain media playlists.
   * @param playlistUrls The {@link Uri}s of the media playlists that can be adapted between by this
   *     chunk source.
   * @param playlistFormats The {@link Format Formats} corresponding to the media playlists.
   * @param dataSourceFactory An {@link HlsDataSourceFactory} to create {@link DataSource}s for the
   *     chunks.
   * @param mediaTransferListener The transfer listener which should be informed of any media data
   *     transfers. May be null if no listener is available.
   * @param timestampAdjusterProvider A provider of {@link TimestampAdjuster} instances. If multiple
   *     {@link HlsChunkSource}s are used for a single playback, they should all share the same
   *     provider.
   * @param muxedCaptionFormats List of muxed caption {@link Format}s. Null if no closed caption
   *     information is available in the master playlist.
   */
  public HlsChunkSource(
      HlsExtractorFactory extractorFactory,
      HlsPlaylistTracker playlistTracker,
      Uri[] playlistUrls,
      Format[] playlistFormats,
      HlsDataSourceFactory dataSourceFactory,
      @Nullable TransferListener mediaTransferListener,
      TimestampAdjusterProvider timestampAdjusterProvider,
      List<Format> muxedCaptionFormats) {
    this.extractorFactory = extractorFactory;
    this.playlistTracker = playlistTracker;
    this.playlistUrls = playlistUrls;
    this.playlistFormats = playlistFormats;
    this.timestampAdjusterProvider = timestampAdjusterProvider;
    this.muxedCaptionFormats = muxedCaptionFormats;
    keyCache = new FullSegmentEncryptionKeyCache();
    liveEdgeInPeriodTimeUs = C.TIME_UNSET;
    mediaDataSource = dataSourceFactory.createDataSource(C.DATA_TYPE_MEDIA);
    if (mediaTransferListener != null) {
      mediaDataSource.addTransferListener(mediaTransferListener);
    }
    encryptionDataSource = dataSourceFactory.createDataSource(C.DATA_TYPE_DRM);
    trackGroup = new TrackGroup(playlistFormats);
    int[] initialTrackSelection = new int[playlistUrls.length];
    for (int i = 0; i < playlistUrls.length; i++) {
      initialTrackSelection[i] = i;
    }
    trackSelection = new InitializationTrackSelection(trackGroup, initialTrackSelection);
  }

  /**
   * If the source is currently having difficulty providing chunks, then this method throws the
   * underlying error. Otherwise does nothing.
   *
   * @throws IOException The underlying error.
   */
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    }
    if (expectedPlaylistUrl != null && seenExpectedPlaylistError) {
      playlistTracker.maybeThrowPlaylistRefreshError(expectedPlaylistUrl);
    }
  }

  /**
   * Returns the track group exposed by the source.
   */
  public TrackGroup getTrackGroup() {
    return trackGroup;
  }

  /**
   * Sets the current track selection.
   *
   * @param trackSelection The {@link TrackSelection}.
   */
  public void setTrackSelection(TrackSelection trackSelection) {
    this.trackSelection = trackSelection;
  }

  /** Returns the current {@link TrackSelection}. */
  public TrackSelection getTrackSelection() {
    return trackSelection;
  }

  /**
   * Resets the source.
   */
  public void reset() {
    fatalError = null;
  }

  /**
   * Sets whether this chunk source is responsible for initializing timestamp adjusters.
   *
   * @param isTimestampMaster True if this chunk source is responsible for initializing timestamp
   *     adjusters.
   */
  public void setIsTimestampMaster(boolean isTimestampMaster) {
    this.isTimestampMaster = isTimestampMaster;
  }

  /**
   * Returns the next chunk to load.
   *
   * <p>If a chunk is available then {@link HlsChunkHolder#chunk} is set. If the end of the stream
   * has been reached then {@link HlsChunkHolder#endOfStream} is set. If a chunk is not available
   * but the end of the stream has not been reached, {@link HlsChunkHolder#playlistUrl} is set to
   * contain the {@link Uri} that refers to the playlist that needs refreshing.
   *
   * @param playbackPositionUs The current playback position relative to the period start in
   *     microseconds. If playback of the period to which this chunk source belongs has not yet
   *     started, the value will be the starting position in the period minus the duration of any
   *     media in previous periods still to be played.
   * @param loadPositionUs The current load position relative to the period start in microseconds.
   * @param queue The queue of buffered {@link HlsMediaChunk}s.
   * @param out A holder to populate.
   */
  public void getNextChunk(
      long playbackPositionUs, long loadPositionUs, List<HlsMediaChunk> queue, HlsChunkHolder out) {
    HlsMediaChunk previous = queue.isEmpty() ? null : queue.get(queue.size() - 1);
    int oldTrackIndex = previous == null ? C.INDEX_UNSET : trackGroup.indexOf(previous.trackFormat);
    long bufferedDurationUs = loadPositionUs - playbackPositionUs;
    long timeToLiveEdgeUs = resolveTimeToLiveEdgeUs(playbackPositionUs);
    if (previous != null && !independentSegments) {
      // Unless segments are known to be independent, switching tracks requires downloading
      // overlapping segments. Hence we subtract the previous segment's duration from the buffered
      // duration.
      // This may affect the live-streaming adaptive track selection logic, when we compare the
      // buffered duration to time-to-live-edge to decide whether to switch. Therefore, we subtract
      // the duration of the last loaded segment from timeToLiveEdgeUs as well.
      long subtractedDurationUs = previous.getDurationUs();
      bufferedDurationUs = Math.max(0, bufferedDurationUs - subtractedDurationUs);
      if (timeToLiveEdgeUs != C.TIME_UNSET) {
        timeToLiveEdgeUs = Math.max(0, timeToLiveEdgeUs - subtractedDurationUs);
      }
    }

    // Select the track.
    MediaChunkIterator[] mediaChunkIterators = createMediaChunkIterators(previous, loadPositionUs);
    trackSelection.updateSelectedTrack(
        playbackPositionUs, bufferedDurationUs, timeToLiveEdgeUs, queue, mediaChunkIterators);
    int selectedTrackIndex = trackSelection.getSelectedIndexInTrackGroup();

    boolean switchingTrack = oldTrackIndex != selectedTrackIndex;
    Uri selectedPlaylistUrl = playlistUrls[selectedTrackIndex];
    if (!playlistTracker.isSnapshotValid(selectedPlaylistUrl)) {
      out.playlistUrl = selectedPlaylistUrl;
      seenExpectedPlaylistError &= selectedPlaylistUrl.equals(expectedPlaylistUrl);
      expectedPlaylistUrl = selectedPlaylistUrl;
      // Retry when playlist is refreshed.
      return;
    }
    HlsMediaPlaylist mediaPlaylist =
        playlistTracker.getPlaylistSnapshot(selectedPlaylistUrl, /* isForPlayback= */ true);
    independentSegments = mediaPlaylist.hasIndependentSegments;

    updateLiveEdgeTimeUs(mediaPlaylist);

    // Select the chunk.
    long startOfPlaylistInPeriodUs =
        mediaPlaylist.startTimeUs - playlistTracker.getInitialStartTimeUs();
    long chunkMediaSequence =
        getChunkMediaSequence(
            previous, switchingTrack, mediaPlaylist, startOfPlaylistInPeriodUs, loadPositionUs);
    if (chunkMediaSequence < mediaPlaylist.mediaSequence && previous != null && switchingTrack) {
        // We try getting the next chunk without adapting in case that's the reason for falling
        // behind the live window.
        selectedTrackIndex = oldTrackIndex;
        selectedPlaylistUrl = playlistUrls[selectedTrackIndex];
        mediaPlaylist =
            playlistTracker.getPlaylistSnapshot(selectedPlaylistUrl, /* isForPlayback= */ true);
        startOfPlaylistInPeriodUs =
            mediaPlaylist.startTimeUs - playlistTracker.getInitialStartTimeUs();
        chunkMediaSequence = previous.getNextChunkIndex();
    }

    if (chunkMediaSequence < mediaPlaylist.mediaSequence) {
      fatalError = new BehindLiveWindowException();
      return;
    }

    int segmentIndexInPlaylist = (int) (chunkMediaSequence - mediaPlaylist.mediaSequence);
    if (segmentIndexInPlaylist >= mediaPlaylist.segments.size()) {
      if (mediaPlaylist.hasEndTag) {
        out.endOfStream = true;
      } else /* Live */ {
        out.playlistUrl = selectedPlaylistUrl;
        seenExpectedPlaylistError &= selectedPlaylistUrl.equals(expectedPlaylistUrl);
        expectedPlaylistUrl = selectedPlaylistUrl;
      }
      return;
    }
    // We have a valid playlist snapshot, we can discard any playlist errors at this point.
    seenExpectedPlaylistError = false;
    expectedPlaylistUrl = null;

    // Handle encryption.
    HlsMediaPlaylist.Segment segment = mediaPlaylist.segments.get(segmentIndexInPlaylist);

    // Check if the segment or its initialization segment are fully encrypted.
    Uri initSegmentKeyUri = getFullEncryptionKeyUri(mediaPlaylist, segment.initializationSegment);
    out.chunk = maybeCreateEncryptionChunkFor(initSegmentKeyUri, selectedTrackIndex);
    if (out.chunk != null) {
      return;
    }
    Uri mediaSegmentKeyUri = getFullEncryptionKeyUri(mediaPlaylist, segment);
    out.chunk = maybeCreateEncryptionChunkFor(mediaSegmentKeyUri, selectedTrackIndex);
    if (out.chunk != null) {
      return;
    }

    out.chunk =
        HlsMediaChunk.createInstance(
            extractorFactory,
            mediaDataSource,
            playlistFormats[selectedTrackIndex],
            startOfPlaylistInPeriodUs,
            mediaPlaylist,
            segmentIndexInPlaylist,
            selectedPlaylistUrl,
            muxedCaptionFormats,
            trackSelection.getSelectionReason(),
            trackSelection.getSelectionData(),
            isTimestampMaster,
            timestampAdjusterProvider,
            previous,
            /* mediaSegmentKey= */ keyCache.get(mediaSegmentKeyUri),
            /* initSegmentKey= */ keyCache.get(initSegmentKeyUri));
  }

  /**
   * Called when the {@link HlsSampleStreamWrapper} has finished loading a chunk obtained from this
   * source.
   *
   * @param chunk The chunk whose load has been completed.
   */
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof EncryptionKeyChunk) {
      EncryptionKeyChunk encryptionKeyChunk = (EncryptionKeyChunk) chunk;
      scratchSpace = encryptionKeyChunk.getDataHolder();
      keyCache.put(encryptionKeyChunk.dataSpec.uri, encryptionKeyChunk.getResult());
    }
  }

  /**
   * Attempts to blacklist the track associated with the given chunk. Blacklisting will fail if the
   * track is the only non-blacklisted track in the selection.
   *
   * @param chunk The chunk whose load caused the blacklisting attempt.
   * @param blacklistDurationMs The number of milliseconds for which the track selection should be
   *     blacklisted.
   * @return Whether the blacklisting succeeded.
   */
  public boolean maybeBlacklistTrack(Chunk chunk, long blacklistDurationMs) {
    return trackSelection.blacklist(
        trackSelection.indexOf(trackGroup.indexOf(chunk.trackFormat)), blacklistDurationMs);
  }

  /**
   * Called when a playlist load encounters an error.
   *
   * @param playlistUrl The {@link Uri} of the playlist whose load encountered an error.
   * @param blacklistDurationMs The duration for which the playlist should be blacklisted. Or {@link
   *     C#TIME_UNSET} if the playlist should not be blacklisted.
   * @return True if blacklisting did not encounter errors. False otherwise.
   */
  public boolean onPlaylistError(Uri playlistUrl, long blacklistDurationMs) {
    int trackGroupIndex = C.INDEX_UNSET;
    for (int i = 0; i < playlistUrls.length; i++) {
      if (playlistUrls[i].equals(playlistUrl)) {
        trackGroupIndex = i;
        break;
      }
    }
    if (trackGroupIndex == C.INDEX_UNSET) {
      return true;
    }
    int trackSelectionIndex = trackSelection.indexOf(trackGroupIndex);
    if (trackSelectionIndex == C.INDEX_UNSET) {
      return true;
    }
    seenExpectedPlaylistError |= playlistUrl.equals(expectedPlaylistUrl);
    return blacklistDurationMs == C.TIME_UNSET
        || trackSelection.blacklist(trackSelectionIndex, blacklistDurationMs);
  }

  /**
   * Returns an array of {@link MediaChunkIterator}s for upcoming media chunks.
   *
   * @param previous The previous media chunk. May be null.
   * @param loadPositionUs The position at which the iterators will start.
   * @return Array of {@link MediaChunkIterator}s for each track.
   */
  public MediaChunkIterator[] createMediaChunkIterators(
      @Nullable HlsMediaChunk previous, long loadPositionUs) {
    int oldTrackIndex = previous == null ? C.INDEX_UNSET : trackGroup.indexOf(previous.trackFormat);
    MediaChunkIterator[] chunkIterators = new MediaChunkIterator[trackSelection.length()];
    for (int i = 0; i < chunkIterators.length; i++) {
      int trackIndex = trackSelection.getIndexInTrackGroup(i);
      Uri playlistUrl = playlistUrls[trackIndex];
      if (!playlistTracker.isSnapshotValid(playlistUrl)) {
        chunkIterators[i] = MediaChunkIterator.EMPTY;
        continue;
      }
      HlsMediaPlaylist playlist =
          playlistTracker.getPlaylistSnapshot(playlistUrl, /* isForPlayback= */ false);
      long startOfPlaylistInPeriodUs =
          playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
      boolean switchingTrack = trackIndex != oldTrackIndex;
      long chunkMediaSequence =
          getChunkMediaSequence(
              previous, switchingTrack, playlist, startOfPlaylistInPeriodUs, loadPositionUs);
      if (chunkMediaSequence < playlist.mediaSequence) {
        chunkIterators[i] = MediaChunkIterator.EMPTY;
        continue;
      }
      int chunkIndex = (int) (chunkMediaSequence - playlist.mediaSequence);
      chunkIterators[i] =
          new HlsMediaPlaylistSegmentIterator(playlist, startOfPlaylistInPeriodUs, chunkIndex);
    }
    return chunkIterators;
  }

  // Private methods.

  /**
   * Returns the media sequence number of the segment to load next in {@code mediaPlaylist}.
   *
   * @param previous The last (at least partially) loaded segment.
   * @param switchingTrack Whether the segment to load is not preceded by a segment in the same
   *     track.
   * @param mediaPlaylist The media playlist to which the segment to load belongs.
   * @param startOfPlaylistInPeriodUs The start of {@code mediaPlaylist} relative to the period
   *     start in microseconds.
   * @param loadPositionUs The current load position relative to the period start in microseconds.
   * @return The media sequence of the segment to load.
   */
  private long getChunkMediaSequence(
      @Nullable HlsMediaChunk previous,
      boolean switchingTrack,
      HlsMediaPlaylist mediaPlaylist,
      long startOfPlaylistInPeriodUs,
      long loadPositionUs) {
    if (previous == null || switchingTrack) {
      long endOfPlaylistInPeriodUs = startOfPlaylistInPeriodUs + mediaPlaylist.durationUs;
      long targetPositionInPeriodUs =
          (previous == null || independentSegments) ? loadPositionUs : previous.startTimeUs;
      if (!mediaPlaylist.hasEndTag && targetPositionInPeriodUs >= endOfPlaylistInPeriodUs) {
        // If the playlist is too old to contain the chunk, we need to refresh it.
        return mediaPlaylist.mediaSequence + mediaPlaylist.segments.size();
      }
      long targetPositionInPlaylistUs = targetPositionInPeriodUs - startOfPlaylistInPeriodUs;
      return Util.binarySearchFloor(
              mediaPlaylist.segments,
              /* value= */ targetPositionInPlaylistUs,
              /* inclusive= */ true,
              /* stayInBounds= */ !playlistTracker.isLive() || previous == null)
          + mediaPlaylist.mediaSequence;
    }
    // We ignore the case of previous not having loaded completely, in which case we load the next
    // segment.
    return previous.getNextChunkIndex();
  }

  private long resolveTimeToLiveEdgeUs(long playbackPositionUs) {
    final boolean resolveTimeToLiveEdgePossible = liveEdgeInPeriodTimeUs != C.TIME_UNSET;
    return resolveTimeToLiveEdgePossible
        ? liveEdgeInPeriodTimeUs - playbackPositionUs
        : C.TIME_UNSET;
  }

  private void updateLiveEdgeTimeUs(HlsMediaPlaylist mediaPlaylist) {
    liveEdgeInPeriodTimeUs =
        mediaPlaylist.hasEndTag
            ? C.TIME_UNSET
            : (mediaPlaylist.getEndTimeUs() - playlistTracker.getInitialStartTimeUs());
  }

  @Nullable
  private Chunk maybeCreateEncryptionChunkFor(@Nullable Uri keyUri, int selectedTrackIndex) {
    if (keyUri == null) {
      return null;
    }
    if (keyCache.containsKey(keyUri)) {
      // The key is present in the key cache. We re-insert it to prevent it from being evicted by
      // the following key addition. Note that removal of the key is necessary to affect the
      // eviction order.
      keyCache.put(keyUri, keyCache.remove(keyUri));
      return null;
    }
    DataSpec dataSpec = new DataSpec(keyUri, 0, C.LENGTH_UNSET, null, DataSpec.FLAG_ALLOW_GZIP);
    return new EncryptionKeyChunk(
        encryptionDataSource,
        dataSpec,
        playlistFormats[selectedTrackIndex],
        trackSelection.getSelectionReason(),
        trackSelection.getSelectionData(),
        scratchSpace);
  }

  @Nullable
  private static Uri getFullEncryptionKeyUri(HlsMediaPlaylist playlist, @Nullable Segment segment) {
    if (segment == null || segment.fullSegmentEncryptionKeyUri == null) {
      return null;
    }
    return UriUtil.resolveToUri(playlist.baseUri, segment.fullSegmentEncryptionKeyUri);
  }

  // Private classes.

  /**
   * A {@link TrackSelection} to use for initialization.
   */
  private static final class InitializationTrackSelection extends BaseTrackSelection {

    private int selectedIndex;

    public InitializationTrackSelection(TrackGroup group, int[] tracks) {
      super(group, tracks);
      selectedIndex = indexOf(group.getFormat(0));
    }

    @Override
    public void updateSelectedTrack(
        long playbackPositionUs,
        long bufferedDurationUs,
        long availableDurationUs,
        List<? extends MediaChunk> queue,
        MediaChunkIterator[] mediaChunkIterators) {
      long nowMs = SystemClock.elapsedRealtime();
      if (!isBlacklisted(selectedIndex, nowMs)) {
        return;
      }
      // Try from lowest bitrate to highest.
      for (int i = length - 1; i >= 0; i--) {
        if (!isBlacklisted(i, nowMs)) {
          selectedIndex = i;
          return;
        }
      }
      // Should never happen.
      throw new IllegalStateException();
    }

    @Override
    public int getSelectedIndex() {
      return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
      return C.SELECTION_REASON_UNKNOWN;
    }

    @Override
    public Object getSelectionData() {
      return null;
    }

  }

  private static final class EncryptionKeyChunk extends DataChunk {

    private byte[] result;

    public EncryptionKeyChunk(
        DataSource dataSource,
        DataSpec dataSpec,
        Format trackFormat,
        int trackSelectionReason,
        Object trackSelectionData,
        byte[] scratchSpace) {
      super(dataSource, dataSpec, C.DATA_TYPE_DRM, trackFormat, trackSelectionReason,
          trackSelectionData, scratchSpace);
    }

    @Override
    protected void consume(byte[] data, int limit) {
      result = Arrays.copyOf(data, limit);
    }

    public byte[] getResult() {
      return result;
    }

  }

  /** {@link MediaChunkIterator} wrapping a {@link HlsMediaPlaylist}. */
  private static final class HlsMediaPlaylistSegmentIterator extends BaseMediaChunkIterator {

    private final HlsMediaPlaylist playlist;
    private final long startOfPlaylistInPeriodUs;

    /**
     * Creates iterator.
     *
     * @param playlist The {@link HlsMediaPlaylist} to wrap.
     * @param startOfPlaylistInPeriodUs The start time of the playlist in the period, in
     *     microseconds.
     * @param chunkIndex The index of the first available chunk in the playlist.
     */
    public HlsMediaPlaylistSegmentIterator(
        HlsMediaPlaylist playlist, long startOfPlaylistInPeriodUs, int chunkIndex) {
      super(/* fromIndex= */ chunkIndex, /* toIndex= */ playlist.segments.size() - 1);
      this.playlist = playlist;
      this.startOfPlaylistInPeriodUs = startOfPlaylistInPeriodUs;
    }

    @Override
    public DataSpec getDataSpec() {
      checkInBounds();
      Segment segment = playlist.segments.get((int) getCurrentIndex());
      Uri chunkUri = UriUtil.resolveToUri(playlist.baseUri, segment.url);
      return new DataSpec(
          chunkUri, segment.byterangeOffset, segment.byterangeLength, /* key= */ null);
    }

    @Override
    public long getChunkStartTimeUs() {
      checkInBounds();
      Segment segment = playlist.segments.get((int) getCurrentIndex());
      return startOfPlaylistInPeriodUs + segment.relativeStartTimeUs;
    }

    @Override
    public long getChunkEndTimeUs() {
      checkInBounds();
      Segment segment = playlist.segments.get((int) getCurrentIndex());
      long segmentStartTimeInPeriodUs = startOfPlaylistInPeriodUs + segment.relativeStartTimeUs;
      return segmentStartTimeInPeriodUs + segment.durationUs;
    }
  }

  /**
   * LRU cache that holds up to {@link #KEY_CACHE_SIZE} full-segment-encryption keys. Which each
   * addition, once the cache's size exceeds {@link #KEY_CACHE_SIZE}, the oldest item (according to
   * insertion order) is removed.
   */
  private static final class FullSegmentEncryptionKeyCache extends LinkedHashMap<Uri, byte[]> {

    public FullSegmentEncryptionKeyCache() {
      super(
          /* initialCapacity= */ KEY_CACHE_SIZE * 2, /* loadFactor= */ 1, /* accessOrder= */ false);
    }

    @Override
    public byte[] get(Object keyUri) {
      if (keyUri == null) {
        return null;
      }
      return super.get(keyUri);
    }

    @Override
    public byte[] put(Uri keyUri, byte[] key) {
      return super.put(keyUri, Assertions.checkNotNull(key));
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Uri, byte[]> entry) {
      return size() > KEY_CACHE_SIZE;
    }
  }
}
