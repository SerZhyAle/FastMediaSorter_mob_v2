# Build script with automatic version increment
# Version format: 2.YY.MMdd.HHmm (e.g., 2.25.1117.1223)
# versionCode format: shorter to fit Int max value (2147483647)
# Using format: MMDDHHmm (e.g., 11171223)

$ErrorActionPreference = "Stop"

# Generate version code from current date/time
$now = Get-Date
$yy = $now.ToString("yy")
$mmdd = $now.ToString("MMdd")
$hhmm = $now.ToString("HHmm")
$versionCodeInt = [int]$now.ToString("MMddHHmm")  # Short format for versionCode (fits in Int)
$versionName = "2.$yy.$mmdd.$hhmm"

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Building with version:" -ForegroundColor Cyan
Write-Host "  versionCode: $versionCodeInt (MMddHHmm)" -ForegroundColor Green
Write-Host "  versionName: $versionName (2.YY.MMdd.HHmm)" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Cyan

# Path to build.gradle.kts
$buildGradlePath = "app_v2\build.gradle.kts"

# Read current file
$content = Get-Content $buildGradlePath -Raw

# Backup original file
$backupPath = "$buildGradlePath.backup"
Copy-Item $buildGradlePath $backupPath -Force
Write-Host "Backup created: $backupPath" -ForegroundColor Yellow

# Replace versionCode
$content = $content -replace '(versionCode\s*=\s*)\d+', "`${1}$versionCodeInt"

# Replace versionName (with debug to verify)
$oldVersionMatch = [regex]::Match($content, '(versionName\s*=\s*)"([^"]*)"')
if ($oldVersionMatch.Success) {
    $oldVersion = $oldVersionMatch.Groups[2].Value
    Write-Host "Current versionName: $oldVersion" -ForegroundColor Yellow
}
$content = $content -replace '(versionName\s*=\s*)"[^"]*"', "`${1}`"$versionName`""

# Write updated content
Set-Content $buildGradlePath $content -NoNewline

Write-Host "[RCS] Updated build.gradle.kts with version: $versionName" -ForegroundColor Green

# Run gradle build
Write-Host "`nStarting Gradle build..." -ForegroundColor Cyan
& .\gradlew.bat :app_v2:assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n==================================" -ForegroundColor Green
    Write-Host "BUILD SUCCESSFUL" -ForegroundColor Green
    Write-Host "Version: $versionName" -ForegroundColor Green
    Write-Host "==================================" -ForegroundColor Green
    
    # Keep the new version
    Remove-Item $backupPath -Force
    Write-Host "Version committed to build.gradle.kts" -ForegroundColor Green
    
    # Gradle sync
    Write-Host "`n[SYNC] Running Gradle sync..." -ForegroundColor Cyan
    .\gradlew.bat tasks --refresh-dependencies | Out-Null
    Write-Host "[SYNC] Gradle sync completed" -ForegroundColor Green
    
    # Install and run on device
    Write-Host "`n[ADB] Installing APK on device..." -ForegroundColor Cyan
    $apkPath = "app_v2\build\outputs\apk\debug\app_v2-debug.apk"
    & adb install -r $apkPath
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[ADB] Starting app..." -ForegroundColor Cyan
        & adb shell am start -n com.sza.fastmediasorter/.ui.main.MainActivity
        Write-Host "[ADB] App launched" -ForegroundColor Green
    } else {
        Write-Host "[ADB] Install failed (device connected?)" -ForegroundColor Yellow
    }
} else {
    Write-Host "`n==================================" -ForegroundColor Red
    Write-Host "BUILD FAILED" -ForegroundColor Red
    Write-Host "==================================" -ForegroundColor Red
    
    # Restore backup
    Move-Item $backupPath $buildGradlePath -Force
    Write-Host "build.gradle.kts restored from backup" -ForegroundColor Yellow
    exit 1
}

Write-Host "`nAPK location: app_v2\build\outputs\apk\debug\" -ForegroundColor Cyan
