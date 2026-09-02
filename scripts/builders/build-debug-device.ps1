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
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-debug-device.ps1" -Domain Build.Phone
try {

# ADB path
$adb = Get-ToolPath -Tool Adb

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
    ":app_v2:assembleStandardDebug",
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

& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName

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
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
