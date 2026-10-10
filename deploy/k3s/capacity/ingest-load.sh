#!/usr/bin/env bash

# HTTP 日志入口容量输入工具。
# 该脚本只产生压测请求和基础统计，不代表任何 EPS 已通过容量验收。
set -euo pipefail

TARGET_URL="${TARGET_URL:-http://localhost:8103/monitor/api/logs/ingest}"
TOKEN="${TOKEN:-}"
PROJECT_ID="${PROJECT_ID:-capacity-test}"
EPS="${EPS:-10}"
DURATION_SECONDS="${DURATION_SECONDS:-60}"
WORKERS="${WORKERS:-}"

if [[ -z "$TOKEN" ]]; then
  echo "TOKEN is required" >&2
  exit 2
fi
if ! [[ "$EPS" =~ ^[1-9][0-9]*$ && "$DURATION_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "EPS and DURATION_SECONDS must be positive integers" >&2
  exit 2
fi
if [[ -z "$WORKERS" ]]; then
  WORKERS=$(( EPS < 16 ? EPS : 16 ))
fi
if ! [[ "$WORKERS" =~ ^[1-9][0-9]*$ ]]; then
  echo "WORKERS must be a positive integer" >&2
  exit 2
fi

load_tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/log-ingest-load.XXXXXX")"
cleanup() {
  jobs -p | xargs -r kill 2>/dev/null || true
  rm -rf "$load_tmp_dir"
}
trap cleanup EXIT INT TERM

worker() {
  local worker_id="$1"
  local worker_eps=$(( (EPS + WORKERS - 1) / WORKERS ))
  local interval
  interval="$(awk -v rate="$worker_eps" 'BEGIN { printf "%.6f", 1 / rate }')"
  local deadline=$(( $(date +%s) + DURATION_SECONDS ))
  local sequence=0
  local result_file="$load_tmp_dir/worker-${worker_id}.txt"
  : > "$result_file"
  while (( $(date +%s) < deadline )); do
    sequence=$((sequence + 1))
    local event_id="load-${worker_id}-$(date +%s%N 2>/dev/null || date +%s)-${sequence}"
    curl --silent --show-error --output /dev/null \
      --connect-timeout 3 --max-time 15 \
      --header "X-Log-Ingest-Token: ${TOKEN}" \
      --header "Content-Type: application/json" \
      --write-out '%{http_code} %{time_total}\n' \
      --data "{\"event.id\":\"${event_id}\",\"project.id\":\"${PROJECT_ID}\",\"service.name\":\"capacity-load\",\"deployment.environment\":\"load-test\",\"log.level\":\"INFO\",\"message\":\"capacity probe\"}" \
      "$TARGET_URL" >> "$result_file" || printf '000 15\n' >> "$result_file"
    sleep "$interval"
  done
}

start_epoch="$(date +%s)"
for worker_id in $(seq 1 "$WORKERS"); do
  worker "$worker_id" &
done
wait
end_epoch="$(date +%s)"

awk -v target="$TARGET_URL" -v eps="$EPS" -v duration="$DURATION_SECONDS" \
  -v started="$start_epoch" -v ended="$end_epoch" '
  {
    status=$1; latency=$2 + 0; total++;
    if (status == 202) accepted++;
    else if (status >= 400 && status < 500) client++;
    else if (status >= 500 || status == 000) server++;
    if (latency > 0) { sum += latency; measured++ }
  }
  END {
    printf "target=%s\n", target;
    printf "target_eps=%s\n", eps;
    printf "duration_seconds=%s\n", duration;
    printf "elapsed_seconds=%s\n", ended - started;
    printf "requests=%d\n", total + 0;
    printf "accepted_202=%d\n", accepted + 0;
    printf "client_4xx=%d\n", client + 0;
    printf "server_5xx_or_transport=%d\n", server + 0;
    printf "average_latency_ms=%.2f\n", measured ? sum / measured * 1000 : 0;
    printf "note=measurement_input_only; run against production-like Kafka/ES before capacity claims\n";
  }' "$load_tmp_dir"/worker-*.txt
