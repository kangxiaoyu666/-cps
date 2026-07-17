package com.waimaicps.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.auth.AdminContext;
import com.waimaicps.auth.AdminPrincipal;
import com.waimaicps.common.BusinessException;
import com.waimaicps.jobs.AffiliateOrderSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminOperationsControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminOperationsController controller = new AdminOperationsController(
            jdbc,
            new ObjectMapper(),
            mock(PasswordEncoder.class),
            mock(AffiliateOrderSyncService.class));

    @AfterEach
    void clearAdminContext() {
        AdminContext.clear();
    }

    @Test
    void rejectsPidBindingToUserFromAnotherTenant() {
        AdminContext.set(new AdminPrincipal(7L, 1L, AdminPrincipal.Role.TENANT_ADMIN, "admin"));
        when(jdbc.queryForObject(
                "SELECT COUNT(*) FROM wx_user WHERE tenant_id=? AND id=?",
                Integer.class,
                1L,
                99L)).thenReturn(0);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> controller.bindPid(
                        10L,
                        new AdminOperationsController.PidBinding(99L),
                        mock(HttpServletRequest.class)));

        assertEquals("USER_NOT_FOUND", error.code());
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }
}
