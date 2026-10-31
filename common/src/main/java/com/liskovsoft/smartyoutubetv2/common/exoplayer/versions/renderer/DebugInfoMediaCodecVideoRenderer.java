package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build.VERSION;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.ExoUtils;

public class DebugInfoMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    private static final String TAG = DebugInfoMediaCodecVideoRenderer.class.getSimpleName();
    private int mFrameIndex;
    private boolean mIsSetOutputSurfaceWorkaroundEnabled;

    // Exo 2.9
    //public DebugInfoMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                     @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener,
    //                                     int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener,
    //            maxDroppedFramesToNotify);
    //}

    // Exo 2.10, 2.11
    public DebugInfoMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
                                            @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    // Exo 2.12, 2.13
    //public DebugInfoMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     boolean enableDecoderFallback, @Nullable Handler eventHandler,
    //                                     @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    //}

    @Override
    protected CodecMaxValues getCodecMaxValues(
            MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
        ExoUtils.updateVideoDecoderInfo(codecInfo);

        return super.getCodecMaxValues(codecInfo, format, streamFormats);
    }

    // Measure real fps.
    // Note, that you can't accurate measure frame rate because actual frame rate is the average frame rate for the whole video track!
    // 29.97fps test: https://www.youtube.com/watch?v=LXb3EKWsInQ (Costa Rica)
    // More info: https://github.com/google/ExoPlayer/issues/4088
    //@Override
    //protected void renderOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
    //    super.renderOutputBuffer(codec, index, presentationTimeUs);
    //}
    //
    //@Override
    //protected void renderOutputBufferV21(MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
    //    super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
    //
    //    mFrameIndex++;
    //
    //    Log.d(TAG, "Real fps: %s", 1_000_000f / (presentationTimeUs / mFrameIndex));
    //}

    @Override
    protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
        // Null surface error on Android 9 (VERSION.SDK_INT >= 28) and above (appears on background audio playback)
        // Need to be enabled on older version of ExoPlayer (e.g. 2.10.6).
        // It's because there's no tweaks for modern devices.
        return mIsSetOutputSurfaceWorkaroundEnabled || super.codecNeedsSetOutputSurfaceWorkaround(name);
    }

    /**
     * Null surface error on Android 9 (VERSION.SDK_INT >= 28) and above (appears on background audio playback)<br/>
     * Need to be enabled on older version of ExoPlayer (e.g. 2.10.6).<br/>
     * It's because there's no tweaks for modern devices.
     */
    public void enableSetOutputSurfaceWorkaround(boolean enable) {
        mIsSetOutputSurfaceWorkaroundEnabled = enable;
    }
}
