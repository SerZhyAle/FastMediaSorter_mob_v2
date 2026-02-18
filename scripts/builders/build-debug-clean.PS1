# Quick debug build script with auto-versioning and CLEAN BUILD
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)

param(
    [switch]$SkipZip
)

Write-Host "Cleaning caches and building debug APK (auto-versioned)..." -ForegroundColor Cyan

# Generate version code and name
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
$versionCodeInt = [Convert]::ToInt32($versionCodeStr)
$versionName = "$firstYearDigit.$lastYearDigit$firstMonthDigit.$secondMonthDigit$dayStr$firstHourDigit.$secondHourDigit$minuteStr"

Write-Host "Version: $versionName (code: $versionCodeInt)" -ForegroundColor Green

# Define paths
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$buildGradlePath = "$projectRoot\app_v2\build.gradle.kts"

# Update build.gradle.kts
$content = Get-Content $buildGradlePath -Raw
$content = $content -replace '(versionCode\s*=\s*)\d+', "`${1}$versionCodeInt"
$content = $content -replace '(versionName\s*=\s*)"[^"]*"', "`${1}`"$versionName`""
Set-Content $buildGradlePath $content -NoNewline

# --- CLEANING CACHES ---
Write-Host "Cleaning project..." -ForegroundColor Yellow

# Remove build directories manually for thorough clean
$appBuild = Join-Path $projectRoot "app_v2\build"
if (Test-Path $appBuild) {
    Write-Host "Removing app_v2\build..." -ForegroundColor Gray
    Remove-Item -Path $appBuild -Recurse -Force -ErrorAction SilentlyContinue
}

$rootBuild = Join-Path $projectRoot "build"
if (Test-Path $rootBuild) {
    Write-Host "Removing root build..." -ForegroundColor Gray
    Remove-Item -Path $rootBuild -Recurse -Force -ErrorAction SilentlyContinue
}

# Run Gradle Clean with no build cache
Write-Host "Running gradlew clean..." -ForegroundColor Gray
& "$projectRoot\gradlew.bat" clean --no-build-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nClean Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}
# -----------------------

# Start the Gradle build process (Debug only for speed)
Write-Host "Starting build (no cache)..." -ForegroundColor Yellow
# Using --no-build-cache to force recompilation
& "$projectRoot\gradlew.bat" assembleStandardDebug --no-build-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"

# Resolve actual APK path from AGP output metadata (fallback to newest .apk)
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\standard\debug"
$metadataPath = Join-Path $apkDir "output-metadata.json"
$apkPath = $null

if (Test-Path -Path $metadataPath) {
    try {
        $meta = Get-Content -Path $metadataPath -Raw | ConvertFrom-Json
        if ($meta.elements -and $meta.elements.Count -gt 0 -and $meta.elements[0].outputFile) {
            $apkPath = Join-Path $apkDir $meta.elements[0].outputFile
        }
    }
    catch {
        Write-Host "Warning: Failed to parse output-metadata.json, falling back to latest APK." -ForegroundColor Yellow
    }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    $latestApk = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latestApk) {
        $apkPath = $latestApk.FullName
    }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "APK location: $apkPath" -ForegroundColor Cyan

if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_standard_debug.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | standard-debug-clean | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

# Zip with password and copy to Google Drive
$gdDir = "c:\GD\WORK\FastMediaSorter"
if (!(Test-Path -Path $gdDir)) {
    New-Item -ItemType Directory -Path $gdDir | Out-Null
}

$zipName = [System.IO.Path]::ChangeExtension($destName, ".zip")
$zipPath = "$gdDir\$zipName"

# Use 7-Zip to create password-protected archive
if ($SkipZip) {
    Write-Host "SkipZip enabled: ZIP creation and Google Drive archive copy skipped." -ForegroundColor Yellow
}
else {
    $7zipPath = "C:\Program Files\7-Zip\7z.exe"
    if (Test-Path -Path $7zipPath) {
        if (Test-Path -Path $zipPath) {
            Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
        }

        & $7zipPath a -y -tzip -p1 "$zipPath" "$downloadsDir\$destName" | Out-Null

        if ($LASTEXITCODE -eq 0) {
            Write-Host "APK zipped with password and copied to Google Drive: $zipPath" -ForegroundColor Cyan
        }
        else {
            Write-Host "Warning: 7-Zip archive creation failed with exit code $LASTEXITCODE" -ForegroundColor Yellow
        }
        # Write-Host "Password: 1" -ForegroundColor Yellow
    }
    else {
        Write-Host "Warning: 7-Zip not found. APK not copied to Google Drive." -ForegroundColor Yellow
        Write-Host "Install 7-Zip from https://www.7-zip.org/ to enable Google Drive upload." -ForegroundColor Yellow
    }
}
