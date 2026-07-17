package com.waimaicps.affiliate;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TopSigner {
    public String sign(Map<String, String> parameters, String appSecret) {
        String canonical = parameters.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getKey() + entry.getValue())
                .reduce("", String::concat);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().withUpperCase()
                    .formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算淘宝开放平台请求签名", ex);
        }
    }
}
