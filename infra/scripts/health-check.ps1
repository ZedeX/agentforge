<#
.SYNOPSIS
    Post-deploy health check for all agent-platform services.
.DESCRIPTION
    Polls /actuator/health of each service via kubectl exec (no port-forward needed).
    Fails if any service is not UP within timeout.
.PARAMETER Namespace
    K8s namespace. Default: agent-platform-prod
.PARAMETER TimeoutSeconds
    Per-service timeout. Default: 120
.PARAMETER LogFile
    Log file path. Default: ./logs/health-check-<timestamp>.log
.EXAMPLE
    .\health-check.ps1 -Namespace agent-platform-prod
#>
param(
    [string]$Namespace = "agent-platform-prod",
    [int]$TimeoutSeconds = 120,
    [string]$LogFile = "./logs/health-check-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
)

$ErrorActionPreference = "Stop"
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "OK"    { Write-Host $line -ForegroundColor Cyan }
        default { Write-Host $line -ForegroundColor Green }
    }
}

# 12 deployable services (agent-planning merged into agent-task-orchestrator)
$services = @(
    @{name="agent-gateway"; port=8080},
    @{name="agent-session"; port=8082},
    @{name="agent-task-orchestrator"; port=8084},
    @{name="agent-memory"; port=8088},
    @{name="agent-tool-engine"; port=8090},
    @{name="agent-runtime"; port=8092},
    @{name="agent-model-gateway"; port=8094},
    @{name="agent-repo"; port=8096},
    @{name="agent-knowledge"; port=8098},
    @{name="agent-quality"; port=8100},
    @{name="agent-risk-control"; port=8102},
    @{name="agent-observability"; port=8104}
)

Write-Log "INFO" "=== Health Check Start (ns=$Namespace) ==="
Write-Log "INFO" "Services: $($services.Count), timeout: ${TimeoutSeconds}s per service"

$allHealthy = $true
foreach ($svc in $services) {
    $svcName = $svc.name
    $svcPort = $svc.port
    Write-Log "INFO" "Checking $svcName (port $svcPort) ..."

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $healthy = $false
    while ((Get-Date) -lt $deadline) {
        # Use kubectl exec to curl from inside the pod (no port-forward needed)
        $pod = kubectl get pods -n $Namespace -l app=$svcName -o jsonpath='{.items[0].metadata.name}' 2>$null
        if (-not $pod) {
            Start-Sleep -Seconds 5
            continue
        }
        $result = kubectl exec $pod -n $Namespace -- curl -fs -m 5 http://localhost:$svcPort/actuator/health 2>$null
        if ($result -match '"status":"UP"') {
            $healthy = $true
            break
        }
        Start-Sleep -Seconds 5
    }

    if ($healthy) {
        Write-Log "OK" "$svcName healthy"
    } else {
        Write-Log "ERROR" "$svcName NOT healthy within ${TimeoutSeconds}s"
        $allHealthy = $false
    }
}

if ($allHealthy) {
    Write-Log "OK" "=== All $($services.Count) services healthy ==="
    exit 0
} else {
    Write-Log "ERROR" "=== Some services unhealthy, see log: $LogFile ==="
    exit 1
}
