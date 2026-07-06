$ErrorActionPreference = 'Continue'
$base = 'e:\git\Agent-Platform-Prototype\target\jmh-aggregate\20260706-105042'
$files = Get-ChildItem $base -Filter '*.json' | Sort-Object Name

Write-Host "=== JMH Benchmark Summary (run: 20260706-105042) ==="
Write-Host ""
$summary = @()
foreach ($f in $files) {
    $json = Get-Content $f.FullName -Raw | ConvertFrom-Json
    foreach ($entry in $json) {
        $bench = $entry.benchmark -replace 'com\.agent\..*\.perf\.',''
        $params = ($entry.params.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ', '
        $score = $entry.primaryMetric.score
        $unit = $entry.primaryMetric.scoreUnit
        $p95 = $entry.primaryMetric.scorePercentiles.'95.0'
        $summary += [PSCustomObject]@{
            Benchmark = $bench
            Params = $params
            Score = [math]::Round($score, 3)
            P95 = [math]::Round($p95, 3)
            Unit = $unit
        }
    }
}
$summary | Format-Table -AutoSize
Write-Host ""
Write-Host "Total benchmarks: $($summary.Count)"
