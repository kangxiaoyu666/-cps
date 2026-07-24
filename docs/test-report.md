# 测试报告

## 当前定位

项目已从“安全重写工程基线”推进到“本地全链路可运营演示工程”。本报告记录的是本机 Docker、真实 MySQL/Redis、Spring Boot API 与本地联盟 Mock 的实际验收结果；通过本地闭环不等同于已经满足生产上线条件。

## 已执行结果（2026-07-17）

### 后端与数据库门禁

- Docker Engine 29.3.0 已实际启动。
- MySQL 8.4、Redis 7.4 和 Affiliate Mock 三个开发容器均处于 healthy 状态。
- Flyway V1-V5 已在开发 MySQL 8.4 和两个独立 Testcontainers MySQL 8.4 实例中成功迁移到 v5。
- Java 21 执行 `mvn clean test checkstyle:check package`：BUILD SUCCESS。
- 共执行 26 项测试：0 失败、0 错误、0 跳过；Checkstyle 0 违规。
- MySQL Testcontainers 资金测试与完整 Spring 上下文测试均实际执行，没有因 Docker 不可用而跳过。
- 已生成可执行文件 `backend/target/backend-0.1.0-SNAPSHOT.jar`。
- 最新 JAR 使用 dev profile 启动后，`GET /actuator/health` 返回 `UP`。

### HTTP 资金闭环

使用 `infra/scripts/e2e-finance.py` 通过真实 Spring Boot HTTP API、本地联盟 Mock、MySQL 和 Redis 完成验收，最终输出：

```json
{
  "result": "PASS",
  "runId": "1784271778499",
  "userSid": "u3",
  "firstOrder": "E2E-1784271778499-ONE",
  "secondOrder": "E2E-1784271778499-TWO",
  "withdrawalId": 1
}
```

以下场景全部通过：

1. 租户管理员登录、Session Cookie 与 CSRF 写请求校验。
2. dev 小程序登录、随机会话 Token 与租户解析。
3. 美团 Mock 推广转链并生成用户归因 `sid=u3`。
4. 结算订单同步、用户归因、佣金计算和钱包入账。
5. 同一 `Idempotency-Key` 重复提交提现返回同一记录，只冻结一次余额。
6. 提现从 `SUBMITTED` 经 `APPROVED` 到 `PAID`，付款只扣冻结余额。
7. 重复付款返回 `INVALID_WITHDRAWAL_TRANSITION`，钱包余额不变。
8. 重复同步与单订单重试不重复结算、不重复入账。
9. 退款冲正时可用余额不足，形成 `debt_cent=1000`，可用余额保持非负。
10. 重复退款不重复增加欠款。
11. 欠款用户提交提现返回 `WITHDRAWAL_BLOCKED_BY_DEBT`。
12. 后续 1500 分佣金先偿还 1000 分欠款，仅剩 500 分进入可用余额。
13. 钱包保留 `COMMISSION`、`WITHDRAWAL_FREEZE`、`WITHDRAWAL_PAID`、`COMMISSION_REVERSAL` 不可变流水。

### 最终数据库与 Redis 证据

- 第一笔订单状态为 `REFUNDED`，结算与退款均只处理一次。
- 第二笔订单状态为 `SETTLED`，只产生一笔 1500 分自购佣金。
- 第一笔 1000 分佣金记录已标记 `REVERSED`，并存在唯一反向佣金记录。
- 用户 3 最终钱包：`available_cent=500`、`frozen_cent=0`、`debt_cent=0`。
- 提现 1 最终状态为 `PAID`，审计链完整记录 `SUBMIT → APPROVED → MARK_PAID`。
- 最近多次美团同步任务为 `SUCCESS`；历史 `FAILED` 记录保留用于审计，没有被覆盖。
- Redis 中无 `affiliate-sync:*` 锁残留。

## 本轮实跑发现并修复的问题

- 删除 MySQL 8.4 不允许的“CHECK 约束引用自增字段”，自邀请和邀请环继续由业务层阻止。
- 为存在测试构造器的联盟适配器和调度器显式标注生产构造器，避免 Spring Bean 装配失败。
- 将 Spring Security CSRF 请求处理器调整为与前端原始 `XSRF-TOKEN` Cookie 回传方式兼容。
- 将 MySQL `BIGINT UNSIGNED` JDBC 读取从 `(Long)` 强转改为 `((Number) value).longValue()`，兼容 Connector 返回 `BigInteger`。
- 固化 Docker 29 与 Testcontainers/docker-java 的 API 兼容配置。

## 本轮加固测试（2026-07-24）

### 资金并发安全测试（3 项）

