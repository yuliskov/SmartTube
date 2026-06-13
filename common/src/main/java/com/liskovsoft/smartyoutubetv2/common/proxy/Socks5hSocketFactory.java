package com.liskovsoft.smartyoutubetv2.common.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

/**
 * A {@link SocketFactory} that creates {@link Socks5hSocket} instances for SOCKS5h
 * proxy connections with remote DNS resolution.
 * <p>
 * This factory works in conjunction with {@link Socks5hDnsMapping} to retrieve the
 * original hostname for each connection. When OkHttp calls {@code createSocket()}
 * followed by {@code socket.connect(InetSocketAddress, timeout)}, the socket:
 * <ol>
 *   <li>Looks up the original hostname via {@link Socks5hDnsMapping#lookupHostname(InetAddress)}</li>
 *   <li>Connects to the SOCKS proxy server</li>
 *   <li>Sends the hostname (not the IP) to the proxy for remote DNS resolution</li>
 * </ol>
 * <p>
 * This is the key mechanism that enables SOCKS5h in OkHttp, since OkHttp normally
 * resolves DNS locally before connecting through a SOCKS proxy.
 */
public class Socks5hSocketFactory extends SocketFactory {

    private final String mProxyHost;
    private final int mProxyPort;
    private final String mProxyUser;
    private final String mProxyPassword;

    /**
     * Creates a new SOCKS5h socket factory.
     *
     * @param proxyHost SOCKS proxy hostname or IP
     * @param proxyPort SOCKS proxy port
     * @param proxyUser optional username (null for no auth)
     * @param proxyPassword optional password (null for no auth)
     */
    public Socks5hSocketFactory(String proxyHost, int proxyPort, String proxyUser, String proxyPassword) {
        this.mProxyHost = proxyHost;
        this.mProxyPort = proxyPort;
        this.mProxyUser = proxyUser;
        this.mProxyPassword = proxyPassword;
    }

    public Socks5hSocketFactory(String proxyHost, int proxyPort) {
        this(proxyHost, proxyPort, null, null);
    }

    @Override
    public Socket createSocket() throws IOException {
        return new Socks5hSocket(mProxyHost, mProxyPort, mProxyUser, mProxyPassword);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = new Socks5hSocket(mProxyHost, mProxyPort, mProxyUser, mProxyPassword);
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        Socket socket = new Socks5hSocket(mProxyHost, mProxyPort, mProxyUser, mProxyPassword);
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress localAddress, int localPort) throws IOException {
        return createSocket(host, port);
    }
}
