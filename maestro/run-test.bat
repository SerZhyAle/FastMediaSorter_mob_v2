@echo off
REM Maestro Test Runner - Windows Batch Script
REM Quick wrapper to run Maestro tests with proper environment setup

setlocal enabledelayedexpansion

REM Add Node.js and npm global to PATH. A batch file cannot dot-source the PowerShell resolver,
REM so it honours the same FMS_NODE override and otherwise asks the shell where node is (S2326).
set "NODEJS_PATH="
if defined FMS_NODE for %%I in ("%FMS_NODE%") do set "NODEJS_PATH=%%~dpI"
if not defined NODEJS_PATH for /f "delims=" %%I in ('where node 2^>nul') do if not defined NODEJS_PATH set "NODEJS_PATH=%%~dpI"
set "NPM_PATH=%APPDATA%\npm"
if defined NODEJS_PATH set "PATH=%NODEJS_PATH%;%NPM_PATH%;%PATH%"

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
