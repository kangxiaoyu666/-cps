# 环境变量清单

| 变量 | 必填 | 敏感 | 说明 |
|---|---|---|---|
| DB_URL/DB_USERNAME/DB_PASSWORD | 是 | 密码敏感 | MySQL 连接 | 
| REDIS_HOST/REDIS_PORT | 是 | 否 | Redis 地址 |
| SESSION_TOKEN_PEPPER | 是 | 是 | 会话令牌散列 pepper，至少 32 字节 |
| WECHAT_APP_ID/WECHAT_APP_SECRET | 生产是 | Secret 敏感 | 微信小程序登录 |
| DATA_ENCRYPTION_KEY | 生产是 | 是 | openid、unionid 和用户敏感字段 AES-GCM 主密钥，Base64 编码 32 字节 |
| AFFILIATE_SECRET_KEY | 生产是 | 是 | 联盟配置信封加密主密钥 |
| DEFAULT_TENANT_CODE | 是 | 否 | 小程序默认租户编码 |
| COOKIE_SECURE | 生产是 | 否 | 生产必须 true |
| INTEGRATION_MOCK_ENABLED | 是 | 否 | 生产必须 false |
| NVD_API_KEY | CI 推荐 | 是 | 后端 OWASP 依赖漏洞扫描使用；未配置时首次同步会非常慢 |
| SERVER_PORT | 否 | 否 | 默认 8080 |

密钥不得进入 `.env` 以外的仓库文件；生产优先使用 KMS/Secret Manager 注入。
