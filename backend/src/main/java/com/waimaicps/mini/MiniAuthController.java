package com.waimaicps.mini;

import com.waimaicps.common.ApiResponse;
import com.waimaicps.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mini/auth")
public class MiniAuthController {
    private final MiniAuthService auth;
    private final String tenantCode;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public MiniAuthController(MiniAuthService auth, org.springframework.jdbc.core.JdbcTemplate jdbc,
            @Value("${app.mini.default-tenant-code:}") String tenantCode) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.tenantCode = tenantCode;
    }

    @PostMapping("/login")
    ApiResponse<Map<String, Object>> login(@RequestHeader(value = "X-Tenant-Code", required = false) String headerTenantCode,
            @Valid @RequestBody LoginRequest input, HttpServletRequest request) {
        String code = headerTenantCode == null || headerTenantCode.isBlank() ? tenantCode : headerTenantCode;
        Long tenantId = jdbc.query("SELECT id FROM tenant WHERE code=? AND status='ACTIVE'",
                rs -> rs.next() ? rs.getLong(1) : null, code);
        if (tenantId == null) throw new com.waimaicps.common.BusinessException("TENANT_NOT_FOUND", "租户不存在或已停用");
        MiniAuthService.LoginResult result = auth.login(tenantId, input.code, input.scene);
        return ApiResponse.success(Map.of("token", result.token(), "expiresIn", result.expiresIn()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }

    record LoginRequest(@NotBlank String code, String scene) {}
}
