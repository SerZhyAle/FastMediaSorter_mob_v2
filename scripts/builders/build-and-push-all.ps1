# Master Build and Push Script
# Builds ALL flavors (Standard, Lite, Photos, Legacy, VR, noLegal) in both Debug and Release modes
# Also builds Wear OS (Debug + Release)
# Copies artifacts to DOWNLOADS folder
# Commits and pushes to git

$ErrorActionPreference = "Stop"

$scriptPath = $PSScriptRoot
$projectRoot = Resolve-Path "$scriptPath\..\.."
$gradlew = "$projectRoot\gradlew.bat"
$downloadsDir = "$projectRoot\DOWNLOADS"

# 1. Clean and Build All
Write-Host "=== Starting Full Build: Standard / Lite / Photos / Legacy / VR / NoLegal + Wear OS - Debug + Release ===" -ForegroundColor Cyan
Write-Host "This may take a while..." -ForegroundColor Yellow

# Try to force-delete locked wear build directory (Windows file lock issue)
Write-Host "Cleaning wear build directory manually..." -ForegroundColor Yellow
Remove-Item -Path "$projectRoot\wear\build" -Recurse -Force -ErrorAction SilentlyContinue

# Retry logic for locked files (Windows issue with lint cache)
$maxRetries = 2
$retryCount = 0
$buildSuccess = $false
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
# S2109: builds every flavor AND the watch module, so it genuinely holds both build domains.
Enter-BuildLockOrExit -Reason "build-and-push-all.ps1" -Domain @('Build.Phone', 'Build.Wear')
try {

$stamp = Get-BuildVersionStamp
Write-Host "Version override: $($stamp.VersionName)" -ForegroundColor Green

while (-not $buildSuccess -and $retryCount -lt $maxRetries) {
    try {
        if ($retryCount -gt 0) {
            Write-Host "`nRetry $retryCount of $maxRetries... Waiting 5 seconds for file locks to release" -ForegroundColor Yellow
            Start-Sleep -Seconds 5
            # Force delete again before retry
            Remove-Item -Path "$projectRoot\wear\build" -Recurse -Force -ErrorAction SilentlyContinue
        }
        
        Write-Host "Running Gradle build... Logs saved to build_all_log.txt" -ForegroundColor Yellow
        # Two-pass build: non-noLegal flavors first (Chaquopy disabled), then noLegal (Chaquopy enabled).
        # Chaquopy 17.x must not see non-noLegal variants (minSdk/ABI incompatibilities).
        # -Pchaquopy.enabled=false overrides local.properties so standard/lite/etc. variants are enabled.
        Write-Host "  Pass 1: non-noLegal flavors..." -ForegroundColor DarkGray
        # --max-workers=4 (2026-07-12): the full 12-variant one-shot invocation with the
        # default 8 workers OOMs the Kotlin daemon (GC overhead limit exceeded) under parallel
        # compilation of this many flavors at once - see agent-memory project_build_gotchas.md
        # #13. Capping concurrency here trades some wall-clock for not crashing the daemon.
        & $gradlew `
            :app_v2:assembleStandardDebug :app_v2:assembleLiteDebug :app_v2:assemblePhotosDebug :app_v2:assembleLegacyDebug :app_v2:assembleVrDebug `
            :app_v2:assembleStandardRelease :app_v2:assembleLiteRelease :app_v2:assemblePhotosRelease :app_v2:assembleLegacyRelease :app_v2:assembleVrRelease `
            "-Pchaquopy.enabled=false" `
            "-Pfms.versionCode=$($stamp.AppVersionCode)" `
            "-Pfms.versionName=$($stamp.VersionName)" `
            --max-workers=4 `
            --configuration-cache `
            | Tee-Object -FilePath "$projectRoot\build_all_log.txt"

        $pass1Exit = $LASTEXITCODE
        if ($pass1Exit -ne 0) {
            throw "Pass 1 (non-noLegal) failed with exit code $pass1Exit"
        }

        # S2090: the watch task names carry a flavor segment now. Only the store variant is built here -
        # this script mirrors artifacts to Drive and the tc folder, and the sideload watch variant is
        # built on request rather than on every batch run (ADR-6). The copy loop below still scans both,
        # so a noLegal build made by hand is still picked up and mirrored under its own name.
        Write-Host "  Pass 1b: Wear OS (standard)..." -ForegroundColor DarkGray
        & $gradlew `
            :wear:assembleStandardDebug :wear:assembleStandardRelease `
            "-Pchaquopy.enabled=false" `
            "-Pfms.versionCode=$($stamp.WearVersionCode)" `
            "-Pfms.versionName=$($stamp.VersionName)" `
            --max-workers=4 `
            --configuration-cache `
            | Tee-Object -Append -FilePath "$projectRoot\build_all_log.txt"

        if ($LASTEXITCODE -ne 0) {
            throw "Pass 1b (Wear OS) failed with exit code $LASTEXITCODE"
        }

        Write-Host "  Pass 2: noLegal flavor..." -ForegroundColor DarkGray
        & $gradlew `
            :app_v2:assembleNoLegalDebug :app_v2:assembleNoLegalRelease `
            "-Pchaquopy.enabled=true" `
            "-Pfms.versionCode=$($stamp.AppVersionCode)" `
            "-Pfms.versionName=$($stamp.VersionName)" `
            --max-workers=4 `
            --no-configuration-cache `
            | Tee-Object -Append -FilePath "$projectRoot\build_all_log.txt"

        if ($LASTEXITCODE -eq 0) {
            $buildSuccess = $true
        }
        else {
            $retryCount++
            if ($retryCount -ge $maxRetries) {
                Write-Host "`nBuild Exit Code Non-Zero after $maxRetries attempts! Stopping." -ForegroundColor Red
                exit $LASTEXITCODE
            }
        }
    }
    catch {
        $retryCount++
        if ($retryCount -ge $maxRetries) {
            Write-Host "`nBuild Failed after $maxRetries attempts! Check build_all_log.txt for details." -ForegroundColor Red
            exit 1
        }
    }
}

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone', 'Build.Wear')
}

