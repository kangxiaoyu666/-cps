# 开发规范

- Java 使用分层包：`controller/application/domain/repository/integration`，Controller 不返回实体。
- 金额统一用 `BIGINT` 分；时间统一存 UTC，展示层转换时区。
- 所有租户业务查询必须包含来自认证身份的 `tenant_id`。
- 创建资金类资源必须使用 `Idempotency-Key`；钱包流水只能追加，不允许更新或删除。
- TypeScript 开启 strict；页面必须覆盖 loading、empty、error 和正常状态。
- 禁止提交密钥、真实用户数据、真实联盟响应样本。
- 变更数据库只新增 Flyway migration，不修改已发布 migration。
