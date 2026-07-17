package com.waimaicps.user;

import com.waimaicps.common.BusinessException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShareSceneService {
    private static final Duration TTL = Duration.ofHours(24);
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public ShareSceneService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String create(long tenantId, long inviterId) {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        String scene = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(scene), tenantId + ":" + inviterId, TTL);
        return scene;
    }

    public Long consume(long tenantId, String scene) {
        if (scene == null || scene.isBlank()) return null;
        String value = redis.opsForValue().getAndDelete(key(scene));
        if (value == null) throw new BusinessException("SHARE_SCENE_EXPIRED", "邀请场景参数已过期");
        String[] parts = value.split(":", 2);
        if (parts.length != 2 || Long.parseLong(parts[0]) != tenantId) {
            throw new BusinessException("SHARE_SCENE_INVALID", "邀请场景参数无效");
        }
        return Long.parseLong(parts[1]);
    }

    private String key(String scene) {
        return "share-scene:" + scene;
    }
}
