/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Message;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Vector;

import de.blinkt.openvpn.R;

public class VpnStatus {


    private static final LinkedList<LogItem> logbuffer;

    private static Vector<LogListener> logListener;
    private static Vector<StateListener> stateListener;
    private static Vector<ByteCountListener> byteCountListener;

    private static String mLaststatemsg = "";

    private static String mLaststate = "NOPROCESS";

    private static int mLastStateresid = R.string.state_noprocess;

    private static Intent mLastIntent = null;

    private static HandlerThread mHandlerThread;

    private static String mLastConnectedVPNUUID;
    static boolean readFileLog =false;
    final static java.lang.Object readFileLock = new Object();


    public static TrafficHistory trafficHistory;


    public static void logException(LogLevel ll, String context, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        LogItem li;
        if (context != null) {
            li = new LogItem(ll, R.string.unhandled_exception_context, e.getMessage(), sw.toString(), context);
        } else {
            li = new LogItem(ll, R.string.unhandled_exception, e.getMessage(), sw.toString());
        }
        newLogItem(li);
    }

    public static void logException(Throwable e) {
        logException(LogLevel.ERROR, null, e);
    }

    public static void logException(String context, Throwable e) {
        logException(LogLevel.ERROR, context, e);
    }

    static final int MAXLOGENTRIES = 1000;

    public static boolean isVPNActive() {
        return mLastLevel != ConnectionStatus.LEVEL_AUTH_FAILED && !(mLastLevel == ConnectionStatus.LEVEL_NOTCONNECTED);
    }

    public static String getLastCleanLogMessage(Context c) {
        String message = mLaststatemsg;
        switch (mLastLevel) {
            case LEVEL_CONNECTED:
                String[] parts = mLaststatemsg.split(",");
                /*
                   (a) the integer unix date/time,
                   (b) the state name,
                   0 (c) optional descriptive string (used mostly on RECONNECTING
                    and EXITING to show the reason for the disconnect),

                    1 (d) optional TUN/TAP local IPv4 address
                   2 (e) optional address of remote server,
                   3 (f) optional port of remote server,
                   4 (g) optional local address,
                   5 (h) optional local port, and
                   6 (i) optional TUN/TAP local IPv6 address.
*/
                // Return only the assigned IP addresses in the UI
                if (parts.length >= 7)
                    message = String.format(Locale.US, "%s %s", parts[1], parts[6]);
                break;
        }

        while (message.endsWith(","))
            message = message.substring(0, message.length() - 1);

        String status = mLaststate;
        if (status.equals("NOPROCESS"))
            return message;

        if (mLastStateresid == R.string.state_waitconnectretry) {
            return c.getString(R.string.state_waitconnectretry, mLaststatemsg);
        }

        String prefix = c.getString(mLastStateresid);
        if (mLastStateresid == R.string.unknown_state)
            message = status + message;
        if (message.length() > 0)
            prefix += ": ";

        return prefix + message;

    }

    public static void initLogCache(File cacheDir) {
        mHandlerThread = new HandlerThread("LogFileWriter", Thread.MIN_PRIORITY);
        mHandlerThread.start();
        mLogFileHandler = new LogFileHandler(mHandlerThread.getLooper());


        Message m = mLogFileHandler.obtainMessage(LogFileHandler.LOG_INIT, cacheDir);
        mLogFileHandler.sendMessage(m);

    }

    public static void flushLog() {
        if (mLogFileHandler!=null)
            mLogFileHandler.sendEmptyMessage(LogFileHandler.FLUSH_TO_DISK);
    }

    public synchronized static void setConnectedVPNProfile(String uuid) {
        mLastConnectedVPNUUID = uuid;
        for (StateListener sl: stateListener)
            sl.setConnectedVPN(uuid);
    }


    public static String getLastConnectedVPNProfile()
    {
        return mLastConnectedVPNUUID;
    }

    public static void setTrafficHistory(TrafficHistory trafficHistory) {
        VpnStatus.trafficHistory = trafficHistory;
    }


    public enum LogLevel {
        INFO(2),
        ERROR(-2),
        WARNING(1),
        VERBOSE(3),
        DEBUG(4);

        protected int mValue;

        LogLevel(int value) {
            mValue = value;
        }

        public int getInt() {
            return mValue;
        }

        public static LogLevel getEnumByValue(int value) {
            switch (value) {
                case 2:
                    return INFO;
                case -2:
                    return ERROR;
                case 1:
                    return WARNING;
                case 3:
                    return VERBOSE;
                case 4:
                    return DEBUG;

                default:
                    return null;
            }
        }
    }

