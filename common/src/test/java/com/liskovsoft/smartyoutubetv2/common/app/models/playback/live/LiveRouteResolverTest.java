package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import org.junit.Test;

import okhttp3.HttpUrl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LiveRouteResolverTest {
    @Test public void extractsFinalWatchVideoId() {
        assertEquals("dQw4w9WgXcQ", LiveRouteResolver.extractVideoId(
                HttpUrl.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=x"), ""));
    }

    @Test public void extractsFinalLivePathVideoId() {
        assertEquals("dQw4w9WgXcQ", LiveRouteResolver.extractVideoId(
                HttpUrl.parse("https://www.youtube.com/live/dQw4w9WgXcQ"), ""));
    }

    @Test public void acceptsOnlyCanonicalBodyVideoId() {
        assertEquals("dQw4w9WgXcQ", LiveRouteResolver.extractVideoId(
                HttpUrl.parse("https://www.youtube.com/@channel/live"),
                "<link rel=\"canonical\" href=\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\">"));
        assertNull(LiveRouteResolver.extractVideoId(
                HttpUrl.parse("https://www.youtube.com/@channel/live"),
                "{\"videoId\":\"aaaaaaaaaaa\"}"));
    }

    @Test public void directCanonicalChannelCannotBeReboundByBody() {
        String expected = "UC1234567890123456789012";
        assertEquals(expected, LiveRouteResolver.extractCanonicalChannelId(
                "{\"channelId\":\"UC9999999999999999999999\"}", expected));
    }
}
