package com.waimaicps.affiliate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MeituanSignerTest {
    @Test
    void createsGatewayCompatibleHeaders() {
        Map<String, String> headers = new MeituanSigner().sign(
                "app-key", "secret", URI.create("https://example.test/cps/query"),
                "POST", "{\"page\":1}", 1_700_000_000_000L);

        assertEquals("app-key", headers.get("S-Ca-App"));
        assertEquals("1700000000000", headers.get("S-Ca-Timestamp"));
        assertEquals("S-Ca-Timestamp,S-Ca-App", headers.get("S-Ca-Signature-Headers"));
        assertEquals("3at6DCd2hNSnRY5QowJvhQ==", headers.get("Content-MD5"));
        assertFalse(headers.get("S-Ca-Signature").isBlank());
    }
}
