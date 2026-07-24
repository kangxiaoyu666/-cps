package com.waimaicps.affiliate;

import com.waimaicps.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AffiliateAttributionService {
    private final JdbcTemplate jdbc;

    public AffiliateAttributionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void bind(long tenantId, AffiliatePlatform platform, String trackingId, long userId) {
        if (!exists("SELECT COUNT(*) FROM wx_user WHERE tenant_id=? AND id=?", tenantId, userId)) {
            throw new BusinessException("USER_NOT_FOUND", "归因用户不存在");
        }
        if (!exists(
                "SELECT COUNT(*) FROM affiliate_channel WHERE tenant_id=? AND platform=?",
                tenantId, platform.name())) {
            throw new BusinessException("AFFILIATE_NOT_CONFIGURED", "联盟渠道不存在");
        }
        jdbc.update(
                "INSERT INTO affiliate_attribution(tenant_id,channel_id,platform,tracking_id,user_id) "
                        + "SELECT tenant_id,id,platform,?,? FROM affiliate_channel "
                        + "WHERE tenant_id=? AND platform=? "
                        + "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",
                trackingId, userId, tenantId, platform.name());
    }

    private boolean exists(String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count != null && count == 1;
    }
}
