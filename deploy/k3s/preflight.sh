#!/usr/bin/env bash
set -Eeuo pipefail

command -v kubectl >/dev/null || { echo "kubectl is required" >&2; exit 1; }
kubectl cluster-info
kubectl get nodes -o wide
echo
echo "Storage classes:"
kubectl get storageclass
echo
echo "Ingress classes:"
kubectl get ingressclass 2>/dev/null || true
echo
echo "K3s checks completed. The install script also verifies the selected storage class before changing resources."
