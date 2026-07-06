@echo off
setlocal
set "PATH=D:\_program\maven\apache-maven-3.9.16\bin;D:\_program\jdk17.0.18-win_x64\bin;%PATH%"
set "JAVA_HOME=D:\_program\jdk17.0.18-win_x64"
set "MAVEN_OPTS=-Xmx2g -Dfile.encoding=UTF-8"

echo === Running CrossServiceProtoE2ETest (in-process gRPC, no Docker) ===
call mvn -f e:\git\Agent-Platform-Prototype\pom.xml -Pno-docker -pl agent-test-infra -Dskip.surefire.tests=false -Dtest=CrossServiceProtoE2ETest test
set "EXITCODE=%ERRORLEVEL%"
echo === Exit code: %EXITCODE% ===
endlocal & exit /b %EXITCODE%
