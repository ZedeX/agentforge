# Policy for agent-model-gateway service account.
# Grants read access to common secrets (MySQL/Redis/Milvus) + JWT signing key.
# Used by: agent-model-gateway-sa (bound via vault-seeds.sh)

path "secret/data/agent-platform/common/mysql" {
  capabilities = ["read"]
}

path "secret/data/agent-platform/common/redis" {
  capabilities = ["read"]
}

path "secret/data/agent-platform/common/milvus" {
  capabilities = ["read"]
}

# agent-runtime is stateless; only needs session-level secrets
path "secret/data/agent-platform/service/jwt" {
  capabilities = ["read"]
}

# agent-model-gateway additionally needs model provider API keys
path "secret/data/agent-platform/model/openai" {
  capabilities = ["read"]
}

path "secret/data/agent-platform/model/anthropic" {
  capabilities = ["read"]
}

path "secret/data/agent-platform/model/qwen" {
  capabilities = ["read"]
}

path "secret/data/agent-platform/model/deepseek" {
  capabilities = ["read"]
}