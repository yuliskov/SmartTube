package com.google.android.exoplayer2.source.sabr.manifest;

import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/** Selects equivalent Googlevideo media networks advertised by a signed SABR URL. */
final class SabrCdnSelector {
    private static final String GOOGLEVIDEO_SUFFIX = ".googlevideo.com";
    private final List<String> candidateUrls = new ArrayList<>();
    private int selectedIndex;

    public SabrCdnSelector(@Nullable String initialUrl) {
        if (initialUrl == null) {
            return;
        }

        candidateUrls.add(initialUrl);
        addAlternateUrls(initialUrl);
    }

    public synchronized @Nullable String getCurrentUrl() {
        return candidateUrls.isEmpty() ? null : candidateUrls.get(selectedIndex);
    }

    /**
     * Advances after a failure on the current network. If another track already
     * advanced, a stale failure from an earlier network remains retryable.
     */
    public synchronized boolean maybeAdvance(@Nullable String failedUrl) {
        int failedIndex = findCandidateIndex(failedUrl);

        if (failedIndex < 0 || failedIndex > selectedIndex) {
            return false;
        }

        if (failedIndex < selectedIndex) {
            return true;
        }

        if (selectedIndex + 1 >= candidateUrls.size()) {
            return false;
        }

        selectedIndex++;
        return true;
    }

    private void addAlternateUrls(String initialUrl) {
        URI initialUri;

        try {
            initialUri = URI.create(initialUrl);
        } catch (IllegalArgumentException e) {
            return;
        }

        String initialHost = initialUri.getHost();
        String currentNetwork = extractNetwork(initialHost);
        String networksValue = getQueryParameter(initialUri.getRawQuery(), "mn");

        if (initialHost == null || currentNetwork == null || networksValue == null) {
            return;
        }

        String[] networks = networksValue.split(",");
        int currentNetworkIndex = -1;

        for (int i = 0; i < networks.length; i++) {
            if (currentNetwork.equals(networks[i])) {
                currentNetworkIndex = i;
                break;
            }
        }

        if (currentNetworkIndex < 0) {
            return;
        }

        for (int i = currentNetworkIndex + 1; i < networks.length; i++) {
            if (!networks[i].matches("[A-Za-z0-9-]+")) {
                continue;
            }

            String host = "rr" + (i + 1) + "---" + networks[i] + GOOGLEVIDEO_SUFFIX;
            String alternateUrl = replaceAuthority(initialUrl, initialUri, host);

            if (alternateUrl != null) {
                candidateUrls.add(alternateUrl);
            }
        }
    }

    private int findCandidateIndex(@Nullable String url) {
        String failedHost = getHost(url);

        if (failedHost == null) {
            return -1;
        }

        for (int i = 0; i < candidateUrls.size(); i++) {
            String candidateHost = getHost(candidateUrls.get(i));

            if (candidateHost != null && candidateHost.equalsIgnoreCase(failedHost)) {
                return i;
            }
        }

        return -1;
    }

    private static @Nullable String getHost(@Nullable String url) {
        if (url == null) {
            return null;
        }

        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable String extractNetwork(@Nullable String host) {
        if (host == null || !host.endsWith(GOOGLEVIDEO_SUFFIX)) {
            return null;
        }

        int networkStart = host.indexOf("---");
        int networkEnd = host.length() - GOOGLEVIDEO_SUFFIX.length();

        if (networkStart < 0 || networkStart + 3 >= networkEnd) {
            return null;
        }

        return host.substring(networkStart + 3, networkEnd);
    }

    private static @Nullable String getQueryParameter(@Nullable String rawQuery, String key) {
        if (rawQuery == null) {
            return null;
        }

        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;

            if (key.equals(decode(rawKey))) {
                return decode(separator >= 0 ? pair.substring(separator + 1) : "");
            }
        }

        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static @Nullable String replaceAuthority(String url, URI uri, String host) {
        String oldAuthority = uri.getRawAuthority();

        if (oldAuthority == null) {
            return null;
        }

        String newAuthority = uri.getPort() >= 0 ? host + ":" + uri.getPort() : host;
        int authorityStart = url.indexOf(oldAuthority);

        if (authorityStart < 0) {
            return null;
        }

        return url.substring(0, authorityStart) + newAuthority
                + url.substring(authorityStart + oldAuthority.length());
    }
}
