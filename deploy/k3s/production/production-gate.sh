#!/usr/bin/env bash
set -Eeuo pipefail

# 生产门禁只验证外部 HA 依赖已明确接管，避免误用单节点联调清单。
: "${KAFKA_HA_READY:=false}"
: "${ELASTICSEARCH_HA_READY:=false}"
: "${MYSQL_HA_READY:=false}"
: "${REDIS_HA_READY:=false}"
: "${OTEL_STACK_READY:=false}"
: "${ALERTING_READY:=false}"

for pair in \
  "KAFKA_HA_READY=${KAFKA_HA_READY}" \
  "ELASTICSEARCH_HA_READY=${ELASTICSEARCH_HA_READY}" \
  "MYSQL_HA_READY=${MYSQL_HA_READY}" \
  "REDIS_HA_READY=${REDIS_HA_READY}" \
  "OTEL_STACK_READY=${OTEL_STACK_READY}" \
  "ALERTING_READY=${ALERTING_READY}"; do
  key="${pair%%=*}"
  value="${pair#*=}"
  if [[ "$value" != "true" ]]; then
    echo "$key must be true after the production dependency evidence is archived" >&2
    exit 1
  fi
done

echo "production dependency gate passed"
