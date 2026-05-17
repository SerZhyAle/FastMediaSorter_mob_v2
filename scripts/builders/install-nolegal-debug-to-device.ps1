# Install noLegal DEBUG APK on connected device WITHOUT launching it.
#
# noLegal = standard + VR + sideload-only capabilities (single universal APK,
# S0156). Installs on any Android device (4 ABIs); OpenXR bridge is compiled
# for arm64-v8a only, so VR mode is reachable only on Quest-class headsets.
#
# Why install-only (no auto-launch):
#   Symmetric with `ivr` / `ivrd`. The user picks the launch surface on the
#   device — phone launcher for standard UI, Quest Library (Unknown Sources)
#   for the VR entry. Auto-launching via `adb shell am start` would force one
#   of those surfaces and, on Quest, would skip the vrshell launch_id path
#   needed for FOCUSED XR sessions (same reason documented in
#   install-vr-debug-to-device.ps1).
#
# Usage:
#   .\scripts\builders\install-nolegal-debug-to-device.ps1
#   .\scripts\builders\install-nolegal-debug-to-device.ps1 -ApkPath C:\custom\path.apk
#   .\a.ps1 ivn

param(
    [string]$ApkPath = $null
)

$ErrorActionPreference = 'Stop'

$adb = 'C:\Users\serzh\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -Path $adb)) {
    $adb = 'adb'
}

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
$variant = 'debug'
$packageName = 'com.sza.fastmediasorter.debug'

Write-Host "noLegal APK installer — DEBUG" -ForegroundColor Cyan
Write-Host "Launch policy: install only — DO NOT auto-launch (see script header)" -ForegroundColor Magenta

# Resolve APK path.
if (-not $ApkPath) {
    $apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\noLegal\$variant"
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
    Write-Host "Error: noLegal debug APK not found." -ForegroundColor Red
    Write-Host "Expected directory: $projectRoot\app_v2\build\outputs\apk\noLegal\$variant" -ForegroundColor Red
    Write-Host "Build it first:  .\scripts\builders\build-nolegal-debug.ps1   (or: .\a.ps1 nd)" -ForegroundColor Gray
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
    Write-Host "Connect a phone/tablet via USB (Developer Mode + USB debugging) or pair Quest over Wi-Fi." -ForegroundColor Gray
    exit 1
}
if ($connected.Count -gt 1) {
    Write-Host "Warning: multiple devices connected — ADB will pick the first one or fail." -ForegroundColor Yellow
    Write-Host "Use ANDROID_SERIAL env var or -s flag in adb to disambiguate." -ForegroundColor Gray
}

# -r: replace existing, -d: downgrade allowed (handy for debug iterations).
Write-Host "`nInstalling..." -ForegroundColor Cyan
& $adb install -r -d $ApkPath
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
