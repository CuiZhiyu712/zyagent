@echo off
setlocal
cd /d "%~dp0frontend"
title zyagent frontend

echo Starting zyagent frontend...
echo URL: http://127.0.0.1:5173
echo.

call npm.cmd run dev -- --host 127.0.0.1

echo.
echo Frontend process exited.
pause
