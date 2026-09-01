package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** One logical live playback attempt. Prevents stale generations and protocol retry loops. */
public final class LivePlaybackSession {
    public enum State {
        IDLE, RESOLVING_INPUT, RESOLVING_CHANNEL, FETCHING_PLAYER_RESPONSE, SELECTING_SOURCE,
        PREPARING_SABR, PREPARING_DASH, PREPARING_HLS, BUFFERING, PLAYING_LIVE_EDGE,
        PLAYING_DVR, PAUSED, RECONNECTING, UPCOMING, CHANNEL_OFFLINE, RESTRICTED, ERROR, STOPPED
    }

    public enum Failure {
        CAPABILITY, TOKEN, PROTOCOL, INITIALIZATION, NO_PROGRESS, RELOAD,
        DASH, HLS, CANCELLED, UNKNOWN
    }

    private final LivePlaybackSourceSelector selector;
    private final EnumSet<LivePlaybackSourceSelector.Source> attempted =
            EnumSet.noneOf(LivePlaybackSourceSelector.Source.class);
    private final List<String> fallbackReasons = new ArrayList<>();
    private LivePlaybackDescriptor descriptor;
    private LivePlaybackSourceSelector.Configuration configuration;
    private LivePlaybackSourceSelector.Source currentSource;
    private long generation;
    private State state = State.IDLE;

    public LivePlaybackSession(LivePlaybackSourceSelector selector) {
        this.selector = selector;
    }

    public LivePlaybackSourceSelector.Decision start(
            LivePlaybackDescriptor descriptor,
            LivePlaybackSourceSelector.Configuration configuration) {
        generation++;
        attempted.clear();
        fallbackReasons.clear();
        this.descriptor = descriptor;
        this.configuration = configuration;
        state = State.SELECTING_SOURCE;
        return chooseNext();
    }

    public LivePlaybackSourceSelector.Decision fail(Failure failure) {
        if (currentSource == null || descriptor == null) {
            state = State.ERROR;
            return LivePlaybackSourceSelector.Decision.unavailable("No active source to fall back from");
        }
        fallbackReasons.add(currentSource + ":" + failure);
        state = State.RECONNECTING;
        return chooseNext();
    }

    public void stop() {
        generation++;
        currentSource = null;
        state = State.STOPPED;
    }

    /** Publishes player lifecycle state only while this attempt still owns a source. */
    public void updatePlaybackState(State state) {
        if (currentSource != null && state != null) {
            this.state = state;
        }
    }

    public long getGeneration() {
        return generation;
    }

    public State getState() {
        return state;
    }

    public LivePlaybackSourceSelector.Source getCurrentSource() {
        return currentSource;
    }

    public List<String> getFallbackReasons() {
        return Collections.unmodifiableList(fallbackReasons);
    }

    private LivePlaybackSourceSelector.Decision chooseNext() {
        LivePlaybackSourceSelector.Decision decision =
                selector.select(descriptor, configuration, attempted);
        if (!decision.isAvailable()) {
            currentSource = null;
            state = State.ERROR;
            return decision;
        }
        currentSource = decision.source;
        attempted.add(currentSource);
        switch (currentSource) {
            case SABR: state = State.PREPARING_SABR; break;
            case DASH_FORMATS:
            case DASH_MANIFEST: state = State.PREPARING_DASH; break;
            case HLS: state = State.PREPARING_HLS; break;
        }
        return decision;
    }
}
