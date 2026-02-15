<#
.SYNOPSIS
    Pushes the contents of the 'test_media' folder to a connected Android device.

.DESCRIPTION
    This script checks for a connected ADB device, cleans the target directory on the device,
    pushes the contents of the local 'test_media' directory, and triggers a media scan.

.NOTES
    File Name      : setup_test_media.ps1
    Author         : Antigravity
    Prerequisite   : ADB must be installed and in the PATH.
#>

$ErrorActionPreference = "Stop"

# Configuration
$ScriptPath = $PSScriptRoot
$ProjectRoot = Resolve-Path "$ScriptPath\..\.."
$LocalMediaDir = "C:\GIT\FastMediaSorter_mob_v2\test_media"
$DeviceDestDir = "/sdcard/Download/FastMediaSorter_Test"

Write-Host "--- Android Test Media Setup ---" -ForegroundColor Cyan

# 1. Check for ADB and connected devices
Write-Host "Checking for connected devices..."

$AdbExe = "adb"
$PossibleAdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

if (Test-Path $PossibleAdbPath) {
    Write-Host "Using ADB from: $PossibleAdbPath" -ForegroundColor DarkGray
    $AdbExe = $PossibleAdbPath
}
elseif (-not (Get-Command "adb" -ErrorAction SilentlyContinue)) {
    Write-Error "ADB not found. Please ensure Android SDK Platform-Tools are in your PATH or at $env:LOCALAPPDATA\Android\Sdk\platform-tools"
}

try {
    $adbDevices = & $AdbExe devices
    # Check if any line matches a device entry (format: "serial\tdevice" or "serial device")
    # We look for "device" at the end of the line, avoiding "List of devices attached".
    $foundDevice = $adbDevices -match "\sdevice$"
    if (-not $foundDevice) {
        Write-Error "No active device found. Please connect a device/emulator and ensure USB debugging is enabled."
    }
    Write-Host "Found device(s):"
    $foundDevice | ForEach-Object { Write-Host "  $_" -ForegroundColor Green }
}
catch {
    Write-Error "Failed to run ADB ($AdbExe): $_"
}

# 2. Check source directory (unchanged)
if (-not (Test-Path $LocalMediaDir)) {
    Write-Warning "Local directory '$LocalMediaDir' does not exist."
    Write-Host "Creating '$LocalMediaDir'..."
    New-Item -ItemType Directory -Path $LocalMediaDir | Out-Null
    Write-Host "Please put some media files in '$LocalMediaDir' and run this script again." -ForegroundColor Yellow
    exit
}

# 3. Clean and Recreate Destination on Device
Write-Host "Cleaning destination on device: $DeviceDestDir"
& $AdbExe shell rm -rf "$DeviceDestDir"
& $AdbExe shell mkdir -p "$DeviceDestDir"

# 4. Push Files
Write-Host "Pushing media files from '$LocalMediaDir'..."
& $AdbExe push "$LocalMediaDir/." "$DeviceDestDir/"

# 5. Prepare deterministic ops folders and files
Write-Host "Preparing test ops folders/files..." -ForegroundColor DarkGray
& $AdbExe shell "mkdir -p $DeviceDestDir/_ops_src $DeviceDestDir/_ops_dst"
& $AdbExe shell "echo TEST_COPY > $DeviceDestDir/_ops_src/ops_copy_1.jpg"
& $AdbExe shell "echo TEST_MOVE > $DeviceDestDir/_ops_src/ops_move_1.jpg"

# 6. Trigger Media Scan
Write-Host "Triggering Media Store scan..."
& $AdbExe shell "find $DeviceDestDir -type f | while read f; do am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://`"`$f`"`; done"


Write-Host "Done! Media files are located at '$DeviceDestDir'." -ForegroundColor Green
Write-Host ""
Write-Host "Next step: Run Maestro test" -ForegroundColor Cyan
Write-Host "  .\maestro\run-tests.ps1 smoke" -ForegroundColor Yellow
Write-Host ""
Write-Host "Or run specific test:" -ForegroundColor Cyan
Write-Host "  maestro test .\maestro\smoke\test_media_workflow.yaml" -ForegroundColor Yellow
Write-Host ""
