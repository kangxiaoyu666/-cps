package com.waimaicps.affiliate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TopSignerTest {
    @Test
    void sortsParametersAndExcludesSign() throws Exception {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("v", "2.0");
        parameters.put("app_key", "123");
        parameters.put("method", "demo.method");
        parameters.put("sign", "ignored");

        String canonical = "app_key123methoddemo.methodv2.0";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = java.util.HexFormat.of().withUpperCase()
                .formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected, new TopSigner().sign(parameters, "secret"));
    }
}
