<#
.SYNOPSIS
    Agent Platform database initialization orchestrator.
.DESCRIPTION
    Executes all DDL scripts under infra/sql/ in order:
      1. MySQL DDL (9 databases + seed data)
      2. ClickHouse DDL (metrics tables)
      3. Milvus Collections (6 collections)
      4. Neo4j constraints + relationships
      5. Redis hot config
    Logs to infra/sql/logs/init-{timestamp}.log.
    Stops on first failure.
.PARAMETER MySqlHost
    MySQL host. Default: localhost
.PARAMETER MySqlPort
    MySQL port. Default: 3306
.PARAMETER MySqlUser
    MySQL admin user. Default: root
.PARAMETER MySqlPassword
    MySQL admin password (mandatory).
.PARAMETER ClickHouseHost
    ClickHouse host. Default: localhost
.PARAMETER ClickHousePort
    ClickHouse port. Default: 8123
.PARAMETER ClickHouseUser
    ClickHouse user. Default: default
.PARAMETER ClickHousePassword
    ClickHouse password. Default: empty
.PARAMETER MilvusHost
    Milvus host. Default: localhost
.PARAMETER MilvusPort
    Milvus port. Default: 19530
.PARAMETER MilvusUser
    Milvus user. Default: empty
.PARAMETER MilvusPassword
    Milvus password. Default: empty
.PARAMETER Neo4jHost
    Neo4j host (bolt). Default: localhost
.PARAMETER Neo4jPort
    Neo4j bolt port. Default: 7687
.PARAMETER Neo4jUser
    Neo4j user. Default: neo4j
.PARAMETER Neo4jPassword
    Neo4j password (mandatory).
.PARAMETER RedisHost
    Redis host. Default: localhost
.PARAMETER RedisPort
    Redis port. Default: 6379
.PARAMETER RedisPassword
    Redis password. Default: empty
.PARAMETER SkipClickHouse
    Skip ClickHouse init.
.PARAMETER SkipMilvus
    Skip Milvus init.
.PARAMETER SkipNeo4j
    Skip Neo4j init.
.PARAMETER SkipRedis
    Skip Redis init.
.PARAMETER SkipMySql
    Skip MySQL init.
.EXAMPLE
    .\init-all.ps1 -MySqlPassword secret -Neo4jPassword secret -RedisPassword secret
.EXAMPLE
    .\init-all.ps1 -MySqlPassword secret -SkipMilvus -SkipNeo4j
#>

param(
    [string]$MySqlHost       = "localhost",
    [int]   $MySqlPort        = 3306,
    [string]$MySqlUser        = "root",
    [Parameter(Mandatory=$true)]
    [string]$MySqlPassword,

    [string]$ClickHouseHost   = "localhost",
    [int]   $ClickHousePort   = 8123,
    [string]$ClickHouseUser   = "default",
    [string]$ClickHousePassword = "",

    [string]$MilvusHost       = "localhost",
    [int]   $MilvusPort        = 19530,
    [string]$MilvusUser        = "",
    [string]$MilvusPassword    = "",

    [string]$Neo4jHost         = "localhost",
    [int]   $Neo4jPort         = 7687,
    [string]$Neo4jUser         = "neo4j",
    [Parameter(Mandatory=$true)]
    [string]$Neo4jPassword,

    [string]$RedisHost        = "localhost",
    [int]   $RedisPort         = 6379,
    [string]$RedisPassword     = "",

    [switch]$SkipMySql,
    [switch]$SkipClickHouse,
    [switch]$SkipMilvus,
    [switch]$SkipNeo4j,
    [switch]$SkipRedis
)

$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"

# ----------------------------------------------------------------------
# Resolve paths
# ----------------------------------------------------------------------
$scriptRoot  = Split-Path -Parent $MyInvocation.MyCommand.Path
$mysqlDir    = Join-Path $scriptRoot "mysql"
$milvusDir   = Join-Path $scriptRoot "milvus"
$neo4jDir    = Join-Path $scriptRoot "neo4j"
$redisDir    = Join-Path $scriptRoot "redis"
$logDir      = Join-Path $scriptRoot "logs"

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

$timestamp  = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile    = Join-Path $logDir "init-$timestamp.log"

# ----------------------------------------------------------------------
# Logging helper
# ----------------------------------------------------------------------
function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $logFile -Value $line -Encoding UTF8
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "INFO"  { Write-Host $line -ForegroundColor Green }
        "OK"    { Write-Host $line -ForegroundColor Cyan }
        default { Write-Host $line }
    }
}

