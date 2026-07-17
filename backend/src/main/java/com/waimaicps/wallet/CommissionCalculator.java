package com.waimaicps.wallet;

import com.waimaicps.common.BusinessException;

public final class CommissionCalculator {
    private CommissionCalculator() {}

    public static long calculate(long settledCommissionCent, int rateBps) {
        if (settledCommissionCent < 0 || rateBps < 0 || rateBps > 10000) {
            throw new BusinessException("INVALID_COMMISSION_INPUT", "佣金或比例不合法");
        }
        return Math.multiplyExact(settledCommissionCent, rateBps) / 10000;
    }

    public static void validateRule(int selfRateBps, int directRateBps) {
        if (selfRateBps < 0 || directRateBps < 0 || selfRateBps + directRateBps > 10000) {
            throw new BusinessException("INVALID_COMMISSION_RULE", "自购与一级奖励比例之和不能超过 100% ");
        }
    }
}
