package com.waimaicps.wallet;

import com.waimaicps.common.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommissionSettlementService {
    private final JdbcTemplate jdbc;
    private final WalletLedgerService ledger;

    public CommissionSettlementService(JdbcTemplate jdbc, WalletLedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Transactional
    public void settle(long tenantId, long orderId) {
        Order order = jdbc.query("SELECT id,external_order_id,attributed_user_id,status,settled_commission_cent,rule_self_rate_bps,rule_direct_rate_bps,commission_processed_at FROM affiliate_order WHERE tenant_id=? AND id=? FOR UPDATE",
                rs -> rs.next() ? map(rs) : null, tenantId, orderId);
        if (order == null) throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        if (!"SETTLED".equals(order.status)) throw new BusinessException("ORDER_NOT_SETTLED", "联盟订单尚未结算");
        if (order.processed) return;
        if (order.userId == null) throw new BusinessException("ORDER_NOT_ATTRIBUTED", "订单未归因用户");

        List<Reward> rewards = new ArrayList<>();
        CommissionCalculator.validateRule(order.selfRate, order.directRate);
        long self = CommissionCalculator.calculate(order.commission, order.selfRate);
        if (self > 0) rewards.add(new Reward(order.userId, "SELF_PURCHASE", self, order.selfRate));
        Long inviter = jdbc.query("SELECT direct_inviter_id FROM wx_user WHERE tenant_id=? AND id=?",
                rs -> rs.next() ? nullableLong(rs.getObject(1)) : null, tenantId, order.userId);
        long direct = CommissionCalculator.calculate(order.commission, order.directRate);
        if (inviter != null && direct > 0) rewards.add(new Reward(inviter, "DIRECT_INVITE", direct, order.directRate));

        for (Reward reward : rewards) {
            String businessNo = order.externalNo + ":" + reward.type + ":" + reward.userId;
            jdbc.update("INSERT INTO commission_record(tenant_id,order_id,beneficiary_user_id,reward_type,rate_bps_snapshot,amount_cent,status,business_no) VALUES(?,?,?,?,?,?,'CREDITED',?)",
                    tenantId, orderId, reward.userId, reward.type, reward.rate, reward.amount, businessNo);
            ledger.apply(tenantId, reward.userId, "COMMISSION", businessNo, "commission:" + businessNo,
                    reward.amount, 0, orderId, null, "联盟结算佣金");
        }
        int updated = jdbc.update("UPDATE affiliate_order SET commission_processed_at=UTC_TIMESTAMP(6),version=version+1 WHERE tenant_id=? AND id=? AND commission_processed_at IS NULL",
                tenantId, orderId);
        if (updated != 1) throw new BusinessException("SETTLEMENT_CONFLICT", "订单已被其他任务结算");
    }

    private Order map(ResultSet rs) throws SQLException {
        return new Order(rs.getLong("id"), rs.getString("external_order_id"),
                nullableLong(rs.getObject("attributed_user_id")), rs.getString("status"),
                rs.getLong("settled_commission_cent"), rs.getInt("rule_self_rate_bps"),
                rs.getInt("rule_direct_rate_bps"), rs.getTimestamp("commission_processed_at") != null);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record Order(long id, String externalNo, Long userId, String status, long commission, int selfRate,
            int directRate, boolean processed) {}

    private record Reward(long userId, String type, long amount, int rate) {}
}
