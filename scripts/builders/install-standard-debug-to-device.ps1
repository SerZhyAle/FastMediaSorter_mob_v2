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
if ($connected.Count -gt 1) {
    Write-Host "Warning: multiple devices connected - ADB will pick the first one or fail." -ForegroundColor Yellow
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
Write-Host "`nLaunch from the device: app launcher -> FastMediaSorter (standard debug)" -ForegroundColor Cyan
