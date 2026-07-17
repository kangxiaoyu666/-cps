package com.waimaicps.affiliate;

import com.fasterxml.jackson.databind.JsonNode;
import com.waimaicps.common.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

final class AffiliateRequestSupport {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AffiliateRequestSupport() {
    }

    static long cents(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return 0;
        }
        try {
            return new BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new BusinessException("AFFILIATE_RESPONSE_INVALID", "联盟金额字段格式错误: " + field);
        }
    }

    static Instant epochSeconds(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : Instant.ofEpochSecond(value.asLong());
    }

    static Instant chinaDateTime(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME).toInstant(ZoneOffset.ofHours(8));
        } catch (RuntimeException ex) {
            throw new BusinessException("AFFILIATE_RESPONSE_INVALID", "联盟时间字段格式错误: " + field);
        }
    }

    static String formatChina(Instant instant) {
        return DATE_TIME.withZone(ZoneOffset.ofHours(8)).format(instant);
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new BusinessException("AFFILIATE_RESPONSE_INVALID", "联盟响应缺少字段: " + field);
        }
        return value;
    }

    static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static Map<String, Object> snapshot(JsonNode node, String... fields) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    result.put(field, value.numberValue());
                } else if (value.isBoolean()) {
                    result.put(field, value.booleanValue());
                } else {
                    result.put(field, value.asText());
                }
            }
        }
        return Map.copyOf(result);
    }

    static String officialError(JsonNode error) {
        if (error == null) {
            return "联盟官方接口返回失败";
        }
        String message = firstText(error, "sub_msg", "message", "msg", "biz_error_desc");
        return message == null ? "联盟官方接口返回失败" : message;
    }

    static boolean isEmptyObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;
        }
        Iterator<JsonNode> values = node.elements();
        return !values.hasNext();
    }
}
