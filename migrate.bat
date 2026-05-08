@echo off
REM ============================================================
REM   Migrate Python data → Kotlin Lucene index
REM ============================================================
chcp 65001 > nul
title Bahthia Migration

cd /d "%~dp0"

REM Find JDK 21
set "JAVA_HOME="
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
)
if not defined JAVA_HOME (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%D"
)

if not defined JAVA_HOME (
    echo [ERROR] JDK 21 not found.
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM Defaults — change these to suit your setup
set "SHARDS_DIR=E:\البرامج والتطبيقات\المكتبة البحثية\data\shards"
set "OUTPUT_DIR=%APPDATA%\Bahthia\lucene-index"

echo.
echo ============================================================
echo   Bahthia Library — Data Migration
echo ============================================================
echo.
echo Source : %SHARDS_DIR%
echo Target : %OUTPUT_DIR%
echo.
echo Press any key to begin migration (or Ctrl+C to cancel)...
pause > nul

call gradlew.bat :importer:migrate --args="\"%SHARDS_DIR%\" \"%OUTPUT_DIR%\"" --no-configuration-cache

echo.
echo ============================================================
echo Migration finished.
echo ============================================================
pause
