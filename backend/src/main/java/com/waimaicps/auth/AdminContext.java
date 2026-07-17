package com.waimaicps.auth;

import com.waimaicps.common.BusinessException;

public final class AdminContext {
    private static final ThreadLocal<AdminPrincipal> CURRENT = new ThreadLocal<>();

    private AdminContext() {}

    public static void set(AdminPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AdminPrincipal require() {
        AdminPrincipal principal = CURRENT.get();
        if (principal == null) throw new BusinessException("UNAUTHORIZED", "管理员未登录");
        return principal;
    }

    public static AdminPrincipal requireTenantAdmin() {
        AdminPrincipal principal = require();
        if (principal.role() != AdminPrincipal.Role.TENANT_ADMIN) {
            throw new BusinessException("FORBIDDEN", "需要租户管理员权限");
        }
        return principal;
    }

    public static AdminPrincipal requirePlatformAdmin() {
        AdminPrincipal principal = require();
        if (!principal.isPlatformAdmin()) throw new BusinessException("FORBIDDEN", "需要平台管理员权限");
        return principal;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
