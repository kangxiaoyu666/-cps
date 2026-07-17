#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${ENV_FILE:-${INFRA_DIR}/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-${INFRA_DIR}/docker-compose.prod.yml}"
BACKUP_ROOT="${BACKUP_ROOT:-${INFRA_DIR}/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="${BACKUP_ROOT}/${TIMESTAMP}"

command -v docker >/dev/null 2>&1 || { printf '错误：未安装 docker。\n' >&2; exit 1; }
command -v gzip >/dev/null 2>&1 || { printf '错误：未安装 gzip。\n' >&2; exit 1; }
[[ -f "${ENV_FILE}" ]] || { printf '错误：环境文件不存在：%s\n' "${ENV_FILE}" >&2; exit 1; }

compose=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")
"${compose[@]}" ps --status running mysql redis >/dev/null
[[ "$("${compose[@]}" ps --status running -q mysql)" ]] || { printf '错误：mysql 未运行。\n' >&2; exit 1; }
[[ "$("${compose[@]}" ps --status running -q redis)" ]] || { printf '错误：redis 未运行。\n' >&2; exit 1; }

mkdir -p "${BACKUP_DIR}"
cleanup() {
  "${compose[@]}" exec -T redis rm -f /data/waimai-cps-backup.rdb >/dev/null 2>&1 || true
}
trap cleanup EXIT

printf '正在备份 MySQL...\n'
"${compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --host=127.0.0.1 --user=root --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF "$MYSQL_DATABASE"' \
  | gzip -9 >"${BACKUP_DIR}/mysql.sql.gz"

printf '正在备份 Redis...\n'
"${compose[@]}" exec -T redis sh -c 'redis-cli --no-auth-warning -a "$REDIS_PASSWORD" --rdb /data/waimai-cps-backup.rdb >/dev/null'
"${compose[@]}" cp redis:/data/waimai-cps-backup.rdb "${BACKUP_DIR}/redis.rdb"

printf 'created_at_utc=%s\ncompose_project=%s\n' "${TIMESTAMP}" "$(basename "${INFRA_DIR}")" >"${BACKUP_DIR}/metadata.txt"
if command -v shasum >/dev/null 2>&1; then
  (cd "${BACKUP_DIR}" && shasum -a 256 mysql.sql.gz redis.rdb metadata.txt >SHA256SUMS)
elif command -v sha256sum >/dev/null 2>&1; then
  (cd "${BACKUP_DIR}" && sha256sum mysql.sql.gz redis.rdb metadata.txt >SHA256SUMS)
fi

printf '备份完成：%s\n' "${BACKUP_DIR}"
