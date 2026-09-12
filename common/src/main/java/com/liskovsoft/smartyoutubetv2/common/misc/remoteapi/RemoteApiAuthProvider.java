package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteApiData;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteApiAuthProvider {
    private static final int PAIRING_CODE_LENGTH = 6;
    private static final long PAIRING_CODE_EXPIRY_MS = 5 * 60 * 1000;
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 60 * 1000;

    // Index into the per-IP long[] state: [count, windowStartMs]
    private static final int IDX_COUNT = 0;
    private static final int IDX_TIMESTAMP = 1;

    private final RemoteApiData mApiData;
    private final SecureRandom mSecureRandom;
    // Accessed concurrently from NanoHTTPD request threads, so use a concurrent map.
    // The value holds a 64-bit timestamp, so it must be long[] (an int can't hold currentTimeMillis()).
    private final Map<String, long[]> mFailedAttempts;

    public RemoteApiAuthProvider(RemoteApiData apiData) {
        mApiData = apiData;
        mSecureRandom = new SecureRandom();
        mFailedAttempts = new ConcurrentHashMap<>();
    }

    public String generatePairingCode() {
        StringBuilder codeBuilder = new StringBuilder();

        for (int i = 0; i < PAIRING_CODE_LENGTH; i++) {
            int digit = mSecureRandom.nextInt(10);
            codeBuilder.append(digit);

            if (i == 2) {
                codeBuilder.append(" ");
            }
        }

        String code = codeBuilder.toString();
        long expiryMs = System.currentTimeMillis() + PAIRING_CODE_EXPIRY_MS;

        mApiData.setPairingCode(code, expiryMs);

        return code;
    }

    public String verifyPairingCode(String code, String clientIp) {
        if (!checkRateLimit(clientIp)) {
            return null;
        }

        // Atomically validate AND invalidate the code: a second concurrent verify with
        // the same code must fail (one code, one token), closing the double-issue race.
        if (code == null || !mApiData.consumePairingCode(code)) {
            recordFailedAttempt(clientIp);
            return null;
        }

        String token = generateToken();

        mApiData.addPairedToken(token, null);
        resetFailedAttempts(clientIp);

        return token;
    }

    public boolean isTokenValid(String token) {
        return mApiData.isTokenValid(token);
    }

    /** True when the client has exceeded the pairing rate limit and should be told to back off. */
    public boolean isRateLimited(String clientIp) {
        return !checkRateLimit(clientIp);
    }

    public void revokeAllTokens() {
        mApiData.removeAllTokens();
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        mSecureRandom.nextBytes(tokenBytes);

        StringBuilder hexBuilder = new StringBuilder();
        for (byte b : tokenBytes) {
            hexBuilder.append(String.format("%02x", b & 0xff));
        }

        return hexBuilder.toString();
    }

    private boolean checkRateLimit(String clientIp) {
        if (clientIp == null) {
            return true;
        }

        long[] attempts = mFailedAttempts.get(clientIp);
        if (attempts == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - attempts[IDX_TIMESTAMP] > RATE_LIMIT_WINDOW_MS) {
            mFailedAttempts.remove(clientIp);
            return true;
        }

        return attempts[IDX_COUNT] < MAX_FAILED_ATTEMPTS;
    }

    private void recordFailedAttempt(String clientIp) {
        if (clientIp == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long[] attempts = mFailedAttempts.get(clientIp);

        if (attempts == null || now - attempts[IDX_TIMESTAMP] > RATE_LIMIT_WINDOW_MS) {
            mFailedAttempts.put(clientIp, new long[]{1, now});
        } else {
            attempts[IDX_COUNT]++;
        }
    }

    private void resetFailedAttempts(String clientIp) {
        if (clientIp != null) {
            mFailedAttempts.remove(clientIp);
        }
    }
}
