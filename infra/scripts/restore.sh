#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${ENV_FILE:-${INFRA_DIR}/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-${INFRA_DIR}/docker-compose.prod.yml}"
BACKUP_DIR="${1:-}"

usage() {
  printf '用法：%s <备份目录>\n' "$(basename "$0")" >&2
}

[[ -n "${BACKUP_DIR}" ]] || { usage; exit 2; }
[[ -f "${ENV_FILE}" ]] || { printf '错误：环境文件不存在：%s\n' "${ENV_FILE}" >&2; exit 1; }
[[ -f "${BACKUP_DIR}/mysql.sql.gz" ]] || { printf '错误：缺少 mysql.sql.gz。\n' >&2; exit 1; }
[[ -f "${BACKUP_DIR}/redis.rdb" ]] || { printf '错误：缺少 redis.rdb。\n' >&2; exit 1; }
BACKUP_DIR="$(cd -- "${BACKUP_DIR}" && pwd)"
command -v docker >/dev/null 2>&1 || { printf '错误：未安装 docker。\n' >&2; exit 1; }
command -v gzip >/dev/null 2>&1 || { printf '错误：未安装 gzip。\n' >&2; exit 1; }

if [[ "${CONFIRM_RESTORE:-}" != "YES" ]]; then
  printf '错误：恢复会覆盖当前 MySQL 数据库和 Redis 数据。确认后使用 CONFIRM_RESTORE=YES 重新执行。\n' >&2
  exit 2
fi

compose=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

if [[ -f "${BACKUP_DIR}/SHA256SUMS" ]]; then
  if command -v shasum >/dev/null 2>&1; then
    (cd "${BACKUP_DIR}" && shasum -a 256 -c SHA256SUMS)
  elif command -v sha256sum >/dev/null 2>&1; then
    (cd "${BACKUP_DIR}" && sha256sum -c SHA256SUMS)
  else
    printf '警告：未找到校验和工具，跳过完整性检查。\n' >&2
  fi
fi

printf '正在停止应用写入...\n'
"${compose[@]}" stop admin-web backend redis >/dev/null
"${compose[@]}" up -d mysql

printf '正在恢复 MySQL...\n'
gzip -dc "${BACKUP_DIR}/mysql.sql.gz" \
  | "${compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --host=127.0.0.1 --user=root "$MYSQL_DATABASE"'

printf '正在恢复 Redis...\n'
"${compose[@]}" run --rm --no-deps --user 0 --entrypoint sh redis -c 'rm -rf /data/appendonlydir /data/dump.rdb /data/waimai-cps-backup.rdb'
"${compose[@]}" run --rm --no-deps --user 0 --entrypoint sh -v "${BACKUP_DIR}:/restore:ro" redis -c 'cp /restore/redis.rdb /data/dump.rdb && chown redis:redis /data/dump.rdb'
"${compose[@]}" up -d redis

printf '正在启动应用并等待健康检查...\n'
"${compose[@]}" up -d --wait backend admin-web
printf '恢复完成。请立即执行 deploy-check.sh 并进行业务抽查。\n'
