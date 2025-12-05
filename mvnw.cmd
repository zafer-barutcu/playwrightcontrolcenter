@echo off
setlocal

set BASE_DIR=%~dp0
set WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

rem Maven wrapper jar kontrolü
if not exist "%WRAPPER_JAR%" (
  echo Wrapper jar bulunamadi: %WRAPPER_JAR%
  exit /b 1
)

rem JAVA_HOME kontrolü
if not defined JAVA_HOME (
  echo JAVA_HOME tanimli degil
  exit /b 1
)

rem multi-module project directory sistem property’si için yol hazırla
set MAVEN_PROJECTBASEDIR=%BASE_DIR:~0,-1%
if "%MAVEN_PROJECTBASEDIR%"=="" set MAVEN_PROJECTBASEDIR=%BASE_DIR%

set MAVEN_JAVA_EXE="%JAVA_HOME%\bin\java.exe"

%MAVEN_JAVA_EXE% -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -cp "%WRAPPER_JAR%" %WRAPPER_LAUNCHER% %*
