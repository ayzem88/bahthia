@echo off
REM English-named alias for تشغيل.bat
chcp 65001 > nul
cd /d "%~dp0"
call "تشغيل.bat" %*
