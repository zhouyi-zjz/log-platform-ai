#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/deploy/k3s/.env}"
[[ -f "$ENV_FILE" ]] || { echo "Missing environment file: $ENV_FILE" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
: "${NAMESPACE:=log-platform}"
: "${LOG_INGEST_TOKEN:?LOG_INGEST_TOKEN is required}"

command -v kubectl >/dev/null || { echo "kubectl is required" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }

kubectl -n "$NAMESPACE" rollout status deployment/log-platform-consumer --timeout=2m

PORT="${SMOKE_PORT:-18080}"
BACKEND_METRICS_PORT="${BACKEND_METRICS_PORT:-18081}"
CONSUMER_METRICS_PORT="${CONSUMER_METRICS_PORT:-18082}"
TMP_DIR="$(mktemp -d)"
cleanup() {
  if [[ -n "${PORT_FORWARD_PID:-}" ]]; then kill "$PORT_FORWARD_PID" >/dev/null 2>&1 || true; fi
  if [[ -n "${BACKEND_METRICS_PID:-}" ]]; then kill "$BACKEND_METRICS_PID" >/dev/null 2>&1 || true; fi
  if [[ -n "${CONSUMER_METRICS_PID:-}" ]]; then kill "$CONSUMER_METRICS_PID" >/dev/null 2>&1 || true; fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

kubectl -n "$NAMESPACE" port-forward service/log-platform-frontend "$PORT:80" >"$TMP_DIR/port-forward.log" 2>&1 &
PORT_FORWARD_PID=$!
for _ in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/healthz" >/dev/null; then break; fi
  sleep 1
done
curl -fsS "http://127.0.0.1:$PORT/healthz" | grep -q '^ok' || {
  cat "$TMP_DIR/port-forward.log" >&2
  exit 1
}

curl -fsS "http://127.0.0.1:$PORT/monitor/api/auth/csrf" >"$TMP_DIR/csrf.json"
grep -q 'headerName' "$TMP_DIR/csrf.json" || { cat "$TMP_DIR/csrf.json" >&2; exit 1; }

status="$(curl -sS -o "$TMP_DIR/ingest.json" -w '%{http_code}' \
  -X POST "http://127.0.0.1:$PORT/monitor/api/logs/ingest" \
  -H 'Content-Type: application/json' \
  -H "X-Log-Ingest-Token: $LOG_INGEST_TOKEN" \
  --data-binary @"$ROOT_DIR/docs/ingest/log-event-example.json")"
if [[ "$status" != "202" ]]; then
  echo "Expected ingest HTTP 202, got $status" >&2
  cat "$TMP_DIR/ingest.json" >&2
  exit 1
fi

kubectl -n "$NAMESPACE" port-forward service/log-platform-backend "$BACKEND_METRICS_PORT:8103" \
  >"$TMP_DIR/backend-metrics-port-forward.log" 2>&1 &
BACKEND_METRICS_PID=$!
kubectl -n "$NAMESPACE" port-forward service/log-platform-consumer "$CONSUMER_METRICS_PORT:8104" \
  >"$TMP_DIR/consumer-metrics-port-forward.log" 2>&1 &
CONSUMER_METRICS_PID=$!
for _ in {1..60}; do
  backend_metrics="$(curl -fsS "http://127.0.0.1:$BACKEND_METRICS_PORT/actuator/prometheus" 2>/dev/null || true)"
  consumer_metrics="$(curl -fsS "http://127.0.0.1:$CONSUMER_METRICS_PORT/actuator/prometheus" 2>/dev/null || true)"
  if grep -q 'log_platform_kafka_available{[^}]*role="api"[^}]*} 1' <<<"$backend_metrics" \
      && grep -q 'log_platform_consumer_events_total{[^}]*role="consumer"' <<<"$consumer_metrics"; then
    break
  fi
  sleep 1
done
grep -q 'log_platform_kafka_available{[^}]*role="api"[^}]*} 1' <<<"$backend_metrics" || {
  cat "$TMP_DIR/backend-metrics-port-forward.log" >&2
  echo "Kafka monitoring did not become available" >&2
  exit 1
}
grep -q 'log_platform_consumer_events_total{[^}]*role="consumer"' <<<"$consumer_metrics" || {
  cat "$TMP_DIR/consumer-metrics-port-forward.log" >&2
  echo "Consumer Prometheus metrics are unavailable" >&2
  exit 1
}

echo "Frontend health, Kafka ingest, Kafka monitoring, and Consumer metrics checks passed."
echo "The ingest response only confirms Kafka acceptance; verify ES visibility with the UI or query API."
