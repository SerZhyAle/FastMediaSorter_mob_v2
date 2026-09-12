#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Build the FastMediaSorter VR release APK using the current release version.

.DESCRIPTION
    Stamps the build clock into the APK unless the caller pins a version. Pass
    -VersionName/-VersionCode from the release orchestrator so the VR GitHub Store asset carries
    the same version as the rest of that release spectrum (S1873).

.PARAMETER DryRun
    Validate paths and print the resolved version without invoking Gradle.

.PARAMETER VersionName
    Pin the published versionName instead of stamping the clock. Requires -VersionCode.

.PARAMETER VersionCode
    Pin the published versionCode instead of stamping the clock. Requires -VersionName.
#>

[CmdletBinding()]
param(
    [switch] $DryRun,
    [string] $VersionName,
    [int]    $VersionCode
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-vr-release.ps1" -Domain Build.Phone
try {

$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$gradlew = Join-Path $projectRoot "gradlew.bat"
# S1873: the version this script used to read out of app_v2/build.gradle.kts had no writer left
# after ADR-4, so reusing it would have shipped the sentinel. -VersionName/-VersionCode carry the
# release orchestrator's single shared stamp; without them the invocation clock applies, which is
# correct for a standalone VR build and is what every other builder now does.
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"

if ([string]::IsNullOrWhiteSpace($VersionName) -ne ($VersionCode -le 0)) {
    throw "Pass both -VersionName and -VersionCode together, or omit both to stamp from the clock."
}

if ([string]::IsNullOrWhiteSpace($VersionName)) {
    $stamp = Get-BuildVersionStamp
    $versionName = $stamp.VersionName
    $versionCode = $stamp.AppVersionCode
}
else {
    $versionName = $VersionName.Trim()
    $versionCode = $VersionCode
}

Write-Host "Building VR release APK..." -ForegroundColor Cyan
Write-Host "Version: $versionName (code: $versionCode)" -ForegroundColor Green

if ($DryRun) {
    Write-Host "Dry-run complete: Gradle invocation skipped." -ForegroundColor Cyan
    exit 0
}

Push-Location $projectRoot
try {
    Write-Host "Running: gradlew assembleVrRelease" -ForegroundColor Yellow
    & $gradlew :app_v2:assembleVrRelease "-Pfms.versionCode=$versionCode" "-Pfms.versionName=$versionName" "-Pchaquopy.enabled=false" --configuration-cache
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

# This builder was the only one of the 24 that never mirrored (found 2026-08-23): every sibling
# carried the delivery block this call replaced, so the Drive copy of vr-release silently stayed at
# the April build while DOWNLOADS advanced to June. A mirror that is stale rather than absent is the
# worse failure - it looks like a delivered artifact.
& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path $destPath -Name $destName -NoCommander

exit 0

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
