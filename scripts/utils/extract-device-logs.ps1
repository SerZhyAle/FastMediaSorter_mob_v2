#!/usr/bin/env pwsh
# Extract device logs for FastMediaSorter debugging
# Usage: .\scripts\utils\extract-device-logs.ps1

param(
    [string]$OutputDir = "temp",
    [string]$PackageName = "com.sza.fastmediasorter"
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = Join-Path $OutputDir "device_logs_$timestamp.txt"
$prefsFile = Join-Path $OutputDir "app_settings_$timestamp.preferences.pb"

. "$PSScriptRoot\project-paths.ps1"

# Resolve adb: prefer PATH, then common Android SDK locations. The dev machine
# does not always have platform-tools on PATH, which previously aborted the
# whole script at the first `adb` call.
function Resolve-Adb {
    # Discovery is shared (S2326). The resolver raises when nothing is found, while this caller
    # prints its own instructions and exits 1, so the raise is converted back to $null here.
    try { return Get-ToolPath -Tool Adb -Quiet } catch { return $null }
}

$adb = Resolve-Adb
if (-not $adb) {
    Write-Host "ERROR: adb not found on PATH or in common SDK locations!" -ForegroundColor Red
    Write-Host "Install Android platform-tools or set ANDROID_HOME / ANDROID_SDK_ROOT" -ForegroundColor Yellow
    exit 1
}

Write-Host "=== Extracting Device Logs ===" -ForegroundColor Cyan
Write-Host "adb: $adb"
Write-Host "Output: $OutputDir"
Write-Host ""

# Check if device is connected
$devices = & $adb devices | Select-String -Pattern "device$"
if (-not $devices) {
    Write-Host "ERROR: No Android device connected!" -ForegroundColor Red
    Write-Host "Connect device via USB and enable USB debugging" -ForegroundColor Yellow
    exit 1
}

Write-Host "Device connected: $($devices[0])" -ForegroundColor Green
Write-Host ""

# Resolve the actually-installed package. Debug builds carry the `.debug`
# applicationIdSuffix, so filtering by the bare release id yields an empty log.
# Prefer the explicit -PackageName if installed; otherwise fall back to the
# first installed variant that starts with it (e.g. `<base>.debug`).
$installed = & $adb shell pm list packages 2>$null |
    ForEach-Object { ($_ -replace '^package:', '').Trim() } |
    Where-Object { $_ -eq $PackageName -or $_ -like "$PackageName.*" }
if ($installed) {
    $resolved = $installed | Where-Object { $_ -eq $PackageName } | Select-Object -First 1
    if (-not $resolved) { $resolved = $installed | Select-Object -First 1 }
    if ($resolved -ne $PackageName) {
        Write-Host "Resolved installed package: $resolved (requested: $PackageName)" -ForegroundColor Cyan
    }
    $PackageName = $resolved
} else {
    Write-Host "WARNING: no installed package matching '$PackageName*' - logcat filter may be empty" -ForegroundColor Yellow
}
Write-Host "Package: $PackageName"
Write-Host ""

# Create output directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

# Extract logcat
Write-Host "[1/4] Extracting logcat..." -ForegroundColor Yellow
try {
    # Preserve the main package lines plus XR/native tags that do not always repeat the
    # package id once logcat is converted/exported.
    $logPatterns = @(
        "FastMediaSorter",
        "LocaleHelper",
        [regex]::Escape($PackageName),
        "S0249\\.XrSession",
        "S0249\\.JniBridge",
        "DiagnosticXr",
        "XrEntryGatewayImpl",
        "VrSettingsBlockFragment",
        "xrInitializeLoaderKHR",
        "xrCreateInstance",
        "xrGetSystem"
    )
    $logcatOutput = & $adb logcat -d -t 5000 | Select-String -Pattern $logPatterns
    $logcatOutput | Out-File -FilePath $logFile -Encoding UTF8
    Write-Host "  Saved to: $logFile" -ForegroundColor Green
}
catch {
    Write-Host "  FAILED: $_" -ForegroundColor Red
}

# Extract DataStore preferences
Write-Host "[2/4] Extracting DataStore settings..." -ForegroundColor Yellow
try {
    # The app stores settings in Preferences DataStore (binary protobuf), not the legacy
    # SharedPreferences XML. `base64` keeps the transfer byte-safe: decoding locally and
    # writing the raw bytes preserves the payload for protobuf inspection, which a text
    # `cat` + `Out-File` round trip would corrupt.
    $encoded = & $adb shell "run-as $PackageName base64 /data/data/$PackageName/files/datastore/settings.preferences_pb" 2>$null
    if ($encoded) {
        $bytes = [Convert]::FromBase64String(($encoded -join ''))
        [System.IO.File]::WriteAllBytes($prefsFile, $bytes)
        Write-Host "  Saved to: $prefsFile" -ForegroundColor Green

        # Best-effort: the protobuf payload interleaves tag and length bytes between the
        # key name and its string value, so skip the display when the shape does not match.
        $text = [System.Text.Encoding]::GetEncoding(28591).GetString($bytes)
        if ($text -match 'language[^a-zA-Z0-9_]{0,6}([a-z]{2,3}(?:-[A-Za-z]{2,8})?)') {
            Write-Host "  Current language: $($matches[1])" -ForegroundColor Cyan
        }
    }
    else {
        Write-Host "  FAILED: could not read settings.preferences_pb (device not rooted or app not debuggable)" -ForegroundColor Yellow
    }
}
catch {
    Write-Host "  FAILED: $_" -ForegroundColor Red
}

# Check Android version
Write-Host "[3/4] Checking Android version..." -ForegroundColor Yellow
try {
    $sdkVersion = & $adb shell getprop ro.build.version.sdk
    $androidVersion = & $adb shell getprop ro.build.version.release
    Write-Host "  Android $androidVersion (SDK $sdkVersion)" -ForegroundColor Green
    
    if ([int]$sdkVersion -ge 33) {
        Write-Host "  Android 13+ detected - using LocaleManager API" -ForegroundColor Cyan
        
        # Try to get per-app locale settings
        $localeInfo = & $adb shell dumpsys localemanager 2>$null | Select-String -Pattern $PackageName
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
    # Get app path on device. A split-APK install lists one line per split, and -match on
    # an array filters lines instead of populating $Matches, so match the first line only.
    $appPath = & $adb shell pm path $PackageName 2>$null | Select-Object -First 1
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
Write-Host "2. Check DataStore settings: notepad $prefsFile"
Write-Host "3. If issue persists, send these files for analysis"
Write-Host ""
