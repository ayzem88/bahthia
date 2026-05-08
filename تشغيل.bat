@echo off
chcp 65001 > nul
title Bahthia Library - المكتبة البحثيّة

cd /d "%~dp0"

echo.
echo ============================================================
echo   Bahthia Library - المكتبة البحثيّة
echo ============================================================
echo.

REM ------------------------------------------------------------
REM Find JDK 21 automatically
REM ------------------------------------------------------------
set "JAVA_HOME="
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
)
if not defined JAVA_HOME (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%D"
)
if not defined JAVA_HOME (
    for /d %%D in ("C:\Program Files\Java\jdk-21*") do set "JAVA_HOME=%%D"
)

if not defined JAVA_HOME (
    echo [ERROR] JDK 21 not found
    echo Download from: https://adoptium.net/temurin/
    echo.
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using JDK: %JAVA_HOME%
echo.
echo Starting application... (first launch may take 2-5 minutes)
echo.

REM Run gradle to launch the app
call gradlew.bat :app-desktop:run --no-configuration-cache

set EXIT_CODE=%ERRORLEVEL%
echo.
if %EXIT_CODE% NEQ 0 (
    echo ============================================================
    echo [ERROR] Application exited with error code %EXIT_CODE%
    echo ============================================================
    pause
) else (
    echo ============================================================
    echo Application closed normally.
    echo ============================================================
    timeout /t 3 > nul
)
