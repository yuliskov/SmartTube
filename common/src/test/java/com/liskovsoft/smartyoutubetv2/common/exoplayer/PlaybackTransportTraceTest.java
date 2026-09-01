package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.telemetry.PlaybackTransportTrace;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackTransportTraceTest {
    @Test
    public void classifiesHlsDeliveryStages() {
        assertEquals(PlaybackTransportTrace.Stage.HLS_MASTER,
                PlaybackTransportTrace.classify(PlaybackTransportTrace.Protocol.HLS,
                        "https://manifest.googlevideo.com/api/manifest/hls_variant/file/index.m3u8",
                        true, true, false, PlaybackTransportTrace.Track.UNKNOWN));
        assertEquals(PlaybackTransportTrace.Stage.HLS_MEDIA_PLAYLIST,
                PlaybackTransportTrace.classify(PlaybackTransportTrace.Protocol.HLS,
                        "https://manifest.googlevideo.com/api/manifest/hls_playlist/file/index.m3u8",
                        true, false, false, PlaybackTransportTrace.Track.UNKNOWN));
        assertEquals(PlaybackTransportTrace.Stage.HLS_INIT,
                PlaybackTransportTrace.classify(PlaybackTransportTrace.Protocol.HLS,
                        "https://rr1.googlevideo.com/videoplayback/init",
                        false, false, true, PlaybackTransportTrace.Track.VIDEO));
        assertEquals(PlaybackTransportTrace.Stage.HLS_AUDIO_SEGMENT,
                PlaybackTransportTrace.classify(PlaybackTransportTrace.Protocol.HLS,
                        "https://rr1.googlevideo.com/videoplayback/segment",
                        false, false, false, PlaybackTransportTrace.Track.AUDIO));
    }

    @Test
    public void redactedUrlFactsContainNoSensitiveValues() {
        String secret = "very-secret-proof-token";
        String facts = PlaybackTransportTrace.describeUrl(
                "https://rr1.googlevideo.com/api/manifest/hls_variant/n/challenge/pot/" + secret +
                        "/file/index.m3u8?expire=4102444800&pot=" + secret + "&id=private-video",
                1_700_000_000L);

        assertTrue(facts.contains("host=googlevideo"));
        assertTrue(facts.contains("nPath=true"));
        assertTrue(facts.contains("potPath=true"));
        assertTrue(facts.contains("potQuery=true"));
        assertTrue(facts.contains("expiry=valid"));
        assertFalse(facts.contains(secret));
        assertFalse(facts.contains("private-video"));
        assertFalse(facts.contains("challenge"));

        String pathExpiry = PlaybackTransportTrace.describeUrl(
                "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/4102444800/file/index.m3u8",
                1_700_000_000L);
        assertTrue(pathExpiry.contains("expiry=valid"));
    }
}
