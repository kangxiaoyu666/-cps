package com.waimaicps.wallet;

import com.waimaicps.common.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommissionReversalService {
    private final JdbcTemplate jdbc;
    private final WalletLedgerService ledger;

    public CommissionReversalService(JdbcTemplate jdbc, WalletLedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Transactional
    public void reverseIfRefunded(long tenantId, long orderId) {
        Order order = jdbc.query(
                "SELECT id,external_order_id,status,refunded_commission_cent,refund_processed_at "
                        + "FROM affiliate_order WHERE tenant_id=? AND id=? FOR UPDATE",
                rs -> rs.next() ? mapOrder(rs) : null, tenantId, orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!"REFUNDED".equals(order.status) || order.processed) {
            return;
        }
        List<Commission> commissions = jdbc.query(
                "SELECT id,beneficiary_user_id,reward_type,rate_bps_snapshot,amount_cent,business_no "
                        + "FROM commission_record WHERE tenant_id=? AND order_id=? AND status='CREDITED' FOR UPDATE",
                (rs, rowNum) -> mapCommission(rs), tenantId, orderId);
        long credited = commissions.stream().mapToLong(Commission::amount).sum();
        if (credited == 0) {
            markProcessed(tenantId, orderId);
            return;
        }
        long refundCommission = order.refundedCommission > 0 ? order.refundedCommission : credited;
        long remaining = Math.min(refundCommission, credited);
        for (int index = 0; index < commissions.size(); index++) {
            Commission commission = commissions.get(index);
            long amount = index == commissions.size() - 1
                    ? remaining
                    : Math.min(remaining, proportional(commission.amount, refundCommission, credited));
            if (amount <= 0) {
                continue;
            }
            remaining -= amount;
            String businessNo = commission.businessNo + ":REVERSAL";
            jdbc.update(
                    "INSERT INTO commission_record(tenant_id,order_id,beneficiary_user_id,reward_type,"
                            + "rate_bps_snapshot,amount_cent,status,reversal_of_id,business_no) "
                            + "VALUES(?,?,?,?,?,?,'REVERSED',?,?)",
                    tenantId, orderId, commission.userId, commission.rewardType + "_REVERSAL",
                    commission.rate, -amount, commission.id, businessNo);
            ledger.applyReversal(tenantId, commission.userId, businessNo,
                    "commission-reversal:" + commission.id, amount, orderId);
            int updated = jdbc.update(
                    "UPDATE commission_record SET status='REVERSED' WHERE tenant_id=? AND id=? AND status='CREDITED'",
                    tenantId, commission.id);
            if (updated != 1) {
                throw new BusinessException("REVERSAL_CONFLICT", "佣金记录已被其他任务冲正");
            }
        }
        markProcessed(tenantId, orderId);
    }

    private long proportional(long amount, long refund, long credited) {
        return java.math.BigDecimal.valueOf(amount)
                .multiply(java.math.BigDecimal.valueOf(refund))
                .divide(java.math.BigDecimal.valueOf(credited), 0, java.math.RoundingMode.DOWN)
                .longValue();
    }

    private void markProcessed(long tenantId, long orderId) {
        jdbc.update("UPDATE affiliate_order SET refund_processed_at=UTC_TIMESTAMP(6),version=version+1 "
                + "WHERE tenant_id=? AND id=? AND refund_processed_at IS NULL", tenantId, orderId);
    }

    private Order mapOrder(ResultSet rs) throws SQLException {
        return new Order(rs.getLong("id"), rs.getString("external_order_id"), rs.getString("status"),
                rs.getLong("refunded_commission_cent"), rs.getTimestamp("refund_processed_at") != null);
    }

    private Commission mapCommission(ResultSet rs) throws SQLException {
        return new Commission(rs.getLong("id"), rs.getLong("beneficiary_user_id"),
                rs.getString("reward_type"), rs.getInt("rate_bps_snapshot"),
                rs.getLong("amount_cent"), rs.getString("business_no"));
    }

    private record Order(long id, String externalNo, String status, long refundedCommission, boolean processed) {
    }

    private record Commission(long id, long userId, String rewardType, int rate, long amount, String businessNo) {
    }
}
