#!/usr/bin/env bash
set -Eeuo pipefail

NAMESPACE="${1:-log-platform}"
if [[ "${CONFIRM:-}" != "DELETE" ]]; then
  echo "This deletes namespace $NAMESPACE and its PVCs. Re-run with CONFIRM=DELETE to continue." >&2
  exit 1
fi
kubectl delete namespace "$NAMESPACE" --wait=true
