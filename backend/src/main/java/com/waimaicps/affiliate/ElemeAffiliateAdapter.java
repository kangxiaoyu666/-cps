package com.waimaicps.affiliate;

import com.fasterxml.jackson.databind.JsonNode;
import com.waimaicps.common.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("ELEME")
public class ElemeAffiliateAdapter implements AffiliateAdapter {
    public static final String LINK_PERMISSION = "OFFICIAL_ACTIVITY";
    public static final String ORDER_PERMISSION = "POSITIVE_ORDER";
    public static final String REFUND_PERMISSION = "REFUND_ORDER";
    private static final int PAGE_SIZE = 50;
    private final AffiliateConfigurationService configurations;
    private final AffiliateAttributionService attributions;
    private final TopApiClient top;

    public ElemeAffiliateAdapter(
            AffiliateConfigurationService configurations,
            AffiliateAttributionService attributions,
            TopApiClient top) {
        this.configurations = configurations;
        this.attributions = attributions;
        this.top = top;
    }

    @Override
    public PromotionLink generatePromotionLink(long tenantId, long userId, String activityCode) {
        AffiliateConfiguration config = configurations.requireActive(
                tenantId, AffiliatePlatform.ELEME, LINK_PERMISSION);
        String trackingId = trackingId(userId);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("pid", config.pid());
        query.put("activity_id", blank(activityCode) ? config.activityId() : activityCode);
        query.put("sid", trackingId);
        query.put("include_qr_code", false);
        query.put("include_image", false);
        query.put("include_watchword", false);
        JsonNode root = top.execute(TopApiClient.ACTIVITY_METHOD, config, Map.of("query_request", query));
        if (root.path("result_code").asInt(-1) != 0) {
            throw new BusinessException("AFFILIATE_OFFICIAL_ERROR", AffiliateRequestSupport.officialError(root));
        }
        JsonNode data = root.path("data");
        JsonNode link = data.path("link");
        String url = AffiliateRequestSupport.firstText(link, "h5_short_link", "h5_url", "ele_scheme_url");
        if (blank(url)) {
            url = AffiliateRequestSupport.firstText(link.path("h5_promotion"), "short_link", "h5_url");
        }
        if (blank(url)) {
            throw new BusinessException("AFFILIATE_RESPONSE_INVALID", "淘宝闪购官方成功响应未返回推广链接");
        }
        attributions.bind(tenantId, AffiliatePlatform.ELEME, trackingId, userId);
        Instant expiresAt = AffiliateRequestSupport.epochSeconds(data, "end_time");
        return new PromotionLink("ELEME", url, expiresAt);
    }

    @Override
    public OrderPage fetchOrderPage(long tenantId, Instant from, Instant to, String cursor) {
        AffiliateConfiguration config = configurations.requireActive(
                tenantId, AffiliatePlatform.ELEME, ORDER_PERMISSION);
        int page = parsePage(cursor);
        JsonNode positive = queryReport(TopApiClient.ORDER_METHOD, config, from, to, page);
        List<AffiliateOrderPayload> orders = new ArrayList<>();
        array(positive.path("result"), "order_detail_report_d_t_o").forEach(item -> orders.add(mapPositive(item)));
        long total = positive.path("total_count").asLong(orders.size());
        if (config.hasPermission(REFUND_PERMISSION)) {
            JsonNode refunds = queryReport(TopApiClient.REFUND_METHOD, config, from, to, page);
            array(refunds.path("result"), "refund_order_detail_report_d_t_o")
                    .forEach(item -> orders.add(mapRefund(item)));
            total = Math.max(total, refunds.path("total_count").asLong());
        }
        boolean hasMore = (long) page * PAGE_SIZE < total;
        return new OrderPage(List.copyOf(orders), hasMore ? Integer.toString(page + 1) : null, hasMore);
    }

