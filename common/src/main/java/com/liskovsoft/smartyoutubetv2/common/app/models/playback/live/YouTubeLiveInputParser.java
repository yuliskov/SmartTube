package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes supported YouTube identifiers and URLs. This class performs no network work. */
public final class YouTubeLiveInputParser {
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern CHANNEL_ID = Pattern.compile("^UC[A-Za-z0-9_-]{22}$");
    private static final Pattern HANDLE = Pattern.compile("^@[A-Za-z0-9._-]{3,30}$");

    public LiveInput parse(String rawInput) {
        String value = rawInput != null ? rawInput.trim() : "";
        if (VIDEO_ID.matcher(value).matches()) {
            return video(value);
        }
        if (CHANNEL_ID.matcher(value).matches()) {
            return channel(value);
        }
        if (HANDLE.matcher(value).matches()) {
            return channel(value);
        }
        if (value.isEmpty()) {
            throw invalid("Enter a YouTube video, channel, or @handle");
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException error) {
            throw invalid("Malformed YouTube URL");
        }
        String scheme = lower(uri.getScheme());
        if (!("http".equals(scheme) || "https".equals(scheme))) {
            throw invalid("Only http or https YouTube URLs are supported");
        }
        String host = normalizeHost(uri.getHost());
        if (host == null) {
            throw invalid("YouTube URL host is missing");
        }
        String[] path = pathSegments(uri.getPath());

        if ("youtu.be".equals(host)) {
            if (path.length == 1 && VIDEO_ID.matcher(path[0]).matches()) {
                return video(path[0]);
            }
            throw invalid("Malformed youtu.be video URL");
        }
        if (!"youtube.com".equals(host)) {
            throw invalid("Unsupported host: " + host);
        }

        if (path.length == 1 && "watch".equals(path[0])) {
            String id = queryParameter(uri.getRawQuery(), "v");
            if (id != null && VIDEO_ID.matcher(id).matches()) {
                return video(id);
            }
            throw invalid("YouTube watch URL is missing a valid video ID");
        }
        if (path.length == 2 && "live".equals(path[0]) && VIDEO_ID.matcher(path[1]).matches()) {
            return video(path[1]);
        }
        if (path.length >= 2 && "channel".equals(path[0]) && CHANNEL_ID.matcher(path[1]).matches()
                && (path.length == 2 || path.length == 3 && "live".equals(path[2]))) {
            return channel(path[1]);
        }
        if (path.length >= 1 && HANDLE.matcher(path[0]).matches()
                && (path.length == 1 || path.length == 2 && "live".equals(path[1]))) {
            return channel(path[0]);
        }
        if (path.length >= 2 && ("c".equals(path[0]) || "user".equals(path[0]))
                && path[1].matches("[A-Za-z0-9._-]{3,30}")
                && (path.length == 2 || path.length == 3 && "live".equals(path[2]))) {
            return channel("@" + path[1]);
        }
        throw invalid("Unsupported YouTube URL path");
    }

    private static LiveInput video(String id) {
        return new LiveInput(LiveInput.Type.VIDEO, id, "https://www.youtube.com/watch?v=" + id);
    }

    private static LiveInput channel(String reference) {
        String path = reference.startsWith("@") ? reference : "channel/" + reference;
        return new LiveInput(LiveInput.Type.CHANNEL, reference, "https://www.youtube.com/" + path + "/live");
    }

    private static String normalizeHost(String host) {
        String result = lower(host);
        if (result == null) return null;
        if (result.startsWith("www.")) result = result.substring(4);
        if (result.startsWith("m.")) result = result.substring(2);
        if (result.startsWith("music.")) result = result.substring(6);
        return result;
    }

    private static String[] pathSegments(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || "/".equals(rawPath)) return new String[0];
        String path = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path.isEmpty() ? new String[0] : path.split("/");
    }

    private static String queryParameter(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] pieces = pair.split("=", 2);
            if (pieces.length == 2 && key.equals(decode(pieces[0]))) return decode(pieces[1]);
        }
        return null;
    }

    private static String decode(String value) {
        try {
            // The Charset overload requires API 33; the named-charset overload works on minSdk 17.
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable");
        }
    }

    private static String lower(String value) {
        return value != null ? value.toLowerCase(Locale.US) : null;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
