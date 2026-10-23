package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.liskovsoft.mediaserviceinterfaces.RemoteControlService;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal DIAL server (http://www.dial-multiscreen.org) that makes the app show up in the cast dialog of
 * YouTube senders on the same network, the way smart TVs do.
 *
 * <p>The sender finds us with an SSDP M-SEARCH for {@link #DIAL_ST}, reads the device description
 * (which carries the {@code Application-URL} header) and the state of the "YouTube" app, and launches it with
 * {@code POST /apps/YouTube} and a body of {@code pairingCode=...&theme=cl&dialLaunch=watch|browse}.
 * We register that pairing code for our lounge screen, after which the sender controls us through the lounge
 * session the app already listens to for "Link with TV code".
 */
public class DialServer {
    private static final String TAG = DialServer.class.getSimpleName();
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String DIAL_ST = "urn:dial-multiscreen-org:service:dial:1";
    private static final String APP_NAME = "YouTube";
    private static final int HTTP_PORT = 8090;
    private static final int MAX_BODY_SIZE = 16 * 1024;
    private static DialServer sInstance;

    private final Context mContext;
    private final String mUdn;
    private final String mFriendlyName;
    private final RemoteControlService mRemoteControlService;
    private final Runnable mOnLaunch;
    private WifiManager.MulticastLock mMulticastLock;
    private MulticastSocket mSsdpSocket;
    private ServerSocket mHttpSocket;
    private volatile boolean mRunning;
    private volatile boolean mAppRunning;

    public interface Factory {
        DialServer create();
    }

    public DialServer(Context context, String udn, String friendlyName, RemoteControlService remoteControlService, Runnable onLaunch) {
        mContext = context.getApplicationContext();
        mUdn = udn;
        mFriendlyName = friendlyName;
        mRemoteControlService = remoteControlService;
        mOnLaunch = onLaunch;
    }

    public static synchronized void startStop(boolean enable, Factory factory) {
        if (enable && sInstance == null) {
            sInstance = factory.create();
            sInstance.start();
        } else if (!enable && sInstance != null) {
            sInstance.stop();
            sInstance = null;
        }
    }

    private void start() {
        mRunning = true;

        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            mMulticastLock = wifiManager.createMulticastLock(TAG);
            mMulticastLock.setReferenceCounted(false);
            mMulticastLock.acquire();
        }

        Thread httpThread = new Thread(this::runHttp, "DialHttp");
        httpThread.setDaemon(true);
        httpThread.start();

        Thread ssdpThread = new Thread(this::runSsdp, "DialSsdp");
        ssdpThread.setDaemon(true);
        ssdpThread.start();
    }

    private void stop() {
        mRunning = false;
        closeQuietly(mSsdpSocket);
        if (mHttpSocket != null) {
            try {
                mHttpSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (mMulticastLock != null && mMulticastLock.isHeld()) {
            mMulticastLock.release();
        }
    }

    // SSDP

    private void runSsdp() {
        try {
            mSsdpSocket = new MulticastSocket(null);
            mSsdpSocket.setReuseAddress(true);
            mSsdpSocket.bind(new InetSocketAddress(SSDP_PORT));
            mSsdpSocket.joinGroup(InetAddress.getByName(SSDP_ADDRESS));
            Log.d(TAG, "SSDP listening");

            byte[] buffer = new byte[2048];
            while (mRunning) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                mSsdpSocket.receive(packet);
                String request = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (!request.startsWith("M-SEARCH")) {
                    continue;
                }
                String st = getHeader(request, "ST");
                if (!DIAL_ST.equals(st) && !"ssdp:all".equals(st)) {
                    continue;
                }
                answerSearch(packet.getAddress(), packet.getPort());
            }
        } catch (IOException e) {
            if (mRunning) {
                Log.e(TAG, "SSDP error: %s", e.getMessage());
            }
        } finally {
            closeQuietly(mSsdpSocket);
        }
    }

    private void answerSearch(InetAddress address, int port) {
        String ip = getLocalIp();
        if (ip == null) {
            return;
        }
        String response = "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "LOCATION: http://" + ip + ":" + HTTP_PORT + "/dd.xml\r\n" +
                "SERVER: Linux/" + Build.VERSION.RELEASE + " UPnP/1.0 SmartTube/1.0\r\n" +
                "ST: " + DIAL_ST + "\r\n" +
                "USN: uuid:" + mUdn + "::" + DIAL_ST + "\r\n" +
                "BOOTID.UPNP.ORG: 1\r\n" +
                "\r\n";
        byte[] data = response.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(data, data.length, address, port));
        } catch (IOException e) {
            Log.e(TAG, "SSDP reply failed: %s", e.getMessage());
        }
    }

    // HTTP

    private void runHttp() {
        try {
            mHttpSocket = new ServerSocket();
            mHttpSocket.setReuseAddress(true);
            mHttpSocket.bind(new InetSocketAddress(HTTP_PORT));
            Log.d(TAG, "HTTP listening on %s", HTTP_PORT);
            while (mRunning) {
                Socket client = mHttpSocket.accept();
                Thread thread = new Thread(() -> handle(client), "DialClient");
                thread.setDaemon(true);
                thread.start();
            }
        } catch (IOException e) {
            if (mRunning) {
                Log.e(TAG, "HTTP error: %s", e.getMessage());
            }
        }
    }

    private void handle(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(10_000);
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return;
            }
            String method = parts[0];
            String path = parts[1];
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            String body = "";
            if (contentLength > 0 && contentLength <= MAX_BODY_SIZE) {
                char[] chars = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int count = reader.read(chars, read, contentLength - read);
                    if (count < 0) {
                        break;
                    }
                    read += count;
                }
                body = new String(chars, 0, read);
            }

            OutputStream out = socket.getOutputStream();
            String ip = getLocalIp();
            String base = "http://" + ip + ":" + HTTP_PORT;
            String appPath = "/apps/" + APP_NAME;

            if ("GET".equals(method) && path.startsWith("/dd.xml")) {
                respond(out, "200 OK", "text/xml; charset=utf-8", deviceDescription(),
                        Collections.singletonMap("Application-URL", base + "/apps/"));
            } else if ("GET".equals(method) && stripSlash(path).equals(appPath)) {
                respond(out, "200 OK", "text/xml; charset=utf-8", appStatus(), null);
            } else if ("POST".equals(method) && stripSlash(path).equals(appPath)) {
                String pairingCode = parseForm(body).get("pairingCode");
                mAppRunning = true;
                respond(out, "201 Created", null, null, Collections.singletonMap("Location", base + appPath + "/run"));
                onLaunch(pairingCode);
            } else if ("DELETE".equals(method) && path.startsWith(appPath)) {
                mAppRunning = false;
                respond(out, "200 OK", null, null, null);
            } else {
                respond(out, "404 Not Found", null, null, null);
            }
        } catch (IOException e) {
            Log.d(TAG, "HTTP client error: %s", e.getMessage());
        }
    }

    private void onLaunch(String pairingCode) {
        Log.d(TAG, "Launch requested, pairing code present: %s", pairingCode != null);
        if (pairingCode == null) {
            return;
        }
        Thread thread = new Thread(() -> {
            if (mRemoteControlService.registerPairingCode(pairingCode) && mOnLaunch != null) {
                mOnLaunch.run();
            }
        }, "DialPairing");
        thread.setDaemon(true);
        thread.start();
    }

    private String deviceDescription() {
        return "<?xml version=\"1.0\"?>\n" +
                "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\n" +
                "  <specVersion><major>1</major><minor>0</minor></specVersion>\n" +
                "  <device>\n" +
                "    <deviceType>urn:dial-multiscreen-org:device:dial:1</deviceType>\n" +
                "    <friendlyName>" + escapeXml(mFriendlyName) + "</friendlyName>\n" +
                "    <manufacturer>SmartTube</manufacturer>\n" +
                "    <modelName>" + escapeXml(Build.MODEL) + "</modelName>\n" +
                "    <UDN>uuid:" + mUdn + "</UDN>\n" +
                "  </device>\n" +
                "</root>";
    }

    private String appStatus() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<service xmlns=\"urn:dial-multiscreen-org:schemas:dial\" dialVer=\"1.7\">\n" +
                "  <name>" + APP_NAME + "</name>\n" +
                "  <options allowStop=\"false\"/>\n" +
                "  <state>" + (mAppRunning ? "running" : "stopped") + "</state>\n" +
                "</service>";
    }

    private static void respond(OutputStream out, String status, String contentType, String body, Map<String, String> headers) throws IOException {
        byte[] data = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n");
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }
        }
        if (contentType != null) {
            head.append("Content-Type: ").append(contentType).append("\r\n");
        }
        head.append("Content-Length: ").append(data.length).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.flush();
    }

    // Helpers

    private static String getHeader(String message, String name) {
        for (String line : message.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> result = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                result.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static String stripSlash(String path) {
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String getLocalIp() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                String name = networkInterface.getName().toLowerCase(Locale.ROOT);
                if (!name.startsWith("wlan") && !name.startsWith("eth")) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Can't get local address: %s", e.getMessage());
        }
        return null;
    }

    private static void closeQuietly(DatagramSocket socket) {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
