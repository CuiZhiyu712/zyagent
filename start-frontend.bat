@echo off
setlocal
cd /d "%~dp0frontend"
title zyagent frontend

if /I "%~1"=="test" goto test
if /I "%~1"=="build" goto build
if "%~1"=="" goto start
if /I "%~1"=="start" goto start

echo Usage: %~nx0 [start^|test^|build]
exit /b 2

:test
where node.exe >nul 2>nul
if errorlevel 1 (
    echo Node.js was not found on PATH.
    exit /b 1
)
call node.exe --test
exit /b %ERRORLEVEL%

:build
where npm.cmd >nul 2>nul
if errorlevel 1 (
    echo npm was not found on PATH.
    exit /b 1
)
call npm.cmd run build
exit /b %ERRORLEVEL%

:start
echo Starting zyagent frontend...
echo URL: http://127.0.0.1:5173
echo.
call npm.cmd run dev -- --host 127.0.0.1
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo Frontend process exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
