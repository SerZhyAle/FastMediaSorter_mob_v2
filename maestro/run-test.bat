@echo off
REM Maestro Test Runner - Windows Batch Script
REM Quick wrapper to run Maestro tests with proper environment setup

setlocal enabledelayedexpansion

REM Add Node.js and npm global to PATH
set "NODEJS_PATH=C:\Program Files\nodejs"
set "NPM_PATH=%USERPROFILE%\AppData\Roaming\npm"
set "PATH=%NODEJS_PATH%;%NPM_PATH%;%PATH%"

REM Get test argument
set TEST=%1
if "%TEST%"=="" set TEST=all

REM Show info
cls
echo.
echo ╔═════════════════════════════════════════════════════════╗
echo ║     FastMediaSorter v2 - Maestro E2E Tests             ║
echo ║                January 26, 2026                         ║
echo ╚═════════════════════════════════════════════════════════╝
echo.

echo Running tests...
echo.

if "%TEST%"=="all" (
    maestro-cli test smoke/ --timeout 60000
) else (
    maestro-cli test smoke/%TEST%.yaml --timeout 60000
)

echo.
if %errorlevel% equ 0 (
    echo ✅ Tests passed!
) else (
    echo ❌ Tests failed with exit code %errorlevel%
)
pause
exit /b %errorlevel%
