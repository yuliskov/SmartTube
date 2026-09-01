package com.liskovsoft.smartyoutubetv2.common.app.models.playback.live;

import java.net.URI;
import java.net.URISyntaxException;

/** Rejects malformed or non-network manifest values before ExoPlayer preparation. */
final class LiveManifestValidator {
    private LiveManifestValidator() {}

    static boolean isValid(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (URISyntaxException error) {
            return false;
        }
    }
}
