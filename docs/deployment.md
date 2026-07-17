# 部署手册

生产使用 Java 21、MySQL 8、Redis 7、HTTPS 反向代理。先备份，再执行 Flyway，再滚动发布后端，最后发布管理端和小程序。生产配置仅从环境变量/KMS 注入。`INTEGRATION_MOCK_ENABLED` 与管理端 `VITE_USE_MOCK` 必须为 false。健康检查为 `/actuator/health`。发布后检查数据库版本、Redis、登录、租户隔离、任务锁和告警。

## 首次初始化

Flyway 只创建结构，不提交默认账号或明文密码。首次部署应使用离线 BCrypt 工具生成强密码哈希（cost 12），在受控数据库会话中创建 `tenant` 与 `tenant_admin`；平台账号同理写入 `platform_admin`。完成后立即删除初始化 SQL/终端历史中的敏感信息，并验证登录、CSRF、租户边界和审计日志。开发环境如需纯 UI 预览，可临时将管理端 `VITE_USE_MOCK=true`，但此模式不验证后端认证且禁止用于测试/生产。
