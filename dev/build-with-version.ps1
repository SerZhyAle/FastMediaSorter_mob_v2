# Build script with automatic version increment
# Version format: Y.YM.MDDH.Hmm (e.g., 2.51.2161.854 for 2025/12/16 18:54)
# versionCode format: YYMMDDHHm (e.g., 260205015 for 2026/02/05 01:51)
# Using first digit of minutes only to fit Int32.MaxValue (2147483647)
#
# Exit codes:
#   0 - build succeeded
#   1 - build failed, or -VersionName was passed without -VersionCode (or the reverse)

param(
    # S1873: an orchestrator that already resolved a stamp passes it in, so the artifact and
    # whatever the caller names after it (a git tag, a changelog file) cannot drift apart. Omitted,
    # this script stamps from its own clock exactly as before.
    [string]$VersionName,
    [int]$VersionCode
)

$ErrorActionPreference = "Stop"

# Navigate to project root (parent of dev/)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot
. "$PSScriptRoot\..\scripts\utils\project-paths.ps1"
Write-Host "Working directory: $projectRoot" -ForegroundColor Gray

# Branch awareness: warn when building from main
$currentBranch = (git branch --show-current 2>$null).Trim()
if ($currentBranch -eq "main") {
    Write-Host ""
    Write-Host "!! BUILDING FROM 'main' - this is a release-caliber build !!" -ForegroundColor Yellow
    Write-Host "   If this is intentional (release or hotfix), continue." -ForegroundColor Yellow
    Write-Host "   If you meant to build from a DEBUG branch, switch first." -ForegroundColor Yellow
    Write-Host ""
} else {
    Write-Host "Branch: $currentBranch" -ForegroundColor DarkGray
}

function Stop-GradleDaemons {
    Write-Host "Stopping Gradle daemons before cleanup.." -ForegroundColor DarkGray
    & .\gradlew.bat --stop | Out-Null
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

function Invoke-VersionedBuild {
    param(
        [switch]$DisableBuildCache,
        [switch]$NoDaemon
    )

    $gradleArgs = @(
        "assembleStandardDebug",
        "-Pfms.versionCode=$script:versionCodeInt",
        "-Pfms.versionName=$script:versionName",
        "-Pchaquopy.enabled=false",
        "--configuration-cache")
    if ($DisableBuildCache) {
        $gradleArgs += "--no-build-cache"
        $gradleArgs += "--rerun-tasks"
    }
    if ($NoDaemon) {
        $gradleArgs += "--no-daemon"
    }

    Write-Host "Starting Gradle: .\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

    $outputLines = New-Object System.Collections.Generic.List[string]
    & .\gradlew.bat @gradleArgs 2>&1 | ForEach-Object {
        $line = [string]$_
        $outputLines.Add($line)
        Write-Host $line
    }

    return [PSCustomObject]@{
        ExitCode = $LASTEXITCODE
        Output   = ($outputLines -join [Environment]::NewLine)
    }
}

# Generate version code and name from current date/time
# Format: Y.YM.MDDH.Hmm where:
#   Y = first digit of year (2025 -> 2)
#   YM = last digit of year + first digit of month (2025/12 -> 51)
#   MDDH = second digit of month + day + first digit of hour (12/16/18 -> 2161)
#   Hmm = second digit of hour + minutes (18:54 -> 854)
# S1873: one formula for the whole repository, and it travels as a build property. This script used
# to rewrite both build.gradle.kts files in place and leave a .backup beside one of them; ADR-4
# retired that mechanism, because a rewritten constant cannot be told apart from historical residue
# and the mutation forced the release flow to revert two files after every run.
. "$PSScriptRoot\..\scripts\utils\build-version-stamp.ps1"
if ($PSBoundParameters.ContainsKey('VersionName') -ne $PSBoundParameters.ContainsKey('VersionCode')) {
    Write-Error "build-with-version: pass -VersionName and -VersionCode together or neither - half a stamp would put one version in the artifact and another in whatever the caller names after it."
    exit 1
}
if ($VersionName) {
    $versionCodeInt = $VersionCode
    $versionName = $VersionName
    Write-Host "Version supplied by caller (S1873): the artifact and the caller's tag share one stamp." -ForegroundColor DarkGray
} else {
    $stamp = Get-BuildVersionStamp
    $versionCodeInt = $stamp.AppVersionCode
    $versionName = $stamp.VersionName
}

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Building with version:" -ForegroundColor Cyan
Write-Host "  versionCode: $versionCodeInt (yyMMddHHm)" -ForegroundColor Green
Write-Host "  versionName: $versionName (Y.YM.MDDH.Hmm)" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Cyan

# Run gradle build
Write-Host "`nStarting Gradle build..." -ForegroundColor Cyan

$buildResult = Invoke-VersionedBuild
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

        $buildResult = Invoke-VersionedBuild -DisableBuildCache -NoDaemon
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

        $buildResult = Invoke-VersionedBuild -DisableBuildCache -NoDaemon
    }
}

if ($buildResult.ExitCode -eq 0) {
    Write-Host "`n==================================" -ForegroundColor Green
    Write-Host "BUILD SUCCESSFUL" -ForegroundColor Green
    Write-Host "Version: $versionName" -ForegroundColor Green
    Write-Host "==================================" -ForegroundColor Green
    
    # ==========================================
    # COPY APK TO DISTRIBUTION FOLDER
    # ==========================================
    # Updated path with 'standard' flavor
    $apkPath = "app_v2\build\outputs\apk\standard\debug\FastMediaSorter_debug.apk"
    $distFolder = Get-ArtifactSink -Kind Apk

    if (Test-Path $apkPath) {
        $apkName = Split-Path $apkPath -Leaf

        if ($distFolder) {
            Write-Host "`nCopying APK to distribution folder..." -ForegroundColor Cyan
            Copy-Item $apkPath $distFolder -Force
            Write-Host "APK copied to: $distFolder" -ForegroundColor Green
        }

        # -NoZip: this path has never produced the password archive its siblings do, and adding one
        # here would change what a recipient of this builder's output finds on Drive.
        & "$PSScriptRoot\..\scripts\utils\publish-artifact.ps1" -Path $apkPath -Name $apkName -NoZip
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
    # Nothing to restore: S1873 made the version a build property, so a failed run leaves the
    # working tree exactly as it found it.
    exit 1
}

Write-Host "`nAPK location: app_v2\build\outputs\apk\standard\debug\" -ForegroundColor Cyan
