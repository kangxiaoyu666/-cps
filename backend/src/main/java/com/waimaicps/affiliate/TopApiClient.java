package com.waimaicps.affiliate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimaicps.common.BusinessException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TopApiClient {
    static final String ACTIVITY_METHOD = "alibaba.alsc.union.eleme.promotion.officialactivity.get";
    static final String ORDER_METHOD = "alibaba.alsc.union.kbcpx.positive.order.get";
    static final String REFUND_METHOD = "alibaba.alsc.union.kbcpx.refund.order.get";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.ofHours(8));
    private final URI endpoint;
    private final AffiliateHttpClient http;
    private final ObjectMapper objectMapper;
    private final TopSigner signer;
    private final Clock clock;

    @Autowired
    public TopApiClient(
            AffiliateHttpClient http,
            ObjectMapper objectMapper,
            @Value("${app.affiliate.eleme.endpoint:https://eco.taobao.com/router/rest}") URI endpoint) {
        this(http, objectMapper, new TopSigner(), Clock.systemUTC(), endpoint);
    }

    TopApiClient(
            AffiliateHttpClient http,
            ObjectMapper objectMapper,
            TopSigner signer,
            Clock clock,
            URI endpoint) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.clock = clock;
        this.endpoint = endpoint;
    }

    public JsonNode execute(String method, AffiliateConfiguration configuration, Map<String, ?> business) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("method", method);
        parameters.put("app_key", configuration.appKey());
        parameters.put("timestamp", TIMESTAMP.format(clock.instant()));
        parameters.put("v", "2.0");
        parameters.put("format", "json");
        parameters.put("simplify", "false");
        parameters.put("sign_method", "hmac-sha256");
        business.forEach((key, value) -> parameters.put(key, serialize(value)));
        parameters.put("sign", signer.sign(parameters, configuration.appSecret()));
        JsonNode response = http.postForm(endpoint, form(parameters));
        JsonNode gatewayError = response.get("error_response");
        if (gatewayError != null) {
            throw new BusinessException("AFFILIATE_OFFICIAL_ERROR", AffiliateRequestSupport.officialError(gatewayError));
        }
        String rootName = method.replace('.', '_') + "_response";
        JsonNode root = response.get(rootName);
        if (root == null) {
            throw new BusinessException("AFFILIATE_RESPONSE_INVALID", "淘宝开放平台响应结构无效");
        }
        if (root.has("result_success") && !root.path("result_success").asBoolean()) {
            throw new BusinessException("AFFILIATE_OFFICIAL_ERROR", AffiliateRequestSupport.officialError(root));
        }
        return root;
    }

    private String serialize(Object value) {
        if (value instanceof String string) {
            return string;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("AFFILIATE_REQUEST_INVALID", "联盟请求参数无法序列化");
        }
    }

    private String form(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
