package com.waimaicps.config;

import com.waimaicps.affiliate.AffiliateConfigurationService;
import com.waimaicps.affiliate.AffiliateConfigurationService.ConfigurationWrite;
import com.waimaicps.affiliate.AffiliatePlatform;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevDataInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevDataInitializer.class);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AffiliateConfigurationService configurations;
    private final boolean enabled;
    private final String adminPassword;

    public DevDataInitializer(
            JdbcTemplate jdbc,
            PasswordEncoder passwords,
            AffiliateConfigurationService configurations,
            @Value("${app.dev-seed.enabled:true}") boolean enabled,
            @Value("${app.dev-seed.admin-password:DemoAdmin123!}") String adminPassword) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.configurations = configurations;
        this.enabled = enabled;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        long tenantId = ensureTenant();
        ensureAdmins(tenantId);
        ensureCommissionRule(tenantId);
        ensureMockChannels(tenantId);
        LOGGER.info("开发演示数据已就绪 tenantCode=demo tenantAdmin=admin platformAdmin=platform");
    }

    private long ensureTenant() {
        jdbc.update(
                "INSERT INTO tenant(code,name,status,brand_name) VALUES('demo','演示租户','ACTIVE','外卖省钱助手') "
                        + "ON DUPLICATE KEY UPDATE name=VALUES(name),status='ACTIVE' ");
        return jdbc.queryForObject("SELECT id FROM tenant WHERE code='demo'", Long.class);
    }

    private void ensureAdmins(long tenantId) {
        String hash = passwords.encode(adminPassword);
        jdbc.update(
                "INSERT INTO tenant_admin(tenant_id,username,password_hash,display_name,status) "
                        + "VALUES(?,'admin',?,'演示租户管理员','ACTIVE') ON DUPLICATE KEY UPDATE "
                        + "password_hash=VALUES(password_hash),display_name=VALUES(display_name),status='ACTIVE'",
                tenantId, hash);
        jdbc.update(
                "INSERT INTO platform_admin(username,password_hash,display_name,status) "
                        + "VALUES('platform',?,'演示平台管理员','ACTIVE') ON DUPLICATE KEY UPDATE "
                        + "password_hash=VALUES(password_hash),display_name=VALUES(display_name),status='ACTIVE'",
                hash);
    }

    private void ensureCommissionRule(long tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commission_rule WHERE tenant_id=? AND status='ACTIVE'", Long.class, tenantId);
        if (count != null && count == 0) {
            long adminId = jdbc.queryForObject(
                    "SELECT id FROM tenant_admin WHERE tenant_id=? AND username='admin'", Long.class, tenantId);
            jdbc.update(
                    "INSERT INTO commission_rule(tenant_id,self_rate_bps,direct_invite_rate_bps,"
                            + "effective_from,status,created_by) VALUES(?,5000,1000,UTC_TIMESTAMP(6),'ACTIVE',?)",
                    tenantId, adminId);
        }
    }

    private void ensureMockChannels(long tenantId) {
        configurations.save(tenantId, AffiliatePlatform.MEITUAN, new ConfigurationWrite(
                "美团本地 Mock", "mock-app-key", "mock-secret", "mock-act", "mock-pid", "validation",
                Set.of("GET_REFERRAL_LINK", "QUERY_ORDER"), true));
        configurations.save(tenantId, AffiliatePlatform.ELEME, new ConfigurationWrite(
                "饿了么本地 Mock", "mock-app-key", "mock-secret", "mock-act", "mock-pid", "validation",
                Set.of("OFFICIAL_ACTIVITY", "POSITIVE_ORDER", "REFUND_ORDER"), true));
    }
}
