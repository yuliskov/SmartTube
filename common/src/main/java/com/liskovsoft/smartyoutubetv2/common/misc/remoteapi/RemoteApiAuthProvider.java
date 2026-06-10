package com.liskovsoft.smartyoutubetv2.common.misc.remoteapi;

import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteApiData;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class RemoteApiAuthProvider {
    private static final int PAIRING_CODE_LENGTH = 6;
    private static final long PAIRING_CODE_EXPIRY_MS = 5 * 60 * 1000;
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 60 * 1000;

    private final RemoteApiData mApiData;
    private final SecureRandom mSecureRandom;
    private final Map<String, int[]> mFailedAttempts;

    public RemoteApiAuthProvider(RemoteApiData apiData) {
        mApiData = apiData;
        mSecureRandom = new SecureRandom();
        mFailedAttempts = new HashMap<>();
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

        if (code == null || !mApiData.isPairingCodeValid(code)) {
            recordFailedAttempt(clientIp);
            return null;
        }

        String token = generateToken();

        mApiData.addPairedToken(token, null);
        mApiData.setPairingCode(null, 0);
        resetFailedAttempts(clientIp);

        return token;
    }

    public boolean isTokenValid(String token) {
        return mApiData.isTokenValid(token);
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

        int[] attempts = mFailedAttempts.get(clientIp);
        if (attempts == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - attempts[1] > RATE_LIMIT_WINDOW_MS) {
            mFailedAttempts.remove(clientIp);
            return true;
        }

        return attempts[0] < MAX_FAILED_ATTEMPTS;
    }

    private void recordFailedAttempt(String clientIp) {
        if (clientIp == null) {
            return;
        }

        int[] attempts = mFailedAttempts.get(clientIp);
        long now = System.currentTimeMillis();

        if (attempts == null || now - attempts[1] > RATE_LIMIT_WINDOW_MS) {
            mFailedAttempts.put(clientIp, new int[]{1, (int) now});
        } else {
            attempts[0]++;
        }
    }

    private void resetFailedAttempts(String clientIp) {
        if (clientIp != null) {
            mFailedAttempts.remove(clientIp);
        }
    }
}
