package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LivePlaybackSessionTest {
    private final LivePlaybackSourceSelector selector = new LivePlaybackSourceSelector();
    private final LivePlaybackSourceSelector.Configuration allEnabled =
            new LivePlaybackSourceSelector.Configuration(true, true, true, true);

    @Test public void selectsHlsThenLiveDashThenAdaptiveDashThenSabrWithoutLooping() {
        LivePlaybackSession session = new LivePlaybackSession(selector);
        assertEquals(LivePlaybackSourceSelector.Source.HLS,
                session.start(descriptor(true, true, true, true), allEnabled).source);
        assertEquals(LivePlaybackSourceSelector.Source.DASH_MANIFEST,
                session.fail(LivePlaybackSession.Failure.HLS).source);
        assertEquals(LivePlaybackSourceSelector.Source.DASH_FORMATS,
                session.fail(LivePlaybackSession.Failure.DASH).source);
        assertEquals(LivePlaybackSourceSelector.Source.SABR,
                session.fail(LivePlaybackSession.Failure.DASH).source);
        assertFalse(session.fail(LivePlaybackSession.Failure.PROTOCOL).isAvailable());
        assertTrue(session.isTerminal());
        assertEquals(4, session.getFallbackReasons().size());
    }

    @Test public void usesDashManifestWhenHlsUnavailable() {
        LivePlaybackSession session = new LivePlaybackSession(selector);
        assertEquals(LivePlaybackSourceSelector.Source.DASH_MANIFEST,
                session.start(descriptor(false, false, true, false), allEnabled).source);
    }

    @Test public void usesHlsWhenOnlyHlsExists() {
        LivePlaybackSession session = new LivePlaybackSession(selector);
        assertEquals(LivePlaybackSourceSelector.Source.HLS,
                session.start(descriptor(false, false, false, true), allEnabled).source);
    }

    @Test public void noSourceIsTypedError() {
        LivePlaybackSession session = new LivePlaybackSession(selector);
        LivePlaybackSourceSelector.Decision decision =
                session.start(descriptor(false, false, false, false), allEnabled);
        assertFalse(decision.isAvailable());
        assertEquals(LivePlaybackSession.State.ERROR, session.getState());
    }

    @Test public void newAttemptRetiresPriorGenerationAndAttempts() {
        LivePlaybackSession session = new LivePlaybackSession(selector);
        long first = session.start(descriptor(true, true, false, false), allEnabled).source != null
                ? session.getGeneration() : -1;
        session.fail(LivePlaybackSession.Failure.PROTOCOL);
        session.start(descriptor(true, true, false, false), allEnabled);
        assertTrue(session.getGeneration() > first);
        assertEquals(LivePlaybackSourceSelector.Source.DASH_FORMATS, session.getCurrentSource());
    }

    @Test public void publishesTypedPlaybackLifecycleForActiveAttempt() {
        LivePlaybackSession session = new LivePlaybackSession(selector);
        session.start(descriptor(true, true, false, false), allEnabled);
        session.updatePlaybackState(LivePlaybackSession.State.BUFFERING);
        assertEquals(LivePlaybackSession.State.BUFFERING, session.getState());
        session.updatePlaybackState(LivePlaybackSession.State.PLAYING_DVR);
        assertEquals(LivePlaybackSession.State.PLAYING_DVR, session.getState());
        session.stop();
        session.updatePlaybackState(LivePlaybackSession.State.PLAYING_LIVE_EDGE);
        assertEquals(LivePlaybackSession.State.STOPPED, session.getState());
        assertFalse(session.isTerminal());
    }

    private static LivePlaybackDescriptor descriptor(boolean sabr, boolean dashFormats,
                                                     boolean dashManifest, boolean hls) {
        return new LivePlaybackDescriptor("dQw4w9WgXcQ", true, true, true,
                false, null, sabr, dashFormats, dashManifest, hls);
    }
}
