package com.waimaicps.user;

import com.waimaicps.common.BusinessException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {
    private static final int MAX_DEPTH = 1000;
    private final JdbcTemplate jdbc;

    public InvitationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void bindOnce(long tenantId, long userId, long inviterId) {
        if (userId == inviterId) {
            throw new BusinessException("SELF_INVITATION", "不能邀请自己");
        }
        UserLink user = loadForUpdate(tenantId, userId);
        loadForUpdate(tenantId, inviterId);
        if (user.inviterId != null) {
            if (user.inviterId == inviterId) return;
            throw new BusinessException("INVITATION_IMMUTABLE", "邀请关系已经建立，不允许修改");
        }
        ensureNoCycle(tenantId, userId, inviterId);
        int updated = jdbc.update("UPDATE wx_user SET direct_inviter_id=?,invited_at=UTC_TIMESTAMP(6),version=version+1 WHERE tenant_id=? AND id=? AND direct_inviter_id IS NULL AND version=?",
                inviterId, tenantId, userId, user.version);
        if (updated != 1) {
            throw new BusinessException("INVITATION_CONFLICT", "邀请关系已被其他请求建立");
        }
    }

    private void ensureNoCycle(long tenantId, long userId, long inviterId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = inviterId;
        int depth = 0;
        while (cursor != null) {
            if (cursor == userId || !visited.add(cursor)) {
                throw new BusinessException("INVITATION_CYCLE", "邀请关系不能形成环");
            }
            if (++depth > MAX_DEPTH) {
                throw new BusinessException("INVITATION_TOO_DEEP", "邀请链异常，拒绝绑定");
            }
            cursor = jdbc.query("SELECT direct_inviter_id FROM wx_user WHERE tenant_id=? AND id=?",
                    rs -> rs.next() ? nullableLong(rs.getObject(1)) : null, tenantId, cursor);
        }
    }

    private UserLink loadForUpdate(long tenantId, long userId) {
        return jdbc.query("SELECT direct_inviter_id,version FROM wx_user WHERE tenant_id=? AND id=? FOR UPDATE", rs -> {
            if (!rs.next()) throw new BusinessException("USER_NOT_FOUND", "用户不存在");
            return new UserLink(nullableLong(rs.getObject(1)), rs.getLong(2));
        }, tenantId, userId);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record UserLink(Long inviterId, long version) {}
}
