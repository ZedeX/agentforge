@echo off
setlocal
set "PATH=D:\_program\maven\apache-maven-3.9.16\bin;D:\_program\jdk17.0.18-win_x64\bin;%PATH%"
set "JAVA_HOME=D:\_program\jdk17.0.18-win_x64"
set "MAVEN_OPTS=-Xmx2g -Dfile.encoding=UTF-8"

echo === Compiling agent-test-infra module (Scala + Java) with -U forced update ===
call mvn -U -f e:\git\Agent-Platform-Prototype\pom.xml -Pno-docker -pl agent-test-infra -am test-compile -DskipTests
set "EXITCODE=%ERRORLEVEL%"
echo === Exit code: %EXITCODE% ===
endlocal & exit /b %EXITCODE%
