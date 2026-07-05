#!/usr/bin/env bash
# Initialize dev Vault with seed secrets. Run against Vault dev instance only.
# Usage: VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=agentplatform ./vault-seeds.sh
#
# WARNING: This script writes placeholder secrets. REPLACE model API keys with
# real values in production via `vault kv put secret/agent-platform/model/<provider>`.

set -euo pipefail

: "${VAULT_ADDR:?VAULT_ADDR must be set}"
: "${VAULT_TOKEN:?VAULT_TOKEN must be set}"

export VAULT_ADDR VAULT_TOKEN

POLICY_DIR="$(dirname "$0")/vault-policies"

echo "[1/8] Enabling kv-v2 at secret/ ..."
vault secrets enable -path=secret -version=2 kv || true

echo "[2/8] Writing common secrets ..."
vault kv put secret/agent-platform/common/mysql \
    username="agent_platform" password="$(openssl rand -base64 24)" > /dev/null
vault kv put secret/agent-platform/common/redis \
    password="$(openssl rand -base64 24)" > /dev/null
vault kv put secret/agent-platform/common/milvus \
    username="root" password="$(openssl rand -base64 24)" > /dev/null
vault kv put secret/agent-platform/common/neo4j \
    username="neo4j" password="$(openssl rand -base64 24)" > /dev/null
vault kv put secret/agent-platform/common/minio \
    access_key="$(openssl rand -hex 12)" secret_key="$(openssl rand -base64 24)" > /dev/null

echo "[3/8] Writing model provider API keys (REPLACE with real keys in prod) ..."
vault kv put secret/agent-platform/model/openai api_key="sk-REPLACE_ME" > /dev/null
vault kv put secret/agent-platform/model/anthropic api_key="sk-ant-REPLACE_ME" > /dev/null
vault kv put secret/agent-platform/model/qwen api_key="sk-REPLACE_ME" > /dev/null
vault kv put secret/agent-platform/model/deepseek api_key="sk-REPLACE_ME" > /dev/null

echo "[4/8] Writing JWT signing key ..."
vault kv put secret/agent-platform/service/jwt \
    signing_key="$(openssl rand -base64 48)" > /dev/null

echo "[5/8] Enabling Kubernetes auth ..."
vault auth enable kubernetes || true
vault write auth/kubernetes/config \
    kubernetes_host="https://kubernetes.default.svc:443" || true

echo "[6/8] Writing policies ..."
for svc in agent-gateway agent-session agent-task-orchestrator agent-memory \
           agent-tool-engine agent-runtime agent-model-gateway agent-repo \
           agent-knowledge agent-quality agent-risk-control agent-observability; do
    vault policy write "agent-platform-${svc}" "${POLICY_DIR}/${svc}.hcl"
done

echo "[7/8] Creating roles (binding K8s SA -> Vault role) ..."
for svc in agent-gateway agent-session agent-task-orchestrator agent-memory \
           agent-tool-engine agent-runtime agent-model-gateway agent-repo \
           agent-knowledge agent-quality agent-risk-control agent-observability; do
    vault write "auth/kubernetes/role/agent-platform-${svc}" \
        bound_service_account_names="${svc}-sa" \
        bound_service_account_namespaces="agent-platform-prod" \
        policies="agent-platform-${svc}" \
        ttl=1h
done

echo "[8/8] Done. Verify with:"
echo "  vault kv get secret/agent-platform/common/mysql"
echo "  vault read auth/kubernetes/role/agent-platform-agent-runtime"
