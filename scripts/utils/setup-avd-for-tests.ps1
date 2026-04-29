# Setup AVD for Maestro Tests
# Автоматическая настройка эмулятора для Maestro тестов
# Usage: .\scripts\utils\setup-avd-for-tests.ps1

param(
    [switch]$SkipPermissions
)

$ErrorActionPreference = "Continue"

function Get-AdbPath {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) { return $adb.Source }

    $paths = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe"
    )
    foreach ($p in $paths) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  🚀 AVD Setup for Maestro Tests" -ForegroundColor Cyan
Write-Host "  FastMediaSorter v2" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# Check ADB
$adb = Get-AdbPath
if (-not $adb) {
    Write-Host "❌ ADB not found!" -ForegroundColor Red
    Write-Host "Install Android SDK Platform Tools" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ ADB found: $adb" -ForegroundColor Green

# Wait for device
Write-Host ""
Write-Host "⏳ Waiting for device..." -ForegroundColor Yellow
& $adb wait-for-device

# Check boot completed
$bootCheck = & $adb shell getprop sys.boot_completed 2>$null
if ($bootCheck -ne "1") {
    Write-Host "⚠ Device is booting, waiting 10 seconds..." -ForegroundColor Yellow
    Start-Sleep -Seconds 10
}

Write-Host "✓ Device ready" -ForegroundColor Green

# Disable animations
Write-Host ""
Write-Host "🎬 Disabling animations (CRITICAL for Maestro)..." -ForegroundColor Yellow
& $adb shell settings put global window_animation_scale 0.0
& $adb shell settings put global transition_animation_scale 0.0
& $adb shell settings put global animator_duration_scale 0.0

$animCheck = & $adb shell settings get global window_animation_scale
if ($animCheck -eq "0.0" -or $animCheck -eq "null") {
    Write-Host "✓ Animations disabled" -ForegroundColor Green
}
else {
    Write-Host "⚠ Animation settings may not have applied (manual check recommended)" -ForegroundColor Yellow
}

# Clear logcat
Write-Host ""
Write-Host "🗑️  Clearing logcat..." -ForegroundColor Yellow
& $adb logcat -c 2>$null
Write-Host "✓ Logcat cleared" -ForegroundColor Green

# Create test directories
Write-Host ""
Write-Host "📁 Creating test media directories..." -ForegroundColor Yellow
& $adb shell mkdir -p /sdcard/Pictures/test_media 2>$null
& $adb shell mkdir -p /sdcard/Movies/test_media 2>$null
& $adb shell mkdir -p /sdcard/Music/test_media 2>$null
& $adb shell mkdir -p /sdcard/Documents/test_media 2>$null

& $adb shell chmod 777 /sdcard/Pictures/test_media 2>$null
& $adb shell chmod 777 /sdcard/Movies/test_media 2>$null
& $adb shell chmod 777 /sdcard/Music/test_media 2>$null
& $adb shell chmod 777 /sdcard/Documents/test_media 2>$null

Write-Host "✓ Test directories created" -ForegroundColor Green

# Grant permissions if app installed
$appInstalled = & $adb shell pm list packages | Select-String "com.sza.fastmediasorter"
if ($appInstalled -and -not $SkipPermissions) {
    Write-Host ""
    Write-Host "🔐 Granting storage permissions to FastMediaSorter..." -ForegroundColor Yellow
    
    & $adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_EXTERNAL_STORAGE 2>$null
    & $adb shell pm grant com.sza.fastmediasorter.debug android.permission.WRITE_EXTERNAL_STORAGE 2>$null
    & $adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_MEDIA_IMAGES 2>$null
    & $adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_MEDIA_VIDEO 2>$null
    & $adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_MEDIA_AUDIO 2>$null
    & $adb shell pm grant com.sza.fastmediasorter.debug android.permission.POST_NOTIFICATIONS 2>$null
    
    Write-Host "✓ Permissions granted" -ForegroundColor Green
}
elseif (-not $appInstalled) {
    Write-Host ""
    Write-Host "ℹ️  App not installed yet (permissions will be handled during test run)" -ForegroundColor Cyan
}

# Check memory
Write-Host ""
Write-Host "💾 Checking available memory..." -ForegroundColor Yellow
$memInfo = & $adb shell cat /proc/meminfo | Select-String "MemAvailable"
if ($memInfo) {
    Write-Host "  $memInfo" -ForegroundColor Gray
    # Parse available memory in KB
    if ($memInfo -match "(\d+)\s+kB") {
        $memKB = [int]$matches[1]
        $memMB = [math]::Round($memKB / 1024, 0)
        if ($memMB -lt 1500) {
            Write-Host "⚠ Low memory: ${memMB} MB (recommended: 1500+ MB)" -ForegroundColor Yellow
            Write-Host "  Consider increasing AVD RAM or closing apps" -ForegroundColor Gray
        }
        else {
            Write-Host "✓ Memory: ${memMB} MB available" -ForegroundColor Green
        }
    }
}

# Check hardware acceleration
Write-Host ""
Write-Host "🎮 Checking hardware graphics..." -ForegroundColor Yellow
$glesCheck = & $adb shell getprop ro.kernel.qemu.gles 2>$null
if ($glesCheck -eq "1") {
    Write-Host "✓ Hardware acceleration: ON" -ForegroundColor Green
}
else {
    Write-Host "⚠ Hardware acceleration may be OFF" -ForegroundColor Yellow
    Write-Host "  Enable in AVD settings: Graphics = Hardware - GLES 2.0" -ForegroundColor Gray
}

# Summary
Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  ✅ AVD Configuration Complete!" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. (Optional) Upload test media files to /sdcard/**/test_media/" -ForegroundColor Gray
Write-Host "     See: maestro/AVD_SETUP_FOR_TESTS.md for details" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Run smoke tests:" -ForegroundColor Gray
Write-Host "     .\scripts\utils\run-maestro-smoke.ps1" -ForegroundColor Yellow
Write-Host ""
Write-Host "  3. Run stress tests with monitoring:" -ForegroundColor Gray
Write-Host "     .\scripts\utils\run-maestro-stress.ps1 -Suite all -Monitor -Report" -ForegroundColor Yellow
Write-Host ""
Write-Host "For full setup guide, see:" -ForegroundColor Cyan
Write-Host "  maestro/AVD_SETUP_FOR_TESTS.md" -ForegroundColor White
Write-Host ""
