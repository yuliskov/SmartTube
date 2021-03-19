package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.V3;

import android.content.Context;
import android.os.Handler;
import androidx.fragment.app.FragmentActivity;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.common.CustomFixMediaCodecAudioRenderer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.common.CustomFixMediaCodecVideoRenderer;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

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
    private final PlayerData mPlayerData;
    private int mOperationMode = MediaCodecRenderer.OPERATION_MODE_SYNCHRONOUS;

    public CustomOverridesRenderersFactory(FragmentActivity activity) {
        super(activity);

        mPlayerData = PlayerData.instance(activity);

        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
        setEnableDecoderFallback(true);

        mOperationMode = MediaCodecRenderer.OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD_ASYNCHRONOUS_QUEUEING;
        experimentalSetMediaCodecOperationMode(mOperationMode);
    }

    @Override
    protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
                                       boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler,
                                       AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out);

        CustomFixMediaCodecAudioRenderer audioRenderer = null;

        if (mPlayerData.getAudioDelayMs() != 0) {
            audioRenderer = new CustomFixMediaCodecAudioRenderer(
                            context,
                            mediaCodecSelector,
                            enableDecoderFallback,
                            eventHandler,
                            eventListener,
                            audioSink);

            audioRenderer.setAudioDelayMs(mPlayerData.getAudioDelayMs());
        }

        if (audioRenderer != null) {
            // Restore global operation mode (needed for stability)
            audioRenderer.experimentalSetMediaCodecOperationMode(mOperationMode);

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
                out.add(index, audioRenderer);
            }
        }
    }

    @Override
    protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
                                       boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener,
                                       long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener,
                allowedVideoJoiningTimeMs, out);

        CustomFixMediaCodecVideoRenderer videoRenderer = null;

        if (mPlayerData.isFrameDropFixEnabled() || mPlayerData.isAmlogicFixEnabled()) {
            videoRenderer = new CustomFixMediaCodecVideoRenderer(
                    context,
                    mediaCodecSelector,
                    allowedVideoJoiningTimeMs,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);

            videoRenderer.enableFrameDropFix(mPlayerData.isFrameDropFixEnabled());
            videoRenderer.enableAmlogicFix(mPlayerData.isAmlogicFixEnabled());
        }

        if (videoRenderer != null) {
            // Restore global operation mode (needed for stability)
            videoRenderer.experimentalSetMediaCodecOperationMode(mOperationMode);

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
                out.add(index, videoRenderer);
            }
        }
    }
}
