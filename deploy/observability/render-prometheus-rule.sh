#!/usr/bin/env bash
set -Eeuo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RULE_FILE="${1:-$BASE_DIR/prometheus-rules.yaml}"

[[ -f "$RULE_FILE" ]] || { echo "Missing Prometheus rule file: $RULE_FILE" >&2; exit 1; }

printf '%s\n' \
  'apiVersion: monitoring.coreos.com/v1' \
  'kind: PrometheusRule' \
  'metadata:' \
  '  name: log-platform-observability' \
  '  namespace: monitoring' \
  '  labels:' \
  '    app.kubernetes.io/name: log-platform' \
  '    prometheus: kube-prometheus' \
  'spec:'
sed 's/^/  /' "$RULE_FILE"
