package com.waimaicps.admin;

import com.waimaicps.auth.AdminAuthService;
import com.waimaicps.auth.AdminAuthenticationFilter;
import com.waimaicps.auth.AdminPrincipal;
import com.waimaicps.common.ApiResponse;
import com.waimaicps.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminAuthController {
    private final AdminAuthService auth;

    public AdminAuthController(AdminAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/v1/admin/auth/login")
    ApiResponse<?> tenantLogin(@Valid @RequestBody TenantLoginRequest input, HttpServletRequest request) {
        AdminPrincipal principal = auth.loginTenant(input.tenantCode(), input.username(), input.password(), clientKey(request));
        establishSession(request, principal);
        return ok(Map.of("displayName", principal.displayName(), "role", principal.role().name()), request);
    }

    @PostMapping("/api/v1/platform/auth/login")
    ApiResponse<?> platformLogin(@Valid @RequestBody PlatformLoginRequest input, HttpServletRequest request) {
        AdminPrincipal principal = auth.loginPlatform(input.username(), input.password(), clientKey(request));
        establishSession(request, principal);
        return ok(Map.of("displayName", principal.displayName(), "role", principal.role().name()), request);
    }

    @GetMapping({"/api/v1/admin/auth/csrf", "/api/v1/platform/auth/csrf"})
    ApiResponse<?> csrf(CsrfToken token, HttpServletRequest request) {
        return ok(Map.of("headerName", token.getHeaderName()), request);
    }

    @PostMapping({"/api/v1/admin/auth/logout", "/api/v1/platform/auth/logout"})
    ApiResponse<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ok(Map.of("loggedOut", true), request);
    }

    private void establishSession(HttpServletRequest request, AdminPrincipal principal) {
        request.getSession(true).setAttribute(AdminAuthenticationFilter.SESSION_ATTRIBUTE, principal);
        request.changeSessionId();
    }

    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private ApiResponse<?> ok(Object data, HttpServletRequest request) {
        return ApiResponse.success(data, String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }

    record TenantLoginRequest(@NotBlank String tenantCode, @NotBlank String username, @NotBlank String password) {}
    record PlatformLoginRequest(@NotBlank String username, @NotBlank String password) {}
}
