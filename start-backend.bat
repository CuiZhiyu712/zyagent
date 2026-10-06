@echo off
setlocal
cd /d "%~dp0"
title zyagent backend

set "MVN_CMD=%~dp0.tools\apache-maven-3.9.9\bin\mvn.cmd"
if not exist "%MVN_CMD%" (
    where mvn >nul 2>nul
    if errorlevel 1 (
        echo Maven was not found. Install Maven or restore .tools\apache-maven-3.9.9.
        exit /b 1
    )
    set "MVN_CMD=mvn"
)

if /I "%~1"=="test" goto test
if /I "%~1"=="build" goto build
if "%~1"=="" goto start
if /I "%~1"=="start" goto start

echo Usage: %~nx0 [start^|test^|build]
exit /b 2

:test
call "%MVN_CMD%" -B -f backend\pom.xml test
set "EXIT_CODE=%ERRORLEVEL%"
exit /b %EXIT_CODE%

:build
call "%MVN_CMD%" -B -f backend\pom.xml clean verify
set "EXIT_CODE=%ERRORLEVEL%"
exit /b %EXIT_CODE%

:start
echo Starting zyagent backend...
echo URL: http://127.0.0.1:8080
echo.
call "%MVN_CMD%" -f backend\pom.xml spring-boot:run
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo Backend process exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
