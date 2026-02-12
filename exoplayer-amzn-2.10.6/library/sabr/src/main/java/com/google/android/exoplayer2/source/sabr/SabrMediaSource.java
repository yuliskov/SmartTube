package com.google.android.exoplayer2.source.sabr;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.DefaultCompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.sabr.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.sabr.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;

import java.io.IOException;

public final class SabrMediaSource extends BaseMediaSource {
    /**
     * The interval in milliseconds between invocations of {@link
     * SourceInfoRefreshListener#onSourceInfoRefreshed(MediaSource, Timeline, Object)} when the
     * source's {@link Timeline} is changing dynamically (for example, for incomplete live streams).
     */
    private static final int NOTIFY_MANIFEST_INTERVAL_MS = 5000;
    /**
     * The minimum default start position for live streams, relative to the start of the live window.
     */
    private static final long MIN_LIVE_DEFAULT_START_POSITION_US = 5000000;
    private final SabrManifest manifest;
    private final SabrChunkSource.Factory chunkSourceFactory;
    private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private @Nullable TransferListener mediaTransferListener;
    private final LoaderErrorThrower manifestLoadErrorThrower;
    private final PlayerEmsgCallback playerEmsgCallback;
    private Loader loader;
    private IOException manifestFatalError;
    private final long livePresentationDelayMs;
    private final SparseArray<SabrMediaPeriod> periodsById;
    private final @Nullable Object tag;
    private long elapsedRealtimeOffsetMs;
    private int firstPeriodId;
    private final boolean livePresentationDelayOverridesManifest;

    /**
     * The default presentation delay for live streams. The presentation delay is the duration by
     * which the default start position precedes the end of the live window.
     */
    private static final long DEFAULT_LIVE_PRESENTATION_DELAY_MS = 30000;

