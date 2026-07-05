<#
.SYNOPSIS
    Agent Platform full build: Maven compile + Docker images.
.DESCRIPTION
    1. Run maven clean package (skip tests optional).
    2. Build Docker image per service using infra/docker/Dockerfile.template + --build-arg SERVICE_NAME.
    3. Optionally push to registry.
.PARAMETER SkipTests
    Skip unit tests.
.PARAMETER SkipImage
    Skip Docker image build.
.PARAMETER PushImage
    Push built images to registry (requires -Registry).
.PARAMETER Registry
    Docker registry URL (e.g. registry.cn-hangzhou.aliyuncs.com/agent-platform). If empty, images stay local.
.PARAMETER Services
    Comma-separated service list. Default: all 12.
.PARAMETER ImageTag
    Image tag. Default: yyyyMMdd-HHmmss.
.EXAMPLE
    .\build-all.ps1 -SkipTests
    .\build-all.ps1 -SkipTests -PushImage -Registry registry.cn-hangzhou.aliyuncs.com/agent-platform
#>
param(
    [switch]$SkipTests,
    [switch]$SkipImage,
    [switch]$PushImage,
    [string]$Registry = "",
    [string]$Services = "all",
    [string]$ImageTag = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [string]$LogFile = "./logs/build-$((Get-Date -Format 'yyyyMMdd-HHmmss')).log"
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptRoot)
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

$allServices = @(
    "agent-gateway","agent-session","agent-task-orchestrator","agent-memory",
    "agent-tool-engine","agent-runtime","agent-model-gateway","agent-repo",
    "agent-knowledge","agent-quality","agent-risk-control","agent-observability"
)

if ($Services -ne "all") {
    $buildServices = $Services -split "," | ForEach-Object { $_.Trim() }
} else {
    $buildServices = $allServices
}

Write-Log "INFO" "=== Agent Platform Build Start ==="
Write-Log "INFO" "Project root: $projectRoot"
Write-Log "INFO" "Image tag: $ImageTag"
Write-Log "INFO" "Services: $($buildServices.Count) - $($buildServices -join ', ')"
Write-Log "INFO" "SkipTests: $SkipTests, SkipImage: $SkipImage, PushImage: $PushImage"
Write-Log "INFO" "Log: $LogFile"

# ============ Step 1: Maven build ============
$mvn = Find-Tool "mvn" "D:\_program\maven\apache-maven-3.9.16\bin\mvn.cmd"
if (-not $mvn) {
    Write-Log "ERROR" "Maven not found in PATH nor D:\_program\maven\"
    exit 1
}
Write-Log "INFO" "Maven: $mvn"

$mvnArgs = @("clean", "package", "-B", "-q")
if ($SkipTests) { $mvnArgs += "-DskipTests" }
$moduleList = $buildServices -join ","
$mvnArgs += "-pl", "agent-common,agent-proto,$moduleList", "-am"

Write-Log "INFO" "Running: mvn $($mvnArgs -join ' ')"
Push-Location $projectRoot
try {
    & $mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Maven build failed (exit $LASTEXITCODE)"
        exit 1
    }
    Write-Log "OK" "Maven build succeeded"
} finally {
    Pop-Location
}

if ($SkipImage) {
    Write-Log "INFO" "SkipImage=true, skipping Docker build"
    Write-Log "OK" "=== Build Done (Maven only) ==="
    exit 0
}

# ============ Step 2: Docker image build ============
$docker = Find-Tool "docker"
if (-not $docker) {
    Write-Log "ERROR" "Docker not found in PATH"
    exit 1
}
Write-Log "INFO" "Docker: $docker"

# Build base image first if not present
Write-Log "INFO" "Checking base image agentplatform/base:17-jre-skywalking-9.7 ..."
$baseCheck = docker image inspect agentplatform/base:17-jre-skywalking-9.7 2>$null
if (-not $baseCheck) {
    Write-Log "WARN" "Base image not found, building from infra/docker/Dockerfile.base ..."
    & docker build -t agentplatform/base:17-jre-skywalking-9.7 `
        -f "$projectRoot\infra\docker\Dockerfile.base" `
        "$projectRoot\infra\docker"
    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Base image build failed"
        exit 1
    }
    Write-Log "OK" "Base image built"
} else {
    Write-Log "OK" "Base image exists"
}

# Build each service image via Dockerfile.template
foreach ($svc in $buildServices) {
    $imageName = if ($Registry) { "$Registry/${svc}:$ImageTag" } else { "agentplatform/${svc}:$ImageTag" }
    Write-Log "INFO" "Building image: $imageName"

    & docker build -t $imageName `
        -f "$projectRoot\infra\docker\Dockerfile.template" `
        --build-arg "SERVICE_NAME=${svc}" `
        $projectRoot

    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Image build failed for ${svc}"
        exit 1
    }
    Write-Log "OK" "Built: $imageName"
}

# ============ Step 3: Push to registry (optional) ============
if ($PushImage -and $Registry) {
    Write-Log "INFO" "Pushing $($buildServices.Count) images to $Registry ..."
    foreach ($svc in $buildServices) {
        $imageName = "$Registry/${svc}:$ImageTag"
        Write-Log "INFO" "Pushing: $imageName"
        & docker push $imageName
        if ($LASTEXITCODE -ne 0) {
            Write-Log "ERROR" "Push failed for $imageName"
            exit 1
        }
        Write-Log "OK" "Pushed: $imageName"
    }
} elseif ($PushImage -and -not $Registry) {
    Write-Log "WARN" "PushImage requested but -Registry not set, skipping push"
}

Write-Log "OK" "=== Build Done ($($buildServices.Count) services, tag=$ImageTag) ==="
Write-Log "INFO" "Next: .\deploy.ps1 -ImageTag $ImageTag"
