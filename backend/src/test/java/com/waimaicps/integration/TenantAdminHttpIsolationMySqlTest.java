package com.waimaicps.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.affiliate.AffiliateAttributionService;
import com.waimaicps.affiliate.AffiliatePlatform;
import com.waimaicps.common.BusinessException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.affiliate.sync.enabled=false",
        "app.dev-seed.enabled=false",
        "app.session.token-pepper=tenant-http-test-pepper-that-is-at-least-32-bytes",
        "app.data.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.affiliate.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.data.redis.connect-timeout=100ms",
        "spring.data.redis.timeout=100ms"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TenantAdminHttpIsolationMySqlTest {
    private static final String TENANT_PASSWORD = "TenantAdmin123!";
    private static final String PLATFORM_PASSWORD = "PlatformAdmin123!";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("waimai_cps_tenant_http_test")
            .withUsername("waimai")
            .withPassword("waimai_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AffiliateAttributionService attributionService;

    private long tenantA;
    private long tenantB;
    private long userA;
    private long userB;
    private long orderB;
    private long withdrawalA;
    private long withdrawalB;

    @BeforeEach
    void setUp() {
        clearDatabaseInForeignKeyOrder();
        tenantA = createTenant("tenant-a", "A-Tenant");
        tenantB = createTenant("tenant-b", "B-Tenant");
        createTenantAdmin(tenantA, "A-Admin");
        createTenantAdmin(tenantB, "B-Admin");
        createPlatformAdmin();

        TenantData dataA = createTenantData(tenantA, "A-");
        TenantData dataB = createTenantData(tenantB, "B-");
        userA = dataA.userId();
        userB = dataB.userId();
        withdrawalA = dataA.withdrawalId();
        orderB = dataB.orderId();
        withdrawalB = dataB.withdrawalId();
    }

    @Test
    void independentTenantSessionsOnlyListOwnData() throws Exception {
        AdminSession sessionA = loginTenant("tenant-a");
        AdminSession sessionB = loginTenant("tenant-b");

        assertNotSame(sessionA.cookies(), sessionB.cookies());
        assertNotEquals(cookieValue(sessionA, "JSESSIONID"), cookieValue(sessionB, "JSESSIONID"));
        assertOwnList(sessionA, "orders", "external_order_id", "A-ORDER", "B-");
        assertOwnList(sessionA, "wallets", "business_no", "A-WALLET", "B-");
        assertOwnList(sessionA, "withdrawals", "withdrawal_no", "A-WITHDRAWAL", "B-");
        assertOwnList(sessionA, "channels", "display_name", "A-CHANNEL", "B-");
        assertOwnList(sessionB, "orders", "external_order_id", "B-ORDER", "A-");
        assertOwnList(sessionB, "wallets", "business_no", "B-WALLET", "A-");
        assertOwnList(sessionB, "withdrawals", "withdrawal_no", "B-WITHDRAWAL", "A-");
        assertOwnList(sessionB, "channels", "display_name", "B-CHANNEL", "A-");
    }

    @Test
    void tenantAdminCannotMutateOtherTenantWithdrawal() throws Exception {
        AdminSession sessionA = loginTenant("tenant-a");
        fetchCsrfToken(sessionA);
        Map<String, Object> withdrawalBefore = withdrawalState(withdrawalB);
        Map<String, Object> walletBefore = walletState(tenantB);
        long auditBefore = count("SELECT COUNT(*) FROM withdrawal_audit WHERE withdrawal_id=?", withdrawalB);
        long entriesBefore = count("SELECT COUNT(*) FROM wallet_entry WHERE tenant_id=?", tenantB);
        List<WriteRequest> requests = List.of(
                new WriteRequest("approve", null),
                new WriteRequest("reject", "{\"reason\":\"cross-tenant\"}"),
                new WriteRequest(
                        "paid",
                        "{\"channel\":\"WECHAT\",\"reference\":\"CROSS\","
                                + "\"proofUrl\":\"https://example.test/proof\"}"));

        for (WriteRequest request : requests) {
            HttpResponse<String> response = postWithCsrf(
                    sessionA,
                    "/api/v1/admin/withdrawals/" + withdrawalB + "/" + request.action(),
                    request.body());
            assertJsonError(response, 404, "WITHDRAWAL_NOT_FOUND");
            assertEquals(withdrawalBefore, withdrawalState(withdrawalB));
            assertEquals(walletBefore, walletState(tenantB));
        }
        assertEquals(auditBefore, count("SELECT COUNT(*) FROM withdrawal_audit WHERE withdrawal_id=?", withdrawalB));
        assertEquals(entriesBefore, count("SELECT COUNT(*) FROM wallet_entry WHERE tenant_id=?", tenantB));
    }

    @Test
    void tenantAdminCannotRetryOtherTenantOrder() throws Exception {
        AdminSession sessionA = loginTenant("tenant-a");
        fetchCsrfToken(sessionA);
        long jobsBefore = count("SELECT COUNT(*) FROM job_execution");

        HttpResponse<String> response = postWithCsrf(
                sessionA, "/api/v1/admin/orders/" + orderB + "/retry");

        assertJsonError(response, 404, "ORDER_NOT_FOUND");
        assertEquals(jobsBefore, count("SELECT COUNT(*) FROM job_execution"));
    }

    @Test
    void tenantAndPlatformRolesCannotCrossNamespaces() throws Exception {
        AdminSession tenantSession = loginTenant("tenant-a");
        AdminSession platformSession = loginPlatform();

        assertJsonError(get(tenantSession, "/api/v1/platform/tenants"), 403, "FORBIDDEN");
        assertJsonError(get(platformSession, "/api/v1/admin/orders"), 403, "FORBIDDEN");
    }

    @Test
    void disabledTenantExistingSessionIsRejected() throws Exception {
        AdminSession sessionA = loginTenant("tenant-a");
        assertEquals(200, get(sessionA, "/api/v1/admin/dashboard").statusCode());
        jdbc.update("UPDATE tenant SET status='DISABLED',version=version+1 WHERE id=?", tenantA);

        HttpResponse<String> response = get(sessionA, "/api/v1/admin/dashboard");

        assertJsonError(response, 401, "UNAUTHORIZED");
    }

    @Test
    void attributionRejectsUserFromAnotherTenant() {
        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> attributionService.bind(tenantA, AffiliatePlatform.MEITUAN, "cross-user", userB));

        assertEquals("USER_NOT_FOUND", failure.code());
        assertEquals(0L, count("SELECT COUNT(*) FROM affiliate_attribution"));
        attributionService.bind(tenantA, AffiliatePlatform.MEITUAN, "own-user", userA);
        attributionService.bind(tenantA, AffiliatePlatform.MEITUAN, "own-user", userA);
        assertEquals(1L, count("SELECT COUNT(*) FROM affiliate_attribution WHERE tenant_id=?", tenantA));
    }

    @Test
    void missingCsrfRejectsWrite() throws Exception {
        AdminSession sessionA = loginTenant("tenant-a");
        Map<String, Object> withdrawalBefore = withdrawalState(withdrawalA);

        HttpResponse<String> response = postWithoutCsrf(
                sessionA, "/api/v1/admin/withdrawals/" + withdrawalA + "/approve");

        assertEquals(403, response.statusCode());
        assertEquals(withdrawalBefore, withdrawalState(withdrawalA));
    }

    private void clearDatabaseInForeignKeyOrder() {
        jdbc.update("DELETE FROM wallet_entry");
        jdbc.update("DELETE FROM withdrawal_audit");
        jdbc.update("DELETE FROM withdrawal");
        jdbc.update("DELETE FROM commission_record");
        jdbc.update("DELETE FROM affiliate_order");
        jdbc.update("DELETE FROM affiliate_attribution");
        jdbc.update("DELETE FROM affiliate_sync_cursor");
        jdbc.update("DELETE FROM job_execution");
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM mini_session");
        jdbc.update("DELETE FROM affiliate_pid");
        jdbc.update("DELETE FROM affiliate_channel");
        jdbc.update("DELETE FROM wallet_account");
        jdbc.update("DELETE FROM commission_rule");
        jdbc.update("DELETE FROM content_config");
        jdbc.update("DELETE FROM wx_user");
        jdbc.update("DELETE FROM tenant_admin");
        jdbc.update("DELETE FROM platform_admin");
        jdbc.update("DELETE FROM tenant");
    }

    private long createTenant(String code, String name) {
        jdbc.update("INSERT INTO tenant(code,name,status,brand_name) VALUES(?,?,'ACTIVE',?)", code, name, name);
        return jdbc.queryForObject("SELECT id FROM tenant WHERE code=?", Long.class, code);
    }

    private void createTenantAdmin(long tenantId, String displayName) {
        jdbc.update(
                "INSERT INTO tenant_admin(tenant_id,username,password_hash,display_name,status) "
                        + "VALUES(?,'admin',?,?,'ACTIVE')",
                tenantId, passwordEncoder.encode(TENANT_PASSWORD), displayName);
    }

    private void createPlatformAdmin() {
        jdbc.update(
                "INSERT INTO platform_admin(username,password_hash,display_name,status) "
                        + "VALUES('platform',?,'Platform-Admin','ACTIVE')",
                passwordEncoder.encode(PLATFORM_PASSWORD));
    }

    private TenantData createTenantData(long tenantId, String marker) {
        jdbc.update(
                "INSERT INTO wx_user(tenant_id,openid_ciphertext,openid_hash,nickname,invite_code,status) "
                        + "VALUES(?,UNHEX(?),UNHEX(SHA2(?,256)),?,?,'ACTIVE')",
                tenantId, marker.equals("A-") ? "01" : "02", marker + "OPENID",
                marker + "USER", marker + "INVITE");
        long userId = jdbc.queryForObject(
                "SELECT id FROM wx_user WHERE tenant_id=? AND invite_code=?",
                Long.class, tenantId, marker + "INVITE");

        jdbc.update(
                "INSERT INTO affiliate_channel(tenant_id,platform,display_name,encrypted_config,"
                        + "config_key_version,status) VALUES(?,'MEITUAN',?,NULL,'test','DISABLED')",
                tenantId, marker + "CHANNEL");
        long channelId = jdbc.queryForObject(
                "SELECT id FROM affiliate_channel WHERE tenant_id=? AND platform='MEITUAN'",
                Long.class, tenantId);

        jdbc.update(
                "INSERT INTO affiliate_order(tenant_id,platform,external_order_id,channel_id,"
                        + "attributed_user_id,status,order_amount_cent,estimated_commission_cent) "
                        + "VALUES(?,'MEITUAN',?,?,?,'PAID',5000,500)",
                tenantId, marker + "ORDER", channelId, userId);
        long orderId = jdbc.queryForObject(
                "SELECT id FROM affiliate_order WHERE tenant_id=? AND external_order_id=?",
                Long.class, tenantId, marker + "ORDER");

        jdbc.update(
                "INSERT INTO wallet_account(tenant_id,user_id,available_cent,frozen_cent,debt_cent,"
                        + "lifetime_income_cent) VALUES(?,?,4000,1000,0,5000)",
                tenantId, userId);
        long walletId = jdbc.queryForObject(
                "SELECT id FROM wallet_account WHERE tenant_id=? AND user_id=?",
                Long.class, tenantId, userId);
        jdbc.update(
                "INSERT INTO wallet_entry(tenant_id,wallet_account_id,user_id,business_type,business_no,"
                        + "direction,available_delta_cent,frozen_delta_cent,debt_delta_cent,"
                        + "available_before_cent,available_after_cent,frozen_before_cent,frozen_after_cent,"
                        + "debt_before_cent,debt_after_cent,related_order_id,idempotency_key,memo) "
                        + "VALUES(?,?,?,'COMMISSION',?,'CREDIT',5000,0,0,0,5000,0,0,0,0,?,?,?)",
                tenantId, walletId, userId, marker + "WALLET", orderId,
                marker + "WALLET-IDEMPOTENCY", marker + "WALLET-ENTRY");

        jdbc.update(
                "INSERT INTO withdrawal(tenant_id,user_id,withdrawal_no,amount_cent,status,submitted_at,"
                        + "idempotency_key) VALUES(?,?,?,1000,'SUBMITTED',UTC_TIMESTAMP(6),?)",
                tenantId, userId, marker + "WITHDRAWAL", marker + "WITHDRAWAL-IDEMPOTENCY");
        long withdrawalId = jdbc.queryForObject(
                "SELECT id FROM withdrawal WHERE tenant_id=? AND withdrawal_no=?",
                Long.class, tenantId, marker + "WITHDRAWAL");
        return new TenantData(userId, orderId, withdrawalId);
    }

    private AdminSession loginTenant(String tenantCode) throws Exception {
        AdminSession session = newSession();
        String body = objectMapper.writeValueAsString(Map.of(
                "tenantCode", tenantCode,
                "username", "admin",
                "password", TENANT_PASSWORD));
        HttpResponse<String> response = postJson(session, "/api/v1/admin/auth/login", body);
        assertEquals(200, response.statusCode());
        assertEquals("SUCCESS", json(response).path("code").asText());
        assertFalse(cookieValue(session, "JSESSIONID").isBlank());
        return session;
    }

    private AdminSession loginPlatform() throws Exception {
        AdminSession session = newSession();
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "platform",
                "password", PLATFORM_PASSWORD));
        HttpResponse<String> response = postJson(session, "/api/v1/platform/auth/login", body);
        assertEquals(200, response.statusCode());
        assertEquals("SUCCESS", json(response).path("code").asText());
        return session;
    }

    private AdminSession newSession() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        return new AdminSession(client, cookies);
    }

    private String fetchCsrfToken(AdminSession session) throws Exception {
        HttpResponse<String> response = get(session, "/api/v1/admin/auth/csrf");
        assertEquals(200, response.statusCode());
        assertEquals("SUCCESS", json(response).path("code").asText());
        String encodedToken = cookieValue(session, "XSRF-TOKEN");
        assertFalse(encodedToken.isBlank());
        return URLDecoder.decode(encodedToken, StandardCharsets.UTF_8);
    }

    private String cookieValue(AdminSession session, String name) {
        List<HttpCookie> cookies = session.cookies().getCookieStore().getCookies();
        return cookies.stream()
                .filter(cookie -> cookie.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse("");
    }

    private HttpResponse<String> get(AdminSession session, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return session.client().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(AdminSession session, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return session.client().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithCsrf(AdminSession session, String path) throws Exception {
        return postWithCsrf(session, path, null);
    }

    private HttpResponse<String> postWithCsrf(AdminSession session, String path, String body) throws Exception {
        String token = cookieValue(session, "XSRF-TOKEN");
        assertFalse(token.isBlank());
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("X-XSRF-TOKEN", URLDecoder.decode(token, StandardCharsets.UTF_8));
        if (body == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return session.client().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutCsrf(AdminSession session, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return session.client().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private void assertOwnList(
            AdminSession session,
            String resource,
            String markerField,
            String ownMarker,
            String foreignPrefix) throws Exception {
        HttpResponse<String> response = get(session, "/api/v1/admin/" + resource);
        assertEquals(200, response.statusCode());
        JsonNode body = json(response);
        assertEquals("SUCCESS", body.path("code").asText());
        JsonNode data = body.path("data");
        assertEquals(1, data.path("total").asLong());
        assertEquals(1, data.path("items").size());
        assertEquals(ownMarker, data.path("items").get(0).path(markerField).asText());
        assertTrue(response.body().contains(ownMarker));
        assertFalse(response.body().contains(foreignPrefix));
    }

    private void assertJsonError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode());
        assertEquals(code, json(response).path("code").asText());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private Map<String, Object> withdrawalState(long withdrawalId) {
        return jdbc.queryForMap(
                "SELECT status,version,reviewed_by,approved_at FROM withdrawal WHERE id=?",
                withdrawalId);
    }

    private Map<String, Object> walletState(long tenantId) {
        return jdbc.queryForMap(
                "SELECT available_cent,frozen_cent,debt_cent,lifetime_income_cent,version "
                        + "FROM wallet_account WHERE tenant_id=?",
                tenantId);
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private record TenantData(long userId, long orderId, long withdrawalId) {
    }

    private record WriteRequest(String action, String body) {
    }

    private record AdminSession(HttpClient client, CookieManager cookies) {
    }
}
