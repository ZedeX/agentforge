<#
.SYNOPSIS
  Run all JMH microbenchmarks across 5 modules and collect JSON results.

.DESCRIPTION
  Iterates over (module, benchmarkClass) tuples, invokes JMH via exec:java on
  each module with -f 0 (no fork, required because exec:java does not propagate
  classpath to forked VM), and writes per-benchmark JSON to a unified directory.

  Non-forked mode is used only because exec:java cannot supply classpath to the
  forked VM. CI should re-run with -f 1 via maven-dependency-plugin build-classpath
  + exec:exec for true isolation. See docs/tests/test-strategy.md.

.PARAMETER Wi
  Warmup iterations per measurement. Default 1 (quick local mode).

.PARAMETER I
  Measurement iterations. Default 2 (quick local mode).

.PARAMETER W
  Warmup time per iteration. Default "1s".

.PARAMETER R
  Measurement time per iteration. Default "1s".

.EXAMPLE
  .\run-jmh-benchmarks.ps1
  .\run-jmh-benchmarks.ps1 -Wi 3 -I 5
#>
[CmdletBinding()]
param(
    [int]$Wi = 1,
    [int]$I  = 2,
    [string]$W = "1s",
    [string]$R = "1s"
)

$ErrorActionPreference = "Stop"
$root = "e:\git\Agent-Platform-Prototype"
$mvn  = "D:\_program\maven\apache-maven-3.9.16\bin\mvn.cmd"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outRoot = Join-Path $root "target\jmh-aggregate\$stamp"
New-Item -ItemType Directory -Force -Path $outRoot | Out-Null
$logFile = Join-Path $outRoot "run.log"

# (module, benchmarkClassSimple) tuples — 8 benchmarks total
$benchmarks = @(
    @{ Module="agent-model-gateway";      Class="TokenCounterPerfTest" },
    @{ Module="agent-model-gateway";      Class="IntentRecognizePerfTest" },
    @{ Module="agent-model-gateway";      Class="PromptCachePerfTest" },
    @{ Module="agent-runtime";            Class="TokenCompressPerfTest" },
    @{ Module="agent-runtime";            Class="ReflexionBuildPerfTest" },
    @{ Module="agent-task-orchestrator";  Class="DagSchedulePerfTest" },
    @{ Module="agent-tool-engine";        Class="ToolInvokePerfTest" },
    @{ Module="agent-memory";             Class="MemoryRecallPerfTest" }
)

$summary = @()
foreach ($b in $benchmarks) {
    $mod   = $b.Module
    $cls   = $b.Class
    $jsonOut = Join-Path $outRoot "$mod-$cls.json"
    Write-Host "==> [$($benchmarks.IndexOf($b)+1)/$($benchmarks.Count)] $mod :: $cls" -ForegroundColor Cyan

    # Ensure module-local target/jmh-results dir exists (JMH will not create parents).
    $moduleJmhDir = Join-Path $root "$mod\target\jmh-results"
    if (-not (Test-Path $moduleJmhDir)) {
        New-Item -ItemType Directory -Force -Path $moduleJmhDir | Out-Null
    }

    # Step 1: force test-compile so JMH annotation processor generates /META-INF/BenchmarkList.
    # Without this, exec:java will fail with "Unable to find the resource: /META-INF/BenchmarkList".
    $compileArgs = @(
        '-f', (Join-Path $root 'pom.xml'),
        '-Pno-docker',
        '-pl', $mod,
        'test-compile',
        '-q'
    )
    $compileOut = & $mvn @compileArgs 2>&1
    $compileOut | Out-File -FilePath (Join-Path $outRoot "$mod-$cls.compile.log") -Encoding utf8

    # Step 2: run JMH via exec:java with -f 0 (non-forked; exec:java cannot propagate classpath to forked VM).
    $pattern = ".*$cls"
    $argList = @(
        '-f', (Join-Path $root 'pom.xml'),
        '-Pno-docker',
        '-pl', $mod,
        'exec:java',
        '-Dexec.mainClass=org.openjdk.jmh.Main',
        '-Dexec.classpathScope=test',
        "-Dexec.args=-wi $Wi -i $I -f 0 -w $W -r $R -rf json -rff $($jsonOut -replace '\\','/') $pattern"
    )

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $output = & $mvn @argList 2>&1
        $sw.Stop()
        $output | Out-File -FilePath (Join-Path $outRoot "$mod-$cls.console.log") -Encoding utf8
        $output | Out-File -FilePath $logFile -Append -Encoding utf8

        if (Test-Path $jsonOut) {
            $size = (Get-Item $jsonOut).Length
            $status = "OK"
        } else {
            $size = 0
            $status = "MISSING_JSON"
        }
    } catch {
        $sw.Stop()
        $status = "ERROR: $($_.Exception.Message)"
        $size = 0
    }

    $summary += [PSCustomObject]@{
        Module    = $mod
        Benchmark = $cls
        Status    = $status
        JsonBytes = $size
        ElapsedSec = [int]$sw.Elapsed.TotalSeconds
    }
    Write-Host "    -> $status ($([int]$sw.Elapsed.TotalSeconds)s, $size bytes)"
}

$summary | Export-Csv -Path (Join-Path $outRoot "summary.csv") -NoTypeInformation -Encoding utf8
$summary | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "`nAggregate output: $outRoot" -ForegroundColor Green
return $outRoot
