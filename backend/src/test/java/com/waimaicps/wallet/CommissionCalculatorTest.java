package com.waimaicps.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.waimaicps.common.BusinessException;
import org.junit.jupiter.api.Test;

class CommissionCalculatorTest {
    @Test
    void calculatesInCentsWithoutFloatingPoint() {
        assertEquals(1234L, CommissionCalculator.calculate(12345L, 1000));
        assertEquals(0L, CommissionCalculator.calculate(1L, 1));
    }

    @Test
    void rejectsRatesAboveTotalCommission() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> CommissionCalculator.validateRule(6000, 5000));
        assertEquals("INVALID_COMMISSION_RULE", error.code());
    }
}
