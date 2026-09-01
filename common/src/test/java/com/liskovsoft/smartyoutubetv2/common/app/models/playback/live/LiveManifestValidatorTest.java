package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveManifestValidatorTest {
    @Test public void acceptsHttpNetworkManifest() {
        assertTrue(LiveManifestValidator.isValid(
                "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/123"));
    }

    @Test public void rejectsRelativeAndNonNetworkValues() {
        assertFalse(LiveManifestValidator.isValid("/api/manifest/live.m3u8"));
        assertFalse(LiveManifestValidator.isValid("file:///tmp/live.m3u8"));
        assertFalse(LiveManifestValidator.isValid("not a url"));
    }
}
