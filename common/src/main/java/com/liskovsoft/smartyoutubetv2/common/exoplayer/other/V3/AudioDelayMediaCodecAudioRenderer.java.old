package com.liskovsoft.smartyoutubetv2.common.exoplayer.other.V3;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;

public class AudioDelayMediaCodecAudioRenderer extends MediaCodecAudioRenderer {
    private static final String TAG = AudioDelayMediaCodecAudioRenderer.class.getSimpleName();
    private int mDelayUs;

    public AudioDelayMediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
        super(context, mediaCodecSelector);
    }

    public AudioDelayMediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                             @Nullable Handler eventHandler,
                                             @Nullable AudioRendererEventListener eventListener) {
        super(context, mediaCodecSelector, eventHandler, eventListener);
    }

    public AudioDelayMediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                             @Nullable Handler eventHandler,
                                             @Nullable AudioRendererEventListener eventListener,
                                             @Nullable AudioCapabilities audioCapabilities,
                                             AudioProcessor... audioProcessors) {
        super(context, mediaCodecSelector, eventHandler, eventListener, audioCapabilities, audioProcessors);
    }

    public AudioDelayMediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector,
                                             @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, AudioSink audioSink) {
        super(context, mediaCodecSelector, eventHandler, eventListener, audioSink);
    }

    public AudioDelayMediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, AudioSink audioSink) {
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
