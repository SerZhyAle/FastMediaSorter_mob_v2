# Install Release APK on ADB Connected Device
# Finds the latest release APK and installs it on the connected Android device
#
# USAGE:
#   .\Install_release_on_adb_connected_device.ps1                  # Install standard release
#   .\Install_release_on_adb_connected_device.ps1 -Launch          # Install and launch app
#   .\Install_release_on_adb_connected_device.ps1 -Flavor lite     # Install lite flavor
#   .\Install_release_on_adb_connected_device.ps1 -NoReplace       # Fresh install (don't replace)
#
# PARAMETERS:
#   -Launch       Launch app after installation
#   -NoReplace    Do not use -r flag (fresh install, removes user data)
#   -Flavor       App flavor: standard, lite, photos, legacy (default: standard)
#
# REQUIREMENTS:
#   - Android SDK Platform-Tools (adb) in PATH
#   - Device connected via USB/WiFi with USB debugging enabled
#   - Release APK already built (.\gradlew.bat assembleStandardRelease)

param(
    [switch]$Launch,           # Launch app after installation
    [switch]$NoReplace,        # Do not use -r flag (replace existing app)
    [string]$Flavor = "standard"  # Flavor to install: standard, lite, photos, legacy
)

# ADB path
$adb = "adb"

# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\.."
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\$Flavor\release"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Install Release APK on ADB Device" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Flavor: $Flavor" -ForegroundColor Yellow

# Check if ADB is available
try {
    $adbVersion = & $adb version 2>&1 | Select-Object -First 1
    Write-Host "ADB: $adbVersion" -ForegroundColor Green
}
catch {
    Write-Host "ERROR: ADB not found in PATH" -ForegroundColor Red
    Write-Host "Please install Android SDK Platform-Tools" -ForegroundColor Yellow
    exit 1
}

# Check connected devices
Write-Host "`nChecking connected devices..." -ForegroundColor Cyan
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\s+device$' }

if (-not $devices) {
    Write-Host "ERROR: No devices connected via ADB" -ForegroundColor Red
    Write-Host "Connect a device and enable USB debugging" -ForegroundColor Yellow
    exit 1
}

$deviceCount = ($devices | Measure-Object).Count
Write-Host "Found $deviceCount device(s):" -ForegroundColor Green
$devices | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }

# Find latest release APK
Write-Host "`nSearching for release APK..." -ForegroundColor Cyan
Write-Host "Directory: $apkDir" -ForegroundColor Gray

if (-not (Test-Path $apkDir)) {
    Write-Host "ERROR: Release APK directory not found" -ForegroundColor Red
    Write-Host "Build release APK first: .\gradlew.bat assemble${Flavor}Release" -ForegroundColor Yellow
    exit 1
}

$apkFiles = Get-ChildItem -Path $apkDir -Filter "*.apk" | Sort-Object LastWriteTime -Descending

if ($apkFiles.Count -eq 0) {
    Write-Host "ERROR: No APK files found in $apkDir" -ForegroundColor Red
    Write-Host "Build release APK first: .\gradlew.bat assemble${Flavor}Release" -ForegroundColor Yellow
    exit 1
}

$latestApk = $apkFiles[0]
$apkPath = $latestApk.FullName
$apkSize = [math]::Round($latestApk.Length / 1MB, 2)

Write-Host "Latest APK: $($latestApk.Name)" -ForegroundColor Green
Write-Host "Size: $apkSize MB" -ForegroundColor Gray
Write-Host "Modified: $($latestApk.LastWriteTime)" -ForegroundColor Gray

# Install APK
Write-Host "`nInstalling APK..." -ForegroundColor Cyan

$installArgs = @("install")
if (-not $NoReplace) {
    $installArgs += "-r"  # Replace existing app
}
$installArgs += $apkPath  # No quotes - PowerShell handles paths automatically

Write-Host "Command: adb $($installArgs -join ' ')" -ForegroundColor Gray

try {
    $output = & $adb $installArgs 2>&1
    $output | ForEach-Object { Write-Host $_ -ForegroundColor Gray }
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`nERROR: Installation failed (exit code: $LASTEXITCODE)" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    
    if ($output -match "Success") {
        Write-Host "`n========================================" -ForegroundColor Green
        Write-Host "Installation Successful!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
    }
    else {
        Write-Host "`nWARNING: Installation may have failed" -ForegroundColor Yellow
        Write-Host "Check output above for errors" -ForegroundColor Yellow
    }
}
catch {
    Write-Host "`nERROR: Installation failed" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

# Launch app if requested
if ($Launch) {
    Write-Host "`nLaunching app..." -ForegroundColor Cyan
    
    # Determine package name based on flavor
    $packageName = switch ($Flavor) {
        "standard" { "com.sza.fastmediasorter" }
        "lite" { "com.sza.fastmediasorter.lite" }
        "photos" { "com.sza.fastmediasorter.photos" }
        "legacy" { "com.sza.fastmediasorter.legacy" }
        default { "com.sza.fastmediasorter" }
    }
    
    $activityName = "$packageName/.ui.main.MainActivity"
    
    try {
        $launchOutput = & $adb shell am start -n $activityName 2>&1
        $launchOutput | ForEach-Object { Write-Host $_ -ForegroundColor Gray }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "App launched successfully!" -ForegroundColor Green
        }
        else {
            Write-Host "WARNING: Failed to launch app" -ForegroundColor Yellow
        }
    }
    catch {
        Write-Host "WARNING: Failed to launch app" -ForegroundColor Yellow
        Write-Host $_.Exception.Message -ForegroundColor Gray
    }
}

Write-Host "`nDone!" -ForegroundColor Cyan
