#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Build the FastMediaSorter VR release APK using the current release version.

.DESCRIPTION
    This script intentionally does not bump versionCode or versionName.
    Run it after build-aab-release.ps1 / .\a.ps1 r in the release worktree so
    the VR GitHub Store asset uses the same version as the standard release.

.PARAMETER DryRun
    Validate paths and print the current version without invoking Gradle.
#>

[CmdletBinding()]
param(
    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-vr-release.ps1" -Domain Build.Phone
try {

$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = Join-Path $projectRoot "gradlew.bat"
$buildGradlePath = Join-Path $projectRoot "app_v2\build.gradle.kts"

if (-not (Test-Path -LiteralPath $buildGradlePath)) {
    throw "build.gradle.kts not found at $buildGradlePath"
}

$buildContent = Get-Content -LiteralPath $buildGradlePath -Raw
# (?i): version lives in `defaultAppVersionName`/`defaultAppVersionCode` (capital V); match case-insensitively.
$versionNameMatch = [regex]::Match($buildContent, '(?i)versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($buildContent, '(?i)versionCode\s*=\s*(\d+)')

if (-not $versionNameMatch.Success) {
    throw "Could not parse versionName from $buildGradlePath"
}
if (-not $versionCodeMatch.Success) {
    throw "Could not parse versionCode from $buildGradlePath"
}

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = $versionCodeMatch.Groups[1].Value

Write-Host "Building VR release APK..." -ForegroundColor Cyan
Write-Host "Version: $versionName (code: $versionCode)" -ForegroundColor Green
Write-Host "Version bump: skipped (uses current standard release version)" -ForegroundColor Yellow

if ($DryRun) {
    Write-Host "Dry-run complete: Gradle invocation skipped." -ForegroundColor Cyan
    exit 0
}

Push-Location $projectRoot
try {
    Write-Host "Running: gradlew assembleVrRelease" -ForegroundColor Yellow
    & $gradlew :app_v2:assembleVrRelease "-Pchaquopy.enabled=false" --configuration-cache
    $buildExit = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($buildExit -ne 0) {
    Write-Host "`nVR APK Build Failed! Exiting..." -ForegroundColor Red
    exit $buildExit
}

Write-Host "`nVR APK Build Successful!" -ForegroundColor Green

$apkDir = Join-Path $projectRoot "app_v2\build\outputs\apk\vr\release"
# S1972: one resolver for every builder - it selects by ABI from output-metadata.json
# and refuses to guess, where this block used to take element 0 and then the newest file.
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
$resolvedArtifact = Find-BuildArtifact -Dir $apkDir
$apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

if (-not $apkPath -or -not (Test-Path -LiteralPath $apkPath)) {
    Write-Host "Error: VR APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "APK location: $apkPath" -ForegroundColor Cyan
Write-Host "Package name: com.sza.fastmediasorter.vr" -ForegroundColor Cyan

$downloadsDir = Join-Path $projectRoot "DOWNLOADS"
if (-not (Test-Path -LiteralPath $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}

$destName = "FastMediaSorter_vr_release.apk"
$destPath = Join-Path $downloadsDir $destName
Copy-Item -LiteralPath $apkPath -Destination $destPath -Force
Write-Host "APK copied to $destPath" -ForegroundColor Green

$journalPath = Join-Path $downloadsDir "builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | vr-release | $destName | $versionName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

# Zip with password and copy to Google Drive.
# This builder was the only one of the 24 that never mirrored (found 2026-08-23): every sibling
# carried the block below, so the Drive copy of vr-release silently stayed at the April build while
# DOWNLOADS advanced to June. A mirror that is stale rather than absent is the worse failure - it
# looks like a delivered artifact.
$gdDir = "c:\GD\WORK\FastMediaSorter"
if (!(Test-Path -Path $gdDir)) {
    New-Item -ItemType Directory -Path $gdDir | Out-Null
}

# Copy raw APK to Google Drive (in addition to password-protected ZIP below).
# Recipients with security policies that block APK downloads use the .zip copy;
# the raw .apk lets fast paths skip the unzip step.
Copy-Item -Path $destPath -Destination "$gdDir\$destName" -Force
Write-Host "APK copied to $gdDir\$destName" -ForegroundColor Green

$zipName = [System.IO.Path]::ChangeExtension($destName, ".zip")
$zipPath = "$gdDir\$zipName"

# Use 7-Zip to create password-protected archive
$7zipPath = "C:\Program Files\7-Zip\7z.exe"
if (Test-Path -Path $7zipPath) {
    & $7zipPath a -tzip -p1 "$zipPath" $destPath | Out-Null
    Write-Host "APK zipped with password and copied to Google Drive: $zipPath" -ForegroundColor Cyan
}

exit 0

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
