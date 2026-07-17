package com.waimaicps.affiliate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MeituanSigner {
    public Map<String, String> sign(
            String appKey,
            String appSecret,
            URI uri,
            String method,
            String body,
            long timestampMillis) {
        String contentMd5 = base64Digest("MD5", body);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("S-Ca-App", appKey);
        headers.put("S-Ca-Timestamp", Long.toString(timestampMillis));
        headers.put("Content-MD5", contentMd5);
        headers.put("S-Ca-Signature-Headers", "S-Ca-Timestamp,S-Ca-App");
        String signedHeaders = "S-Ca-App:" + appKey + "\n"
                + "S-Ca-Timestamp:" + timestampMillis + "\n";
        String canonical = method.toUpperCase() + "\n" + contentMd5 + "\n"
                + signedHeaders + uri.getRawPath();
        headers.put("S-Ca-Signature", hmac(appSecret, canonical));
        return headers;
    }

    private String base64Digest(String algorithm, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算美团请求摘要", ex);
        }
    }

    private String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算美团请求签名", ex);
        }
    }
}
