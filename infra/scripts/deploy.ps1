<#
.SYNOPSIS
    K8s one-click deploy for agent-platform services.
.DESCRIPTION
    1. kubectl apply namespace + SA + RBAC + ConfigMap + Vault config
    2. kubectl apply deployments + services + ingress
    3. kubectl apply HPA + PDB
    4. kubectl rollout status (wait for each deployment)
    5. Run health-check.ps1
.PARAMETER Namespace
    Target namespace. Default: agent-platform-prod
.PARAMETER ImageTag
    Image tag to patch into deployments. Default: latest.
.PARAMETER SkipHealthCheck
    Skip post-deploy health check.
.EXAMPLE
    .\deploy.ps1 -ImageTag 20260705-143000
    .\deploy.ps1 -Namespace agent-platform-staging -SkipHealthCheck
#>
param(
    [string]$Namespace = "agent-platform-prod",
    [string]$ImageTag = "latest",
    [switch]$SkipHealthCheck,
    [string]$LogFile = "./logs/deploy-$((Get-Date -Format 'yyyyMMdd-HHmmss')).log"
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$k8sDir = Split-Path -Parent $scriptRoot | Join-Path -ChildPath "k8s"
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "OK"    { Write-Host $line -ForegroundColor Cyan }
        default { Write-Host $line -ForegroundColor Green }
    }
}

function Find-Tool {
    param([string]$Name)
    $exe = (Get-Command $Name -ErrorAction SilentlyContinue).Source
    return $exe
}

$kubectl = Find-Tool "kubectl"
if (-not $kubectl) {
    Write-Log "ERROR" "kubectl not found in PATH"
    exit 1
}

Write-Log "INFO" "=== Agent Platform K8s Deploy Start ==="
Write-Log "INFO" "Namespace: $Namespace"
Write-Log "INFO" "ImageTag: $ImageTag"
Write-Log "INFO" "k8s dir: $k8sDir"
Write-Log "INFO" "Log: $LogFile"

$services = @(
    "agent-gateway","agent-session","agent-task-orchestrator","agent-memory",
    "agent-tool-engine","agent-runtime","agent-model-gateway","agent-repo",
    "agent-knowledge","agent-quality","agent-risk-control","agent-observability"
)

# ============ Step 1: Namespace + SA + ConfigMap + Vault ============
Write-Log "INFO" "[1/5] Applying namespace + RBAC + ConfigMap ..."
& kubectl apply -f "$k8sDir\00-namespace.yaml"
& kubectl apply -f "$k8sDir\01-serviceaccounts.yaml"
& kubectl apply -f "$k8sDir\02-configmap-bootstrap.yaml"
& kubectl apply -f "$k8sDir\03-vault-config.yaml"
Write-Log "OK" "Namespace + RBAC applied"

# ============ Step 2: Deployments + Services + Ingress ============
Write-Log "INFO" "[2/5] Applying deployments + services + ingress ..."
& kubectl apply -f "$k8sDir\deployments\"
& kubectl apply -f "$k8sDir\services\"
Write-Log "OK" "Deployments + services applied"

# ============ Step 3: Patch image tag ============
if ($ImageTag -ne "latest") {
    Write-Log "INFO" "[3/5] Patching image tag to $ImageTag ..."
    foreach ($svc in $services) {
        & kubectl set image deployment/${svc} ${svc}=agentplatform/${svc}:${ImageTag} -n $Namespace 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Log "OK" "Patched ${svc} -> ${ImageTag}"
        }
    }
} else {
    Write-Log "INFO" "[3/5] ImageTag=latest, skipping patch (uses deployment yaml default)"
}

# ============ Step 4: HPA + PDB ============
Write-Log "INFO" "[4/5] Applying HPA + PDB ..."
& kubectl apply -f "$k8sDir\hpa\"
& kubectl apply -f "$k8sDir\pdb\"
Write-Log "OK" "HPA + PDB applied"

# ============ Step 5: Rollout status ============
Write-Log "INFO" "[5/5] Waiting for rollout ..."
$allRolled = $true
foreach ($svc in $services) {
    Write-Log "INFO" "Rollout status: $svc"
    & kubectl rollout status deployment/${svc} -n $Namespace --timeout=300s
    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Rollout failed for ${svc}"
        $allRolled = $false
    } else {
        Write-Log "OK" "${svc} rolled out"
    }
}

if (-not $allRolled) {
    Write-Log "ERROR" "=== Deploy completed with rollout failures ==="
    exit 1
}

# ============ Health check ============
if (-not $SkipHealthCheck) {
    Write-Log "INFO" "Running post-deploy health check ..."
    & "$scriptRoot\health-check.ps1" -Namespace $Namespace
    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Health check failed, see health-check log"
        exit 1
    }
} else {
    Write-Log "INFO" "SkipHealthCheck=true, skipping health check"
}

Write-Log "OK" "=== Deploy Done ($($services.Count) services, ns=$Namespace) ==="
