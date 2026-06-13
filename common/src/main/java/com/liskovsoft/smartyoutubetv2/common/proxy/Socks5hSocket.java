package com.liskovsoft.smartyoutubetv2.common.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * A Socket implementation that uses SOCKS5h protocol for remote DNS resolution.
 * <p>
 * Unlike Java's standard SOCKS5 implementation ({@code SocksSocketImpl}), which resolves
 * DNS locally and sends the resolved IP to the proxy, SOCKS5h sends the hostname directly
 * to the proxy server for remote DNS resolution.
 * <p>
 * Usage:
 * <pre>
 *   Socket socket = new Socks5hSocket(proxyHost, proxyPort, proxyUser, proxyPass);
 *   socket.connect(new InetSocketAddress("www.youtube.com", 443));
 * </pre>
 * <p>
 * The {@code connect()} method is idempotent - subsequent calls after the initial
 * connection are no-ops. This allows compatibility with OkHttp which calls
 * {@code socket.connect()} after {@code createSocket()}.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc1928">RFC 1928 - SOCKS Protocol Version 5</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc1929">RFC 1929 - Username/Password Authentication for SOCKS V5</a>
 */
public class Socks5hSocket extends Socket {

    private final String mProxyHost;
    private final int mProxyPort;
    private final String mProxyUser;
    private final String mProxyPassword;
    private boolean mConnected = false;

    /**
     * Creates a new SOCKS5h socket.
     *
     * @param proxyHost SOCKS proxy hostname or IP
     * @param proxyPort SOCKS proxy port
     * @param proxyUser optional username for authentication (null for no auth)
     * @param proxyPassword optional password for authentication (null for no auth)
     */
    public Socks5hSocket(String proxyHost, int proxyPort, String proxyUser, String proxyPassword) {
        this.mProxyHost = proxyHost;
        this.mProxyPort = proxyPort;
        this.mProxyUser = proxyUser;
        this.mProxyPassword = proxyPassword;
    }