    private SabrMediaSource(
            SabrManifest manifest,
            SabrChunkSource.Factory chunkSourceFactory,
            CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
            LoadErrorHandlingPolicy loadErrorHandlingPolicy,
            long livePresentationDelayMs,
            boolean livePresentationDelayOverridesManifest,
            @Nullable Object tag
    ) {
        this.manifest = manifest;
        this.chunkSourceFactory = chunkSourceFactory;
        this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        this.livePresentationDelayMs = livePresentationDelayMs;
        this.livePresentationDelayOverridesManifest = livePresentationDelayOverridesManifest;
        this.tag = tag;
        periodsById = new SparseArray<>();
        playerEmsgCallback = new DefaultPlayerEmsgCallback();
        manifestLoadErrorThrower = new ManifestLoadErrorThrower();
    }

    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
        this.mediaTransferListener = mediaTransferListener;
        loader = new Loader("Loader:SabrMediaSource");
        processManifest();
    }

    @Override
    protected void releaseSourceInternal() {
        if (loader != null) {
            loader.release();
            loader = null;
        }
        elapsedRealtimeOffsetMs = 0;
        manifestFatalError = null;
        firstPeriodId = 0;
        periodsById.clear();
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        manifestLoadErrorThrower.maybeThrowError();
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId periodId, Allocator allocator, long startPositionUs) {
        int periodIndex = (Integer) periodId.periodUid - firstPeriodId;
        EventDispatcher periodEventDispatcher =
                createEventDispatcher(periodId, manifest.getPeriod(periodIndex).startMs);
        SabrMediaPeriod mediaPeriod = new SabrMediaPeriod(
                firstPeriodId + periodIndex,
                manifest,
                periodIndex,
                chunkSourceFactory,
                mediaTransferListener,
                loadErrorHandlingPolicy,
                periodEventDispatcher,
                elapsedRealtimeOffsetMs,
                manifestLoadErrorThrower,
                allocator,
                compositeSequenceableLoaderFactory,
                playerEmsgCallback);
        periodsById.put(mediaPeriod.id, mediaPeriod);
        return mediaPeriod;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        SabrMediaPeriod sabrMediaPeriod = (SabrMediaPeriod) mediaPeriod;
        sabrMediaPeriod.release();
        periodsById.remove(sabrMediaPeriod.id);
    }

    private void processManifest() {
        // Update any periods.
        for (int i = 0; i < periodsById.size(); i++) {
            int id = periodsById.keyAt(i);
            if (id >= firstPeriodId) {
                periodsById.valueAt(i).updateManifest(manifest, id - firstPeriodId);
            } else {
                // This period has been removed from the manifest so it doesn't need to be updated.
            }
        }
        // Update the window.
        boolean windowChangingImplicitly = false;
        int lastPeriodIndex = manifest.getPeriodCount() - 1;
        PeriodSeekInfo firstPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(manifest.getPeriod(0),
                manifest.getPeriodDurationUs(0));
        PeriodSeekInfo lastPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(
                manifest.getPeriod(lastPeriodIndex), manifest.getPeriodDurationUs(lastPeriodIndex));
        // Get the period-relative start/end times.
        long currentStartTimeUs = firstPeriodSeekInfo.availableStartTimeUs;
        long currentEndTimeUs = lastPeriodSeekInfo.availableEndTimeUs;
        if (manifest.dynamic && !lastPeriodSeekInfo.isIndexExplicit) {
            // The manifest describes an incomplete live stream. Update the start/end times to reflect the
            // live stream duration and the manifest's time shift buffer depth.
            long liveStreamDurationUs = getNowUnixTimeUs() - C.msToUs(manifest.availabilityStartTimeMs);
            long liveStreamEndPositionInLastPeriodUs = liveStreamDurationUs
                    - C.msToUs(manifest.getPeriod(lastPeriodIndex).startMs);
            currentEndTimeUs = Math.min(liveStreamEndPositionInLastPeriodUs, currentEndTimeUs);
            if (manifest.timeShiftBufferDepthMs != C.TIME_UNSET) {
                long timeShiftBufferDepthUs = C.msToUs(manifest.timeShiftBufferDepthMs);
                long offsetInPeriodUs = currentEndTimeUs - timeShiftBufferDepthUs;
                int periodIndex = lastPeriodIndex;
                while (offsetInPeriodUs < 0 && periodIndex > 0) {
                    offsetInPeriodUs += manifest.getPeriodDurationUs(--periodIndex);
                }
                if (periodIndex == 0) {
                    currentStartTimeUs = Math.max(currentStartTimeUs, offsetInPeriodUs);
                } else {
                    // The time shift buffer starts after the earliest period.
                    // TODO: Does this ever happen?
                    currentStartTimeUs = manifest.getPeriodDurationUs(0);
                }
            }
            windowChangingImplicitly = true;
        }
        long windowDurationUs = currentEndTimeUs - currentStartTimeUs;
        for (int i = 0; i < manifest.getPeriodCount() - 1; i++) {
            windowDurationUs += manifest.getPeriodDurationUs(i);
        }
        long windowDefaultStartPositionUs = 0;
        if (manifest.dynamic) {
            long presentationDelayForManifestMs = livePresentationDelayMs;
            if (!livePresentationDelayOverridesManifest
                    && manifest.suggestedPresentationDelayMs != C.TIME_UNSET) {
                presentationDelayForManifestMs = manifest.suggestedPresentationDelayMs;
            }
            // Snap the default position to the start of the segment containing it.
            windowDefaultStartPositionUs = windowDurationUs - C.msToUs(presentationDelayForManifestMs);
            if (windowDefaultStartPositionUs < MIN_LIVE_DEFAULT_START_POSITION_US) {
                // The default start position is too close to the start of the live window. Set it to the
                // minimum default start position provided the window is at least twice as big. Else set
                // it to the middle of the window.
                windowDefaultStartPositionUs = Math.min(MIN_LIVE_DEFAULT_START_POSITION_US,
                        windowDurationUs / 2);
            }
        }
        long windowStartTimeMs = manifest.availabilityStartTimeMs
                + manifest.getPeriod(0).startMs + C.usToMs(currentStartTimeUs);
        SabrTimeline timeline =
                new SabrTimeline(
                        manifest.availabilityStartTimeMs,
                        windowStartTimeMs,
                        firstPeriodId,
                        currentStartTimeUs,
                        windowDurationUs,
                        windowDefaultStartPositionUs,
                        manifest,
                        tag);
        refreshSourceInfo(timeline, manifest);
    }

    private long getNowUnixTimeUs() {
        if (elapsedRealtimeOffsetMs != 0) {
            return C.msToUs(SystemClock.elapsedRealtime() + elapsedRealtimeOffsetMs);
        } else {
            return C.msToUs(System.currentTimeMillis());
        }
    }

    public static final class Factory implements AdsMediaSource.MediaSourceFactory {
        private final SabrChunkSource.Factory chunkSourceFactory;
        @Nullable private final DataSource.Factory manifestDataSourceFactory;
        private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
        private final DefaultCompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
        private long livePresentationDelayMs;
        private boolean livePresentationDelayOverridesManifest;
        private boolean isCreateCalled;
        @Nullable private Object tag;

        /**
         * Creates a new factory for {@link SabrMediaSource}s.
         *
         * @param chunkSourceFactory A factory for {@link SabrChunkSource} instances.
         * @param manifestDataSourceFactory A factory for {@link DataSource} instances that will be used
         *     to load (and refresh) the manifest. May be {@code null} if the factory will only ever be
         *     used to create create media sources with sideloaded manifests via {@link
         *     #createMediaSource(SabrManifest, Handler, MediaSourceEventListener)}.
         */
        public Factory(
                SabrChunkSource.Factory chunkSourceFactory,
                @Nullable DataSource.Factory manifestDataSourceFactory) {
            this.chunkSourceFactory = chunkSourceFactory;
            this.manifestDataSourceFactory = manifestDataSourceFactory;
            loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
            livePresentationDelayMs = DEFAULT_LIVE_PRESENTATION_DELAY_MS;
            compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
        }

        @Override
        public MediaSource createMediaSource(Uri uri) {
            return null;
        }

        /**
         * Returns a new {@link SabrMediaSource} using the current parameters and the specified
         * sideloaded manifest.
         *
         * @param manifest The manifest.
         * @return The new {@link SabrMediaSource}.
         */
        public SabrMediaSource createMediaSource(SabrManifest manifest) {
            isCreateCalled = true;
            return new SabrMediaSource(
                    manifest,
                    chunkSourceFactory,
                    compositeSequenceableLoaderFactory,
                    loadErrorHandlingPolicy,
                    livePresentationDelayMs,
                    livePresentationDelayOverridesManifest,
                    tag
            );
        }

        /**
         * @deprecated Use {@link #createMediaSource(SabrManifest)} and {@link
         *     #addEventListener(Handler, MediaSourceEventListener)} instead.
         */
        @Deprecated
        public SabrMediaSource createMediaSource(
                SabrManifest manifest,
                @Nullable Handler eventHandler,
                @Nullable MediaSourceEventListener eventListener) {
            isCreateCalled = true;
            SabrMediaSource mediaSource = createMediaSource(manifest);
            if (eventHandler != null && eventListener != null) {
                mediaSource.addEventListener(eventHandler, eventListener);
            }
            return mediaSource;
        }

        @Override
        public int[] getSupportedTypes() {
            return new int[0];
        }

        /**
         * Sets the {@link LoadErrorHandlingPolicy}. The default value is created by calling {@link
         * DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy()}.
         *
         * <p>Calling this method overrides any calls to {@link #setMinLoadableRetryCount(int)}.
         *
         * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the {@code create} methods has already been called.
         */
        public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
            Assertions.checkState(!isCreateCalled);
            this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
            return this;
        }

        /**
         * Sets the minimum number of times to retry if a loading error occurs. See {@link
         * #setLoadErrorHandlingPolicy} for the default value.
         *
         * <p>Calling this method is equivalent to calling {@link #setLoadErrorHandlingPolicy} with
         * {@link DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy(int)
         * DefaultLoadErrorHandlingPolicy(minLoadableRetryCount)}
         *
         * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the {@code create} methods has already been called.
         * @deprecated Use {@link #setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)} instead.
         */
        @Deprecated
        public Factory setMinLoadableRetryCount(int minLoadableRetryCount) {
            return setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount));
        }

        /**
         * Sets a tag for the media source which will be published in the {@link
         * com.google.android.exoplayer2.Timeline} of the source as {@link
         * com.google.android.exoplayer2.Timeline.Window#tag}.
         *
         * @param tag A tag for the media source.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the {@code create} methods has already been called.
         */
        public Factory setTag(Object tag) {
            Assertions.checkState(!isCreateCalled);
            this.tag = tag;
            return this;
        }

        /**
         * Sets the duration in milliseconds by which the default start position should precede the end
         * of the live window for live playbacks. The {@code overridesManifest} parameter specifies
         * whether the value is used in preference to one in the manifest, if present. The default value
         * is {@link #DEFAULT_LIVE_PRESENTATION_DELAY_MS}, and by default {@code overridesManifest} is
         * false.
         *
         * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
         *     default start position should precede the end of the live window.
         * @param overridesManifest Whether the value is used in preference to one in the manifest, if
         *     present.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the {@code create} methods has already been called.
         */
        public Factory setLivePresentationDelayMs(
                long livePresentationDelayMs, boolean overridesManifest) {
            Assertions.checkState(!isCreateCalled);
            this.livePresentationDelayMs = livePresentationDelayMs;
            this.livePresentationDelayOverridesManifest = overridesManifest;
            return this;
        }
    }

    /**
     * A {@link LoaderErrorThrower} that throws fatal {@link IOException} that has occurred during
     * manifest loading from the manifest {@code loader}, or exception with the loaded manifest.
     */
    /* package */ final class ManifestLoadErrorThrower implements LoaderErrorThrower {

        @Override
        public void maybeThrowError() throws IOException {
            loader.maybeThrowError();
            maybeThrowManifestError();
        }

        @Override
        public void maybeThrowError(int minRetryCount) throws IOException {
            loader.maybeThrowError(minRetryCount);
            maybeThrowManifestError();
        }

        private void maybeThrowManifestError() throws IOException {
            if (manifestFatalError != null) {
                throw manifestFatalError;
            }
        }
    }

    private static final class DefaultPlayerEmsgCallback implements PlayerEmsgCallback {
        @Override
        public void onDashManifestRefreshRequested() {
            //SabrMediaSource.this.onDashManifestRefreshRequested();
        }

        @Override
        public void onDashManifestPublishTimeExpired(long expiredManifestPublishTimeUs) {
            //SabrMediaSource.this.onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs);
        }
    }

    private static final class SabrTimeline extends Timeline {

        private final long presentationStartTimeMs;
        private final long windowStartTimeMs;

        private final int firstPeriodId;
        private final long offsetInFirstPeriodUs;
        private final long windowDurationUs;
        private final long windowDefaultStartPositionUs;
        private final SabrManifest manifest;
        private final @Nullable Object windowTag;

        public SabrTimeline(
                long presentationStartTimeMs,
                long windowStartTimeMs,
                int firstPeriodId,
                long offsetInFirstPeriodUs,
                long windowDurationUs,
                long windowDefaultStartPositionUs,
                SabrManifest manifest,
                @Nullable Object windowTag) {
            this.presentationStartTimeMs = presentationStartTimeMs;
            this.windowStartTimeMs = windowStartTimeMs;
            this.firstPeriodId = firstPeriodId;
            this.offsetInFirstPeriodUs = offsetInFirstPeriodUs;
            this.windowDurationUs = windowDurationUs;
            this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
            this.manifest = manifest;
            this.windowTag = windowTag;
        }

        @Override
        public int getPeriodCount() {
            return manifest.getPeriodCount();
        }

        @Override
        public Period getPeriod(int periodIndex, Period period, boolean setIdentifiers) {
            Assertions.checkIndex(periodIndex, 0, getPeriodCount());
            Object id = setIdentifiers ? manifest.getPeriod(periodIndex).id : null;
            Object uid = setIdentifiers ? (firstPeriodId + periodIndex) : null;
            return period.set(id, uid, 0, manifest.getPeriodDurationUs(periodIndex),
                    C.msToUs(manifest.getPeriod(periodIndex).startMs - manifest.getPeriod(0).startMs)
                            - offsetInFirstPeriodUs);
        }

        @Override
        public int getWindowCount() {
            return 1;
        }

        @Override
        public Window getWindow(
                int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
            Assertions.checkIndex(windowIndex, 0, 1);
            long windowDefaultStartPositionUs = getAdjustedWindowDefaultStartPositionUs(
                    defaultPositionProjectionUs);
            Object tag = setTag ? windowTag : null;
            boolean isDynamic =
                    manifest.dynamic
                            && manifest.minUpdatePeriodMs != C.TIME_UNSET
                            && manifest.durationMs == C.TIME_UNSET;
            return window.set(
                    tag,
                    presentationStartTimeMs,
                    windowStartTimeMs,
                    /* isSeekable= */ true,
                    isDynamic,
                    windowDefaultStartPositionUs,
                    windowDurationUs,
                    /* firstPeriodIndex= */ 0,
                    /* lastPeriodIndex= */ getPeriodCount() - 1,
                    offsetInFirstPeriodUs);
        }

        @Override
        public int getIndexOfPeriod(Object uid) {
            if (!(uid instanceof Integer)) {
                return C.INDEX_UNSET;
            }
            int periodId = (int) uid;
            int periodIndex = periodId - firstPeriodId;
            return periodIndex < 0 || periodIndex >= getPeriodCount() ? C.INDEX_UNSET : periodIndex;
        }

        private long getAdjustedWindowDefaultStartPositionUs(long defaultPositionProjectionUs) {
            long windowDefaultStartPositionUs = this.windowDefaultStartPositionUs;
            if (!manifest.dynamic) {
                return windowDefaultStartPositionUs;
            }
            if (defaultPositionProjectionUs > 0) {
                windowDefaultStartPositionUs += defaultPositionProjectionUs;
                if (windowDefaultStartPositionUs > windowDurationUs) {
                    // The projection takes us beyond the end of the live window.
                    return C.TIME_UNSET;
                }
            }
            // Attempt to snap to the start of the corresponding video segment.
            int periodIndex = 0;
            long defaultStartPositionInPeriodUs = offsetInFirstPeriodUs + windowDefaultStartPositionUs;
            long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
            while (periodIndex < manifest.getPeriodCount() - 1
                    && defaultStartPositionInPeriodUs >= periodDurationUs) {
                defaultStartPositionInPeriodUs -= periodDurationUs;
                periodIndex++;
                periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
            }
            com.google.android.exoplayer2.source.sabr.manifest.Period period =
                    manifest.getPeriod(periodIndex);
            int videoAdaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO);
            if (videoAdaptationSetIndex == C.INDEX_UNSET) {
                // No video adaptation set for snapping.
                return windowDefaultStartPositionUs;
            }
            // If there are multiple video adaptation sets with unaligned segments, the initial time may
            // not correspond to the start of a segment in both, but this is an edge case.
            //SabrSegmentIndex snapIndex = period.adaptationSets.get(videoAdaptationSetIndex)
            //        .representations.get(0).getIndex();
            //if (snapIndex == null || snapIndex.getSegmentCount(periodDurationUs) == 0) {
            //    // Video adaptation set does not include a non-empty index for snapping.
            //    return windowDefaultStartPositionUs;
            //}
            //long segmentNum = snapIndex.getSegmentNum(defaultStartPositionInPeriodUs, periodDurationUs);
            //return windowDefaultStartPositionUs + snapIndex.getTimeUs(segmentNum)
            //        - defaultStartPositionInPeriodUs;

            long startTimeUs = 0; // TODO: calc SABR start time

            return windowDefaultStartPositionUs + startTimeUs
                    - defaultStartPositionInPeriodUs;
        }

        @Override
        public Object getUidOfPeriod(int periodIndex) {
            Assertions.checkIndex(periodIndex, 0, getPeriodCount());
            return firstPeriodId + periodIndex;
        }
    }

    private static final class PeriodSeekInfo {

        public static PeriodSeekInfo createPeriodSeekInfo(
                com.google.android.exoplayer2.source.sabr.manifest.Period period, long durationUs) {
            int adaptationSetCount = period.adaptationSets.size();
            long availableStartTimeUs = 0;
            long availableEndTimeUs = Long.MAX_VALUE; // TODO: calc SABR start time
            boolean isIndexExplicit = false;
            boolean seenEmptyIndex = false;

            boolean haveAudioVideoAdaptationSets = false;
            for (int i = 0; i < adaptationSetCount; i++) {
                int type = period.adaptationSets.get(i).type;
                if (type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO) {
                    haveAudioVideoAdaptationSets = true;
                    break;
                }
            }

            for (int i = 0; i < adaptationSetCount; i++) {
                AdaptationSet adaptationSet = period.adaptationSets.get(i);
                // Exclude text adaptation sets from duration calculations, if we have at least one audio
                // or video adaptation set. See: https://github.com/google/ExoPlayer/issues/4029
                if (haveAudioVideoAdaptationSets && adaptationSet.type == C.TRACK_TYPE_TEXT) {
                    continue;
                }

                //SabrSegmentIndex index = adaptationSet.representations.get(0).getIndex();
                //if (index == null) {
                //    return new PeriodSeekInfo(true, 0, durationUs);
                //}
                //isIndexExplicit |= index.isExplicit();
                //int segmentCount = index.getSegmentCount(durationUs);
                //if (segmentCount == 0) {
                //    seenEmptyIndex = true;
                //    availableStartTimeUs = 0;
                //    availableEndTimeUs = 0;
                //} else if (!seenEmptyIndex) {
                //    long firstSegmentNum = index.getFirstSegmentNum();
                //    long adaptationSetAvailableStartTimeUs = index.getTimeUs(firstSegmentNum);
                //    availableStartTimeUs = Math.max(availableStartTimeUs, adaptationSetAvailableStartTimeUs);
                //    if (segmentCount != SabrSegmentIndex.INDEX_UNBOUNDED) {
                //        long lastSegmentNum = firstSegmentNum + segmentCount - 1;
                //        long adaptationSetAvailableEndTimeUs = index.getTimeUs(lastSegmentNum)
                //                + index.getDurationUs(lastSegmentNum, durationUs);
                //        availableEndTimeUs = Math.min(availableEndTimeUs, adaptationSetAvailableEndTimeUs);
                //    }
                //}
            }
            return new PeriodSeekInfo(isIndexExplicit, availableStartTimeUs, availableEndTimeUs);
        }

        public final boolean isIndexExplicit;
        public final long availableStartTimeUs;
        public final long availableEndTimeUs;

        private PeriodSeekInfo(boolean isIndexExplicit, long availableStartTimeUs,
                               long availableEndTimeUs) {
            this.isIndexExplicit = isIndexExplicit;
            this.availableStartTimeUs = availableStartTimeUs;
            this.availableEndTimeUs = availableEndTimeUs;
        }

    }
}
