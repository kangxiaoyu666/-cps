package com.waimaicps.affiliate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.common.BusinessException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("MEITUAN")
public class MeituanAffiliateAdapter implements AffiliateAdapter {
    public static final String LINK_PERMISSION = "GET_REFERRAL_LINK";
    public static final String ORDER_PERMISSION = "QUERY_ORDER";
    private final AffiliateConfigurationService configurations;
    private final AffiliateHttpClient http;
    private final ObjectMapper objectMapper;
    private final MeituanSigner signer;
    private final AffiliateAttributionService attributions;
    private final Clock clock;
    private final URI linkEndpoint;
    private final URI orderEndpoint;

    @Autowired
    public MeituanAffiliateAdapter(
            AffiliateConfigurationService configurations,
            AffiliateHttpClient http,
            ObjectMapper objectMapper,
            AffiliateAttributionService attributions,
            @Value("${app.affiliate.meituan.link-endpoint:"
                    + "https://media.meituan.com/cps_open/common/api/v1/get_referral_link}") URI linkEndpoint,
            @Value("${app.affiliate.meituan.order-endpoint:"
                    + "https://media.meituan.com/cps_open/common/api/v1/query_order}") URI orderEndpoint) {
        this(configurations, http, objectMapper, new MeituanSigner(), attributions, Clock.systemUTC(),
                linkEndpoint, orderEndpoint);
    }

    MeituanAffiliateAdapter(
            AffiliateConfigurationService configurations,
            AffiliateHttpClient http,
            ObjectMapper objectMapper,
            MeituanSigner signer,
            AffiliateAttributionService attributions,
            Clock clock,
            URI linkEndpoint,
            URI orderEndpoint) {
        this.configurations = configurations;
        this.http = http;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.attributions = attributions;
        this.clock = clock;
        this.linkEndpoint = linkEndpoint;
        this.orderEndpoint = orderEndpoint;
    }

    @Override
    public PromotionLink generatePromotionLink(long tenantId, long userId, String activityCode) {
        AffiliateConfiguration config = configurations.requireActive(
                tenantId, AffiliatePlatform.MEITUAN, LINK_PERMISSION);
        String trackingId = trackingId(userId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("actId", blank(activityCode) ? config.activityId() : activityCode);
        request.put("sid", trackingId);
        request.put("linkType", 1);
        JsonNode response = post(linkEndpoint, config, request);
        if (response.path("code").asInt(-1) != 0) {
            throw officialError(response);
        }
        String link = AffiliateRequestSupport.firstText(response, "data");
        if (blank(link)) {
            throw new BusinessException("AFFILIATE_RESPONSE_INVALID", "美团官方成功响应未返回推广链接");
        }
        attributions.bind(tenantId, AffiliatePlatform.MEITUAN, trackingId, userId);
        return new PromotionLink("MEITUAN", link, null);
    }

    @Override
    public OrderPage fetchOrderPage(
            long tenantId,
            Instant from,
            Instant to,
            String cursor) {
        AffiliateConfiguration config = configurations.requireActive(
                tenantId, AffiliatePlatform.MEITUAN, ORDER_PERMISSION);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("platform", 1);
        request.put("businessLine", List.of(1));
        request.put("startTime", from.getEpochSecond());
        request.put("endTime", to.getEpochSecond());
        request.put("limit", 100);
        request.put("queryTimeType", 2);
        request.put("searchType", 2);
        request.put("page", 1);
        if (!blank(cursor)) {
            request.put("scrollId", cursor);
        }
        JsonNode response = post(orderEndpoint, config, request);
        if (response.path("code").asInt(-1) != 0) {
            throw officialError(response);
        }
        JsonNode data = response.path("data");
        List<AffiliateOrderPayload> orders = new ArrayList<>();
        JsonNode dataList = data.path("dataList");
        if (dataList.isArray()) {
            dataList.forEach(item -> orders.add(mapOrder(item)));
        }
        String nextCursor = AffiliateRequestSupport.text(data, "scrollId");
        boolean hasMore = orders.size() == 100 && !blank(nextCursor) && !nextCursor.equals(cursor);
        return new OrderPage(List.copyOf(orders), nextCursor, hasMore);
    }

    @Override
    public AffiliateOrderPayload queryOrder(long tenantId, String externalOrderId) {
        AffiliateConfiguration config = configurations.requireActive(
                tenantId, AffiliatePlatform.MEITUAN, ORDER_PERMISSION);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("platform", 1);
        request.put("businessLine", List.of(1));
        request.put("orderId", externalOrderId);
        request.put("limit", 100);
        request.put("page", 1);
        JsonNode response = post(orderEndpoint, config, request);
        if (response.path("code").asInt(-1) != 0) {
            throw officialError(response);
        }
        JsonNode items = response.path("data").path("dataList");
        if (!items.isArray() || items.isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "美团官方未返回该订单");
        }
        return mapOrder(items.get(0));
    }

