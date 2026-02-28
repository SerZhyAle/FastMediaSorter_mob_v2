#!/usr/bin/env powershell
# Maestro E2E Smoke Tests Runner
# Purpose: Quick smoke test execution for FastMediaSorter v2
# Usage: .\run-maestro-smoke-tests.ps1

param(
    [ValidateSet("all", "app_launch", "local_browse", "media_play", "image_view", "file_operations", "settings", "help")]
    [string]$Test = "all",
    
    [switch]$Debug,
    [switch]$NoInstall,
    [switch]$Interactive,
    [switch]$AllowPmClear
)

$ErrorActionPreference = "Stop"

function Show-Help {
    Write-Host @"
Maestro E2E Smoke Tests Runner

USAGE:
  .\run-maestro-smoke-tests.ps1 [options]

OPTIONS:
  -Test <test>           Which test to run (default: all)
                         Values: all, app_launch, local_browse, media_play, 
                                image_view, file_operations, settings
  
  -Debug                 Show debug output during test execution
  
  -NoInstall             Don't rebuild/install app before testing

    -AllowPmClear          Explicitly allow `adb shell pm clear ...` (DANGEROUS: wipes app data)
  
  -Interactive           Use interactive mode (maestro studio)
  
  -Help                  Show this help message

EXAMPLES:
  # Run all smoke tests
  .\run-maestro-smoke-tests.ps1
  
  # Run app_launch test with debug output
  .\run-maestro-smoke-tests.ps1 -Test app_launch -Debug
  
  # Run interactive mode
  .\run-maestro-smoke-tests.ps1 -Interactive
  
  # Run without rebuilding app
  .\run-maestro-smoke-tests.ps1 -NoInstall

REQUIREMENTS:
  - Android device connected (adb devices)
  - Maestro CLI installed (maestro --version)
  - App built and installed

"@
}

function Check-Prerequisites {
    Write-Host "🔍 Checking prerequisites..."
    
    # Check ADB
    try {
        $adbOutput = adb devices 2>&1 | Select-String "device" | Select-Object -Skip 1
        if (-not $adbOutput) {
            Write-Host "❌ No Android devices found. Please:"
            Write-Host "   1. Enable USB Debugging on device (Settings > Developer Options)"
            Write-Host "   2. Connect device via USB cable"
            Write-Host "   3. Run: adb devices"
            exit 1
        }
        Write-Host "✅ Android device found:"
        Write-Host $adbOutput
    }
    catch {
        Write-Host "❌ ADB not found. Please install Android SDK."
        exit 1
    }
    
    # Check Maestro
    try {
        $maestroVersion = maestro --version 2>&1
        Write-Host "✅ Maestro CLI: $maestroVersion"
    }
    catch {
        Write-Host "❌ Maestro not installed. Install with:"
        Write-Host "   npm install -g maestro-cli"
        exit 1
    }
    
    # Check app installed
    $appInstalled = adb shell pm list packages | Select-String "com.sza.fastmediasorter"
    if (-not $appInstalled) {
        Write-Host "⚠️  App not installed. Installing..."
        if (-not $NoInstall) {
            Build-And-Install-App
        }
    }
    else {
        Write-Host "✅ App installed"
    }
}

function Build-And-Install-App {
    Write-Host "`n📦 Building and installing app..."
    
    if (-not $AllowPmClear) {
        Write-Host "❌ Refusing to run 'pm clear' without explicit -AllowPmClear flag." -ForegroundColor Red
        Write-Host "   Re-run with -AllowPmClear ONLY if data wipe is intended." -ForegroundColor Yellow
        exit 2
    }

    # Clear app data (explicitly allowed)
    adb shell pm clear com.sza.fastmediasorter
    
    # Build
    .\dev\build-with-version.ps1 | Out-Null
    
    Write-Host "✅ App installed"
}

function Run-Test {
    param([string]$TestFile)
    
    $testPath = "maestro/smoke/$TestFile.yaml"
    
    if (-not (Test-Path $testPath)) {
        Write-Host "❌ Test file not found: $testPath"
        exit 1
    }
    
    Write-Host "`n▶️  Running test: $TestFile"
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    $maestroArgs = @()
    if ($Debug) {
        $maestroArgs += "--debug"
    }
    $maestroArgs += "test"
    $maestroArgs += $testPath
    
    # Run test
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $result = maestro @maestroArgs 2>&1
    $stopwatch.Stop()
    
    Write-Host $result
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ PASSED ($($stopwatch.Elapsed.TotalSeconds)s)"
        return $true
    }
    else {
        Write-Host "❌ FAILED"
        return $false
    }
}

function Run-Interactive {
    Write-Host "🎬 Starting interactive mode (maestro studio)..."
    Write-Host "Open a web browser to: http://localhost:5590"
    maestro studio
}

function Run-All-Tests {
    Write-Host "`n🧪 Running all smoke tests..."
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`n"
    
    $tests = @("app_launch", "local_browse", "media_play", "image_view", "file_operations", "settings")
    $results = @{}
    $totalTime = [System.Diagnostics.Stopwatch]::StartNew()
    
    foreach ($test in $tests) {
        $results[$test] = Run-Test $test
    }
    
    $totalTime.Stop()
    
    # Summary
    Write-Host "`n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    Write-Host "📊 TEST SUMMARY"
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    $passed = 0
    $failed = 0
    
    foreach ($test in $tests) {
        $status = if ($results[$test]) { "✅ PASS" } else { "❌ FAIL" }
        Write-Host "$status  $test"
        if ($results[$test]) { $passed++ } else { $failed++ }
    }
    
    Write-Host "`nTotal: $passed passed, $failed failed in $($totalTime.Elapsed.TotalSeconds)s"
    
    if ($failed -eq 0) {
        Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        Write-Host "🎉 All smoke tests passed!"
        Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
    else {
        exit 1
    }
}

# Main
if ($Test -eq "help") {
    Show-Help
    exit 0
}

Write-Host @"
╔════════════════════════════════════════════════════════════╗
║        FastMediaSorter v2 - Maestro E2E Smoke Tests       ║
║                    January 25, 2026                        ║
╚════════════════════════════════════════════════════════════╝
"@

Check-Prerequisites

if ($Interactive) {
    Run-Interactive
}
elseif ($Test -eq "all") {
    Run-All-Tests
}
else {
    $result = Run-Test $Test
    if (-not $result) {
        exit 1
    }
}

Write-Host "`n✅ Done!"
