package com.liskovsoft.smartyoutubetv2.common.vot;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class VotHeaders {
    private VotHeaders() {
    }

    public static Map<String, String> simpleTranslate(byte[] body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Vtrans-Signature", VotSignature.sign(body));
        headers.put("Sec-Vtrans-Token", VotSignature.randomToken());
        return headers;
    }

    public static Map<String, String> sessionTranslate(VotSession session, byte[] body, String path) {
        return buildSecHeaders("Vtrans", session, body, path);
    }

    public static Map<String, String> merge(Map<String, String> base, Map<String, String> extra) {
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        if (base == null || base.isEmpty()) {
            return extra;
        }
        Map<String, String> merged = new HashMap<>(base);
        merged.putAll(extra);
        return merged;
    }

    public static Map<String, String> oauthHeader(String token) {
        Map<String, String> headers = new HashMap<>();
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "OAuth " + token);
        }
        return headers;
    }

    private static Map<String, String> buildSecHeaders(String secType, VotSession session, byte[] body, String path) {
        String token = session.uuid + ":" + path + ":" + VotConfig.COMPONENT_VERSION;
        byte[] tokenBody = token.getBytes(StandardCharsets.UTF_8);
        String tokenSign = VotSignature.sign(tokenBody);
        String fullToken = tokenSign + ":" + token;

        Map<String, String> headers = new HashMap<>();
        headers.put(secType + "-Signature", VotSignature.sign(body));
        headers.put("Sec-" + secType + "-Sk", session.secretKey);
        headers.put("Sec-" + secType + "-Token", fullToken);
        return headers;
    }
}
