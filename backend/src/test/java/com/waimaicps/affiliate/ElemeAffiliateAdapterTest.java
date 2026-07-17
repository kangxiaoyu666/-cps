package com.waimaicps.affiliate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.common.BusinessException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ElemeAffiliateAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesOfficialActivityResponse() throws Exception {
        AffiliateConfigurationService configurations = mock(AffiliateConfigurationService.class);
        AffiliateAttributionService attributions = mock(AffiliateAttributionService.class);
        TopApiClient top = mock(TopApiClient.class);
        when(configurations.requireActive(2, AffiliatePlatform.ELEME, ElemeAffiliateAdapter.LINK_PERMISSION))
                .thenReturn(configuration());
        when(top.execute(eq(TopApiClient.ACTIVITY_METHOD), any(), any(Map.class)))
                .thenReturn(mapper.readTree("{\"result_code\":0,\"message\":\"success\",\"data\":{"
                        + "\"end_time\":1800000000,\"link\":{\"h5_url\":\"https://official.test/eleme\"}}}"));
        ElemeAffiliateAdapter adapter = new ElemeAffiliateAdapter(configurations, attributions, top);

        AffiliateAdapter.PromotionLink link = adapter.generatePromotionLink(2, 71, "");

        assertEquals("https://official.test/eleme", link.url());
        verify(attributions).bind(2, AffiliatePlatform.ELEME, "u1z", 71);
    }

    @Test
    void missingOfficialLinkFails() throws Exception {
        AffiliateConfigurationService configurations = mock(AffiliateConfigurationService.class);
        TopApiClient top = mock(TopApiClient.class);
        when(configurations.requireActive(2, AffiliatePlatform.ELEME, ElemeAffiliateAdapter.LINK_PERMISSION))
                .thenReturn(configuration());
        when(top.execute(eq(TopApiClient.ACTIVITY_METHOD), any(), any(Map.class)))
                .thenReturn(mapper.readTree("{\"result_code\":0,\"message\":\"success\","
                        + "\"data\":{\"link\":{}}}"));
        ElemeAffiliateAdapter adapter = new ElemeAffiliateAdapter(
                configurations, mock(AffiliateAttributionService.class), top);

        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter.generatePromotionLink(2, 71, ""));

        assertEquals("AFFILIATE_RESPONSE_INVALID", error.code());
    }

    private AffiliateConfiguration configuration() {
        return new AffiliateConfiguration(
                "app-key", "secret", "activity", "pid", "sid",
                Set.of(ElemeAffiliateAdapter.LINK_PERMISSION, ElemeAffiliateAdapter.ORDER_PERMISSION,
                        ElemeAffiliateAdapter.REFUND_PERMISSION));
    }
}
