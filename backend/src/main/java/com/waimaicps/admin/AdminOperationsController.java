package com.waimaicps.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.auth.AdminContext;
import com.waimaicps.auth.AdminPrincipal;
import com.waimaicps.common.ApiResponse;
import com.waimaicps.common.BusinessException;
import com.waimaicps.common.RequestIdFilter;
import com.waimaicps.jobs.AffiliateOrderSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AdminOperationsController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwords;
    private final AffiliateOrderSyncService syncService;

    public AdminOperationsController(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            PasswordEncoder passwords,
            AffiliateOrderSyncService syncService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.passwords = passwords;
        this.syncService = syncService;
    }

    @PutMapping("/admin/content/{configType}/{configKey}")
    @Transactional
    ApiResponse<?> saveContent(
            @PathVariable String configType,
            @PathVariable String configKey,
            @Valid @RequestBody ContentWrite input,
            HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        long tenantId = admin.requireTenantId();
        String json = json(input.content());
        jdbc.update(
                "INSERT INTO content_config(tenant_id,config_type,config_key,content_json,status,"
                        + "published_at,created_by) VALUES(?,?,?,CAST(? AS JSON),?,CASE WHEN ?='PUBLISHED' "
                        + "THEN UTC_TIMESTAMP(6) ELSE NULL END,?) ON DUPLICATE KEY UPDATE "
                        + "content_json=VALUES(content_json),status=VALUES(status),published_at=VALUES(published_at),"
                        + "created_by=VALUES(created_by),version=version+1",
                tenantId, configType, configKey, json, input.status(), input.status(), admin.adminId());
        audit(tenantId, admin.adminId(), "CONTENT_SAVE", "CONTENT", configType + ":" + configKey, request);
        return ok(Map.of("saved", true), request);
    }

    @PostMapping("/admin/pids")
    @Transactional
    ApiResponse<?> createPid(@Valid @RequestBody PidWrite input, HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        long tenantId = admin.requireTenantId();
        Long channelId = jdbc.query(
                "SELECT id FROM affiliate_channel WHERE tenant_id=? AND platform=?",
                rs -> rs.next() ? rs.getLong(1) : null, tenantId, input.platform().toUpperCase());
        if (channelId == null) {
            throw new BusinessException("AFFILIATE_NOT_CONFIGURED", "请先配置联盟渠道");
        }
        try {
            jdbc.update(
                    "INSERT INTO affiliate_pid(tenant_id,channel_id,external_pid,external_sid,relation_id,status) "
                            + "VALUES(?,?,?,?,?,'AVAILABLE')",
                    tenantId, channelId, input.externalPid(), input.externalSid(), input.relationId());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("PID_ALREADY_EXISTS", "该推广位已经存在");
        }
        audit(tenantId, admin.adminId(), "PID_CREATE", "AFFILIATE_PID", input.externalPid(), request);
        return ok(Map.of("created", true), request);
    }

    @PutMapping("/admin/pids/{id}/binding")
    @Transactional
    ApiResponse<?> bindPid(
            @PathVariable long id,
            @RequestBody PidBinding input,
            HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        long tenantId = admin.requireTenantId();
        if (input.userId() != null && !userBelongsToTenant(tenantId, input.userId())) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        int updated = jdbc.update(
                "UPDATE affiliate_pid SET user_id=?,status=?,bound_at=CASE WHEN ? IS NULL THEN NULL "
                        + "ELSE UTC_TIMESTAMP(6) END,version=version+1 WHERE tenant_id=? AND id=?",
                input.userId(), input.userId() == null ? "AVAILABLE" : "BOUND", input.userId(), tenantId, id);
        if (updated != 1) {
            throw new BusinessException("PID_NOT_FOUND", "推广位不存在");
        }
        audit(tenantId, admin.adminId(), "PID_BIND", "AFFILIATE_PID", Long.toString(id), request);
        return ok(Map.of("updated", true), request);
    }

    @PostMapping("/admin/commission-rules")
    @Transactional
    ApiResponse<?> createRule(@Valid @RequestBody RuleWrite input, HttpServletRequest request) {
        if (input.selfRateBps() + input.directInviteRateBps() > 10000) {
            throw new BusinessException("COMMISSION_RULE_INVALID", "分佣比例合计不能超过 100%");
        }
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        long tenantId = admin.requireTenantId();
        Instant effectiveFrom = input.effectiveFrom() == null ? Instant.now() : input.effectiveFrom();
        jdbc.update(
                "UPDATE commission_rule SET status='INACTIVE',effective_to=?,version=version+1 "
                        + "WHERE tenant_id=? AND status='ACTIVE' AND effective_from<?",
                Timestamp.from(effectiveFrom), tenantId, Timestamp.from(effectiveFrom));
        jdbc.update(
                "INSERT INTO commission_rule(tenant_id,self_rate_bps,direct_invite_rate_bps,"
                        + "effective_from,status,created_by) VALUES(?,?,?,?, 'ACTIVE',?)",
                tenantId, input.selfRateBps(), input.directInviteRateBps(),
                Timestamp.from(effectiveFrom), admin.adminId());
        audit(tenantId, admin.adminId(), "RULE_CREATE", "COMMISSION_RULE", null, request);
        return ok(Map.of("created", true), request);
    }

    @PostMapping("/admin/orders/{id}/retry")
    ApiResponse<?> retryOrder(@PathVariable long id, HttpServletRequest request) {
        long tenantId = AdminContext.requireTenantAdmin().requireTenantId();
        OrderIdentity order = jdbc.query(
                "SELECT platform,external_order_id FROM affiliate_order WHERE tenant_id=? AND id=?",
                rs -> rs.next() ? new OrderIdentity(rs.getString(1), rs.getString(2)) : null, tenantId, id);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        return ok(syncService.syncOrder(tenantId, order.platform(), order.externalOrderId()), request);
    }

    @PostMapping("/platform/tenants")
    @Transactional
    ApiResponse<?> createTenant(@Valid @RequestBody TenantWrite input, HttpServletRequest request) {
        AdminPrincipal platform = AdminContext.requirePlatformAdmin();
        try {
            jdbc.update("INSERT INTO tenant(code,name,status,brand_name) VALUES(?,?, 'ACTIVE',?)",
                    input.code(), input.name(), input.brandName());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("TENANT_ALREADY_EXISTS", "租户编码已经存在");
        }
        long tenantId = jdbc.queryForObject("SELECT id FROM tenant WHERE code=?", Long.class, input.code());
        jdbc.update(
                "INSERT INTO tenant_admin(tenant_id,username,password_hash,display_name,status) "
                        + "VALUES(?,?,?,?, 'ACTIVE')",
                tenantId, input.adminUsername(), passwords.encode(input.adminPassword()), input.adminDisplayName());
        audit(tenantId, platform.adminId(), "TENANT_CREATE", "TENANT", Long.toString(tenantId), request);
        return ok(Map.of("id", tenantId), request);
    }

    @PutMapping("/platform/tenants/{id}/status")
    ApiResponse<?> updateTenantStatus(
            @PathVariable long id,
            @Valid @RequestBody StatusWrite input,
            HttpServletRequest request) {
        AdminPrincipal platform = AdminContext.requirePlatformAdmin();
        int updated = jdbc.update(
                "UPDATE tenant SET status=?,version=version+1 WHERE id=?",
                input.status(), id);
        if (updated != 1) {
            throw new BusinessException("TENANT_NOT_FOUND", "租户不存在");
        }
        audit(id, platform.adminId(), "TENANT_STATUS", "TENANT", Long.toString(id), request);
        return ok(Map.of("updated", true), request);
    }

    @DeleteMapping("/admin/content/{configType}/{configKey}")
    ApiResponse<?> unpublishContent(
            @PathVariable String configType,
            @PathVariable String configKey,
            HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        int updated = jdbc.update(
                "UPDATE content_config SET status='ARCHIVED',version=version+1 "
                        + "WHERE tenant_id=? AND config_type=? AND config_key=?",
                admin.requireTenantId(), configType, configKey);
        return ok(Map.of("archived", updated == 1), request);
    }

    private boolean userBelongsToTenant(long tenantId, long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wx_user WHERE tenant_id=? AND id=?",
                Integer.class, tenantId, userId);
        return count != null && count == 1;
    }

    private String json(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content == null ? Map.of() : content);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("CONTENT_INVALID", "内容配置无法序列化");
        }
    }

    private void audit(
            Long tenantId,
            long actorId,
            String action,
            String resourceType,
            String resourceId,
            HttpServletRequest request) {
        jdbc.update(
                "INSERT INTO audit_log(tenant_id,actor_type,actor_id,action,resource_type,resource_id,"
                        + "request_id,ip_address,detail_json,result) VALUES(?,'ADMIN',?,?,?,?,?,?,"
                        + "JSON_OBJECT(),'SUCCESS')",
                tenantId, actorId, action, resourceType, resourceId,
                String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE)), request.getRemoteAddr());
    }

    private ApiResponse<?> ok(Object data, HttpServletRequest request) {
        return ApiResponse.success(data, String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }

    public record ContentWrite(@NotBlank String status, Map<String, Object> content) {
    }

    public record PidWrite(
            @NotBlank String platform,
            @NotBlank String externalPid,
            String externalSid,
            String relationId) {
    }

    public record PidBinding(Long userId) {
    }

    public record RuleWrite(
            @Min(0) @Max(10000) int selfRateBps,
            @Min(0) @Max(10000) int directInviteRateBps,
            Instant effectiveFrom) {
    }

    public record TenantWrite(
            @NotBlank String code,
            @NotBlank String name,
            String brandName,
            @NotBlank String adminUsername,
            @NotBlank String adminPassword,
            @NotBlank String adminDisplayName) {
    }

    public record StatusWrite(@NotBlank String status) {
    }

    private record OrderIdentity(String platform, String externalOrderId) {
    }
}
