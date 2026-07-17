package com.waimaicps.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WalletDebtPolicyTest {
    @Test
    void reversalConsumesAvailableAndCreatesDebtForShortfall() {
        WalletLedgerService.BalanceChange change = WalletLedgerService.calculateBalanceChange(
                300, 0, "COMMISSION_REVERSAL", -1000, true);

        assertEquals(-300, change.availableDelta());
        assertEquals(700, change.debtDelta());
    }

    @Test
    void laterCommissionRepaysDebtBeforeBecomingAvailable() {
        WalletLedgerService.BalanceChange partial = WalletLedgerService.calculateBalanceChange(
                0, 700, "COMMISSION", 500, false);
        WalletLedgerService.BalanceChange full = WalletLedgerService.calculateBalanceChange(
                0, 700, "COMMISSION", 1000, false);

        assertEquals(0, partial.availableDelta());
        assertEquals(-500, partial.debtDelta());
        assertEquals(300, full.availableDelta());
        assertEquals(-700, full.debtDelta());
    }

    @Test
    void ordinaryDebitDoesNotCreateDebt() {
        WalletLedgerService.BalanceChange change = WalletLedgerService.calculateBalanceChange(
                1000, 0, "WITHDRAWAL_FREEZE", -500, false);

        assertEquals(-500, change.availableDelta());
        assertEquals(0, change.debtDelta());
    }
}
