package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.PlaybackRequestContext;

/** Immutable source-capability snapshot derived from one player response generation. */
public final class LivePlaybackDescriptor {
    public final String videoId;
    public final boolean live;
    public final boolean liveContent;
    public final boolean seekable;
    public final boolean unplayable;
    public final String playabilityReason;
    public final boolean sabr;
    public final boolean dashFormats;
    public final boolean dashManifest;
    public final boolean hls;

    public LivePlaybackDescriptor(String videoId, boolean live, boolean liveContent,
                                  boolean seekable, boolean unplayable, String playabilityReason,
                                  boolean sabr, boolean dashFormats, boolean dashManifest, boolean hls) {
        this.videoId = videoId;
        this.live = live;
        this.liveContent = liveContent;
        this.seekable = seekable;
        this.unplayable = unplayable;
        this.playabilityReason = playabilityReason;
        this.sabr = sabr;
        this.dashFormats = dashFormats;
        this.dashManifest = dashManifest;
        this.hls = hls;
    }

    public static LivePlaybackDescriptor from(MediaItemFormatInfo info) {
        PlaybackRequestContext context = info.getPlaybackRequestContext();
        boolean sabr = info.containsSabrFormats()
                && !TextUtils.isEmpty(info.getServerAbrStreamingUrl())
                && !TextUtils.isEmpty(info.getVideoPlaybackUstreamerConfig())
                && context != null
                && context.getRequestClient() != null
                && !context.isExpired(System.currentTimeMillis())
                && (!context.isStreamingProofRequired()
                    || !TextUtils.isEmpty(context.getStreamingDataPoToken()));
        return new LivePlaybackDescriptor(
                info.getVideoId(), info.isLive(), info.isLiveContent(), info.isStreamSeekable(),
                info.isUnplayable(), info.getPlayabilityReason(), sabr,
                info.containsDashFormats(),
                info.containsDashUrl() && LiveManifestValidator.isValid(info.getDashManifestUrl()),
                info.containsHlsUrl() && LiveManifestValidator.isValid(info.getHlsManifestUrl()));
    }
}
