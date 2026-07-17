# 外卖 CPS 分销平台

安全重写版 MVP 基线。旧 PHP、微擎、订单侠、折淘客及旧微信企业付款接口不进入本项目。

## 目录

- `backend`：Java 21 / Spring Boot 3 / Flyway / MySQL / Redis
- `admin-web`：Vue 3 / TypeScript / Vite / Element Plus / Pinia
- `miniprogram`：微信原生小程序 + TypeScript
- `infra`：本地 MySQL、Redis 与环境变量模板
- `docs`：产品、架构、数据、安全、运维与交付文档

## 本地启动

```bash
cp infra/.env.example infra/.env
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd admin-web && npm install && npm run dev
```

Java 后端默认 `8080`，管理后台默认 `5173`，本地联盟 Mock 默认 `8090`。小程序用微信开发者工具导入 `miniprogram`；开发版默认请求本机后端，体验版和正式版需在微信第三方平台 `extConfig.apiBaseUrl` 中注入 HTTPS API 根地址（例如 `https://api.example.com/api/v1`），且需加入微信服务器域名白名单。活动编码可留空，由后端使用租户后台渠道配置中的活动 ID。开发 profile 会初始化 `demo` 租户、租户管理员 `admin`、平台管理员 `platform`、演示分佣规则和本地 Mock 渠道；默认演示密码 `DemoAdmin123!` 只允许本地使用，可通过 `DEV_ADMIN_PASSWORD` 覆盖，生产 profile 不加载此初始化器。

### 本地资金闭环验收

基础服务和 dev 后端启动后执行：

```bash
python3 infra/scripts/e2e-finance.py
```

脚本会实际验证管理员登录与 CSRF、小程序登录、美团 Mock 转链、订单同步、佣金入账、提现幂等、审核付款、重复付款拦截、重复结算、退款欠款、重复退款、欠款禁提和后续佣金优先偿债。成功时输出 `"result": "PASS"`。2026-07-17 已在 Docker Engine 29.3.0、MySQL 8.4、Redis 7.4 和真实 Spring Boot HTTP API 上完成一次全流程验收，详见 `docs/test-report.md`。

若终端环境中存在全局 `SERVER_PORT` 或数据库变量，启动 JAR 时应显式覆盖，避免继承无关环境值：

```bash
SERVER_PORT=8080 \
SPRING_PROFILES_ACTIVE=dev \
DB_URL='jdbc:mysql://127.0.0.1:3306/waimai_cps?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' \
DB_USERNAME=waimai \
DB_PASSWORD=waimai_dev_password \
REDIS_HOST=127.0.0.1 \
java -jar backend/target/backend-0.1.0-SNAPSHOT.jar
```

## 安全边界

- 用户身份只从服务端会话令牌解析，禁止客户端传 `openid` 作为授权依据。
- 租户上下文只从认证身份建立，禁止请求参数选择租户。
- 金额统一为 `BIGINT` 分；钱包只能通过不可变流水服务变更。
- 仅支持自购奖励、一级直接邀请奖励。
- MVP 提现为人工审核和线下付款登记，不调用自动转账。
- 后台采用 HttpOnly Session Cookie、CSRF Token、BCrypt 密码校验和 Redis 登录限流，租户范围来自服务端身份。
- 小程序 401 最多自动重登一次；不保存 openid；邀请使用 24 小时一次性场景参数。
- 美团与饿了么适配器已实现官方签名、真实 HTTP 转链和订单查询；正式凭据或权限缺失时明确失败，不伪造成功。
- 订单同步器提供 tenant+platform Redis 锁、持久化游标、任务记录、幂等 upsert、结算和退款冲正。
- 退款先扣可用余额，差额记入 `debt_cent`；后续佣金优先偿债，欠款期间禁止提现。

## 配置

开发、测试、生产模板位于 `backend/src/main/resources/application-*.yml`。生产环境密钥只能通过环境变量或密钥管理服务注入。

