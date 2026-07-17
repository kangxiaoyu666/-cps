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

## 仍未通过的生产门禁

1. 真实微信 `jscode2session`、美团联盟和饿了么/淘宝闪购账号、权限与正式订单联调。
2. 同一订单并发结算、同一提现并发付款的高并发压力测试和故障注入测试。
3. 两租户全量 HTTP 越权与数据隔离集成测试。
4. 管理后台平台管理员专用前端和生产运营流程验收。
5. 生产 Compose 镜像构建、HTTPS 域名、微信合法域名、防火墙和日志告警验收。
6. 备份恢复脚本的实际恢复演练及恢复后资金对账。
7. NVD/供应链漏洞扫描、渗透测试、隐私、税务和合规复核。
8. Git 仓库初始化、版本基线和可回滚发布记录。

## 已知非阻断警告

- 当前 Flyway 10.20.1 对 MySQL 8.4 提示“建议升级”，但 V1-V5 已在三个真实 MySQL 8.4 实例中实际成功执行；生产前仍建议升级并重新验收。
- Mockito 在 Java 21 下提示未来 JDK 将限制动态加载 Agent；当前不影响 26 项测试结果，后续应改为显式 Java Agent 配置。
- 管理后台 Element Plus vendor 包仍偏大，属于性能优化项，不阻断本地业务闭环。

## 结论

“本地全链路可运营演示”目标已经达到：Docker、MySQL、Redis、Flyway、完整 Spring 上下文、联盟 Mock、订单同步、佣金、钱包、提现、退款、欠款、后续偿债和关键重复操作拦截均已实际跑通。项目现在可以用于本地演示、产品验收和正式凭据联调，但仍不能直接处理真实订单或真实资金；必须完成上述生产门禁后再上线。
