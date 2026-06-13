package com.liskovsoft.smartyoutubetv2.common.proxy;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.net.InetSocketAddress;

import okhttp3.OkHttpClient;

/**
 * Configures an {@link OkHttpClient} to use SOCKS5h (remote DNS resolution) proxy.
 * <p>
 * <b>Background:</b> SmartTube's {@link ProxyManager} configures SOCKS proxy via Java
 * system properties ({@code socksProxyHost}, {@code socksProxyPort}). Java's standard
 * SOCKS implementation ({@code SocksSocketImpl}) resolves DNS locally before sending
 * the resolved IP to the proxy server. This is problematic when the local DNS is
 * polluted or censored.
 * <p>
 * SOCKS5h (RFC 1928 with ATYP=0x03) sends the hostname directly to the proxy server,
 * allowing the proxy to perform DNS resolution remotely.
 * <p>
 * <b>Usage:</b>
 * <pre>
 *   OkHttpClient baseClient = OkHttpManager.instance().getClient();
 *   OkHttpClient socks5hClient = Socks5hOkHttpConfigurator.configure(baseClient);
 *   // Use socks5hClient for requests that should go through SOCKS5h proxy
 * </pre>
 * <p>
 * This configures:
 * <ul>
 *   <li>Custom {@link okhttp3.Dns} ({@link Socks5hDnsMapping}) to preserve hostname mapping</li>
 *   <li>Custom {@link javax.net.SocketFactory} ({@link Socks5hSocketFactory}) to implement
 *       the SOCKS5h handshake with remote DNS</li>
 *   <li>Clears OkHttp's proxy setting to avoid conflict with the custom socket factory</li>
 * </ul>
 *
 * @see Socks5hSocket
 * @see Socks5hDnsMapping
 * @see Socks5hSocketFactory
 */
public class Socks5hOkHttpConfigurator {

    private static final String TAG = Socks5hOkHttpConfigurator.class.getSimpleName();

    private Socks5hOkHttpConfigurator() {
        // utility class
    }

    /**
     * Creates a new OkHttpClient configured for SOCKS5h proxy if SOCKS proxy is active.
     * <p>
     * If no SOCKS proxy is configured in system properties, returns the original client
     * unchanged. This makes it safe to call unconditionally.
     *
     * @param client the base OkHttpClient (typically from OkHttpManager)
     * @return a new OkHttpClient with SOCKS5h support, or the original client if no SOCKS proxy
     */
    public static OkHttpClient configure(OkHttpClient client) {
        String socksHost = System.getProperty("socksProxyHost");
        String socksPort = System.getProperty("socksProxyPort");

        if (socksHost == null || socksPort == null) {
            return client; // no SOCKS proxy configured, return as-is
        }

        int port;
        try {
            port = Integer.parseInt(socksPort);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid SOCKS proxy port: " + socksPort);
            return client;
        }

        String socksUser = System.getProperty("socksProxyUser");
        String socksPass = System.getProperty("socksProxyPassword");

        Log.d(TAG, "Configuring SOCKS5h proxy: " + socksHost + ":" + port
                + (socksUser != null ? " (auth: " + socksUser + ")" : " (no auth)"));

        // Clear DNS mappings from any previous configuration
        Socks5hDnsMapping.clearMappings();

        return client.newBuilder()
                .dns(new Socks5hDnsMapping())
                .socketFactory(new Socks5hSocketFactory(socksHost, port, socksUser, socksPass))
                .proxy(java.net.Proxy.NO_PROXY) // disable OkHttp's built-in proxy to avoid double-proxying
                .build();
    }

    /**
     * Checks whether SOCKS proxy is currently configured via system properties.
     *
     * @return true if socksProxyHost and socksProxyPort are set
     */
    public static boolean isSocksProxyActive() {
        return System.getProperty("socksProxyHost") != null
                && System.getProperty("socksProxyPort") != null;
    }
}
