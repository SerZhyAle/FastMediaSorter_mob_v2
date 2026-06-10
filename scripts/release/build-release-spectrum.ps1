#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Build the full release spectrum at ONE uniform version for GitHub Release
    publication (S0394).

.DESCRIPTION
    Stamps a single version into BOTH app_v2 and wear, then builds every release
    flavor + the wear release in the established two-pass (Chaquopy) order, so all
    artifacts share one version and the publisher can upload them under one tag.

    Flavors built (release only):
      standard, lite, photos, legacy, vr   (pass 1, Chaquopy disabled)
      wear (:wear:assembleRelease)          (pass 1)
      noLegal                               (pass 2, Chaquopy enabled)

    Out of scope (kept in the existing per-flavor builders / build-and-push-all):
      debug variants, git operations, Google Drive + tc-folder mirrors.

    Run from the release worktree on main so the follow-up publisher
    (scripts/release/publish-github-release.ps1) passes its branch guard.

.PARAMETER SkipBuild
    Stamp the uniform version into app_v2 + wear build.gradle.kts and exit without
    building. Useful to inspect the version reconciliation in isolation.

.PARAMETER ReuseVersion
    Do NOT compute a fresh version. Reuse the version already stamped in
    app_v2/build.gradle.kts (e.g. by a prior `a.ps1 r`) and align wear to it. This
    keeps the GitHub Release APKs aligned with the Google Play AAB produced by
    `a.ps1 r` in the same release window - used by the /skill-release flow.
#>

[CmdletBinding()]
param(
    [switch] $SkipBuild,
    [switch] $ReuseVersion
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
$gradlew     = Join-Path $projectRoot "gradlew.bat"
$appGradle   = Join-Path $projectRoot "app_v2\build.gradle.kts"
$wearGradle  = Join-Path $projectRoot "wear\build.gradle.kts"

# ----------------------------------------------------------------------
# Stamp one version into a module's build.gradle.kts.
# ----------------------------------------------------------------------
function Set-ModuleVersion {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [int]    $Code,
        [Parameter(Mandatory)] [string] $Name
    )
    if (-not (Test-Path -LiteralPath $Path)) { throw "build.gradle.kts not found: $Path" }
    $content = Get-Content -LiteralPath $Path -Raw
    $content = $content -replace '(versionCode\s*=\s*)\d+', "`${1}$Code"
    $content = $content -replace '(versionName\s*=\s*)"[^"]*"', "`${1}`"$Name`""
    Set-Content -LiteralPath $Path -Value $content -NoNewline
    Write-Host "  stamped $Path" -ForegroundColor DarkGray
}

# ----------------------------------------------------------------------
# Version reconciliation.
# Fresh (default): compute one version (formula identical to build-aab-release.ps1)
#   and stamp BOTH modules.
#   versionName: Y.YM.MDDH.Hmm (same for app_v2 + wear).
#   versionCode app_v2: yyMMddHH + first-minute-digit (9 digits).
#   versionCode wear:   yyMMddHH (8 digits) - wear keeps the shorter base by design.
# ReuseVersion: read the version already in app_v2 (set by a prior a.ps1 r) and
#   align only wear to it (wear code = app code without the trailing minute digit).
# ----------------------------------------------------------------------
if ($ReuseVersion) {
    $appContent = Get-Content -LiteralPath $appGradle -Raw
    $vnMatch = [regex]::Match($appContent, 'versionName\s*=\s*"([^"]+)"')
    $vcMatch = [regex]::Match($appContent, 'versionCode\s*=\s*(\d+)')
    if (-not ($vnMatch.Success -and $vcMatch.Success)) {
        throw "-ReuseVersion: cannot read versionName/versionCode from $appGradle. Run a.ps1 r first."
    }
    $versionName     = $vnMatch.Groups[1].Value
    $appVersionCode  = [int]$vcMatch.Groups[1].Value
    if ($appVersionCode -lt 100000000) {
        throw "-ReuseVersion expects a 9-digit release versionCode in app_v2 (got $appVersionCode). Run a.ps1 r first."
    }
    # Drop the trailing minute digit to obtain wear's 8-digit yyMMddHH code.
    $wearVersionCode = [int][math]::Floor($appVersionCode / 10)
    Write-Host "Reusing version: $versionName (app code $appVersionCode, wear code $wearVersionCode)" -ForegroundColor Green
    # app_v2 already carries this version; stamp only wear to match.
    Set-ModuleVersion -Path $wearGradle -Code $wearVersionCode -Name $versionName
} else {
    $now = Get-Date
    $yy  = $now.ToString("yy")
    $mon = $now.ToString("MM")
    $dd  = $now.ToString("dd")
    $HH  = $now.ToString("HH")
    $mm  = $now.ToString("mm")

    $versionName     = "$($yy[0]).$($yy[1])$($mon[0]).$($mon[1])$dd$($HH[0]).$($HH[1])$mm"
    $appVersionCode  = [Convert]::ToInt32($now.ToString("yyMMddHH") + $mm[0])
    $wearVersionCode = [Convert]::ToInt32($now.ToString("yyMMddHH"))

    Write-Host "Spectrum version: $versionName (app code $appVersionCode, wear code $wearVersionCode)" -ForegroundColor Green

    Set-ModuleVersion -Path $appGradle  -Code $appVersionCode  -Name $versionName
    Set-ModuleVersion -Path $wearGradle -Code $wearVersionCode -Name $versionName
}

if ($SkipBuild) {
    Write-Host "-SkipBuild set: version stamped, no build performed." -ForegroundColor Yellow
    exit 0
}

# ----------------------------------------------------------------------
# Two-pass release build. CWD pinned to $projectRoot so Gradle resolves the
# correct project directory regardless of how this script was invoked.
# ----------------------------------------------------------------------
Push-Location $projectRoot
try {
    Write-Host "Pass 1: non-noLegal release flavors + wear release (Chaquopy disabled).." -ForegroundColor Cyan
    & $gradlew `
        assembleStandardRelease assembleLiteRelease assemblePhotosRelease assembleLegacyRelease assembleVrRelease `
        :wear:assembleRelease `
        "-Pchaquopy.enabled=false" `
        --configuration-cache
    if ($LASTEXITCODE -ne 0) { throw "Pass 1 (non-noLegal release) failed with exit $LASTEXITCODE" }

    Write-Host "Pass 2: noLegal release (Chaquopy enabled, no configuration cache).." -ForegroundColor Cyan
    & $gradlew `
        assembleNoLegalRelease `
        "-Pchaquopy.enabled=true" `
        --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw "Pass 2 (noLegal release) failed with exit $LASTEXITCODE" }
}
finally {
    Pop-Location
}

