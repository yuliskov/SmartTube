package com.google.android.exoplayer2.source.sabr;

import android.util.Pair;
import android.util.SparseIntArray;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.sabr.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.sabr.SabrChunkSource.Factory;
import com.google.android.exoplayer2.source.sabr.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.sabr.manifest.Period;
import com.google.android.exoplayer2.source.sabr.manifest.Representation;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
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
import java.util.List;
import java.util.regex.Matcher;

final class SabrMediaPeriod
   implements MediaPeriod,
        SequenceableLoader.Callback<ChunkSampleStream<SabrChunkSource>>,
        ChunkSampleStream.ReleaseCallback<SabrChunkSource> {
    /* package */ final int id;
    private final SabrManifest manifest;
    private final int periodIndex;
    private final Factory chunkSourceFactory;
    @Nullable
    private final TransferListener transferListener;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private final EventDispatcher eventDispatcher;
    private final LoaderErrorThrower manifestLoaderErrorThrower;
    private final TrackGroupArray trackGroups;
    private final TrackGroupInfo[] trackGroupInfos;
    private final Allocator allocator;
    private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private final PlayerEmsgHandler playerEmsgHandler;

    private @Nullable Callback callback;
    private ChunkSampleStream<SabrChunkSource>[] sampleStreams;

    public SabrMediaPeriod(
            int id,
            SabrManifest manifest,
            int periodIndex,
            SabrChunkSource.Factory chunkSourceFactory,
            @Nullable TransferListener transferListener,
            LoadErrorHandlingPolicy loadErrorHandlingPolicy,
            EventDispatcher eventDispatcher,
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
        this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
        this.allocator = allocator;
        this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
        playerEmsgHandler = new PlayerEmsgHandler(manifest, playerEmsgCallback, allocator);
        sampleStreams = newSampleStreamArray(0);
        Period period = manifest.getPeriod(periodIndex);
        Pair<TrackGroupArray, TrackGroupInfo[]> result = buildTrackGroups(period.adaptationSets);
        trackGroups = result.first;
        trackGroupInfos = result.second;
    }

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
    public long selectTracks(
            @Nullable TrackSelection[] selections,
            boolean[] mayRetainStreamFlags,
            @Nullable SampleStream[] streams,
            boolean[] streamResetFlags,
            long positionUs) {
        return 0;
    }

    @Override
    public void discardBuffer(long positionUs, boolean toKeyframe) {

    }

    @Override
    public long readDiscontinuity() {
        return 0;
    }

    @Override
    public long seekToUs(long positionUs) {
        return 0;
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return 0;
    }

    @Override
    public long getBufferedPositionUs() {
        return 0;
    }

    @Override
    public long getNextLoadPositionUs() {
        return 0;
    }

    @Override
    public boolean continueLoading(long positionUs) {
        return false;
    }

    @Override
    public void reevaluateBuffer(long positionUs) {

    }

    // SequenceableLoader.Callback implementation.
    @Override
    public void onContinueLoadingRequested(ChunkSampleStream<SabrChunkSource> source) {
        callback.onContinueLoadingRequested(this);
    }

    @Override
    public void onSampleStreamReleased(ChunkSampleStream<SabrChunkSource> chunkSampleStream) {

    }

    public void release() {
        playerEmsgHandler.release();
        for (ChunkSampleStream<SabrChunkSource> sampleStream : sampleStreams) {
            sampleStream.release(this);
        }
        callback = null;
        eventDispatcher.mediaPeriodReleased();
    }

    @SuppressWarnings("unchecked")
    private static ChunkSampleStream<SabrChunkSource>[] newSampleStreamArray(int length) {
        return new ChunkSampleStream[length];
    }

    private static Pair<TrackGroupArray, TrackGroupInfo[]> buildTrackGroups(
            List<AdaptationSet> adaptationSets) {
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

        int totalGroupCount = primaryGroupCount + totalEmbeddedTrackGroupCount;
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
            groupedAdaptationSetIndices[groupCount++] = new int[] {i};
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

    private static Format[] getCea608TrackFormats(
            List<AdaptationSet> adaptationSets, int[] adaptationSetIndices) {
        return new Format[0];
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