## 生产部署

生产编排位于 `infra/docker-compose.prod.yml`，包含 MySQL、Redis、Java 后端和 Nginx 管理后台。MySQL、Redis 不发布宿主机端口；管理后台是唯一入口，Nginx 提供 SPA 回退、`/api/` 反向代理与安全响应头。仓库不包含生产密钥、TLS 私钥或证书，HTTPS 应由宿主机或独立边缘代理终止。

### 1. 准备配置

```bash
cp infra/.env.example infra/.env
chmod 600 infra/.env
```

替换全部示例值。`SESSION_TOKEN_PEPPER` 至少 32 个随机字符；`DATA_ENCRYPTION_KEY`、`AFFILIATE_SECRET_KEY` 必须分别使用独立的 32 字节随机值并进行 Base64 编码。不要提交 `infra/.env`。默认仅监听 `127.0.0.1:8080`；由同机 TLS 代理转发时保持此设置，需要直接对外监听时才改 `ADMIN_WEB_BIND_ADDRESS`，并同时配置防火墙与 HTTPS。

### 2. 静态检查与启动

```bash
docker compose --env-file infra/.env -f infra/docker-compose.prod.yml config --quiet
docker compose --env-file infra/.env -f infra/docker-compose.prod.yml build --pull
docker compose --env-file infra/.env -f infra/docker-compose.prod.yml up -d --wait
infra/scripts/deploy-check.sh
```

`backend` 等待 MySQL、Redis 健康，`admin-web` 等待后端健康。查看状态和日志：

```bash
docker compose --env-file infra/.env -f infra/docker-compose.prod.yml ps
docker compose --env-file infra/.env -f infra/docker-compose.prod.yml logs --tail=200 backend admin-web
```

### 3. 更新与回滚

更新前先备份，再拉取目标版本并重建：

```bash
infra/scripts/backup.sh
docker compose --env-file infra/.env -f infra/docker-compose.prod.yml build --pull
docker compose --env-file infra/.env -f infra/docker-compose.prod.yml up -d --wait --remove-orphans
infra/scripts/deploy-check.sh
```

回滚应用时切回已验证的源码版本或在 `.env` 中指定已验证的 `BACKEND_IMAGE`、`ADMIN_WEB_IMAGE`；涉及数据库迁移时必须先确认迁移兼容性，不能仅回滚镜像。

### 4. 备份与恢复

备份写入 `infra/backups/<UTC 时间>/`，包括压缩的 MySQL 逻辑备份、Redis RDB、元数据和 SHA-256 校验文件：

```bash
infra/scripts/backup.sh
```

应将备份复制到加密的异机存储，并定期执行恢复演练。恢复会停止应用写入并覆盖当前数据库和 Redis 数据，执行前应额外保留当前快照：

```bash
CONFIRM_RESTORE=YES infra/scripts/restore.sh infra/backups/20260715T000000Z
infra/scripts/deploy-check.sh
```

脚本支持通过 `ENV_FILE`、`COMPOSE_FILE`、`BACKUP_ROOT` 覆盖默认路径。生产运行前还需验证 TLS、域名、防火墙、日志采集、磁盘告警、备份保留策略和微信/联盟正式凭据；真实订单与灾备验收未完成前不得直接上线。

## 当前交付范围

当前仓库定位为“本地全链路可运营演示工程”：已具备数据库结构、认证与租户边界、联盟配置与签名客户端、订单同步/结算/退款、欠款模型、钱包提现、关键运营写接口、本地联盟 Mock 和开发数据初始化。Docker、MySQL 8.4、Redis 7.4、Flyway V1-V5、26 项后端测试和完整 HTTP 资金闭环已经实际执行通过。仍不等同于生产上线版本：真实美团/饿了么和微信凭据、并发与全量跨租户测试、恢复演练、安全扫描、平台管理员前端和合规复核尚未全部完成；这些门禁通过前不得处理真实订单或真实资金。
