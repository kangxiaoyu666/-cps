package com.waimaicps.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.waimaicps.common.BusinessException;
import com.waimaicps.wallet.WalletLedgerService;
import com.waimaicps.withdrawal.WithdrawalService;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WalletWithdrawalMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("waimai_cps_test")
            .withUsername("waimai")
            .withPassword("waimai_test_password");

    private JdbcTemplate jdbc;
    private WalletLedgerService ledger;
    private WithdrawalService withdrawals;
    private long tenantId;
    private long userId;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM wallet_entry");
        jdbc.update("DELETE FROM withdrawal_audit");
        jdbc.update("DELETE FROM withdrawal");
        jdbc.update("DELETE FROM wallet_account");
        jdbc.update("DELETE FROM wx_user");
        jdbc.update("DELETE FROM tenant");
        jdbc.update("INSERT INTO tenant(code,name,status) VALUES('it','集成测试租户','ACTIVE')");
        tenantId = jdbc.queryForObject("SELECT id FROM tenant WHERE code='it'", Long.class);
        jdbc.update("INSERT INTO wx_user(tenant_id,openid_ciphertext,openid_hash,invite_code,status) "
                        + "VALUES(?,X'01',UNHEX(SHA2('it-user',256)),'ITUSER','ACTIVE')",
                tenantId);
        userId = jdbc.queryForObject("SELECT id FROM wx_user WHERE tenant_id=?", Long.class, tenantId);
        ledger = new WalletLedgerService(jdbc);
        withdrawals = new WithdrawalService(jdbc, ledger);
    }

    @Test
    void withdrawalIsIdempotentAndPaidOnlyOnce() {
        ledger.apply(tenantId, userId, "COMMISSION", "order-1", "commission:order-1",
                3000, 0, null, null, "测试佣金");

        Map<String, Object> first = withdrawals.submit(tenantId, userId, 1000, "wd-it-1");
        Map<String, Object> duplicate = withdrawals.submit(tenantId, userId, 1000, "wd-it-1");
        long withdrawalId = ((Number) first.get("id")).longValue();
        withdrawals.approve(tenantId, 1, withdrawalId);
        withdrawals.markPaid(tenantId, 1, withdrawalId, "WECHAT", "PAY-IT-1", "https://example.test/proof");

        assertEquals(first, duplicate);
        assertThrows(BusinessException.class,
                () -> withdrawals.markPaid(tenantId, 1, withdrawalId, "WECHAT", "PAY-IT-1", "proof"));
        assertEquals(2000L, jdbc.queryForObject(
                "SELECT available_cent FROM wallet_account WHERE tenant_id=? AND user_id=?",
                Long.class, tenantId, userId));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT frozen_cent FROM wallet_account WHERE tenant_id=? AND user_id=?",
                Long.class, tenantId, userId));
    }

    @Test
    void reversalCreatesDebtAndLaterCommissionRepaysIt() {
        ledger.apply(tenantId, userId, "COMMISSION", "order-2", "commission:order-2",
                300, 0, null, null, "测试佣金");
        ledger.applyReversal(tenantId, userId, "order-2:REVERSAL", "reversal:order-2", 1000, null);

        assertEquals(0L, balance("available_cent"));
        assertEquals(700L, balance("debt_cent"));
        assertThrows(BusinessException.class,
                () -> withdrawals.submit(tenantId, userId, 1000, "wd-debt"));

        ledger.apply(tenantId, userId, "COMMISSION", "order-3", "commission:order-3",
                1000, 0, null, null, "后续佣金");
        assertEquals(300L, balance("available_cent"));
        assertEquals(0L, balance("debt_cent"));
    }

    private long balance(String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM wallet_account WHERE tenant_id=? AND user_id=?",
                Long.class, tenantId, userId);
    }
}
