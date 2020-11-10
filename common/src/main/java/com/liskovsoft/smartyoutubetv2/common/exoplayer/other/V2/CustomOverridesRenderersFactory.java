package com.liskovsoft.smartyoutubetv2.common.exoplayer.other.V2;

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
 * Main intent: override audio delay
 */
public class CustomOverridesRenderersFactory extends DefaultRenderersFactory {
    private static final String TAG = CustomOverridesRenderersFactory.class.getSimpleName();

    public CustomOverridesRenderersFactory(FragmentActivity activity) {
        super(activity);
        //setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
    }

    /**
     * Delay audio<br/>
     * All real delay happens in {@link AudioDelayMediaCodecAudioRenderer}
     */
    @Override
    protected void buildAudioRenderers(
            Context context,
            @ExtensionRendererMode int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
            boolean playClearSamplesWithoutKeys,
            boolean enableDecoderFallback,
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
                enableDecoderFallback,
                audioProcessors,
                eventHandler,
                eventListener,
                out);

        Renderer originMediaCodecAudioRenderer = null;
        int index = 0;

        for (Renderer renderer : out) {
            if (renderer instanceof MediaCodecAudioRenderer) {
                originMediaCodecAudioRenderer = renderer;
                break;
            }
            index++;
        }

        if (originMediaCodecAudioRenderer != null) {
            // replace origin with custom
            out.remove(originMediaCodecAudioRenderer);
            out.add(index,
                    new AudioDelayMediaCodecAudioRenderer(
                            context,
                            mediaCodecSelector,
                            drmSessionManager,
                            playClearSamplesWithoutKeys,
                            enableDecoderFallback,
                            eventHandler,
                            eventListener,
                            new DefaultAudioSink(AudioCapabilities.getCapabilities(context), audioProcessors)));
        }
    }

    //@Override
    //protected void buildVideoRenderers(Context context,
    //                                   int extensionRendererMode,
    //                                   MediaCodecSelector mediaCodecSelector,
    //                                   @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
    //                                   boolean playClearSamplesWithoutKeys,
    //                                   boolean enableDecoderFallback,
    //                                   Handler eventHandler,
    //                                   VideoRendererEventListener eventListener,
    //                                   long allowedVideoJoiningTimeMs,
    //                                   ArrayList<Renderer> out) {
    //    super.buildVideoRenderers(
    //            context,
    //            extensionRendererMode,
    //            mediaCodecSelector,
    //            drmSessionManager,
    //            playClearSamplesWithoutKeys,
    //            enableDecoderFallback,
    //            eventHandler,
    //            eventListener,
    //            allowedVideoJoiningTimeMs,
    //            out);
    //
    //    Renderer originMediaCodecVideoRenderer = null;
    //    int index = 0;
    //
    //    for (Renderer renderer : out) {
    //        if (renderer instanceof MediaCodecVideoRenderer) {
    //            originMediaCodecVideoRenderer = renderer;
    //            break;
    //        }
    //        index++;
    //    }
    //
    //    if (originMediaCodecVideoRenderer != null) {
    //        // replace origin with custom
    //        out.remove(originMediaCodecVideoRenderer);
    //        out.add(index,
    //                new FrameDropFixMediaCodecVideoRenderer(
    //                        context,
    //                        mediaCodecSelector,
    //                        allowedVideoJoiningTimeMs,
    //                        drmSessionManager,
    //                        playClearSamplesWithoutKeys,
    //                        enableDecoderFallback,
    //                        eventHandler,
    //                        eventListener,
    //                        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
    //    }
    //}
}
