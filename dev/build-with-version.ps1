# Build script with automatic version increment
# Version format: Y.YM.MDDH.Hmm (e.g., 2.51.2161.854 for 2025/12/16 18:54)
# versionCode format: YYMMDDHHm (e.g., 260205015 for 2026/02/05 01:51)
# Using first digit of minutes only to fit Int32.MaxValue (2147483647)

$ErrorActionPreference = "Stop"

# Navigate to project root (parent of dev/)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot
Write-Host "Working directory: $projectRoot" -ForegroundColor Gray

# Generate version code and name from current date/time
# Format: Y.YM.MDDH.Hmm where:
#   Y = first digit of year (2025 -> 2)
#   YM = last digit of year + first digit of month (2025/12 -> 51)
#   MDDH = second digit of month + day + first digit of hour (12/16/18 -> 2161)
#   Hmm = second digit of hour + minutes (18:54 -> 854)
$now = Get-Date
$year = $now.Year
$month = $now.Month
$day = $now.Day
$hour = $now.Hour
$minute = $now.Minute

$firstYearDigit = [int]($year.ToString()[0].ToString())
$lastYearDigit = [int]($year.ToString()[-1].ToString())
$firstMonthDigit = [int]($month.ToString("00")[0].ToString())
$secondMonthDigit = [int]($month.ToString("00")[1].ToString())
$dayStr = $day.ToString("00")
$firstHourDigit = [int]($hour.ToString("00")[0].ToString())
$secondHourDigit = [int]($hour.ToString("00")[1].ToString())
$minuteStr = $minute.ToString("00")
$firstMinuteDigit = [int]($minuteStr[0].ToString())

# YYMMDDHHm format (9 digits): year(2) + month(2) + day(2) + hour(2) + minute_first_digit(1)
$versionCodeStr = $now.ToString("yyMMddHH") + $firstMinuteDigit.ToString()
$versionCodeInt = [Convert]::ToInt32($versionCodeStr)  # YYMMDDHHm format (9 digits, fits in Int)
$versionName = "$firstYearDigit.$lastYearDigit$firstMonthDigit.$secondMonthDigit$dayStr$firstHourDigit.$secondHourDigit$minuteStr"

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Building with version:" -ForegroundColor Cyan
Write-Host "  versionCode: $versionCodeInt (MMddHHmm)" -ForegroundColor Green
Write-Host "  versionName: $versionName (Y.YM.MDDH.Hmm)" -ForegroundColor Green
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

# Replace versionName
$oldVersionMatch = [regex]::Match($content, '(versionName\s*=\s*)"([^"]*)"')
if ($oldVersionMatch.Success) {
    $oldVersion = $oldVersionMatch.Groups[2].Value
    Write-Host "Current versionName: $oldVersion" -ForegroundColor Yellow
}
$content = $content -replace '(versionName\s*=\s*)"[^"]*"', "`${1}`"$versionName`""
Write-Host "Updated versionName to: $versionName" -ForegroundColor Green

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
    
    # ==========================================
    # COPY APK TO DISTRIBUTION FOLDER
    # ==========================================
    # Updated path with 'standard' flavor
    $apkPath = "app_v2\build\outputs\apk\standard\debug\FastMediaSorter_debug.apk"
    $distFolder = "c:\GD\i\APK\"
    
    if (Test-Path $apkPath) {
        if (-not (Test-Path $distFolder)) {
            New-Item -ItemType Directory -Path $distFolder -Force | Out-Null
        }
        
        Write-Host "`nCopying APK to distribution folder..." -ForegroundColor Cyan
        Copy-Item $apkPath $distFolder -Force
        Write-Host "APK copied to: $distFolder" -ForegroundColor Green
        
        # Copy to Google Drive (FastMediaSorter store)
        $gdDir = "c:\GD\WORK\FastMediaSorter"
        if (-not (Test-Path $gdDir)) {
            New-Item -ItemType Directory -Path $gdDir -Force | Out-Null
        }
        $apkName = Split-Path $apkPath -Leaf
        Copy-Item $apkPath "$gdDir\$apkName" -Force
        Write-Host "APK copied to Google Drive: $gdDir\$apkName" -ForegroundColor Cyan
    }
    
    # ==========================================
    # AUTO-DEPLOY & LAUNCH (Non-blocking)
    # ==========================================
    Write-Host "`n==================================" -ForegroundColor Cyan
    Write-Host "AUTO-DEPLOY & LAUNCH (Background)" -ForegroundColor Cyan
    Write-Host "==================================" -ForegroundColor Cyan

    # 1. Find ADB
    $adb = "adb"
    if (-not (Get-Command $adb -ErrorAction SilentlyContinue)) {
        $adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
        if (Test-Path $adbPath) {
            $adb = $adbPath
        }
        else {
            Write-Host "Warning: ADB not found. Skipping deploy." -ForegroundColor Yellow
            $adb = $null
        }
    }

    if ($adb -and (Test-Path $apkPath)) {
        Write-Host "Starting background deployment (not waiting for device)..." -ForegroundColor Gray
        
        # Run all adb commands in background without waiting
        Start-Process -FilePath "powershell" -ArgumentList @(
            "-NoProfile",
            "-Command",
            "& {
                `$adb = '$adb'
                `$apkPath = '$apkPath'
                
                # Clear logcat
                & `$adb logcat -c 2>`$null
                
                # Install APK (with -d to allow downgrade)
                & `$adb install -r -d -t `$apkPath 2>`$null
                
                # Launch app
                & `$adb shell am start -n 'com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity' 2>`$null
            }"
        ) -WindowStyle Hidden
        
        Write-Host "Deployment started in background" -ForegroundColor Green
    }
   
}
else {
    Write-Host "`n==================================" -ForegroundColor Red
    Write-Host "BUILD FAILED" -ForegroundColor Red
    Write-Host "Version: $versionName" -ForegroundColor Green
    Write-Host "==================================" -ForegroundColor Red
    
    # Restore backup
    Move-Item $backupPath $buildGradlePath -Force
    Write-Host "build.gradle.kts restored from backup" -ForegroundColor Yellow
    exit 1
}

Write-Host "`nAPK location: app_v2\build\outputs\apk\standard\debug\" -ForegroundColor Cyan
