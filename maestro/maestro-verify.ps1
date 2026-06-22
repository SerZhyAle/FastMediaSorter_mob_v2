#!/usr/bin/env powershell
# Maestro Setup Verification Script
# Checks all prerequisites and configurations

param(
    [switch]$Fix,
    [switch]$Verbose
)

$ErrorActionPreference = "SilentlyContinue"
$WarningPreference = "SilentlyContinue"

Write-Host @"
╔═══════════════════════════════════════════════════════════════╗
║     FastMediaSorter v2 - Maestro Setup Verification           ║
║                   January 26, 2026                            ║
╚═══════════════════════════════════════════════════════════════╝
"@

$checks = @()
$issuesFound = 0

# Check 1: Maestro CLI
Write-Host "`n📍 Checking Maestro CLI..."
$maestroCommand = Get-Command maestro -ErrorAction SilentlyContinue
$maestroPerUser = Join-Path $env:USERPROFILE ".maestro\bin\maestro.bat"
if ($maestroCommand -or (Test-Path $maestroPerUser)) {
    Write-Host "✅ Maestro CLI installed"
    $checks += "Maestro CLI"
}
else {
    Write-Host "❌ Maestro CLI not found"
    Write-Host "   Install from https://maestro.mobile.dev/getting-started/installing-maestro"
    Write-Host "   Do not use npm install -g maestro-cli; that is a different package."
    $issuesFound++
}

# Check 2: Android SDK
Write-Host "`n📍 Checking Android SDK..."
$sdkPath = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
if (Test-Path $sdkPath) {
    Write-Host "✅ Android SDK found: $sdkPath"
    $checks += "Android SDK"
}
else {
    Write-Host "❌ Android SDK not found"
    Write-Host "   Usually at: C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
    $issuesFound++
}

# Check 3: ADB
Write-Host "`n📍 Checking ADB..."
$adbPath = "$sdkPath\platform-tools\adb.exe"
if (Test-Path $adbPath) {
    Write-Host "✅ ADB found"
    $checks += "ADB"
    
    # Check device connection
    Write-Host "`n📍 Checking connected devices..."
    $devices = & $adbPath devices 2>$null | Select-String "device" | Select-Object -Skip 1
    if ($devices) {
        Write-Host "✅ Connected devices:"
        $devices | ForEach-Object { Write-Host "   • $_" }
        $checks += "Connected Devices"
    }
    else {
        Write-Host "⚠️  No devices connected"
        Write-Host "   Enable USB Debugging on device (Settings > Developer Options)"
        Write-Host "   Connect via USB or start emulator"
    }
}
else {
    Write-Host "❌ ADB not found at: $adbPath"
    $issuesFound++
}

# Check 4: FastMediaSorter app
Write-Host "`n📍 Checking FastMediaSorter app..."
if (Test-Path "$adbPath") {
    $appInstalled = & $adbPath shell pm list packages 2>$null | Select-String "com.sza.fastmediasorter"
    if ($appInstalled) {
        Write-Host "✅ FastMediaSorter app installed"
        $checks += "App Installed"
    }
    else {
        Write-Host "⚠️  FastMediaSorter app not installed"
        Write-Host "   Build with: .\dev\build-with-version.ps1"
    }
}

# Check 5: Maestro test files
Write-Host "`n📍 Checking Maestro test files..."
$testDir = Get-Location
$testFiles = @(
    "maestro/config.yaml",
    "maestro/run-tests.ps1",
    "maestro/smoke/app_launch.yaml",
    "maestro/smoke/local_browse.yaml",
    "maestro/critical/file_operations.yaml",
    "maestro/critical/settings.yaml",
    "maestro/features/browse/browse_all_images.yaml",
    "maestro/features/player/player_image.yaml"
)

$missingTests = 0
foreach ($test in $testFiles) {
    if (Test-Path $test) {
        Write-Host "✅ $test"
    }
    else {
        Write-Host "❌ $test"
        $missingTests++
    }
}

if ($missingTests -eq 0) {
    $checks += "Test Files"
}

# Check 6: Documentation
Write-Host "`n📍 Checking documentation..."
$docs = @(
    "maestro/README.md",
    "maestro/INSTALLATION_WINDOWS.md",
    "maestro/TROUBLESHOOTING.md",
    "maestro/WRITING_TESTS.md"
)

$missingDocs = 0
foreach ($doc in $docs) {
    if (Test-Path $doc) {
        Write-Host "✅ $(Split-Path -Leaf $doc)"
    }
    else {
        Write-Host "⚠️  $(Split-Path -Leaf $doc)"
        $missingDocs++
    }
}

# Summary
Write-Host @"
`n╔═══════════════════════════════════════════════════════════════╗
║                    VERIFICATION SUMMARY                        ║
╚═══════════════════════════════════════════════════════════════╝
"@

if ($issuesFound -eq 0) {
    Write-Host @"
✅ ALL CRITICAL CHECKS PASSED!

Your system is ready for Maestro testing. Next steps:

1. Build the app:
   .\a.ps1 d

2. Run the compact suite runner:
   pwsh -NoProfile -File maestro/run-tests.ps1 -Suite smoke -Json

3. Expected result: runner exits 0 and reports pass=true

For more information:
  • README: maestro/README.md
  • Troubleshooting: maestro/TROUBLESHOOTING.md
"@
}
else {
    Write-Host @"
⚠️  $issuesFound issue(s) found. Please address above.

Quick Fix Guide:
"@
    
    if (-not $maestroCommand -and -not (Test-Path $maestroPerUser)) {
        Write-Host "  • Install Maestro Mobile CLI from https://maestro.mobile.dev"
    }
    if (-not (Test-Path $adbPath)) {
        Write-Host "  • Install Android SDK: Download from developer.android.com/studio"
    }
    Write-Host "`nRun this script again to verify: .\maestro-verify.ps1"
}

Write-Host "`n"

if ($Fix) {
    Write-Host "🔧 Auto-fix is not available for Maestro Mobile CLI. Install it from https://maestro.mobile.dev and rerun verification."
}