    // keytool -printcert -jarfile de.blinkt.openvpn_85.apk
    static final byte[] officalkey = {-58, -42, -44, -106, 90, -88, -87, -88, -52, -124, 84, 117, 66, 79, -112, -111, -46, 86, -37, 109};
    static final byte[] officaldebugkey = {-99, -69, 45, 71, 114, -116, 82, 66, -99, -122, 50, -70, -56, -111, 98, -35, -65, 105, 82, 43};
    static final byte[] amazonkey = {-116, -115, -118, -89, -116, -112, 120, 55, 79, -8, -119, -23, 106, -114, -85, -56, -4, 105, 26, -57};
    static final byte[] fdroidkey = {-92, 111, -42, -46, 123, -96, -60, 79, -27, -31, 49, 103, 11, -54, -68, -27, 17, 2, 121, 104};


    private static ConnectionStatus mLastLevel = ConnectionStatus.LEVEL_NOTCONNECTED;

    private static LogFileHandler mLogFileHandler;

    static {
        logbuffer = new LinkedList<>();
        logListener = new Vector<>();
        stateListener = new Vector<>();
        byteCountListener = new Vector<>();
        trafficHistory = new TrafficHistory();

        logInformation();

    }


    public interface LogListener {
        void newLog(LogItem logItem);
    }

    public interface StateListener {
        void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level, Intent Intent);

