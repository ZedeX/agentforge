@echo off
setlocal
set "PATH=D:\_program\maven\apache-maven-3.9.16\bin;D:\_program\jdk17.0.18-win_x64\bin;%PATH%"
set "JAVA_HOME=D:\_program\jdk17.0.18-win_x64"
set "MAVEN_OPTS=-Xmx2g -Dfile.encoding=UTF-8"

echo === Verifying Testcontainers tests auto-skip without Docker ===
echo === Running AgentRepoJpaTestcontainersTest (expect: skipped, no Docker) ===
call mvn -f e:\git\Agent-Platform-Prototype\pom.xml -pl agent-repo -am -Dtest=AgentRepoJpaTestcontainersTest test -Dsurefire.failIfNoSpecifiedTests=false
set "EXITCODE1=%ERRORLEVEL%"
echo === AgentRepo Exit code: %EXITCODE1% ===

echo.
echo === Running MemoryJpaTestcontainersTest (expect: skipped, no Docker) ===
call mvn -f e:\git\Agent-Platform-Prototype\pom.xml -pl agent-memory -am -Dtest=MemoryJpaTestcontainersTest test -Dsurefire.failIfNoSpecifiedTests=false
set "EXITCODE2=%ERRORLEVEL%"
echo === Memory Exit code: %EXITCODE2% ===

echo.
echo === Running ToolEngineJpaTestcontainersTest (expect: skipped, no Docker) ===
call mvn -f e:\git\Agent-Platform-Prototype\pom.xml -pl agent-tool-engine -am -Dtest=ToolEngineJpaTestcontainersTest test -Dsurefire.failIfNoSpecifiedTests=false
set "EXITCODE3=%ERRORLEVEL%"
echo === ToolEngine Exit code: %EXITCODE3% ===

echo.
echo === Running KnowledgeJpaTestcontainersTest (expect: skipped, no Docker) ===
call mvn -f e:\git\Agent-Platform-Prototype\pom.xml -pl agent-knowledge -am -Dtest=KnowledgeJpaTestcontainersTest test -Dsurefire.failIfNoSpecifiedTests=false
set "EXITCODE4=%ERRORLEVEL%"
echo === Knowledge Exit code: %EXITCODE4% ===

echo.
echo === Running TaskDagTestcontainersTest (expect: skipped, no Docker) ===
call mvn -f e:\git\Agent-Platform-Prototype\pom.xml -pl agent-task-orchestrator -am -Dtest=TaskDagTestcontainersTest test -Dsurefire.failIfNoSpecifiedTests=false
set "EXITCODE5=%ERRORLEVEL%"
echo === TaskOrchestrator Exit code: %EXITCODE5% ===

echo.
echo === All Testcontainers skip verification complete ===
endlocal & exit /b 0
