@ECHO OFF
SETLOCAL
SET "BASE_DIR=%~dp0"
SET "DIST_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-agentic"
SET "MAVEN_HOME=%DIST_DIR%\apache-maven-3.9.16"
IF NOT EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
  IF NOT EXIST "%DIST_DIR%" MKDIR "%DIST_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path '%DIST_DIR%' 'maven.zip'; Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip' -OutFile $zip; Expand-Archive -Force $zip '%DIST_DIR%'; Remove-Item $zip"
  IF ERRORLEVEL 1 EXIT /B 1
)
CALL "%MAVEN_HOME%\bin\mvn.cmd" %*
EXIT /B %ERRORLEVEL%