使用 `@SpringBootTest` 注入真实 Spring 代理 Bean，在 Testcontainers MySQL 8.4 上通过 `ExecutorService` + `CountDownLatch` 双线程并发执行：

1. **并发结算**：同一订单双线程同时调用 `CommissionSettlementService.settle(...)`，只产生一条佣金记录和一条钱包流水，订单版本只递增一次。
2. **并发退款冲正**：同一订单双线程同时调用 `CommissionReversalService.reverseIfRefunded(...)`，只产生一条冲正和一条冲正流水，第二个线程抛出 `REVERSAL_CONFLICT`（HTTP 409）。
3. **并发提现付款**：同一提现双线程同时调用 `WithdrawalService.markPaid(...)`，一个成功另一个抛出 `INVALID_WITHDRAWAL_TRANSITION`，只产生一条 `WITHDRAWAL_PAID` 流水和一条 `MARK_PAID` 审计。

### 双租户 HTTP 越权隔离测试（7 项）

使用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + JDK `HttpClient` + 独立 `CookieManager`，真实执行管理员登录、Session、CSRF 和业务请求：

4. 双租户各自独立 Session，提现列表完全隔离。
5. 租户 A 审批/拒绝/付款租户 B 的提现返回 `WITHDRAWAL_NOT_FOUND`（HTTP 404），B 的提现状态和钱包不变。
6. 租户 A 重试租户 B 的订单同步返回 `JOB_NOT_FOUND`（HTTP 404）。
7. 租户管理员不能访问平台管理员 API，平台管理员不能访问租户管理员 API。
8. 租户被禁用后，使用旧 Session 发送请求返回 `UNAUTHORIZED`（HTTP 401），Session 被立即失效。
9. 跨租户联盟归因用户绑定返回 `USER_NOT_FOUND`，正常重复归因保持幂等。
10. 缺少 `X-XSRF-TOKEN` Header 的写请求返回 `FORBIDDEN`（HTTP 403），数据库无变更。

### 本轮安全加固

- `AdminAuthenticationFilter` 每请求重新检查 `tenant_admin.status`、`platform_admin.status` 和 `tenant.status`，禁用后旧 Session 立即失效。
- `AffiliateAttributionService.bind(...)` 增加 `wx_user WHERE tenant_id=? AND id=?` 归因用户租户归属校验。
- `CommissionReversalService` 退款记录更新增加 `tenant_id` 和 `status='CREDITED'` 条件，冲突时抛 `REVERSAL_CONFLICT`。
- `AffiliateOrderSyncService.finishJob(...)` 任务完成更新增加 `tenant_id` 条件。
- `ApiExceptionHandler` 将 `REVERSAL_CONFLICT` 映射为 HTTP 409。
- GitHub CI 将无 NVD Key 的 OWASP 全量扫描替换为 Google 官方 OSV Scanner reusable workflow。

### 全量测试汇总

| 轮次 | 测试数 | 失败 | 错误 | 跳过 | Checkstyle |
|------|--------|------|------|------|------------|
| 2026-07-17 | 26 | 0 | 0 | 0 | 0 |
| 2026-07-24 | 36 | 0 | 0 | 0 | 0 |

## 仍未通过的生产门禁

1. 真实微信 `jscode2session`、美团联盟和饿了么/淘宝闪购账号、权限与正式订单联调。
2. 生产 Compose 镜像构建、HTTPS 域名、微信合法域名、防火墙和日志告警验收。
3. 管理后台平台管理员专用前端和生产运营流程验收。
4. 备份恢复脚本的实际恢复演练及恢复后资金对账。
5. 更高强度压力测试（100+ 并发）和故障注入测试。
6. 渗透测试、隐私、税务和联盟合规复核。

## 已知非阻断警告

- 当前 Flyway 10.20.1 对 MySQL 8.4 提示"建议升级"，但 V1-V5 已在多个真实 MySQL 8.4 实例中实际成功执行；生产前仍建议升级并重新验收。
- Mockito 在 Java 21 下提示未来 JDK 将限制动态加载 Agent；当前不影响测试结果，后续应改为显式 Java Agent 配置。
- 管理后台 Element Plus vendor 包仍偏大（约 963 KB），属于性能优化项，不阻断本地业务闭环。

## 结论

"本地全链路可运营演示"目标已经达到：Docker、MySQL、Redis、Flyway、完整 Spring 上下文、联盟 Mock、订单同步、佣金、钱包、提现、退款、欠款、后续偿债、并发事务隔离、双租户越权防护和关键重复操作拦截均已实际跑通。项目现在可以用于本地演示、产品验收和正式凭据联调，但仍不能直接处理真实订单或真实资金；必须完成上述生产门禁后再上线。
