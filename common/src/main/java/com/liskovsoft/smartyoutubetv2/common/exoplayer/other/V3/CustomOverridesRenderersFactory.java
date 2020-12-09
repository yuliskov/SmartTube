package com.liskovsoft.smartyoutubetv2.common.exoplayer.other.V3;

import android.content.Context;
import android.os.Handler;
import androidx.fragment.app.FragmentActivity;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;

/**
 * Main intent: override audio delay
 */
public class CustomOverridesRenderersFactory extends DefaultRenderersFactory {
    private static final String TAG = CustomOverridesRenderersFactory.class.getSimpleName();
    private static final String[] FRAME_DROP_FIX_LIST = {
            "T95ZPLUS (q201_3GB)",
            "UGOOS (UGOOS)",
            "55UC30G (ctl_iptv_mrvl)" // Kivi 55uc30g
    };

    public CustomOverridesRenderersFactory(FragmentActivity activity) {
        super(activity);
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
        //experimentalSetVideoMediaCodecOperationMode(MediaCodecRenderer.OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD);
    }

    @Override
    protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
                                       boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler,
                                       AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);

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
                            enableDecoderFallback,
                            eventHandler,
                            eventListener,
                            audioSink));
        }
    }

    @Override
    protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
                                       boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener,
                                       long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener,
                allowedVideoJoiningTimeMs, out);

        if (!Helpers.contains(FRAME_DROP_FIX_LIST, Helpers.getDeviceName())) {
            return;
        }

        Renderer originMediaCodecVideoRenderer = null;
        int index = 0;

        for (Renderer renderer : out) {
            if (renderer instanceof MediaCodecVideoRenderer) {
                originMediaCodecVideoRenderer = renderer;
                break;
            }
            index++;
        }

        if (originMediaCodecVideoRenderer != null) {
            // replace origin with custom
            out.remove(originMediaCodecVideoRenderer);
            out.add(index,
                    new FrameDropFixMediaCodecVideoRenderer(
                            context,
                            mediaCodecSelector,
                            allowedVideoJoiningTimeMs,
                            enableDecoderFallback,
                            eventHandler,
                            eventListener,
                            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
        }
    }
}
