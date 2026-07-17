package com.waimaicps.withdrawal;

import com.waimaicps.common.BusinessException;
import com.waimaicps.wallet.WalletLedgerService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {
    private static final long MIN_AMOUNT_CENT = 1000;
    private final JdbcTemplate jdbc;
    private final WalletLedgerService ledger;

    public WithdrawalService(JdbcTemplate jdbc, WalletLedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Transactional
    public Map<String, Object> submit(long tenantId, long userId, long amountCent, String idempotencyKey) {
        if (amountCent < MIN_AMOUNT_CENT) {
            throw new BusinessException("WITHDRAWAL_BELOW_MINIMUM", "最低提现金额为 10 元");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BusinessException("IDEMPOTENCY_KEY_INVALID", "提现幂等键无效");
        }
        Map<String, Object> existing = findByIdempotency(tenantId, userId, idempotencyKey);
        if (existing != null) {
            return existing;
        }
        long debtCent = jdbc.query(
                "SELECT debt_cent FROM wallet_account WHERE tenant_id=? AND user_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L, tenantId, userId);
        if (debtCent > 0) {
            throw new BusinessException("WITHDRAWAL_BLOCKED_BY_DEBT", "存在待偿还欠款，暂不可提现");
        }
        String no = "WD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        try {
            jdbc.update("INSERT INTO withdrawal(tenant_id,user_id,withdrawal_no,amount_cent,status,submitted_at,idempotency_key) VALUES(?,?,?,?,?,?,?)",
                    tenantId, userId, no, amountCent, WithdrawalStatus.SUBMITTED.name(), Timestamp.from(Instant.now()), idempotencyKey);
        } catch (DuplicateKeyException ex) {
            Map<String, Object> duplicate = findByIdempotency(tenantId, userId, idempotencyKey);
            if (duplicate != null) return duplicate;
            throw ex;
        }
        Long id = jdbc.queryForObject("SELECT id FROM withdrawal WHERE tenant_id=? AND user_id=? AND idempotency_key=?", Long.class, tenantId, userId, idempotencyKey);
        ledger.apply(tenantId, userId, "WITHDRAWAL_FREEZE", no, "freeze:" + idempotencyKey, -amountCent, amountCent, null, id, "提交提现并冻结余额");
        audit(tenantId, id, "USER", userId, "SUBMIT", null, WithdrawalStatus.SUBMITTED, null);
        return Map.of("id", id, "withdrawalNo", no, "status", WithdrawalStatus.SUBMITTED.name(), "amountCent", amountCent);
    }

    @Transactional
    public void cancel(long tenantId, long userId, long withdrawalId) {
        Withdrawal row = loadForUpdate(tenantId, withdrawalId);
        if (row.userId != userId) throw new BusinessException("FORBIDDEN", "无权操作该提现");
        row.status.requireTransitionTo(WithdrawalStatus.CANCELED);
        updateStatus(tenantId, withdrawalId, row, WithdrawalStatus.CANCELED, "canceled_at=UTC_TIMESTAMP(6)");
        ledger.apply(tenantId, userId, "WITHDRAWAL_RELEASE", row.no, "cancel:" + row.no, row.amount, -row.amount, null, withdrawalId, "取消提现并释放冻结余额");
        audit(tenantId, withdrawalId, "USER", userId, "CANCEL", row.status, WithdrawalStatus.CANCELED, null);
    }

    @Transactional
    public void approve(long tenantId, long adminId, long withdrawalId) {
        transitionWithoutWallet(tenantId, adminId, withdrawalId, WithdrawalStatus.APPROVED, "approved_at=UTC_TIMESTAMP(6),reviewed_by=" + adminId);
    }

    @Transactional
    public void markPaid(long tenantId, long adminId, long withdrawalId, String channel, String reference, String proofUrl) {
        Withdrawal row = loadForUpdate(tenantId, withdrawalId);
        row.status.requireTransitionTo(WithdrawalStatus.PAID);
        int updated = jdbc.update("UPDATE withdrawal SET status='PAID',paid_at=UTC_TIMESTAMP(6),payout_channel=?,payout_reference=?,proof_url=?,reviewed_by=?,version=version+1 WHERE tenant_id=? AND id=? AND status='APPROVED' AND version=?",
                channel, reference, proofUrl, adminId, tenantId, withdrawalId, row.version);
        if (updated != 1) throw new BusinessException("WITHDRAWAL_CONFLICT", "提现状态已变化");
        ledger.apply(tenantId, row.userId, "WITHDRAWAL_PAID", row.no, "paid:" + row.no, 0, -row.amount, null, withdrawalId, "线下付款完成");
        audit(tenantId, withdrawalId, "TENANT_ADMIN", adminId, "MARK_PAID", row.status, WithdrawalStatus.PAID, reference);
    }

    @Transactional
    public void reject(long tenantId, long adminId, long withdrawalId, String reason) {
        Withdrawal row = loadForUpdate(tenantId, withdrawalId);
        row.status.requireTransitionTo(WithdrawalStatus.REJECTED);
        int updated = jdbc.update("UPDATE withdrawal SET status='REJECTED',rejected_at=UTC_TIMESTAMP(6),rejection_reason=?,reviewed_by=?,version=version+1 WHERE tenant_id=? AND id=? AND status='SUBMITTED' AND version=?",
                reason, adminId, tenantId, withdrawalId, row.version);
        if (updated != 1) throw new BusinessException("WITHDRAWAL_CONFLICT", "提现状态已变化");
        ledger.apply(tenantId, row.userId, "WITHDRAWAL_RELEASE", row.no, "reject:" + row.no, row.amount, -row.amount, null, withdrawalId, "拒绝提现并释放冻结余额");
        audit(tenantId, withdrawalId, "TENANT_ADMIN", adminId, "REJECT", row.status, WithdrawalStatus.REJECTED, reason);
    }

    private Map<String, Object> findByIdempotency(long tenantId, long userId, String key) {
        return jdbc.query("SELECT id,withdrawal_no,status,amount_cent FROM withdrawal WHERE tenant_id=? AND user_id=? AND idempotency_key=?",
                rs -> rs.next() ? Map.of("id", rs.getLong(1), "withdrawalNo", rs.getString(2),
                        "status", rs.getString(3), "amountCent", rs.getLong(4)) : null,
                tenantId, userId, key);
    }

    private void transitionWithoutWallet(long tenantId, long adminId, long id, WithdrawalStatus target, String extraSet) {
        Withdrawal row = loadForUpdate(tenantId, id);
        row.status.requireTransitionTo(target);
        updateStatus(tenantId, id, row, target, extraSet);
        audit(tenantId, id, "TENANT_ADMIN", adminId, target.name(), row.status, target, null);
    }

    private Withdrawal loadForUpdate(long tenantId, long id) {
        return jdbc.query("SELECT user_id,withdrawal_no,amount_cent,status,version FROM withdrawal WHERE tenant_id=? AND id=? FOR UPDATE", rs -> {
            if (!rs.next()) throw new BusinessException("WITHDRAWAL_NOT_FOUND", "提现记录不存在");
            return new Withdrawal(rs.getLong(1), rs.getString(2), rs.getLong(3), WithdrawalStatus.valueOf(rs.getString(4)), rs.getLong(5));
        }, tenantId, id);
    }

    private void updateStatus(long tenantId, long id, Withdrawal row, WithdrawalStatus target, String extraSet) {
        int updated = jdbc.update("UPDATE withdrawal SET status=?," + extraSet + ",version=version+1 WHERE tenant_id=? AND id=? AND status=? AND version=?",
                target.name(), tenantId, id, row.status.name(), row.version);
        if (updated != 1) throw new BusinessException("WITHDRAWAL_CONFLICT", "提现状态已变化");
    }

    private void audit(long tenantId, long id, String actorType, long actorId, String action, WithdrawalStatus from, WithdrawalStatus to, String detail) {
        jdbc.update("INSERT INTO withdrawal_audit(tenant_id,withdrawal_id,actor_type,actor_id,action,from_status,to_status,detail_json) VALUES(?,?,?,?,?,?,?,JSON_OBJECT('detail',?))",
                tenantId, id, actorType, actorId, action, from == null ? null : from.name(), to.name(), detail);
    }

    private record Withdrawal(long userId, String no, long amount, WithdrawalStatus status, long version) {}
}
