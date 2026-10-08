@echo off
setlocal EnableExtensions
title VOID - stop all
cd /d "%~dp0"

echo Stopping the API (port 8080) and the store (port 5173)...
for %%P in (8080 5173) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr /r /c:":%%P .*LISTENING"') do taskkill /PID %%A /T /F >nul 2>&1
)

echo Stopping database and mail catcher (data is kept)...
docker compose stop >nul 2>&1

echo Done.
timeout /t 3 >nul
