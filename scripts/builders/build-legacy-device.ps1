# Build Legacy Debug APK and Install on Device
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)
#
# EXIT CODES
#   0 - built, installed and launched on the resolved device.
#   1 - APK not found after a successful build, or no unambiguous target device.
#   other - the exit code of the failing gradle, adb install or adb launch call.

param(
    # S3169: with a watch also paired the unqualified adb call resolves nothing and this script
    # used to report success anyway. ANDROID_SERIAL is the default so an exported serial works
    # without touching the call site.
    [string]$DeviceId = $env:ANDROID_SERIAL
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-legacy-device.ps1" -Domain Build.Phone
try {

# ADB path
$adb = Get-ToolPath -Tool Adb

Write-Host "Building Legacy Debug APK (auto-versioned)..." -ForegroundColor Cyan
Write-Host "Features: Full (Android 6.0+ compatibility)" -ForegroundColor Yellow

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
& $gradlew :app_v2:assembleLegacyDebug "-Pfms.versionCode=$versionCodeInt" "-Pfms.versionName=$versionName" "-Pchaquopy.enabled=false" --configuration-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

# Resolve actual APK path from AGP output metadata
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\legacy\debug"
# S1972: one resolver for every builder - it selects by ABI from output-metadata.json
# and refuses to guess, where this block used to take element 0 and then the newest file.
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
$resolvedArtifact = Find-BuildArtifact -Dir $apkDir
$apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

# Resolve the target device once, before anything is sent to it (S3169).
. "$PSScriptRoot\..\devtest\lib\target-device.ps1"
try {
    $targetSerial = Resolve-TargetDevice -Adb $adb -DeviceId $DeviceId
}
catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$target = @('-s', $targetSerial)
Write-Host "`nTarget device: $targetSerial" -ForegroundColor Gray

# Wait for device
Write-Host "Waiting for device..." -ForegroundColor Yellow
& $adb @target wait-for-device

# Clear logcat before launching
Write-Host "Clearing logcat..." -ForegroundColor Cyan
& $adb @target logcat -c

# Install and launch the debug build
Write-Host "Installing and launching legacy debug build..." -ForegroundColor Cyan
& $adb @target install -r -d $apkPath
if ($LASTEXITCODE -ne 0) {
    Write-Host "Install failed on $targetSerial (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}
& $adb @target shell am start -n com.sza.fastmediasorter.legacy.debug/com.sza.fastmediasorter.ui.main.MainActivity
if ($LASTEXITCODE -ne 0) {
    Write-Host "Launch failed on $targetSerial (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nLegacy debug build launched successfully on $targetSerial!" -ForegroundColor Green
Write-Host "Compatible with Android 6.0+ (API 23+)" -ForegroundColor Yellow

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_legacy_debug.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | legacy-debug-device | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName

# Initializing log saving
if (!(Test-Path -Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = "$logDir\logcat_legacy_$timestamp.log"

# Capture the install-and-launch window the `logcat -c` above cleared for, then stop. S3297: the
# background stream this replaces was started after `am start`, so it never held the launch it was
# meant to record, and nothing ever stopped it.
. "$PSScriptRoot\..\devtest\lib\logcat-snapshot.ps1"
$snapshot = Save-DeviceLogcatSnapshot -Adb $adb -Serial $targetSerial -Path $logFile
if ($snapshot.Ok) {
    Write-Host "Logcat snapshot: $($snapshot.Lines) line(s) -> $($snapshot.Path)" -ForegroundColor Green
}
else {
    Write-Host "Logcat snapshot failed: $($snapshot.Message)" -ForegroundColor Yellow
}
}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
