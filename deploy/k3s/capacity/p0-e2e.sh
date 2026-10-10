#!/usr/bin/env bash
set -Eeuo pipefail

# 端到端验收：HTTP 202 -> Kafka -> ES 可查 -> Trace 联查。
# 不把 HTTP 202 当作 ES 成功，必须显式轮询查询结果。
: "${BASE_URL:=http://localhost:8103}"
: "${INGEST_TOKEN:?INGEST_TOKEN is required}"
: "${PROJECT_ID:=p0-e2e}"
: "${WAIT_SECONDS:=30}"
: "${TRACE_ID:=4bf92f3577b34da6a3ce929d0e0e4736}"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 2; }

event_id="p0-e2e-$(date +%s%N)"
payload="$(jq -cn --arg id "$event_id" --arg project "$PROJECT_ID" --arg trace "$TRACE_ID" \
  '{"event.id":$id,"project.id":$project,"service.name":"p0-e2e","deployment.environment":"p0","trace.id":$trace,"span.id":"00f067aa0ba902b7","log.level":"INFO","message":"p0 end-to-end probe"}')"

ingest_status="$(curl --silent --show-error --output /tmp/log-platform-p0-ingest.json --write-out '%{http_code}' \
  -X POST "$BASE_URL/monitor/api/logs/ingest" \
  -H 'Content-Type: application/json' -H "X-Log-Ingest-Token: $INGEST_TOKEN" --data "$payload")"
[[ "$ingest_status" == "202" ]] || { cat /tmp/log-platform-p0-ingest.json >&2; echo "ingest did not return 202" >&2; exit 1; }

echo "Kafka accepted event_id=$event_id"
deadline=$(( $(date +%s) + WAIT_SECONDS ))
query="$(jq -cn --arg project "$PROJECT_ID" --arg trace "$TRACE_ID" \
  '{"project.id":$project,"trace.id":$trace,"size":20}')"
while (( $(date +%s) < deadline )); do
  body="$(curl --silent --show-error -X POST "$BASE_URL/monitor/api/logs/search" \
    -H 'Content-Type: application/json' --data "$query" || true)"
  if jq -e --arg id "$event_id" '.data.items[]? | select(.source["event.id"] == $id)' >/dev/null 2>&1 <<<"$body"; then
    echo "Elasticsearch visibility check passed"
    trace="$(curl --silent --show-error "$BASE_URL/monitor/api/traces/$TRACE_ID" || true)"
    jq -e --arg trace "$TRACE_ID" '.data.traceId == $trace' >/dev/null <<<"$trace" || {
      echo "Trace correlation endpoint did not return expected trace" >&2
      exit 1
    }
    echo "Trace correlation check passed"
    exit 0
  fi
  sleep 2
done

echo "event was accepted by Kafka but not visible in Elasticsearch within ${WAIT_SECONDS}s" >&2
exit 1
