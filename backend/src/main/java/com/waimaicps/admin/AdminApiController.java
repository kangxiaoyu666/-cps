package com.waimaicps.admin;

import com.waimaicps.affiliate.AffiliateAdapter;
import com.waimaicps.affiliate.AffiliatePlatform;
import com.waimaicps.auth.AdminContext;
import com.waimaicps.auth.AdminPrincipal;
import com.waimaicps.common.ApiResponse;
import com.waimaicps.common.PageQuery;
import com.waimaicps.common.RequestIdFilter;
import com.waimaicps.jobs.AffiliateOrderSyncService;
import com.waimaicps.withdrawal.WithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminApiController {
    private final JdbcTemplate jdbc;
    private final WithdrawalService withdrawals;
    private final AffiliateOrderSyncService syncService;
    private final AffiliateAdapter meituan;
    private final AffiliateAdapter eleme;

    public AdminApiController(JdbcTemplate jdbc, WithdrawalService withdrawals, AffiliateOrderSyncService syncService,
            @Qualifier("MEITUAN") AffiliateAdapter meituan, @Qualifier("ELEME") AffiliateAdapter eleme) {
        this.jdbc = jdbc;
        this.withdrawals = withdrawals;
        this.syncService = syncService;
        this.meituan = meituan;
        this.eleme = eleme;
    }

    @GetMapping("/api/v1/admin/dashboard")
    ApiResponse<?> dashboard(HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        long tenantId = admin.requireTenantId();
        Map<String, Object> data = Map.of(
                "tenantId", tenantId,
                "ordersToday", count("SELECT COUNT(*) FROM affiliate_order WHERE tenant_id=? AND created_at>=UTC_DATE()", tenantId),
                "pendingWithdrawals", count("SELECT COUNT(*) FROM withdrawal WHERE tenant_id=? AND status='SUBMITTED'", tenantId));
        return ok(data, request);
    }

    @GetMapping("/api/v1/admin/{resource:content|channels|pids|users|orders|commissions|commission-rules|wallets|withdrawals|audits|audit-logs}")
    ApiResponse<?> list(@PathVariable String resource, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        PageQuery query = new PageQuery(page, pageSize);
        return ok(Map.of("items", listResource(resource, admin.requireTenantId(), query), "page", query.page(),
                "pageSize", query.pageSize(), "total", countResource(resource, admin.requireTenantId())), request);
    }

    @PostMapping("/api/v1/admin/channels/{platform}/validate")
    ApiResponse<?> validate(@PathVariable String platform, HttpServletRequest request) {
        long tenantId = AdminContext.requireTenantAdmin().requireTenantId();
        return ok(adapter(platform).validateConfiguration(tenantId), request);
    }

    @PostMapping("/api/v1/admin/orders/sync/{platform}")
    ApiResponse<?> sync(@PathVariable String platform, HttpServletRequest request) {
        long tenantId = AdminContext.requireTenantAdmin().requireTenantId();
        AffiliatePlatform normalized = AffiliatePlatform.parse(platform);
        Instant now = Instant.now();
        Instant from = syncService.suggestedStart(tenantId, normalized, now);
        return ok(syncService.sync(tenantId, normalized.name(), from, now), request);
    }

    @PostMapping("/api/v1/admin/withdrawals/{id}/approve")
    ApiResponse<?> approve(@PathVariable long id, HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        withdrawals.approve(admin.requireTenantId(), admin.adminId(), id);
        return ok(Map.of("approved", true), request);
    }

    @PostMapping("/api/v1/admin/withdrawals/{id}/reject")
    ApiResponse<?> reject(@PathVariable long id, @Valid @RequestBody RejectRequest input, HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        withdrawals.reject(admin.requireTenantId(), admin.adminId(), id, input.reason());
        return ok(Map.of("rejected", true), request);
    }

    @PostMapping("/api/v1/admin/withdrawals/{id}/paid")
    ApiResponse<?> paid(@PathVariable long id, @Valid @RequestBody PaidRequest input, HttpServletRequest request) {
        AdminPrincipal admin = AdminContext.requireTenantAdmin();
        withdrawals.markPaid(admin.requireTenantId(), admin.adminId(), id, input.channel(), input.reference(), input.proofUrl());
        return ok(Map.of("paid", true), request);
    }

    @GetMapping("/api/v1/platform/tenants")
    ApiResponse<?> tenants(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        AdminContext.requirePlatformAdmin();
        PageQuery query = new PageQuery(page, pageSize);
        List<Map<String, Object>> items = jdbc.queryForList("SELECT id,code,name,status,brand_name,created_at FROM tenant ORDER BY id DESC LIMIT ? OFFSET ?",
                query.pageSize(), query.offset());
        return ok(Map.of("items", items, "page", query.page(), "pageSize", query.pageSize(),
                "total", jdbc.queryForObject("SELECT COUNT(*) FROM tenant", Long.class)), request);
    }

    @GetMapping("/api/v1/platform/jobs")
    ApiResponse<?> jobs(HttpServletRequest request) {
        AdminContext.requirePlatformAdmin();
        return ok(jdbc.queryForList("SELECT id,tenant_id,platform,job_type,status,started_at,finished_at,error_code FROM job_execution ORDER BY id DESC LIMIT 100"), request);
    }

    @GetMapping("/api/v1/platform/health-summary")
    ApiResponse<?> health(HttpServletRequest request) {
        AdminContext.requirePlatformAdmin();
        return ok(Map.of("database", jdbc.queryForObject("SELECT 1", Integer.class) == 1, "status", "UP"), request);
    }

    private List<Map<String, Object>> listResource(String resource, long tenantId, PageQuery query) {
        ResourceQuery definition = resource(resource);
        return jdbc.queryForList("SELECT " + definition.columns + " FROM " + definition.table + " WHERE tenant_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
                tenantId, query.pageSize(), query.offset());
    }

    private long countResource(String resource, long tenantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + resource(resource).table + " WHERE tenant_id=?", Long.class, tenantId);
    }

    private ResourceQuery resource(String resource) {
        return switch (resource) {
            case "content" -> new ResourceQuery("content_config", "id,config_type,config_key,status,published_at,created_at,updated_at,version");
            case "channels" -> new ResourceQuery("affiliate_channel", "id,platform,display_name,status,last_validated_at,last_validation_result,created_at,updated_at,version");
            case "pids" -> new ResourceQuery("affiliate_pid", "id,channel_id,user_id,external_pid,external_sid,relation_id,status,bound_at,created_at,updated_at,version");
            case "users" -> new ResourceQuery("wx_user", "id,nickname,avatar_url,direct_inviter_id,invite_code,status,invited_at,created_at,updated_at,version");
            case "orders" -> new ResourceQuery("affiliate_order", "id,platform,external_order_id,attributed_user_id,status,order_amount_cent,estimated_commission_cent,settled_commission_cent,paid_at,settled_at,refunded_at,commission_processed_at,created_at,updated_at,version");
            case "commissions" -> new ResourceQuery("commission_record", "id,order_id,beneficiary_user_id,reward_type,rate_bps_snapshot,amount_cent,status,reversal_of_id,business_no,created_at");
            case "commission-rules" -> new ResourceQuery("commission_rule", "id,self_rate_bps,direct_invite_rate_bps,effective_from,effective_to,status,created_at,version");
            case "wallets" -> new ResourceQuery("wallet_entry", "id,user_id,business_type,business_no,direction,available_delta_cent,frozen_delta_cent,debt_delta_cent,available_after_cent,frozen_after_cent,debt_after_cent,created_at");
            case "withdrawals" -> new ResourceQuery("withdrawal", "id,user_id,withdrawal_no,amount_cent,status,payout_channel,payout_reference,proof_url,rejection_reason,submitted_at,approved_at,paid_at,canceled_at,rejected_at,version");
            case "audits", "audit-logs" -> new ResourceQuery("audit_log", "id,actor_type,actor_id,action,resource_type,resource_id,request_id,result,created_at");
            default -> throw new IllegalArgumentException("Unsupported resource");
        };
    }

    private record ResourceQuery(String table, String columns) {}

    private long count(String sql, long tenantId) {
        return jdbc.queryForObject(sql, Long.class, tenantId);
    }

    private AffiliateAdapter adapter(String platform) {
        return switch (platform.toUpperCase()) {
            case "MEITUAN" -> meituan;
            case "ELEME" -> eleme;
            default -> throw new com.waimaicps.common.BusinessException("UNSUPPORTED_PLATFORM", "不支持的联盟平台");
        };
    }

    private ApiResponse<?> ok(Object data, HttpServletRequest request) {
        return ApiResponse.success(data, String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }

    record RejectRequest(@NotBlank String reason) {}
    record PaidRequest(@NotBlank String channel, @NotBlank String reference, @NotBlank String proofUrl) {}
}
