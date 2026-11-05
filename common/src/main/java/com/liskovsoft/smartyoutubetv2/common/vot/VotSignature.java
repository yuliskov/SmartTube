package com.liskovsoft.smartyoutubetv2.common.vot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public final class VotSignature {
    private VotSignature() {
    }

    public static String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(VotConfig.HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("VOT HMAC failed", e);
        }
    }

    public static String randomToken() {
        String hex = "0123456789ABCDEF";
        StringBuilder uuid = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            uuid.append(hex.charAt((int) (Math.random() * 16)));
        }
        return uuid.toString();
    }
}