    @Override
    public AffiliateOrderPayload queryOrder(long tenantId, String externalOrderId) {
        AffiliateConfiguration config = configurations.requireActive(
                tenantId, AffiliatePlatform.ELEME, ORDER_PERMISSION);
        Map<String, Object> params = reportParameters(
                config, Instant.now().minus(java.time.Duration.ofDays(180)), Instant.now(), 1);
        params.put("order_id", externalOrderId);
        JsonNode root = top.execute(TopApiClient.ORDER_METHOD, config, params);
        JsonNode items = array(root.path("result"), "order_detail_report_d_t_o");
        if (!items.isArray() || items.isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "淘宝闪购官方未返回该订单");
        }
        return mapPositive(items.get(0));
    }

    @Override
    public ValidationResult validateConfiguration(long tenantId) {
        try {
            AffiliateConfiguration config = configurations.requireActive(
                    tenantId, AffiliatePlatform.ELEME, LINK_PERMISSION);
            Map<String, Object> query = Map.of(
                    "pid", config.pid(), "activity_id", config.activityId(),
                    "sid", config.sid() == null ? "validation" : config.sid(),
                    "include_qr_code", false, "include_image", false, "include_watchword", false);
            JsonNode root = top.execute(TopApiClient.ACTIVITY_METHOD, config, Map.of("query_request", query));
            if (root.path("result_code").asInt(-1) != 0
                    || AffiliateRequestSupport.isEmptyObject(root.path("data").path("link"))) {
                throw new BusinessException("AFFILIATE_OFFICIAL_ERROR", AffiliateRequestSupport.officialError(root));
            }
            configurations.recordValidation(tenantId, AffiliatePlatform.ELEME, true, "官方活动接口验证通过");
            return new ValidationResult(true, "OK", "淘宝闪购官方接口验证通过");
        } catch (BusinessException ex) {
            configurations.recordValidation(tenantId, AffiliatePlatform.ELEME, false, ex.getMessage());
            return new ValidationResult(false, ex.code(), ex.getMessage());
        }
    }

    private JsonNode queryReport(
            String method,
            AffiliateConfiguration config,
            Instant from,
            Instant to,
            int page) {
        return top.execute(method, config, reportParameters(config, from, to, page));
    }

    private Map<String, Object> reportParameters(
            AffiliateConfiguration config,
            Instant from,
            Instant to,
            int page) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("date_type", 4);
        params.put("start_date", AffiliateRequestSupport.formatChina(from));
        params.put("end_date", AffiliateRequestSupport.formatChina(to));
        params.put("biz_unit", 2);
        params.put("page_size", PAGE_SIZE);
        params.put("page_number", page);
        params.put("pid", config.pid());
        params.put("order_channel", "taobao_shangou");
        return params;
    }

    private AffiliateOrderPayload mapPositive(JsonNode item) {
        int itemStatus = item.path("order_item_status").asInt(-1);
        int orderStatus = item.path("order_state").asInt(-1);
        int settlement = item.path("settle_state").asInt(-1);
        String status;
        if (itemStatus == 3 || itemStatus == 5 || orderStatus == 0) {
            status = "REFUNDED";
        } else if (settlement == 1) {
            status = "SETTLED";
        } else if (orderStatus == 4) {
            status = "COMPLETED";
        } else if (orderStatus == 2 || itemStatus == 2) {
            status = "PAID";
        } else {
            status = "DISCOVERED";
        }
        String attrType = item.path("attr_type").asText("0");
        String orderId = AffiliateRequestSupport.requiredText(item, "biz_order_id");
        long commission = settlement == 1
                ? AffiliateRequestSupport.cents(item, "settle")
                : AffiliateRequestSupport.cents(item, "income");
        long refunded = "REFUNDED".equals(status) ? commission : 0;
        return new AffiliateOrderPayload(
                attrType + ":" + orderId, status, AffiliateRequestSupport.cents(item, "pay_amount"),
                commission, refunded, AffiliateRequestSupport.chinaDateTime(item, "pay_time"),
                "REFUNDED".equals(status) ? AffiliateRequestSupport.chinaDateTime(item, "gmt_modified") : null,
                AffiliateRequestSupport.text(item, "sid"),
                AffiliateRequestSupport.snapshot(item, "biz_order_id", "attr_type", "parent_order_id",
                        "pay_amount", "income", "settle", "pay_time", "settle_time", "gmt_modified",
                        "order_state", "order_item_status", "settle_state", "sid", "pid", "activity_id"));
    }

    private AffiliateOrderPayload mapRefund(JsonNode item) {
        String attrType = item.path("attr_type").asText("0");
        String orderId = AffiliateRequestSupport.requiredText(item, "biz_order_id");
        boolean succeeded = item.path("explain_state").asInt(-1) == 0;
        return new AffiliateOrderPayload(
                attrType + ":" + orderId, succeeded ? "REFUNDED" : "REFUND_PENDING",
                AffiliateRequestSupport.cents(item, "pay_amount"),
                0,
                0,
                null, AffiliateRequestSupport.chinaDateTime(item,
                        succeeded ? "explain_end_time" : "explain_start_time"),
                AffiliateRequestSupport.text(item, "sid"),
                AffiliateRequestSupport.snapshot(item, "biz_order_id", "attr_type", "parent_order_id",
                        "pay_amount", "refund_amount", "settle", "explain_state", "return_commission_state",
                        "explain_start_time", "explain_end_time", "gmt_modified", "sid", "pid"));
    }

    private JsonNode array(JsonNode result, String field) {
        JsonNode node = result.path(field);
        if (node.isArray()) {
            return node;
        }
        if (node.isObject()) {
            return new com.fasterxml.jackson.databind.node.ArrayNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance).add(node);
        }
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    }

    private int parsePage(String cursor) {
        if (blank(cursor)) {
            return 1;
        }
        try {
            int page = Integer.parseInt(cursor);
            if (page < 1 || page > 50) {
                throw new NumberFormatException();
            }
            return page;
        } catch (NumberFormatException ex) {
            throw new BusinessException("AFFILIATE_CURSOR_INVALID", "淘宝闪购订单同步游标无效");
        }
    }

    private String trackingId(long userId) {
        return "u" + Long.toUnsignedString(userId, 36);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
