# Policy for agent-session service account.
# Grants read access to common secrets (MySQL/Redis/Milvus) + JWT signing key.
# Used by: agent-session-sa (bound via vault-seeds.sh)

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
