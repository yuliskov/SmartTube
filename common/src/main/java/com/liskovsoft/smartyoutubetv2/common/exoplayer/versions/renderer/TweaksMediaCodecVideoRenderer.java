package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.sharedutils.mylogger.Log;

public class TweaksMediaCodecVideoRenderer extends DebugInfoMediaCodecVideoRenderer {
    private static final String TAG = TweaksMediaCodecVideoRenderer.class.getSimpleName();
    private boolean mIsFrameDropFixEnabled;
    private boolean mIsFrameDropSonyFixEnabled;
    private boolean mIsAmlogicFixEnabled;

    // Exo 2.9
    //public CustomMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                     @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener,
    //                                     int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener,
    //            maxDroppedFramesToNotify);
    //}

    // Exo 2.10, 2.11
    public TweaksMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
                                         @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    // Exo 2.12, 2.13
    //public TweaksMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     boolean enableDecoderFallback, @Nullable Handler eventHandler,
    //                                     @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    //}

    // EXO: 2.10, 2.11, 2.12
    @TargetApi(21)
    protected void renderOutputBufferV21(
            MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
        // Fix frame drops on SurfaceView
        // https://github.com/google/ExoPlayer/issues/6348
        // https://developer.android.com/reference/android/media/MediaCodec#releaseOutputBuffer(int,%20long)
        super.renderOutputBufferV21(codec, index, presentationTimeUs, mIsFrameDropFixEnabled ? 0 : releaseTimeNs);
    }

    // EXO: 2.13
    //@TargetApi(21)
    //protected void renderOutputBufferV21(
    //        MediaCodecAdapter codec, int index, long presentationTimeUs, long releaseTimeNs) {
    //    // Fix frame drops on SurfaceView
    //    // https://github.com/google/ExoPlayer/issues/6348
    //    // https://developer.android.com/reference/android/media/MediaCodec#releaseOutputBuffer(int,%20long)
    //    super.renderOutputBufferV21(codec, index, presentationTimeUs, 0);
    //}

    @Override
    protected CodecMaxValues getCodecMaxValues(
            MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
        CodecMaxValues maxValues =
                super.getCodecMaxValues(codecInfo, format, streamFormats);

        if (mIsAmlogicFixEnabled) {
            if (maxValues.width < 1920 || maxValues.height < 1089) {
                Log.d(TAG, "Applying Amlogic fix...");
                return new CodecMaxValues(
                        Math.max(maxValues.width, 1920),
                        Math.max(maxValues.height, 1089),
                        maxValues.inputSize);
            }
        }

        return maxValues;
    }

    /**
     * Frame drop fixes on Sony Bravia<br/>
     * https://github.com/google/ExoPlayer/issues/6348#issuecomment-718986083
     */
    @Override
    protected boolean isBufferLate(long earlyUs) {
        if (mIsFrameDropSonyFixEnabled) {
            return earlyUs < -1000000;
        }

        return super.isBufferLate(earlyUs);
    }

    /**
     * Frame drop fixes on Sony Bravia<br/>
     * https://github.com/google/ExoPlayer/issues/6348#issuecomment-718986083
     */
    @Override
    protected boolean isBufferVeryLate(long earlyUs) {
        if (mIsFrameDropSonyFixEnabled) {
            return earlyUs < -1500000;
        }

        return super.isBufferVeryLate(earlyUs);
    }

    public void enableFrameDropFix(boolean enabled) {
        mIsFrameDropFixEnabled = enabled;
    }

    public void enableFrameDropSonyFix(boolean enabled) {
        mIsFrameDropSonyFixEnabled = enabled;
    }

    public void enableAmlogicFix(boolean enabled) {
        mIsAmlogicFixEnabled = enabled;
    }
}
