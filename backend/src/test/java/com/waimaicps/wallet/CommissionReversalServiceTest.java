package com.waimaicps.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class CommissionReversalServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void reversesCreditedCommissionAndAllowsDebt() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WalletLedgerService ledger = mock(WalletLedgerService.class);
        ResultSet orderRs = mock(ResultSet.class);
        when(orderRs.next()).thenReturn(true);
        when(orderRs.getLong("id")).thenReturn(9L);
        when(orderRs.getString("external_order_id")).thenReturn("order-9");
        when(orderRs.getString("status")).thenReturn("REFUNDED");
        when(orderRs.getLong("refunded_commission_cent")).thenReturn(300L);
        when(orderRs.getTimestamp("refund_processed_at")).thenReturn(null);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(1L), eq(9L)))
                .thenAnswer(invocation -> ((ResultSetExtractor<?>) invocation.getArgument(1)).extractData(orderRs));
        ResultSet commissionRs = mock(ResultSet.class);
        when(commissionRs.getLong("id")).thenReturn(11L);
        when(commissionRs.getLong("beneficiary_user_id")).thenReturn(7L);
        when(commissionRs.getString("reward_type")).thenReturn("SELF_PURCHASE");
        when(commissionRs.getInt("rate_bps_snapshot")).thenReturn(5000);
        when(commissionRs.getLong("amount_cent")).thenReturn(300L);
        when(commissionRs.getString("business_no")).thenReturn("order-9:SELF_PURCHASE:7");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L), eq(9L)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1))
                        .mapRow(commissionRs, 0)));

        new CommissionReversalService(jdbc, ledger).reverseIfRefunded(1, 9);

        verify(ledger).applyReversal(1, 7, "order-9:SELF_PURCHASE:7:REVERSAL",
                "commission-reversal:11", 300, 9L);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("refund_processed_at"), eq(1L), eq(9L));
    }

    @SuppressWarnings("unchecked")
    @Test
    void alreadyProcessedRefundIsIdempotent() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WalletLedgerService ledger = mock(WalletLedgerService.class);
        ResultSet orderRs = mock(ResultSet.class);
        when(orderRs.next()).thenReturn(true);
        when(orderRs.getLong("id")).thenReturn(9L);
        when(orderRs.getString("external_order_id")).thenReturn("order-9");
        when(orderRs.getString("status")).thenReturn("REFUNDED");
        when(orderRs.getLong("refunded_commission_cent")).thenReturn(300L);
        when(orderRs.getTimestamp("refund_processed_at")).thenReturn(Timestamp.from(Instant.now()));
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(1L), eq(9L)))
                .thenAnswer(invocation -> ((ResultSetExtractor<?>) invocation.getArgument(1)).extractData(orderRs));

        new CommissionReversalService(jdbc, ledger).reverseIfRefunded(1, 9);

        verify(ledger, never()).applyReversal(any(Long.class), any(Long.class), anyString(),
                anyString(), any(Long.class), any());
    }
}
