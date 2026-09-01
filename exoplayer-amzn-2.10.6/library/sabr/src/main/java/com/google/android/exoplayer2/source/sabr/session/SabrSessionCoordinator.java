package com.google.android.exoplayer2.source.sabr.session;

import android.util.Base64;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.LiveMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.NextRequestPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextSendingPolicy;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrContextUpdate;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext;
import com.google.protobuf.ByteString;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serialized owner of all server-directed state shared by the audio and video SABR consumers.
 */
public final class SabrSessionCoordinator {
    public enum State {
        UNINITIALIZED,
        BOOTSTRAPPING,
        READY,
        REQUESTING,
        WAITING_BACKOFF,
        SEEKING,
        RELOADING_PLAYER,
        ENDED,
        ERROR,
        CLOSED
    }

    public interface Clock {
        long elapsedRealtimeMs();
    }

    public interface PoTokenRefreshProvider {
        @Nullable String refreshPoToken() throws Exception;
    }

    public static final class RequestTicket {
        public final long generation;
        public final long requestNumber;
        private final String endpoint;

        private RequestTicket(long generation, long requestNumber, String endpoint) {
            this.generation = generation;
            this.requestNumber = requestNumber;
            this.endpoint = endpoint;
        }

        public String getEndpoint() {
            return endpoint;
        }
    }

    private final Clock clock;
    private final String videoId;
    private final boolean postLiveAtBootstrap;
    private final SabrRequestScheduler scheduler;
    private final SabrLiveWindowTracker liveWindowTracker;
    private final SabrDiagnostics diagnostics;
    private final Map<Integer, SabrContextUpdate> contextUpdates = new HashMap<>();
    private final Set<Integer> activeContextTypes = new HashSet<>();
    private String endpoint;
    private @Nullable String poToken;
    private NextRequestPolicy nextRequestPolicy = NextRequestPolicy.getDefaultInstance();
    private StreamProtectionStatus.Status protectionStatus = StreamProtectionStatus.Status.UNKNOWN;
    private State state;
    private long generation = 1;
    private long nextRequestNumber;
    private long lastRequestNumber = -1;
    private int inFlightRequestCount;
    private int redirectCount;
    private int reloadCount;
    private long protectionRefreshGeneration = -1;
    private int protectionRefreshCount;
    private long lastSeekTargetMs = -1;
    private long lastSeekElapsedMs = Long.MIN_VALUE;

    public SabrSessionCoordinator(
            @Nullable String endpoint,
            @Nullable String poToken,
            String videoId,
            boolean postLive,
            Clock clock) {
        this.endpoint = endpoint != null ? endpoint : "";
        this.poToken = emptyToNull(poToken);
        this.videoId = videoId;
        this.postLiveAtBootstrap = postLive;
        this.clock = clock;
        scheduler = new SabrRequestScheduler(clock::elapsedRealtimeMs);
        liveWindowTracker = new SabrLiveWindowTracker(clock::elapsedRealtimeMs, 30_000);
        liveWindowTracker.setBroadcastId(queryValue(this.endpoint, "id"));
        diagnostics = new SabrDiagnostics(200);
        state = this.endpoint.isEmpty() ? State.UNINITIALIZED : State.READY;
    }

    public synchronized RequestTicket beginRequest() throws SabrSessionException {
        requireOpen();
        if (endpoint.isEmpty()) {
            throw new SabrSessionException(
                    SabrSessionException.Category.UNAVAILABLE, "SABR endpoint is unavailable");
        }
        if (state == State.RELOADING_PLAYER) {
            throw new SabrSessionException(
                    SabrSessionException.Category.RELOAD_REQUIRED, "Player reload is in progress");
        }
        long requestNumber = nextRequestNumber++;
        lastRequestNumber = requestNumber;
        inFlightRequestCount++;
        state = State.REQUESTING;
        scheduler.onRequestStarted();
        diagnostics.record(event("request", requestNumber, 0, null));
        return new RequestTicket(generation, requestNumber, endpoint);
    }

