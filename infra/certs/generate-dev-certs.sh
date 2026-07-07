#!/bin/bash
# Generate self-signed CA + server/client certs for dev gRPC mTLS (R-05).
# Usage: bash generate-dev-certs.sh [output-dir]
# Output: ca.crt, ca.key, server.crt, server.key, client.crt, client.key, trusted-clients.crt

set -euo pipefail

OUT="${1:-.}"
mkdir -p "$OUT"

echo "Generating dev CA..."
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$OUT/ca.key" \
  -out "$OUT/ca.crt" \
  -days 3650 \
  -subj "/CN=agent-platform-dev-ca/O=AgentForge/C=CN" \
  2>/dev/null

echo "Generating server certificate..."
# SAN includes localhost + service names used in docker-compose
cat > "$OUT/server-ext.cnf" <<'EOF'
[req]
distinguished_name = req_dn
req_extensions = v3_req
prompt = no

[req_dn]
CN = agent-platform-dev-server
O = AgentForge
C = CN

[v3_req]
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = agent-gateway
DNS.3 = agent-tool-engine
DNS.4 = agent-runtime
DNS.5 = agent-risk-control
DNS.6 = agent-task-orchestrator
DNS.7 = agent-model-gateway
DNS.8 = agent-memory
DNS.9 = agent-knowledge
DNS.10 = agent-repo
DNS.11 = agent-quality
DNS.12 = agent-session
DNS.13 = agent-observability
IP.1 = 127.0.0.1
EOF

openssl req -newkey rsa:2048 -nodes \
  -keyout "$OUT/server.key" \
  -out "$OUT/server.csr" \
  -config "$OUT/server-ext.cnf" \
  2>/dev/null

openssl x509 -req -in "$OUT/server.csr" \
  -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" -CAcreateserial \
  -out "$OUT/server.crt" \
  -days 365 \
  -extfile "$OUT/server-ext.cnf" -extensions v3_req \
  2>/dev/null

echo "Generating client certificate..."
cat > "$OUT/client-ext.cnf" <<'EOF'
[req]
distinguished_name = req_dn
req_extensions = v3_req
prompt = no

[req_dn]
CN = agent-platform-dev-client
O = AgentForge
C = CN

[v3_req]
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = clientAuth
EOF

openssl req -newkey rsa:2048 -nodes \
  -keyout "$OUT/client.key" \
  -out "$OUT/client.csr" \
  -config "$OUT/client-ext.cnf" \
  2>/dev/null

openssl x509 -req -in "$OUT/client.csr" \
  -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" -CAcreateserial \
  -out "$OUT/client.crt" \
  -days 365 \
  -extfile "$OUT/client-ext.cnf" -extensions v3_req \
  2>/dev/null

# Trusted clients collection = just the client cert (or CA cert for CA-based trust)
cp "$OUT/client.crt" "$OUT/trusted-clients.crt"

# Cleanup
rm -f "$OUT/server.csr" "$OUT/client.csr" "$OUT/server-ext.cnf" "$OUT/client-ext.cnf" "$OUT/ca.srl"

echo "Dev certs generated in $OUT/:"
ls -la "$OUT"/*.crt "$OUT"/*.key
echo ""
echo "For docker-compose, mount $OUT as /app/certs in each service."
echo "Set env vars: GRPC_SERVER_CERT=/app/certs/server.crt GRPC_SERVER_KEY=/app/certs/server.key etc."
echo "For dev mode (allow unauthenticated clients): GRPC_CLIENT_AUTH=WANT"
