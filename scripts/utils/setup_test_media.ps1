<#
.SYNOPSIS
    Prepares the Android device with a structured test media layout for pre-release manual testing.

.DESCRIPTION
    Creates a directory structure on the device that covers every block in
    dev/PRE_RELEASE_MANUAL_TESTS.md:

    FastMediaSorter_Test/
      DCIM/            – Block 1 (browse, filter, sort) + Block 2 (player)
        _subfolder/    – Block 1.1 (subfolder navigation)
      Docs/            – Block 2.8 (PDF, TXT, EPUB)
      OCR/             – Block 3  (image with embedded text)
      Audio/           – Block 2.6 (audio + LRC lyrics file)
      Ops/
        src/           – Blocks 1.3-1.9 (copy/move/rename/delete/overwrite)
        dst/           – empty destination (also pre-seeded for overwrite test)
      Edge/            – Block 15 (file without extension, large video)
      Empty/           – Block 15.4 (empty-folder placeholder)

    /sdcard/Android/media/com.test.prerelease/
                       – Block 1.8 (Android/media protected path)

.NOTES
    File Name      : setup_test_media.ps1
    Author         : Antigravity
    Prerequisite   : ADB must be installed and in the PATH.
                     Connect device with USB debugging enabled before running.
#>

$ErrorActionPreference = "Stop"

# ── Paths ─────────────────────────────────────────────────────────────────────
$LocalMediaDir = "C:\GIT\FastMediaSorter_mob_v2\test_media"
$DeviceDestDir = "/sdcard/Download/FastMediaSorter_Test"
$AndroidMediaDir = "/sdcard/Android/media/com.test.prerelease"

# ── ADB resolution ────────────────────────────────────────────────────────────
Write-Host "--- FastMediaSorter Pre-Release Test Media Setup ---" -ForegroundColor Cyan

$AdbExe = "adb"
$PossibleAdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (Test-Path $PossibleAdbPath) {
    $AdbExe = $PossibleAdbPath
    Write-Host "ADB: $AdbExe" -ForegroundColor DarkGray
} elseif (-not (Get-Command "adb" -ErrorAction SilentlyContinue)) {
    Write-Error "ADB not found. Add Android SDK platform-tools to PATH or install to $env:LOCALAPPDATA\Android\Sdk\platform-tools"
}

$adbDevices = & $AdbExe devices
$foundDevice = $adbDevices -match "\sdevice$"
if (-not $foundDevice) {
    Write-Error "No active ADB device. Connect a device/emulator with USB debugging enabled."
}
Write-Host "Device: $($foundDevice[0].Trim())" -ForegroundColor Green

# ── Source check ──────────────────────────────────────────────────────────────
if (-not (Test-Path $LocalMediaDir)) {
    Write-Error "test_media directory not found: $LocalMediaDir"
}

# Helper: push a single file with a progress label
function Push-File {
    param([string]$Local, [string]$Remote, [string]$Label)
    $src = Join-Path $LocalMediaDir $Local
    if (-not (Test-Path $src)) {
        Write-Warning "  SKIP (not found): $Local"
        return
    }
    Write-Host "  [$Label] $Local → $Remote" -ForegroundColor DarkGray
    & $AdbExe push $src $Remote | Out-Null
}

