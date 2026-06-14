@echo off
setlocal
cd /d "%~dp0"
title zyagent backend

echo Starting zyagent backend...
echo URL: http://127.0.0.1:8080
echo.

call ".\.tools\apache-maven-3.9.9\bin\mvn.cmd" -q -f backend\pom.xml spring-boot:run

echo.
echo Backend process exited.
pause
