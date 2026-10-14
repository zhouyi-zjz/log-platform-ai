#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K3S_DIR="$ROOT_DIR/deploy/k3s"
ENV_FILE="${1:-$K3S_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing environment file: $ENV_FILE" >&2
  echo "Copy deploy/k3s/.env.example to deploy/k3s/.env and edit every secret." >&2
  exit 1
fi

PUSH_IMAGES_OVERRIDE="${PUSH_IMAGES-}"
LOAD_IMAGES_OVERRIDE="${LOAD_IMAGES-}"
SKIP_BUILD_OVERRIDE="${SKIP_BUILD-}"
INSTALL_FLUENT_BIT_OVERRIDE="${INSTALL_FLUENT_BIT-}"
INSTALL_KAFKA_ALERT_ROUTING_OVERRIDE="${INSTALL_KAFKA_ALERT_ROUTING-}"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

[[ -n "$PUSH_IMAGES_OVERRIDE" ]] && PUSH_IMAGES="$PUSH_IMAGES_OVERRIDE"
[[ -n "$LOAD_IMAGES_OVERRIDE" ]] && LOAD_IMAGES="$LOAD_IMAGES_OVERRIDE"
[[ -n "$SKIP_BUILD_OVERRIDE" ]] && SKIP_BUILD="$SKIP_BUILD_OVERRIDE"
[[ -n "$INSTALL_FLUENT_BIT_OVERRIDE" ]] && INSTALL_FLUENT_BIT="$INSTALL_FLUENT_BIT_OVERRIDE"
[[ -n "$INSTALL_KAFKA_ALERT_ROUTING_OVERRIDE" ]] && INSTALL_KAFKA_ALERT_ROUTING="$INSTALL_KAFKA_ALERT_ROUTING_OVERRIDE"

: "${NAMESPACE:=log-platform}"
: "${STORAGE_CLASS:=local-path}"
: "${APP_HOST:=logs.example.com}"
: "${INGRESS_CLASS:=traefik}"
: "${TLS_SECRET_NAME:=log-platform-tls}"
: "${SESSION_COOKIE_SECURE:=true}"
: "${IMAGE_REPOSITORY:=registry.example.com/log-platform}"
: "${IMAGE_TAG:=dev}"
: "${IMAGE_PULL_POLICY:=IfNotPresent}"
: "${PUSH_IMAGES:=false}"
: "${LOAD_IMAGES:=false}"
: "${KAFKA_REPLICAS:=2}"
: "${KAFKA_REPLICATION_FACTOR:=2}"
: "${KAFKA_SECURITY_PROTOCOL:=PLAINTEXT}"
: "${ES_REPLICAS:=2}"
: "${BACKEND_REPLICAS:=1}"
: "${CONSUMER_REPLICAS:=1}"
: "${FRONTEND_REPLICAS:=1}"
: "${INSTALL_FLUENT_BIT:=false}"
: "${INSTALL_OTEL_COLLECTOR:=false}"
: "${INSTALL_PROMETHEUS_RULES:=false}"
: "${INSTALL_KAFKA_ALERT_ROUTING:=false}"
: "${OTEL_COLLECTOR_IMAGE:=otel/opentelemetry-collector-contrib:0.123.0}"
: "${OTEL_ENABLED:=false}"
: "${OTEL_EXPORTER_OTLP_ENDPOINT:=http://otel-collector:4318/v1/traces}"
: "${TEMPO_QUERY_ENABLED:=false}"
: "${TEMPO_QUERY_ENDPOINT:=http://tempo:3200}"
: "${TEMPO_QUERY_TIMEOUT_MS:=3000}"
: "${TEMPO_OTLP_ENDPOINT:=tempo:4317}"
: "${LOKI_PUSH_ENDPOINT:=http://loki:3100/loki/api/v1/push}"
: "${FINOPS_BUDGET_AUDIT_TOPIC:=finops.budget-audit.v1}"
: "${FINOPS_BUDGET_AUDIT_CONSUMER_ENABLED:=false}"
: "${FINOPS_BUDGET_AUDIT_CONSUMER_GROUP:=log-platform-budget-audit}"
: "${FINOPS_BUDGET_AUDIT_INDEX:=finops-budget-audit-v1}"
: "${FINOPS_BUDGET_AUDIT_MAX_PAYLOAD_BYTES:=65536}"
: "${MYSQL_STORAGE_SIZE:=20Gi}"
: "${KAFKA_STORAGE_SIZE:=50Gi}"
: "${ES_STORAGE_SIZE:=100Gi}"
: "${REDIS_STORAGE_SIZE:=5Gi}"
: "${REDIS_IMAGE:=redis:7.4.1-alpine}"
: "${BACKEND_MAX_REPLICAS:=${BACKEND_REPLICAS}}"
: "${BACKEND_MIN_AVAILABLE:=1}"
: "${ES_JAVA_OPTS:=-Xms1g -Xmx1g}"
: "${MYSQL_IMAGE:=mysql:8.4.2}"
: "${KAFKA_IMAGE:=apache/kafka:3.9.1}"
: "${ELASTICSEARCH_IMAGE:=docker.elastic.co/elasticsearch/elasticsearch:8.15.3}"
: "${CURL_IMAGE:=curlimages/curl:8.10.1}"
: "${FLUENT_BIT_IMAGE:=fluent/fluent-bit:3.2.10}"
: "${BOOTSTRAP_ADMIN_DISPLAY_NAME:=系统管理员}"

