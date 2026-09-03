# Build Lite Debug APK and Install on Device
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-lite-device.ps1" -Domain Build.Phone
try {

# ADB path
$adb = Get-ToolPath -Tool Adb

Write-Host "Building Lite Debug APK (auto-versioned)..." -ForegroundColor Cyan
Write-Host "Features: Local files only (no cloud, no EPUB)" -ForegroundColor Yellow

# S1873: one formula for the whole repository, and it travels as a build property - a build
# never writes build.gradle.kts, so the working tree stays clean and nothing has to revert it.
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
$stamp = Get-BuildVersionStamp
$versionName = $stamp.VersionName
$versionCodeInt = $stamp.AppVersionCode

Write-Host "Version: $versionName (code: $versionCodeInt)" -ForegroundColor Green

# Start the Gradle build process
# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"
$logDir = "$projectRoot\temp"

# Start the Gradle build process
& $gradlew :app_v2:assembleLiteDebug "-Pfms.versionCode=$versionCodeInt" "-Pfms.versionName=$versionName" "-Pchaquopy.enabled=false" --configuration-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

# Resolve actual APK path from AGP output metadata
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\lite\debug"
# S1972: one resolver for every builder - it selects by ABI from output-metadata.json
# and refuses to guess, where this block used to take element 0 and then the newest file.
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
$resolvedArtifact = Find-BuildArtifact -Dir $apkDir
$apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

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
Write-Host "Installing and launching lite debug build..." -ForegroundColor Cyan
& $adb install -r -d $apkPath
& $adb shell am start -n com.sza.fastmediasorter.lite.debug/com.sza.fastmediasorter.ui.main.MainActivity

Write-Host "`nLite debug build launched successfully!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_lite_debug.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | lite-debug-device | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName

# Initializing log saving
if (!(Test-Path -Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = "$logDir\logcat_lite_$timestamp.log"

# Start logcat capture in background
Write-Host "Starting logcat capture in background to $logFile..." -ForegroundColor Yellow
$logcatProcess = Start-Process -FilePath $adb -ArgumentList "logcat", "-v", "threadtime" -RedirectStandardOutput $logFile -NoNewWindow -PassThru
Write-Host "Logcat capture running in background (PID: $($logcatProcess.Id))" -ForegroundColor Green
Write-Host "To stop: Stop-Process -Id $($logcatProcess.Id)" -ForegroundColor Cyan
}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
