package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

/** Immutable result of resolving one channel reference to a broadcast. */
public final class LiveChannelResolution {
    public enum Status {
        LIVE, UPCOMING, OFFLINE, RESTRICTED, UNAVAILABLE,
        NETWORK_ERROR, RESOLUTION_ERROR, CANCELLED
    }

    public final Status status;
    public final String videoId;
    public final String channelId;
    public final String channelName;
    public final String title;
    public final String thumbnailUrl;
    public final String reason;

    private LiveChannelResolution(Status status, LiveChannelResolver.Candidate item, String reason) {
        this.status = status;
        videoId = item != null ? item.videoId : null;
        channelId = item != null ? item.channelId : null;
        channelName = item != null ? item.channelName : null;
        title = item != null ? item.title : null;
        thumbnailUrl = item != null ? item.thumbnailUrl : null;
        this.reason = reason;
    }

    static LiveChannelResolution live(LiveChannelResolver.Candidate item) {
        return new LiveChannelResolution(Status.LIVE, item, null);
    }

    static LiveChannelResolution upcoming(LiveChannelResolver.Candidate item) {
        return new LiveChannelResolution(Status.UPCOMING, item, "The next broadcast is scheduled but not live");
    }

    static LiveChannelResolution offline() {
        return new LiveChannelResolution(Status.OFFLINE, null, "The channel is not currently live");
    }

    static LiveChannelResolution unavailable(String reason) {
        return new LiveChannelResolution(Status.UNAVAILABLE, null,
                reason != null ? reason : "The channel is unavailable or restricted");
    }

    static LiveChannelResolution restricted(String reason) {
        return new LiveChannelResolution(Status.RESTRICTED, null,
                reason != null ? reason : "The broadcast is restricted in this playback context");
    }

    static LiveChannelResolution networkError(String reason) {
        return new LiveChannelResolution(Status.NETWORK_ERROR, null,
                reason != null ? reason : "Network error while resolving the channel");
    }

    static LiveChannelResolution resolutionError(String reason) {
        return new LiveChannelResolution(Status.RESOLUTION_ERROR, null,
                reason != null ? reason : "The live-channel response was inconsistent");
    }
}
