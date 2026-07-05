# Verify PowerShell + YAML syntax for all infra files.
$ErrorActionPreference = "Continue"

$scripts = @(
    "e:\git\Agent-Platform-Prototype\infra\scripts\build-all.ps1",
    "e:\git\Agent-Platform-Prototype\infra\scripts\deploy.ps1",
    "e:\git\Agent-Platform-Prototype\infra\scripts\deploy-middleware.ps1",
    "e:\git\Agent-Platform-Prototype\infra\scripts\health-check.ps1",
    "e:\git\Agent-Platform-Prototype\infra\nacos\import-nacos.ps1",
    "e:\git\Agent-Platform-Prototype\infra\docker\generate-dockerfiles.ps1",
    "e:\git\Agent-Platform-Prototype\infra\vault\generate-policies.ps1",
    "e:\git\Agent-Platform-Prototype\infra\k8s\generate-deployments.ps1"
)

Write-Host "=== PowerShell syntax check ===" -ForegroundColor Cyan
$pwshOk = 0; $pwshFail = 0
foreach ($s in $scripts) {
    $tokens = $null; $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($s, [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count -eq 0) {
        Write-Host "OK: $(Split-Path -Leaf $s)" -ForegroundColor Green
        $pwshOk++
    } else {
        Write-Host "FAIL: $(Split-Path -Leaf $s)" -ForegroundColor Red
        $errors | ForEach-Object { Write-Host "  $($_.Extent.StartLineNumber):$($_.Extent.StartColumnNumber) - $($_.Message)" -ForegroundColor Yellow }
        $pwshFail++
    }
}
Write-Host "PowerShell: $pwshOk OK, $pwshFail FAIL`n" -ForegroundColor Cyan

# YAML syntax check (use kubectl if available, else use python yaml)
$kubectl = Get-Command kubectl -ErrorAction SilentlyContinue
$python = Get-Command python -ErrorAction SilentlyContinue
$yamlFiles = Get-ChildItem "e:\git\Agent-Platform-Prototype\infra" -Recurse -Include "*.yaml","*.yml" | Where-Object { $_.FullName -notmatch "generate-" }

Write-Host "=== YAML syntax check ($($yamlFiles.Count) files) ===" -ForegroundColor Cyan
$ymlOk = 0; $ymlFail = 0
foreach ($f in $yamlFiles) {
    if ($kubectl) {
        $result = & kubectl apply --dry-run=client -f $f.FullName 2>&1
        # kubectl returns nonzero for namespace already exists etc, but yaml syntax errors are reported
        if ($LASTEXITCODE -eq 0 -or $result -match "configured|created|unchanged|already exists") {
            Write-Host "OK: $($f.FullName.Replace('e:\git\Agent-Platform-Prototype\infra\',''))" -ForegroundColor Green
            $ymlOk++
        } else {
            Write-Host "FAIL: $($f.Name) - $result" -ForegroundColor Red
            $ymlFail++
        }
    } elseif ($python) {
        $result = & python -c "import yaml,sys; yaml.safe_load(open(sys.argv[1],encoding='utf-8'))" $f.FullName 2>&1
        if ($LASTEXITCODE -eq 0) {
            $ymlOk++
        } else {
            Write-Host "FAIL: $($f.Name) - $result" -ForegroundColor Red
            $ymlFail++
        }
    } else {
        Write-Host "SKIP: no kubectl/python available for yaml validation" -ForegroundColor Yellow
        break
    }
}
Write-Host "YAML: $ymlOk OK, $ymlFail FAIL" -ForegroundColor Cyan

# JSON syntax check
$jsonFiles = Get-ChildItem "e:\git\Agent-Platform-Prototype\infra" -Recurse -Include "*.json"
Write-Host "`n=== JSON syntax check ($($jsonFiles.Count) files) ===" -ForegroundColor Cyan
$jsonOk = 0; $jsonFail = 0
foreach ($f in $jsonFiles) {
    try {
        Get-Content $f.FullName -Raw | ConvertFrom-Json | Out-Null
        $jsonOk++
    } catch {
        Write-Host "FAIL: $($f.Name) - $($_.Exception.Message)" -ForegroundColor Red
        $jsonFail++
    }
}
Write-Host "JSON: $jsonOk OK, $jsonFail FAIL" -ForegroundColor Cyan

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
Write-Host "PowerShell: $pwshOk/$($scripts.Count) OK"
Write-Host "YAML: $ymlOk/$($yamlFiles.Count) OK"
Write-Host "JSON: $jsonOk/$($jsonFiles.Count) OK"
