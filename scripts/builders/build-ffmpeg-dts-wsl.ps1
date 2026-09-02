<#
.SYNOPSIS
    PowerShell launcher for the WSL2 FFmpeg DTS build (NDK r27c, 16 KB compliant).

.NOTES
    Exit codes:
      0  the AAR was built, or the build script succeeded.
      1  WSL2 is unavailable, or Phase 1 setup has not been run.
      *  any other code is the WSL build script's own, forwarded unchanged.
#>

# build-ffmpeg-dts-wsl.ps1
# ══════════════════════════════════════════════════════════════════════════════
# Usage (from project root):
#   .\scripts\builders\build-ffmpeg-dts-wsl.ps1
#
# Prerequisites:
#   1. Run Phase 1 ONCE in a WSL2 terminal to prepare the Linux NDK r27c
#      environment and clone media3 / FFmpeg. This script prints the exact command with the
#      mount path already resolved for wherever the tree currently lives.
#   2. This script handles Phases 2-3 (FFmpeg configure+build, JNI bridge, AAR packaging).
#
# Output:
#   app_v2\libs\fms-ffmpeg-dts.aar  (16 KB page-size compliant)
#   After success: app_v2\build.gradle.kts is auto-updated - or run:
#   .\scripts\builders\build-ffmpeg-dts-wsl.ps1 and follow the printed steps.
# ══════════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\..\utils\project-paths.ps1"
$ProjectRoot = Get-ProjectRoot

# Convert Windows project path to WSL path. wslpath is authoritative when WSL answers, but it is
# also the thing being launched, so a local /mnt/<letter>/<rest> translation is computed first and
# used as the fallback - the mount path has to exist in the error messages printed when WSL is
# missing, which is exactly when wslpath cannot be asked (S2326 step 04.3).
$MountPath = '/mnt/' + $ProjectRoot.Substring(0, 1).ToLower() + ($ProjectRoot.Substring(2) -replace '\\', '/')
$WslProjectRoot = wsl wslpath -a $ProjectRoot.Replace('\', '/') 2>$null
if (-not $WslProjectRoot) { $WslProjectRoot = $MountPath }
$Phase1Script = "${MountPath}/temp/wsl2-phase1-setup.sh"
$BuildScript = "${WslProjectRoot}/scripts/builders/build-ffmpeg-dts.sh"
$OutDir = Join-Path $ProjectRoot "app_v2\libs"

Write-Host ""
Write-Host "══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host " FMS: Custom FFmpeg DTS build (Phases 2-3) via WSL2"           -ForegroundColor Cyan
Write-Host " Script : $BuildScript"
Write-Host " Output : $OutDir\fms-ffmpeg-dts.aar"
Write-Host "══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Verify WSL2 is available
try {
    wsl --status 2>&1 | Out-Null
}
catch {
    Write-Error "WSL2 not available. Install from Microsoft Store or run 'wsl --install'."
    exit 1
}

# Verify Phase 1 was done: both the media3 clone AND the Linux NDK r27c must exist
$WorkDirCheck = wsl bash -c "test -d ~/ffmpeg-android-build/media && echo ok || echo missing_media" 2>&1
$NdkCheck = wsl bash -c "test -d ~/android-ndk-r27c && echo ok || echo missing_ndk" 2>&1
if ($WorkDirCheck -ne "ok") {
    Write-Host "[ERROR] Phase 1 not complete - media3 not cloned. Run this first in a WSL2 terminal:" -ForegroundColor Red
    Write-Host "  wsl bash $Phase1Script" -ForegroundColor Yellow
    exit 1
}
if ($NdkCheck -ne "ok") {
    Write-Host "[ERROR] Phase 1 not complete - Linux NDK r27c missing at ~/android-ndk-r27c. Run:" -ForegroundColor Red
    Write-Host "  wsl bash $Phase1Script" -ForegroundColor Yellow
    exit 1
}

# Ensure libs dir exists on Windows side
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# Run the build script in WSL2 (interactive output)
Write-Host "[RUN] bash $BuildScript $MountPath" -ForegroundColor Green
Write-Host ""
$exitCode = 0
# The mount path goes in as an argument: this side already knows where the tree lives, the shell
# side would have to guess it back from a literal (S2326 step 04.4).
wsl bash -c "bash '$BuildScript' '$MountPath' 2>&1"
$exitCode = $LASTEXITCODE

Write-Host ""
if ($exitCode -eq 0) {
    $AarPath = Join-Path $OutDir "fms-ffmpeg-dts.aar"
    if (Test-Path $AarPath) {
        $sizeMb = [math]::Round((Get-Item $AarPath).Length / 1MB, 1)
        Write-Host "══════════════════════════════════════════════════════════════" -ForegroundColor Green
        Write-Host " SUCCESS: fms-ffmpeg-dts.aar ($sizeMb MB)"                     -ForegroundColor Green
        Write-Host ""
        Write-Host " Phase 4 - verify + re-enable in Gradle:"
        Write-Host "   1. 16 KB alignment: already verified by the build script (readelf -l per ABI)."
        Write-Host "      The build fails fast if any slice is non-compliant; nothing to re-check manually."
        Write-Host "   2. app_v2\build.gradle.kts already wired (AAR deps: standard/noLegal/legacy/vr)."
        Write-Host "   3. .\gradlew.bat assembleStandardDebug"
        Write-Host "   4. Inspect APK: python -m zipfile -l app_v2\build\outputs\apk\standard\debug\*.apk | Select-String ffmpeg"
        Write-Host "   5. Test DTS MKV playback on device."
        Write-Host "══════════════════════════════════════════════════════════════" -ForegroundColor Green
    }
    else {
        Write-Host "[WARN] Build exited 0 but AAR not found at expected path." -ForegroundColor Yellow
        Write-Host "       Check WSL2 output above for the actual output location."
    }
}
else {
    Write-Host "[FAIL] Build script exited with code $exitCode." -ForegroundColor Red
    Write-Host "       Review WSL2 output above for errors."
    Write-Host "       Common fixes:"
    Write-Host "         - Run Phase 1 setup: temp\wsl2-phase1-setup.sh"
    Write-Host "         - Check NDK path in build-ffmpeg-dts.sh (ANDROID_NDK var)"
    Write-Host "         - Check disk space: wsl df -h /"
    exit $exitCode
}
