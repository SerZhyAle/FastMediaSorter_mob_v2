#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Build the Android XR release bundle (AAB) for Google Play's dedicated Android XR track (S0556).

.DESCRIPTION
    Produces app_v2/build/outputs/bundle/xrRelease/*.aab and copies it to DOWNLOADS/. The versionCode
    passed to gradle is the ordinary 9-digit app code; the xr flavor adds its own constant offset
    (xrVersionCodeOffset in app_v2/build.gradle.kts), so pass the SAME -VersionCode the phone bundle of
    the release was built with and the two artifacts still never collide under one package name.

    After the build it runs scripts/quality/assert-android-xr-manifest.ps1 on the merged release
    manifest and scripts/quality/assert-16kb-alignment.ps1 on the release native libraries, and
    fails the build when either does - an artifact the XR track would filter wrongly or reject is
    not handed over.

    Signing follows the release build type, exactly as for the phone bundle. The upload itself is
    the operator's step: store_assets/PLAY_CONSOLE_XR_TRACK.md.

.PARAMETER DryRun
    Validate paths and print the resolved version without invoking Gradle.

.PARAMETER VersionName
    Pin the published versionName instead of stamping the clock. Requires -VersionCode.

.PARAMETER VersionCode
    Pin the base versionCode (the phone's) instead of stamping the clock. Requires -VersionName.

.EXAMPLE
    pwsh -NoProfile -File scripts/builders/build-xr-release.ps1
    pwsh -NoProfile -File scripts/builders/build-xr-release.ps1 -VersionName 2.60.9261.530 -VersionCode 260926153

.NOTES
    Exit codes:
      0  bundle built, both post-build gates passed, copy written
      1  bundle missing after a green gradle run, or a post-build gate failed
      2  -VersionName and -VersionCode were not passed together
      4  the Build.Phone domain is held by another session (queued - rerun after the turn)
      any other non-zero value is gradle's own exit code
#>

[CmdletBinding()]
param(
    [switch] $DryRun,
    [string] $VersionName,
    [int]    $VersionCode
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($VersionName) -ne ($VersionCode -le 0)) {
    Write-Error 'Pass both -VersionName and -VersionCode together, or omit both to stamp from the clock.' -ErrorAction Continue
    exit 2
}

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason 'build-xr-release.ps1' -Domain Build.Phone
try {
    $projectRoot = (Resolve-Path "$PSScriptRoot\..\..\").Path
    $gradlew = Join-Path $projectRoot 'gradlew.bat'
    . "$PSScriptRoot\..\utils\build-version-stamp.ps1"

    if ([string]::IsNullOrWhiteSpace($VersionName)) {
        $stamp = Get-BuildVersionStamp
        $versionName = $stamp.VersionName
        $versionCode = $stamp.AppVersionCode
    }
    else {
        $versionName = $VersionName.Trim()
        $versionCode = $VersionCode
    }

    Write-Host 'Building Android XR release bundle..' -ForegroundColor Cyan
    Write-Host "Version: $versionName (base code: $versionCode; the xr flavor adds its offset)" -ForegroundColor Green

    if ($DryRun) {
        Write-Host 'Dry-run complete: Gradle invocation skipped.' -ForegroundColor Cyan
        exit 0
    }

    Push-Location $projectRoot
    try {
        & $gradlew :app_v2:bundleXrRelease "-Pfms.versionCode=$versionCode" "-Pfms.versionName=$versionName" '-Pchaquopy.enabled=false' --configuration-cache
        $buildExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    if ($buildExit -ne 0) {
        Write-Host "`nAndroid XR bundle build failed." -ForegroundColor Red
        exit $buildExit
    }

    $bundleDir = Join-Path $projectRoot 'app_v2\build\outputs\bundle\xrRelease'
    $bundles = @(Get-ChildItem -Path $bundleDir -Filter *.aab -File -ErrorAction SilentlyContinue)
    if ($bundles.Count -ne 1) {
        Write-Host "Error: expected one .aab in $bundleDir, found $($bundles.Count)" -ForegroundColor Red
        exit 1
    }

    $mergedManifest = Get-ChildItem -Path (Join-Path $projectRoot 'app_v2\build\intermediates\merged_manifest\xrRelease') `
        -Filter AndroidManifest.xml -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $mergedManifest) {
        Write-Host 'Error: no merged xrRelease manifest to judge' -ForegroundColor Red
        exit 1
    }
    & pwsh -NoProfile -File (Join-Path $projectRoot 'scripts\quality\assert-android-xr-manifest.ps1') -ManifestPath $mergedManifest.FullName
    if ($LASTEXITCODE -ne 0) {
        Write-Error "build-xr-release: assert-android-xr-manifest failed (exit $LASTEXITCODE)." -ErrorAction Continue
        exit 1
    }
    & pwsh -NoProfile -File (Join-Path $projectRoot 'scripts\quality\assert-16kb-alignment.ps1') `
        -ScanRoot (Join-Path $projectRoot 'app_v2\build\intermediates\merged_native_libs\xrRelease')
    if ($LASTEXITCODE -ne 0) {
        Write-Error "build-xr-release: assert-16kb-alignment failed (exit $LASTEXITCODE)." -ErrorAction Continue
        exit 1
    }

    $downloadsDir = Join-Path $projectRoot 'DOWNLOADS'
    if (-not (Test-Path -LiteralPath $downloadsDir)) {
        New-Item -ItemType Directory -Path $downloadsDir | Out-Null
    }
    $destName = 'FastMediaSorter_xr_release.aab'
    $destPath = Join-Path $downloadsDir $destName
    Copy-Item -LiteralPath $bundles[0].FullName -Destination $destPath -Force
    Write-Host "AAB copied to $destPath" -ForegroundColor Green

    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Add-Content -Path (Join-Path $downloadsDir 'builds_versions.lst') -Value "$timestamp | xr-release | $destName | $versionName"
    exit 0
}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
