package com.waimaicps.auth;

import com.waimaicps.common.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {
    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    public AdminAuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    public AdminPrincipal loginTenant(String tenantCode, String username, String password, String clientKey) {
        enforceRateLimit("tenant:" + clientKey + ":" + username);
        AdminRow row = jdbc.query("SELECT a.id,a.tenant_id,a.password_hash,a.display_name FROM tenant_admin a JOIN tenant t ON t.id=a.tenant_id WHERE t.code=? AND t.status='ACTIVE' AND a.username=? AND a.status='ACTIVE'",
                rs -> rs.next() ? mapTenant(rs) : null, tenantCode, username);
        if (row == null || !passwordEncoder.matches(password, row.passwordHash)) {
            throw new BusinessException("INVALID_CREDENTIALS", "账号或密码错误");
        }
        jdbc.update("UPDATE tenant_admin SET last_login_at=UTC_TIMESTAMP(6) WHERE id=? AND tenant_id=?", row.id, row.tenantId);
        return new AdminPrincipal(row.id, row.tenantId, AdminPrincipal.Role.TENANT_ADMIN, row.displayName);
    }

    public AdminPrincipal loginPlatform(String username, String password, String clientKey) {
        enforceRateLimit("platform:" + clientKey + ":" + username);
        AdminRow row = jdbc.query("SELECT id,password_hash,display_name FROM platform_admin WHERE username=? AND status='ACTIVE'",
                rs -> rs.next() ? mapPlatform(rs) : null, username);
        if (row == null || !passwordEncoder.matches(password, row.passwordHash)) {
            throw new BusinessException("INVALID_CREDENTIALS", "账号或密码错误");
        }
        jdbc.update("UPDATE platform_admin SET last_login_at=UTC_TIMESTAMP(6) WHERE id=?", row.id);
        return new AdminPrincipal(row.id, null, AdminPrincipal.Role.PLATFORM_ADMIN, row.displayName);
    }

    private void enforceRateLimit(String identity) {
        String key = "login-limit:" + identity;
        try {
            Long attempts = redis.opsForValue().increment(key);
            if (attempts != null && attempts == 1) redis.expire(key, java.time.Duration.ofMinutes(1));
            if (attempts != null && attempts > MAX_ATTEMPTS_PER_MINUTE) {
                throw new BusinessException("LOGIN_RATE_LIMITED", "登录尝试过于频繁，请稍后再试");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ignored) {
            // Database credential verification remains authoritative if Redis is temporarily unavailable.
        }
    }

    private AdminRow mapTenant(ResultSet rs) throws SQLException {
        return new AdminRow(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("password_hash"), rs.getString("display_name"));
    }

    private AdminRow mapPlatform(ResultSet rs) throws SQLException {
        return new AdminRow(rs.getLong("id"), null, rs.getString("password_hash"), rs.getString("display_name"));
    }

    private record AdminRow(long id, Long tenantId, String passwordHash, String displayName) {}
}
