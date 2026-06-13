package com.liskovsoft.smartyoutubetv2.common.proxy;

import okhttp3.Dns;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A DNS implementation that preserves the original hostname for SOCKS5h remote resolution.
 * <p>
 * When OkHttp resolves a hostname via its standard {@link Dns} interface, it gets back
 * {@link InetAddress} objects. These are then wrapped in {@link java.net.InetSocketAddress},
 * and the original hostname is lost - {@code InetSocketAddress.getHostString()} returns
 * the IP string instead of the domain name.
 * <p>
 * This class solves the problem by:
 * <ol>
 *   <li>Resolving the hostname to a real IP (so OkHttp's connection logic works normally)</li>
 *   <li>Storing the hostname-to-IP mapping in a concurrent map</li>
 *   <li>Providing {@link #lookupHostname(InetAddress)} for the SocketFactory to retrieve
 *       the original hostname from the resolved IP</li>
 * </ol>
 * <p>
 * The mapped IP is used as a lookup key. Since OkHttp processes connections sequentially
 * for a given route, the mapping is reliable within a single connection lifecycle.
 */
public class Socks5hDnsMapping implements Dns {

    /**
     * Maps resolved IP string -> original hostname.
     * Thread-safe for concurrent access from OkHttp's connection pool.
     */
    private static final ConcurrentHashMap<String, String> sIpToHostname = new ConcurrentHashMap<>();

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = SYSTEM.lookup(hostname);

        // Store the mapping: IP -> original hostname
        for (InetAddress addr : addresses) {
            sIpToHostname.put(addr.getHostAddress(), hostname);
        }

        return addresses;
    }

    /**
     * Looks up the original hostname for a resolved IP address.
     * <p>
     * Called by {@link Socks5hSocketFactory} to retrieve the hostname that should
     * be sent to the SOCKS5h proxy for remote DNS resolution.
     *
     * @param address the resolved IP address
     * @return the original hostname, or the IP string if no mapping exists
     */
    public static String lookupHostname(InetAddress address) {
        String hostname = sIpToHostname.get(address.getHostAddress());
        return hostname != null ? hostname : address.getHostAddress();
    }

    /**
     * Clears all stored mappings. Call when proxy settings change.
     */
    public static void clearMappings() {
        sIpToHostname.clear();
    }
}