for required in DB_USERNAME DB_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD LOG_INGEST_TOKEN \
  BOOTSTRAP_ADMIN_USERNAME BOOTSTRAP_ADMIN_PASSWORD; do
  if [[ -z "${!required:-}" || "${!required}" == change-me-* ]]; then
    echo "$required must be set to a non-default value in $ENV_FILE" >&2
    exit 1
  fi
done

if [[ "$DB_USERNAME" == "root" ]]; then
  echo "DB_USERNAME must be a dedicated application user, not root" >&2
  exit 1
fi
if (( ${#BOOTSTRAP_ADMIN_PASSWORD} < 12 )); then
  echo "BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters" >&2
  exit 1
fi
if [[ "$KAFKA_REPLICAS" != "2" || "$KAFKA_REPLICATION_FACTOR" != "2" ]]; then
  echo "This K3s deployment is configured for exactly two Kafka brokers with topic replication factor 2." >&2
  echo "Set KAFKA_REPLICAS=2 and KAFKA_REPLICATION_FACTOR=2, or use the production operator manifests for another topology." >&2
  exit 1
fi
if [[ "$ES_REPLICAS" != "2" ]]; then
  echo "This K3s deployment is configured for exactly two Elasticsearch nodes; set ES_REPLICAS=2." >&2
  exit 1
fi
if [[ "$IMAGE_PULL_POLICY" != "IfNotPresent" && "$IMAGE_PULL_POLICY" != "Always" && "$IMAGE_PULL_POLICY" != "Never" ]]; then
  echo "IMAGE_PULL_POLICY must be IfNotPresent, Always, or Never" >&2
  exit 1
fi
if [[ "$INSTALL_KAFKA_ALERT_ROUTING" == "true" && -z "${ALERT_WEBHOOK_URL:-}" ]]; then
  echo "ALERT_WEBHOOK_URL is required when INSTALL_KAFKA_ALERT_ROUTING=true" >&2
  exit 1
fi

for command_name in kubectl envsubst; do
  command -v "$command_name" >/dev/null || {
    echo "Required command not found: $command_name" >&2
    exit 1
  }
done

kubectl cluster-info >/dev/null
kubectl get storageclass "$STORAGE_CLASS" >/dev/null

BACKEND_IMAGE="$IMAGE_REPOSITORY/backend:$IMAGE_TAG"
FRONTEND_IMAGE="$IMAGE_REPOSITORY/frontend:$IMAGE_TAG"
DEPLOYMENT_ID="$(date +%s)"
export NAMESPACE STORAGE_CLASS APP_HOST INGRESS_CLASS TLS_SECRET_NAME SESSION_COOKIE_SECURE
export IMAGE_PULL_POLICY BACKEND_IMAGE FRONTEND_IMAGE DEPLOYMENT_ID
export DB_USERNAME KAFKA_REPLICAS KAFKA_REPLICATION_FACTOR KAFKA_SECURITY_PROTOCOL ES_REPLICAS BACKEND_REPLICAS CONSUMER_REPLICAS FRONTEND_REPLICAS
export BOOTSTRAP_ADMIN_DISPLAY_NAME
export MYSQL_STORAGE_SIZE KAFKA_STORAGE_SIZE ES_STORAGE_SIZE REDIS_STORAGE_SIZE ES_JAVA_OPTS
export MYSQL_IMAGE KAFKA_IMAGE ELASTICSEARCH_IMAGE REDIS_IMAGE CURL_IMAGE FLUENT_BIT_IMAGE
export BACKEND_MAX_REPLICAS BACKEND_MIN_AVAILABLE
export INSTALL_OTEL_COLLECTOR OTEL_COLLECTOR_IMAGE OTEL_ENABLED OTEL_EXPORTER_OTLP_ENDPOINT
export TEMPO_QUERY_ENABLED TEMPO_QUERY_ENDPOINT TEMPO_QUERY_TIMEOUT_MS
export TEMPO_OTLP_ENDPOINT LOKI_PUSH_ENDPOINT
export FINOPS_BUDGET_AUDIT_TOPIC FINOPS_BUDGET_AUDIT_CONSUMER_ENABLED
export FINOPS_BUDGET_AUDIT_CONSUMER_GROUP FINOPS_BUDGET_AUDIT_INDEX FINOPS_BUDGET_AUDIT_MAX_PAYLOAD_BYTES

build_images() {
  if [[ "${SKIP_BUILD:-false}" == "true" ]]; then
    echo "Skipping image build because SKIP_BUILD=true"
    return
  fi
  command -v docker >/dev/null || {
    echo "docker is required to build images. Set SKIP_BUILD=true if images already exist." >&2
    exit 1
  }
  docker build -f "$K3S_DIR/docker/backend.Dockerfile" -t "$BACKEND_IMAGE" "$ROOT_DIR"
  docker build -f "$K3S_DIR/docker/frontend.Dockerfile" -t "$FRONTEND_IMAGE" "$ROOT_DIR"
  if [[ "$PUSH_IMAGES" == "true" ]]; then
    docker push "$BACKEND_IMAGE"
    docker push "$FRONTEND_IMAGE"
  fi
  if [[ "$LOAD_IMAGES" == "true" ]]; then
    command -v k3s >/dev/null || {
      echo "k3s is required for LOAD_IMAGES=true" >&2
      exit 1
    }
    docker save "$BACKEND_IMAGE" | sudo k3s ctr images import -
    docker save "$FRONTEND_IMAGE" | sudo k3s ctr images import -
  fi
  if [[ "$PUSH_IMAGES" != "true" && "$LOAD_IMAGES" != "true" ]]; then
    echo "Set PUSH_IMAGES=true for a registry or LOAD_IMAGES=true for a single-node K3s cluster." >&2
    exit 1
  fi
}

apply_manifest() {
  local file="$1"
  envsubst < "$K3S_DIR/manifests/$file" | kubectl apply -f -
}

create_configmaps() {
  kubectl -n "$NAMESPACE" create configmap log-platform-db-init \
    --from-file=01-base.sql="$ROOT_DIR/sql/base.sql" \
    --from-file=02-project-token-and-audit.sql="$ROOT_DIR/sql/migrations/V1__project_token_and_query_audit.sql" \
    --dry-run=client -o yaml | kubectl apply -f -
  kubectl -n "$NAMESPACE" create configmap log-platform-es-init \
    --from-file=logs-index-template.json="$ROOT_DIR/deploy/elasticsearch/logs-index-template.json" \
    --from-file=finops-budget-audit-index-template.json="$ROOT_DIR/deploy/elasticsearch/finops-budget-audit-index-template.json" \
    --from-file=ilm-policy.json="$K3S_DIR/config/ilm-policy.json" \
    --dry-run=client -o yaml | kubectl apply -f -
  if [[ "$INSTALL_FLUENT_BIT" == "true" ]]; then
    kubectl -n "$NAMESPACE" create configmap log-platform-fluent-bit-config \
      --from-file=fluent-bit.conf="$ROOT_DIR/deploy/fluent-bit/fluent-bit.conf" \
      --from-file=parsers.conf="$ROOT_DIR/deploy/fluent-bit/parsers.conf" \
      --dry-run=client -o yaml | kubectl apply -f -
  fi
  if [[ "$INSTALL_OTEL_COLLECTOR" == "true" ]]; then
    kubectl -n "$NAMESPACE" create configmap log-platform-otel-collector-config \
      --from-file=otel-collector-config.yaml="$ROOT_DIR/deploy/observability/otel-collector-config.yaml" \
      --dry-run=client -o yaml | kubectl apply -f -
  fi
}

echo "Building application images..."
build_images

echo "Creating namespace and secrets..."
apply_manifest 00-namespace.yaml
kubectl -n "$NAMESPACE" create secret generic log-platform-db \
  --from-literal=root-password="$MYSQL_ROOT_PASSWORD" \
  --from-literal=app-password="$DB_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NAMESPACE" create secret generic log-platform-app \
  --from-literal=ingest-token="$LOG_INGEST_TOKEN" \
  --from-literal=redis-password="$REDIS_PASSWORD" \
  --from-literal=redis-url="redis://:$REDIS_PASSWORD@redis:6379" \
  --from-literal=bootstrap-admin-username="$BOOTSTRAP_ADMIN_USERNAME" \
  --from-literal=bootstrap-admin-password="$BOOTSTRAP_ADMIN_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
create_configmaps

echo "Applying data services..."
apply_manifest 10-mysql.yaml
apply_manifest 20-kafka.yaml
apply_manifest 30-elasticsearch.yaml
apply_manifest 35-redis.yaml
kubectl -n "$NAMESPACE" rollout status statefulset/mysql --timeout=15m
apply_manifest 15-mysql-schema-job.yaml
kubectl -n "$NAMESPACE" wait --for=condition=complete "job/mysql-schema-$DEPLOYMENT_ID" --timeout=10m
kubectl -n "$NAMESPACE" rollout status statefulset/kafka --timeout=15m
kubectl -n "$NAMESPACE" rollout status statefulset/elasticsearch --timeout=15m
kubectl -n "$NAMESPACE" rollout status statefulset/redis --timeout=10m

echo "Initializing Kafka and Elasticsearch..."
apply_manifest 70-kafka-topic-job.yaml
apply_manifest 80-elasticsearch-init-job.yaml
kubectl -n "$NAMESPACE" wait --for=condition=complete "job/kafka-topic-init-$DEPLOYMENT_ID" --timeout=10m
kubectl -n "$NAMESPACE" wait --for=condition=complete "job/elasticsearch-init-$DEPLOYMENT_ID" --timeout=10m

echo "Applying application services..."
apply_manifest 40-backend.yaml
apply_manifest 45-backend-availability.yaml
apply_manifest 41-consumer.yaml
if [[ "$INSTALL_OTEL_COLLECTOR" == "true" ]]; then
  apply_manifest 46-otel-collector.yaml
fi
if [[ "$INSTALL_PROMETHEUS_RULES" == "true" ]]; then
  kubectl get namespace monitoring >/dev/null
  "$ROOT_DIR/deploy/observability/render-prometheus-rule.sh" | kubectl apply -f -
  envsubst < "$ROOT_DIR/deploy/observability/kafka-servicemonitor.yaml" | kubectl apply -f -
fi
if [[ "$INSTALL_KAFKA_ALERT_ROUTING" == "true" ]]; then
  kubectl get namespace monitoring >/dev/null
  kubectl -n monitoring create secret generic log-platform-alerting \
    --from-literal=webhook-url="$ALERT_WEBHOOK_URL" \
    --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -f "$ROOT_DIR/deploy/observability/kafka-alertmanager-config.yaml"
fi
apply_manifest 50-frontend.yaml
apply_manifest 60-ingress.yaml
if [[ "$INSTALL_FLUENT_BIT" == "true" ]]; then
  apply_manifest 90-fluent-bit.yaml
fi
kubectl -n "$NAMESPACE" rollout status deployment/log-platform-backend --timeout=10m
kubectl -n "$NAMESPACE" rollout status deployment/log-platform-consumer --timeout=10m
kubectl -n "$NAMESPACE" rollout status deployment/log-platform-frontend --timeout=10m

echo
echo "Deployment completed. Check status with:"
echo "  kubectl -n $NAMESPACE get pods,svc,ingress"
echo "  deploy/k3s/smoke-test.sh $ENV_FILE"
