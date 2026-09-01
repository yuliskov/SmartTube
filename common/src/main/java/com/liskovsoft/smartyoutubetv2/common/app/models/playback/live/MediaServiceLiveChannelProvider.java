package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;

/** Multi-strategy channel resolver whose candidates are accepted only after a fresh /player check. */
public final class MediaServiceLiveChannelProvider implements LiveChannelResolver.Provider {
    private static final String TAG = MediaServiceLiveChannelProvider.class.getSimpleName();
    private static final int MAX_CANDIDATES = 12;
    private static final long CANONICAL_CACHE_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long PLAYER_TIMEOUT_SECONDS = 15;

    private final ContentService contentService;
    private final MediaItemService mediaItemService;
    private final LiveRouteResolver routeResolver;
    private final LiveCandidateStrategyChain strategyChain;
    private final LiveCandidateVerifier verifier = new LiveCandidateVerifier();
    private final Map<String, CanonicalCacheEntry> canonicalCache = new HashMap<>();

    public MediaServiceLiveChannelProvider(ContentService contentService,
                                           MediaItemService mediaItemService) {
        this(contentService, mediaItemService, new LiveRouteResolver());
    }

    MediaServiceLiveChannelProvider(ContentService contentService,
                                    MediaItemService mediaItemService,
                                    LiveRouteResolver routeResolver) {
        this.contentService = contentService;
        this.mediaItemService = mediaItemService;
        this.routeResolver = routeResolver;
        List<LiveCandidateStrategy> strategies = new ArrayList<>();
        strategies.add(new LiveTabStrategy());
        strategies.add(new LiveRouteStrategy());
        strategies.add(new ChannelSearchStrategy());
        strategies.add(new GenericBrowseStrategy());
        strategyChain = new LiveCandidateStrategyChain(strategies, 10);
    }

    @Override
    public Observable<LiveChannelResolver.ProviderResult> load(String channelReference) {
        return Observable.defer(() -> resolveCanonicalIdentity(channelReference)
                .flatMap(identity -> {
                    if (identity.status == LiveCandidateStrategy.Result.Status.NETWORK_ERROR) {
                        return Observable.just(LiveChannelResolver.ProviderResult.networkError(identity.reason));
                    }
                    if (identity.status == LiveCandidateStrategy.Result.Status.RESOLUTION_ERROR
                            || isEmpty(identity.canonicalChannelId)) {
                        return Observable.just(LiveChannelResolver.ProviderResult.resolutionError(
                                identity.reason != null ? identity.reason
                                        : "Could not establish an exact canonical channel"));
                    }
                    LiveCandidateStrategy.Query query = new LiveCandidateStrategy.Query(
                            channelReference, identity.canonicalChannelId);
                    return strategyChain.execute(query)
                            .flatMap(results -> verifyResults(identity.canonicalChannelId, results));
                }));
    }

    private Observable<CanonicalIdentity> resolveCanonicalIdentity(String reference) {
        if (isChannelId(reference)) {
            return Observable.just(CanonicalIdentity.success(reference));
        }
        CanonicalCacheEntry cached = canonicalCache.get(reference);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAtMs) {
            return Observable.just(CanonicalIdentity.success(cached.channelId));
        }

        Observable<CanonicalIdentity> exactSearch = contentService
                .getSearchObserve(reference, SearchOptions.TYPE_CHANNEL)
                .map(groups -> findExactChannelId(groups, reference))
                .filter(id -> !isEmpty(id))
                .take(1)
                .map(CanonicalIdentity::success)
                .onErrorResumeNext(Observable.empty());