    public synchronized boolean completeRequest(RequestTicket ticket) {
        if (state == State.CLOSED || ticket.generation != generation) {
            return false;
        }
        inFlightRequestCount = Math.max(0, inFlightRequestCount - 1);
        state = clock.elapsedRealtimeMs() < scheduler.getBackoffDeadlineElapsedMs()
                ? State.WAITING_BACKOFF
                : inFlightRequestCount > 0 ? State.REQUESTING : State.READY;
        return true;
    }

    public synchronized void processNextRequestPolicy(NextRequestPolicy policy) {
        nextRequestPolicy = policy;
        scheduler.onPolicy(policy);
        state = clock.elapsedRealtimeMs() < scheduler.getBackoffDeadlineElapsedMs()
                ? State.WAITING_BACKOFF : state;
        diagnostics.record(event("policy", lastRequestNumber,
                policy.hasBackoffTimeMs() ? policy.getBackoffTimeMs() : 0, null));
    }

    public synchronized NextRequestPolicy getNextRequestPolicy() {
        return nextRequestPolicy;
    }

    public synchronized void processContextUpdate(SabrContextUpdate update) {
        if (!update.hasType() || !update.hasValue() || !update.hasWritePolicy()) {
            return;
        }
        if (update.getWritePolicy()
                == SabrContextUpdate.SabrContextWritePolicy.SABR_CONTEXT_WRITE_POLICY_KEEP_EXISTING
                && contextUpdates.containsKey(update.getType())) {
            return;
        }
        contextUpdates.put(update.getType(), update);
        if (update.hasSendByDefault() && update.getSendByDefault()) {
            activeContextTypes.add(update.getType());
        }
    }

    public synchronized void processContextSendingPolicy(SabrContextSendingPolicy policy) {
        activeContextTypes.addAll(policy.getStartPolicyList());
        for (int type : policy.getStopPolicyList()) {
            activeContextTypes.remove(type);
        }
        for (int type : policy.getDiscardPolicyList()) {
            activeContextTypes.remove(type);
            contextUpdates.remove(type);
        }
    }

    public synchronized StreamerContext createStreamerContext(StreamerContext.ClientInfo clientInfo) {
        StreamerContext.Builder builder = StreamerContext.newBuilder()
                .setClientInfo(clientInfo)
                .addAllSabrContexts(createActiveContexts())
                .addAllUnsentSabrContexts(createUnsentContextTypes());
        if (nextRequestPolicy.hasPlaybackCookie()) {
            builder.setPlaybackCookie(nextRequestPolicy.getPlaybackCookie().toByteString());
        }
        if (poToken != null) {
            byte[] decoded = decodeUrlSafeBase64(poToken);
            if (decoded.length > 0) {
                builder.setPoToken(ByteString.copyFrom(decoded));
            }
        }
        return builder.build();
    }

    public synchronized void processLiveMetadata(LiveMetadata metadata) throws SabrSessionException {
        if (metadata.hasVideoId() && !videoId.equals(metadata.getVideoId())) {
            throw new SabrSessionException(
                    SabrSessionException.Category.REBOOTSTRAP_REQUIRED,
                    "Live metadata belongs to a different video");
        }
        liveWindowTracker.update(metadata, postLiveAtBootstrap, generation);
        diagnostics.record(event("live-window", lastRequestNumber, 0, null));
    }

    public synchronized long seek(long requestedPositionMs) throws SabrSessionException {
        requireOpen();
        long clamped = liveWindowTracker.clampSeekPositionMs(requestedPositionMs);
        long now = clock.elapsedRealtimeMs();
        boolean duplicateTrackNotification = clamped == lastSeekTargetMs
                && now >= lastSeekElapsedMs && now - lastSeekElapsedMs <= 250;
        if (!duplicateTrackNotification) {
            generation++;
            inFlightRequestCount = 0;
            scheduler.resetForGeneration();
            lastSeekTargetMs = clamped;
            lastSeekElapsedMs = now;
        }
        state = State.SEEKING;
        diagnostics.record(event("seek", lastRequestNumber, 0, null));
        state = State.READY;
        return clamped;
    }

