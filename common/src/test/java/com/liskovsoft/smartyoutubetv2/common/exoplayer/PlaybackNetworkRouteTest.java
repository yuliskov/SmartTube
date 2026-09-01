package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackNetworkRouteTest {
    @Test
    public void detectsIpv4BindingInSignedPath() {
        assertTrue(PlaybackNetworkRoute.isIpv4BoundGoogleVideoUrl(
                "https://rr1---sn.example.googlevideo.com/videoplayback/ip/203.0.113.8/id/example/file/seg.ts"));
    }

    @Test
    public void detectsIpv4BindingInSignedQuery() {
        assertTrue(PlaybackNetworkRoute.isIpv4BoundGoogleVideoUrl(
                "https://rr1---sn.example.googlevideo.com/videoplayback?id=example&ip=198.51.100.22"));
    }

    @Test
    public void rejectsIpv6AndMalformedBindings() {
        assertFalse(PlaybackNetworkRoute.isIpv4BoundGoogleVideoUrl(
                "https://rr1---sn.example.googlevideo.com/videoplayback/ip/2001:db8::1/file/seg.ts"));
        assertFalse(PlaybackNetworkRoute.isIpv4BoundGoogleVideoUrl(
                "https://rr1---sn.example.googlevideo.com/videoplayback/ip/999.0.0.1/file/seg.ts"));
    }

    @Test
    public void rejectsUntrustedHostsAndMalformedUrls() {
        assertFalse(PlaybackNetworkRoute.isIpv4BoundGoogleVideoUrl(
                "https://example.com/videoplayback/ip/203.0.113.8/file/seg.ts"));
        assertFalse(PlaybackNetworkRoute.isIpv4BoundGoogleVideoUrl("not a url"));
    }
}
