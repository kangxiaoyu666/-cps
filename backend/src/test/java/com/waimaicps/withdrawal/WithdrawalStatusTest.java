package com.waimaicps.withdrawal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.waimaicps.common.BusinessException;
import org.junit.jupiter.api.Test;

class WithdrawalStatusTest {
    @Test void allowsDefinedTransitions() {
        assertDoesNotThrow(() -> WithdrawalStatus.SUBMITTED.requireTransitionTo(WithdrawalStatus.APPROVED));
        assertDoesNotThrow(() -> WithdrawalStatus.SUBMITTED.requireTransitionTo(WithdrawalStatus.REJECTED));
        assertDoesNotThrow(() -> WithdrawalStatus.SUBMITTED.requireTransitionTo(WithdrawalStatus.CANCELED));
        assertDoesNotThrow(() -> WithdrawalStatus.APPROVED.requireTransitionTo(WithdrawalStatus.PAID));
    }

    @Test void blocksDuplicatePaymentAndReapproval() {
        assertThrows(BusinessException.class, () -> WithdrawalStatus.PAID.requireTransitionTo(WithdrawalStatus.PAID));
        assertThrows(BusinessException.class, () -> WithdrawalStatus.PAID.requireTransitionTo(WithdrawalStatus.APPROVED));
        assertThrows(BusinessException.class, () -> WithdrawalStatus.APPROVED.requireTransitionTo(WithdrawalStatus.REJECTED));
    }
}
