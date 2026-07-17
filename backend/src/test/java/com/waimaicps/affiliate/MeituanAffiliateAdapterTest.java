package com.waimaicps.affiliate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.waimaicps.common.BusinessException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MeituanAffiliateAdapterTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsOnlyOfficialPromotionLink() throws IOException {
        AtomicReference<String> signature = new AtomicReference<>();
        URI endpoint = startServer(exchange -> {
            signature.set(exchange.getRequestHeaders().getFirst("S-Ca-Signature"));
            respond(exchange, 200, "{\"code\":0,\"message\":\"成功\",\"data\":\"https://official.test/link\"}");
        });
        AffiliateConfigurationService configurations = mock(AffiliateConfigurationService.class);
        AffiliateAttributionService attributions = mock(AffiliateAttributionService.class);
        when(configurations.requireActive(1, AffiliatePlatform.MEITUAN, MeituanAffiliateAdapter.LINK_PERMISSION))
                .thenReturn(configuration());
        MeituanAffiliateAdapter adapter = adapter(configurations, attributions, endpoint);

        AffiliateAdapter.PromotionLink result = adapter.generatePromotionLink(1, 35, "");

        assertEquals("https://official.test/link", result.url());
        verify(attributions).bind(1, AffiliatePlatform.MEITUAN, "uz", 35);
        org.junit.jupiter.api.Assertions.assertNotNull(signature.get());
    }

    @Test
    void officialFailureIsNotConvertedToSuccess() throws IOException {
        URI endpoint = startServer(exchange -> respond(exchange, 200,
                "{\"code\":1,\"message\":\"未授权\",\"data\":null}"));
        AffiliateConfigurationService configurations = mock(AffiliateConfigurationService.class);
        when(configurations.requireActive(1, AffiliatePlatform.MEITUAN, MeituanAffiliateAdapter.LINK_PERMISSION))
                .thenReturn(configuration());
        MeituanAffiliateAdapter adapter = adapter(
                configurations, mock(AffiliateAttributionService.class), endpoint);

        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter.generatePromotionLink(1, 35, ""));

        assertEquals("AFFILIATE_OFFICIAL_ERROR", error.code());
        assertEquals("未授权", error.getMessage());
    }

    private MeituanAffiliateAdapter adapter(
            AffiliateConfigurationService configurations,
            AffiliateAttributionService attributions,
            URI endpoint) {
        ObjectMapper mapper = new ObjectMapper();
        AffiliateHttpClient http = new AffiliateHttpClient(
                HttpClient.newHttpClient(), mapper);
        return new MeituanAffiliateAdapter(
                configurations, http, mapper, new MeituanSigner(), attributions,
                Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC),
                endpoint, endpoint);
    }

    private AffiliateConfiguration configuration() {
        return new AffiliateConfiguration(
                "app-key", "secret", "activity", "pid", "sid",
                Set.of(MeituanAffiliateAdapter.LINK_PERMISSION, MeituanAffiliateAdapter.ORDER_PERMISSION));
    }

    private URI startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api", handler);
        server.start();
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/api");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
