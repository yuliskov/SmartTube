package com.google.android.exoplayer2.source.sabr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Map;

public class SabrRequestHeadersTest {
    @Test
    public void umpPostUsesIdentityEncoding() {
        Map<String, String> headers = DefaultSabrChunkSource.createSabrRequestHeaders();

        assertEquals("application/x-protobuf", headers.get("Content-Type"));
        assertEquals("identity", headers.get("Accept-Encoding"));
        assertEquals("application/vnd.yt-ump", headers.get("Accept"));
    }
}
