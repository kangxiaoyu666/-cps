package com.waimaicps.auth;

import com.waimaicps.common.BusinessException;

public final class TenantContext {
    private static final ThreadLocal<MiniPrincipal> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(MiniPrincipal principal) {
        CURRENT.set(principal);
    }

    public static MiniPrincipal require() {
        MiniPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new BusinessException("UNAUTHORIZED", "登录已失效");
        }
        return principal;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
