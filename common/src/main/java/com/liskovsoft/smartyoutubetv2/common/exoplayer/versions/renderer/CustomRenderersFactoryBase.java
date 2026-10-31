package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.content.Context;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;

import java.util.ArrayList;

public abstract class CustomRenderersFactoryBase extends DefaultRenderersFactory {
    public CustomRenderersFactoryBase(Context context) {
        super(context);
    }

    protected void replaceVideoRenderer(ArrayList<Renderer> renderers, MediaCodecVideoRenderer videoRenderer) {
        if (renderers != null && videoRenderer != null) {
            Renderer originMediaCodecVideoRenderer = null;
            int index = 0;

            for (Renderer renderer : renderers) {
                if (renderer instanceof MediaCodecVideoRenderer) {
                    originMediaCodecVideoRenderer = renderer;
                    break;
                }
                index++;
            }

            if (originMediaCodecVideoRenderer != null) {
                // replace origin with custom
                renderers.remove(originMediaCodecVideoRenderer);
                renderers.add(index, videoRenderer);
            }
        }
    }

    protected void replaceAudioRenderer(ArrayList<Renderer> renderers, MediaCodecAudioRenderer audioRenderer) {
        if (renderers != null && audioRenderer != null) {
            Renderer originMediaCodecAudioRenderer = null;
            int index = 0;

            for (Renderer renderer : renderers) {
                if (renderer instanceof MediaCodecAudioRenderer) {
                    originMediaCodecAudioRenderer = renderer;
                    break;
                }
                index++;
            }

            if (originMediaCodecAudioRenderer != null) {
                // replace origin with custom
                renderers.remove(originMediaCodecAudioRenderer);
                renderers.add(index, audioRenderer);
            }
        }
    }
}
