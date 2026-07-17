#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${ENV_FILE:-${INFRA_DIR}/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-${INFRA_DIR}/docker-compose.prod.yml}"

failures=0
pass() { printf '[通过] %s\n' "$1"; }
fail() { printf '[失败] %s\n' "$1" >&2; failures=$((failures + 1)); }
warn() { printf '[警告] %s\n' "$1" >&2; }

command -v docker >/dev/null 2>&1 || { fail '未安装 docker'; exit 1; }
[[ -f "${ENV_FILE}" ]] || { fail "环境文件不存在：${ENV_FILE}"; exit 1; }

required_vars=(MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD SESSION_TOKEN_PEPPER DATA_ENCRYPTION_KEY AFFILIATE_SECRET_KEY DEFAULT_TENANT_CODE)
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a
for name in "${required_vars[@]}"; do
  value="${!name:-}"
  if [[ -z "${value}" || "${value}" == *replace_with* || "${value}" == *change_me* ]]; then
    fail "${name} 未设置或仍为示例值"
  else
    pass "${name} 已设置"
  fi
done
session_token_pepper="${SESSION_TOKEN_PEPPER:-}"
[[ ${#session_token_pepper} -ge 32 ]] || fail 'SESSION_TOKEN_PEPPER 少于 32 个字符'

if [[ "$(uname -s)" != "Darwin" ]]; then
  mode="$(stat -c '%a' "${ENV_FILE}" 2>/dev/null || true)"
  [[ "${mode}" == "600" || "${mode}" == "400" ]] || warn "建议将 ${ENV_FILE} 权限设为 600"
fi

compose=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")
if "${compose[@]}" config --quiet; then
  pass 'docker compose config 静态检查'
else
  fail 'docker compose config 静态检查'
fi

if ! docker info >/dev/null 2>&1; then
  warn 'Docker daemon 未运行；跳过运行状态和 HTTP 检查'
  (( failures == 0 )) || exit 1
  exit 0
fi
pass 'Docker daemon 正在运行'

for service in mysql redis backend admin-web; do
  container_id="$("${compose[@]}" ps -q "${service}")"
  if [[ -z "${container_id}" ]]; then
    fail "${service} 容器未运行"
    continue
  fi
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_id}")"
  if [[ "${status}" == "healthy" ]]; then
    pass "${service} 健康"
  else
    fail "${service} 状态为 ${status}"
  fi
done

bind_address="${ADMIN_WEB_BIND_ADDRESS:-127.0.0.1}"
port="${ADMIN_WEB_PORT:-8080}"
if [[ "${bind_address}" == "0.0.0.0" || "${bind_address}" == "::" ]]; then
  check_host="127.0.0.1"
else
  check_host="${bind_address}"
fi
if command -v curl >/dev/null 2>&1 && curl --fail --silent --show-error "http://${check_host}:${port}/healthz" >/dev/null; then
  pass 'admin-web HTTP 健康检查'
else
  fail 'admin-web HTTP 健康检查'
fi

(( failures == 0 )) || exit 1
printf '所有部署检查均通过。\n'
