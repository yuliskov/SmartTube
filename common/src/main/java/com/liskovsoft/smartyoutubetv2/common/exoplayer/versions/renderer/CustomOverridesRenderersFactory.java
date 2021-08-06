package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.AmazonQuirks;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.BlacklistMediaCodecSelector;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import java.util.ArrayList;

/**
 * Main intent: override audio delay
 */
public class CustomOverridesRenderersFactory extends CustomRenderersFactoryBase {
    private static final String TAG = CustomOverridesRenderersFactory.class.getSimpleName();
    private static final String[] FRAME_DROP_FIX_LIST = {
            "T95ZPLUS (q201_3GB)",
            "UGOOS (UGOOS)",
            "55UC30G (ctl_iptv_mrvl)" // Kivi 55uc30g
    };
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;
    // 2.12, 2.13
    //private int mOperationMode = MediaCodecRenderer.OPERATION_MODE_SYNCHRONOUS;

    // 2.9, 2.10, 2.11
    public CustomOverridesRenderersFactory(Context activity) {
        super(activity);

        mPlayerData = PlayerData.instance(activity);
        mPlayerTweaksData = PlayerTweaksData.instance(activity);

        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
        // setEnableDecoderFallback(true); // Exo 2.10 and up

        if (mPlayerTweaksData.isSWDecoderForced()) {
            setMediaCodecSelector(new BlacklistMediaCodecSelector());
        }

        AmazonQuirks.disableSnappingToVsync(mPlayerTweaksData.isSnappingToVsyncDisabled());
        AmazonQuirks.skipProfileLevelCheck(mPlayerTweaksData.isProfileLevelCheckSkipped());
    }

    // 2.12, 2.13
    //public CustomOverridesRenderersFactory(Context activity) {
    //    super(activity);
    //
    //    mPlayerData = PlayerData.instance(activity);
    //    mPlayerTweaksData = PlayerTweaksData.instance(activity);
    //
    //    // Exo 2.12 (Exclusive experimental tweaks)
    //    //mOperationMode = MediaCodecRenderer.OPERATION_MODE_ASYNCHRONOUS_DEDICATED_THREAD_ASYNCHRONOUS_QUEUEING;
    //    //experimentalSetMediaCodecOperationMode(mOperationMode);
    //
    //    setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
    //    // setEnableDecoderFallback(true); // Exo 2.10 and up
    //
    //    if (mPlayerTweaksData.isSWDecoderForced()) {
    //        setMediaCodecSelector(new BlacklistMediaCodecSelector());
    //    }
    //
    //    AmazonQuirks.skipProfileLevelCheck(mPlayerTweaksData.isProfileLevelCheckSkipped());
    //}

    // Exo 2.9
    //@Override
    //protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
    //                                   @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                   AudioProcessor[] audioProcessors, Handler eventHandler, AudioRendererEventListener eventListener,
    //                                   ArrayList<Renderer> out) {
    //    super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys,
    //            audioProcessors, eventHandler, eventListener, out);
    //
    //    CustomMediaCodecAudioRenderer audioRenderer = null;
    //
    //    if (mPlayerData.getAudioDelayMs() != 0) {
    //        audioRenderer =
    //                new CustomMediaCodecAudioRenderer(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler,
    //                        eventListener, new DefaultAudioSink(AudioCapabilities.getCapabilities(context), audioProcessors));
    //
    //        audioRenderer.setAudioDelayMs(mPlayerData.getAudioDelayMs());
    //    }
    //
    //    replaceAudioRenderer(out, audioRenderer);
    //}

    // Exo 2.9
    //@Override
    //protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
    //                                   @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                   Handler eventHandler, VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs,
    //                                   ArrayList<Renderer> out) {
    //    super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler,
    //            eventListener, allowedVideoJoiningTimeMs, out);
    //
    //    CustomMediaCodecVideoRenderer videoRenderer = null;
    //
    //    if (mPlayerTweaksData.isFrameDropFixEnabled() || mPlayerTweaksData.isAmlogicFixEnabled()) {
    //        videoRenderer = new CustomMediaCodecVideoRenderer(context, mediaCodecSelector, allowedVideoJoiningTimeMs, drmSessionManager,
    //                playClearSamplesWithoutKeys, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    //
    //        videoRenderer.enableFrameDropFix(mPlayerTweaksData.isFrameDropFixEnabled());
    //        videoRenderer.enableAmlogicFix(mPlayerTweaksData.isAmlogicFixEnabled());
    //    }
    //
    //    replaceVideoRenderer(out, videoRenderer);
    //}

    // 2.10, 2.11
    @Override
    protected void buildAudioRenderers(Context context, @ExtensionRendererMode int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
                                       @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
                                       boolean enableDecoderFallback, AudioProcessor[] audioProcessors, Handler eventHandler,
                                       AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys,
                enableDecoderFallback, audioProcessors, eventHandler, eventListener, out);

        if (mPlayerData.getAudioDelayMs() == 0 && !mPlayerTweaksData.isAudioSyncFixEnabled()) {
            // Improve performance a bit by eliminating calculations presented in custom renderer.

            return;
        }

        DelayMediaCodecAudioRenderer audioRenderer =
                new DelayMediaCodecAudioRenderer(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback,
                        eventHandler, eventListener, new DefaultAudioSink(AudioCapabilities.getCapabilities(context), audioProcessors));

        audioRenderer.setAudioDelayMs(mPlayerData.getAudioDelayMs());
        audioRenderer.enableAudioSyncFix(mPlayerTweaksData.isAudioSyncFixEnabled());

