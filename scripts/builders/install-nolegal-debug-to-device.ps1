# Install noLegal DEBUG APK on connected device WITHOUT launching it.
#
# noLegal = standard + VR + sideload-only capabilities (single universal APK,
# S0156). Installs on any Android device (4 ABIs); OpenXR bridge is compiled
# for arm64-v8a only, so VR mode is reachable only on Quest-class headsets.
#
# Why install-only (no auto-launch):
#   The user picks the launch surface on the device - phone launcher for
#   standard UI, Quest Library (Unknown Sources) for the VR entry.
#   Auto-launching via `adb shell am start` would force one of those surfaces
#   and, on Quest, would skip the vrshell launch_id path needed for FOCUSED XR
#   sessions (S1983: the dedicated VR installers this once pointed at are gone;
#   the same reasoning is written up in docs/DEV_OPS.md, Quest debugging).
#
# Usage:
#   .\scripts\builders\install-nolegal-debug-to-device.ps1
#   .\scripts\builders\install-nolegal-debug-to-device.ps1 -ApkPath C:\custom\path.apk
#   .\scripts\builders\install-nolegal-debug-to-device.ps1 -DeviceId <serial>
#   .\a.ps1 ind
#   .\a.ps1 ivn

param(
    [string]$ApkPath = $null,
    # S1986: this repo routinely has a phone, a watch and an emulator online at once, and bare
    # `adb install` then fails with "more than one device/emulator" after the APK was already built.
    # Falls back to ANDROID_SERIAL so an exported serial keeps working without repeating it per call.
    [string]$DeviceId = $env:ANDROID_SERIAL
)

. "$PSScriptRoot\..\utils\project-paths.ps1"
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
. "$PSScriptRoot\..\utils\get-device-abi.ps1"

$adb = Get-ToolPath -Tool Adb
if (-not (Test-Path -Path $adb)) {
    $adb = 'adb'
}

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
$variant = 'debug'
$packageName = 'com.sza.fastmediasorter.debug'

Write-Host "noLegal APK installer - DEBUG" -ForegroundColor Cyan
Write-Host "Launch policy: install only - DO NOT auto-launch (see script header)" -ForegroundColor Magenta

# Confirm device presence and resolve target device.
Write-Host "`nChecking for connected device..." -ForegroundColor Cyan
$devicesRaw = & $adb devices
$connected = @($devicesRaw | Select-String -Pattern '\tdevice$')
if ($connected.Count -eq 0) {
    Write-Host "Error: no device connected via ADB." -ForegroundColor Red
    Write-Host "Connect a phone/tablet via USB (Developer Mode + USB debugging) or pair Quest over Wi-Fi." -ForegroundColor Gray
    exit 1
}
if ($connected.Count -gt 1 -and -not $DeviceId) {
    $serials = @($connected | ForEach-Object { ($_ -split '\s+')[0] })
    $nonWatch = @($serials | Where-Object {
        $chars = (& $adb -s $_ shell getprop ro.build.characteristics 2>$null) -join ''
        $chars -notmatch 'watch'
    })
    if ($nonWatch.Count -eq 1) {
        $DeviceId = $nonWatch[0]
        Write-Host "Selected $DeviceId (only online phone/tablet/VR device; watch ignored)." -ForegroundColor Gray
    }
    else {
        Write-Host "Error: $($connected.Count) devices connected and no -DeviceId given." -ForegroundColor Red
        Write-Host "Pass -DeviceId <serial> (or export ANDROID_SERIAL); `adb devices` lists them." -ForegroundColor Gray
        exit 1
    }
}

# Resolve APK path.
if (-not $ApkPath) {
    $apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\noLegal\$variant"
    $deviceAbi = Get-TargetDeviceAbi -Adb $adb -DeviceId $DeviceId
    try {
        $resolved = Find-BuildArtifact -Dir $apkDir -Abi $deviceAbi
        if ($resolved) { $ApkPath = $resolved.FullName }
    }
    catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

if (-not $ApkPath -or -not (Test-Path -Path $ApkPath)) {
    Write-Host "Error: noLegal debug APK not found." -ForegroundColor Red
    Write-Host "Expected directory: $projectRoot\app_v2\build\outputs\apk\noLegal\$variant" -ForegroundColor Red
    Write-Host "Build it first:  .\scripts\builders\build-nolegal-debug.ps1   (or: .\a.ps1 nd)" -ForegroundColor Gray
    exit 1
}

$apkSizeMb = [math]::Round((Get-Item $ApkPath).Length / 1MB, 1)
Write-Host "APK: $ApkPath ($apkSizeMb MB)" -ForegroundColor Green

# -r: replace existing, -d: downgrade allowed (handy for debug iterations).
$target = if ($DeviceId) { "device $DeviceId" } else { 'the only connected device' }
Write-Host "`nInstalling to $target.." -ForegroundColor Cyan
$adbArgs = @()
if ($DeviceId) { $adbArgs += @('-s', $DeviceId) }
$adbArgs += @('install', '-r', '-d', $ApkPath)
& $adb @adbArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "Install failed (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nInstalled OK: $packageName" -ForegroundColor Green
Write-Host "App NOT launched by design." -ForegroundColor Green
Write-Host "`nLaunch from the device:" -ForegroundColor Cyan
Write-Host "  Phone/tablet: app launcher -> FastMediaSorter (noLegal debug)" -ForegroundColor White
Write-Host "  Quest:        Library -> Unknown Sources -> FastMediaSorter (noLegal debug)" -ForegroundColor White
Write-Host "Launching from Quest Library gives the VR entry a valid vrshell launch_id." -ForegroundColor Gray