if (-not $buildSuccess) {
    Write-Host "`nBuild Failed! Stopping." -ForegroundColor Red
    exit 1
}

Write-Host "`nBuild Successful! Processing Artifacts..." -ForegroundColor Green

# 2. Copy Artifacts
if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$journalPath = "$downloadsDir\builds_versions.lst"

# Resolve one artifact per variant this script actually built, rather than walking the whole apk
# tree. The walk produced a destination name from the two parent directory names, so every output
# of a split variant mapped to the SAME name and each copy silently overwrote the last, leaving
# whichever the enumeration happened to reach last (S1972 §2.1).
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"

$builtVariants = @(
    @{ Flavor = 'standard'; BuildType = 'debug' }
    @{ Flavor = 'standard'; BuildType = 'release' }
    @{ Flavor = 'lite'; BuildType = 'debug' }
    @{ Flavor = 'lite'; BuildType = 'release' }
    @{ Flavor = 'photos'; BuildType = 'debug' }
    @{ Flavor = 'photos'; BuildType = 'release' }
    @{ Flavor = 'legacy'; BuildType = 'debug' }
    @{ Flavor = 'legacy'; BuildType = 'release' }
    @{ Flavor = 'vr'; BuildType = 'debug' }
    @{ Flavor = 'vr'; BuildType = 'release' }
    @{ Flavor = 'noLegal'; BuildType = 'debug' }
    @{ Flavor = 'noLegal'; BuildType = 'release' }
)

foreach ($variant in $builtVariants) {
    $flavor = $variant.Flavor
    $buildType = $variant.BuildType
    $variantDir = "$projectRoot\app_v2\build\outputs\apk\$flavor\$buildType"

    $apk = Find-BuildArtifact -Dir $variantDir
    if (-not $apk) {
        Write-Host "Skipped: $flavor $buildType - no artifact in $variantDir" -ForegroundColor Yellow
        continue
    }

    $newName = "FastMediaSorter_${flavor}_${buildType}.apk"
    $destPath = "$downloadsDir\$newName"
    Copy-Item -Path $apk.FullName -Destination $destPath -Force
    Write-Host "Copied: $newName" -ForegroundColor Gray

    # Log to journal
    $logEntry = "$timestamp | $flavor-$buildType-batch | $newName"
    Add-Content -Path $journalPath -Value $logEntry
    
    # Copy raw APK to Google Drive AND create password-protected ZIP.
    # Both raw .apk and .zip (password=1) must live on GD:
    #   - raw .apk for recipients with normal security
    #   - .zip for recipients whose security policy blocks .apk downloads
    $gdDir = "c:\GD\WORK\FastMediaSorter"
    if (!(Test-Path -Path $gdDir)) {
        New-Item -ItemType Directory -Path $gdDir | Out-Null
    }

    Copy-Item -Path $destPath -Destination "$gdDir\$newName" -Force
    Write-Host "  -> Google Drive (raw): $newName" -ForegroundColor Gray

    $zipName = [System.IO.Path]::ChangeExtension($newName, ".zip")
    $zipPath = "$gdDir\$zipName"

    # Use 7-Zip to create password-protected archive
    $7zipPath = Get-ToolPath -Tool SevenZip
    if (Test-Path -Path $7zipPath) {
        & $7zipPath a -tzip -p1 "$zipPath" "$destPath" | Out-Null
        # Write-Host "  -> Google Drive: $zipName (password: 1)" -ForegroundColor Cyan
    }
    else {
        Write-Host "  -> Warning: 7-Zip not found. ZIP step skipped (raw APK still copied)." -ForegroundColor Yellow
    }

    # Copy APK to tc folder
    $tcDir = "c:\GD\tc\SZA\_APP"
    if (!(Test-Path -Path $tcDir)) {
        New-Item -ItemType Directory -Path $tcDir | Out-Null
    }
    Copy-Item -Path $destPath -Destination "$tcDir\$newName" -Force
}

