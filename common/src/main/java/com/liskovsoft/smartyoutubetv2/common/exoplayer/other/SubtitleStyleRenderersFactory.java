package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

/**
 * My wrapper<br/>
 * Main intent: fix subtitle alignment on some videos
 */
public class SubtitleStyleRenderersFactory extends AudioDelayRenderersFactoryV2 {
    public SubtitleStyleRenderersFactory(FragmentActivity activity) {
        super(activity);
    }

    @Override
    public Renderer[] createRenderers(Handler eventHandler, VideoRendererEventListener videoRendererEventListener, AudioRendererEventListener audioRendererEventListener, TextOutput textRendererOutput, MetadataOutput metadataRendererOutput, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        textRendererOutput = new SubtitleStyleRendererDecorator(textRendererOutput);

        return super.createRenderers(
                eventHandler,
                videoRendererEventListener,
                audioRendererEventListener,
                textRendererOutput,
                metadataRendererOutput,
                drmSessionManager
        );
    }
}
