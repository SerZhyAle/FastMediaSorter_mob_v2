#!/usr/bin/env pwsh
# Extract device logs for language switching debugging
# Usage: .\scripts\utils\extract-device-logs.ps1

param(
    [string]$OutputDir = "temp",
    [string]$PackageName = "com.sza.fastmediasorter"
)

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = Join-Path $OutputDir "device_logs_$timestamp.txt"
$prefsFile = Join-Path $OutputDir "app_settings_$timestamp.xml"

Write-Host "=== Extracting Device Logs ===" -ForegroundColor Cyan
Write-Host "Package: $PackageName"
Write-Host "Output: $OutputDir"
Write-Host ""

# Check if device is connected
$devices = adb devices | Select-String -Pattern "device$"
if (-not $devices) {
    Write-Host "ERROR: No Android device connected!" -ForegroundColor Red
    Write-Host "Connect device via USB and enable USB debugging" -ForegroundColor Yellow
    exit 1
}

Write-Host "Device connected: $($devices[0])" -ForegroundColor Green
Write-Host ""

# Create output directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

# Extract logcat
Write-Host "[1/4] Extracting logcat..." -ForegroundColor Yellow
try {
    # Last 5000 lines of logcat, filter for our app
    $logcatOutput = adb logcat -d -t 5000 | Select-String -Pattern "FastMediaSorter|LocaleHelper|$PackageName"
    $logcatOutput | Out-File -FilePath $logFile -Encoding UTF8
    Write-Host "  Saved to: $logFile" -ForegroundColor Green
}
catch {
    Write-Host "  FAILED: $_" -ForegroundColor Red
}

# Extract SharedPreferences
Write-Host "[2/4] Extracting SharedPreferences..." -ForegroundColor Yellow
try {
    $prefsOutput = adb shell "run-as $PackageName cat /data/data/$PackageName/shared_prefs/app_settings.xml" 2>$null
    if ($prefsOutput) {
        $prefsOutput | Out-File -FilePath $prefsFile -Encoding UTF8
        Write-Host "  Saved to: $prefsFile" -ForegroundColor Green
        
        # Parse language setting
        if ($prefsOutput -match '<string name="selected_language">([^<]+)</string>') {
            $lang = $matches[1]
            Write-Host "  Current language: $lang" -ForegroundColor Cyan
        }
    }
    else {
        Write-Host "  FAILED: Cannot access app data (device not rooted or app not debuggable)" -ForegroundColor Yellow
    }
}
catch {
    Write-Host "  FAILED: $_" -ForegroundColor Red
}

# Check Android version
Write-Host "[3/4] Checking Android version..." -ForegroundColor Yellow
try {
    $sdkVersion = adb shell getprop ro.build.version.sdk
    $androidVersion = adb shell getprop ro.build.version.release
    Write-Host "  Android $androidVersion (SDK $sdkVersion)" -ForegroundColor Green
    
    if ([int]$sdkVersion -ge 33) {
        Write-Host "  Android 13+ detected - using LocaleManager API" -ForegroundColor Cyan
        
        # Try to get per-app locale settings
        $localeInfo = adb shell dumpsys localemanager 2>$null | Select-String -Pattern $PackageName
        if ($localeInfo) {
            Write-Host "  Per-app locale: $localeInfo" -ForegroundColor Cyan
        }
    }
    else {
        Write-Host "  Android < 13 - using Configuration API" -ForegroundColor Cyan
    }
}
catch {
    Write-Host "  FAILED: $_" -ForegroundColor Red
}

# Check if APK has localization resources
Write-Host "[4/4] Checking app resources..." -ForegroundColor Yellow
try {
    # Get app path on device
    $appPath = adb shell pm path $PackageName 2>$null
    if ($appPath -match "package:(.+)") {
        $apkPath = $matches[1].Trim()
        Write-Host "  APK location: $apkPath" -ForegroundColor Green
        
        # List resources (requires aapt2 or apktool)
        # This is informational only
        Write-Host "  To verify resources, extract APK and check res/ folder" -ForegroundColor Cyan
    }
}
catch {
    Write-Host "  FAILED: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
Write-Host "Logs saved to: $OutputDir" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Review logs: notepad $logFile"
Write-Host "2. Check SharedPreferences: notepad $prefsFile"
Write-Host "3. If issue persists, send these files for analysis"
Write-Host ""
