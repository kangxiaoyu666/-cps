package com.waimaicps.affiliate;

import com.waimaicps.common.BusinessException;
import java.util.Locale;

public enum AffiliatePlatform {
    MEITUAN,
    ELEME;

    public static AffiliatePlatform parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("UNSUPPORTED_PLATFORM", "不支持的联盟平台");
        }
    }
}
