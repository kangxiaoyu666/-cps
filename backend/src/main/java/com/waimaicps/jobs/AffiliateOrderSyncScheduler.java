package com.waimaicps.jobs;

import com.waimaicps.affiliate.AffiliatePlatform;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AffiliateOrderSyncScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AffiliateOrderSyncScheduler.class);
    private final JdbcTemplate jdbc;
    private final AffiliateOrderSyncService sync;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public AffiliateOrderSyncScheduler(
            JdbcTemplate jdbc,
            AffiliateOrderSyncService sync,
            @Value("${app.affiliate.sync.enabled:true}") boolean enabled) {
        this(jdbc, sync, Clock.systemUTC(), enabled);
    }

    AffiliateOrderSyncScheduler(
            JdbcTemplate jdbc,
            AffiliateOrderSyncService sync,
            Clock clock,
            boolean enabled) {
        this.jdbc = jdbc;
        this.sync = sync;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.affiliate.sync.fixed-delay-ms:300000}")
    public void synchronize() {
        if (!enabled) {
            return;
        }
        List<Channel> channels = jdbc.query(
                "SELECT tenant_id,platform FROM affiliate_channel "
                        + "WHERE status='ACTIVE' AND encrypted_config IS NOT NULL",
                (rs, rowNum) -> new Channel(rs.getLong(1), AffiliatePlatform.parse(rs.getString(2))));
        Instant now = clock.instant();
        for (Channel channel : channels) {
            try {
                Instant from = sync.suggestedStart(channel.tenantId, channel.platform, now);
                sync.sync(channel.tenantId, channel.platform.name(), from, now);
            } catch (RuntimeException ex) {
                LOGGER.error("联盟订单调度失败 tenantId={} platform={} code={}",
                        channel.tenantId, channel.platform, ex.getClass().getSimpleName());
            }
        }
    }

    private record Channel(long tenantId, AffiliatePlatform platform) {
    }
}
