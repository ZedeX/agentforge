# Generate 11 remaining Vault policies from agent-runtime.hcl template.
# agent-model-gateway.hcl gets additional model/* paths (model provider API keys).
# Run: pwsh -File generate-policies.ps1

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$dir = Join-Path $scriptDir "vault-policies"
$template = Get-Content (Join-Path $dir "agent-runtime.hcl") -Raw

$services = @(
    "agent-gateway","agent-session","agent-task-orchestrator","agent-memory",
    "agent-tool-engine","agent-model-gateway","agent-repo","agent-knowledge",
    "agent-quality","agent-risk-control","agent-observability"
)

foreach ($svc in $services) {
    # Replace only the comment header to keep policy paths identical (all services share common/* + jwt)
    $policy = $template -replace "agent-runtime service account", "$svc service account"
    $policy = $policy -replace "Used by: agent-runtime-sa", "Used by: ${svc}-sa"
    $path = Join-Path $dir "$svc.hcl"
    Set-Content -Path $path -Value $policy -Encoding UTF8 -NoNewline
    Write-Host "Generated: $path"
}

# Patch agent-model-gateway.hcl with additional model/* permissions
$modelPath = Join-Path $dir "agent-model-gateway.hcl"
$modelPolicy = Get-Content $modelPath -Raw
$modelExtras = @"

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
"@
Set-Content -Path $modelPath -Value ($modelPolicy + $modelExtras) -Encoding UTF8 -NoNewline
Write-Host "Patched: $modelPath (added model/* paths)"