    /**
     * Creates an unconnected SOCKS5h socket.
     */
    public Socks5hSocket(String proxyHost, int proxyPort) {
        this(proxyHost, proxyPort, null, null);
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (mConnected) {
            return; // already connected via SOCKS5h tunnel, ignore subsequent calls
        }

        if (!(endpoint instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type: " + endpoint.getClass());
        }

        InetSocketAddress targetAddr = (InetSocketAddress) endpoint;
        String targetHost = targetAddr.getHostString();
        int targetPort = targetAddr.getPort();

        // Step 1: Connect to the SOCKS proxy server (plain TCP)
        super.connect(new InetSocketAddress(mProxyHost, mProxyPort), timeout);

        if (timeout > 0) {
            setSoTimeout(timeout);
        }

        InputStream in = getInputStream();
        OutputStream out = getOutputStream();

        // Step 2: SOCKS5 greeting
        boolean hasAuth = mProxyUser != null && !mProxyUser.isEmpty()
                && mProxyPassword != null && !mProxyPassword.isEmpty();

        if (hasAuth) {
            // Offer both no-auth and username/password methods
            out.write(new byte[]{0x05, 0x02, 0x00, 0x02});
        } else {
            // Only no-auth
            out.write(new byte[]{0x05, 0x01, 0x00});
        }
        out.flush();

        // Step 3: Read server's auth method selection
        int version = in.read();
        int method = in.read();

        if (version != 0x05) {
            throw new IOException("SOCKS5: unexpected protocol version: " + version);
        }

        // Step 4: Handle authentication if required
        if (method == 0x02) {
            // Username/password authentication (RFC 1929)
            if (!hasAuth) {
                throw new IOException("SOCKS5: proxy requires authentication but none provided");
            }

            byte[] userBytes = mProxyUser.getBytes("UTF-8");
            byte[] passBytes = mProxyPassword.getBytes("UTF-8");

            // Auth request: version 0x01 + ulen + uname + plen + passwd
            byte[] authRequest = new byte[1 + 1 + userBytes.length + 1 + passBytes.length];
            authRequest[0] = 0x01;
            authRequest[1] = (byte) userBytes.length;
            System.arraycopy(userBytes, 0, authRequest, 2, userBytes.length);
            authRequest[2 + userBytes.length] = (byte) passBytes.length;
            System.arraycopy(passBytes, 0, authRequest, 3 + userBytes.length, passBytes.length);

            out.write(authRequest);
            out.flush();

            int authVersion = in.read();
            int authStatus = in.read();

            if (authVersion != 0x01 || authStatus != 0x00) {
                throw new IOException("SOCKS5: authentication failed (status=" + authStatus + ")");
            }
        } else if (method == 0xFF) {
            throw new IOException("SOCKS5: no acceptable authentication method");
        } else if (method != 0x00) {
            throw new IOException("SOCKS5: unsupported authentication method: " + method);
        }

        // Step 5: Send CONNECT request with hostname (SOCKS5h - remote DNS resolution)
        // ATYP=0x03 means domain name, followed by length byte and the domain name
        byte[] hostBytes = targetHost.getBytes("UTF-8");

        // Request: VER(1) + CMD(1) + RSV(1) + ATYP(1) + DST.ADDR(1+len) + DST.PORT(2)
        byte[] request = new byte[4 + 1 + hostBytes.length + 2];
        request[0] = 0x05;  // VER: SOCKS5
        request[1] = 0x01;  // CMD: CONNECT
        request[2] = 0x00;  // RSV: reserved
        request[3] = 0x03;  // ATYP: domain name (SOCKS5h)
        request[4] = (byte) hostBytes.length;  // domain name length
        System.arraycopy(hostBytes, 0, request, 5, hostBytes.length);
        // DST.PORT in network byte order (big-endian)
        request[5 + hostBytes.length] = (byte) (targetPort >> 8);
        request[6 + hostBytes.length] = (byte) (targetPort & 0xFF);

        out.write(request);
        out.flush();

        // Step 6: Read SOCKS5 response
        int respVersion = in.read();
        int respStatus = in.read();
        in.read(); // reserved byte

        if (respVersion != 0x05) {
            throw new IOException("SOCKS5: unexpected response version: " + respVersion);
        }

        if (respStatus != 0x00) {
            throw new IOException("SOCKS5: connection failed, status=" + respStatus
                    + " (" + getReplyDescription(respStatus) + ")");
        }

        // Step 7: Skip the bound address in the response
        int bndAtyp = in.read();
        switch (bndAtyp) {
            case 0x01: // IPv4
                skipBytes(in, 4 + 2); // 4 bytes IP + 2 bytes port
                break;
            case 0x03: // Domain name
                int bndNameLen = in.read();
                skipBytes(in, bndNameLen + 2); // name + 2 bytes port
                break;
            case 0x04: // IPv6
                skipBytes(in, 16 + 2); // 16 bytes IP + 2 bytes port
                break;
            default:
                throw new IOException("SOCKS5: unsupported bound address type: " + bndAtyp);
        }

        // Restore original timeout (SOCKS handshake is done)
        if (timeout > 0) {
            setSoTimeout(0);
        }

        mConnected = true;
    }

    private static void skipBytes(InputStream in, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            if (in.read() == -1) {
                throw new IOException("SOCKS5: unexpected end of stream while reading bound address");
            }
        }
    }

    private static String getReplyDescription(int status) {
        switch (status) {
            case 0x01: return "general SOCKS server failure";
            case 0x02: return "connection not allowed by ruleset";
            case 0x03: return "network unreachable";
            case 0x04: return "host unreachable";
            case 0x05: return "connection refused";
            case 0x06: return "TTL expired";
            case 0x07: return "command not supported";
            case 0x08: return "address type not supported";
            default:   return "unknown error";
        }
    }
}
