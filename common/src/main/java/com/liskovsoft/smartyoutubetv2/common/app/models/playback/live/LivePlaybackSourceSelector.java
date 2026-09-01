package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import java.util.Set;

/** Pure HLS -> live DASH -> adaptive DASH -> gated SABR policy. */
public final class LivePlaybackSourceSelector {
    public enum Source { SABR, DASH_FORMATS, DASH_MANIFEST, HLS }
    public enum Mode { AUTO, FORCE_HLS, FORCE_DASH_MANIFEST, FORCE_ADAPTIVE_DASH, FORCE_SABR }

    public static final class Configuration {
        public final boolean sabrEnabled;
        public final boolean dashFormatsEnabled;
        public final boolean dashManifestEnabled;
        public final boolean hlsEnabled;
        public final Mode mode;

        public Configuration(boolean sabrEnabled, boolean dashFormatsEnabled,
                             boolean dashManifestEnabled, boolean hlsEnabled) {
            this(sabrEnabled, dashFormatsEnabled, dashManifestEnabled, hlsEnabled, Mode.AUTO);
        }

        public Configuration(boolean sabrEnabled, boolean dashFormatsEnabled,
                             boolean dashManifestEnabled, boolean hlsEnabled, Mode mode) {
            this.sabrEnabled = sabrEnabled;
            this.dashFormatsEnabled = dashFormatsEnabled;
            this.dashManifestEnabled = dashManifestEnabled;
            this.hlsEnabled = hlsEnabled;
            this.mode = mode != null ? mode : Mode.AUTO;
        }
    }

    public static final class Decision {
        public final Source source;
        public final String reason;

        private Decision(Source source, String reason) {
            this.source = source;
            this.reason = reason;
        }

        static Decision use(Source source, String reason) {
            return new Decision(source, reason);
        }

        static Decision unavailable(String reason) {
            return new Decision(null, reason);
        }

        public boolean isAvailable() {
            return source != null;
        }
    }

    public Decision select(LivePlaybackDescriptor descriptor, Configuration config,
                           Set<Source> attempted) {
        if (descriptor.unplayable) {
            return Decision.unavailable(descriptor.playabilityReason != null
                    ? descriptor.playabilityReason : "Player response is unplayable");
        }
        if (!descriptor.live && !descriptor.liveContent) {
            return Decision.unavailable("Player response is not live content");
        }
        if (config.mode != Mode.AUTO) {
            return selectForced(descriptor, config, attempted);
        }
        if (config.hlsEnabled && descriptor.hls && !attempted.contains(Source.HLS)) {
            return Decision.use(Source.HLS, "Live HLS manifest is available");
        }
        if (config.dashManifestEnabled && descriptor.dashManifest
                && !attempted.contains(Source.DASH_MANIFEST)) {
            return Decision.use(Source.DASH_MANIFEST, "Live DASH manifest is available");
        }
        if (config.dashFormatsEnabled && descriptor.dashFormats
                && !attempted.contains(Source.DASH_FORMATS)) {
            return Decision.use(Source.DASH_FORMATS, "Adaptive DASH formats are available");
        }
        if (config.sabrEnabled && descriptor.sabr && !attempted.contains(Source.SABR)) {
            return Decision.use(Source.SABR, "Experimental SABR live bootstrap is complete");
        }
        return Decision.unavailable("No untried live playback source is available");
    }

    private Decision selectForced(LivePlaybackDescriptor descriptor, Configuration config,
                                  Set<Source> attempted) {
        Source forced;
        boolean enabled;
        boolean available;
        switch (config.mode) {
            case FORCE_HLS:
                forced = Source.HLS; enabled = config.hlsEnabled; available = descriptor.hls; break;
            case FORCE_DASH_MANIFEST:
                forced = Source.DASH_MANIFEST; enabled = config.dashManifestEnabled;
                available = descriptor.dashManifest; break;
            case FORCE_ADAPTIVE_DASH:
                forced = Source.DASH_FORMATS; enabled = config.dashFormatsEnabled;
                available = descriptor.dashFormats; break;
            case FORCE_SABR:
                forced = Source.SABR; enabled = config.sabrEnabled; available = descriptor.sabr; break;
            default:
                return Decision.unavailable("Unsupported forced live source");
        }
        return enabled && available && !attempted.contains(forced)
                ? Decision.use(forced, "Debug source override: " + config.mode.name())
                : Decision.unavailable("Forced live source is unavailable or already tried");
    }
}
