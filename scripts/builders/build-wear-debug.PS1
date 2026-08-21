# Quick debug build script for Wear OS
# Version format: Y.YM.MDDH.Hmm - byte-identical to app_v2. The watch versionCode is yyMMddHH
# (8 digits); app_v2 appends the first minute digit and gets 9, so the two never collide under the
# shared applicationId (S1681). A release stamps both modules from one timestamp via
# scripts/release/build-release-spectrum.ps1; this script stamps only its own debug artifact.

param(
    # The wear debug APK is a sideload DISTRIBUTION artifact - it is copied to DOWNLOADS and to
    # Google Drive and installed on the watch by hand - so it must carry the real build timestamp
    # by default (S1816). Frozen versions belong to the compile-only fast checks (fw/fwr/fwu) that
    # produce no APK. Without a stamp two watch builds separated by any amount of code change
    # report one identical versionName, and the device cannot say which one it is running.
    # Pass -AutoVersion:$false to opt back into the checked-in frozen version when
    # configuration-cache reuse matters more than a fresh version.
    [switch]$AutoVersion = $true
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-wear-debug.PS1"
try {

Write-Host "Building Wear OS debug APK.." -ForegroundColor Cyan

# Start the Gradle build process (Debug only for speed)
$gradleArgs = @(":wear:assembleDebug", "--configuration-cache")
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
if ($AutoVersion) {
    # 8 digits, deliberately one shorter than app_v2's 9: the documented derivation is
    # wear = floor(app / 10), and Play refuses two artifacts repeating a versionCode under one
    # applicationId. assert-module-version-parity.ps1 guards the checked-in pair; this override is
    # per-invocation and invisible to it.
    $stamp = Get-BuildVersionStamp
    $versionCodeInt = $stamp.WearVersionCode
    $versionName = $stamp.VersionName
    Write-Host "Version override: $versionName (code: $versionCodeInt)" -ForegroundColor Green
    $gradleArgs += @(
        "-Pfms.versionCode=$versionCodeInt",
        "-Pfms.versionName=$versionName"
    )
}
else {
    Write-Host "Mode: frozen-version build (-AutoVersion:`$false, config-cache reuse)" -ForegroundColor Yellow
}

& "$PSScriptRoot\..\..\gradlew.bat" @gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$downloadsDir = "$projectRoot\DOWNLOADS"

# Resolve actual APK path from AGP output metadata (fallback to newest .apk)
$apkDir = Join-Path $projectRoot "wear\build\outputs\apk\debug"
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
$destName = "FastMediaSorter_wear_debug.apk"
# Judge what was produced, not what was intended. A packaging path that lost its version
# property still writes a fresh file - only the artifact's own metadata shows that the
# version inside it belongs to an older build (S1873). Checked before the copy, because the
# copy is what puts it in DOWNLOADS and on Google Drive.
if ($AutoVersion) {
    & (Join-Path $PSScriptRoot "..\quality\assert-artifact-version-fresh.ps1") -Path $apkDir
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
$logEntry = "$timestamp | wear-debug | $destName"
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
   #  Write-Host "Password: 1" -ForegroundColor Yellow
}
else {
    Write-Host "Warning: 7-Zip not found. APK not copied to Google Drive." -ForegroundColor Yellow
    Write-Host "Install 7-Zip from https://www.7-zip.org/ to enable Google Drive upload." -ForegroundColor Yellow
}

}
finally {
    Exit-AgentLock -Name Build
}
