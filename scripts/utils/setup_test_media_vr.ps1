<#
.SYNOPSIS
    Prepares a Quest (or any Android XR) device with VR stereoscopic test media.

.DESCRIPTION
    Pushes every file under c:\Common\test_media\3dvr to the headset at
    /sdcard/Download/FastMediaSorter_Test/3DVR/<variant>/, one subfolder per
    StereoMode enum value. Used by the in-app "3DVR integration test" section
    (vr flavor only) to iterate the player across every stereo/spherical mode.

    Subfolder → StereoMode mapping:

      3DVR/
        sbs/              → SBS_FULL / SBS_HALF  (flat side-by-side)
        ou/               → OU                   (flat over-under)
        mono/             → MONO                 (plain 2D sanity)
        360_mono/         → EQUIRECT_360_MONO
        360_sbs/          → EQUIRECT_360_SBS
        360_ou/           → EQUIRECT_360_OU
        180_mono/         → EQUIRECT_180_MONO
        180_sbs/          → EQUIRECT_180_SBS
        vr180_fisheye/    → VR180_FISHEYE_SBS
        cylinder/         → CYLINDER_180

    File name convention: the stereo-mode token MUST appear somewhere in the
    filename (case-insensitive). The app's StereoDetector uses filename hints
    as a fallback when metadata is missing, and the 3DVR integration test
    relies on those hints to assert the expected route for each file.
    Examples: synth_SBS_FULL_3840x1080.jpg, Boersensaal_Hamburg_stereo_360_8K.webm.

.NOTES
    File Name      : setup_test_media_vr.ps1
    Author         : Antigravity
    Prerequisite   : ADB in PATH, Quest connected with developer mode on, and
                     c:\Common\test_media\3dvr populated (run synth_vr_test_media.py
                     at least once + drop any organic samples into the matching
                     subfolders).
#>

$ErrorActionPreference = "Stop"

# ── Paths ─────────────────────────────────────────────────────────────────────
$LocalVrRoot = "c:\Common\test_media\3dvr"
$DeviceVrRoot = "/sdcard/Download/FastMediaSorter_Test/3DVR"

# StereoMode enum value for each subfolder — read by the in-app integration test
# via a JSON manifest we push alongside the media files.
$VariantMap = [ordered]@{
    "sbs"            = "SBS_FULL"
    "ou"             = "OU"
    "mono"           = "MONO"
    "360_mono"       = "EQUIRECT_360_MONO"
    "360_sbs"        = "EQUIRECT_360_SBS"
    "360_ou"         = "EQUIRECT_360_OU"
    "180_mono"       = "EQUIRECT_180_MONO"
    "180_sbs"        = "EQUIRECT_180_SBS"
    "vr180_fisheye"  = "VR180_FISHEYE_SBS"
    "cylinder"       = "CYLINDER_180"
}

Write-Host "--- FastMediaSorter VR Test Media Setup ---" -ForegroundColor Cyan

# ── ADB resolution ────────────────────────────────────────────────────────────
$AdbExe = "adb"
$PossibleAdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (Test-Path $PossibleAdbPath) {
    $AdbExe = $PossibleAdbPath
    Write-Host "ADB: $AdbExe" -ForegroundColor DarkGray
} elseif (-not (Get-Command "adb" -ErrorAction SilentlyContinue)) {
    Write-Error "ADB not found. Add Android SDK platform-tools to PATH."
}

$adbDevices = & $AdbExe devices
$foundDevice = $adbDevices -match "\sdevice$"
if (-not $foundDevice) {
    Write-Error "No active ADB device. Enable Quest developer mode and connect via USB or Wi-Fi."
}
Write-Host "Device: $($foundDevice[0].Trim())" -ForegroundColor Green

if (-not (Test-Path $LocalVrRoot)) {
    Write-Error "VR source directory missing: $LocalVrRoot. Run synth_vr_test_media.py first."
}

