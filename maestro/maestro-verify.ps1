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

# Check 1: Node.js
Write-Host "`n📍 Checking Node.js..."
try {
    $nodeVersion = & "C:\Program Files\nodejs\node.exe" --version 2>$null
    Write-Host "✅ Node.js: $nodeVersion"
    $checks += "Node.js"
}
catch {
    Write-Host "❌ Node.js not found"
    Write-Host "   Fix with: winget install OpenJS.NodeJS"
    $issuesFound++
}

# Check 2: npm
Write-Host "`n📍 Checking npm..."
try {
    $npmVersion = & "C:\Program Files\nodejs\npm.cmd" --version 2>$null
    Write-Host "✅ npm: v$npmVersion"
    $checks += "npm"
}
catch {
    Write-Host "❌ npm not found"
    Write-Host "   Usually installed with Node.js"
    $issuesFound++
}

# Check 3: Maestro CLI
Write-Host "`n📍 Checking Maestro CLI..."
$maestroPath = "C:\Users\$env:USERNAME\AppData\Roaming\npm\maestro-cli.ps1"
if (Test-Path $maestroPath) {
    Write-Host "✅ Maestro CLI installed"
    $checks += "Maestro CLI"
}
else {
    Write-Host "❌ Maestro CLI not found"
    Write-Host "   Install with: npm install -g maestro-cli"
    $issuesFound++
}

# Check 4: Android SDK
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

# Check 5: ADB
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

# Check 6: FastMediaSorter app
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

# Check 7: Maestro test files
Write-Host "`n📍 Checking Maestro test files..."
$testDir = Get-Location
$testFiles = @(
    "maestro/config.yaml",
    "maestro/smoke/app_launch.yaml",
    "maestro/smoke/local_browse.yaml",
    "maestro/smoke/media_play.yaml",
    "maestro/smoke/image_view.yaml",
    "maestro/smoke/file_operations.yaml",
    "maestro/smoke/settings.yaml"
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

# Check 8: Documentation
Write-Host "`n📍 Checking documentation..."
$docs = @(
    "maestro/README.md",
    "maestro/MAESTRO_SETUP_GUIDE.md",
    "maestro/SETUP_COMPLETE.md"
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
   .\dev\build-with-version.ps1

2. Run smoke tests:
   cd maestro
   maestro-cli test smoke/

3. Expected result: All 6 tests pass (~3-4 minutes)

For more information:
  • Quick Reference: maestro/QUICK_REFERENCE.txt
  • Detailed Setup: maestro/MAESTRO_SETUP_GUIDE.md
  • README: maestro/README.md
"@
}
else {
    Write-Host @"
⚠️  $issuesFound issue(s) found. Please address above.

Quick Fix Guide:
"@
    
    if (-not (Test-Path "C:\Program Files\nodejs")) {
        Write-Host "  • Install Node.js: winget install OpenJS.NodeJS"
    }
    if (-not (Test-Path $maestroPath)) {
        Write-Host "  • Install Maestro: npm install -g maestro-cli"
    }
    if (-not (Test-Path $adbPath)) {
        Write-Host "  • Install Android SDK: Download from developer.android.com/studio"
    }
    Write-Host "`nRun this script again to verify: .\maestro-verify.ps1"
}

Write-Host "`n"

if ($Fix) {
    Write-Host "🔧 Attempting auto-fix..."
    
    if (-not (Test-Path $maestroPath)) {
        Write-Host "Installing Maestro CLI..."
        & "C:\Program Files\nodejs\npm.cmd" install -g maestro-cli
        Write-Host "✅ Maestro CLI installed"
    }
    
    Write-Host "✅ Auto-fix complete. Run verification again."
}
