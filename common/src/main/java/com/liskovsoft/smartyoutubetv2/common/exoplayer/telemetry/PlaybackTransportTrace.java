package com.liskovsoft.smartyoutubetv2.common.exoplayer.telemetry;

import com.liskovsoft.mediaserviceinterfaces.data.PlaybackRequestContext;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Redacted, correlation-safe vocabulary for player transport diagnostics. */
public final class PlaybackTransportTrace {
    public enum Protocol { HLS, DASH, DIRECT, SABR, UNKNOWN }
    public enum Track { AUDIO, VIDEO, UNKNOWN }
    public enum Stage {
        HLS_MASTER, HLS_MEDIA_PLAYLIST, HLS_INIT, HLS_AUDIO_SEGMENT,
        HLS_VIDEO_SEGMENT, HLS_KEY, DASH_MANIFEST, DASH_INIT,
        DASH_AUDIO_SEGMENT, DASH_VIDEO_SEGMENT, DIRECT_MEDIA, UNKNOWN
    }

    private static final Pattern EXPIRE_QUERY = Pattern.compile("(?:^|[?&])expire=([0-9]+)(?:&|$)");
    private static final Pattern EXPIRE_PATH = Pattern.compile("/expire/([0-9]+)(?=/)");

    private PlaybackTransportTrace() {
    }

    public static Stage classify(Protocol protocol, String url, boolean manifest,
                                 boolean firstManifest, boolean initialization, Track track) {
        String lower = url != null ? url.toLowerCase(Locale.US) : "";
        if (protocol == Protocol.HLS) {
            if (manifest) {
                return firstManifest ? Stage.HLS_MASTER : Stage.HLS_MEDIA_PLAYLIST;
            }
            if (lower.contains("/key/") || lower.endsWith(".key")) return Stage.HLS_KEY;
            if (initialization) return Stage.HLS_INIT;
            if (track == Track.AUDIO) return Stage.HLS_AUDIO_SEGMENT;
            return Stage.HLS_VIDEO_SEGMENT; // HLS transport commonly carries muxed A/V media.
        } else if (protocol == Protocol.DASH) {
            if (manifest) return Stage.DASH_MANIFEST;
            if (initialization) return Stage.DASH_INIT;
            if (track == Track.AUDIO) return Stage.DASH_AUDIO_SEGMENT;
            if (track == Track.VIDEO) return Stage.DASH_VIDEO_SEGMENT;
        } else if (protocol == Protocol.DIRECT) {
            return Stage.DIRECT_MEDIA;
        }
        return Stage.UNKNOWN;
    }

    public static String describeUrl(String url, long nowEpochSeconds) {
        if (url == null) return "host=none,path=none,nPath=false,potPath=false,potQuery=false,expiry=absent,urlHash=none";
        String lower = url.toLowerCase(Locale.US);
        String hostClass = "other";
        try {
            String host = URI.create(url).getHost();
            if (host != null && host.endsWith(".googlevideo.com")) hostClass = "googlevideo";
            else if (host != null && (host.endsWith(".youtube.com") || host.equals("youtube.com"))) hostClass = "youtube";
            else if (host != null && host.endsWith(".googleapis.com")) hostClass = "googleapis";
        } catch (IllegalArgumentException ignored) {
            hostClass = "invalid";
        }
        String pathClass = lower.contains(".m3u8") || lower.contains("/manifest/hls") ? "hls" :
                lower.contains(".mpd") || lower.contains("/manifest/dash") ? "dash" :
                lower.contains("/videoplayback") ? "media" : "other";
        Matcher expiry = EXPIRE_QUERY.matcher(url);
        String expiryClass = "absent";
        boolean expiryFound = expiry.find();
        if (!expiryFound) {
            expiry = EXPIRE_PATH.matcher(url);
            expiryFound = expiry.find();
        }
        if (expiryFound) {
            try {
                expiryClass = Long.parseLong(expiry.group(1)) > nowEpochSeconds ? "valid" : "expired";
            } catch (NumberFormatException error) {
                expiryClass = "invalid";
            }
        }
        return "host=" + hostClass +
                ",path=" + pathClass +
                ",nPath=" + lower.contains("/n/") +
                ",potPath=" + lower.contains("/pot/") +
                ",potQuery=" + (lower.contains("?pot=") || lower.contains("&pot=")) +
                ",expiry=" + expiryClass +
                ",urlHash=" + shortHash(url);
    }

    public static String describeContext(PlaybackRequestContext context) {
        if (context == null || context.getRequestClient() == null) {
            return "generation=none,client=unknown,uaHash=none,cpnHash=none";
        }
        return "generation=" + context.getGenerationId() +
                ",client=" + safeLabel(context.getRequestClient().getClientName()) +
                ",uaHash=" + shortHash(context.getRequestClient().getUserAgent()) +
                ",cpnHash=" + shortHash(context.getClientPlaybackNonce()) +
                ",contextExpiry=" + (context.getExpiresAtEpochMs() <= 0 ? "absent" :
                context.isExpired(System.currentTimeMillis()) ? "expired" : "valid");
    }

    public static String safeContentType(String value) {
        if (value == null) return "unknown";
        String normalized = value.split(";", 2)[0].trim().toLowerCase(Locale.US);
        return normalized.matches("[a-z0-9.+-]+/[a-z0-9.+-]+") ? normalized : "unknown";
    }

    public static String shortHash(String value) {
        if (value == null || value.isEmpty()) return "none";
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(12);
            for (int i = 0; i < 6; i++) result.append(String.format(Locale.US, "%02x", bytes[i]));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return "unavailable";
        }
    }

    private static String safeLabel(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,64}") ? value : "unknown";
    }
}
