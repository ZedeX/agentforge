<#
.SYNOPSIS
    Batch import Nacos configurations from infra/nacos/.
.DESCRIPTION
    Reads all .yml files under shared/ and services/ and POSTs them to Nacos config API.
    Supports namespace selection (dev/staging/prod) via -Environment.
.PARAMETER NacosHost
    Nacos host. Default: localhost
.PARAMETER NacosPort
    Nacos port. Default: 8848
.PARAMETER NacosUser
    Nacos username. Default: nacos
.PARAMETER NacosPassword
    Nacos password (mandatory).
.PARAMETER Environment
    Target namespace suffix: dev | staging | prod
.EXAMPLE
    .\import-nacos.ps1 -Environment prod -NacosPassword nacos
#>
param(
    [string]$NacosHost = "localhost",
    [int]$NacosPort = 8848,
    [string]$NacosUser = "nacos",
    [Parameter(Mandatory=$true)]
    [string]$NacosPassword,
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev","staging","prod")]
    [string]$Environment
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$logDir = Join-Path $scriptRoot "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$logFile = Join-Path $logDir "import-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $logFile -Value $line -Encoding UTF8
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "OK"    { Write-Host $line -ForegroundColor Cyan }
        default { Write-Host $line -ForegroundColor Green }
    }
}

# Step 1: Login to get access token
Write-Log "INFO" "Logging in to Nacos as $NacosUser ..."
$loginBody = @{ username = $NacosUser; password = $NacosPassword }
$loginResp = Invoke-RestMethod -Uri "http://${NacosHost}:${NacosPort}/nacos/v1/auth/login" -Method Post -Body $loginBody
$accessToken = $loginResp.accessToken
if (-not $accessToken) { Write-Log "ERROR" "Login failed"; exit 1 }
Write-Log "OK" "Login OK"

$namespace = "agent-platform-$Environment"

# Step 2: Import shared configs (COMMON_GROUP)
$sharedDir = Join-Path $scriptRoot "shared"
$sharedFiles = Get-ChildItem -Path $sharedDir -Filter "*.yml"
foreach ($f in $sharedFiles) {
    $dataId = $f.Name
    $group = "COMMON_GROUP"
    $content = Get-Content $f.FullName -Raw
    $body = @{
        dataId = $dataId
        group = $group
        tenant = $namespace
        type = "yaml"
        content = $content
    }
    $url = "http://${NacosHost}:${NacosPort}/nacos/v1/cs/configs?accessToken=$accessToken"
    try {
        Invoke-RestMethod -Uri $url -Method Post -Body $body -ErrorAction Stop | Out-Null
        Write-Log "OK" "Imported shared/$dataId -> $namespace / $group"
    } catch {
        Write-Log "ERROR" "Failed shared/$dataId : $($_.Exception.Message)"
    }
}

# Step 3: Import service configs (SERVICE_GROUP)
$svcDir = Join-Path $scriptRoot "services"
$svcFiles = Get-ChildItem -Path $svcDir -Filter "*-$Environment.yml" -ErrorAction SilentlyContinue
foreach ($f in $svcFiles) {
    $dataId = $f.Name
    $group = "SERVICE_GROUP"
    $content = Get-Content $f.FullName -Raw
    $body = @{
        dataId = $dataId
        group = $group
        tenant = $namespace
        type = "yaml"
        content = $content
    }
    $url = "http://${NacosHost}:${NacosPort}/nacos/v1/cs/configs?accessToken=$accessToken"
    try {
        Invoke-RestMethod -Uri $url -Method Post -Body $body -ErrorAction Stop | Out-Null
        Write-Log "OK" "Imported services/$dataId -> $namespace / $group"
    } catch {
        Write-Log "ERROR" "Failed services/$dataId : $($_.Exception.Message)"
    }
}

Write-Log "INFO" "Import done. Log: $logFile"
