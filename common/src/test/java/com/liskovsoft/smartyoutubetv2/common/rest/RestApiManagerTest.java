package com.liskovsoft.smartyoutubetv2.common.rest;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class RestApiManagerTest {

    private RestApiManager manager;
    private AppPrefs prefs;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        manager = RestApiManager.instance(app);
        prefs = AppPrefs.instance(app);
        prefs.setRestApiUsername("admin");
        prefs.setRestApiPassword("secret");
    }

    // --- Video ID extraction ---

    @Test
    public void extractVideoId_bareId_returnsId() {
        assertEquals("dQw4w9WgXcQ", manager.extractVideoId("dQw4w9WgXcQ"));
    }

    @Test
    public void extractVideoId_watchUrl_returnsId() {
        assertEquals("dQw4w9WgXcQ", manager.extractVideoId(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    public void extractVideoId_youtuBeUrl_returnsId() {
        assertEquals("dQw4w9WgXcQ", manager.extractVideoId(
                "https://youtu.be/dQw4w9WgXcQ"));
    }

    @Test
    public void extractVideoId_shortsUrl_returnsId() {
        assertEquals("dQw4w9WgXcQ", manager.extractVideoId(
                "https://youtube.com/shorts/dQw4w9WgXcQ"));
    }

    @Test
    public void extractVideoId_liveUrl_returnsId() {
        assertEquals("dQw4w9WgXcQ", manager.extractVideoId(
                "https://youtube.com/live/dQw4w9WgXcQ"));
    }

    @Test
    public void extractVideoId_embedUrl_returnsId() {
        assertEquals("dQw4w9WgXcQ", manager.extractVideoId(
                "https://www.youtube.com/embed/dQw4w9WgXcQ"));
    }

    @Test
    public void extractVideoId_invalidUrl_returnsNull() {
        assertNull(manager.extractVideoId("https://example.com/video"));
    }

    // --- Playlist ID extraction ---

    @Test
    public void extractPlaylistId_barePlaylistId_returnsId() {
        assertEquals("PL1234567890abcdef", manager.extractPlaylistId("PL1234567890abcdef"));
    }

    @Test
    public void extractPlaylistId_bareVideoId_returnsNull() {
        assertNull(manager.extractPlaylistId("dQw4w9WgXcQ")); // 11 chars = video ID
    }

    @Test
    public void extractPlaylistId_playlistUrl_returnsId() {
        assertEquals("PL123", manager.extractPlaylistId(
                "https://www.youtube.com/playlist?list=PL123"));
    }

    @Test
    public void extractPlaylistId_watchUrlWithList_returnsId() {
        assertEquals("PL123", manager.extractPlaylistId(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"));
    }

    // --- Basic Auth ---

    @Test
    public void isAuthorized_validCredentials_returnsTrue() {
        RestApiServer.RestRequest request = buildRequest("Basic YWRtaW46c2VjcmV0"); // admin:secret
        assertTrue(manager.isAuthorized(request, prefs));
    }

    @Test
    public void isAuthorized_invalidPassword_returnsFalse() {
        RestApiServer.RestRequest request = buildRequest("Basic YWRtaW46d3Jvbmc="); // admin:wrong
        assertFalse(manager.isAuthorized(request, prefs));
    }

    @Test
    public void isAuthorized_missingHeader_returnsFalse() {
        RestApiServer.RestRequest request = buildRequest(null);
        assertFalse(manager.isAuthorized(request, prefs));
    }

    @Test
    public void isAuthorized_malformedBase64_returnsFalse() {
        RestApiServer.RestRequest request = buildRequest("Basic !!!not-base64!!!");
        assertFalse(manager.isAuthorized(request, prefs));
    }

    @Test
    public void isAuthorized_noColonInCredentials_returnsFalse() {
        RestApiServer.RestRequest request = buildRequest("Basic YWRtaW4="); // just "admin", no colon
        assertFalse(manager.isAuthorized(request, prefs));
    }

    // --- Helpers ---

    private RestApiServer.RestRequest buildRequest(String authHeader) {
        Map<String, String> headers = new HashMap<>();
        if (authHeader != null) {
            headers.put("authorization", authHeader);
        }
        return new RestApiServer.RestRequest("GET", "/", new HashMap<>(), headers);
    }
}
