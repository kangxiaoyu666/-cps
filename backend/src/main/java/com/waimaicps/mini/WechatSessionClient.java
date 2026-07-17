package com.waimaicps.mini;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.waimaicps.common.BusinessException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class WechatSessionClient {
    private final RestTemplate rest;
    private final String appId;
    private final String appSecret;

    public WechatSessionClient(RestTemplateBuilder builder,
            @Value("${app.wechat.app-id:}") String appId,
            @Value("${app.wechat.app-secret:}") String appSecret) {
        this.rest = builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(8)).build();
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public WechatSession exchange(String code) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new BusinessException("WECHAT_NOT_CONFIGURED", "微信小程序凭据未配置");
        }
        try {
            WechatResponse response = rest.getForObject(
                    "https://api.weixin.qq.com/sns/jscode2session?appid={appId}&secret={secret}&js_code={code}&grant_type=authorization_code",
                    WechatResponse.class, appId, appSecret, code);
            if (response == null || response.openid == null || response.errorCode != null) {
                throw new BusinessException("WECHAT_LOGIN_FAILED", "微信登录失败");
            }
            return new WechatSession(response.openid, response.unionid, response.sessionKey);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException("WECHAT_UNAVAILABLE", "微信登录服务暂时不可用");
        }
    }

    public record WechatSession(String openid, String unionid, String sessionKey) {}
    private record WechatResponse(String openid, String unionid,
            @JsonProperty("session_key") String sessionKey,
            @JsonProperty("errcode") Integer errorCode) {}
}
