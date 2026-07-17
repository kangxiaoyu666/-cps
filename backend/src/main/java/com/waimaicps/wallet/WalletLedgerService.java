package com.waimaicps.wallet;

import com.waimaicps.common.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletLedgerService {
    private final JdbcTemplate jdbc;

    public WalletLedgerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void apply(
            long tenantId,
            long userId,
            String businessType,
            String businessNo,
            String idempotencyKey,
            long availableDelta,
            long frozenDelta,
            Long orderId,
            Long withdrawalId,
            String memo) {
        applyInternal(tenantId, userId, businessType, businessNo, idempotencyKey, availableDelta,
                frozenDelta, orderId, withdrawalId, memo, false, Math.max(availableDelta, 0));
    }

    @Transactional
    public void applyReversal(
            long tenantId,
            long userId,
            String businessNo,
            String idempotencyKey,
            long amountCent,
            Long orderId) {
        if (amountCent <= 0) {
            throw new BusinessException("REVERSAL_AMOUNT_INVALID", "冲正金额必须大于零");
        }
        applyInternal(tenantId, userId, "COMMISSION_REVERSAL", businessNo, idempotencyKey,
                -amountCent, 0, orderId, null, "退款佣金冲正", true, -amountCent);
    }

    private void applyInternal(
            long tenantId,
            long userId,
            String businessType,
            String businessNo,
            String idempotencyKey,
            long requestedAvailableDelta,
            long frozenDelta,
            Long orderId,
            Long withdrawalId,
            String memo,
            boolean reversal,
            long lifetimeDelta) {
        Wallet wallet = loadOrCreate(tenantId, userId);
        BalanceChange change = calculateBalanceChange(
                wallet.available, wallet.debt, businessType, requestedAvailableDelta, reversal);
        long availableAfter = wallet.available + change.availableDelta;
        long frozenAfter = wallet.frozen + frozenDelta;
        long debtAfter = wallet.debt + change.debtDelta;
        if (availableAfter < 0 || frozenAfter < 0 || debtAfter < 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "余额不足");
        }
        try {
            jdbc.update(
                    "INSERT INTO wallet_entry(tenant_id,wallet_account_id,user_id,business_type,"
                            + "business_no,direction,available_delta_cent,frozen_delta_cent,debt_delta_cent,"
                            + "available_before_cent,available_after_cent,frozen_before_cent,frozen_after_cent,"
                            + "debt_before_cent,debt_after_cent,related_order_id,related_withdrawal_id,"
                            + "idempotency_key,memo) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenantId, wallet.id, userId, businessType, businessNo,
                    change.availableDelta + frozenDelta + change.debtDelta >= 0 ? "CREDIT" : "DEBIT",
                    change.availableDelta, frozenDelta, change.debtDelta,
                    wallet.available, availableAfter, wallet.frozen, frozenAfter, wallet.debt, debtAfter,
                    orderId, withdrawalId, idempotencyKey, memo);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("DUPLICATE_OPERATION", "该资金操作已处理");
        }
        int updated = jdbc.update(
                "UPDATE wallet_account SET available_cent=?,frozen_cent=?,debt_cent=?,"
                        + "lifetime_income_cent=GREATEST(lifetime_income_cent+?,0),version=version+1 "
                        + "WHERE id=? AND tenant_id=?",
                availableAfter, frozenAfter, debtAfter, lifetimeDelta, wallet.id, tenantId);
        if (updated != 1) {
            throw new BusinessException("WALLET_CONFLICT", "钱包更新冲突");
        }
    }

    public Map<String, Long> summary(long tenantId, long userId) {
        return jdbc.query(
                "SELECT available_cent,frozen_cent,debt_cent,lifetime_income_cent "
                        + "FROM wallet_account WHERE tenant_id=? AND user_id=?",
                rs -> rs.next()
                        ? Map.of(
                                "availableCent", rs.getLong(1),
                                "frozenCent", rs.getLong(2),
                                "debtCent", rs.getLong(3),
                                "lifetimeIncomeCent", rs.getLong(4))
                        : Map.of(
                                "availableCent", 0L,
                                "frozenCent", 0L,
                                "debtCent", 0L,
                                "lifetimeIncomeCent", 0L),
                tenantId, userId);
    }

    private Wallet loadOrCreate(long tenantId, long userId) {
        Wallet wallet = jdbc.query(
                "SELECT id,available_cent,frozen_cent,debt_cent FROM wallet_account "
                        + "WHERE tenant_id=? AND user_id=? FOR UPDATE",
                rs -> rs.next() ? map(rs) : null, tenantId, userId);
        if (wallet != null) {
            return wallet;
        }
        jdbc.update("INSERT INTO wallet_account(tenant_id,user_id) VALUES(?,?)", tenantId, userId);
        return jdbc.query(
                "SELECT id,available_cent,frozen_cent,debt_cent FROM wallet_account "
                        + "WHERE tenant_id=? AND user_id=? FOR UPDATE",
                rs -> {
                    rs.next();
                    return map(rs);
                }, tenantId, userId);
    }

    static BalanceChange calculateBalanceChange(
            long availableCent,
            long debtCent,
            String businessType,
            long requestedAvailableDelta,
            boolean reversal) {
        if (reversal) {
            long amount = Math.negateExact(requestedAvailableDelta);
            long fromAvailable = Math.min(availableCent, amount);
            return new BalanceChange(-fromAvailable, amount - fromAvailable);
        }
        if ("COMMISSION".equals(businessType) && requestedAvailableDelta > 0 && debtCent > 0) {
            long repayment = Math.min(debtCent, requestedAvailableDelta);
            return new BalanceChange(requestedAvailableDelta - repayment, -repayment);
        }
        return new BalanceChange(requestedAvailableDelta, 0);
    }

    private Wallet map(ResultSet rs) throws SQLException {
        return new Wallet(
                rs.getLong("id"),
                rs.getLong("available_cent"),
                rs.getLong("frozen_cent"),
                rs.getLong("debt_cent"));
    }

    private record Wallet(long id, long available, long frozen, long debt) {
    }

    record BalanceChange(long availableDelta, long debtDelta) {
    }
}
