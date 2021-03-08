package com.liskovsoft.smartyoutubetv2.common.playback.exoplayer.state;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;

import java.util.ArrayList;

/**
 * My wrapper<br/>
 * Main intent: override audio delay
 */
public class AudioDelayRenderersFactoryV1 extends DefaultRenderersFactory {
    private static final String TAG = AudioDelayRenderersFactoryV1.class.getSimpleName();

    public AudioDelayRenderersFactoryV1(FragmentActivity activity) {
        super(activity);
    }

    @Override
    protected void buildAudioRenderers(
            Context context,
            @ExtensionRendererMode int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
            boolean playClearSamplesWithoutKeys,
            AudioProcessor[] audioProcessors,
            Handler eventHandler,
            AudioRendererEventListener eventListener,
            ArrayList<Renderer> out) {
        super.buildAudioRenderers(
                context,
                extensionRendererMode,
                mediaCodecSelector,
                drmSessionManager,
                playClearSamplesWithoutKeys,
                audioProcessors,
                eventHandler,
                eventListener,
                out);

        Renderer mediaCodecAudioRenderer = null;

        for (Renderer renderer : out) {
            if (renderer instanceof MediaCodecAudioRenderer) {
                mediaCodecAudioRenderer = renderer;
                break;
            }
        }

        int index = out.indexOf(mediaCodecAudioRenderer);

        if (index != -1) {
            out.remove(mediaCodecAudioRenderer);
            out.add(index,
                    new AudioDelayMediaCodecAudioRendererV1(
                            context,
                            mediaCodecSelector,
                            drmSessionManager,
                            playClearSamplesWithoutKeys,
                            eventHandler,
                            eventListener,
                            new DefaultAudioSink(AudioCapabilities.getCapabilities(context), audioProcessors)));
        }
    }
}