# ----------------------------------------------------------------------
# Tool locator helper
# ----------------------------------------------------------------------
function Find-Tool {
    param([string]$Name, [string]$FallbackPath)
    $exe = (Get-Command $Name -ErrorAction SilentlyContinue).Source
    if ($exe) { return $exe }
    if ($FallbackPath -and (Test-Path $FallbackPath)) { return $FallbackPath }
    return $null
}

# ----------------------------------------------------------------------
# Start
# ----------------------------------------------------------------------
Write-Log "INFO" "=== Agent Platform Init Start ==="
Write-Log "INFO" "Script root: $scriptRoot"
Write-Log "INFO" "Log file: $logFile"

# ======================================================================
# Step 1: MySQL DDL (9 databases + seed data)
# ======================================================================
if (-not $SkipMySql) {
    Write-Log "INFO" "--- Step 1: MySQL DDL ---"

    $mysqlExe = Find-Tool "mysql" "D:\_program\mariadb\bin\mysql.exe"
    if (-not $mysqlExe) {
        Write-Log "ERROR" "mysql client not found in PATH or D:\_program\mariadb\bin\"
        exit 1
    }
    Write-Log "INFO" "Using mysql: $mysqlExe"

    $mysqlScripts = @(
        "01-agent-session.sql",
        "02-agent-task.sql",
        "03-agent-memory.sql",
        "04-agent-tool.sql",
        "05-agent-model.sql",
        "06-agent-repo.sql",
        "07-agent-knowledge.sql",
        "08-agent-quality.sql",
        "09-agent-risk.sql",
        "11-seed-data.sql"
    )

    foreach ($script in $mysqlScripts) {
        $path = Join-Path $mysqlDir $script
        if (-not (Test-Path $path)) {
            Write-Log "WARN" "Skip missing script: $script"
            continue
        }
        Write-Log "INFO" "Executing: $script"
        $args = @(
            "-h", $MySqlHost,
            "-P", $MySqlPort,
            "-u", $MySqlUser,
            "-p$MySqlPassword",
            "--default-character-set=utf8mb4",
            "-e", "source $path"
        )
        $output = & $mysqlExe @args 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Log "ERROR" "Failed: $script (exit=$LASTEXITCODE)"
            Write-Log "ERROR" "Output: $output"
            exit 1
        }
        Write-Log "OK" "Done: $script"
    }
    Write-Log "INFO" "MySQL DDL completed."
} else {
    Write-Log "WARN" "Skipping MySQL DDL."
}

# ======================================================================
# Step 2: ClickHouse DDL (metrics tables)
# ======================================================================
if (-not $SkipClickHouse) {
    Write-Log "INFO" "--- Step 2: ClickHouse DDL ---"

    $chScript = Join-Path $mysqlDir "10-clickhouse-metrics.sql"
    if (Test-Path $chScript) {
        $chClient = Find-Tool "clickhouse-client" $null
        if ($chClient) {
            Write-Log "INFO" "Using clickhouse-client: $chClient"
            $chArgs = @(
                "--host=$ClickHouseHost",
                "--port=$ClickHousePort",
                "--user=$ClickHouseUser"
            )
            if ($ClickHousePassword) {
                $chArgs += "--password=$ClickHousePassword"
            }
            $chArgs += "--multiquery"
            $chArgs += "--queries-file=$chScript"

            Write-Log "INFO" "Executing: 10-clickhouse-metrics.sql"
            $output = & $chClient @chArgs 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "ERROR" "ClickHouse DDL failed (exit=$LASTEXITCODE)"
                Write-Log "ERROR" "Output: $output"
                exit 1
            }
            Write-Log "OK" "ClickHouse DDL completed."
        } else {
            Write-Log "WARN" "clickhouse-client not found in PATH. Run manually: clickhouse-client < $chScript"
        }
    } else {
        Write-Log "WARN" "ClickHouse script not found: $chScript"
    }
} else {
    Write-Log "WARN" "Skipping ClickHouse DDL."
}

# ======================================================================
# Step 3: Milvus Collections (6 collections)
# ======================================================================
if (-not $SkipMilvus) {
    Write-Log "INFO" "--- Step 3: Milvus Collections ---"

    $milvusScript = Join-Path $milvusDir "01-init-collections.py"
    if (Test-Path $milvusScript) {
        $python = Find-Tool "python" $null
        if (-not $python) {
            $python = Find-Tool "python3" $null
        }
        if ($python) {
            Write-Log "INFO" "Using python: $python"
            $pyArgs = @(
                $milvusScript,
                "--host", $MilvusHost,
                "--port", $MilvusPort
            )
            if ($MilvusUser) {
                $pyArgs += @("--user", $MilvusUser)
            }
            if ($MilvusPassword) {
                $pyArgs += @("--password", $MilvusPassword)
            }
            Write-Log "INFO" "Executing: 01-init-collections.py"
            $output = & $python @pyArgs 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "ERROR" "Milvus init failed (exit=$LASTEXITCODE)"
                Write-Log "ERROR" "Output: $output"
                exit 1
            }
            Write-Log "OK" "Milvus Collections created."
        } else {
            Write-Log "WARN" "python not found in PATH. Run manually: python $milvusScript --host $MilvusHost"
        }
    } else {
        Write-Log "WARN" "Milvus script not found: $milvusScript"
    }
} else {
    Write-Log "WARN" "Skipping Milvus init."
}

