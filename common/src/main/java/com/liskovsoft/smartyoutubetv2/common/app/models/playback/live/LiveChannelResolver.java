package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;

/** Resolves channel inputs with bounded caching; disposing the returned stream cancels backend work. */
public final class LiveChannelResolver {
    public interface Clock { long nowMs(); }

    public interface Provider {
        Observable<ProviderResult> load(String channelReference);
    }

    public static final class Candidate {
        public final String videoId;
        public final String channelId;
        public final String channelName;
        public final String title;
        public final String thumbnailUrl;
        public final boolean live;
        public final boolean upcoming;
        public final String strategy;

        public Candidate(String videoId, String channelId, String channelName, String title,
                         String thumbnailUrl, boolean live, boolean upcoming) {
            this(videoId, channelId, channelName, title, thumbnailUrl, live, upcoming, null);
        }

        public Candidate(String videoId, String channelId, String channelName, String title,
                         String thumbnailUrl, boolean live, boolean upcoming, String strategy) {
            this.videoId = videoId;
            this.channelId = channelId;
            this.channelName = channelName;
            this.title = title;
            this.thumbnailUrl = thumbnailUrl;
            this.live = live;
            this.upcoming = upcoming;
            this.strategy = strategy;
        }
    }

    public static final class ProviderResult {
        public final List<Candidate> candidates;
        public final String unavailableReason;
        public final String restrictedReason;
        public final String networkError;
        public final String resolutionError;

        private ProviderResult(List<Candidate> candidates, String unavailableReason,
                               String restrictedReason, String networkError,
                               String resolutionError) {
            this.candidates = candidates != null ? candidates : Collections.emptyList();
            this.unavailableReason = unavailableReason;
            this.restrictedReason = restrictedReason;
            this.networkError = networkError;
            this.resolutionError = resolutionError;
        }

        public static ProviderResult available(List<Candidate> candidates) {
            return new ProviderResult(candidates, null, null, null, null);
        }

        public static ProviderResult unavailable(String reason) {
            return new ProviderResult(Collections.emptyList(), reason, null, null, null);
        }

        public static ProviderResult restricted(String reason) {
            return new ProviderResult(Collections.emptyList(), null, reason, null, null);
        }

        public static ProviderResult networkError(String reason) {
            return new ProviderResult(Collections.emptyList(), null, null, reason, null);
        }

        public static ProviderResult resolutionError(String reason) {
            return new ProviderResult(Collections.emptyList(), null, null, null, reason);
        }
    }

    private static final long DEFAULT_CACHE_MS = 30_000;
    private final Provider provider;
    private final Clock clock;
    private final long cacheMs;
    private final Map<String, CacheEntry> cache = new HashMap<>();

    public LiveChannelResolver(Provider provider) {
        this(provider, System::currentTimeMillis, DEFAULT_CACHE_MS);
    }

    public LiveChannelResolver(Provider provider, Clock clock, long cacheMs) {
        this.provider = provider;
        this.clock = clock;
        this.cacheMs = Math.max(0, cacheMs);
    }

    public Observable<LiveChannelResolution> resolve(LiveInput input, boolean forceRefresh) {
        if (input == null || input.getType() != LiveInput.Type.CHANNEL) {
            return Observable.error(new IllegalArgumentException("A channel input is required"));
        }
        String key = input.getValue();
        CacheEntry entry = cache.get(key);
        long now = clock.nowMs();
        if (!forceRefresh && entry != null && now < entry.expiresAtMs) {
            return Observable.just(entry.resolution);
        }
        return provider.load(key)
                .take(1)
                .map(this::classify)
                .onErrorReturn(error -> LiveChannelResolution.networkError("Channel resolution failed"))
                .doOnNext(result -> cacheResult(key, result));
    }

    public void invalidate(String channelReference) {
        cache.remove(channelReference);
    }

    private LiveChannelResolution classify(ProviderResult result) {
        if (result.networkError != null) {
            return LiveChannelResolution.networkError(result.networkError);
        }
        if (result.resolutionError != null) {
            return LiveChannelResolution.resolutionError(result.resolutionError);
        }
        if (result.unavailableReason != null) {
            return LiveChannelResolution.unavailable(result.unavailableReason);
        }
        if (result.restrictedReason != null) {
            return LiveChannelResolution.restricted(result.restrictedReason);
        }
        Candidate upcoming = null;
        for (Candidate item : result.candidates) {
            if (item == null || item.videoId == null) continue;
            if (item.live && !item.upcoming) return LiveChannelResolution.live(item);
            if (upcoming == null && item.upcoming) upcoming = item;
        }
        return upcoming != null ? LiveChannelResolution.upcoming(upcoming)
                : LiveChannelResolution.offline();
    }

    private void cacheResult(String key, LiveChannelResolution result) {
        if (result.status == LiveChannelResolution.Status.NETWORK_ERROR ||
                result.status == LiveChannelResolution.Status.RESOLUTION_ERROR ||
                result.status == LiveChannelResolution.Status.CANCELLED) {
            return;
        }
        long ttl = result.status == LiveChannelResolution.Status.LIVE ? cacheMs : Math.min(cacheMs, 10_000);
        cache.put(key, new CacheEntry(result, saturatedAdd(clock.nowMs(), ttl)));
    }

    private static long saturatedAdd(long value, long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        return value + delta;
    }

    private static final class CacheEntry {
        final LiveChannelResolution resolution;
        final long expiresAtMs;

        CacheEntry(LiveChannelResolution resolution, long expiresAtMs) {
            this.resolution = resolution;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
