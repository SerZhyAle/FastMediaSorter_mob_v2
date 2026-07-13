# Master Build and Push Script
# Builds ALL flavors (Standard, Lite, Photos, Legacy, VR) in both Debug and Release modes
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

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-and-push-all.ps1"
try {

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
            assembleStandardDebug assembleLiteDebug assemblePhotosDebug assembleLegacyDebug assembleVrDebug `
            assembleStandardRelease assembleLiteRelease assemblePhotosRelease assembleLegacyRelease assembleVrRelease `
            :wear:assembleDebug :wear:assembleRelease `
            "-Pchaquopy.enabled=false" `
            --max-workers=4 `
            --configuration-cache `
            | Tee-Object -FilePath "$projectRoot\build_all_log.txt"

        $pass1Exit = $LASTEXITCODE
        if ($pass1Exit -ne 0) {
            throw "Pass 1 (non-noLegal) failed with exit code $pass1Exit"
        }

        Write-Host "  Pass 2: noLegal flavor..." -ForegroundColor DarkGray
        & $gradlew `
            assembleNoLegalDebug assembleNoLegalRelease `
            "-Pchaquopy.enabled=true" `
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
    Exit-AgentLock -Name Build
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

# Find all generated APKs
$apkFiles = Get-ChildItem -Path "$projectRoot\app_v2\build\outputs\apk" -Filter "*.apk" -Recurse

foreach ($apk in $apkFiles) {
    # Determine flavor/variant from path or name (e.g. ...\standard\debug\FastMediaSorter_debug.apk)
    # Generic unique name generation
    
    # We want: FastMediaSorter_standard_debug.apk
    # Current structure example: ...\standard\debug\FastMediaSorter_debug.apk
    
    $parentDir = $apk.Directory.Name # e.g. "debug" or "release"
    $grandParentDir = $apk.Directory.Parent.Name # e.g. "standard", "lite"
    
    $flavor = $grandParentDir
    $buildType = $parentDir
    
    if ($flavor -match "apk") { 
        # Fallback if structure is different
        $newName = $apk.Name
    }
    else {
        $newName = "FastMediaSorter_${flavor}_${buildType}.apk"
    }
    
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
    $7zipPath = "C:\Program Files\7-Zip\7z.exe"
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
    foreach ($buildType in @("debug", "release")) {
        $wearApkDir = "$wearApkRoot\$buildType"
        if (-not (Test-Path $wearApkDir)) { continue }

        # Prefer output-metadata.json; fall back to newest .apk
        $apkPath = $null
        $metaPath = "$wearApkDir\output-metadata.json"
        if (Test-Path $metaPath) {
            try {
                $meta = Get-Content $metaPath -Raw | ConvertFrom-Json
                if ($meta.elements -and $meta.elements.Count -gt 0) {
                    $apkPath = Join-Path $wearApkDir $meta.elements[0].outputFile
                }
            } catch { }
        }
        if (-not $apkPath -or -not (Test-Path $apkPath)) {
            $latest = Get-ChildItem $wearApkDir -Filter *.apk -ErrorAction SilentlyContinue |
                      Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($latest) { $apkPath = $latest.FullName }
        }

        if (-not $apkPath -or -not (Test-Path $apkPath)) {
            Write-Host "  Warning: Wear $buildType APK not found, skipping." -ForegroundColor Yellow
            continue
        }

        $wearDest = "$downloadsDir\FastMediaSorter_wear_$buildType.apk"
        Copy-Item -Path $apkPath -Destination $wearDest -Force
        Write-Host "Copied: FastMediaSorter_wear_$buildType.apk" -ForegroundColor Gray

        $logEntry = "$timestamp | wear-$buildType-batch | FastMediaSorter_wear_$buildType.apk"
        Add-Content -Path $journalPath -Value $logEntry

        # Copy raw APK to Google Drive AND create password-protected ZIP.
        $gdDir = "c:\GD\WORK\FastMediaSorter"
        if (!(Test-Path $gdDir)) { New-Item -ItemType Directory -Path $gdDir | Out-Null }
        Copy-Item -Path $wearDest -Destination "$gdDir\FastMediaSorter_wear_$buildType.apk" -Force
        $7zipPath = "C:\Program Files\7-Zip\7z.exe"
        if (Test-Path $7zipPath) {
            & $7zipPath a -tzip -p1 "$gdDir\FastMediaSorter_wear_$buildType.zip" "$wearDest" | Out-Null
        }

        # Copy to tc folder
        $tcDir = "c:\GD\tc\SZA\_APP"
        if (!(Test-Path $tcDir)) { New-Item -ItemType Directory -Path $tcDir | Out-Null }
        Copy-Item -Path $wearDest -Destination "$tcDir\FastMediaSorter_wear_$buildType.apk" -Force
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
