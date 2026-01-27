package com.google.android.exoplayer2.source.sabr;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.rawcc.RawCcExtractor;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.ContainerMediaChunk;
import com.google.android.exoplayer2.source.chunk.InitializationChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.source.sabr.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import com.google.android.exoplayer2.source.sabr.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.sabr.manifest.RangedUri;
import com.google.android.exoplayer2.source.sabr.manifest.Representation;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.source.sabr.parser.adapter.SabrFragmentedMp4Adapter;
import com.google.android.exoplayer2.source.sabr.parser.adapter.SabrMatroskaAdapter;
import com.google.android.exoplayer2.source.sabr.parser.core.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.models.AudioSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.CaptionSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.parser.models.VideoSelector;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultSabrChunkSource implements SabrChunkSource {
    public static final class Factory implements SabrChunkSource.Factory {

        private final DataSource.Factory dataSourceFactory;
        private final int maxSegmentsPerLoad;

        public Factory(DataSource.Factory dataSourceFactory) {
            this(dataSourceFactory, 1);
        }

        public Factory(DataSource.Factory dataSourceFactory, int maxSegmentsPerLoad) {
            this.dataSourceFactory = dataSourceFactory;
            this.maxSegmentsPerLoad = maxSegmentsPerLoad;
        }

        @Override
        public SabrChunkSource createSabrChunkSource(
                LoaderErrorThrower manifestLoaderErrorThrower,
                SabrManifest manifest,
                int periodIndex,
                int[] adaptationSetIndices,
                TrackSelection trackSelection,
                int trackType,
                long elapsedRealtimeOffsetMs,
                boolean enableEventMessageTrack,
                List<Format> closedCaptionFormats,
                @Nullable PlayerTrackEmsgHandler playerEmsgHandler,
                @Nullable TransferListener transferListener) {
            DataSource dataSource = dataSourceFactory.createDataSource();
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return new DefaultSabrChunkSource(
                    manifestLoaderErrorThrower,
                    manifest,
                    periodIndex,
                    adaptationSetIndices,
                    trackSelection,
                    trackType,
                    dataSource,
                    elapsedRealtimeOffsetMs,
                    maxSegmentsPerLoad,
                    enableEventMessageTrack,
                    closedCaptionFormats,
                    playerEmsgHandler);
        }

    }

    private static final String TAG = DefaultSabrChunkSource.class.getSimpleName();
    private final LoaderErrorThrower manifestLoaderErrorThrower;
    private final int[] adaptationSetIndices;
    private final int trackType;
    private final DataSource dataSource;
    private final long elapsedRealtimeOffsetMs;
    private final int maxSegmentsPerLoad;
    @Nullable private final PlayerTrackEmsgHandler playerTrackEmsgHandler;

    protected final RepresentationHolder[] representationHolders;

    private TrackSelection trackSelection;
    private FormatSelector formatSelector;
    private SabrManifest manifest;
    private int periodIndex;
    private IOException fatalError;
    private boolean missingLastSegment;
    private long liveEdgeTimeUs;

    private final SabrStream sabrStream;
    private final Map<String, String> sabrHeaders;
    private int nexChunkIdx = -1;

    /**
     * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
     * @param manifest The initial manifest.
     * @param periodIndex The index of the period in the manifest.
     * @param adaptationSetIndices The indices of the adaptation sets in the period.
     * @param trackSelection The track selection.
     * @param trackType The type of the tracks in the selection.
     * @param dataSource A {@link DataSource} suitable for loading the media data.
     * @param elapsedRealtimeOffsetMs If known, an estimate of the instantaneous difference between
     *     server-side unix time and {@link SystemClock#elapsedRealtime()} in milliseconds, specified
     *     as the server's unix time minus the local elapsed time. If unknown, set to 0.
     * @param maxSegmentsPerLoad The maximum number of segments to combine into a single request. Note
     *     that segments will only be combined if their {@link Uri}s are the same and if their data
     *     ranges are adjacent.
     * @param enableEventMessageTrack Whether to output an event message track.
     * @param closedCaptionFormats The {@link Format Formats} of closed caption tracks to be output.
     * @param playerTrackEmsgHandler The {@link PlayerTrackEmsgHandler} instance to handle emsg
     *     messages targeting the player. Maybe null if this is not necessary.
     */
    public DefaultSabrChunkSource(
            LoaderErrorThrower manifestLoaderErrorThrower,
            SabrManifest manifest,
            int periodIndex,
            int[] adaptationSetIndices,
            TrackSelection trackSelection,
            int trackType,
            DataSource dataSource,
            long elapsedRealtimeOffsetMs,
            int maxSegmentsPerLoad,
            boolean enableEventMessageTrack,
            List<Format> closedCaptionFormats,
            @Nullable PlayerTrackEmsgHandler playerTrackEmsgHandler) {
        this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
        this.manifest = manifest;
        this.adaptationSetIndices = adaptationSetIndices;
        this.trackSelection = trackSelection;
        this.formatSelector = createFormatSelector(trackType, trackSelection);
        this.trackType = trackType;
        this.dataSource = dataSource;
        this.periodIndex = periodIndex;
        this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
        this.maxSegmentsPerLoad = maxSegmentsPerLoad;
        this.playerTrackEmsgHandler = playerTrackEmsgHandler;

        long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
        liveEdgeTimeUs = C.TIME_UNSET;
        
        this.sabrStream = manifest.getSabrStream(trackType);
        //this.sabrStream.setAudioSelection(createAudioSelection(trackType, trackSelection));
        //this.sabrStream.setVideoSelection(createVideoSelection(trackType, trackSelection));
        //this.sabrStream.setCaptionSelection(createCaptionSelection(trackType, trackSelection));
        this.sabrStream.setFormatSelector(formatSelector);

        sabrHeaders = new HashMap<>();
        sabrHeaders.put("Content-Type", "application/x-protobuf");
        //sabrHeaders.put("Accept-Encoding", "identity");
        sabrHeaders.put("Accept", "application/vnd.yt-ump");

        List<Representation> representations = getRepresentations();
        representationHolders = new RepresentationHolder[trackSelection.length()];
        for (int i = 0; i < representationHolders.length; i++) {
            Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
            representationHolders[i] =
                    new RepresentationHolder(
                            periodDurationUs,
                            trackType,
                            representation,
                            enableEventMessageTrack,
                            closedCaptionFormats,
                            playerTrackEmsgHandler,
                            sabrStream);
        }
    }

    @Override
    public void updateManifest(SabrManifest newManifest, int newPeriodIndex) {
        try {
            manifest = newManifest;
            periodIndex = newPeriodIndex;
            long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
            List<Representation> representations = getRepresentations();
            for (int i = 0; i < representationHolders.length; i++) {
                Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
                representationHolders[i] =
                        representationHolders[i].copyWithNewRepresentation(periodDurationUs, representation);
            }
        } catch (BehindLiveWindowException e) {
            fatalError = e;
        }
    }

    @Override
    public void updateTrackSelection(TrackSelection trackSelection) {
        this.trackSelection = trackSelection;
        this.formatSelector = createFormatSelector(trackType, trackSelection);
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        // Segments are aligned across representations, so any segment index will do.
        //for (RepresentationHolder representationHolder : representationHolders) {
        //    if (representationHolder.segmentIndex != null) {
        //        long segmentNum = representationHolder.getSegmentNum(positionUs);
        //        long firstSyncUs = representationHolder.getSegmentStartTimeUs(segmentNum);
        //        long secondSyncUs =
        //                firstSyncUs < positionUs && segmentNum < representationHolder.getSegmentCount() - 1
        //                        ? representationHolder.getSegmentStartTimeUs(segmentNum + 1)
        //                        : firstSyncUs;
        //        return Util.resolveSeekPositionUs(positionUs, seekParameters, firstSyncUs, secondSyncUs);
        //    }
        //}
        // We don't have a segment index to adjust the seek position with yet.
        return positionUs;
    }

    @Override
    public void maybeThrowError() throws IOException {
        if (fatalError != null) {
            throw fatalError;
        } else {
            manifestLoaderErrorThrower.maybeThrowError();
        }
    }

    @Override
    public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        if (fatalError != null || trackSelection.length() < 2) {
            return queue.size();
        }
        return trackSelection.evaluateQueueSize(playbackPositionUs, queue);
    }

    @Override
    public void getNextChunk(long playbackPositionUs, long loadPositionUs, List<? extends MediaChunk> queue, ChunkHolder out) {
        if (fatalError != null) {
            return;
        }

        long bufferedDurationUs = loadPositionUs - playbackPositionUs;
        long timeToLiveEdgeUs = resolveTimeToLiveEdgeUs(playbackPositionUs);
        long presentationPositionUs =
                C.msToUs(manifest.availabilityStartTimeMs)
                        + C.msToUs(manifest.getPeriod(periodIndex).startMs)
                        + loadPositionUs;

        if (playerTrackEmsgHandler != null
                && playerTrackEmsgHandler.maybeRefreshManifestBeforeLoadingNextChunk(
                presentationPositionUs)) {
            return;
        }

        long nowUnixTimeUs = getNowUnixTimeUs();
        MediaChunk previous = queue.isEmpty() ? null : queue.get(queue.size() - 1);
        MediaChunkIterator[] chunkIterators = new MediaChunkIterator[trackSelection.length()];
        //for (int i = 0; i < chunkIterators.length; i++) {
        //    RepresentationHolder representationHolder = representationHolders[i];
        //    if (representationHolder.segmentIndex == null) {
        //        chunkIterators[i] = MediaChunkIterator.EMPTY;
        //    } else {
        //        long firstAvailableSegmentNum =
        //                representationHolder.getFirstAvailableSegmentNum(manifest, periodIndex, nowUnixTimeUs);
        //        long lastAvailableSegmentNum =
        //                representationHolder.getLastAvailableSegmentNum(manifest, periodIndex, nowUnixTimeUs);
        //        long segmentNum =
        //                getSegmentNum(
        //                        representationHolder,
        //                        previous,
        //                        loadPositionUs,
        //                        firstAvailableSegmentNum,
        //                        lastAvailableSegmentNum);
        //        if (segmentNum < firstAvailableSegmentNum) {
        //            chunkIterators[i] = MediaChunkIterator.EMPTY;
        //        } else {
        //            chunkIterators[i] =
        //                    new RepresentationSegmentIterator(
        //                            representationHolder, segmentNum, lastAvailableSegmentNum);
        //        }
        //    }
        //}

        trackSelection.updateSelectedTrack(
                playbackPositionUs, bufferedDurationUs, timeToLiveEdgeUs, queue, chunkIterators);

        RepresentationHolder representationHolder =
                representationHolders[trackSelection.getSelectedIndex()];

        if (representationHolder.extractorWrapper != null) {
            Representation selectedRepresentation = representationHolder.representation;
            RangedUri pendingInitializationUri = null;
            RangedUri pendingIndexUri = null;
            if (representationHolder.extractorWrapper.getSampleFormats() == null) {
                pendingInitializationUri = selectedRepresentation.getInitializationUri();
            }
            // No segment index in SABR
            //if (representationHolder.segmentIndex == null) {
            //    pendingIndexUri = selectedRepresentation.getIndexUri();
            //}
            //if (pendingInitializationUri != null || pendingIndexUri != null) {
            //if (pendingInitializationUri != null) {
            //    // We have initialization and/or index requests to make.
            //    out.chunk = newInitializationChunk(representationHolder, dataSource,
            //            trackSelection.getSelectedFormat(), trackSelection.getSelectionReason(),
            //            trackSelection.getSelectionData(), pendingInitializationUri, pendingIndexUri);
            //    return;
            //}
        }

        long periodDurationUs = representationHolder.periodDurationUs;
        boolean periodEnded = periodDurationUs != C.TIME_UNSET;

        //if (representationHolder.getSegmentCount() == 0) {
        //    // The index doesn't define any segments.
        //    out.endOfStream = periodEnded;
        //    return;
        //}

        //long firstAvailableSegmentNum =
        //        representationHolder.getFirstAvailableSegmentNum(manifest, periodIndex, nowUnixTimeUs);
        //long lastAvailableSegmentNum =
        //        representationHolder.getLastAvailableSegmentNum(manifest, periodIndex, nowUnixTimeUs);

        //updateLiveEdgeTimeUs(representationHolder, lastAvailableSegmentNum);

        //long segmentNum =
        //        getSegmentNum(
        //                representationHolder,
        //                previous,
        //                loadPositionUs,
        //                firstAvailableSegmentNum,
        //                lastAvailableSegmentNum);
        //if (segmentNum < firstAvailableSegmentNum) {
        //    // This is before the first chunk in the current manifest.
        //    fatalError = new BehindLiveWindowException();
        //    return;
        //}
        //
        //if (segmentNum > lastAvailableSegmentNum
        //        || (missingLastSegment && segmentNum >= lastAvailableSegmentNum)) {
        //    // The segment is beyond the end of the period.
        //    out.endOfStream = periodEnded;
        //    return;
        //}

        //if (periodEnded && representationHolder.getSegmentStartTimeUs(segmentNum) >= periodDurationUs) {
        //    // The period duration clips the period to a position before the segment.
        //    out.endOfStream = true;
        //    return;
        //}

        //int maxSegmentCount =
        //        (int) Math.min(maxSegmentsPerLoad, lastAvailableSegmentNum - segmentNum + 1);
        //if (periodDurationUs != C.TIME_UNSET) {
        //    while (maxSegmentCount > 1
        //            && representationHolder.getSegmentStartTimeUs(segmentNum + maxSegmentCount - 1)
        //            >= periodDurationUs) {
        //        // The period duration clips the period to a position before the last segment in the range
        //        // [segmentNum, segmentNum + maxSegmentCount - 1]. Reduce maxSegmentCount.
        //        maxSegmentCount--;
        //    }
        //}

        long seekTimeUs = queue.isEmpty() ? loadPositionUs : C.TIME_UNSET;
        out.chunk =
                newMediaChunk(
                        representationHolder,
                        dataSource,
                        trackType,
                        trackSelection.getSelectedFormat(),
                        trackSelection.getSelectionReason(),
                        trackSelection.getSelectionData(),
                        C.INDEX_UNSET, // TODO: does sabr has segment num?
                        //segmentNum,
                        //maxSegmentCount,
                        seekTimeUs);
    }

    @Override
    public void onChunkLoadCompleted(Chunk chunk) {
        if (chunk instanceof InitializationChunk) {
            InitializationChunk initializationChunk = (InitializationChunk) chunk;
            int trackIndex = trackSelection.indexOf(initializationChunk.trackFormat);
            RepresentationHolder representationHolder = representationHolders[trackIndex];
            // The null check avoids overwriting an index obtained from the manifest with one obtained
            // from the stream. If the manifest defines an index then the stream shouldn't, but in cases
            // where it does we should ignore it.
            //if (representationHolder.segmentIndex == null) {
            //    SeekMap seekMap = representationHolder.extractorWrapper.getSeekMap();
            //    if (seekMap != null) {
            //        representationHolders[trackIndex] =
            //                representationHolder.copyWithNewSegmentIndex(
            //                        new SabrWrappingSegmentIndex(
            //                                (ChunkIndex) seekMap,
            //                                representationHolder.representation.presentationTimeOffsetUs));
            //    }
            //}
        }
        if (playerTrackEmsgHandler != null) {
            playerTrackEmsgHandler.onChunkLoadCompleted(chunk);
        }
    }

    @Override
    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e, long blacklistDurationMs) {
        Log.e(TAG, "Chunk load failed: " + e.getMessage());
        if (!cancelable) {
            return false;
        }
        if (playerTrackEmsgHandler != null
                && playerTrackEmsgHandler.maybeRefreshManifestOnLoadingError(chunk)) {
            return true;
        }
        // Workaround for missing segment at the end of the period
        //if (!manifest.dynamic && chunk instanceof MediaChunk
        //        && e instanceof InvalidResponseCodeException
        //        && ((InvalidResponseCodeException) e).responseCode == 404) {
        //    RepresentationHolder representationHolder =
        //            representationHolders[trackSelection.indexOf(chunk.trackFormat)];
        //    int segmentCount = representationHolder.getSegmentCount();
        //    if (segmentCount != SabrSegmentIndex.INDEX_UNBOUNDED && segmentCount != 0) {
        //        long lastAvailableSegmentNum = representationHolder.getFirstSegmentNum() + segmentCount - 1;
        //        if (((MediaChunk) chunk).getNextChunkIndex() > lastAvailableSegmentNum) {
        //            missingLastSegment = true;
        //            return true;
        //        }
        //    }
        //}
        return blacklistDurationMs != C.TIME_UNSET
                && trackSelection.blacklist(trackSelection.indexOf(chunk.trackFormat), blacklistDurationMs);
    }

    private ArrayList<Representation> getRepresentations() {
        List<AdaptationSet> manifestAdaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        ArrayList<Representation> representations = new ArrayList<>();
        for (int adaptationSetIndex : adaptationSetIndices) {
            representations.addAll(manifestAdaptationSets.get(adaptationSetIndex).representations);
        }
        return representations;
    }

    private long resolveTimeToLiveEdgeUs(long playbackPositionUs) {
        boolean resolveTimeToLiveEdgePossible = manifest.dynamic && liveEdgeTimeUs != C.TIME_UNSET;
        return resolveTimeToLiveEdgePossible ? liveEdgeTimeUs - playbackPositionUs : C.TIME_UNSET;
    }

    private long getNowUnixTimeUs() {
        if (elapsedRealtimeOffsetMs != 0) {
            return (SystemClock.elapsedRealtime() + elapsedRealtimeOffsetMs) * 1000;
        } else {
            return System.currentTimeMillis() * 1000;
        }
    }

    //private long getSegmentNum(
    //        RepresentationHolder representationHolder,
    //        @Nullable MediaChunk previousChunk,
    //        long loadPositionUs,
    //        long firstAvailableSegmentNum,
    //        long lastAvailableSegmentNum) {
    //    return previousChunk != null
    //            ? previousChunk.getNextChunkIndex()
    //            : Util.constrainValue(
    //            representationHolder.getSegmentNum(loadPositionUs),
    //            firstAvailableSegmentNum,
    //            lastAvailableSegmentNum);
    //}

    //private void updateLiveEdgeTimeUs(
    //        RepresentationHolder representationHolder, long lastAvailableSegmentNum) {
    //    liveEdgeTimeUs = manifest.dynamic
    //            ? representationHolder.getSegmentEndTimeUs(lastAvailableSegmentNum) : C.TIME_UNSET;
    //}

    protected Chunk newInitializationChunk(
            RepresentationHolder representationHolder,
            DataSource dataSource,
            Format trackFormat,
            int trackSelectionReason,
            Object trackSelectionData,
            RangedUri initializationUri,
            RangedUri indexUri) {
        //RangedUri requestUri;
        //String baseUrl = representationHolder.representation.baseUrl;
        //baseUrl = Utils.updateQuery(baseUrl, "rn", sabrStream.getIncSabrRequestNumber());
        //if (initializationUri != null) {
        //    // It's common for initialization and index data to be stored adjacently. Attempt to merge
        //    // the two requests together to request both at once.
        //    requestUri = initializationUri.attemptMerge(indexUri, baseUrl);
        //    if (requestUri == null) {
        //        requestUri = initializationUri;
        //    }
        //} else {
        //    requestUri = indexUri;
        //}
        // NOTE: first protobuf request (before the video start off)
        // MOD: add protobuf data
        //DataSpec dataSpec = new DataSpec(requestUri.resolveUri(baseUrl), requestUri.start,
        //        requestUri.length, representationHolder.representation.getCacheKey());
        DataSpec dataSpec = new DataSpec(
                //Uri.parse(sabrStream.getRequestUrl()),
                Uri.parse(manifest.getRequestUrl(trackType)),
                DataSpec.HTTP_METHOD_POST,
                //sabrStream.createVideoPlaybackAbrRequest(trackType, true).toByteArray(),
                manifest.createVideoPlaybackAbrRequest(trackType, true).toByteArray(),
                0, 0, C.LENGTH_UNSET,
                //requestUri.start,
                //requestUri.start,
                //requestUri.length,
                representationHolder.representation.getCacheKey(),
                0,
                sabrHeaders);
        Log.e(TAG, "Load init chunk: track=" + trackType + ", rn=" + manifest.getSabrRequestNumber());
        return new InitializationChunk(dataSource, dataSpec, trackFormat,
                trackSelectionReason, trackSelectionData, representationHolder.extractorWrapper);
    }

    protected Chunk newMediaChunk(
            RepresentationHolder representationHolder,
            DataSource dataSource,
            int trackType,
            Format trackFormat,
            int trackSelectionReason,
            Object trackSelectionData,
            long firstSegmentNum,
            //int maxSegmentCount,
            long seekTimeUs) {
        boolean isInit = nexChunkIdx == -1;
        FormatId formatId = formatSelector.getSelectedFormatId();
        int iTag = formatId != null ? formatId.getItag() : -1;

        if (nexChunkIdx == -1) {
            sabrStream.reset(iTag);
        }

        nexChunkIdx++;

        Representation representation = representationHolder.representation;

        //long startTimeMs = sabrStream.getSegmentStartTimeMs(trackType);
        //long durationMs = sabrStream.getSegmentDurationMs(trackType);
        long startTimeMs = sabrStream.getSegmentStartTimeMs(iTag);
        long durationMs = sabrStream.getSegmentDurationMs(iTag);
        boolean isSabrTimeSet = startTimeMs >= 0 && durationMs > 0;

        long startTimeUs = isSabrTimeSet ? startTimeMs * 1_000L : C.TIME_UNSET;
        long endTimeUs = isSabrTimeSet ? startTimeUs + (durationMs * 1_000L) : C.TIME_UNSET;
        long clippedEndTimeUs = C.TIME_UNSET;
        int segmentCount = C.INDEX_UNSET; // TODO: replace with SABR value?
        //long startTimeUs = representationHolder.getSegmentStartTimeUs(firstSegmentNum);
        //RangedUri segmentUri = representationHolder.getSegmentUrl(firstSegmentNum);
        //String baseUrl = representation.baseUrl;
        //baseUrl = Utils.updateQuery(baseUrl, "rn", sabrStream.getIncSabrRequestNumber());

        DataSpec dataSpec = new DataSpec(
                //Uri.parse(sabrStream.getRequestUrl()),
                Uri.parse(manifest.getRequestUrl(trackType)),
                //segmentUri.resolveUri(baseUrl),
                DataSpec.HTTP_METHOD_POST,
                //sabrStream.createVideoPlaybackAbrRequest(trackType, false).toByteArray(),
                manifest.createVideoPlaybackAbrRequest(trackType, isInit).toByteArray(),
                0, 0, C.LENGTH_UNSET,
                //segmentUri.start,
                //segmentUri.start,
                //segmentUri.length,
                representation.getCacheKey(),
                0,
                sabrHeaders);
        long sampleOffsetUs = -representation.presentationTimeOffsetUs;
        Log.e(TAG, "Load media chunk: track=" + trackType + ", rn=" + manifest.getSabrRequestNumber() + ", startTimeMs=" + startTimeMs);
        return new ContainerMediaChunk(
                dataSource,
                dataSpec,
                trackFormat,
                trackSelectionReason,
                trackSelectionData,
                startTimeUs,
                endTimeUs,
                seekTimeUs,
                clippedEndTimeUs,
                firstSegmentNum,
                segmentCount,
                sampleOffsetUs,
                representationHolder.extractorWrapper);

        //if (representationHolder.extractorWrapper == null) {
        //    long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum);
        //    DataSpec dataSpec = new DataSpec(segmentUri.resolveUri(baseUrl),
        //            segmentUri.start, segmentUri.length, representation.getCacheKey());
        //    return new SingleSampleMediaChunk(dataSource, dataSpec, trackFormat, trackSelectionReason,
        //            trackSelectionData, startTimeUs, endTimeUs, firstSegmentNum, trackType, trackFormat);
        //} else {
        //    int segmentCount = 1;
        //    for (int i = 1; i < maxSegmentCount; i++) {
        //        RangedUri nextSegmentUri = representationHolder.getSegmentUrl(firstSegmentNum + i);
        //        RangedUri mergedSegmentUri = segmentUri.attemptMerge(nextSegmentUri, baseUrl);
        //        if (mergedSegmentUri == null) {
        //            // Unable to merge segment fetches because the URIs do not merge.
        //            break;
        //        }
        //        segmentUri = mergedSegmentUri;
        //        segmentCount++;
        //    }
        //    long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum + segmentCount - 1);
        //    long periodDurationUs = representationHolder.periodDurationUs;
        //    long clippedEndTimeUs =
        //            periodDurationUs != C.TIME_UNSET && periodDurationUs <= endTimeUs
        //                    ? periodDurationUs
        //                    : C.TIME_UNSET;
        //    // NOTE: next protobuf requests (during the playback)
        //    // MOD: add protobuf data
        //    //DataSpec dataSpec = new DataSpec(segmentUri.resolveUri(baseUrl),
        //    //        segmentUri.start, segmentUri.length, representation.getCacheKey());
        //    int sabrRequestNumber = sabrStream.getIncSabrRequestNumber();
        //    DataSpec dataSpec = new DataSpec(
        //            segmentUri.resolveUri(baseUrl),
        //            DataSpec.HTTP_METHOD_POST,
        //            sabrStream.buildInitVideoPlaybackAbrRequest(trackType).toByteArray(),
        //            0, 0, C.LENGTH_UNSET,
        //            //segmentUri.start,
        //            //segmentUri.start,
        //            //segmentUri.length,
        //            representation.getCacheKey(),
        //            0,
        //            sabrHeaders);
        //    long sampleOffsetUs = -representation.presentationTimeOffsetUs;
        //    return new ContainerMediaChunk(
        //            dataSource,
        //            dataSpec,
        //            trackFormat,
        //            trackSelectionReason,
        //            trackSelectionData,
        //            startTimeUs,
        //            endTimeUs,
        //            seekTimeUs,
        //            clippedEndTimeUs,
        //            firstSegmentNum,
        //            segmentCount,
        //            sampleOffsetUs,
        //            representationHolder.extractorWrapper);
        //}
    }

    private static AudioSelector createAudioSelection(int trackType, TrackSelection trackSelection) {
        if (trackType != C.TRACK_TYPE_AUDIO) {
            return null;
        }

        Format selectedFormat = trackSelection.getSelectedFormat();

        return new AudioSelector("selected_audio", false, selectedFormat);
    }

    private static VideoSelector createVideoSelection(int trackType, TrackSelection trackSelection) {
        if (trackType != C.TRACK_TYPE_VIDEO) {
            return null;
        }

        Format selectedFormat = trackSelection.getSelectedFormat();

        return new VideoSelector("selected_video", false, selectedFormat);
    }

    private static CaptionSelector createCaptionSelection(int trackType, TrackSelection trackSelection) {
        if (trackType != C.TRACK_TYPE_TEXT) {
            return null;
        }

        Format selectedFormat = trackSelection.getSelectedFormat();

        return new CaptionSelector("selected_caption", false, selectedFormat);
    }

    private static FormatSelector createFormatSelector(int trackType, TrackSelection trackSelection) {
        Format selectedFormat = trackSelection.getSelectedFormat();

        switch (trackType) {
            case C.TRACK_TYPE_AUDIO:
                return new FormatSelector("selected_audio", false, selectedFormat);
            case C.TRACK_TYPE_VIDEO:
                return new FormatSelector("selected_video", false, selectedFormat);
            case C.TRACK_TYPE_TEXT:
                return new FormatSelector("selected_caption", false, selectedFormat);
        }

        throw new IllegalStateException("Unknown track type");
    }

    /** {@link MediaChunkIterator} wrapping a {@link RepresentationHolder}. */
    //protected static final class RepresentationSegmentIterator extends BaseMediaChunkIterator {
    //
    //    private final RepresentationHolder representationHolder;
    //
    //    /**
    //     * Creates iterator.
    //     *
    //     * @param representation The {@link RepresentationHolder} to wrap.
    //     * @param firstAvailableSegmentNum The number of the first available segment.
    //     * @param lastAvailableSegmentNum The number of the last available segment.
    //     */
    //    public RepresentationSegmentIterator(
    //            RepresentationHolder representation,
    //            long firstAvailableSegmentNum,
    //            long lastAvailableSegmentNum) {
    //        super(/* fromIndex= */ firstAvailableSegmentNum, /* toIndex= */ lastAvailableSegmentNum);
    //        this.representationHolder = representation;
    //    }
    //
    //    @Override
    //    public DataSpec getDataSpec() {
    //        checkInBounds();
    //        Representation representation = representationHolder.representation;
    //        RangedUri segmentUri = representationHolder.getSegmentUrl(getCurrentIndex());
    //        Uri resolvedUri = segmentUri.resolveUri(representation.baseUrl);
    //        String cacheKey = representation.getCacheKey();
    //        return new DataSpec(resolvedUri, segmentUri.start, segmentUri.length, cacheKey);
    //    }
    //
    //    @Override
    //    public long getChunkStartTimeUs() {
    //        checkInBounds();
    //        return representationHolder.getSegmentStartTimeUs(getCurrentIndex());
    //    }
    //
    //    @Override
    //    public long getChunkEndTimeUs() {
    //        checkInBounds();
    //        return representationHolder.getSegmentEndTimeUs(getCurrentIndex());
    //    }
    //}

    /** Holds information about a snapshot of a single {@link Representation}. */
    protected static final class RepresentationHolder {

        /* package */ final @Nullable ChunkExtractorWrapper extractorWrapper;

        public final Representation representation;
        //public final @Nullable SabrSegmentIndex segmentIndex;

        private final long periodDurationUs;
        private final long segmentNumShift;

        /* package */ RepresentationHolder(
                long periodDurationUs,
                int trackType,
                Representation representation,
                boolean enableEventMessageTrack,
                List<Format> closedCaptionFormats,
                TrackOutput playerEmsgTrackOutput,
                SabrStream sabrStream) {
            this(
                    periodDurationUs,
                    representation,
                    createExtractorWrapper(
                            trackType,
                            representation,
                            enableEventMessageTrack,
                            closedCaptionFormats,
                            playerEmsgTrackOutput,
                            sabrStream),
                    /* segmentNumShift= */ 0
                    //representation.getIndex()
            );
        }

        private RepresentationHolder(
                long periodDurationUs,
                Representation representation,
                @Nullable ChunkExtractorWrapper extractorWrapper,
                long segmentNumShift
                //@Nullable SabrSegmentIndex segmentIndex
        ) {
            this.periodDurationUs = periodDurationUs;
            this.representation = representation;
            this.segmentNumShift = segmentNumShift;
            this.extractorWrapper = extractorWrapper;
            //this.segmentIndex = segmentIndex;
        }

        @CheckResult
            /* package */ RepresentationHolder copyWithNewRepresentation(
                long newPeriodDurationUs, Representation newRepresentation)
                throws BehindLiveWindowException {

            return new RepresentationHolder(
                    newPeriodDurationUs, newRepresentation, extractorWrapper, segmentNumShift);

            //SabrSegmentIndex oldIndex = representation.getIndex();
            //SabrSegmentIndex newIndex = newRepresentation.getIndex();

            //if (oldIndex == null) {
            //    // Segment numbers cannot shift if the index isn't defined by the manifest.
            //    return new RepresentationHolder(
            //            newPeriodDurationUs, newRepresentation, extractorWrapper, segmentNumShift, oldIndex);
            //}
            //
            //if (!oldIndex.isExplicit()) {
            //    // Segment numbers cannot shift if the index isn't explicit.
            //    return new RepresentationHolder(
            //            newPeriodDurationUs, newRepresentation, extractorWrapper, segmentNumShift, newIndex);
            //}
            //
            //int oldIndexSegmentCount = oldIndex.getSegmentCount(newPeriodDurationUs);
            //if (oldIndexSegmentCount == 0) {
            //    // Segment numbers cannot shift if the old index was empty.
            //    return new RepresentationHolder(
            //            newPeriodDurationUs, newRepresentation, extractorWrapper, segmentNumShift, newIndex);
            //}
            //
            //long oldIndexFirstSegmentNum = oldIndex.getFirstSegmentNum();
            //long oldIndexStartTimeUs = oldIndex.getTimeUs(oldIndexFirstSegmentNum);
            //long oldIndexLastSegmentNum = oldIndexFirstSegmentNum + oldIndexSegmentCount - 1;
            //long oldIndexEndTimeUs =
            //        oldIndex.getTimeUs(oldIndexLastSegmentNum)
            //                + oldIndex.getDurationUs(oldIndexLastSegmentNum, newPeriodDurationUs);
            //long newIndexFirstSegmentNum = newIndex.getFirstSegmentNum();
            //long newIndexStartTimeUs = newIndex.getTimeUs(newIndexFirstSegmentNum);
            //long newSegmentNumShift = segmentNumShift;
            //if (oldIndexEndTimeUs == newIndexStartTimeUs) {
            //    // The new index continues where the old one ended, with no overlap.
            //    newSegmentNumShift += oldIndexLastSegmentNum + 1 - newIndexFirstSegmentNum;
            //} else if (oldIndexEndTimeUs < newIndexStartTimeUs) {
            //    // There's a gap between the old index and the new one which means we've slipped behind the
            //    // live window and can't proceed.
            //    throw new BehindLiveWindowException();
            //} else if (newIndexStartTimeUs < oldIndexStartTimeUs) {
            //    // The new index overlaps with (but does not have a start position contained within) the old
            //    // index. This can only happen if extra segments have been added to the start of the index.
            //    newSegmentNumShift -=
            //            newIndex.getSegmentNum(oldIndexStartTimeUs, newPeriodDurationUs)
            //                    - oldIndexFirstSegmentNum;
            //} else {
            //    // The new index overlaps with (and has a start position contained within) the old index.
            //    newSegmentNumShift +=
            //            oldIndex.getSegmentNum(newIndexStartTimeUs, newPeriodDurationUs)
            //                    - newIndexFirstSegmentNum;
            //}
            //return new RepresentationHolder(
            //        newPeriodDurationUs, newRepresentation, extractorWrapper, newSegmentNumShift, newIndex);
        }

        //@CheckResult
        //    /* package */ RepresentationHolder copyWithNewSegmentIndex(SabrSegmentIndex segmentIndex) {
        //    return new RepresentationHolder(
        //            periodDurationUs, representation, extractorWrapper, segmentNumShift, segmentIndex);
        //}

        //public long getFirstSegmentNum() {
        //    return segmentIndex.getFirstSegmentNum() + segmentNumShift;
        //}
        //
        //public int getSegmentCount() {
        //    return segmentIndex.getSegmentCount(periodDurationUs);
        //}
        //
        //public long getSegmentStartTimeUs(long segmentNum) {
        //    return segmentIndex.getTimeUs(segmentNum - segmentNumShift);
        //}
        //
        //public long getSegmentEndTimeUs(long segmentNum) {
        //    return getSegmentStartTimeUs(segmentNum)
        //            + segmentIndex.getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
        //}
        //
        //public long getSegmentNum(long positionUs) {
        //    return segmentIndex.getSegmentNum(positionUs, periodDurationUs) + segmentNumShift;
        //}
        //
        //public RangedUri getSegmentUrl(long segmentNum) {
        //    return segmentIndex.getSegmentUrl(segmentNum - segmentNumShift);
        //}

        //public long getFirstAvailableSegmentNum(
        //        SabrManifest manifest, int periodIndex, long nowUnixTimeUs) {
        //    if (getSegmentCount() == SabrSegmentIndex.INDEX_UNBOUNDED
        //            && manifest.timeShiftBufferDepthMs != C.TIME_UNSET) {
        //        // The index is itself unbounded. We need to use the current time to calculate the range of
        //        // available segments.
        //        long liveEdgeTimeUs = nowUnixTimeUs - C.msToUs(manifest.availabilityStartTimeMs);
        //        long periodStartUs = C.msToUs(manifest.getPeriod(periodIndex).startMs);
        //        long liveEdgeTimeInPeriodUs = liveEdgeTimeUs - periodStartUs;
        //        long bufferDepthUs = C.msToUs(manifest.timeShiftBufferDepthMs);
        //        return Math.max(
        //                getFirstSegmentNum(), getSegmentNum(liveEdgeTimeInPeriodUs - bufferDepthUs));
        //    }
        //    return getFirstSegmentNum();
        //}
        //
        //public long getLastAvailableSegmentNum(
        //        SabrManifest manifest, int periodIndex, long nowUnixTimeUs) {
        //    int availableSegmentCount = getSegmentCount();
        //    if (availableSegmentCount == SabrSegmentIndex.INDEX_UNBOUNDED) {
        //        // The index is itself unbounded. We need to use the current time to calculate the range of
        //        // available segments.
        //        long liveEdgeTimeUs = nowUnixTimeUs - C.msToUs(manifest.availabilityStartTimeMs);
        //        long periodStartUs = C.msToUs(manifest.getPeriod(periodIndex).startMs);
        //        long liveEdgeTimeInPeriodUs = liveEdgeTimeUs - periodStartUs;
        //        // getSegmentNum(liveEdgeTimeInPeriodUs) will not be completed yet, so subtract one to get
        //        // the index of the last completed segment.
        //        return getSegmentNum(liveEdgeTimeInPeriodUs) - 1;
        //    }
        //    return getFirstSegmentNum() + availableSegmentCount - 1;
        //}

        private static boolean mimeTypeIsWebm(String mimeType) {
            return mimeType.startsWith(MimeTypes.VIDEO_WEBM) || mimeType.startsWith(MimeTypes.AUDIO_WEBM)
                    || mimeType.startsWith(MimeTypes.APPLICATION_WEBM);
        }

        private static boolean mimeTypeIsRawText(String mimeType) {
            return MimeTypes.isText(mimeType) || MimeTypes.APPLICATION_TTML.equals(mimeType);
        }

        private static @Nullable ChunkExtractorWrapper createExtractorWrapper(
                int trackType,
                Representation representation,
                boolean enableEventMessageTrack,
                List<Format> closedCaptionFormats,
                TrackOutput playerEmsgTrackOutput,
                SabrStream sabrStream) {
            String containerMimeType = representation.format.containerMimeType;
            if (containerMimeType == null || mimeTypeIsRawText(containerMimeType)) {
                return null;
            }

            // MOD: replaced with SabrExtractor
            //Extractor extractor;
            //if (MimeTypes.APPLICATION_RAWCC.equals(containerMimeType)) {
            //    extractor = new RawCcExtractor(representation.format);
            //} else if (mimeTypeIsWebm(containerMimeType)) {
            //    extractor = new MatroskaExtractor(MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES);
            //} else {
            //    int flags = 0;
            //    if (enableEventMessageTrack) {
            //        flags |= FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK;
            //    }
            //    extractor =
            //            new FragmentedMp4Extractor(
            //                    flags, null, null, null, closedCaptionFormats, playerEmsgTrackOutput);
            //}

            //Extractor extractor = new SabrExtractor(trackType, representation.format, sabrStream);

            Extractor extractor;
            if (MimeTypes.APPLICATION_RAWCC.equals(containerMimeType)) {
                extractor = new RawCcExtractor(representation.format);
            } else if (mimeTypeIsWebm(containerMimeType)) {
                extractor = new SabrMatroskaAdapter(SabrMatroskaAdapter.FLAG_DISABLE_SEEK_FOR_CUES, sabrStream);
            } else {
                int flags = 0;
                if (enableEventMessageTrack) {
                    flags |= SabrFragmentedMp4Adapter.FLAG_ENABLE_EMSG_TRACK;
                }
                extractor =
                        new SabrFragmentedMp4Adapter(
                                flags, null, null, null, closedCaptionFormats, playerEmsgTrackOutput, sabrStream);
            }

            // Prefer drmInitData obtained from the manifest over drmInitData obtained from the stream,
            // as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
            return new ChunkExtractorWrapper(extractor, trackType, representation.format);
        }
    }
}
