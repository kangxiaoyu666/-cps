package com.waimaicps.affiliate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.common.BusinessException;
import com.waimaicps.crypto.FieldCryptoService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AffiliateConfigurationService {
    private static final String KEY_VERSION = "v1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FieldCryptoService crypto;

    public AffiliateConfigurationService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${app.affiliate.encryption-key:}") String encryptionKey) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.crypto = new FieldCryptoService(encryptionKey);
    }

    @Transactional
    public ConfigurationView save(long tenantId, AffiliatePlatform platform, ConfigurationWrite input) {
        AffiliateConfiguration existing = findConfiguration(tenantId, platform);
        ConfigurationWrite merged = mergeSecrets(input, existing);
        validate(merged);
        AffiliateConfiguration configuration = new AffiliateConfiguration(
                merged.appKey().trim(), merged.appSecret(), merged.activityId().trim(), merged.pid().trim(),
                trimToNull(merged.sid()), Set.copyOf(merged.permissions()));
        byte[] encrypted = crypto.encrypt(writeJson(configuration));
        String displayName = merged.displayName().isBlank() ? platform.name() : merged.displayName().trim();
        int updated = jdbc.update(
                "UPDATE affiliate_channel SET display_name=?,encrypted_config=?,config_key_version=?,"
                        + "status=?,configured_at=UTC_TIMESTAMP(6),last_validated_at=NULL,"
                        + "last_validation_result=NULL,version=version+1 WHERE tenant_id=? AND platform=?",
                displayName, encrypted, KEY_VERSION, merged.enabled() ? "ACTIVE" : "DISABLED",
                tenantId, platform.name());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO affiliate_channel(tenant_id,platform,display_name,encrypted_config,"
                            + "config_key_version,status,configured_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",
                    tenantId, platform.name(), displayName, encrypted, KEY_VERSION,
                    merged.enabled() ? "ACTIVE" : "DISABLED");
        }
        return get(tenantId, platform);
    }

    public List<ConfigurationView> list(long tenantId) {
        return jdbc.query(
                "SELECT id,platform,display_name,status,encrypted_config,configured_at,last_validated_at,"
                        + "last_validation_result,updated_at,version FROM affiliate_channel "
                        + "WHERE tenant_id=? ORDER BY platform",
                (rs, rowNum) -> view(rs), tenantId);
    }

    public ConfigurationView get(long tenantId, AffiliatePlatform platform) {
        ConfigurationView view = jdbc.query(
                "SELECT id,platform,display_name,status,encrypted_config,configured_at,last_validated_at,"
                        + "last_validation_result,updated_at,version FROM affiliate_channel "
                        + "WHERE tenant_id=? AND platform=?",
                rs -> rs.next() ? view(rs) : null, tenantId, platform.name());
        if (view == null) {
            throw new BusinessException("AFFILIATE_NOT_CONFIGURED", platform.name() + " 联盟配置不存在");
        }
        return view;
    }

    public AffiliateConfiguration requireActive(long tenantId, AffiliatePlatform platform, String permission) {
        StoredConfiguration stored = jdbc.query(
                "SELECT status,encrypted_config FROM affiliate_channel WHERE tenant_id=? AND platform=?",
                rs -> rs.next() ? new StoredConfiguration(rs.getString(1), rs.getBytes(2)) : null,
                tenantId, platform.name());
        if (stored == null || stored.encrypted == null) {
            throw new BusinessException("AFFILIATE_NOT_CONFIGURED", platform.name() + " 联盟正式凭据未配置");
        }
        if (!"ACTIVE".equals(stored.status)) {
            throw new BusinessException("AFFILIATE_DISABLED", platform.name() + " 联盟渠道未启用");
        }
        AffiliateConfiguration configuration = read(stored.encrypted);
        if (!configuration.hasPermission(permission)) {
            throw new BusinessException("AFFILIATE_PERMISSION_MISSING", platform.name() + " 未声明所需 API 权限: " + permission);
        }
        return configuration;
    }

    @Transactional
    public void delete(long tenantId, AffiliatePlatform platform) {
        int deleted = jdbc.update(
                "UPDATE affiliate_channel SET encrypted_config=NULL,status='DISABLED',configured_at=NULL,"
                        + "last_validated_at=NULL,last_validation_result=NULL,version=version+1 "
                        + "WHERE tenant_id=? AND platform=? AND encrypted_config IS NOT NULL",
                tenantId, platform.name());
        if (deleted == 0) {
            throw new BusinessException("AFFILIATE_NOT_CONFIGURED", platform.name() + " 联盟配置不存在");
        }
    }

    @Transactional
    public void recordValidation(long tenantId, AffiliatePlatform platform, boolean valid, String message) {
        jdbc.update(
                "UPDATE affiliate_channel SET last_validated_at=UTC_TIMESTAMP(6),last_validation_result=?,"
                        + "version=version+1 WHERE tenant_id=? AND platform=?",
                (valid ? "SUCCESS: " : "FAILED: ") + truncate(message, 480), tenantId, platform.name());
    }

    private ConfigurationView view(ResultSet rs) throws SQLException {
        byte[] encrypted = rs.getBytes("encrypted_config");
        AffiliateConfiguration configuration = encrypted == null ? null : read(encrypted);
        return new ConfigurationView(
                rs.getLong("id"), AffiliatePlatform.valueOf(rs.getString("platform")),
                rs.getString("display_name"), rs.getString("status"), encrypted != null,
                configuration == null ? null : mask(configuration.appKey()),
                configuration == null ? null : configuration.activityId(),
                configuration == null ? null : configuration.pid(),
                configuration == null ? null : configuration.sid(),
                configuration == null ? Set.of() : configuration.permissions(),
                rs.getTimestamp("configured_at") == null ? null : rs.getTimestamp("configured_at").toInstant(),
                rs.getTimestamp("last_validated_at") == null ? null : rs.getTimestamp("last_validated_at").toInstant(),
                rs.getString("last_validation_result"), rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }

    private AffiliateConfiguration read(byte[] encrypted) {
        try {
            return objectMapper.readValue(crypto.decrypt(encrypted), AffiliateConfiguration.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("AFFILIATE_CONFIG_INVALID", "联盟配置密文无法解析");
        }
    }

    private String writeJson(AffiliateConfiguration configuration) {
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("AFFILIATE_CONFIG_INVALID", "联盟配置无法序列化");
        }
    }

    private AffiliateConfiguration findConfiguration(long tenantId, AffiliatePlatform platform) {
        byte[] encrypted = jdbc.query(
                "SELECT encrypted_config FROM affiliate_channel WHERE tenant_id=? AND platform=?",
                rs -> rs.next() ? rs.getBytes(1) : null, tenantId, platform.name());
        return encrypted == null ? null : read(encrypted);
    }

    static ConfigurationWrite mergeSecrets(ConfigurationWrite input, AffiliateConfiguration existing) {
        if (input == null) {
            return null;
        }
        return new ConfigurationWrite(
                input.displayName(),
                blankStatic(input.appKey()) && existing != null ? existing.appKey() : input.appKey(),
                blankStatic(input.appSecret()) && existing != null ? existing.appSecret() : input.appSecret(),
                input.activityId(), input.pid(), input.sid(), input.permissions(), input.enabled());
    }

    private void validate(ConfigurationWrite input) {
        if (input == null || blank(input.appKey()) || blank(input.appSecret()) || blank(input.activityId())
                || blank(input.pid()) || input.permissions() == null) {
            throw new BusinessException("AFFILIATE_CONFIG_INVALID", "appKey、appSecret、活动、PID 和 API 权限均为必填项");
        }
        Set<String> required = input.permissions();
        if (required.isEmpty()) {
            throw new BusinessException("AFFILIATE_CONFIG_INVALID", "至少需要声明转链和订单查询 API 权限");
        }
    }

    private boolean blank(String value) {
        return blankStatic(value);
    }

    private static boolean blankStatic(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private String mask(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private String truncate(String value, int length) {
        String safe = value == null ? "无返回信息" : value;
        return safe.substring(0, Math.min(safe.length(), length));
    }

    public record ConfigurationWrite(
            String displayName,
            String appKey,
            String appSecret,
            String activityId,
            String pid,
            String sid,
            Set<String> permissions,
            boolean enabled) {
        public ConfigurationWrite {
            displayName = displayName == null ? "" : displayName;
        }
    }

    public record ConfigurationView(
            long id,
            AffiliatePlatform platform,
            String displayName,
            String status,
            boolean secretConfigured,
            String appKeyMasked,
            String activityId,
            String pid,
            String sid,
            Set<String> permissions,
            java.time.Instant configuredAt,
            java.time.Instant lastValidatedAt,
            String lastValidationResult,
            java.time.Instant updatedAt,
            long version) {
    }

    private record StoredConfiguration(String status, byte[] encrypted) {
    }
}
