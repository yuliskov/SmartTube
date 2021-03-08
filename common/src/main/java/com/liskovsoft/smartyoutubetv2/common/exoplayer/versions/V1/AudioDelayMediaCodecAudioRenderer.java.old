package com.liskovsoft.smartyoutubetv2.common.playback.exoplayer.state;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;

public class AudioDelayMediaCodecAudioRendererV1 extends MediaCodecAudioRenderer {
    private static final String TAG = AudioDelayMediaCodecAudioRendererV1.class.getSimpleName();
    private int mDelayUs;

    public AudioDelayMediaCodecAudioRendererV1(Context context, MediaCodecSelector mediaCodecSelector) {
        super(context, mediaCodecSelector);
    }

    public AudioDelayMediaCodecAudioRendererV1(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys) {
        super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys);
    }

    public AudioDelayMediaCodecAudioRendererV1(Context context, MediaCodecSelector mediaCodecSelector, @Nullable Handler eventHandler,
                                               @Nullable AudioRendererEventListener eventListener) {
        super(context, mediaCodecSelector, eventHandler, eventListener);
    }

    public AudioDelayMediaCodecAudioRendererV1(Context context, MediaCodecSelector mediaCodecSelector,
                                               @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                               boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler,
                                               @Nullable AudioRendererEventListener eventListener) {
        super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener);
    }

    public AudioDelayMediaCodecAudioRendererV1(Context context, MediaCodecSelector mediaCodecSelector,
                                               @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                               boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler,
                                               @Nullable AudioRendererEventListener eventListener, @Nullable AudioCapabilities audioCapabilities,
                                               AudioProcessor... audioProcessors) {
        super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, audioCapabilities,
                audioProcessors);
    }

    public AudioDelayMediaCodecAudioRendererV1(Context context, MediaCodecSelector mediaCodecSelector,
                                               @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                               boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler,
                                               @Nullable AudioRendererEventListener eventListener, AudioSink audioSink) {
        super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, audioSink);
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
