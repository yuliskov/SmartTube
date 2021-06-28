package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;

public class DelayMediaCodecAudioRenderer extends MediaCodecAudioRenderer {
    private static final String TAG = DelayMediaCodecAudioRenderer.class.getSimpleName();
    private int mDelayUs;

    // Exo 2.9
    //public CustomMediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector,
    //                                           @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
    //                                           boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler,
    //                                           @Nullable AudioRendererEventListener eventListener, AudioSink audioSink) {
    //    super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, audioSink);
    //}

    // Exo 2.10, 2.11
    //public DelayMediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector,
    //                                    @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
    //                                    boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, @Nullable Handler eventHandler,
    //                                    @Nullable AudioRendererEventListener eventListener, AudioSink audioSink) {
    //    super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, audioSink);
    //}

    // Exo 2.12, 2.13
    public DelayMediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                            boolean enableDecoderFallback, @Nullable Handler eventHandler,
                                            @Nullable AudioRendererEventListener eventListener, AudioSink audioSink) {
        super(context, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink);
    }

    @Override
    public long getPositionUs() {
        return super.getPositionUs() + mDelayUs;
    }

    public void setAudioDelayMs(int delayMs) {
        mDelayUs = delayMs * 1_000;
    }

    public int getAudioDelayMs() {
        return mDelayUs / 1_000;
    }
}
