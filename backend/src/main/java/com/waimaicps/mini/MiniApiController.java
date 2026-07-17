package com.waimaicps.mini;

import com.waimaicps.affiliate.AffiliateAdapter;
import com.waimaicps.auth.MiniPrincipal;
import com.waimaicps.auth.TenantContext;
import com.waimaicps.common.ApiResponse;
import com.waimaicps.common.BusinessException;
import com.waimaicps.common.PageQuery;
import com.waimaicps.common.RequestIdFilter;
import com.waimaicps.user.ShareSceneService;
import com.waimaicps.wallet.WalletLedgerService;
import com.waimaicps.withdrawal.WithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mini")
public class MiniApiController {
    private final JdbcTemplate jdbc;
    private final WalletLedgerService wallet;
    private final WithdrawalService withdrawals;
    private final AffiliateAdapter meituan;
    private final AffiliateAdapter eleme;
    private final ShareSceneService shareScenes;

    public MiniApiController(
            JdbcTemplate jdbc,
            WalletLedgerService wallet,
            WithdrawalService withdrawals,
            @Qualifier("MEITUAN") AffiliateAdapter meituan,
            @Qualifier("ELEME") AffiliateAdapter eleme,
            ShareSceneService shareScenes) {
        this.jdbc = jdbc;
        this.wallet = wallet;
        this.withdrawals = withdrawals;
        this.meituan = meituan;
        this.eleme = eleme;
        this.shareScenes = shareScenes;
    }

    @GetMapping("/home")
    ApiResponse<?> home(HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        List<Map<String, Object>> content = jdbc.queryForList(
                "SELECT config_type,config_key,content_json FROM content_config "
                        + "WHERE tenant_id=? AND status='PUBLISHED' ORDER BY id",
                principal.tenantId());
        return ok(Map.of("content", content), request);
    }

    @GetMapping("/profile")
    ApiResponse<?> profile(HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        return ok(jdbc.queryForMap(
                "SELECT id,nickname,avatar_url,invite_code,status FROM wx_user "
                        + "WHERE tenant_id=? AND id=?",
                principal.tenantId(), principal.userId()), request);
    }

    @PutMapping("/profile")
    ApiResponse<?> updateProfile(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        jdbc.update(
                "UPDATE wx_user SET nickname=?,avatar_url=?,version=version+1 "
                        + "WHERE tenant_id=? AND id=?",
                body.get("nickname"), body.get("avatarUrl"),
                principal.tenantId(), principal.userId());
        return ok(Map.of("updated", true), request);
    }

    @GetMapping("/orders")
    ApiResponse<?> orders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        PageQuery query = new PageQuery(page, pageSize);
        return ok(jdbc.queryForList(
                "SELECT external_order_id,platform,status,order_amount_cent,"
                        + "settled_commission_cent,paid_at FROM affiliate_order "
                        + "WHERE tenant_id=? AND attributed_user_id=? "
                        + "ORDER BY id DESC LIMIT ? OFFSET ?",
                principal.tenantId(), principal.userId(),
                query.pageSize(), query.offset()), request);
    }

    @GetMapping("/wallet")
    ApiResponse<?> wallet(HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        return ok(wallet.summary(principal.tenantId(), principal.userId()), request);
    }

    @GetMapping("/wallet/entries")
    ApiResponse<?> entries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        PageQuery query = new PageQuery(page, pageSize);
        return ok(jdbc.queryForList(
                "SELECT business_type,business_no,available_delta_cent,"
                        + "frozen_delta_cent,debt_delta_cent,available_after_cent,frozen_after_cent,"
                        + "debt_after_cent,created_at "
                        + "FROM wallet_entry WHERE tenant_id=? AND user_id=? "
                        + "ORDER BY id DESC LIMIT ? OFFSET ?",
                principal.tenantId(), principal.userId(),
                query.pageSize(), query.offset()), request);
    }

    @GetMapping("/invitations")
    ApiResponse<?> invitations(HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        return ok(jdbc.queryForList(
                "SELECT nickname,avatar_url,invited_at FROM wx_user "
                        + "WHERE tenant_id=? AND direct_inviter_id=? ORDER BY invited_at DESC",
                principal.tenantId(), principal.userId()), request);
    }

    @PostMapping("/promotion-links")
    ApiResponse<?> promotion(
            @Valid @RequestBody PromotionRequest body,
            HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        AffiliateAdapter adapter = adapter(body.platform());
        return ok(adapter.generatePromotionLink(
                principal.tenantId(), principal.userId(), body.activityCode()), request);
    }

    @GetMapping("/withdrawals")
    ApiResponse<?> withdrawalList(HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        return ok(jdbc.queryForList(
                "SELECT id,withdrawal_no,amount_cent,status,submitted_at,paid_at "
                        + "FROM withdrawal WHERE tenant_id=? AND user_id=? ORDER BY id DESC",
                principal.tenantId(), principal.userId()), request);
    }

    @PostMapping("/withdrawals")
    ApiResponse<?> submit(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody WithdrawalRequest body,
            HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        return ok(withdrawals.submit(
                principal.tenantId(), principal.userId(), body.amountCent(), key), request);
    }

    @PostMapping("/withdrawals/{id}/cancel")
    ApiResponse<?> cancel(
            @PathVariable long id,
            HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        withdrawals.cancel(principal.tenantId(), principal.userId(), id);
        return ok(Map.of("canceled", true), request);
    }

    @PostMapping("/share-scenes")
    ApiResponse<?> shareScene(HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        return ok(Map.of(
                "scene", shareScenes.create(principal.tenantId(), principal.userId()),
                "expiresIn", 86400), request);
    }

    @GetMapping("/content/help")
    ApiResponse<?> help(HttpServletRequest request) {
        MiniPrincipal principal = TenantContext.require();
        return ok(jdbc.queryForList(
                "SELECT config_key,content_json FROM content_config "
                        + "WHERE tenant_id=? AND config_type='HELP' "
                        + "AND status='PUBLISHED' ORDER BY id",
                principal.tenantId()), request);
    }

    private AffiliateAdapter adapter(String platform) {
        return switch (platform.toUpperCase()) {
            case "MEITUAN" -> meituan;
            case "ELEME" -> eleme;
            default -> throw new BusinessException(
                    "UNSUPPORTED_PLATFORM", "不支持的联盟平台");
        };
    }

    private ApiResponse<?> ok(Object data, HttpServletRequest request) {
        return ApiResponse.success(
                data, String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }

    record PromotionRequest(@NotBlank String platform, String activityCode) {}
    record WithdrawalRequest(@Min(1) long amountCent) {}
}
