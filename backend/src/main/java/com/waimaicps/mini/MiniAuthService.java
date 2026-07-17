package com.waimaicps.mini;

import com.waimaicps.auth.MiniSessionService;
import com.waimaicps.common.BusinessException;
import com.waimaicps.crypto.FieldCryptoService;
import com.waimaicps.user.InvitationService;
import com.waimaicps.user.ShareSceneService;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniAuthService {
    private final JdbcTemplate jdbc;
    private final MiniSessionService sessions;
    private final WechatSessionClient wechat;
    private final FieldCryptoService crypto;
    private final InvitationService invitations;
    private final ShareSceneService shareScenes;
    private final boolean mockEnabled;
    private final SecureRandom random = new SecureRandom();

    public MiniAuthService(JdbcTemplate jdbc, MiniSessionService sessions, WechatSessionClient wechat,
            FieldCryptoService crypto, InvitationService invitations, ShareSceneService shareScenes,
            @Value("${app.integration.mock-enabled:false}") boolean mockEnabled) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.wechat = wechat;
        this.crypto = crypto;
        this.invitations = invitations;
        this.shareScenes = shareScenes;
        this.mockEnabled = mockEnabled;
    }

    @Transactional
    public LoginResult login(long tenantId, String code, String scene) {
        String openid;
        String unionid = null;
        if (mockEnabled) {
            if (!code.startsWith("dev-")) {
                throw new BusinessException("WECHAT_LOGIN_FAILED", "开发模式仅接受 dev-* 测试 code");
            }
            openid = "mock:" + code;
        } else {
            WechatSessionClient.WechatSession result = wechat.exchange(code);
            openid = result.openid();
            unionid = result.unionid();
        }
        byte[] openidHash = crypto.hash(openid);
        Long userId = findUser(tenantId, openidHash);
        if (userId == null) {
            userId = createUser(tenantId, openid, unionid, openidHash);
        }
        Long inviterId = shareScenes.consume(tenantId, scene);
        if (inviterId != null) invitations.bindOnce(tenantId, userId, inviterId);
        String token = sessions.issue(tenantId, userId);
        return new LoginResult(token, 604800);
    }

    private Long findUser(long tenantId, byte[] openidHash) {
        return jdbc.query("SELECT id FROM wx_user WHERE tenant_id=? AND openid_hash=? AND status='ACTIVE'",
                rs -> rs.next() ? rs.getLong(1) : null, tenantId, openidHash);
    }

    private long createUser(long tenantId, String openid, String unionid, byte[] openidHash) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                jdbc.update("INSERT INTO wx_user(tenant_id,openid_ciphertext,openid_hash,unionid_ciphertext,invite_code,status) VALUES(?,?,?,?,?,'ACTIVE')",
                        tenantId, crypto.encrypt(openid), openidHash,
                        unionid == null ? null : crypto.encrypt(unionid), inviteCode());
                Long id = findUser(tenantId, openidHash);
                if (id != null) return id;
            } catch (DuplicateKeyException ex) {
                Long existing = findUser(tenantId, openidHash);
                if (existing != null) return existing;
            }
        }
        throw new BusinessException("USER_CREATE_FAILED", "用户创建失败");
    }

    private String inviteCode() {
        byte[] bytes = new byte[9];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase();
    }

    public record LoginResult(String token, long expiresIn) {}
}
