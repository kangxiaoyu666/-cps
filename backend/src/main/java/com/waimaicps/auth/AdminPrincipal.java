package com.waimaicps.auth;

public record AdminPrincipal(long adminId, Long tenantId, Role role, String displayName) {
    public enum Role { TENANT_ADMIN, PLATFORM_ADMIN }

    public boolean isPlatformAdmin() {
        return role == Role.PLATFORM_ADMIN;
    }

    public long requireTenantId() {
        if (tenantId == null) {
            throw new com.waimaicps.common.BusinessException("TENANT_CONTEXT_REQUIRED", "该操作需要租户上下文");
        }
        return tenantId;
    }
}