        return exactSearch.switchIfEmpty(routeResolver.resolve(reference)
                        .map(snapshot -> {
                            if (snapshot.status == LiveCandidateStrategy.Result.Status.NETWORK_ERROR) {
                                return CanonicalIdentity.networkError(snapshot.reason);
                            }
                            if (snapshot.status == LiveCandidateStrategy.Result.Status.RESOLUTION_ERROR
                                    || isEmpty(snapshot.canonicalChannelId)) {
                                return CanonicalIdentity.resolutionError(
                                        "No exact handle match or canonical route identity");
                            }
                            return CanonicalIdentity.success(snapshot.canonicalChannelId);
                        }))
                .doOnNext(identity -> {
                    if (!isEmpty(identity.canonicalChannelId)) {
                        canonicalCache.put(reference, new CanonicalCacheEntry(
                                identity.canonicalChannelId,
                                saturatedAdd(System.currentTimeMillis(), CANONICAL_CACHE_MS)));
                    }
                });
    }

    private Observable<LiveChannelResolver.ProviderResult> verifyResults(
            String canonicalChannelId, List<LiveCandidateStrategy.Result> results) {
        List<LiveChannelResolver.Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean strategyNetworkError = false;
        boolean strategyResolutionError = false;
        for (LiveCandidateStrategy.Result result : results) {
            Log.d(TAG, "Live strategy: name=%s, state=%s, candidates=%s",
                    result.strategy, result.status, result.candidates.size());
            strategyNetworkError |= result.status == LiveCandidateStrategy.Result.Status.NETWORK_ERROR;
            strategyResolutionError |= result.status == LiveCandidateStrategy.Result.Status.RESOLUTION_ERROR;
            for (LiveChannelResolver.Candidate candidate : result.candidates) {
                if (candidate != null && candidate.videoId != null
                        && seen.add(candidate.videoId) && candidates.size() < MAX_CANDIDATES) {
                    candidates.add(candidate);
                }
            }
        }
        if (candidates.isEmpty()) {
            if (strategyNetworkError) {
                return Observable.just(LiveChannelResolver.ProviderResult.networkError(
                        "A live-channel lookup strategy failed"));
            }
            if (strategyResolutionError) {
                return Observable.just(LiveChannelResolver.ProviderResult.resolutionError(
                        "A live-channel lookup strategy returned an inconsistent result"));
            }
            return Observable.just(LiveChannelResolver.ProviderResult.available(Collections.emptyList()));
        }

        final boolean hadStrategyNetworkError = strategyNetworkError;
        final boolean hadStrategyResolutionError = strategyResolutionError;
        return Observable.fromIterable(candidates)
                .concatMap(candidate -> verifyCandidate(canonicalChannelId, candidate)
                        .timeout(PLAYER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .onErrorReturn(error -> VerificationAttempt.networkError()))
                .toList()
                .toObservable()
                .map(attempts -> summarizeAttempts(attempts,
                        hadStrategyNetworkError, hadStrategyResolutionError));
    }

    private Observable<VerificationAttempt> verifyCandidate(
            String canonicalChannelId, LiveChannelResolver.Candidate candidate) {
        return mediaItemService.getFormatInfoObserve(candidate.videoId)
                .take(1)
                .map(info -> {
                    LivePlaybackDescriptor descriptor = LivePlaybackDescriptor.from(info);
                    boolean usableSource = descriptor.hls || descriptor.dashManifest
                            || descriptor.dashFormats || info.containsUrlFormats() || descriptor.sabr;
                    LiveCandidateVerifier.Facts facts = new LiveCandidateVerifier.Facts(
                            candidate.videoId, info.getVideoId(), canonicalChannelId,
                            info.getChannelId(), candidate.upcoming, info.isLive(),
                            info.isLiveContent(), info.isUnplayable(), usableSource,
                            info.getStartTimeMs(), info.getPlayabilityReason());
                    LiveCandidateVerifier.Verification verified = verifier.verify(
                            facts, System.currentTimeMillis());
                    return VerificationAttempt.from(candidate, info, verified);
                })
                .switchIfEmpty(Observable.just(VerificationAttempt.resolutionError(
                        "Player verification completed without a response")));
    }

    private LiveChannelResolver.ProviderResult summarizeAttempts(
            List<VerificationAttempt> attempts,
            boolean strategyNetworkError, boolean strategyResolutionError) {
        LiveChannelResolver.Candidate upcoming = null;
        String restrictedReason = null;
        boolean playerNetworkError = false;
        boolean malformed = strategyResolutionError;
        for (VerificationAttempt attempt : attempts) {
            if (attempt.status == LiveCandidateVerifier.Status.LIVE) {
                Log.d(TAG, "Live verification: strategy=%s, state=LIVE", attempt.candidate.strategy);
                return LiveChannelResolver.ProviderResult.available(
                        Collections.singletonList(attempt.candidate));
            }
            if (upcoming == null && attempt.status == LiveCandidateVerifier.Status.UPCOMING) {
                upcoming = attempt.candidate;
            }
            if (restrictedReason == null && attempt.status == LiveCandidateVerifier.Status.RESTRICTED) {
                restrictedReason = attempt.reason;
            }
            playerNetworkError |= attempt.networkError;
            malformed |= attempt.status == LiveCandidateVerifier.Status.MISMATCH
                    || attempt.status == LiveCandidateVerifier.Status.EMPTY_STREAMING_DATA;
        }
        if (upcoming != null) {
            return LiveChannelResolver.ProviderResult.available(Collections.singletonList(upcoming));
        }
        if (restrictedReason != null) {
            return LiveChannelResolver.ProviderResult.restricted(restrictedReason);
        }
        if (strategyNetworkError || playerNetworkError) {
            return LiveChannelResolver.ProviderResult.networkError(
                    "Live status could not be established because a request failed");
        }
        if (malformed) {
            return LiveChannelResolver.ProviderResult.resolutionError(
                    "Candidate verification returned inconsistent or empty playback data");
        }
        return LiveChannelResolver.ProviderResult.available(Collections.emptyList());
    }

    static String findExactChannelId(List<MediaGroup> groups, String handle) {
        if (groups == null || isEmpty(handle)) return null;
        Set<String> exactIds = new HashSet<>();
        for (MediaGroup group : groups) {
            if (group == null || group.getMediaItems() == null) continue;
            for (MediaItem item : group.getMediaItems()) {
                if (item == null || item.getType() != MediaItem.TYPE_CHANNEL
                        || !isChannelId(item.getChannelId())) continue;
                if (containsExactHandle(item.getTitle(), handle)
                        || containsExactHandle(item.getAuthor(), handle)
                        || containsExactHandle(toString(item.getSecondTitle()), handle)
                        || containsExactHandle(item.getBadgeText(), handle)
                        || containsExactHandle(item.getSearchQuery(), handle)) {
                    exactIds.add(item.getChannelId());
                }
            }
        }
        return exactIds.size() == 1 ? exactIds.iterator().next() : null;
    }

    private List<LiveChannelResolver.Candidate> candidatesFromGroup(
            MediaGroup group, String canonicalChannelId, String strategy,
            boolean requireLiveSignal) {
        if (group == null) return Collections.emptyList();
        return candidatesFromGroups(Collections.singletonList(group), canonicalChannelId,
                strategy, requireLiveSignal);
    }

    private List<LiveChannelResolver.Candidate> candidatesFromPages(
            List<List<MediaGroup>> pages, String canonicalChannelId,
            String strategy, boolean requireLiveSignal) {
        List<MediaGroup> flattened = new ArrayList<>();
        if (pages != null) {
            for (List<MediaGroup> page : pages) {
                if (page != null) flattened.addAll(page);
            }
        }
        return candidatesFromGroups(flattened, canonicalChannelId, strategy, requireLiveSignal);
    }

    private List<LiveChannelResolver.Candidate> candidatesFromGroups(
            List<MediaGroup> groups, String canonicalChannelId,
            String strategy, boolean requireLiveSignal) {
        List<LiveChannelResolver.Candidate> result = new ArrayList<>();
        if (groups == null) return result;
        for (MediaGroup group : groups) {
            if (group == null || group.getMediaItems() == null) continue;
            for (MediaItem item : group.getMediaItems()) {
                if (item == null || isEmpty(item.getVideoId())) continue;
                if (requireLiveSignal && !(item.isLive() || item.isUpcoming())) continue;
                if (!isEmpty(item.getChannelId())
                        && !canonicalChannelId.equals(item.getChannelId())) continue;
                result.add(new LiveChannelResolver.Candidate(
                        item.getVideoId(),
                        !isEmpty(item.getChannelId()) ? item.getChannelId() : canonicalChannelId,
                        item.getAuthor(), item.getTitle(), item.getCardImageUrl(),
                        item.isLive(), item.isUpcoming(), strategy));
            }
        }
        return result;
    }

    private final class LiveTabStrategy implements LiveCandidateStrategy {
        @Override public String name() { return "LIVE_TAB"; }

        @Override
        public Observable<Result> find(Query query) {
            return contentService.getChannelLiveObserve(query.canonicalChannelId)
                    .map(group -> Result.success(name(), candidatesFromGroup(group,
                            query.canonicalChannelId, name(), false), true));
        }
    }

    private final class LiveRouteStrategy implements LiveCandidateStrategy {
        @Override public String name() { return "CANONICAL_LIVE_ROUTE"; }

        @Override
        public Observable<Result> find(Query query) {
            return routeResolver.resolve(query.channelReference)
                    .map(snapshot -> {
                        if (snapshot.status == Result.Status.NETWORK_ERROR) {
                            return Result.networkError(name(), snapshot.reason);
                        }
                        if (snapshot.status == Result.Status.RESOLUTION_ERROR) {
                            return Result.resolutionError(name(), snapshot.reason);
                        }
                        if (!isEmpty(snapshot.canonicalChannelId)
                                && !query.canonicalChannelId.equals(snapshot.canonicalChannelId)) {
                            return Result.resolutionError(name(), "Canonical route channel mismatch");
                        }
                        List<LiveChannelResolver.Candidate> candidates = isEmpty(snapshot.videoId)
                                ? Collections.emptyList()
                                : Collections.singletonList(new LiveChannelResolver.Candidate(
                                snapshot.videoId, query.canonicalChannelId, null, null,
                                null, false, false, name()));
                        return Result.success(name(), candidates, false);
                    });
        }
    }

    private final class ChannelSearchStrategy implements LiveCandidateStrategy {
        @Override public String name() { return "CHANNEL_LIVE_SEARCH"; }

        @Override
        public Observable<Result> find(Query query) {
            return contentService.getChannelSearchObserve(query.canonicalChannelId, "live")
                    .map(group -> Result.success(name(), candidatesFromGroup(group,
                            query.canonicalChannelId, name(), false), false));
        }
    }

    private final class GenericBrowseStrategy implements LiveCandidateStrategy {
        @Override public String name() { return "GENERIC_BROWSE"; }

        @Override
        public Observable<Result> find(Query query) {
            return contentService.getChannelObserve(query.canonicalChannelId)
                    .toList()
                    .toObservable()
                    .map(pages -> Result.success(name(), candidatesFromPages(pages,
                            query.canonicalChannelId, name(), true), false));
        }
    }

    private static boolean containsExactHandle(String value, String handle) {
        if (isEmpty(value) || isEmpty(handle)) return false;
        String normalizedValue = value.toLowerCase(Locale.US);
        String normalizedHandle = handle.toLowerCase(Locale.US);
        int index = normalizedValue.indexOf(normalizedHandle);
        while (index >= 0) {
            int end = index + normalizedHandle.length();
            boolean startBoundary = index == 0 || !isHandleChar(normalizedValue.charAt(index - 1));
            boolean endBoundary = end == normalizedValue.length()
                    || !isHandleChar(normalizedValue.charAt(end));
            if (startBoundary && endBoundary) return true;
            index = normalizedValue.indexOf(normalizedHandle, index + 1);
        }
        return false;
    }

    private static boolean isHandleChar(char value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '_' || value == '-';
    }

    private static String toString(CharSequence value) {
        return value != null ? value.toString() : null;
    }

    private static boolean isChannelId(String value) {
        return value != null && value.matches("UC[A-Za-z0-9_-]{22}");
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static long saturatedAdd(long value, long delta) {
        return delta > 0 && value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta;
    }

    private static final class CanonicalCacheEntry {
        final String channelId;
        final long expiresAtMs;

        CanonicalCacheEntry(String channelId, long expiresAtMs) {
            this.channelId = channelId;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class CanonicalIdentity {
        final String canonicalChannelId;
        final LiveCandidateStrategy.Result.Status status;
        final String reason;

        private CanonicalIdentity(String canonicalChannelId,
                                  LiveCandidateStrategy.Result.Status status,
                                  String reason) {
            this.canonicalChannelId = canonicalChannelId;
            this.status = status;
            this.reason = reason;
        }

        static CanonicalIdentity success(String channelId) {
            return new CanonicalIdentity(channelId,
                    LiveCandidateStrategy.Result.Status.SUCCESS, null);
        }

        static CanonicalIdentity networkError(String reason) {
            return new CanonicalIdentity(null,
                    LiveCandidateStrategy.Result.Status.NETWORK_ERROR, reason);
        }

        static CanonicalIdentity resolutionError(String reason) {
            return new CanonicalIdentity(null,
                    LiveCandidateStrategy.Result.Status.RESOLUTION_ERROR, reason);
        }
    }

    private static final class VerificationAttempt {
        final LiveChannelResolver.Candidate candidate;
        final LiveCandidateVerifier.Status status;
        final String reason;
        final boolean networkError;

        private VerificationAttempt(LiveChannelResolver.Candidate candidate,
                                    LiveCandidateVerifier.Status status,
                                    String reason, boolean networkError) {
            this.candidate = candidate;
            this.status = status;
            this.reason = reason;
            this.networkError = networkError;
        }

        static VerificationAttempt from(LiveChannelResolver.Candidate original,
                                        MediaItemFormatInfo info,
                                        LiveCandidateVerifier.Verification verification) {
            LiveChannelResolver.Candidate verified = new LiveChannelResolver.Candidate(
                    original.videoId, info.getChannelId(),
                    !isEmpty(info.getAuthor()) ? info.getAuthor() : original.channelName,
                    !isEmpty(info.getTitle()) ? info.getTitle() : original.title,
                    original.thumbnailUrl,
                    verification.status == LiveCandidateVerifier.Status.LIVE,
                    verification.status == LiveCandidateVerifier.Status.UPCOMING,
                    original.strategy);
            return new VerificationAttempt(verified, verification.status,
                    verification.reason, false);
        }

        static VerificationAttempt networkError() {
            return new VerificationAttempt(null, LiveCandidateVerifier.Status.NOT_LIVE,
                    "Player verification request failed", true);
        }

        static VerificationAttempt resolutionError(String reason) {
            return new VerificationAttempt(null, LiveCandidateVerifier.Status.MISMATCH,
                    reason, false);
        }
    }
}
