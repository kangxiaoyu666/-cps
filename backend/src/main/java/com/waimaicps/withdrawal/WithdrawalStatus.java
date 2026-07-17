package com.waimaicps.withdrawal;

import com.waimaicps.common.BusinessException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum WithdrawalStatus {
    SUBMITTED, APPROVED, PAID, REJECTED, CANCELED;

    private static final Map<WithdrawalStatus, Set<WithdrawalStatus>> ALLOWED = Map.of(
            SUBMITTED, EnumSet.of(APPROVED, REJECTED, CANCELED),
            APPROVED, EnumSet.of(PAID),
            PAID, EnumSet.noneOf(WithdrawalStatus.class),
            REJECTED, EnumSet.noneOf(WithdrawalStatus.class),
            CANCELED, EnumSet.noneOf(WithdrawalStatus.class));

    public void requireTransitionTo(WithdrawalStatus target) {
        if (!ALLOWED.get(this).contains(target)) {
            throw new BusinessException("INVALID_WITHDRAWAL_TRANSITION", "不允许从 " + this + " 变更为 " + target);
        }
    }
}
