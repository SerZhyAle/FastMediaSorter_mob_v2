# Build, install, and launch debug build on connected device
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

param(
    # A packaged debug APK is a distribution artifact - it is copied to DOWNLOADS and installed
    # by hand - so it carries its own build timestamp by default (S1873). Frozen versions belong
    # to the compile-only fast checks (fk/fc/fr/fw) that produce no APK. Pass -AutoVersion:$false
    # to opt back in when configuration-cache reuse matters more than a truthful version.
    [switch]$AutoVersion = $true
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-debug-device.ps1"
try {

# ADB path
$adb = "C:\Users\serzh\AppData\Local\Android\Sdk\platform-tools\adb.exe"

Write-Host "Building debug APK.." -ForegroundColor Cyan
if ($AutoVersion) {
    Write-Host "Mode: auto-versioned device build" -ForegroundColor Yellow
}
else {
    Write-Host "Mode: fast device build (stable Gradle version fields)" -ForegroundColor Yellow
}

# Start the Gradle build process (Debug only for speed)
# Note: Now builds 'standardDebug' flavor automatically
# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"
$logDir = "$projectRoot\temp"

# Start the Gradle build process (Debug only for speed)
# Note: Now builds 'standardDebug' flavor automatically
$gradleArgs = @(
    "assembleStandardDebug",
    "-Pchaquopy.enabled=false",
    "--configuration-cache"
)
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
if ($AutoVersion) {
    $stamp = Get-BuildVersionStamp
    $versionCodeInt = $stamp.AppVersionCode
    $versionName = $stamp.VersionName
    Write-Host "Version override: $versionName (code: $versionCodeInt)" -ForegroundColor Green
    $gradleArgs += @(
        "-Pfms.versionCode=$versionCodeInt",
        "-Pfms.versionName=$versionName"
    )
}
& $gradlew @gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

# Resolve actual APK path from AGP output metadata
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

# Install and launch the debug build (updated path with 'standard' flavor)
Write-Host "Installing and launching debug build..." -ForegroundColor Cyan
& $adb install -r -d $apkPath
& $adb shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity

Write-Host "`nDebug build launched successfully!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_standard_debug.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | standard-debug-device | $destName"
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
    # Write-Host "Password: 1" -ForegroundColor Yellow
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
$logFile = "$logDir\logcat_$timestamp.log"

# Start logcat capture in background
Write-Host "Starting logcat capture in background to $logFile..." -ForegroundColor Yellow
$logcatProcess = Start-Process -FilePath $adb -ArgumentList "logcat", "-v", "threadtime" -RedirectStandardOutput $logFile -NoNewWindow -PassThru
Write-Host "Logcat capture running in background (PID: $($logcatProcess.Id))" -ForegroundColor Green
Write-Host "To stop: Stop-Process -Id $($logcatProcess.Id)" -ForegroundColor Cyan

}
finally {
    Exit-AgentLock -Name Build
}