# ======================================================================
# Step 4: Neo4j constraints + relationships
# ======================================================================
if (-not $SkipNeo4j) {
    Write-Log "INFO" "--- Step 4: Neo4j Constraints + Relationships ---"

    $cypherShell = Find-Tool "cypher-shell" $null
    if ($cypherShell) {
        Write-Log "INFO" "Using cypher-shell: $cypherShell"

        $neo4jScripts = @(
            "01-init-constraints.cypher",
            "02-init-relationships.cypher"
        )

        foreach ($script in $neo4jScripts) {
            $path = Join-Path $neo4jDir $script
            if (-not (Test-Path $path)) {
                Write-Log "WARN" "Skip missing script: $script"
                continue
            }
            Write-Log "INFO" "Executing: $script"
            $cypherArgs = @(
                "-a", "bolt://$Neo4jHost`:$Neo4jPort",
                "-u", $Neo4jUser,
                "-p", $Neo4jPassword,
                "--format", "plain",
                "-f", $path
            )
            $output = & $cypherShell @cypherArgs 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Log "ERROR" "Neo4j script failed: $script (exit=$LASTEXITCODE)"
                Write-Log "ERROR" "Output: $output"
                exit 1
            }
            Write-Log "OK" "Done: $script"
        }
        Write-Log "OK" "Neo4j init completed."
    } else {
        Write-Log "WARN" "cypher-shell not found in PATH. Run manually the scripts in $neo4jDir"
    }
} else {
    Write-Log "WARN" "Skipping Neo4j init."
}

# ======================================================================
# Step 5: Redis hot config
# ======================================================================
if (-not $SkipRedis) {
    Write-Log "INFO" "--- Step 5: Redis Hot Config ---"

    $redisScript = Join-Path $redisDir "01-init-data.redis"
    if (Test-Path $redisScript) {
        $redisCli = Find-Tool "redis-cli" $null
        if ($redisCli) {
            Write-Log "INFO" "Using redis-cli: $redisCli"
            $redisArgs = @(
                "-h", $RedisHost,
                "-p", $RedisPort
            )
            if ($RedisPassword) {
                $redisArgs += @("-a", $RedisPassword, "--no-auth-warning")
            }
            Write-Log "INFO" "Executing: 01-init-data.redis"
            # Pipe the redis script file into redis-cli
            $output = Get-Content $redisScript -Raw | & $redisCli @redisArgs --pipe 2>&1
            if ($LASTEXITCODE -ne 0) {
                # Retry with line-by-line mode if --pipe fails
                Write-Log "WARN" "Pipe mode failed, retrying line-by-line..."
                $lines = Get-Content $redisScript | Where-Object { $_ -and -not $_.StartsWith("--") }
                foreach ($line in $lines) {
                    & $redisCli @redisArgs $line 2>&1 | Out-Null
                }
            }
            Write-Log "OK" "Redis hot config loaded."
        } else {
            Write-Log "WARN" "redis-cli not found in PATH. Run manually: redis-cli -h $RedisHost -p $RedisPort < $redisScript"
        }
    } else {
        Write-Log "WARN" "Redis script not found: $redisScript"
    }
} else {
    Write-Log "WARN" "Skipping Redis init."
}

# ----------------------------------------------------------------------
# Summary
# ----------------------------------------------------------------------
Write-Log "INFO" "=== Agent Platform Init Summary ==="
Write-Log "INFO" "MySQL:      $(if($SkipMySql){'SKIPPED'}else{'DONE'})"
Write-Log "INFO" "ClickHouse: $(if($SkipClickHouse){'SKIPPED'}else{'DONE'})"
Write-Log "INFO" "Milvus:     $(if($SkipMilvus){'SKIPPED'}else{'DONE'})"
Write-Log "INFO" "Neo4j:      $(if($SkipNeo4j){'SKIPPED'}else{'DONE'})"
Write-Log "INFO" "Redis:      $(if($SkipRedis){'SKIPPED'}else{'DONE'})"
Write-Log "INFO" "Log: $logFile"
Write-Log "INFO" "=== Agent Platform Init Done ==="
