@echo off
REM Run agent-memory JMH benchmark in isolation, redirect all output to file to avoid PowerShell CLR crash.
REM UTF-8 codepage for JMH console output.
chcp 65001 >nul
set MVN=D:\_program\maven\apache-maven-3.9.16\bin\mvn.cmd
set ROOT=e:\git\Agent-Platform-Prototype
set OUT=%ROOT%\target\jmh-aggregate\memory-standalone.json
set LOG=%ROOT%\target\jmh-aggregate\memory-standalone.console.log
if not exist "%ROOT%\agent-memory\target\jmh-results" mkdir "%ROOT%\agent-memory\target\jmh-results"

call %MVN% -f "%ROOT%\pom.xml" -Pno-docker -pl agent-memory exec:java ^
  -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  "-Dexec.args=-wi 1 -i 2 -f 0 -w 1s -r 1s -rf json -rff e:/git/Agent-Platform-Prototype/agent-memory/target/jmh-results/memory.json .*MemoryRecallPerfTest" ^
  > "%LOG%" 2>&1

REM Copy result to aggregate output location
if exist "%ROOT%\agent-memory\target\jmh-results\memory.json" (
  copy /Y "%ROOT%\agent-memory\target\jmh-results\memory.json" "%OUT%" >nul
  echo RESULT_OK %OUT%
) else (
  echo RESULT_MISSING
)
