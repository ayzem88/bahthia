@echo off
chcp 65001 > nul
title Bahthia Library - Release Builder

cd /d "%~dp0"

echo.
echo ============================================================
echo   Bahthia Library - Release Builder
echo ============================================================
echo.

REM ------------------------------------------------------------
REM Find JDK 21 automatically (same logic as تشغيل.bat)
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

REM ------------------------------------------------------------
REM Read version from AppMetadata.kt
REM ------------------------------------------------------------
set "VERSION="
for /f "tokens=2 delims==" %%V in ('findstr /c:"VERSION" "modules\domain\src\main\kotlin\com\bahthia\domain\AppMetadata.kt" ^| findstr /v "DISPLAY"') do (
    set "VLINE=%%V"
)
if defined VLINE (
    REM Remove quotes and spaces
    set "VERSION=%VLINE: =%"
    set "VERSION=%VERSION:"=%"
)

if not defined VERSION (
    echo [WARN] Could not auto-detect version from AppMetadata.kt
    set /p VERSION="Type the version (example 0.5.0): "
)

echo Detected version: %VERSION%
echo.

REM ------------------------------------------------------------
REM Step 1: Run tests first (safety net)
REM ------------------------------------------------------------
echo [1/3] Running tests...
echo ------------------------------------------------------------
call gradlew.bat test --no-configuration-cache
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Tests failed. Aborting release build.
    pause
    exit /b 1
)
echo.

REM ------------------------------------------------------------
REM Step 2: Build MSI
REM ------------------------------------------------------------
echo [2/3] Building MSI installer (this may take 3 to 8 minutes)...
echo ------------------------------------------------------------
call gradlew.bat :app-desktop:packageMsi --no-configuration-cache
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] MSI build failed
    pause
    exit /b 1
)
echo.

REM ------------------------------------------------------------
REM Step 3: Locate the MSI and show next steps
REM ------------------------------------------------------------
echo [3/3] Locating MSI...
echo ------------------------------------------------------------

set "MSI_DIR=modules\app-desktop\build\compose\binaries\main\msi"
set "MSI_FILE="
for %%F in ("%MSI_DIR%\*.msi") do set "MSI_FILE=%%F"

if not defined MSI_FILE (
    echo [WARN] MSI file not found in expected directory: %MSI_DIR%
    echo Check build output above for the actual path.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESSFUL
echo ============================================================
echo.
echo MSI location: %MSI_FILE%
echo Version:      %VERSION%
echo.
echo ------------------------------------------------------------
echo   Next steps to publish (manual):
echo ------------------------------------------------------------
echo.
echo  1. Open the folder containing the MSI:
echo     explorer "%~dp0%MSI_DIR%"
echo.
echo  2. Go to GitHub Releases page (will open in browser):
echo     https://github.com/ayzem88/bahthia/releases/new
echo.
echo  3. Fill in:
echo     - Tag:    v%VERSION%
echo     - Title:  Bahthia %VERSION%
echo     - Body:   copy notes from CHANGELOG.md
echo     - Drop the MSI file in the upload area
echo     - Click "Publish release"
echo.
echo  4. Update version.json on bahthia.com (manual edit on the site repo).
echo.
echo ------------------------------------------------------------

REM Open MSI folder + GitHub Releases page in browser
explorer "%~dp0%MSI_DIR%"
start "" "https://github.com/ayzem88/bahthia/releases/new"

echo.
pause