        void setConnectedVPN(String uuid);
    }

    public interface ByteCountListener {
        void updateByteCount(long in, long out, long diffIn, long diffOut);
    }

    public synchronized static void logMessage(LogLevel level, String prefix, String message) {
        newLogItem(new LogItem(level, prefix + message));

    }

    public synchronized static void clearLog() {
        logbuffer.clear();
        logInformation();
        if (mLogFileHandler != null)
            mLogFileHandler.sendEmptyMessage(LogFileHandler.TRIM_LOG_FILE);
    }

    private static void logInformation() {
        String nativeAPI;
        try {
            nativeAPI = NativeUtils.getNativeAPI();
        } catch (UnsatisfiedLinkError ignore) {
            nativeAPI = "error";
        }

        logInfo(R.string.mobile_info, Build.MODEL, Build.BOARD, Build.BRAND, Build.VERSION.SDK_INT,
                nativeAPI, Build.VERSION.RELEASE, Build.ID, Build.FINGERPRINT, "", "");
    }

    public synchronized static void addLogListener(LogListener ll) {
        logListener.add(ll);
    }

    public synchronized static void removeLogListener(LogListener ll) {
        logListener.remove(ll);
    }

    public synchronized static void addByteCountListener(ByteCountListener bcl) {
        TrafficHistory.LastDiff diff = trafficHistory.getLastDiff(null);
        bcl.updateByteCount(diff.getIn(), diff.getOut(), diff.getDiffIn(),diff.getDiffOut());
        byteCountListener.add(bcl);
    }

    public synchronized static void removeByteCountListener(ByteCountListener bcl) {
        byteCountListener.remove(bcl);
    }


    public synchronized static void addStateListener(StateListener sl) {
        if (!stateListener.contains(sl)) {
            stateListener.add(sl);
            if (mLaststate != null)
                sl.updateState(mLaststate, mLaststatemsg, mLastStateresid, mLastLevel, mLastIntent);
        }
    }

    private static int getLocalizedState(String state) {
        switch (state) {
            case "CONNECTING":
                return R.string.state_connecting;
            case "WAIT":
                return R.string.state_wait;
            case "AUTH":
                return R.string.state_auth;
            case "GET_CONFIG":
                return R.string.state_get_config;
            case "ASSIGN_IP":
                return R.string.state_assign_ip;
            case "ADD_ROUTES":
                return R.string.state_add_routes;
            case "CONNECTED":
                return R.string.state_connected;
            case "DISCONNECTED":
                return R.string.state_disconnected;
            case "RECONNECTING":
                return R.string.state_reconnecting;
            case "EXITING":
                return R.string.state_exiting;
            case "RESOLVE":
                return R.string.state_resolve;
            case "TCP_CONNECT":
                return R.string.state_tcp_connect;
            case "AUTH_PENDING":
                return R.string.state_auth_pending;
            default:
                return R.string.unknown_state;
        }

    }

    public static void updateStatePause(OpenVPNManagement.pauseReason pauseReason) {
        switch (pauseReason) {
            case noNetwork:
                VpnStatus.updateStateString("NONETWORK", "", R.string.state_nonetwork, ConnectionStatus.LEVEL_NONETWORK);
                break;
            case screenOff:
                VpnStatus.updateStateString("SCREENOFF", "", R.string.state_screenoff, ConnectionStatus.LEVEL_VPNPAUSED);
                break;
            case userPause:
                VpnStatus.updateStateString("USERPAUSE", "", R.string.state_userpause, ConnectionStatus.LEVEL_VPNPAUSED);
                break;
        }

    }

    private static ConnectionStatus getLevel(String state) {
        String[] noreplyet = {"CONNECTING", "WAIT", "RECONNECTING", "RESOLVE", "TCP_CONNECT"};
        String[] reply = {"AUTH", "GET_CONFIG", "ASSIGN_IP", "ADD_ROUTES", "AUTH_PENDING"};
        String[] connected = {"CONNECTED"};
        String[] notconnected = {"DISCONNECTED", "EXITING"};

        for (String x : noreplyet)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET;

        for (String x : reply)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED;

        for (String x : connected)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTED;

        for (String x : notconnected)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_NOTCONNECTED;

        return ConnectionStatus.UNKNOWN_LEVEL;

    }


    public synchronized static void removeStateListener(StateListener sl) {
        stateListener.remove(sl);
    }


    synchronized public static LogItem[] getlogbuffer() {

        // The stoned way of java to return an array from a vector
        // brought to you by eclipse auto complete
        return logbuffer.toArray(new LogItem[logbuffer.size()]);

    }

    static void updateStateString(String state, String msg) {
        // We want to skip announcing that we are trying to get the configuration since
        // this is just polling until the user input has finished.be
        if (mLastLevel == ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT && state.equals("GET_CONFIG"))
            return;
        int rid = getLocalizedState(state);
        ConnectionStatus level = getLevel(state);
        updateStateString(state, msg, rid, level);
    }

    public synchronized static void updateStateString(String state, String msg, int resid, ConnectionStatus level)
    {
        updateStateString(state, msg, resid, level, null);
    }

    public synchronized static void updateStateString(String state, String msg, int resid, ConnectionStatus level, Intent intent) {
        // Workound for OpenVPN doing AUTH and wait and being connected
        // Simply ignore these state
        if (mLastLevel == ConnectionStatus.LEVEL_CONNECTED &&
                (state.equals("WAIT") || state.equals("AUTH"))) {
            newLogItem(new LogItem((LogLevel.DEBUG), String.format("Ignoring OpenVPN Status in CONNECTED state (%s->%s): %s", state, level.toString(), msg)));
            return;
        }

        mLaststate = state;
        mLaststatemsg = msg;
        mLastStateresid = resid;
        mLastLevel = level;
        mLastIntent = intent;


        for (StateListener sl : stateListener) {
            sl.updateState(state, msg, resid, level, intent);
        }
        //newLogItem(new LogItem((LogLevel.DEBUG), String.format("New OpenVPN Status (%s->%s): %s",state,level.toString(),msg)));
    }

    public static void logInfo(String message) {
        newLogItem(new LogItem(LogLevel.INFO, message));
    }

    public static void logDebug(String message) {
        newLogItem(new LogItem(LogLevel.DEBUG, message));
    }

    public static void logInfo(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.INFO, resourceId, args));
    }

    public static void logDebug(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.DEBUG, resourceId, args));
    }

    static void newLogItem(LogItem logItem) {
        newLogItem(logItem, false);
    }


    synchronized static void newLogItem(LogItem logItem, boolean cachedLine) {
        if (cachedLine) {
            logbuffer.addFirst(logItem);
        } else {
            logbuffer.addLast(logItem);
            if (mLogFileHandler != null) {
                Message m = mLogFileHandler.obtainMessage(LogFileHandler.LOG_MESSAGE, logItem);
                mLogFileHandler.sendMessage(m);
            }
        }

        if (logbuffer.size() > MAXLOGENTRIES + MAXLOGENTRIES / 2) {
            while (logbuffer.size() > MAXLOGENTRIES)
                logbuffer.removeFirst();
            if (mLogFileHandler != null)
                mLogFileHandler.sendMessage(mLogFileHandler.obtainMessage(LogFileHandler.TRIM_LOG_FILE));
        }

        for (LogListener ll : logListener) {
            ll.newLog(logItem);
        }
    }


    public static void logError(String msg) {
        newLogItem(new LogItem(LogLevel.ERROR, msg));

    }

    public static void logWarning(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.WARNING, resourceId, args));
    }

    public static void logWarning(String msg) {
        newLogItem(new LogItem(LogLevel.WARNING, msg));
    }


    public static void logError(int resourceId) {
        newLogItem(new LogItem(LogLevel.ERROR, resourceId));
    }

    public static void logError(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.ERROR, resourceId, args));
    }

    public static void logMessageOpenVPN(LogLevel level, int ovpnlevel, String message) {
        /* Check for the weak md whe we have a message from OpenVPN */
        newLogItem(new LogItem(level, ovpnlevel, message));
    }


    public static void addExtraHints(String msg) {
        if ((msg.endsWith("md too weak") && msg.startsWith("OpenSSL: error")) || msg.contains("error:140AB18E")
                || msg.contains("SSL_CA_MD_TOO_WEAK") || (msg.contains("ca md too weak")))
            logError("OpenSSL reported a certificate with a weak hash, please see the in app FAQ about weak hashes.");
        if ((msg.contains("digital envelope routines::unsupported")))
            logError("The encryption method of your private keys/pkcs12 might be outdated and you probably need to enable " +
                    "the OpenSSL legacy provider to be able to use this profile.");
    }

    public static synchronized void updateByteCount(long in, long out) {
        TrafficHistory.LastDiff diff = trafficHistory.add(in, out);

        for (ByteCountListener bcl : byteCountListener) {
            bcl.updateByteCount(in, out, diff.getDiffIn(), diff.getDiffOut());
        }
    }
}
