package com.waimaicps.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.waimaicps.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InvitationGuardTest {
    @Test
    void selfInvitationIsRejectedBeforeDatabaseAccess() {
        InvitationService service = new InvitationService(new JdbcTemplate());
        BusinessException error = assertThrows(BusinessException.class, () -> service.bindOnce(1L, 9L, 9L));
        assertEquals("SELF_INVITATION", error.code());
    }
}
