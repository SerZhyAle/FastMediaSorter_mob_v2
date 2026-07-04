# Build Photos Debug APK and Install on Device
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-photos-device.ps1"
try {

# ADB path
$adb = "C:\Users\serzh\AppData\Local\Android\Sdk\platform-tools\adb.exe"

Write-Host "Building Photos Debug APK (auto-versioned)..." -ForegroundColor Cyan
Write-Host "Features: Images only (with cloud support)" -ForegroundColor Yellow

# Generate version
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

# Start the Gradle build process
# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"
$logDir = "$projectRoot\temp"

# Update build.gradle.kts
$buildGradlePath = "$projectRoot\app_v2\build.gradle.kts"
$content = Get-Content $buildGradlePath -Raw
$content = $content -replace '(versionCode\s*=\s*)\d+', "`${1}$versionCodeInt"
$content = $content -replace '(versionName\s*=\s*)"[^"]*"', "`${1}`"$versionName`""
Set-Content $buildGradlePath $content -NoNewline

# Start the Gradle build process
& $gradlew assemblePhotosDebug "-Pchaquopy.enabled=false" --configuration-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

# Resolve actual APK path from AGP output metadata
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\photos\debug"
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
        Write-Host "Warning: Failed to parse output-metadata.json" -ForegroundColor Yellow
    }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    $latestApk = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latestApk) { $apkPath = $latestApk.FullName }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

# Wait for device
Write-Host "`nWaiting for device..." -ForegroundColor Yellow
& $adb wait-for-device

# Clear logcat before launching
Write-Host "Clearing logcat..." -ForegroundColor Cyan
& $adb logcat -c

# Install and launch the debug build
Write-Host "Installing and launching photos debug build..." -ForegroundColor Cyan
& $adb install -r -d $apkPath
& $adb shell am start -n com.sza.fastmediasorter.photos.debug/com.sza.fastmediasorter.ui.main.MainActivity

Write-Host "`nPhotos debug build launched successfully!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_photos_debug.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | photos-debug-device | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

# Zip with password and copy to Google Drive
$gdDir = "c:\GD\WORK\FastMediaSorter"
if (!(Test-Path -Path $gdDir)) {
    New-Item -ItemType Directory -Path $gdDir | Out-Null
}

# Copy raw APK to Google Drive (in addition to password-protected ZIP below).
# Recipients with security policies that block APK downloads use the .zip copy;
# the raw .apk lets fast paths skip the unzip step.
Copy-Item -Path "$downloadsDir\$destName" -Destination "$gdDir\$destName" -Force
Write-Host "APK copied to $gdDir\$destName" -ForegroundColor Green

$zipName = [System.IO.Path]::ChangeExtension($destName, ".zip")
$zipPath = "$gdDir\$zipName"

# Use 7-Zip to create password-protected archive
$7zipPath = "C:\Program Files\7-Zip\7z.exe"
if (Test-Path -Path $7zipPath) {
    & $7zipPath a -tzip -p1 "$zipPath" "$downloadsDir\$destName" | Out-Null
    Write-Host "APK zipped with password and copied to Google Drive: $zipPath" -ForegroundColor Cyan
   #  Write-Host "Password: 1" -ForegroundColor Yellow
}
else {
    Write-Host "Warning: 7-Zip not found. APK not copied to Google Drive." -ForegroundColor Yellow
    Write-Host "Install 7-Zip from https://www.7-zip.org/ to enable Google Drive upload." -ForegroundColor Yellow
}

# Copy APK to tc folder
$tcDir = "c:\GD\tc\SZA\_APP"
if (!(Test-Path -Path $tcDir)) {
    New-Item -ItemType Directory -Path $tcDir | Out-Null
}
Copy-Item -Path "$downloadsDir\$destName" -Destination "$tcDir\$destName" -Force
Write-Host "APK copied to $tcDir\$destName" -ForegroundColor Green

# Initializing log saving
if (!(Test-Path -Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = "$logDir\logcat_photos_$timestamp.log"

# Start logcat capture in background
Write-Host "Starting logcat capture in background to $logFile..." -ForegroundColor Yellow
$logcatProcess = Start-Process -FilePath $adb -ArgumentList "logcat", "-v", "threadtime" -RedirectStandardOutput $logFile -NoNewWindow -PassThru
Write-Host "Logcat capture running in background (PID: $($logcatProcess.Id))" -ForegroundColor Green
Write-Host "To stop: Stop-Process -Id $($logcatProcess.Id)" -ForegroundColor Cyan
}
finally {
    Exit-AgentLock -Name Build
}