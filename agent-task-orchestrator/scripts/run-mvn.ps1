# Helper script: run maven with clean PATH (avoid noise from invalid PATH entries).
# Usage: pwsh -File run-mvn.ps1 <mvn args...>
$env:JAVA_HOME = 'D:\_program\jdk17.0.18-win_x64'
$env:Path = "$env:JAVA_HOME\bin;D:\_program\maven\apache-maven-3.9.16\bin;C:\Windows\System32;C:\Windows\System32\Wbem"
$ErrorActionPreference = 'Continue'
& 'D:\_program\maven\apache-maven-3.9.16\bin\mvn.cmd' @args
exit $LASTEXITCODE
