package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.misc.remoteapi.RemoteApiConstants;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RemoteApiData extends DataChangeBase {
    private static final String REMOTE_API_DATA = "remote_api_data";
    private static final String DEVICE_NAME_DEFAULT = "Android TV";
    private static final int DEFAULT_PORT = RemoteApiConstants.DEFAULT_PORT;
    private static final String TOKEN_SEPARATOR = ";;";

    @SuppressLint("StaticFieldLeak")
    private static RemoteApiData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsApiEnabled;
    private int mPort;
    private String mDeviceId;
    private String mDeviceName;
    private String mPairedTokens;
    private String mPairingCode;
    private long mPairingCodeExpiryMs;
    private boolean mAllowAllConnections;

    private RemoteApiData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        restoreState();
    }

    public static RemoteApiData instance(Context context) {
        if (sInstance == null) {
            sInstance = new RemoteApiData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void enableApi(boolean enable) {
        mIsApiEnabled = enable;
        persistState();
    }

    public boolean isApiEnabled() {
        return mIsApiEnabled;
    }

    /** When true, any device on the local network may use the API without pairing. */
    public boolean isAllowAllConnections() {
        return mAllowAllConnections;
    }

    public void setAllowAllConnections(boolean allow) {
        mAllowAllConnections = allow;
        persistState();
    }

    public int getPort() {
        return mPort;
    }

    public void setPort(int port) {
        mPort = port;
        persistState();
    }

    public String getDeviceId() {
        return mDeviceId;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public void setDeviceName(String deviceName) {
        mDeviceName = deviceName;
        persistState();
    }

    public void addPairedToken(String token, String deviceName) {
        if (token == null || token.isEmpty()) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        String entry = token + ":" + (deviceName != null ? deviceName : "") + ":" + timestamp;

        if (mPairedTokens == null || mPairedTokens.isEmpty()) {
            mPairedTokens = entry;
        } else {
            mPairedTokens = mPairedTokens + TOKEN_SEPARATOR + entry;
        }

        persistState();
    }

    public void removePairedToken(String token) {
        if (token == null || mPairedTokens == null || mPairedTokens.isEmpty()) {
            return;
        }

        String[] entries = mPairedTokens.split(TOKEN_SEPARATOR);
        StringBuilder sb = new StringBuilder();

        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length >= 1 && !parts[0].equals(token)) {
                if (sb.length() > 0) {
                    sb.append(TOKEN_SEPARATOR);
                }
                sb.append(entry);
            }
        }

        mPairedTokens = sb.length() > 0 ? sb.toString() : "";
        persistState();
    }

    public void removeAllTokens() {
        mPairedTokens = "";
        persistState();
    }

    public boolean isTokenValid(String token) {
        if (token == null || mPairedTokens == null || mPairedTokens.isEmpty()) {
            return false;
        }

        String[] entries = mPairedTokens.split(TOKEN_SEPARATOR);

        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length >= 1 && constantTimeEquals(parts[0], token)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Constant-time string comparison: avoids leaking a token/code's length or matching
     * prefix through response timing (a brute-force aid on the LAN). Returns false for
     * any null input.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] ab = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        int result = ab.length ^ bb.length;
        for (int i = 0; i < ab.length; i++) {
            byte other = i < bb.length ? bb[i] : 0;
            result |= ab[i] ^ other;
        }

        return result == 0;
    }

    public List<String[]> getPairedDevices() {
        List<String[]> result = new ArrayList<>();

        if (mPairedTokens == null || mPairedTokens.isEmpty()) {
            return result;
        }

        String[] entries = mPairedTokens.split(TOKEN_SEPARATOR);

        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length >= 3) {
                result.add(new String[]{parts[0], parts[1], parts[2]});
            } else if (parts.length == 2) {
                result.add(new String[]{parts[0], parts[1], "0"});
            } else if (parts.length == 1) {
                result.add(new String[]{parts[0], "", "0"});
            }
        }

        return result;
    }

    public void setPairingCode(String code, long expiryMs) {
        mPairingCode = code;
        mPairingCodeExpiryMs = expiryMs;
        persistState();
    }

    public String getPairingCode() {
        return mPairingCode;
    }

    public long getPairingCodeExpiryMs() {
        return mPairingCodeExpiryMs;
    }

    public boolean isPairingCodeValid(String code) {
        if (code == null || mPairingCode == null) {
            return false;
        }

        if (System.currentTimeMillis() > mPairingCodeExpiryMs) {
            return false;
        }

        return constantTimeEquals(mPairingCode, code);
    }

    /**
     * Atomically validate and consume the active pairing code: returns true at most
     * once for a given code, clearing it so a concurrent verify can't reuse it.
     */
    public synchronized boolean consumePairingCode(String code) {
        if (!isPairingCodeValid(code)) {
            return false;
        }

        mPairingCode = null;
        mPairingCodeExpiryMs = 0;
        persistState();
        return true;
    }

    private void restoreState() {
        String data = mAppPrefs.getData(REMOTE_API_DATA);

        String[] split = Helpers.splitData(data);

        mIsApiEnabled = Helpers.parseBoolean(split, 0, true);
        mPort = Helpers.parseInt(split, 1, DEFAULT_PORT);
        mDeviceId = Helpers.parseStr(split, 2);
        mDeviceName = Helpers.parseStr(split, 3);
        mPairedTokens = Helpers.parseStr(split, 4);
        mPairingCode = Helpers.parseStr(split, 5);
        mPairingCodeExpiryMs = Helpers.parseLong(split, 6, 0);
        // Default ON: a home-LAN remote behaves like Chromecast (no pairing dance).
        mAllowAllConnections = Helpers.parseBoolean(split, 7, true);

        if (mDeviceId == null || mDeviceId.isEmpty()) {
            mDeviceId = UUID.randomUUID().toString();
            persistState();
        }

        if (mDeviceName == null || mDeviceName.isEmpty()) {
            mDeviceName = DEVICE_NAME_DEFAULT;
        }
    }

    private void persistState() {
        mAppPrefs.setData(REMOTE_API_DATA, Helpers.mergeData(
                mIsApiEnabled, mPort, mDeviceId, mDeviceName, mPairedTokens,
                mPairingCode, mPairingCodeExpiryMs, mAllowAllConnections
        ));

        onDataChange();
    }
}
