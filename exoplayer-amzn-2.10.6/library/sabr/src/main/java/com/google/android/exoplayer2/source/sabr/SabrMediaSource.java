package com.google.android.exoplayer2.source.sabr;

import android.net.Uri;
import android.os.Handler;

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
    private final SabrManifest mManifest;
    private final SabrChunkSource.Factory mChunkSourceFactory;
    private final CompositeSequenceableLoaderFactory mCompositeSequenceableLoaderFactory;
    private final LoadErrorHandlingPolicy mLoadErrorHandlingPolicy;
    private @Nullable TransferListener mMediaTransferListener;
    private final LoaderErrorThrower mManifestLoadErrorThrower;
    private final PlayerEmsgCallback mPlayerEmsgCallback;
    private Loader mLoader;
    private IOException mManifestFatalError;
    private final long mLivePresentationDelayMs;
    @Nullable private final Object mTag;
    private final int mFirstPeriodId = 0;

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
        mManifest = manifest;
        mChunkSourceFactory = chunkSourceFactory;
        mCompositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
        mLoadErrorHandlingPolicy = loadErrorHandlingPolicy;
        mLivePresentationDelayMs = livePresentationDelayMs;
        mTag = tag;
        mPlayerEmsgCallback = new DefaultPlayerEmsgCallback();
        mManifestLoadErrorThrower = new ManifestLoadErrorThrower();
    }

    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
        
    }

    @Override
    protected void releaseSourceInternal() {

    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {

    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId periodId, Allocator allocator, long startPositionUs) {
        int periodIndex = (Integer) periodId.periodUid - mFirstPeriodId;
        EventDispatcher periodEventDispatcher =
                createEventDispatcher(periodId, mManifest.startMs);
        return new SabrMediaPeriod(
                mFirstPeriodId + periodIndex,
                mManifest,
                periodIndex,
                mChunkSourceFactory,
                mMediaTransferListener,
                mLoadErrorHandlingPolicy,
                periodEventDispatcher,
                mManifestLoadErrorThrower,
                allocator,
                mCompositeSequenceableLoaderFactory,
                mPlayerEmsgCallback);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {

    }

    public static final class Factory implements AdsMediaSource.MediaSourceFactory {
        private final SabrChunkSource.Factory mChunkSourceFactory;
        @Nullable private final DataSource.Factory mManifestDataSourceFactory;
        private final DefaultLoadErrorHandlingPolicy mLoadErrorHandlingPolicy;
        private final DefaultCompositeSequenceableLoaderFactory mCompositeSequenceableLoaderFactory;
        private final long mLivePresentationDelayMs;
        private boolean mIsCreateCalled;
        @Nullable private Object mTag;

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
            mChunkSourceFactory = chunkSourceFactory;
            mManifestDataSourceFactory = manifestDataSourceFactory;
            mLoadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
            mLivePresentationDelayMs = DEFAULT_LIVE_PRESENTATION_DELAY_MS;
            mCompositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
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
            mIsCreateCalled = true;
            return new SabrMediaSource(
                    manifest,
                    mChunkSourceFactory,
                    mCompositeSequenceableLoaderFactory,
                    mLoadErrorHandlingPolicy,
                    mLivePresentationDelayMs,
                    mTag
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
            mIsCreateCalled = true;
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
            Assertions.checkState(!mIsCreateCalled);
            mTag = tag;
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
            mLoader.maybeThrowError();
            maybeThrowManifestError();
        }

        @Override
        public void maybeThrowError(int minRetryCount) throws IOException {
            mLoader.maybeThrowError(minRetryCount);
            maybeThrowManifestError();
        }

        private void maybeThrowManifestError() throws IOException {
            if (mManifestFatalError != null) {
                throw mManifestFatalError;
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
