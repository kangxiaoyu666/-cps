package com.waimaicps.auth;

import com.waimaicps.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MiniSessionService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final byte[] pepper;
    private final long ttlSeconds;

    public MiniSessionService(JdbcTemplate jdbc, @Value("${app.session.token-pepper}") String pepper,
            @Value("${app.session.ttl-seconds}") long ttlSeconds) {
        this.jdbc = jdbc;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        if (this.pepper.length < 32) throw new IllegalArgumentException("SESSION_TOKEN_PEPPER must contain at least 32 UTF-8 bytes");
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(long tenantId, long userId) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO mini_session(tenant_id,user_id,token_hash,expires_at,last_seen_at) VALUES(?,?,?,?,?)",
                tenantId, userId, hash(token), Timestamp.from(now.plus(ttlSeconds, ChronoUnit.SECONDS)), Timestamp.from(now));
        return token;
    }

    public MiniPrincipal authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "缺少会话令牌");
        }
        return jdbc.query("SELECT tenant_id,user_id FROM mini_session WHERE token_hash=? AND revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(6)",
                rs -> {
                    if (!rs.next()) {
                        throw new BusinessException("UNAUTHORIZED", "会话已失效");
                    }
                    return new MiniPrincipal(rs.getLong("tenant_id"), rs.getLong("user_id"));
                }, hash(token));
    }

    private byte[] hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash session token", ex);
        }
    }
}
