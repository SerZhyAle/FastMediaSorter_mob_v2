# Quick debug build script with auto-versioning
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)

param(
    [switch]$SkipZip,
    [string]$Task = ":app_v2:assembleStandardDebug",
    # A packaged debug APK is a distribution artifact - it is copied to DOWNLOADS and installed
    # by hand - so it carries its own build timestamp by default (S1873). Frozen versions belong
    # to the compile-only fast checks (fk/fc/fr/fw) that produce no APK. Pass -AutoVersion:$false
    # to opt back in when configuration-cache reuse matters more than a truthful version.
    [switch]$AutoVersion = $true,
    # S0190 Phase 01 tooling: explicit opt-in for Chaquopy on tasks that don't auto-detect.
    # Auto-enabled when -Task contains "noLegal" (case-insensitive), since the noLegal source
    # set imports com.chaquo.python.* and won't compile without the plugin.
    [switch]$Chaquopy,
    # Quiet mode: suppress UP-TO-DATE / NO-SOURCE / SKIPPED task lines, deprecated-DSL spam,
    # and known-acceptable warnings. Errors, FAIL/SUCCESS verdict, and unknown warnings are
    # always preserved. The full unfiltered output is still captured for retry-detection.
    [switch]$Quiet,

    # S1972: which slice of the split debug build to report and copy. Empty means "ask the connected
    # device", which is the right answer whenever exactly one is attached; name it explicitly when
    # several are, or when building for a device that is not plugged in.
    [string]$Abi = '',

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
# S2127: Test-KotlinStaleIncrementalState + Get-KotlinStaleIncrementalRepairArgs.
. "$PSScriptRoot\gradle-run-verdict.ps1"
Enter-BuildLockOrExit -Reason "build-debug.PS1" -Domain Build.Phone
try {

# Patterns that are suppressed in -Quiet mode. Order: most-frequent first.
# Update this list when adding intentional warnings; do NOT use it to hide real failures.
$script:QuietSuppressPatterns = @(
    '^> Task :app_v2:[A-Za-z0-9_]+ (UP-TO-DATE|NO-SOURCE|SKIPPED|FROM-CACHE)$',
    '^WARNING: The option setting ''android\.(builtInKotlin|newDsl)=false'' is deprecated\.',
    '^WARNING: API ''(applicationVariants|testVariants|unitTestVariants)'' is obsolete',
    '^The current default is ''true''\.$',
    '^It will be removed in version 10\.0 of the Android Gradle plugin\.$',
    '^The legacy variant API is disabled by default in AGP 9\.0',
    '^\s*android\.newDsl=false$',
    '^to this project''s gradle\.properties file\.$',
    '^For more information, see http://developer\.android\.com/build/r/new-dsl\.$',
    '^To determine what is calling (applicationVariants|testVariants|unitTestVariants), use -Pandroid\.debug\.obsoleteApi=true',
    '^Using the build cache is enabled, but no build caches are configured or enabled\.$',
    "^w: file:///.+/app_v2/build\.gradle\.kts:\d+:\d+: 'fun Project\.android",
    "^This class is not used for the public extensions in AGP",
    "^w: file:///.+/PlayerActivity\.kt:(678|686|689|1122):14 'open' has no effect on a final class\.$",
    '^Note: \[\d+\] Wrote GeneratedAppGlideModule',
    '^warning: The following options were not recognized by any processor:',
    '^$'  # blank lines between suppressed blocks
)

function Test-QuietSuppress([string]$line) {
    foreach ($p in $script:QuietSuppressPatterns) {
        if ($line -match $p) { return $true }
    }
    return $false
}

Write-Host "Building debug APK.." -ForegroundColor Cyan
if ($AutoVersion) {
    Write-Host "Mode: auto-versioned artifact build" -ForegroundColor Yellow
}
else {
    Write-Host "Mode: fast reusable debug build (stable Gradle version fields)" -ForegroundColor Yellow
}

# Start the Gradle build process (Debug only for speed)
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
Set-Location $projectRoot
$tempDir = Join-Path $projectRoot "temp"
if (-not (Test-Path -Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
}

$logTimestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$buildLogPath = Join-Path $tempDir "build_debug_$logTimestamp.log"


function Stop-GradleDaemons {
    Write-Host "Stopping Gradle daemons before cleanup.." -ForegroundColor DarkGray
    & "$PSScriptRoot\..\..\\gradlew.bat" --stop | Out-Null
}


function Remove-BuildPathIfExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $targetPath = Join-Path $projectRoot $RelativePath
    if (Test-Path -Path $targetPath) {
        Write-Host "Removing locked build path: $targetPath" -ForegroundColor DarkGray
        Remove-Item -Path $targetPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}


function Get-GradleExecutionHistoryRelativePath {
    $gradleDir = Join-Path $projectRoot ".gradle"
    if (-not (Test-Path -Path $gradleDir)) {
        return $null
    }

    $versionDir = Get-ChildItem -Path $gradleDir -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^\d+\.\d+(\.\d+)?$' } |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($null -eq $versionDir) {
        return $null
    }

    return ".gradle\$($versionDir.Name)\executionHistory"
}


function Invoke-DebugBuild {
    param(
        [switch]$DisableBuildCache,
        [switch]$NoDaemon,
        [switch]$NoKotlinIncremental
    )

    $versionOverrideArgs = @()
    . "$PSScriptRoot\..\utils\build-version-stamp.ps1"
    if ($script:AutoVersion) {
        $stamp = Get-BuildVersionStamp
        $versionCodeInt = $stamp.AppVersionCode
        $versionName = $stamp.VersionName
        $versionOverrideArgs = @(
            "-Pfms.versionCode=$versionCodeInt",
            "-Pfms.versionName=$versionName"
        )
        Write-Host "Version override: $versionName (code: $versionCodeInt)" -ForegroundColor Green
    }

    # S0190 Phase 01: enable Chaquopy when -Chaquopy switch is set OR when the task name
    # contains "noLegal" (the only flavor that bundles the Python runtime). Standard / lite /
    # photos / legacy builds keep Chaquopy off for speed.
    $enableChaquopy = $script:Chaquopy.IsPresent -or ($script:Task -match '(?i)noLegal')
    $chaquopyFlag = if ($enableChaquopy) { "-Pchaquopy.enabled=true" } else { "-Pchaquopy.enabled=false" }
    $gradleArgs = @($script:Task, $chaquopyFlag)
    # S1972: debug-only ABI splits - the phone and the emulator each get their own APK. Conditioned
    # on the task, not on the script, because -Task is settable and this driver would otherwise
    # slice whatever it was pointed at. Three exclusions, all load-bearing:
    #   release  - slicing one would shrink the device set it reaches (canon hard invariant 2);
    #   vr       - an x86_64 vr APK would carry no OpenXR native, the loader AAR being arm64 only;
    #   Chaquopy - the plugin refuses a build with no ndk.abiFilters, and a split build has none,
    #              because AGP refuses the two together. Measured 2026-08-26: "Variant
    #              'noLegalDebug': Chaquopy requires ndk.abiFilters". noLegal therefore stays a
    #              single arm64 APK, which is the shape the owner ruled for on 2026-08-23.
    if ($script:Task -match '(?i)debug' -and $script:Task -notmatch '(?i)vr' -and -not $enableChaquopy) {
        $gradleArgs += "-Pfms.abiSplits=true"
    }
    if ($enableChaquopy) {
        # S0276 follow-up: repo default is CC-off for direct noLegal/IDE safety, and any
        # noLegal/Chaquopy task graph must stay opted out until the plugin becomes
        # configuration-cache-compatible.
        $gradleArgs += "--no-configuration-cache"
    } else {
        # Default repo state is CC-off for direct noLegal/IDE safety, so safe graphs
        # opt back in explicitly here to keep the non-noLegal fast path.
        $gradleArgs += "--configuration-cache"
    }
    if ($DisableBuildCache) {
        $gradleArgs += "--no-build-cache"
        $gradleArgs += "--rerun-tasks"
    }
    if ($NoDaemon) {
        $gradleArgs += "--no-daemon"
    }
    if ($NoKotlinIncremental) {
        $gradleArgs += Get-KotlinStaleIncrementalRepairArgs
    }
    if ($versionOverrideArgs.Count -gt 0) {
        $gradleArgs += $versionOverrideArgs
    }
    if ($script:GradleArgs.Count -gt 0) {
        $gradleArgs += $script:GradleArgs
    }

    Write-Host "Starting Gradle: .\\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

    $outputLines = New-Object System.Collections.Generic.List[string]
    $suppressed = 0
    & "$PSScriptRoot\..\..\\gradlew.bat" @gradleArgs 2>&1 | ForEach-Object {
        $line = [string]$_
        $outputLines.Add($line)
        if ($script:Quiet -and (Test-QuietSuppress $line)) {
            $suppressed++
            return
        }
        Write-Host $line
    }
    if ($script:Quiet -and $suppressed -gt 0) {
        Write-Host "[quiet] suppressed $suppressed lines of known noise" -ForegroundColor DarkGray
    }

    return [PSCustomObject]@{
        ExitCode = $LASTEXITCODE
        Output   = ($outputLines -join [Environment]::NewLine)
    }
}

function Save-BuildLog {
    param(
        [Parameter(Mandatory = $true)]
        [string]$OutputText
    )

    Set-Content -Path $buildLogPath -Value $OutputText -Encoding utf8NoBOM
    Write-Host "Build log: $buildLogPath" -ForegroundColor DarkGray
}

$buildResult = Invoke-DebugBuild
$gradleExecutionHistoryPath = Get-GradleExecutionHistoryRelativePath

if ($buildResult.ExitCode -ne 0) {
    $isGradleCachePackError =
    ($buildResult.Output -match "Failed to store cache entry") -and
    ($buildResult.Output -match "Could not pack tree")

    $isKaptIncrementalDataLockError =
    ($buildResult.Output -match "Unable to delete directory") -and
    ($buildResult.Output -match "kapt3[\\/]incrementalData[\\/]standardDebug")

    if ($isGradleCachePackError) {
        Write-Host "`nDetected Gradle cache packing issue. Stopping daemons, cleaning volatile cache state, and retrying once..." -ForegroundColor Yellow

        Stop-GradleDaemons
        if ($null -ne $gradleExecutionHistoryPath) {
            Remove-BuildPathIfExists -RelativePath $gradleExecutionHistoryPath
        }
        Remove-BuildPathIfExists -RelativePath "app_v2\build\kotlin"

        $buildResult = Invoke-DebugBuild -DisableBuildCache -NoDaemon
    }

    # S2127: a class the Kotlin incremental state lost, reported against a source file nobody edited.
    # Distinct from the two branches around it: nothing is locked and nothing failed to pack, the
    # compile simply cannot see a class whose source file moved between source sets. The retry drops
    # incremental compilation rather than deleting caches - see Get-KotlinStaleIncrementalRepairArgs.
    $isKotlinStaleIncrementalState =
    Test-KotlinStaleIncrementalState -Lines ($buildResult.Output -split "`r?`n")

    if (($buildResult.ExitCode -ne 0) -and $isKotlinStaleIncrementalState) {
        Write-Host "`nKotlin reported a class its incremental state lost, not a source defect (S2127). Retrying once without incremental compilation..." -ForegroundColor Yellow

        $buildResult = Invoke-DebugBuild -NoKotlinIncremental
    }

    if (($buildResult.ExitCode -ne 0) -and $isKaptIncrementalDataLockError) {
        Write-Host "`nDetected Windows file lock in kapt incremental data. Stopping daemons, cleaning kapt temp folders, and retrying once..." -ForegroundColor Yellow

        Stop-GradleDaemons
        # Clear only the volatile kapt/kotlin outputs because stale locked stubs block the next incremental run.
        if ($null -ne $gradleExecutionHistoryPath) {
            Remove-BuildPathIfExists -RelativePath $gradleExecutionHistoryPath
        }
        Remove-BuildPathIfExists -RelativePath "app_v2\build\tmp\kapt3"
        Remove-BuildPathIfExists -RelativePath "app_v2\build\kotlin"

        $buildResult = Invoke-DebugBuild -DisableBuildCache -NoDaemon
    }
}

if ($buildResult.ExitCode -ne 0) {
    Save-BuildLog -OutputText $buildResult.Output
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    # S1786: auto-emit structured failure digest
    $bfdScript = Join-Path $PSScriptRoot "build-failure-digest.ps1"
    if (Test-Path $bfdScript) {
        Write-Host "`n--- Build Failure Digest ---" -ForegroundColor Yellow
        & $bfdScript -LogPath $buildLogPath
    }
    exit $buildResult.ExitCode
}

Save-BuildLog -OutputText $buildResult.Output
Write-Host "`nBuild Successful!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"

# Resolve the built APK path from AGP output metadata.
$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\standard\debug"
# S1972: one resolver for every builder - it selects by ABI from output-metadata.json
# and refuses to guess, where this block used to take element 0 and then the newest file.
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
. "$PSScriptRoot\..\utils\get-device-abi.ps1"
# arm64-v8a is the fallback, not a guess: adb refuses the property read whenever more than one
# device is online, which is this repo's normal state (phone + watch + emulator, S1986), and the
# phone is the thing a debug APK is built for. Pass -Abi x86_64 to take the emulator slice.
$wantedAbi = if ($Abi) { $Abi } else { Get-TargetDeviceAbi -Fallback 'arm64-v8a' }
try {
    $resolvedArtifact = Find-BuildArtifact -Dir $apkDir -Abi $wantedAbi
}
catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "APK location: $apkPath" -ForegroundColor Cyan

if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_standard_debug.apk"
# Judge what was produced, not what was intended. A packaging path that lost its version
# property still writes a fresh file - only the artifact's own metadata shows that the
# version inside it belongs to an older build (S1873). Checked before the copy, because the
# copy is what puts it in DOWNLOADS and on Google Drive.
if ($script:AutoVersion) {
    & (Join-Path $PSScriptRoot "..\quality\assert-artifact-version-fresh.ps1") -Path $apkPath
    if ($LASTEXITCODE -eq 1) {
        Write-Host "Refusing to distribute an artifact whose version is not its own." -ForegroundColor Red
        exit 1
    }
}

Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | standard-debug | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName -NoCommander -NoZip:$SkipZip

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