Write-Host "`nBuild Successful! Release spectrum at version $versionName." -ForegroundColor Green

# ----------------------------------------------------------------------
# Report the resolved release APK for each flavor + wear so the publisher
# (publish-github-release.ps1) can consume them.
# ----------------------------------------------------------------------
$apkRoots = [ordered]@{
    standard = "app_v2\build\outputs\apk\standard\release"
    vr       = "app_v2\build\outputs\apk\vr\release"
    lite     = "app_v2\build\outputs\apk\lite\release"
    photos   = "app_v2\build\outputs\apk\photos\release"
    legacy   = "app_v2\build\outputs\apk\legacy\release"
    noLegal  = "app_v2\build\outputs\apk\noLegal\release"
    wear     = "wear\build\outputs\apk\release"
}

Write-Host "`nRelease spectrum APKs:" -ForegroundColor Cyan
$missing = @()
foreach ($flavor in $apkRoots.Keys) {
    $dir = Join-Path $projectRoot $apkRoots[$flavor]
    $apk = Get-ChildItem -Path $dir -Filter *.apk -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($apk) {
        Write-Host "  $flavor : $($apk.FullName)" -ForegroundColor Green
    } else {
        Write-Host "  $flavor : MISSING ($dir)" -ForegroundColor Red
        $missing += $flavor
    }
}

if ($missing.Count -gt 0) {
    throw "Release spectrum incomplete - missing APK for: $($missing -join ', ')"
}

Write-Host "`nNext: scripts/release/publish-github-release.ps1 - publishes all assets under v$versionName." -ForegroundColor Yellow
