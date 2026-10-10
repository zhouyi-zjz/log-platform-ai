#!/usr/bin/env bash
set -Eeuo pipefail

# 只验证观测栈健康端点和 Prometheus/Tempo/Loki 基础 API，不写入业务数据。
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${OBSERVABILITY_HOST:=127.0.0.1}"
: "${TEMPO_PORT:=3200}"
: "${LOKI_PORT:=3100}"
: "${PROMETHEUS_PORT:=9090}"
: "${COLLECTOR_HEALTH_PORT:=13133}"

curl --fail --silent "http://${OBSERVABILITY_HOST}:${COLLECTOR_HEALTH_PORT}/" >/dev/null
curl --fail --silent "http://${OBSERVABILITY_HOST}:${TEMPO_PORT}/ready" >/dev/null
curl --fail --silent "http://${OBSERVABILITY_HOST}:${LOKI_PORT}/ready" >/dev/null
curl --fail --silent "http://${OBSERVABILITY_HOST}:${PROMETHEUS_PORT}/-/ready" >/dev/null
echo "observability stack health checks passed"