    @Override
    public ValidationResult validateConfiguration(long tenantId) {
        try {
            AffiliateConfiguration config = configurations.requireActive(
                    tenantId, AffiliatePlatform.MEITUAN, LINK_PERMISSION);
            Map<String, Object> request = Map.of(
                    "actId", config.activityId(), "sid", config.sid() == null ? "validation" : config.sid(),
                    "linkType", 1);
            JsonNode response = post(linkEndpoint, config, request);
            if (response.path("code").asInt(-1) != 0) {
                throw officialError(response);
            }
            if (blank(AffiliateRequestSupport.text(response, "data"))) {
                throw new BusinessException("AFFILIATE_RESPONSE_INVALID", "美团官方未返回推广链接");
            }
            configurations.recordValidation(tenantId, AffiliatePlatform.MEITUAN, true, "官方推广链接接口验证通过");
            return new ValidationResult(true, "OK", "美团官方接口验证通过");
        } catch (BusinessException ex) {
            configurations.recordValidation(tenantId, AffiliatePlatform.MEITUAN, false, ex.getMessage());
            return new ValidationResult(false, ex.code(), ex.getMessage());
        }
    }

    private AffiliateOrderPayload mapOrder(JsonNode item) {
        String status = switch (item.path("status").asText()) {
            case "2" -> "PAID";
            case "3" -> "COMPLETED";
            case "6" -> "SETTLED";
            case "4", "5" -> "REFUNDED";
            default -> "DISCOVERED";
        };
        long refund = AffiliateRequestSupport.cents(item, "refundProfit");
        if (refund > 0) {
            status = "REFUNDED";
        }
        return new AffiliateOrderPayload(
                AffiliateRequestSupport.requiredText(item, "orderId"), status,
                AffiliateRequestSupport.cents(item, "payPrice"),
                AffiliateRequestSupport.cents(item, "profit"), refund,
                AffiliateRequestSupport.epochSeconds(item, "payTime"),
                AffiliateRequestSupport.epochSeconds(item, "refundTime"),
                AffiliateRequestSupport.text(item, "sid"),
                AffiliateRequestSupport.snapshot(item, "businessLine", "orderId", "payTime", "updateTime",
                        "payPrice", "profit", "refundProfit", "refundTime", "status", "sid", "actId"));
    }

    private JsonNode post(URI endpoint, AffiliateConfiguration config, Map<String, Object> request) {
        String body;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("AFFILIATE_REQUEST_INVALID", "美团请求参数无法序列化");
        }
        return http.postJson(endpoint,
                signer.sign(config.appKey(), config.appSecret(), endpoint, "POST", body, clock.millis()), body);
    }

    private BusinessException officialError(JsonNode response) {
        return new BusinessException("AFFILIATE_OFFICIAL_ERROR", AffiliateRequestSupport.officialError(response));
    }

    private String trackingId(long userId) {
        return "u" + Long.toUnsignedString(userId, 36);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