Write-Host "`nArtifacts copied to $downloadsDir" -ForegroundColor Green

# 2b. Copy Wear OS APKs
Write-Host "`nProcessing Wear OS artifacts..." -ForegroundColor Cyan
$wearApkRoot = "$projectRoot\wear\build\outputs\apk"
if (Test-Path $wearApkRoot) {
    # S2090: the watch grew a flavor dimension, so its outputs sit one directory deeper. The store
    # variant keeps the historic destination name - callers and the Drive mirror address it by that
    # name - and only the sideload variant carries a flavor segment in what it is copied to.
    foreach ($wearFlavor in @("standard", "noLegal")) {
    foreach ($buildType in @("debug", "release")) {
        $wearApkDir = "$wearApkRoot\$wearFlavor\$buildType"
        if (-not (Test-Path $wearApkDir)) { continue }

        # S1972: one resolver for every builder - it selects by ABI from output-metadata.json
        # and refuses to guess, where this block used to take element 0 and then the newest file.
        . "$PSScriptRoot\..\utils\find-build-artifact.ps1"
        $resolvedArtifact = Find-BuildArtifact -Dir $wearApkDir
        $apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

        if (-not $apkPath -or -not (Test-Path $apkPath)) {
            Write-Host "  Warning: Wear $wearFlavor $buildType APK not found, skipping." -ForegroundColor Yellow
            continue
        }

        $wearSuffix = if ($wearFlavor -eq 'standard') { '' } else { "_$wearFlavor" }
        $wearBaseName = "FastMediaSorter_wear${wearSuffix}_$buildType"
        $wearDest = "$downloadsDir\$wearBaseName.apk"
        Copy-Item -Path $apkPath -Destination $wearDest -Force
        Write-Host "Copied: $wearBaseName.apk" -ForegroundColor Gray

        $logEntry = "$timestamp | wear-$wearFlavor-$buildType-batch | $wearBaseName.apk"
        Add-Content -Path $journalPath -Value $logEntry

        # Copy raw APK to Google Drive AND create password-protected ZIP.
        $gdDir = "c:\GD\WORK\FastMediaSorter"
        if (!(Test-Path $gdDir)) { New-Item -ItemType Directory -Path $gdDir | Out-Null }
        Copy-Item -Path $wearDest -Destination "$gdDir\$wearBaseName.apk" -Force
        $7zipPath = Get-ToolPath -Tool SevenZip
        if (Test-Path $7zipPath) {
            & $7zipPath a -tzip -p1 "$gdDir\$wearBaseName.zip" "$wearDest" | Out-Null
        }

        # Copy to tc folder
        $tcDir = "c:\GD\tc\SZA\_APP"
        if (!(Test-Path $tcDir)) { New-Item -ItemType Directory -Path $tcDir | Out-Null }
        Copy-Item -Path $wearDest -Destination "$tcDir\$wearBaseName.apk" -Force
    }
    }
} else {
    Write-Host "  Warning: Wear build output not found at $wearApkRoot" -ForegroundColor Yellow
}

# 3. Git Operations
Write-Host "`nStarting Git Push..." -ForegroundColor Cyan

# Check if there are changes to commit (specifically in DOWNLOADS or elsewhere)
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Host "Changes detected. Committing..." -ForegroundColor Yellow
    
    # Add all changes (including new APKs thanks to .gitignore update)
    git add .
    
    $commitMsg = "Build artifacts $timestamp (Standard / Lite / Photos / Legacy / VR / NoLegal + Wear OS)"
    git commit -m "$commitMsg"
    
    Write-Host "Pushing to remote..." -ForegroundColor Yellow
    git push
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Successfully pushed to git!" -ForegroundColor Green
    }
    else {
        Write-Host "Git push failed. Please check your connection or credentials." -ForegroundColor Red
    }
}
else {
    Write-Host "No changes to commit." -ForegroundColor Yellow
}

Write-Host "`nDone!" -ForegroundColor Green
