# Quick Stress Test Runner
# FastMediaSorter v2 - Run stress tests with monitoring
# Usage: .\scripts\utils\run-stress.ps1 [-Test monkey|navigation|lifecycle|all] [-NoMonitor]

param(
    [ValidateSet("monkey", "navigation", "lifecycle", "all")]
    [string]$Test = "all",
    [switch]$NoMonitor
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " FastMediaSorter Stress Test Runner" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$utilsPath = $scriptPath
$scriptsPath = Split-Path -Parent $utilsPath
$projectRoot = Split-Path -Parent $scriptsPath

# Check device connection
Write-Host "Checking device connection..." -ForegroundColor Yellow
$devices = adb devices | Select-String "device$" | Measure-Object
if ($devices.Count -eq 0) {
    Write-Host "ERROR: No Android device connected!" -ForegroundColor Red
    Write-Host "Connect device and enable USB debugging" -ForegroundColor Red
    exit 1
}
Write-Host "Device connected: OK" -ForegroundColor Green
Write-Host ""

# Determine suite parameter
$suiteParam = switch ($Test) {
    "monkey" { "monkey" }
    "navigation" { "navigation" }
    "lifecycle" { "lifecycle" }
    default { "all" }
}

# Run stress tests
Push-Location $projectRoot
try {
    if ($NoMonitor) {
        # Quick run without monitoring
        Write-Host "Running stress tests (suite: $suiteParam)..." -ForegroundColor Cyan
        Write-Host ""
        & .\maestro\run-tests.ps1 "stress" -DebugMode
    }
    else {
        # Full run with monitoring and report
        Write-Host "Running stress tests with monitoring (suite: $suiteParam)..." -ForegroundColor Cyan
        Write-Host ""
        & .\scripts\utils\run-maestro-stress.ps1 -Suite $suiteParam -Monitor -Report
    }
    
    $exitCode = $LASTEXITCODE
    Write-Host ""
    
    if ($exitCode -eq 0) {
        Write-Host "========================================" -ForegroundColor Green
        Write-Host " Stress Tests: COMPLETED" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Logs available in: temp\" -ForegroundColor DarkGray
    }
    else {
        Write-Host "========================================" -ForegroundColor Red
        Write-Host " Stress Tests: FAILED" -ForegroundColor Red
        Write-Host "========================================" -ForegroundColor Red
        Write-Host ""
        Write-Host "Check logs in: temp\" -ForegroundColor DarkGray
    }
    
    exit $exitCode
}
finally {
    Pop-Location
}
