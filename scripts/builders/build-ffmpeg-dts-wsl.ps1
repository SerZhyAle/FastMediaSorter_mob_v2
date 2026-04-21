# build-ffmpeg-dts-wsl.ps1
# ══════════════════════════════════════════════════════════════════════════════
# PowerShell launcher for the WSL2 FFmpeg DTS build (NDK r27c, 16 KB compliant).
#
# Usage (from project root):
#   .\scripts\builders\build-ffmpeg-dts-wsl.ps1
#
# Prerequisites:
#   1. Run Phase 1 ONCE in a WSL2 terminal to prepare the Linux NDK r27c
#      environment and clone media3 / FFmpeg:
#        wsl bash /mnt/p/ANDROID/FastMediaSorter_mob_v2/temp/wsl2-phase1-setup.sh
#   2. This script handles Phases 2-3 (FFmpeg configure+build, JNI bridge, AAR packaging).
#
# Output:
#   app_v2\libs\fms-ffmpeg-dts.aar  (16 KB page-size compliant)
#   After success: app_v2\build.gradle.kts is auto-updated — or run:
#   .\scripts\builders\build-ffmpeg-dts-wsl.ps1 and follow the printed steps.
# ══════════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot | Split-Path | Split-Path   # ..\..\  from scripts\builders\

# Convert Windows project path to WSL path
$WslProjectRoot = wsl wslpath -a $ProjectRoot.Replace('\', '/')
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
    $wslStatus = wsl --status 2>&1
}
catch {
    Write-Error "WSL2 not available. Install from Microsoft Store or run 'wsl --install'."
    exit 1
}

# Verify Phase 1 was done: both the media3 clone AND the Linux NDK r27c must exist
$WorkDirCheck = wsl bash -c "test -d ~/ffmpeg-android-build/media && echo ok || echo missing_media" 2>&1
$NdkCheck = wsl bash -c "test -d ~/android-ndk-r27c && echo ok || echo missing_ndk" 2>&1
if ($WorkDirCheck -ne "ok") {
    Write-Host "[ERROR] Phase 1 not complete — media3 not cloned. Run this first in a WSL2 terminal:" -ForegroundColor Red
    Write-Host "  wsl bash /mnt/p/ANDROID/FastMediaSorter_mob_v2/temp/wsl2-phase1-setup.sh" -ForegroundColor Yellow
    exit 1
}
if ($NdkCheck -ne "ok") {
    Write-Host "[ERROR] Phase 1 not complete — Linux NDK r27c missing at ~/android-ndk-r27c. Run:" -ForegroundColor Red
    Write-Host "  wsl bash /mnt/p/ANDROID/FastMediaSorter_mob_v2/temp/wsl2-phase1-setup.sh" -ForegroundColor Yellow
    exit 1
}

# Ensure libs dir exists on Windows side
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# Run the build script in WSL2 (interactive output)
Write-Host "[RUN] bash $BuildScript" -ForegroundColor Green
Write-Host ""
$exitCode = 0
wsl bash -c "bash '$BuildScript' 2>&1"
$exitCode = $LASTEXITCODE

Write-Host ""
if ($exitCode -eq 0) {
    $AarPath = Join-Path $OutDir "fms-ffmpeg-dts.aar"
    if (Test-Path $AarPath) {
        $sizeMb = [math]::Round((Get-Item $AarPath).Length / 1MB, 1)
        Write-Host "══════════════════════════════════════════════════════════════" -ForegroundColor Green
        Write-Host " SUCCESS: fms-ffmpeg-dts.aar ($sizeMb MB)"                     -ForegroundColor Green
        Write-Host ""
        Write-Host " Phase 4 — verify + re-enable in Gradle:"
        Write-Host "   1. Validate 16 KB alignment (run from PowerShell):"
        Write-Host "        wsl bash -c ""readelf -l ~/ffmpeg-android-build/media/libraries/decoder_ffmpeg/jni/arm64-v8a/libffmpegJNI.so | grep LOAD"""
        Write-Host "        → All LOAD lines must show Align=0x4000 (16 KB), NOT 0x1000 (4 KB)."
        Write-Host "   2. If alignment is correct, in app_v2\build.gradle.kts:"
        Write-Host "        a) Set ENABLE_DTS_DECODER = true for standard, legacy, vr."
        Write-Host "        b) Add the three implementation lines back:"
        Write-Host "             ""standardImplementation""(files(""libs/fms-ffmpeg-dts.aar""))"
        Write-Host "             ""legacyImplementation""(files(""libs/fms-ffmpeg-dts.aar""))"
        Write-Host "             ""vrImplementation""(files(""libs/fms-ffmpeg-dts.aar""))"
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
