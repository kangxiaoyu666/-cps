package com.waimaicps.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.waimaicps.common.BusinessException;
import com.waimaicps.wallet.CommissionReversalService;
import com.waimaicps.wallet.CommissionSettlementService;
import com.waimaicps.wallet.WalletLedgerService;
import com.waimaicps.withdrawal.WithdrawalService;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.affiliate.sync.enabled=false",
        "app.dev-seed.enabled=false",
        "app.session.token-pepper=financial-test-pepper-that-is-at-least-32-bytes",
        "app.data.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.affiliate.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FinancialConcurrencyMySqlTest {
    private static final long TIMEOUT_SECONDS = 10;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("waimai_cps_financial_concurrency_test")
            .withUsername("waimai")
            .withPassword("waimai_test_password");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CommissionSettlementService settlementService;

    @Autowired
    private CommissionReversalService reversalService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private WalletLedgerService walletLedgerService;

    private long tenantId;
    private long userId;
    private long channelId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void setUp() {
        clearDatabase();
        jdbc.update("INSERT INTO tenant(code,name,status) VALUES('financial-it','资金并发测试','ACTIVE')");
        tenantId = jdbc.queryForObject(
                "SELECT id FROM tenant WHERE code='financial-it'", Long.class);
        jdbc.update(
                "INSERT INTO wx_user(tenant_id,openid_ciphertext,openid_hash,invite_code,status) "
                        + "VALUES(?,X'01',UNHEX(SHA2('financial-user',256)),'FINUSER','ACTIVE')",
                tenantId);
        userId = jdbc.queryForObject(
                "SELECT id FROM wx_user WHERE tenant_id=?", Long.class, tenantId);
        jdbc.update(
                "INSERT INTO affiliate_channel(tenant_id,platform,display_name,encrypted_config,"
                        + "config_key_version,status) VALUES(?,'MEITUAN','并发测试渠道',NULL,'v1','DISABLED')",
                tenantId);
        channelId = jdbc.queryForObject(
                "SELECT id FROM affiliate_channel WHERE tenant_id=?", Long.class, tenantId);
        jdbc.update("INSERT INTO wallet_account(tenant_id,user_id) VALUES(?,?)", tenantId, userId);
    }

    @Test
    void concurrentSettlementCreditsOrderExactlyOnce() throws Exception {
        long orderId = createSettledOrder("settlement-order");

        ConcurrentResults results = runConcurrently(
                () -> settlementService.settle(tenantId, orderId),
                () -> settlementService.settle(tenantId, orderId));

        assertBothSucceeded(results);
        assertEquals(1L, count("SELECT COUNT(*) FROM commission_record WHERE order_id=?", orderId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM wallet_entry WHERE related_order_id=? AND business_type='COMMISSION'",
                orderId));
        assertEquals(500L, walletValue("available_cent"));
        assertEquals(500L, walletValue("lifetime_income_cent"));
        assertNotNull(jdbc.queryForObject(
                "SELECT commission_processed_at FROM affiliate_order WHERE id=?", Timestamp.class, orderId));
    }

    @Test
    void concurrentRefundReversesOrderExactlyOnce() throws Exception {
        long orderId = createSettledOrder("refund-order");
        settlementService.settle(tenantId, orderId);
        jdbc.update(
                "UPDATE affiliate_order SET status='REFUNDED',refunded_commission_cent=500,"
                        + "refunded_at=UTC_TIMESTAMP(6) WHERE id=?",
                orderId);

        ConcurrentResults results = runConcurrently(
                () -> reversalService.reverseIfRefunded(tenantId, orderId),
                () -> reversalService.reverseIfRefunded(tenantId, orderId));

        assertBothSucceeded(results);
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM commission_record WHERE order_id=? AND reversal_of_id IS NOT NULL",
                orderId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM wallet_entry WHERE related_order_id=? "
                        + "AND business_type='COMMISSION_REVERSAL'",
                orderId));
        assertEquals(0L, walletValue("available_cent"));
        assertEquals(0L, walletValue("debt_cent"));
        assertEquals(0L, walletValue("lifetime_income_cent"));
        assertNotNull(jdbc.queryForObject(
                "SELECT refund_processed_at FROM affiliate_order WHERE id=?", Timestamp.class, orderId));
    }

    @Test
    void concurrentMarkPaidConsumesFrozenBalanceExactlyOnce() throws Exception {
        walletLedgerService.apply(tenantId, userId, "COMMISSION", "withdrawal-funds",
                "commission:withdrawal-funds", 3000, 0, null, null, "提现并发测试入账");
        Map<String, Object> submitted = withdrawalService.submit(
                tenantId, userId, 1000, "financial-withdrawal");
        long withdrawalId = ((Number) submitted.get("id")).longValue();
        withdrawalService.approve(tenantId, 1, withdrawalId);

        ConcurrentResults results = runConcurrently(
                () -> withdrawalService.markPaid(
                        tenantId, 1, withdrawalId, "WECHAT", "PAY-FIRST", "proof-first"),
                () -> withdrawalService.markPaid(
                        tenantId, 1, withdrawalId, "WECHAT", "PAY-SECOND", "proof-second"));

        assertEquals(1L, results.successCount());
        List<Exception> failures = results.failures();
        assertEquals(1, failures.size());
        BusinessException failure = assertInstanceOf(BusinessException.class, failures.getFirst());
        assertEquals("INVALID_WITHDRAWAL_TRANSITION", failure.code());
        Map<String, Object> withdrawal = jdbc.queryForMap(
                "SELECT status,version FROM withdrawal WHERE id=?", withdrawalId);
        assertEquals("PAID", withdrawal.get("status"));
        assertEquals(2L, ((Number) withdrawal.get("version")).longValue());
        assertEquals(0L, walletValue("frozen_cent"));
        assertEquals(2000L, walletValue("available_cent"));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM wallet_entry WHERE related_withdrawal_id=? "
                        + "AND business_type='WITHDRAWAL_PAID'",
                withdrawalId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM withdrawal_audit WHERE withdrawal_id=? AND action='MARK_PAID'",
                withdrawalId));
    }

    private void clearDatabase() {
        jdbc.update("DELETE FROM withdrawal_audit");
        jdbc.update("DELETE FROM wallet_entry");
        jdbc.update("DELETE FROM withdrawal");
        jdbc.update("DELETE FROM commission_record WHERE reversal_of_id IS NOT NULL");
        jdbc.update("DELETE FROM commission_record");
        jdbc.update("DELETE FROM affiliate_order");
        jdbc.update("DELETE FROM wallet_account");
        jdbc.update("DELETE FROM affiliate_channel");
        jdbc.update("DELETE FROM wx_user");
        jdbc.update("DELETE FROM tenant");
    }

    private long createSettledOrder(String externalOrderId) {
        jdbc.update(
                "INSERT INTO affiliate_order(tenant_id,platform,external_order_id,channel_id,"
                        + "attributed_user_id,status,settled_commission_cent,rule_self_rate_bps,"
                        + "rule_direct_rate_bps,settled_at) VALUES(?,'MEITUAN',?,?,?,'SETTLED',500,10000,0,"
                        + "UTC_TIMESTAMP(6))",
                tenantId, externalOrderId, channelId, userId);
        return jdbc.queryForObject(
                "SELECT id FROM affiliate_order WHERE tenant_id=? AND external_order_id=?",
                Long.class, tenantId, externalOrderId);
    }

    private ConcurrentResults runConcurrently(
            ConcurrentAction firstAction, ConcurrentAction secondAction) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Exception> first = executor.submit(
                    () -> invokeWhenStarted(ready, start, firstAction));
            Future<Exception> second = executor.submit(
                    () -> invokeWhenStarted(ready, start, secondAction));
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            return new ConcurrentResults(
                    first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Exception invokeWhenStarted(
            CountDownLatch ready, CountDownLatch start, ConcurrentAction action) {
        ready.countDown();
        try {
            start.await();
            action.run();
            return null;
        } catch (Exception ex) {
            return ex;
        }
    }

    private void assertBothSucceeded(ConcurrentResults results) {
        assertNull(results.first());
        assertNull(results.second());
    }

    private long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private long walletValue(String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM wallet_account WHERE tenant_id=? AND user_id=?",
                Long.class, tenantId, userId);
    }

    @FunctionalInterface
    private interface ConcurrentAction {
        void run() throws Exception;
    }

    private record ConcurrentResults(Exception first, Exception second) {
        long successCount() {
            return java.util.stream.Stream.of(first, second).filter(Objects::isNull).count();
        }

        List<Exception> failures() {
            return java.util.stream.Stream.of(first, second).filter(Objects::nonNull).toList();
        }
    }
}
