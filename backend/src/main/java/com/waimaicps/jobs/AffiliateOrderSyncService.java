package com.waimaicps.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.affiliate.AffiliateAdapter;
import com.waimaicps.affiliate.AffiliatePlatform;
import com.waimaicps.common.BusinessException;
import com.waimaicps.wallet.CommissionReversalService;
import com.waimaicps.wallet.CommissionSettlementService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AffiliateOrderSyncService {
    private static final int MAX_PAGES_PER_RUN = 100;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AffiliateAdapter meituan;
    private final AffiliateAdapter eleme;
    private final CommissionSettlementService settlement;
    private final CommissionReversalService reversal;
    private final TransactionTemplate transactions;

    public AffiliateOrderSyncService(
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Qualifier("MEITUAN") AffiliateAdapter meituan,
            @Qualifier("ELEME") AffiliateAdapter eleme,
            CommissionSettlementService settlement,
            CommissionReversalService reversal,
            TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.meituan = meituan;
        this.eleme = eleme;
        this.settlement = settlement;
        this.reversal = reversal;
        this.transactions = transactions;
    }

    public SyncResult sync(long tenantId, String platform, Instant requestedFrom, Instant requestedTo) {
        AffiliatePlatform normalized = AffiliatePlatform.parse(platform);
        validateWindow(requestedFrom, requestedTo);
        String lockKey = "affiliate-sync:" + tenantId + ":" + normalized.name();
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofMinutes(10));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("JOB_ALREADY_RUNNING", "同租户同平台订单同步任务正在执行");
        }
        long jobId = 0;
        int scanned = 0;
        int succeeded = 0;
        try {
            jobId = startJob(tenantId, normalized.name(), normalized.name() + ":" + UUID.randomUUID());
            SyncCursor state = loadOrCreateCursor(tenantId, normalized, requestedFrom, requestedTo);
            AffiliateAdapter adapter = adapter(normalized);
            for (int pageNumber = 0; pageNumber < MAX_PAGES_PER_RUN; pageNumber++) {
                AffiliateAdapter.OrderPage page = adapter.fetchOrderPage(
                        tenantId, state.windowStart, state.windowEnd, state.cursor);
                scanned += page.orders().size();
                for (AffiliateAdapter.AffiliateOrderPayload order : page.orders()) {
                    persistAndProcess(tenantId, normalized, order);
                    succeeded++;
                }
                if (!page.hasMore()) {
                    completeCursor(tenantId, normalized, state.windowEnd);
                    finishJob(tenantId, jobId, "SUCCESS", scanned, succeeded, null, null);
                    return new SyncResult(jobId, scanned, succeeded, false);
                }
                if (page.nextCursor() == null || page.nextCursor().equals(state.cursor)) {
                    throw new BusinessException("AFFILIATE_CURSOR_STALLED", "联盟官方分页游标未推进");
                }
                saveCursor(tenantId, normalized, page.nextCursor(), state.windowStart, state.windowEnd);
                state = new SyncCursor(page.nextCursor(), state.windowStart, state.windowEnd);
            }
            finishJob(tenantId, jobId, "PARTIAL", scanned, succeeded, null, "达到单次最大分页数，将从持久化游标继续");
            return new SyncResult(jobId, scanned, succeeded, true);
        } catch (RuntimeException ex) {
            if (jobId != 0) {
                finishJob(tenantId, jobId, "FAILED", scanned, succeeded, errorCode(ex), safeMessage(ex));
            }
            throw ex;
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    public SyncResult syncOrder(long tenantId, String platform, String externalOrderId) {
        AffiliatePlatform normalized = AffiliatePlatform.parse(platform);
        AffiliateAdapter.AffiliateOrderPayload payload = adapter(normalized).queryOrder(tenantId, externalOrderId);
        long jobId = startJob(
                tenantId, normalized.name(), normalized.name() + ":ORDER:" + externalOrderId + ":" + UUID.randomUUID());
        try {
            persistAndProcess(tenantId, normalized, payload);
            finishJob(tenantId, jobId, "SUCCESS", 1, 1, null, null);
            return new SyncResult(jobId, 1, 1, false);
        } catch (RuntimeException ex) {
            finishJob(tenantId, jobId, "FAILED", 1, 0, errorCode(ex), safeMessage(ex));
            throw ex;
        }
    }

    public Instant suggestedStart(long tenantId, AffiliatePlatform platform, Instant now) {
        Instant lastSuccess = jdbc.query(
                "SELECT last_success_at FROM affiliate_sync_cursor WHERE tenant_id=? AND platform=?",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null,
                tenantId, platform.name());
        Instant defaultStart = now.minus(Duration.ofHours(1));
        if (lastSuccess == null) {
            return defaultStart;
        }
        Instant overlap = lastSuccess.minus(Duration.ofMinutes(5));
        return overlap.isBefore(defaultStart) ? defaultStart : overlap;
    }

    private void persistAndProcess(
            long tenantId,
            AffiliatePlatform platform,
            AffiliateAdapter.AffiliateOrderPayload payload) {
        Long orderId = transactions.execute(status -> upsert(tenantId, platform, payload));
        if (orderId == null) {
            throw new BusinessException("AFFILIATE_ORDER_WRITE_FAILED", "联盟订单写入失败");
        }
        OrderState current = jdbc.query(
                "SELECT status,attributed_user_id IS NOT NULL FROM affiliate_order WHERE tenant_id=? AND id=?",
                rs -> {
                    if (!rs.next()) {
                        throw new BusinessException("ORDER_NOT_FOUND", "联盟订单写入后未找到");
                    }
                    return new OrderState(rs.getString(1), rs.getBoolean(2));
                }, tenantId, orderId);
        if ("REFUNDED".equals(current.status)) {
            reversal.reverseIfRefunded(tenantId, orderId);
        } else if ("SETTLED".equals(current.status) && current.attributed) {
            settlement.settle(tenantId, orderId);
        }
    }

    private long upsert(
            long tenantId,
            AffiliatePlatform platform,
            AffiliateAdapter.AffiliateOrderPayload payload) {
        Channel channel = jdbc.query(
                "SELECT id FROM affiliate_channel WHERE tenant_id=? AND platform=? AND status='ACTIVE'",
                rs -> rs.next() ? new Channel(rs.getLong(1)) : null, tenantId, platform.name());
        if (channel == null) {
            throw new BusinessException("AFFILIATE_NOT_CONFIGURED", "联盟渠道未启用");
        }
        Long userId = attributedUser(tenantId, platform, payload.trackingId());
        String snapshot = maskedJson(payload.maskedSnapshot());
        int updated = jdbc.update(
                "UPDATE affiliate_order SET status=CASE WHEN status='REFUNDED' THEN status ELSE ? END,"
                        + "order_amount_cent=GREATEST(order_amount_cent,?),"
                        + "settled_commission_cent=GREATEST(settled_commission_cent,?),"
                        + "refunded_commission_cent=GREATEST(refunded_commission_cent,?),"
                        + "paid_at=COALESCE(?,paid_at),"
                        + "refunded_at=COALESCE(?,refunded_at),attributed_user_id=COALESCE(?,attributed_user_id),"
                        + "raw_snapshot_json=CAST(? AS JSON),updated_at=UTC_TIMESTAMP(6),version=version+1 "
                        + "WHERE tenant_id=? AND platform=? AND external_order_id=?",
                payload.status(), payload.amountCent(), payload.commissionCent(),
                payload.refundedCommissionCent(), timestamp(payload.paidAt()), timestamp(payload.refundedAt()),
                userId, snapshot, tenantId, platform.name(), payload.externalOrderId());
        if (updated == 0) {
            Rule rule = activeRule(tenantId);
            jdbc.update(
                    "INSERT INTO affiliate_order(tenant_id,platform,external_order_id,channel_id,"
                            + "attributed_user_id,status,order_amount_cent,settled_commission_cent,"
                            + "refunded_commission_cent,paid_at,refunded_at,raw_snapshot_json,"
                            + "rule_self_rate_bps,rule_direct_rate_bps) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?)",
                    tenantId, platform.name(), payload.externalOrderId(), channel.id, userId,
                    payload.status(), payload.amountCent(), payload.commissionCent(),
                    payload.refundedCommissionCent(), timestamp(payload.paidAt()),
                    timestamp(payload.refundedAt()), snapshot, rule.selfRate, rule.directRate);
        }
        return jdbc.queryForObject(
                "SELECT id FROM affiliate_order WHERE tenant_id=? AND platform=? AND external_order_id=?",
                Long.class, tenantId, platform.name(), payload.externalOrderId());
    }

    private Long attributedUser(long tenantId, AffiliatePlatform platform, String trackingId) {
        if (trackingId == null || trackingId.isBlank()) {
            return null;
        }
        return jdbc.query(
                "SELECT user_id FROM affiliate_attribution WHERE tenant_id=? AND platform=? AND tracking_id=?",
                rs -> rs.next() ? rs.getLong(1) : null, tenantId, platform.name(), trackingId);
    }

    private SyncCursor loadOrCreateCursor(
            long tenantId,
            AffiliatePlatform platform,
            Instant requestedFrom,
            Instant requestedTo) {
        SyncCursor existing = jdbc.query(
                "SELECT cursor_value,window_start,window_end FROM affiliate_sync_cursor "
                        + "WHERE tenant_id=? AND platform=?",
                rs -> rs.next() ? new SyncCursor(
                        rs.getString(1), toInstant(rs.getTimestamp(2)), toInstant(rs.getTimestamp(3))) : null,
                tenantId, platform.name());
        if (existing == null) {
            jdbc.update("INSERT INTO affiliate_sync_cursor(tenant_id,platform,window_start,window_end) "
                            + "VALUES(?,?,?,?)",
                    tenantId, platform.name(), timestamp(requestedFrom), timestamp(requestedTo));
            return new SyncCursor(null, requestedFrom, requestedTo);
        }
        if (existing.cursor != null && existing.windowStart != null && existing.windowEnd != null) {
            return existing;
        }
        jdbc.update("UPDATE affiliate_sync_cursor SET cursor_value=NULL,window_start=?,window_end=?,"
                        + "version=version+1 WHERE tenant_id=? AND platform=?",
                timestamp(requestedFrom), timestamp(requestedTo), tenantId, platform.name());
        return new SyncCursor(null, requestedFrom, requestedTo);
    }

    private void saveCursor(
            long tenantId,
            AffiliatePlatform platform,
            String cursor,
            Instant windowStart,
            Instant windowEnd) {
        jdbc.update("UPDATE affiliate_sync_cursor SET cursor_value=?,window_start=?,window_end=?,"
                        + "version=version+1 WHERE tenant_id=? AND platform=?",
                cursor, timestamp(windowStart), timestamp(windowEnd), tenantId, platform.name());
    }

    private void completeCursor(long tenantId, AffiliatePlatform platform, Instant completedAt) {
        jdbc.update("UPDATE affiliate_sync_cursor SET cursor_value=NULL,window_start=NULL,window_end=NULL,"
                        + "last_success_at=?,version=version+1 WHERE tenant_id=? AND platform=?",
                timestamp(completedAt), tenantId, platform.name());
    }

    private AffiliateAdapter adapter(AffiliatePlatform platform) {
        return switch (platform) {
            case MEITUAN -> meituan;
            case ELEME -> eleme;
        };
    }

    private Rule activeRule(long tenantId) {
        return jdbc.query(
                "SELECT self_rate_bps,direct_invite_rate_bps FROM commission_rule WHERE tenant_id=? "
                        + "AND status='ACTIVE' AND effective_from<=UTC_TIMESTAMP(6) "
                        + "AND (effective_to IS NULL OR effective_to>UTC_TIMESTAMP(6)) "
                        + "ORDER BY effective_from DESC LIMIT 1",
                rs -> rs.next() ? new Rule(rs.getInt(1), rs.getInt(2)) : new Rule(0, 0), tenantId);
    }

    private long startJob(long tenantId, String platform, String executionKey) {
        jdbc.update("INSERT INTO job_execution(tenant_id,platform,job_type,execution_key,status,started_at) "
                        + "VALUES(?,?, 'ORDER_SYNC',?,'RUNNING',UTC_TIMESTAMP(6))",
                tenantId, platform, executionKey);
        return jdbc.queryForObject(
                "SELECT id FROM job_execution WHERE tenant_id=? AND execution_key=?",
                Long.class, tenantId, executionKey);
    }

    private void finishJob(
            long tenantId,
            long jobId,
            String status,
            int scanned,
            int success,
            String code,
            String message) {
        int updated = jdbc.update(
                "UPDATE job_execution SET status=?,finished_at=UTC_TIMESTAMP(6),scanned_count=?,"
                        + "success_count=?,failure_count=?,error_code=?,error_message=? "
                        + "WHERE tenant_id=? AND id=?",
                status, scanned, success, Math.max(scanned - success, 0), code, message, tenantId, jobId);
        if (updated != 1) {
            throw new BusinessException("JOB_NOT_FOUND", "订单同步任务不存在");
        }
    }

    private void releaseLock(String key, String expected) {
        byte[] script = "if redis.call('get',KEYS[1])==ARGV[1] then "
                .concat("return redis.call('del',KEYS[1]) else return 0 end")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] rawKey = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] rawExpected = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        redis.execute((RedisCallback<Long>) connection -> connection.scriptingCommands().eval(
                script,
                org.springframework.data.redis.connection.ReturnType.INTEGER,
                1,
                rawKey,
                rawExpected));
    }

    private String maskedJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot == null ? Map.of() : snapshot);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("AFFILIATE_RESPONSE_INVALID", "联盟响应快照无法解析");
        }
    }

    private void validateWindow(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).toDays() > 31) {
            throw new BusinessException("AFFILIATE_SYNC_WINDOW_INVALID", "订单同步时间窗必须有效且不超过31天");
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String errorCode(RuntimeException ex) {
        return ex instanceof BusinessException business ? business.code() : "AFFILIATE_SYNC_FAILED";
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null ? "订单同步失败" : message.substring(0, Math.min(message.length(), 900));
    }

    public record SyncResult(long jobId, int scannedCount, int successCount, boolean continuationRequired) {
    }

    private record SyncCursor(String cursor, Instant windowStart, Instant windowEnd) {
    }

    private record Channel(long id) {
    }

    private record OrderState(String status, boolean attributed) {
    }

    private record Rule(int selfRate, int directRate) {
    }
}
