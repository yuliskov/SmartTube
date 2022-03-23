package com.liskovsoft.smartyoutubetv2.common.proxy;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * http_proxy=http://USERNAME:PASSWORD@PROXYIP:PROXYPORT
 */
public class PasswdURI {
    private final URI mURI;

    public PasswdURI(String uriString) throws URISyntaxException {
        mURI = new URI(uriString);
    }

    public String getScheme() {
        return mURI.getScheme();
    }

    public String getHost() {
        return mURI.getHost();
    }

    public int getPort() {
        return mURI.getPort();
    }

    public String getUsername() {
        String authority = mURI.getAuthority();
        String[] split = authority.split("@");
        return split.length == 2 ? split[0].split(":")[0] : null;
    }

    public String getPassword() {
        String authority = mURI.getAuthority();
        String[] split = authority.split("@");
        return split.length == 2 ? split[0].split(":")[1] : null;
    }
}
