package com.waimaicps.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.affiliate.AffiliateAdapter;
import com.waimaicps.affiliate.AffiliatePlatform;
import com.waimaicps.wallet.CommissionReversalService;
import com.waimaicps.wallet.CommissionSettlementService;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class AffiliateOrderSyncServiceTest {
    @Test
    void suggestedStartUsesOverlapWithoutGoingBeyondOneHour() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq(5L), eq("MEITUAN"))).thenAnswer(invocation -> {
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.next()).thenReturn(true);
                    when(rs.getTimestamp(1)).thenReturn(Timestamp.from(now.minusSeconds(600)));
                    return ((org.springframework.jdbc.core.ResultSetExtractor<?>) invocation.getArgument(1))
                            .extractData(rs);
                });
        AffiliateOrderSyncService service = service(jdbc);

        assertEquals(now.minusSeconds(900), service.suggestedStart(5, AffiliatePlatform.MEITUAN, now));
    }

    private AffiliateOrderSyncService service(JdbcTemplate jdbc) {
        return new AffiliateOrderSyncService(
                jdbc, mock(StringRedisTemplate.class), new ObjectMapper(),
                mock(AffiliateAdapter.class), mock(AffiliateAdapter.class),
                mock(CommissionSettlementService.class), mock(CommissionReversalService.class),
                mock(TransactionTemplate.class));
    }
}
