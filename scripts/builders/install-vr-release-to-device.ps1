# Install VR RELEASE APK on connected device WITHOUT launching it.
#
# Why no auto-launch:
#   Launching a VR app via `adb shell am start` bypasses the Horizon OS VR shell,
#   so `com.oculus.vrshell.launch_id` is never put into the Intent. The Meta XR
#   runtime then registers the client with an empty `clientLaunchId`, the XR
#   session stays at VISIBLE and never reaches FOCUSED — no true immersive mode.
#   To get FOCUSED, launch the app from the Quest Library (Unknown Sources) on
#   the headset itself. See docs/DEV_OPS.md §Quest Debugging.
#
# Usage:
#   .\scripts\builders\install-vr-release-to-device.ps1
#   .\scripts\builders\install-vr-release-to-device.ps1 -ApkPath C:\custom\path.apk
#   .\a.ps1 ivr

param(
    [string]$ApkPath = $null
)

$ErrorActionPreference = 'Stop'

$adb = 'C:\Users\serzh\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -Path $adb)) {
    $adb = 'adb'
}

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
$variant = 'release'
$packageName = 'com.sza.fastmediasorter.vr'

Write-Host "VR APK installer — RELEASE" -ForegroundColor Cyan
Write-Host "Launch policy: install only — DO NOT auto-launch (see script header)" -ForegroundColor Magenta

# Resolve APK path.
if (-not $ApkPath) {
    $apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\vr\$variant"
    $metadataPath = Join-Path $apkDir 'output-metadata.json'

    if (Test-Path -Path $metadataPath) {
        try {
            $meta = Get-Content -Path $metadataPath -Raw | ConvertFrom-Json
            if ($meta.elements -and $meta.elements.Count -gt 0 -and $meta.elements[0].outputFile) {
                $ApkPath = Join-Path $apkDir $meta.elements[0].outputFile
            }
        }
        catch {
            Write-Host "Warning: failed to parse output-metadata.json — falling back to latest .apk" -ForegroundColor Yellow
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
    Write-Host "Error: VR release APK not found." -ForegroundColor Red
    Write-Host "Expected directory: $projectRoot\app_v2\build\outputs\apk\vr\$variant" -ForegroundColor Red
    Write-Host "Build it first:  .\scripts\builders\build-vr-release.ps1   (or: .\a.ps1 vr)" -ForegroundColor Gray
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
    Write-Host "Connect Quest via USB (enable Developer Mode + USB debugging) or pair over Wi-Fi." -ForegroundColor Gray
    exit 1
}
if ($connected.Count -gt 1) {
    Write-Host "Warning: multiple devices connected — ADB will pick the first one or fail." -ForegroundColor Yellow
    Write-Host "Use ANDROID_SERIAL env var or -s flag in adb to disambiguate." -ForegroundColor Gray
}

# Release build — only replace existing (no downgrade).
Write-Host "`nInstalling..." -ForegroundColor Cyan
& $adb install -r $ApkPath
if ($LASTEXITCODE -ne 0) {
    Write-Host "Install failed (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nInstalled OK: $packageName" -ForegroundColor Green
Write-Host "App NOT launched by design." -ForegroundColor Green
Write-Host "`nNext step on the headset:" -ForegroundColor Cyan
Write-Host "  Menu -> Library -> Unknown Sources -> FastMediaSorter (VR) -> tap to launch" -ForegroundColor White
Write-Host "This path gives the app a valid vrshell launch_id so the XR session can reach FOCUSED." -ForegroundColor Gray
