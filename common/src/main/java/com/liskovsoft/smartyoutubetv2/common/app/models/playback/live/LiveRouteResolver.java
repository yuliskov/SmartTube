package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Resolves YouTube's canonical channel /live route without retaining or logging response data. */
final class LiveRouteResolver {
    private static final int MAX_BODY_CHARS = 2 * 1024 * 1024;
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern CANONICAL_WATCH = Pattern.compile(
            "(?:canonical|canonicalUrl)[^\\n]{0,240}?(?:watch\\?v=|watch\\\\u003fv\\\\u003d)([A-Za-z0-9_-]{11})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CANONICAL_CHANNEL = Pattern.compile(
            "(?:canonical|channelId|browseId)[^\\n]{0,240}?(UC[A-Za-z0-9_-]{22})",
            Pattern.CASE_INSENSITIVE);

    static final class Snapshot {
        final String canonicalChannelId;
        final String videoId;
        final LiveCandidateStrategy.Result.Status status;
        final String reason;

        Snapshot(String canonicalChannelId, String videoId,
                 LiveCandidateStrategy.Result.Status status, String reason) {
            this.canonicalChannelId = canonicalChannelId;
            this.videoId = videoId;
            this.status = status;
            this.reason = reason;
        }

        static Snapshot networkError(String reason) {
            return new Snapshot(null, null, LiveCandidateStrategy.Result.Status.NETWORK_ERROR, reason);
        }

        static Snapshot resolutionError(String reason) {
            return new Snapshot(null, null, LiveCandidateStrategy.Result.Status.RESOLUTION_ERROR, reason);
        }
    }

    private final OkHttpClient client;

    LiveRouteResolver() {
        client = OkHttpManager.instance().getClient().newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    Observable<Snapshot> resolve(String channelReference) {
        return Observable.create(emitter -> {
            String route = routeFor(channelReference);
            if (route == null) {
                emitter.onNext(Snapshot.resolutionError("Unsupported canonical live route"));
                emitter.onComplete();
                return;
            }
            Call call = client.newCall(new Request.Builder()
                    .url(route)
                    .header("Accept-Language", "en-US,en;q=0.8")
                    .build());
            emitter.setCancellable(call::cancel);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call ignored, IOException error) {
                    if (!emitter.isDisposed()) {
                        emitter.onNext(Snapshot.networkError("Canonical live route request failed"));
                        emitter.onComplete();
                    }
                }

                @Override
                public void onResponse(Call ignored, Response response) {
                    try (Response closeable = response) {
                        if (emitter.isDisposed()) return;
                        if (!response.isSuccessful()) {
                            Snapshot failure = response.code() >= 500
                                    ? Snapshot.networkError("Canonical live route server failure")
                                    : Snapshot.resolutionError("Canonical live route was unavailable");
                            emitter.onNext(failure);
                            emitter.onComplete();
                            return;
                        }
                        String body = readBounded(response.body());
                        String fallbackChannel = isChannelId(channelReference) ? channelReference : null;
                        String canonicalId = extractCanonicalChannelId(body, fallbackChannel);
                        String videoId = extractVideoId(response.request().url(), body);
                        emitter.onNext(new Snapshot(canonicalId, videoId,
                                LiveCandidateStrategy.Result.Status.SUCCESS, null));
                        emitter.onComplete();
                    } catch (IOException error) {
                        if (!emitter.isDisposed()) {
                            emitter.onNext(Snapshot.networkError("Canonical live route response failed"));
                            emitter.onComplete();
                        }
                    }
                }
            });
        });
    }

    static String extractVideoId(HttpUrl finalUrl, String body) {
        if (finalUrl != null) {
            String queryId = finalUrl.queryParameter("v");
            if (isVideoId(queryId)) return queryId;
            if (finalUrl.pathSegments().size() >= 2
                    && "live".equals(finalUrl.pathSegments().get(0))) {
                String pathId = finalUrl.pathSegments().get(1);
                if (isVideoId(pathId)) return pathId;
            }
        }
        Matcher matcher = CANONICAL_WATCH.matcher(body != null ? body : "");
        return matcher.find() ? matcher.group(1) : null;
    }

    static String extractCanonicalChannelId(String body, String fallback) {
        if (isChannelId(fallback)) return fallback;
        Matcher matcher = CANONICAL_CHANNEL.matcher(body != null ? body : "");
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String routeFor(String reference) {
        if (isChannelId(reference)) {
            return "https://www.youtube.com/channel/" + reference + "/live";
        }
        if (reference != null && reference.matches("@[A-Za-z0-9._-]{3,30}")) {
            return "https://www.youtube.com/" + reference + "/live";
        }
        return null;
    }

    private static boolean isChannelId(String value) {
        return value != null && value.matches("UC[A-Za-z0-9_-]{22}");
    }

    private static boolean isVideoId(String value) {
        return value != null && VIDEO_ID.matcher(value).matches();
    }

    private static String readBounded(ResponseBody body) throws IOException {
        if (body == null) return "";
        Reader reader = body.charStream();
        StringBuilder result = new StringBuilder(Math.min(MAX_BODY_CHARS, 32 * 1024));
        char[] buffer = new char[8192];
        int remaining = MAX_BODY_CHARS;
        int read;
        while (remaining > 0 && (read = reader.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
            result.append(buffer, 0, read);
            remaining -= read;
        }
        return result.toString();
    }
}
