package com.liskovsoft.smartyoutubetv2.common.proxy;

import androidx.annotation.RequiresApi;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class PasswdInetSocketAddress extends SocketAddress {
    private final String mUsername;
    private final String mPassword;
    private final InetSocketAddress mInetSocketAddress;

    private PasswdInetSocketAddress(String hostname, int port, String username, String password) {
        mInetSocketAddress = InetSocketAddress.createUnresolved(hostname, port);
        mUsername = username != null && username.isEmpty() ? null : username;
        mPassword = password != null && password.isEmpty() ? null : password;
    }

    public static PasswdInetSocketAddress createUnresolved(String host, int port, String username, String password) {
        return new PasswdInetSocketAddress(checkHost(host), checkPort(port), username, password);
    }

    @RequiresApi(api = 19)
    public String getHostString() {
        return mInetSocketAddress.getHostString();
    }

    public int getPort() {
        return mInetSocketAddress.getPort();
    }

    public String getUsername() {
        return mUsername;
    }

    public String getPassword() {
        return mPassword;
    }

    private static int checkPort(int port) {
        if (port < 0 || port > 0xFFFF)
            throw new IllegalArgumentException("port out of range:" + port);
        return port;
    }

    private static String checkHost(String hostname) {
        if (hostname == null)
            throw new IllegalArgumentException("hostname can't be null");
        return hostname;
    }
}
