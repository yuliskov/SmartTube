package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import java.net.URI;
import java.net.URISyntaxException;

/** Identifies signed media URLs whose source address family is part of the authorization. */
final class PlaybackNetworkRoute {
    private PlaybackNetworkRoute() {
    }

    static boolean isIpv4BoundGoogleVideoUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || !(host.equals("googlevideo.com") || host.endsWith(".googlevideo.com"))) {
                return false;
            }

            String path = uri.getRawPath();
            if (path != null) {
                String[] parts = path.split("/");
                for (int i = 0; i + 1 < parts.length; i++) {
                    if ("ip".equals(parts[i]) && isIpv4Literal(parts[i + 1])) {
                        return true;
                    }
                }
            }

            String query = uri.getRawQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    int separator = part.indexOf('=');
                    if (separator > 0 && "ip".equals(part.substring(0, separator)) &&
                            isIpv4Literal(part.substring(separator + 1))) {
                        return true;
                    }
                }
            }
        } catch (URISyntaxException ignored) {
            // A malformed URL will be rejected by the media source through its normal error path.
        }

        return false;
    }

    private static boolean isIpv4Literal(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }

        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) {
                return false;
            }
            int number = 0;
            for (int i = 0; i < octet.length(); i++) {
                char character = octet.charAt(i);
                if (character < '0' || character > '9') {
                    return false;
                }
                number = number * 10 + character - '0';
            }
            if (number > 255) {
                return false;
            }
        }

        return true;
    }
}
