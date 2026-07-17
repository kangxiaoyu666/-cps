package com.waimaicps.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.waimaicps.common.BusinessException;
import org.junit.jupiter.api.Test;

class AdminPrincipalTest {
    @Test
    void tenantIdentitySuppliesTenantBoundary() {
        AdminPrincipal principal = new AdminPrincipal(7L, 42L, AdminPrincipal.Role.TENANT_ADMIN, "operator");
        assertEquals(42L, principal.requireTenantId());
    }

    @Test
    void platformIdentityCannotMasqueradeAsTenant() {
        AdminPrincipal principal = new AdminPrincipal(1L, null, AdminPrincipal.Role.PLATFORM_ADMIN, "platform");
        assertThrows(BusinessException.class, principal::requireTenantId);
    }
}