# Helper: create a remote dir
function New-DeviceDir {
    param([string]$Path)
    & $AdbExe shell "mkdir -p `"$Path`"" | Out-Null
}

# ── 1. Wipe previous run ──────────────────────────────────────────────────────
Write-Host "`n[1/9] Cleaning device directories..." -ForegroundColor Yellow
& $AdbExe shell "rm -rf `"$DeviceDestDir`""
& $AdbExe shell "rm -rf `"$AndroidMediaDir`""

# ── 2. Create folder skeleton ─────────────────────────────────────────────────
Write-Host "[2/9] Creating directory structure..." -ForegroundColor Yellow
foreach ($dir in @(
    "$DeviceDestDir/DCIM/_subfolder",
    "$DeviceDestDir/Docs",
    "$DeviceDestDir/OCR",
    "$DeviceDestDir/Audio",
    "$DeviceDestDir/Ops/src",
    "$DeviceDestDir/Ops/dst",
    "$DeviceDestDir/Edge",
    "$DeviceDestDir/Empty",
    $AndroidMediaDir
)) {
    New-DeviceDir $dir
}

# ── 3. DCIM — Block 1 (browse/filter/sort) + Block 2 (player) ─────────────────
Write-Host "[3/9] Pushing DCIM files (images, video, audio)..." -ForegroundColor Yellow

#  3a. Images with "IMG_" prefix (for filter "name contains IMG" — Block 1.2)
Push-File "test_image_02_green.png"  "$DeviceDestDir/DCIM/IMG_001_green.png"   "IMG filter"
Push-File "test_image_03_blue.png"   "$DeviceDestDir/DCIM/IMG_002_blue.png"    "IMG filter"
Push-File "test_image_04_orange.png" "$DeviceDestDir/DCIM/IMG_003_orange.png"  "IMG filter"
Push-File "test_image_05_purple.png" "$DeviceDestDir/DCIM/IMG_004_purple.png"  "IMG filter"
Push-File "test_image_06_teal.png"   "$DeviceDestDir/DCIM/IMG_005_teal.png"    "IMG filter"

#  3b. Non-IMG images (must NOT appear when filter "IMG" is active — Block 1.2)
Push-File "photo1.jpg"               "$DeviceDestDir/DCIM/photo_001.jpg"        "photo"
Push-File "photo2.jpg"               "$DeviceDestDir/DCIM/photo_002.jpg"        "photo"
Push-File "image_large.jpg"          "$DeviceDestDir/DCIM/photo_large.jpg"      "photo >1MB"
Push-File "image_large (1).jpg"      "$DeviceDestDir/DCIM/photo_large_2.jpg"    "photo >1MB"
Push-File "image1.jpg"               "$DeviceDestDir/DCIM/photo_panorama.jpg"   "photo"
Push-File "image3.webp"              "$DeviceDestDir/DCIM/photo_webp_001.webp"  "webp"
Push-File "image4.webp"              "$DeviceDestDir/DCIM/photo_webp_002.webp"  "webp"
Push-File "Screenshot_Instagram.png" "$DeviceDestDir/DCIM/screenshot_001.png"  "screenshot"
Push-File "20260101_154510.jpg"      "$DeviceDestDir/DCIM/shot_20260101_a.jpg"  "camera shot"
Push-File "20260101_155306.jpg"      "$DeviceDestDir/DCIM/shot_20260101_b.jpg"  "camera shot"

#  3c. Video — Block 2.5
Push-File "test_video.mp4"           "$DeviceDestDir/DCIM/video_sample.mp4"     "video"
Push-File "VID_20260117_041714_254.mp4" "$DeviceDestDir/DCIM/video_large.mp4"  "video large"

#  3d. Subfolder — Block 1.1 subfolder navigation
Push-File "test_image_07_yellow.png" "$DeviceDestDir/DCIM/_subfolder/sub_photo_01.png" "subfolder"
Push-File "test_image_08_pink.png"   "$DeviceDestDir/DCIM/_subfolder/sub_photo_02.png" "subfolder"
Push-File "test_image_09_gray.png"   "$DeviceDestDir/DCIM/_subfolder/sub_photo_03.png" "subfolder"

# ── 4. Audio — Block 2.6 (audio player + lyrics) ─────────────────────────────
Write-Host "[4/9] Pushing audio files..." -ForegroundColor Yellow

Push-File "frank_sinatra_My_way.mp3" "$DeviceDestDir/Audio/frank_sinatra_My_way.mp3"   "audio"
Push-File "10 - I'm So Tired.flac"  "$DeviceDestDir/Audio/test_audio_flac.flac"        "audio flac"
Push-File "adele_skyfall.mp3"        "$DeviceDestDir/Audio/adele_skyfall.mp3"           "audio"

# LRC lyrics file alongside the MP3 (same base name) — Block 2.6 lyrics test
& $AdbExe shell @"
printf '[00:00.00]My Way — Frank Sinatra\n[00:06.00]And now, the end is near\n[00:11.00]And so I face the final curtain\n[00:17.00]My friend, I ll say it clear\n[00:22.00]I ll state my case, of which I m certain\n' > "$DeviceDestDir/Audio/frank_sinatra_My_way.lrc"
"@ | Out-Null
Write-Host "  [lyrics] frank_sinatra_My_way.lrc created on device" -ForegroundColor DarkGray

# ── 5. Docs — Block 2.8 (PDF, EPUB, TXT) ─────────────────────────────────────
Write-Host "[5/9] Pushing document files..." -ForegroundColor Yellow

Push-File "romcom.pdf"                     "$DeviceDestDir/Docs/test_doc_romcom.pdf"  "PDF"
Push-File "Scanned_20221101-2106.pdf"      "$DeviceDestDir/Docs/test_doc_scanned.pdf" "PDF scanned"
Push-File "easy-a.epub"                    "$DeviceDestDir/Docs/test_book.epub"        "EPUB"

# TXT file created inline on device
& $AdbExe shell @"
printf 'FastMediaSorter Pre-Release Manual Test\n========================================\nThis text file is used in Block 2.8 (document viewer test).\nLine 4. Line 5. Line 6. Line 7. Line 8. Line 9. Line 10.\nEnd of file.\n' > "$DeviceDestDir/Docs/readme.txt"
"@ | Out-Null
Write-Host "  [TXT] readme.txt created on device" -ForegroundColor DarkGray

# ── 6. OCR — Block 3 (image with readable text) ───────────────────────────────
Write-Host "[6/9] Pushing OCR test image..." -ForegroundColor Yellow

# Screenshot contains UI text visible to OCR engine
Push-File "Screenshot_Instagram.png"       "$DeviceDestDir/OCR/ocr_screenshot_text.png" "OCR"
Push-File "comeon 2 Tell us all what you can about this data.jpg" "$DeviceDestDir/OCR/ocr_data_table.jpg" "OCR data"

# ── 7. Ops — Blocks 1.3-1.9 (copy / move / rename / delete / overwrite) ───────
Write-Host "[7/9] Pushing Ops/src files (real images, not stubs)..." -ForegroundColor Yellow

# Copy test (Block 1.3) — needs 3 files
Push-File "test_image_02_green.png"  "$DeviceDestDir/Ops/src/IMG_copy_01.png"       "ops copy"
Push-File "test_image_03_blue.png"   "$DeviceDestDir/Ops/src/IMG_copy_02.png"       "ops copy"
Push-File "test_image_04_orange.png" "$DeviceDestDir/Ops/src/IMG_copy_03.png"       "ops copy"

# Move test (Block 1.4) — needs 2 files
Push-File "test_image_05_purple.png" "$DeviceDestDir/Ops/src/IMG_move_01.png"       "ops move"
Push-File "test_image_06_teal.png"   "$DeviceDestDir/Ops/src/IMG_move_02.png"       "ops move"

# Rename test (Block 1.5)
Push-File "test_image_07_yellow.png" "$DeviceDestDir/Ops/src/ops_rename_me.png"     "ops rename"

# Soft-delete test (Block 1.6)
Push-File "test_image_08_pink.png"   "$DeviceDestDir/Ops/src/ops_delete_soft.png"   "ops delete"

# Undo test (Block 1.7)
Push-File "test_image_09_gray.png"   "$DeviceDestDir/Ops/src/ops_undo_me.png"       "ops undo"

# Overwrite policy test (Block 1.9) — 2 source files
Push-File "test_image_10_black.png"  "$DeviceDestDir/Ops/src/ops_overwrite_A.png"   "ops overwrite"
Push-File "test_image_02_green.png"  "$DeviceDestDir/Ops/src/ops_overwrite_B.png"   "ops overwrite"

# Pre-seed Ops/dst with one conflicting name to trigger overwrite dialog (Block 1.9)
Push-File "test_image_02_green.png"  "$DeviceDestDir/Ops/dst/ops_overwrite_A.png"   "dst pre-seed"

# ── 8. Edge cases — Block 15 ──────────────────────────────────────────────────
Write-Host "[8/9] Setting up edge-case files..." -ForegroundColor Yellow

# File without extension — Block 15.5
& $AdbExe shell @"
printf 'This file has no extension. FastMediaSorter should display it without crashing.\nIt should appear in Browse and open a fallback view in Player.\n' > "$DeviceDestDir/Edge/file_without_extension"
"@ | Out-Null
Write-Host "  [edge] file_without_extension created" -ForegroundColor DarkGray

# Large video for transfer stability — Block 15.3
Push-File "Planet Unknown.2016.WEB-DL 1080p.mkv" "$DeviceDestDir/Edge/video_large_200mb.mkv" "large video"

# Empty folder placeholder — Block 15.4 (dir already created in step 2)
Write-Host "  [edge] Empty/ folder ready" -ForegroundColor DarkGray

# Android/media protected path — Block 1.8
# Mimics /sdcard/Android/media/org.telegram.messenger/ to test hard-delete guard.
# NOTE: On Android 11+ the shell user may lack write access here.
#       If the push fails, manually copy a file there via Telegram or WhatsApp
#       and point the app to /sdcard/Android/media/<package>/ instead.
$androidMediaResult = & $AdbExe shell "ls `"$AndroidMediaDir`"" 2>&1
if ($LASTEXITCODE -eq 0) {
    Push-File "test_image_02_green.png" "$AndroidMediaDir/protected_file_01.png" "android/media"
    Push-File "test_image_03_blue.png"  "$AndroidMediaDir/protected_file_02.png" "android/media"
    Write-Host "  [android/media] Files pushed to $AndroidMediaDir" -ForegroundColor DarkGray
} else {
    Write-Warning "  Cannot write to $AndroidMediaDir (Android 11+ restriction)."
    Write-Warning "  For Block 1.8: use real Telegram/WhatsApp folder on the device instead."
}

# ── 9. Media Store scan ───────────────────────────────────────────────────────
Write-Host "[9/9] Triggering Media Store scan..." -ForegroundColor Yellow
# Note: MEDIA_SCANNER_SCAN_FILE broadcast requires individual file paths (not dir).
# The heredoc + shell while-loop approach fails because PowerShell expands $f as
# an undefined PS variable before passing the string to adb shell.
# Fix: enumerate files via adb from PowerShell and broadcast per-file.
$deviceFilePaths = & $AdbExe shell "find `"$DeviceDestDir`" -type f" 2>&1 |
    Where-Object { $_ -match "^/" }
$scannedCount = 0
foreach ($filePath in $deviceFilePaths) {
    $cleanPath = $filePath.Trim()
    if ([string]::IsNullOrEmpty($cleanPath)) { continue }
    & $AdbExe shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$cleanPath'" 2>&1 | Out-Null
    $scannedCount++
}
Write-Host "  Scan broadcasts sent for $scannedCount files" -ForegroundColor DarkGray
Start-Sleep -Seconds 3  # give MediaStore time to process

# ── Summary ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host " Test media ready on device" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host " Folder layout → add as LOCAL resources in app:" -ForegroundColor White
Write-Host "  DCIM/        → 'LOCAL – DCIM'    (Blocks 1, 2)"
Write-Host "  Audio/       → 'LOCAL – Audio'   (Block 2.6)"
Write-Host "  Docs/        → 'LOCAL – Docs'    (Block 2.8)"
Write-Host "  OCR/         → 'LOCAL – OCR'     (Block 3)"
Write-Host "  Ops/src/     → 'LOCAL – OpsSrc'  (Blocks 1.3-1.9)"
Write-Host "  Ops/dst/     → 'LOCAL – Dest'    (copy/move destination)"
Write-Host "  Empty/       → 'LOCAL – Empty'   (Block 15.4)"
Write-Host "  Edge/        → 'LOCAL – Edge'    (Block 15.3, 15.5)"
Write-Host ""
Write-Host " Android/media path (Block 1.8):" -ForegroundColor White
Write-Host "  $AndroidMediaDir"
Write-Host "  → Add as 'LOCAL – Android/media' resource"
Write-Host "  → If empty: see warning above — use Telegram folder instead"
Write-Host ""
Write-Host " Filter test (Block 1.2):" -ForegroundColor White
Write-Host "  Filter 'IMG' → should show 5 files (IMG_001..IMG_005)"
Write-Host "  Filter '>1MB' → should show photo_large*, video_sample, video_large"
Write-Host ""
Write-Host " Lyrics test (Block 2.6):" -ForegroundColor White
Write-Host "  Open Audio/frank_sinatra_My_way.mp3 → .lrc file is alongside"
Write-Host ""
Write-Host " Overwrite test (Block 1.9):" -ForegroundColor White
Write-Host "  Copy 'ops_overwrite_A.png' from Ops/src → Ops/dst (already there)"
Write-Host "  → overwrite dialog must appear"
Write-Host ""
Write-Host " All device paths are under:" -ForegroundColor DarkGray
Write-Host "  $DeviceDestDir" -ForegroundColor DarkGray
Write-Host ""
