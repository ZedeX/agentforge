<#
.SYNOPSIS
    Deploy middleware stack to K8s via Helm.
.DESCRIPTION
    Installs 13 middleware components to agent-platform-infra namespace using Helm charts:
    mysql / redis / milvus / rocketmq / elasticsearch / neo4j / minio / nacos / vault /
    skywalking / prometheus / loki / grafana.
    Idempotent: helm upgrade --install.
.PARAMETER Namespace
    Target namespace for middleware. Default: agent-platform-infra
.PARAMETER SkipWait
    Skip waiting for pods to be ready (faster but no readiness guarantee).
.EXAMPLE
    .\deploy-middleware.ps1
    .\deploy-middleware.ps1 -SkipWait
#>
param(
    [string]$Namespace = "agent-platform-infra",
    [switch]$SkipWait,
    [string]$LogFile = "./logs/deploy-mw-$((Get-Date -Format 'yyyyMMdd-HHmmss')).log"
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
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
    param([string]$Name, [string]$FallbackPath)
    $exe = (Get-Command $Name -ErrorAction SilentlyContinue).Source
    if ($exe) { return $exe }
    if ($FallbackPath -and (Test-Path $FallbackPath)) { return $FallbackPath }
    return $null
}

$helm = Find-Tool "helm" "D:\_program\helm\helm.exe"
$kubectl = Find-Tool "kubectl"
if (-not $helm) { Write-Log "ERROR" "helm not found"; exit 1 }
if (-not $kubectl) { Write-Log "ERROR" "kubectl not found"; exit 1 }

Write-Log "INFO" "=== Middleware Deploy Start ==="
Write-Log "INFO" "Namespace: $Namespace"
Write-Log "INFO" "Helm: $helm"
Write-Log "INFO" "Log: $LogFile"

# Ensure namespace exists
& kubectl get namespace $Namespace 2>$null
if ($LASTEXITCODE -ne 0) {
    & kubectl create namespace $Namespace
    Write-Log "OK" "Created namespace: $Namespace"
}

# Add helm repos (idempotent)
$repos = @(
    @{name="bitnami"; url="https://charts.bitnami.com/bitnami"},
    @{name="milvus"; url="https://zilliztech.github.io/milvus-helm/"},
    @{name="apache"; url="https://apache.github.io/helm-charts/"},
    @{name="elastic"; url="https://helm.elastic.co/"},
    @{name="neo4j"; url="https://helm.neo4j.io/"},
    @{name="minio"; url="https://charts.min.io/"},
    @{name="nacos"; url="https://nacos-io.github.io/nacos-helm/"},
    @{name="hashicorp"; url="https://helm.releases.hashicorp.com"},
    @{name="skywalking"; url="https://apache.jfrog.io/artifactory/skywalking-helm"},
    @{name="prometheus-community"; url="https://prometheus-community.github.io/helm-charts"},
    @{name="grafana"; url="https://grafana.github.io/helm-charts"}
)

Write-Log "INFO" "[1/3] Adding helm repos ..."
foreach ($repo in $repos) {
    & helm repo add $repo.name $repo.url 2>$null
    Write-Log "OK" "Repo: $($repo.name)"
}
& helm repo update
Write-Log "OK" "Helm repos updated"

# Middleware chart definitions (chart-name, release-name, namespace)
$charts = @(
    @{chart="bitnami/mysql"; release="mysql"; chartVer="9.16.0"},
    @{chart="bitnami/redis-cluster"; release="redis"; chartVer="9.2.0"},
    @{chart="milvus/milvus"; release="milvus"; chartVer="4.2.0"},
    @{chart="apache/rocketmq"; release="rocketmq"; chartVer="0.0.2"},
    @{chart="elastic/elasticsearch"; release="elasticsearch"; chartVer="8.13.4"},
    @{chart="neo4j/neo4j"; release="neo4j"; chartVer="5.18.0"},
    @{chart="minio/minio"; release="minio"; chartVer="5.0.0"},
    @{chart="nacos/nacos"; release="nacos"; chartVer="1.0.0"},
    @{chart="hashicorp/vault"; release="vault"; chartVer="0.27.0"},
    @{chart="skywalking/skywalking"; release="skywalking"; chartVer="4.7.0"},
    @{chart="prometheus-community/prometheus"; release="prometheus"; chartVer="25.0.0"},
    @{chart="grafana/loki"; release="loki"; chartVer="6.0.0"},
    @{chart="grafana/grafana"; release="grafana"; chartVer="7.0.0"}
)

Write-Log "INFO" "[2/3] Installing $($charts.Count) middleware charts ..."
foreach ($c in $charts) {
    Write-Log "INFO" "helm upgrade --install $($c.release) $($c.chart) (ns=$Namespace)"
    & helm upgrade --install $c.release $c.chart `
        --namespace $Namespace `
        --create-namespace `
        --timeout 600s 2>&1 | ForEach-Object { Write-Log "INFO" $_ }

    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Failed: $($c.release)"
        # Continue with other charts instead of aborting
    } else {
        Write-Log "OK" "Deployed: $($c.release)"
    }
}

# Wait for pods ready
if (-not $SkipWait) {
    Write-Log "INFO" "[3/3] Waiting for all pods ready (ns=$Namespace) ..."
    & kubectl wait --for=condition=Ready pods --all -n $Namespace --timeout=600s
    if ($LASTEXITCODE -ne 0) {
        Write-Log "WARN" "Some pods not ready after 600s, check: kubectl get pods -n $Namespace"
    } else {
        Write-Log "OK" "All middleware pods ready"
    }
} else {
    Write-Log "INFO" "[3/3] SkipWait=true, skipping pod readiness wait"
}

Write-Log "OK" "=== Middleware Deploy Done ==="
Write-Log "INFO" "Verify: kubectl get pods -n $Namespace"
