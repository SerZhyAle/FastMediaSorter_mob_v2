# Build Standard Debug APK and Install on Device
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151)
#
# EXIT CODES
#   0   - built, installed and launched on the resolved device.
#   1   - APK not found after a successful build, or no unambiguous target device.
#   3   - the app crashed at launch with the stale-Hilt ClassCastException even after a full rebuild
#         (S3510), so the component mismatch is in the source, not in reused build outputs.
#   124 - the build passed its wall-clock ceiling and was stopped (S3290).
#   other - the exit code of the failing gradle, adb install or adb launch call.

param(
    # S3169: without a named device adb picks for itself, and with a watch paired over Wi-Fi
    # debugging it picks nothing at all - every step printed "more than one device/emulator" while
    # this script still reported success. ANDROID_SERIAL is the default so an exported serial works
    # without touching the call site.
    [string]$DeviceId = $env:ANDROID_SERIAL,

    # S3510: rebuild from scratch regardless of what changed since the last device build.
    [switch]$Full,

    # S3513: a device-test build keeps the checked-in version, passed as -Pfms.stableVersion=true, so
    # its configuration-cache entry and its BuildConfig survive the next run. Pass -AutoVersion for a
    # build-time stamp; only `dav` and release carry one by default.
    [switch]$AutoVersion
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-standard-device.ps1" -Domain Build.Phone
try {

# ADB path
$adb = Get-ToolPath -Tool Adb

Write-Host "Building Standard Debug APK..." -ForegroundColor Cyan
Write-Host "Features: Full (cloud, EPUB, translation, OCR)" -ForegroundColor Yellow

if ($AutoVersion) {
    # S1873: one formula for the whole repository, and it travels as a build property - a build
    # never writes build.gradle.kts, so the working tree stays clean and nothing has to revert it.
    . "$PSScriptRoot\..\utils\build-version-stamp.ps1"
    $stamp = Get-BuildVersionStamp
    $versionArgs = @("-Pfms.versionCode=$($stamp.AppVersionCode)", "-Pfms.versionName=$($stamp.VersionName)")
    Write-Host "Version: $($stamp.VersionName) (code: $($stamp.AppVersionCode))" -ForegroundColor Green
}
else {
    $versionArgs = @('-Pfms.stableVersion=true')
    Write-Host "Version: checked-in, stable across device builds (S3513)" -ForegroundColor Green
}

# Start the Gradle build process
# Resolve paths relative to script location
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = "$projectRoot\gradlew.bat"
$logDir = "$projectRoot\temp"

. "$PSScriptRoot\gradle-run-verdict.ps1"
. "$PSScriptRoot\gradle-progress-watch.ps1"
. "$PSScriptRoot\device-build-mode.ps1"
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
. "$PSScriptRoot\..\devtest\lib\target-device.ps1"

# S3094: an APK installed straight onto a device must not pair a stale Hilt-generated component with
# fresh consumers. S3510: that needs a changed Hilt graph, so the full rebuild is taken only when a
# Hilt declaration or a build file moved since the last device build; the launch check below covers
# what the snapshot cannot see.
$freshArtifactArgs = @(Get-FreshGeneratedArtifactBuildArgs)
$statePath = Get-DeviceBuildStatePath -ProjectRoot $projectRoot.Path
$previousState = Read-DeviceBuildState -Path $statePath
$sourceSnapshot = Get-DeviceBuildSourceSnapshot -ProjectRoot $projectRoot.Path -Previous $previousState
$buildMode = Get-DeviceBuildMode -Previous $previousState -Current $sourceSnapshot -ForceFull:$Full
$fullRebuild = $buildMode.Mode -eq 'Full'
if ($fullRebuild) {
    Write-Host "Build mode: Full (S3510) - $($buildMode.Reasons -join '; ')." -ForegroundColor Yellow
}
else {
    Write-Host "Build mode: Incremental (S3510) - no Hilt declaration or build file changed since the last device build." -ForegroundColor Green
}

$baseGradleArgs = @(
    ':app_v2:assembleStandardDebug'
) + $versionArgs + @(
    '-Pchaquopy.enabled=false',
    '--configuration-cache'
)
$launchComponent = 'com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity'
$targetSerial = $null
$hiltCrashSurvived = $false

while ($true) {
    if ($fullRebuild) {
        $gradleArgs = $baseGradleArgs + $freshArtifactArgs
        # S3290: the cost of a full run, stated before the wait rather than after it. Two runs were
        # abandoned as hung while they were executing normally, because nothing said what a normal
        # wait looks like here.
        Write-Host "Every task re-runs from scratch: $($freshArtifactArgs -join ' ') (S3094)." -ForegroundColor Yellow
        Write-Host "A full run measured 4m 51s on 2026-09-18 - 49 tasks, none up to date. Several of them" -ForegroundColor Yellow
        Write-Host "print nothing from start to finish, so silence under a named task is the normal shape." -ForegroundColor Yellow
    }
    else {
        $gradleArgs = $baseGradleArgs
    }

    $buildLogPath = Join-Path $logDir ("build_debug_device_standard_{0}.log" -f (Get-Date -Format 'yyyyMMdd_HHmmss'))
    $run = Invoke-GradleWithProgress -GradleWrapper $gradlew -Arguments $gradleArgs -LogPath $buildLogPath

    if ($run.TimedOut) {
        Write-Host "`nBuild stopped at the ceiling inside $($run.LastTask). Transcript: $buildLogPath" -ForegroundColor Red
        exit $run.ExitCode
    }

    if ($run.ExitCode -ne 0) {
        Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
        exit $run.ExitCode
    }

    # Taken before the build and written only after it passed, so an edit made during the build is
    # still a difference on the next run.
    Save-DeviceBuildState -Path $statePath -Snapshot $sourceSnapshot

    # Resolve actual APK path from AGP output metadata
    $apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\standard\debug"
    # S1972: one resolver for every builder - it selects by ABI from output-metadata.json
    # and refuses to guess, where this block used to take element 0 and then the newest file.
    $resolvedArtifact = Find-BuildArtifact -Dir $apkDir
    $apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

    if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
        Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
        exit 1
    }

    if (-not $targetSerial) {
        # Resolve the target device once, before anything is sent to it (S3169).
        try {
            $targetSerial = Resolve-TargetDevice -Adb $adb -DeviceId $DeviceId
        }
        catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
            exit 1
        }
        $target = @('-s', $targetSerial)
        Write-Host "`nTarget device: $targetSerial" -ForegroundColor Gray

        Write-Host "Waiting for device..." -ForegroundColor Yellow
        & $adb @target wait-for-device
    }

    Write-Host "Clearing logcat..." -ForegroundColor Cyan
    & $adb @target logcat -c

    Write-Host "Installing and launching standard debug build..." -ForegroundColor Cyan
    & $adb @target install -r -d $apkPath
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Install failed on $targetSerial (exit $LASTEXITCODE)." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    & $adb @target shell am start -n $launchComponent
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Launch failed on $targetSerial (exit $LASTEXITCODE)." -ForegroundColor Red
        exit $LASTEXITCODE
    }

    $launchOutcome = Wait-DeviceLaunchOutcome -Package 'com.sza.fastmediasorter.debug' -ReadLog {
        & $adb @target logcat -d -v brief -t 2000 2>$null
    }
    if ($launchOutcome -ne 'StaleHiltCrash') { break }

    if ($fullRebuild) {
        $hiltCrashSurvived = $true
        break
    }

    Write-Host "`nThe app crashed with the stale-Hilt ClassCastException (S3094) after an incremental build." -ForegroundColor Yellow
    Write-Host "Rebuilding from scratch once (S3510)." -ForegroundColor Yellow
    # Dropped first so that a full rebuild which fails still leaves the next run no snapshot to
    # trust, and that run rebuilds from scratch too.
    Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
    $sourceSnapshot = Get-DeviceBuildSourceSnapshot -ProjectRoot $projectRoot.Path -Previous $sourceSnapshot
    $fullRebuild = $true
}

if ($hiltCrashSurvived) {
    Write-Host "`nThe app still crashes with the stale-Hilt ClassCastException after a full rebuild on $targetSerial." -ForegroundColor Red
    Write-Host "Reused outputs are ruled out; the Hilt graph in the source is inconsistent." -ForegroundColor Red
}
elseif ($launchOutcome -eq 'Displayed') {
    Write-Host "`nStandard debug build launched successfully on $targetSerial!" -ForegroundColor Green
}
else {
    Write-Host "`nStandard debug build launched on $targetSerial; no activity was reported displayed within the launch window." -ForegroundColor Yellow
}

if (-not $hiltCrashSurvived) {
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
}

# Initializing log saving
if (!(Test-Path -Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = "$logDir\logcat_standard_$timestamp.log"

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

if ($hiltCrashSurvived) { exit 3 }

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