    public synchronized long goLive() throws SabrSessionException {
        long target = liveWindowTracker.getGoLiveTargetMs();
        if (target < 0) {
            throw new SabrSessionException(
                    SabrSessionException.Category.UNAVAILABLE, "Live edge is not available yet");
        }
        return seek(target);
    }

    public synchronized void redirect(String redirectUrl) throws SabrSessionException {
        requireOpen();
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            throw new SabrSessionException(
                    SabrSessionException.Category.PROTOCOL, "SABR redirect is missing a URL");
        }
        String previousBroadcast = queryValue(endpoint, "id");
        String nextBroadcast = queryValue(redirectUrl, "id");
        if (liveWindowTracker.snapshot().isDynamic()
                && previousBroadcast != null && nextBroadcast != null
                && !previousBroadcast.equals(nextBroadcast)) {
            throw new SabrSessionException(
                    SabrSessionException.Category.REBOOTSTRAP_REQUIRED,
                    "SABR redirect changed live broadcast identity");
        }
        endpoint = redirectUrl;
        liveWindowTracker.setBroadcastId(nextBroadcast);
        redirectCount++;
        diagnostics.record(event("redirect", lastRequestNumber, 0, null));
    }

    public synchronized void setEndpointFromCdnFailover(String newEndpoint) {
        endpoint = newEndpoint;
    }

    public synchronized void markReloadRequired() throws SabrSessionException {
        requireOpen();
        if (state == State.RELOADING_PLAYER) {
            return;
        }
        if (reloadCount >= 1) {
            state = State.ERROR;
            throw new SabrSessionException(
                    SabrSessionException.Category.RELOAD_FAILED, "Player reload retry limit reached");
        }
        reloadCount++;
        generation++;
        inFlightRequestCount = 0;
        scheduler.resetForGeneration();
        state = State.RELOADING_PLAYER;
        diagnostics.record(event("reload", lastRequestNumber, 0, null));
    }

    public synchronized void finishReload(String refreshedEndpoint, @Nullable String refreshedPoToken,
            String refreshedVideoId) throws SabrSessionException {
        if (!videoId.equals(refreshedVideoId) || refreshedEndpoint == null || refreshedEndpoint.isEmpty()) {
            state = State.ERROR;
            throw new SabrSessionException(
                    SabrSessionException.Category.RELOAD_FAILED, "Reloaded player snapshot is incompatible");
        }
        endpoint = refreshedEndpoint;
        poToken = emptyToNull(refreshedPoToken);
        nextRequestPolicy = NextRequestPolicy.getDefaultInstance();
        contextUpdates.clear();
        activeContextTypes.clear();
        protectionStatus = StreamProtectionStatus.Status.UNKNOWN;
        state = State.READY;
    }

    public synchronized void processProtectionStatus(
            StreamProtectionStatus status, @Nullable PoTokenRefreshProvider provider)
            throws SabrSessionException {
        protectionStatus = status.hasStatus()
                ? status.getStatus() : StreamProtectionStatus.Status.UNKNOWN;
        if (protectionStatus == StreamProtectionStatus.Status.OK) {
            return;
        }
        if (protectionStatus == StreamProtectionStatus.Status.ATTESTATION_PENDING) {
            state = State.WAITING_BACKOFF;
            return;
        }
        if (protectionStatus != StreamProtectionStatus.Status.ATTESTATION_REQUIRED) {
            return;
        }
        if (provider == null || protectionRefreshGeneration == generation
                || protectionRefreshCount >= 1) {
            state = State.ERROR;
            throw new SabrSessionException(
                    SabrSessionException.Category.PROTECTION,
                    "Stream protection requires an unavailable or exhausted token refresh");
        }
        protectionRefreshGeneration = generation;
        protectionRefreshCount++;
        try {
            String refreshed = emptyToNull(provider.refreshPoToken());
            if (refreshed == null) {
                throw new IllegalStateException("empty token");
            }
            poToken = refreshed;
            generation++;
            inFlightRequestCount = 0;
            scheduler.resetForGeneration();
            state = State.READY;
        } catch (Exception refreshFailure) {
            state = State.ERROR;
            throw new SabrSessionException(
                    SabrSessionException.Category.PROTECTION,
                    "Stream protection token refresh failed", refreshFailure);
        }
    }

    public synchronized SabrSessionSnapshot snapshot() {
        return new SabrSessionSnapshot(
                state, generation, lastRequestNumber, nextRequestPolicy.hasPlaybackCookie(),
                createActiveContexts().size(), createUnsentContextTypes().size(), reloadCount,
                redirectCount, protectionStatus, liveWindowTracker.snapshot());
    }

    public synchronized long getGeneration() { return generation; }
    public synchronized String getEndpoint() { return endpoint; }
    public synchronized long getLastRequestNumber() { return lastRequestNumber; }
    public SabrRequestScheduler getScheduler() { return scheduler; }
    public SabrLiveWindowTracker getLiveWindowTracker() { return liveWindowTracker; }
    public SabrDiagnostics getDiagnostics() { return diagnostics; }

    public synchronized void close() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        generation++;
        inFlightRequestCount = 0;
        scheduler.close();
        contextUpdates.clear();
        activeContextTypes.clear();
        diagnostics.record(event("closed", lastRequestNumber, 0, null));
    }

    private void requireOpen() throws SabrSessionException {
        if (state == State.CLOSED) {
            throw new SabrSessionException(
                    SabrSessionException.Category.CANCELLED, "SABR session is closed");
        }
        if (state == State.ERROR) {
            throw new SabrSessionException(
                    SabrSessionException.Category.PROTOCOL, "SABR session is in an error state");
        }
    }

    private List<StreamerContext.SabrContext> createActiveContexts() {
        List<StreamerContext.SabrContext> contexts = new ArrayList<>();
        for (Integer type : activeContextTypes) {
            SabrContextUpdate update = contextUpdates.get(type);
            if (update != null) {
                contexts.add(StreamerContext.SabrContext.newBuilder()
                        .setType(type).setValue(update.getValue()).build());
            }
        }
        return contexts;
    }

    private List<Integer> createUnsentContextTypes() {
        List<Integer> unsent = new ArrayList<>();
        for (Integer type : activeContextTypes) {
            if (!contextUpdates.containsKey(type)) {
                unsent.add(type);
            }
        }
        return unsent;
    }

    private SabrDiagnostics.Event event(
            String type, long requestNumber, long backoffMs, @Nullable String errorCategory) {
        SabrLiveWindowState window = liveWindowTracker.snapshot();
        return new SabrDiagnostics.Event(
                clock.elapsedRealtimeMs(), generation, requestNumber, type,
                0, 0, -1, -1, 0, backoffMs, activeContextTypes.size(),
                nextRequestPolicy.hasPlaybackCookie(), poToken != null, errorCategory);
    }

    private static byte[] decodeUrlSafeBase64(String value) {
        String padded = value;
        int remainder = value.length() % 4;
        if (remainder != 0) {
            StringBuilder builder = new StringBuilder(value);
            for (int i = remainder; i < 4; i++) {
                builder.append('=');
            }
            padded = builder.toString();
        }
        return Base64.decode(padded, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private static @Nullable String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static @Nullable String queryValue(String url, String key) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            String query = new URI(url).getRawQuery();
            if (query == null) {
                return null;
            }
            for (String item : query.split("&")) {
                int separator = item.indexOf('=');
                String itemKey = separator >= 0 ? item.substring(0, separator) : item;
                if (key.equals(itemKey)) {
                    return separator >= 0 ? item.substring(separator + 1) : "";
                }
            }
        } catch (URISyntaxException ignored) {
            // Invalid endpoints are rejected by the transport; diagnostics never include the value.
        }
        return null;
    }
}
