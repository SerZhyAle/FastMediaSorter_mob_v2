# Install Standard DEBUG APK on connected device WITHOUT launching it.
#
# Why install-only (no auto-launch):
#   Symmetric with `ivn`. The user chooses when and where to open the app on
#   the device, while this helper only handles deployment.
#
# Usage:
#   .\scripts\builders\install-standard-debug-to-device.ps1
#   .\scripts\builders\install-standard-debug-to-device.ps1 -ApkPath C:\custom\path.apk
#   .\a.ps1 id

param(
    [string]$ApkPath = $null,
    # S1986: this repo routinely has a phone, a watch and an emulator online at once, and bare
    # `adb install` then fails with "more than one device/emulator" after the APK was already built.
    # Falls back to ANDROID_SERIAL so an exported serial keeps working without repeating it per call.
    [string]$DeviceId = $env:ANDROID_SERIAL
)

$ErrorActionPreference = 'Stop'

$adb = 'C:\Users\serzh\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -Path $adb)) {
    $adb = 'adb'
}

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
$variant = 'debug'
$packageName = 'com.sza.fastmediasorter.debug'

Write-Host "Standard APK installer - DEBUG" -ForegroundColor Cyan
Write-Host "Launch policy: install only - DO NOT auto-launch" -ForegroundColor Magenta

# Resolve APK path.
if (-not $ApkPath) {
    $apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\standard\$variant"
    $metadataPath = Join-Path $apkDir 'output-metadata.json'

    if (Test-Path -Path $metadataPath) {
        try {
            $meta = Get-Content -Path $metadataPath -Raw | ConvertFrom-Json
            if ($meta.elements -and $meta.elements.Count -gt 0 -and $meta.elements[0].outputFile) {
                $ApkPath = Join-Path $apkDir $meta.elements[0].outputFile
            }
        }
        catch {
            Write-Host "Warning: failed to parse output-metadata.json - falling back to latest .apk" -ForegroundColor Yellow
        }
    }

    if (-not $ApkPath -or -not (Test-Path -Path $ApkPath)) {
        $latestApk = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($latestApk) { $ApkPath = $latestApk.FullName }
    }
}

if (-not $ApkPath -or -not (Test-Path -Path $ApkPath)) {
    Write-Host "Error: standard debug APK not found." -ForegroundColor Red
    Write-Host "Expected directory: $projectRoot\app_v2\build\outputs\apk\standard\$variant" -ForegroundColor Red
    Write-Host "Build it first:  .\scripts\builders\build-standard-debug.ps1" -ForegroundColor Gray
    exit 1
}

$apkSizeMb = [math]::Round((Get-Item $ApkPath).Length / 1MB, 1)
Write-Host "APK: $ApkPath ($apkSizeMb MB)" -ForegroundColor Green

# Confirm device presence.
Write-Host "`nChecking for connected device..." -ForegroundColor Cyan
$devicesRaw = & $adb devices
$connected = @($devicesRaw | Select-String -Pattern '\tdevice$')
if ($connected.Count -eq 0) {
    Write-Host "Error: no device connected via ADB." -ForegroundColor Red
    Write-Host "Connect a phone/tablet via USB (Developer Mode + USB debugging)." -ForegroundColor Gray
    exit 1
}
if ($connected.Count -gt 1 -and -not $DeviceId) {
    Write-Host "Error: $($connected.Count) devices connected and no -DeviceId given." -ForegroundColor Red
    Write-Host "Pass -DeviceId <serial> (or export ANDROID_SERIAL); `adb devices` lists them." -ForegroundColor Gray
    exit 1
}

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
Write-Host "`nLaunch from the device: app launcher -> FastMediaSorter (standard debug)" -ForegroundColor Cyan
