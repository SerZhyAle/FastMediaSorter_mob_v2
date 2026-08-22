# Quick debug build script with auto-versioning and CLEAN BUILD
# Version format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)

param(
    [switch]$SkipZip,
    # A packaged debug APK is a distribution artifact - it is copied to DOWNLOADS and installed
    # by hand - so it carries its own build timestamp by default (S1873). Frozen versions belong
    # to the compile-only fast checks (fk/fc/fr/fw) that produce no APK. Pass -AutoVersion:$false
    # to opt back in when configuration-cache reuse matters more than a truthful version.
    [switch]$AutoVersion = $true
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-debug-clean.PS1"
try {

Write-Host "Cleaning caches and building debug APK.." -ForegroundColor Cyan
if ($AutoVersion) {
    Write-Host "Mode: auto-versioned artifact build" -ForegroundColor Yellow
}
else {
    Write-Host "Mode: clean fast build (stable Gradle version fields)" -ForegroundColor Yellow
}

# Define paths
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"

# --- CLEANING CACHES ---
Write-Host "Cleaning project..." -ForegroundColor Yellow

# Remove build directories manually for thorough clean
$appBuild = Join-Path $projectRoot "app_v2\build"
if (Test-Path $appBuild) {
    Write-Host "Removing app_v2\build..." -ForegroundColor Gray
    Remove-Item -Path $appBuild -Recurse -Force -ErrorAction SilentlyContinue
}

$rootBuild = Join-Path $projectRoot "build"
if (Test-Path $rootBuild) {
    Write-Host "Removing root build..." -ForegroundColor Gray
    Remove-Item -Path $rootBuild -Recurse -Force -ErrorAction SilentlyContinue
}

# Run Gradle Clean with no build cache
Write-Host "Running gradlew clean..." -ForegroundColor Gray
& "$projectRoot\gradlew.bat" clean --no-build-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nClean Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}
# -----------------------

# Start the Gradle build process (Debug only for speed)
Write-Host "Starting build (no cache)..." -ForegroundColor Yellow
# Using --no-build-cache to force recompilation
$gradleArgs = @(
    "assembleStandardDebug",
    "-Pchaquopy.enabled=false",
    "--no-build-cache",
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
& "$projectRoot\gradlew.bat" @gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$downloadsDir = "$projectRoot\DOWNLOADS"

# Resolve actual APK path from AGP output metadata (fallback to newest .apk)
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
        Write-Host "Warning: Failed to parse output-metadata.json, falling back to latest APK." -ForegroundColor Yellow
    }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    $latestApk = Get-ChildItem -Path $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latestApk) {
        $apkPath = $latestApk.FullName
    }
}

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "APK location: $apkPath" -ForegroundColor Cyan

if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_standard_debug.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | standard-debug-clean | $destName"
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
if ($SkipZip) {
    Write-Host "SkipZip enabled: ZIP creation and Google Drive archive copy skipped." -ForegroundColor Yellow
}
else {
    $7zipPath = "C:\Program Files\7-Zip\7z.exe"
    if (Test-Path -Path $7zipPath) {
        if (Test-Path -Path $zipPath) {
            Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
        }

        & $7zipPath a -y -tzip -p1 "$zipPath" "$downloadsDir\$destName" | Out-Null

        if ($LASTEXITCODE -eq 0) {
            Write-Host "APK zipped with password and copied to Google Drive: $zipPath" -ForegroundColor Cyan
        }
        else {
            Write-Host "Warning: 7-Zip archive creation failed with exit code $LASTEXITCODE" -ForegroundColor Yellow
        }
        # Write-Host "Password: 1" -ForegroundColor Yellow
    }
    else {
        Write-Host "Warning: 7-Zip not found. APK not copied to Google Drive." -ForegroundColor Yellow
        Write-Host "Install 7-Zip from https://www.7-zip.org/ to enable Google Drive upload." -ForegroundColor Yellow
    }
}

}
finally {
    Exit-AgentLock -Name Build
}
