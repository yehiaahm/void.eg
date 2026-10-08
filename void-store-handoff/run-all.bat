@echo off
setlocal EnableExtensions
title VOID - run all
cd /d "%~dp0"

echo.
echo  ==========================================
echo    VOID store - starting everything
echo  ==========================================
echo.

rem ---------------------------------------------------------------- prerequisites
where docker >nul 2>&1 || goto :nodocker
where java   >nul 2>&1 || goto :nojava
where node   >nul 2>&1 || goto :nonode

rem ---------------------------------------------------------------- 1. Docker Desktop
docker info >nul 2>&1
if not errorlevel 1 goto :dockerok
echo [1/5] Starting Docker Desktop...
if not exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" goto :nodockerapp
start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
set /a tries=0
:waitdocker
timeout /t 3 /nobreak >nul
docker info >nul 2>&1
if not errorlevel 1 goto :dockerok
set /a tries+=1
if %tries% geq 60 goto :dockertimeout
goto :waitdocker
:dockerok
echo [1/5] Docker is running.

rem ---------------------------------------------------------------- 2. MySQL + Mailpit
echo [2/5] Starting database and mail catcher...
docker compose up -d --wait
if errorlevel 1 goto :composefail

rem ---------------------------------------------------------------- 3. local config
if exist "backend\local.properties" goto :haveconfig
echo [3/5] Creating backend\local.properties with a new owner password...
powershell -NoProfile -Command "$pw = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 16 | ForEach-Object {[char]$_}); $sec = [Convert]::ToBase64String([byte[]](1..48 | ForEach-Object { Get-Random -Maximum 256 })); Set-Content -Encoding ascii -Path 'backend\local.properties' -Value @('# Local development overrides - NOT committed', 'void.bootstrap.owner-email=owner@void.local', ('void.bootstrap.owner-password=' + $pw), ('void.jwt.secret=' + $sec))"
goto :configdone
:haveconfig
echo [3/5] Using existing backend\local.properties.
:configdone

rem ---------------------------------------------------------------- 4. API (Spring Boot)
netstat -ano | findstr /r /c:":8080 .*LISTENING" >nul
if not errorlevel 1 goto :apirunning
echo [4/5] Starting the API in a new window...
start "VOID API (port 8080)" /d "%~dp0backend" cmd /k mvnw.cmd spring-boot:run
goto :apidone
:apirunning
echo [4/5] API already running on port 8080.
:apidone

rem ---------------------------------------------------------------- 5. Store (Vite)
if exist "frontend\node_modules" goto :havemodules
echo [5/5] Installing frontend packages - first run only, takes a minute...
pushd frontend
call npm install --no-audit --no-fund
popd
:havemodules
netstat -ano | findstr /r /c:":5173 .*LISTENING" >nul
if not errorlevel 1 goto :storerunning
echo [5/5] Starting the store in a new window...
start "VOID Store (port 5173)" /d "%~dp0frontend" cmd /k npm run dev
goto :storedone
:storerunning
echo [5/5] Store already running on port 5173.
:storedone

rem ---------------------------------------------------------------- wait + open
echo.
echo Waiting for the API to be ready - the first start can take a minute...
set /a tries=0
:waitapi
curl.exe -sf -o nul -m 2 http://localhost:8080/actuator/health && goto :apiready
set /a tries+=1
if %tries% geq 90 goto :apislow
timeout /t 2 /nobreak >nul
goto :waitapi
:apislow
echo [!] The API is not answering yet - check the VOID API window for errors.
goto :openbrowser
:apiready
echo API is ready.
:openbrowser

if not defined VOID_NO_BROWSER start "" http://localhost:5173/en
if not defined VOID_NO_BROWSER start "" http://localhost:5173/admin

echo.
echo  ==========================================
echo    Store:  http://localhost:5173
echo    Admin:  http://localhost:5173/admin
echo    Emails: http://localhost:8025
echo.
echo    Admin sign-in: owner@void.local
echo    Password: see backend\local.properties
echo.
echo    To stop everything: run stop-all.bat
echo  ==========================================
echo.
pause
exit /b 0

rem ---------------------------------------------------------------- errors
:nodocker
echo [X] Docker is not installed. Install Docker Desktop: https://www.docker.com/products/docker-desktop
goto :fail
:nojava
echo [X] Java is not installed. Install Java 21: https://adoptium.net
goto :fail
:nonode
echo [X] Node.js is not installed. Install Node 20 or newer: https://nodejs.org
goto :fail
:nodockerapp
echo [X] Docker Desktop was not found. Open it manually, then run this file again.
goto :fail
:dockertimeout
echo [X] Docker did not start within 3 minutes. Open Docker Desktop manually and try again.
goto :fail
:composefail
echo [X] Could not start the database containers. Is port 3307 or 8025 used by another program?
goto :fail
:fail
echo.
pause
exit /b 1
