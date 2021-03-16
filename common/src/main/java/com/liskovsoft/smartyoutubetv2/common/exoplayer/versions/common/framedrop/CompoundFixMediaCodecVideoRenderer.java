package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.common.framedrop;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

public class CompoundFixMediaCodecVideoRenderer extends AmlogicFix2MediaCodecVideoRenderer {
    // Exo 2.10, 2.11
    //public CompoundFixMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                          @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    //}

    // Exo 2.12, 2.13
    public CompoundFixMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
                                             boolean enableDecoderFallback, @Nullable Handler eventHandler,
                                             @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    // EXO: 2.10, 2.11, 2.12
    /**
     * Renders the output buffer with the specified index. This method is only called if the platform
     * API version of the device is 21 or later.
     *
     * @param codec The codec that owns the output buffer.
     * @param index The index of the output buffer to drop.
     * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
     * @param releaseTimeNs The wallclock time at which the frame should be displayed, in nanoseconds.
     */
    @TargetApi(21)
    protected void renderOutputBufferV21(
            MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
        // Fix frame drops on SurfaceView
        // https://github.com/google/ExoPlayer/issues/6348
        // https://developer.android.com/reference/android/media/MediaCodec#releaseOutputBuffer(int,%20long)
        super.renderOutputBufferV21(codec, index, presentationTimeUs, 0);
    }

    // EXO: 2.13
    /**
     * Renders the output buffer with the specified index. This method is only called if the platform
     * API version of the device is 21 or later.
     *
     * @param codec The codec that owns the output buffer.
     * @param index The index of the output buffer to drop.
     * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
     * @param releaseTimeNs The wallclock time at which the frame should be displayed, in nanoseconds.
     */
    //@TargetApi(21)
    //protected void renderOutputBufferV21(
    //        MediaCodecAdapter codec, int index, long presentationTimeUs, long releaseTimeNs) {
    //    // Fix frame drops on SurfaceView
    //    // https://github.com/google/ExoPlayer/issues/6348
    //    // https://developer.android.com/reference/android/media/MediaCodec#releaseOutputBuffer(int,%20long)
    //    super.renderOutputBufferV21(codec, index, presentationTimeUs, 0);
    //}
}