# ── Helpers ───────────────────────────────────────────────────────────────────
function New-DeviceDir {
    param([string]$Path)
    & $AdbExe shell "mkdir -p `"$Path`"" | Out-Null
}

function Push-File {
    param([string]$Local, [string]$Remote, [string]$Label)
    if (-not (Test-Path $Local)) {
        Write-Warning "  SKIP (not found): $Local"
        return $false
    }
    Write-Host "  [$Label] $(Split-Path $Local -Leaf) -> $Remote" -ForegroundColor DarkGray
    & $AdbExe push $Local $Remote | Out-Null
    return $true
}

# ── 1. Wipe + recreate device tree ────────────────────────────────────────────
Write-Host "`n[1/4] Cleaning device directory..." -ForegroundColor Yellow
& $AdbExe shell "rm -rf `"$DeviceVrRoot`""
New-DeviceDir $DeviceVrRoot

Write-Host "[2/4] Creating variant subfolders..." -ForegroundColor Yellow
foreach ($sub in $VariantMap.Keys) {
    New-DeviceDir "$DeviceVrRoot/$sub"
}

# ── 2. Push every file + build manifest ───────────────────────────────────────
Write-Host "[3/4] Pushing VR test media..." -ForegroundColor Yellow

$manifest = [System.Collections.Generic.List[object]]::new()
$totalPushed = 0
$totalSkipped = 0

foreach ($sub in $VariantMap.Keys) {
    $localDir = Join-Path $LocalVrRoot $sub
    $stereoMode = $VariantMap[$sub]
    if (-not (Test-Path $localDir)) {
        Write-Warning "  Missing local dir: $localDir (skipping $stereoMode)"
        $totalSkipped++
        continue
    }

    $files = Get-ChildItem -Path $localDir -File -ErrorAction SilentlyContinue
    if (-not $files -or $files.Count -eq 0) {
        Write-Warning "  Empty: $localDir (no samples for $stereoMode)"
        $totalSkipped++
        continue
    }

    foreach ($file in $files) {
        $remote = "$DeviceVrRoot/$sub/$($file.Name)"
        $ok = Push-File -Local $file.FullName -Remote $remote -Label $stereoMode
        if ($ok) {
            $manifest.Add([pscustomobject]@{
                path       = $remote
                name       = $file.Name
                variant    = $sub
                stereoMode = $stereoMode
                sizeBytes  = $file.Length
            }) | Out-Null
            $totalPushed++
        }
    }
}

# ── 3. Manifest + media scan ──────────────────────────────────────────────────
Write-Host "[4/4] Writing manifest + triggering media scan..." -ForegroundColor Yellow

$manifestObj = [pscustomobject]@{
    version    = 1
    generated  = (Get-Date).ToString("o")
    root       = $DeviceVrRoot
    files      = $manifest
}
$manifestJson = $manifestObj | ConvertTo-Json -Depth 6
$manifestLocal = Join-Path $env:TEMP "fms_3dvr_manifest.json"
$manifestJson | Set-Content -Path $manifestLocal -Encoding UTF8
& $AdbExe push $manifestLocal "$DeviceVrRoot/3dvr_manifest.json" | Out-Null
Remove-Item $manifestLocal -ErrorAction SilentlyContinue

# Media scan (per-file broadcast; directory broadcasts are ignored on modern Android).
$deviceFilePaths = & $AdbExe shell "find `"$DeviceVrRoot`" -type f" 2>&1 |
    Where-Object { $_ -match "^/" }
$scannedCount = 0
foreach ($filePath in $deviceFilePaths) {
    $cleanPath = $filePath.Trim()
    if ([string]::IsNullOrEmpty($cleanPath)) { continue }
    & $AdbExe shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$cleanPath'" 2>&1 | Out-Null
    $scannedCount++
}
Write-Host "  Scan broadcasts sent for $scannedCount files" -ForegroundColor DarkGray
Start-Sleep -Seconds 2

# ── Summary ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " VR test media ready on headset" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host " Root on device: $DeviceVrRoot" -ForegroundColor White
Write-Host " Files pushed:   $totalPushed" -ForegroundColor White
Write-Host " Empty variants: $totalSkipped" -ForegroundColor White
Write-Host " Manifest:       $DeviceVrRoot/3dvr_manifest.json" -ForegroundColor White
Write-Host ""
Write-Host " Next step: open FastMediaSorter (vr flavor) on the headset" -ForegroundColor DarkGray
Write-Host "  Settings -> Integration tests -> 3DVR sweep" -ForegroundColor DarkGray
Write-Host ""