        replaceAudioRenderer(out, audioRenderer);
    }

    // 2.10, 2.11
    @Override
    protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector,
                                       @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
                                       boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener,
                                       long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys,
                enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out);

        if (!mPlayerTweaksData.isFrameDropFixEnabled() && !mPlayerTweaksData.isAmlogicFixEnabled()) {
            // Improve performance a bit by eliminating some if conditions presented in tweaks.
            // But we need to obtain codec real name somehow. So use interceptor below.

            DebugInfoMediaCodecVideoRenderer videoRenderer =
                    new DebugInfoMediaCodecVideoRenderer(context, mediaCodecSelector, allowedVideoJoiningTimeMs, drmSessionManager,
                        playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);

            replaceVideoRenderer(out, videoRenderer);
            videoRenderer.enableSetOutputSurfaceWorkaround(mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled());

            return;
        }

        TweaksMediaCodecVideoRenderer videoRenderer =
                new TweaksMediaCodecVideoRenderer(context, mediaCodecSelector, allowedVideoJoiningTimeMs, drmSessionManager,
                        playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);

        videoRenderer.enableFrameDropFix(mPlayerTweaksData.isFrameDropFixEnabled());
        videoRenderer.enableAmlogicFix(mPlayerTweaksData.isAmlogicFixEnabled());
        videoRenderer.enableSetOutputSurfaceWorkaround(mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled());

        replaceVideoRenderer(out, videoRenderer);
    }

    // Exo 2.12, 2.13
    //@Override
    //protected void buildAudioRenderers(Context context,
    //                                   int extensionRendererMode,
    //                                   MediaCodecSelector mediaCodecSelector,
    //                                   boolean enableDecoderFallback,
    //                                   AudioSink audioSink,
    //                                   Handler eventHandler,
    //                                   AudioRendererEventListener eventListener,
    //                                   ArrayList<Renderer> out) {
    //    super.buildAudioRenderers(
    //            context,
    //            extensionRendererMode,
    //            mediaCodecSelector,
    //            enableDecoderFallback,
    //            audioSink,
    //            eventHandler,
    //            eventListener,
    //            out);
    //
    //    if (mPlayerData.getAudioDelayMs() == 0) {
    //        // Improve performance a bit by eliminating calculations presented in custom renderer.
    //
    //        return;
    //    }
    //
    //    DelayMediaCodecAudioRenderer audioRenderer =
    //            new DelayMediaCodecAudioRenderer(context,
    //                    mediaCodecSelector,
    //                    enableDecoderFallback,
    //                    eventHandler,
    //                    eventListener,
    //                    audioSink);
    //
    //    audioRenderer.setAudioDelayMs(mPlayerData.getAudioDelayMs());
    //
    //    // Restore global operation mode (needed for stability)
    //    //audioRenderer.experimentalSetMediaCodecOperationMode(mOperationMode);
    //
    //    replaceAudioRenderer(out, audioRenderer);
    //}

    // Exo 2.12, 2.13
    //@Override
    //protected void buildVideoRenderers(Context context,
    //                                   int extensionRendererMode,
    //                                   MediaCodecSelector mediaCodecSelector,
    //                                   boolean enableDecoderFallback,
    //                                   Handler eventHandler,
    //                                   VideoRendererEventListener eventListener,
    //                                   long allowedVideoJoiningTimeMs,
    //                                   ArrayList<Renderer> out) {
    //    super.buildVideoRenderers(
    //            context,
    //            extensionRendererMode,
    //            mediaCodecSelector,
    //            enableDecoderFallback,
    //            eventHandler,
    //            eventListener,
    //            allowedVideoJoiningTimeMs,
    //            out);
    //
    //
    //    if (!mPlayerTweaksData.isFrameDropFixEnabled() && !mPlayerTweaksData.isAmlogicFixEnabled()) {
    //        // Improve performance a bit by eliminating some if conditions presented in tweaks.
    //        // But we need to obtain codec real name somehow. So use interceptor below.
    //
    //        DebugInfoMediaCodecVideoRenderer videoRenderer =
    //                new DebugInfoMediaCodecVideoRenderer(context,
    //                        mediaCodecSelector,
    //                        allowedVideoJoiningTimeMs,
    //                        enableDecoderFallback,
    //                        eventHandler,
    //                        eventListener,
    //                        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    //
    //        // Restore global operation mode (needed for stability)
    //        //videoRenderer.experimentalSetMediaCodecOperationMode(mOperationMode);
    //        videoRenderer.enableSetOutputSurfaceWorkaround(mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled());
    //
    //        replaceVideoRenderer(out, videoRenderer);
    //
    //        return;
    //    }
    //
    //    TweaksMediaCodecVideoRenderer videoRenderer =
    //            new TweaksMediaCodecVideoRenderer(context,
    //                    mediaCodecSelector,
    //                    allowedVideoJoiningTimeMs,
    //                    enableDecoderFallback,
    //                    eventHandler,
    //                    eventListener,
    //                    MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    //
    //    videoRenderer.enableFrameDropFix(mPlayerTweaksData.isFrameDropFixEnabled());
    //    videoRenderer.enableAmlogicFix(mPlayerTweaksData.isAmlogicFixEnabled());
    //    videoRenderer.enableSetOutputSurfaceWorkaround(mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled());
    //
    //    // Restore global operation mode (needed for stability)
    //    //videoRenderer.experimentalSetMediaCodecOperationMode(mOperationMode);
    //
    //    replaceVideoRenderer(out, videoRenderer);
    //}
}
