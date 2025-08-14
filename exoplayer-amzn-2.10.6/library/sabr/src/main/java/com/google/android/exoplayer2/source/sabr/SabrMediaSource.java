package com.google.android.exoplayer2.source.sabr;

import android.net.Uri;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;

public final class SabrMediaSource extends BaseMediaSource {
    public static final class Factory implements AdsMediaSource.MediaSourceFactory {
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
            return new SabrMediaSource();
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
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
        return null;
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {

    }
}
