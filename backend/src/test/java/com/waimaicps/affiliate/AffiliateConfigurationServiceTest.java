package com.waimaicps.affiliate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.common.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AffiliateConfigurationServiceTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void encryptsSecretBeforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0, 1);
        AffiliateConfigurationService service = new AffiliateConfigurationService(jdbc, new ObjectMapper(), KEY);
        AffiliateConfigurationService.ConfigurationWrite input =
                new AffiliateConfigurationService.ConfigurationWrite(
                        "美团", "public-key", "top-secret", "activity", "pid", "sid",
                        Set.of("GET_REFERRAL_LINK", "QUERY_ORDER"), true);

        assertThrows(RuntimeException.class, () -> service.save(1, AffiliatePlatform.MEITUAN, input));
        verify(jdbc).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO affiliate_channel"),
                eq(1L), eq("MEITUAN"), eq("美团"), any(byte[].class), eq("v1"), eq("ACTIVE"));
    }

    @Test
    void missingEncryptionKeyFailsExplicitly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AffiliateConfigurationService service = new AffiliateConfigurationService(jdbc, new ObjectMapper(), "");
        AffiliateConfigurationService.ConfigurationWrite input =
                new AffiliateConfigurationService.ConfigurationWrite(
                        "美团", "key", "secret", "activity", "pid", null,
                        Set.of("GET_REFERRAL_LINK"), true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(1, AffiliatePlatform.MEITUAN, input));
        assertEquals("DATA_ENCRYPTION_NOT_CONFIGURED", error.code());
    }

    @Test
    void viewTypeHasNoSecretAccessor() {
        Set<String> names = java.util.Arrays.stream(
                        AffiliateConfigurationService.ConfigurationView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(names.contains("secretConfigured"));
        assertFalse(names.contains("appSecret"));
        assertFalse(names.contains("encryptedConfig"));
    }
}
