package com.google.android.exoplayer2.source.sabr;

import android.net.Uri;
import android.os.Handler;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.DefaultCompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.sabr.PlayerEmsgHandler.PlayerEmsgCallback;
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
    private int firstPeriodId;

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
            @Nullable Object tag
    ) {
        this.manifest = manifest;
        this.chunkSourceFactory = chunkSourceFactory;
        this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        this.livePresentationDelayMs = livePresentationDelayMs;
        this.tag = tag;
        periodsById = new SparseArray<>();
        playerEmsgCallback = new DefaultPlayerEmsgCallback();
        manifestLoadErrorThrower = new ManifestLoadErrorThrower();
    }

    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
        this.mediaTransferListener = mediaTransferListener;
        processManifest();
    }

    @Override
    protected void releaseSourceInternal() {
        if (loader != null) {
            loader.release();
            loader = null;
        }
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
        // TODO: process manifest
    }

    public static final class Factory implements AdsMediaSource.MediaSourceFactory {
        private final SabrChunkSource.Factory chunkSourceFactory;
        @Nullable private final DataSource.Factory manifestDataSourceFactory;
        private final DefaultLoadErrorHandlingPolicy loadErrorHandlingPolicy;
        private final DefaultCompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
        private final long livePresentationDelayMs;
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
            //Assertions.checkState(!isCreateCalled);
            //this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
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
}
