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
      wear (:wear:assembleRelease + :wear:bundleRelease)  (pass 1)
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

.PARAMETER Flavors
    Subset of the spectrum to build. Accepts any of:
      standard, lite, photos, legacy, vr, wear, noLegal
    plus the alias 'all' (== 'full' == 'spectrum') for every flavor.
    Case-insensitive; order-independent; de-duplicated. Omitted / empty =>
    the full spectrum (backward-compatible default for direct invocation).
    The /skill-release flow passes 'standard' by default so a plateau release
    builds only the standard edition unless extra flavors are requested.

.PARAMETER WearFlavor
    Which watch variant the 'wear' entry of -Flavors resolves to: 'standard' (default,
    the one Play accepts) or 'noLegal' (sideload). S2090 gave the wear module its own
    flavor dimension; ADR-6 of that ticket fixes the release default at the store
    variant, because building the sideload one on every release costs a full watch
    build for a difference no user of the release sees.
#>

[CmdletBinding()]
param(
    [switch]   $SkipBuild,
    [switch]   $ReuseVersion,
    [string[]] $Flavors,
    [ValidateSet('standard', 'noLegal')]
    [string]   $WearFlavor = 'standard'
)

$ErrorActionPreference = "Stop"

# ----------------------------------------------------------------------
# Resolve the requested flavor subset against the canonical spectrum.
# Canonical order doubles as build order: pass 1 (Chaquopy off) covers the
# app flavors + wear; pass 2 (Chaquopy on) covers noLegal.
# ----------------------------------------------------------------------
$allFlavors = @('standard', 'lite', 'photos', 'legacy', 'vr', 'wear', 'noLegal')

if (-not $Flavors -or $Flavors.Count -eq 0) {
    $selected = $allFlavors
} else {
    $picked = @()
    foreach ($f in $Flavors) {
        $t = "$f".Trim()
        if ($t -eq '') { continue }
        if ($t -in @('all', 'full', 'spectrum')) { $picked = $allFlavors; break }
        $canon = $allFlavors | Where-Object { $_ -ieq $t }
        if (-not $canon) {
            throw "Unknown flavor '$t'. Valid: $($allFlavors -join ', '), or 'all'."
        }
        $picked += $canon
    }
    # De-dup while preserving canonical order.
    $selected = $allFlavors | Where-Object { $_ -in $picked }
}
if (-not $selected -or $selected.Count -eq 0) {
    throw "No valid flavors selected."
}
Write-Host "Selected flavors: $($selected -join ', ')" -ForegroundColor Green

$pass1Flavors = @($selected | Where-Object { $_ -ne 'wear' -and $_ -ne 'noLegal' })
$buildWear    = 'wear'    -in $selected
$buildNoLegal = 'noLegal' -in $selected
# S2090: the wear module's task names and output directories carry a flavor segment now. Capitalised
# for the gradle task name, lower-case for the path - AGP spells them differently and always has.
$wearTaskFlavor = $WearFlavor.Substring(0, 1).ToUpperInvariant() + $WearFlavor.Substring(1)
if ($buildWear) { Write-Host "Wear variant: $WearFlavor" -ForegroundColor Green }
$buildAnyApp  = ($pass1Flavors.Count -gt 0) -or $buildNoLegal

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
$gradlew     = Join-Path $projectRoot "gradlew.bat"
$appGradle   = Join-Path $projectRoot "app_v2\build.gradle.kts"
$wearGradle  = Join-Path $projectRoot "wear\build.gradle.kts"
. (Join-Path $projectRoot 'scripts/utils/agent-lock.ps1')
. (Join-Path $projectRoot 'scripts/utils/find-build-artifact.ps1')

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
    # (?i): build.gradle.kts holds the version in `defaultAppVersionName`/`defaultAppVersionCode`
    # (capital V); the writer (PowerShell -replace) is case-insensitive, so the reader must be too.
    $vnMatch = [regex]::Match($appContent, '(?i)versionName\s*=\s*"([^"]+)"')
    $vcMatch = [regex]::Match($appContent, '(?i)versionCode\s*=\s*(\d+)')
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
    # app_v2 already carries this version; align wear only when it is in the selection.
    if ($buildWear) {
        Set-ModuleVersion -Path $wearGradle -Code $wearVersionCode -Name $versionName
    }
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

    if ($buildAnyApp) { Set-ModuleVersion -Path $appGradle  -Code $appVersionCode  -Name $versionName }
    if ($buildWear)   { Set-ModuleVersion -Path $wearGradle -Code $wearVersionCode -Name $versionName }
}

# A flavor-scoped selection (e.g. -Flavors wear, or a plateau release run with -Flavors standard)
# only ever stamps the module(s) it was asked to build, by design. That is correct for the build,
# but it is also exactly how the two modules drift apart in a checkout this script's discard step
# (the /skill-release release-worktree flow) never reaches - so warn, non-fatally, at the moment the
# stamp is written rather than leaving it for the next unrelated ticket's `fg` gate (S2117).
& (Join-Path $PSScriptRoot "..\quality\assert-module-version-parity.ps1") -Quiet
if ($LASTEXITCODE -ne 0) {
    Write-Host "Warning: app_v2/build.gradle.kts and wear/build.gradle.kts now disagree on version - see scripts/quality/assert-module-version-parity.ps1. Expected in a partial-flavor run; align both modules before this stamp is committed (S2117)." -ForegroundColor Yellow
}

if ($SkipBuild) {
    Write-Host "-SkipBuild set: version stamped, no build performed." -ForegroundColor Yellow
    exit 0
}

# ----------------------------------------------------------------------
# Two-pass release build. CWD pinned to $projectRoot so Gradle resolves the
# correct project directory regardless of how this script was invoked.
# ----------------------------------------------------------------------
# S2109: this is one of only two entry points whose task graph really spans both modules, and it
# spans them CONDITIONALLY - the flavor selection already says which. Artifact 03 measured that a
# phone release never touches the wear module, so a phone-only spectrum has no business holding the
# watch domain and blocking a sibling that is checking it.
$spectrumDomains = @()
if ($buildAnyApp) { $spectrumDomains += 'Build.Phone' }
if ($buildWear) { $spectrumDomains += 'Build.Wear' }
if ($spectrumDomains.Count -eq 0) { $spectrumDomains = @('Build.Phone', 'Build.Wear') }
Enter-BuildLockOrExit -Reason 'build-release-spectrum.ps1' -Domain $spectrumDomains
Push-Location $projectRoot
try {
    # Pass 1: selected app flavors (Chaquopy off) + wear, if requested.
    $pass1Tasks = @()
    foreach ($f in $pass1Flavors) {
        # standard -> assembleStandardRelease, vr -> assembleVrRelease, etc.
        $pass1Tasks += ":app_v2:assemble$((Get-Culture).TextInfo.ToTitleCase($f))Release"
    }
    # Both watch artifacts come out of this single invocation. The sideload APK and the Play bundle
    # used to be produced by two gradle calls at different times - this script and
    # scripts/builders/build-wear-release.PS1 - with nothing establishing that they were built from
    # one tree state or one version, so a divergence between what a user sideloads and what the store
    # serves would have been caught by no gate (S2040). Kept as a list rather than two literals so a
    # third watch artifact costs one entry instead of a rewrite of this block.
    if ($buildWear) { $pass1Tasks += @(":wear:assemble${wearTaskFlavor}Release", ":wear:bundle${wearTaskFlavor}Release") }

    if ($pass1Tasks.Count -gt 0) {
        Write-Host "Pass 1: $($pass1Tasks -join ', ') (Chaquopy disabled).." -ForegroundColor Cyan
        & $gradlew @pass1Tasks "-Pchaquopy.enabled=false" --configuration-cache
        if ($LASTEXITCODE -ne 0) { throw "Pass 1 (non-noLegal release) failed with exit $LASTEXITCODE" }
    }

    if ($buildNoLegal) {
        Write-Host "Pass 2: noLegal release (Chaquopy enabled, no configuration cache).." -ForegroundColor Cyan
        & $gradlew `
            :app_v2:assembleNoLegalRelease `
            "-Pchaquopy.enabled=true" `
            --no-configuration-cache
        if ($LASTEXITCODE -ne 0) { throw "Pass 2 (noLegal release) failed with exit $LASTEXITCODE" }
    }
}
finally {
    Pop-Location
    Exit-AgentLock -Name 'Build' -Domains $spectrumDomains
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
    wear     = "wear\build\outputs\apk\$WearFlavor\release"
}

Write-Host "`nRelease spectrum APKs:" -ForegroundColor Cyan
$missing = @()
foreach ($flavor in $apkRoots.Keys) {
    if ($flavor -notin $selected) { continue }
    $dir = Join-Path $projectRoot $apkRoots[$flavor]
    # Named by the resolver, not by write time: this line prints the path a human then treats as
    # "the release build of this flavor", and picking a slice at random is exactly what S1972 removed.
    $apk = Find-BuildArtifact -Dir $dir
    if ($apk) {
        Write-Host "  $flavor : $($apk.FullName)" -ForegroundColor Green
    } else {
        Write-Host "  $flavor : MISSING ($dir)" -ForegroundColor Red
        $missing += $flavor
    }
}

# The watch bundle is the artifact the Play Wear track accepts, and it shares this run's gradle
# invocation with the APK reported above. It is checked here rather than trusted because a task that
# produced no output must stop the release the same way a missing APK does - before S2040 the bundle
# was built by a different script entirely, so this report had never had a reason to look for it.
#
# Find-BuildArtifact is deliberately not used: it selects by ABI from output-metadata.json, and AGP
# writes no such file for a bundle. The release directory holds exactly one .aab, so enumeration is
# exact. More than one means something this script does not model, and it refuses rather than picking
# by write time - guessing when the choice is real is what S1972 removed from the builders.
if ($buildWear) {
    $bundleDir = Join-Path $projectRoot "wear\build\outputs\bundle\$WearFlavor\release"
    $wearAabs  = @(Get-ChildItem -Path $bundleDir -Filter *.aab -File -ErrorAction SilentlyContinue)
    if ($wearAabs.Count -eq 1) {
        Write-Host "  wear (aab) : $($wearAabs[0].FullName)" -ForegroundColor Green
    } elseif ($wearAabs.Count -eq 0) {
        Write-Host "  wear (aab) : MISSING ($bundleDir)" -ForegroundColor Red
        $missing += 'wear (aab)'
    } else {
        Write-Host "  wear (aab) : AMBIGUOUS - $($wearAabs.Count) bundles in $bundleDir" -ForegroundColor Red
        $missing += 'wear (aab)'
    }
}

if ($missing.Count -gt 0) {
    throw "Release spectrum incomplete - missing artifact for: $($missing -join ', ')"
}

# ----------------------------------------------------------------------
# Mirror the watch APK to Google Drive.
#
# The watch is the one edition whose only distribution channel today is a hand-installed
# APK - it is in no store, so nothing updates it for anyone. Release 2.60.8232.251 shipped
# the watch to GitHub while the Drive copy stayed at the 15 August build: still named
# FastMediaSorter_wear_release.apk, still looking current, a month out of date. That is
# worse than an absent copy, because nobody checks a file that is already there (S1707).
#
# The phone editions are deliberately not mirrored here: `a.ps1 r` already puts standard on
# Drive, and the remaining flavors are handed out from the GitHub release instead.
# ----------------------------------------------------------------------
if ($buildWear) {
    # The Drive copy is the watch's only distribution channel, so the file chosen here is what a
    # person installs - it must be resolved, never guessed by write time (S1972).
    $wearApk = Find-BuildArtifact -Dir (Join-Path $projectRoot $apkRoots['wear'])
    if ($wearApk) {
        & (Join-Path $PSScriptRoot '..\utils\copy-to-drive.ps1') `
            -Path $wearApk.FullName -Name 'FastMediaSorter_wear_release.apk'
    }
}

# ----------------------------------------------------------------------
# Retain the deobfuscation payload for every variant this run published (S1695).
# Scope follows $selected rather than a hardcoded list, so the set of channels
# can change without editing this code.
#
# 'standard' is deliberately excluded: `a.ps1 r` already retained it straight out
# of the bundle, which is stronger provenance than the loose mapping this script
# could offer, and re-storing it here would only downgrade the record.
#
# Nothing here is fatal. The APKs are built and good; a retention problem is
# reported and left for the pre-release gate to refuse before the next release.
# ----------------------------------------------------------------------
$retainScript = Join-Path $projectRoot 'scripts\release\retain-deobfuscation.ps1'

# Native symbols are variant-keyed under native_debug_metadata, which is the very
# input AGP packs into BUNDLE-METADATA/debugsymbols. It covers every native library
# in the variant, CMake-built and prebuilt alike - so the vr flavor's own OpenXR
# runtime (fms_diagnostic_xr), which exists in no bundle, is captured here too.
# The CMake configure output under .cxx/ is keyed by a build-type hash rather than
# by variant and cannot be resolved from a flavor name, so it is not used.
function Resolve-NativeSymbolsDir {
    param([Parameter(Mandatory)] [string] $Variant)

    $variantRoot = Join-Path $projectRoot "app_v2\build\intermediates\native_debug_metadata\$Variant"
    if (-not (Test-Path -LiteralPath $variantRoot)) { return $null }

    # The task-name segment (extract<Variant>NativeDebugMetadata) is resolved by
    # wildcard rather than reconstructed, so a change in AGP's naming does not
    # silently strip the symbols out of the archive.
    $out = Get-ChildItem -LiteralPath $variantRoot -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'out' } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1

    return $out
}

$mappingRoots = [ordered]@{
    vr      = 'app_v2\build\outputs\mapping\vrRelease\mapping.txt'
    lite    = 'app_v2\build\outputs\mapping\liteRelease\mapping.txt'
    photos  = 'app_v2\build\outputs\mapping\photosRelease\mapping.txt'
    legacy  = 'app_v2\build\outputs\mapping\legacyRelease\mapping.txt'
    noLegal = 'app_v2\build\outputs\mapping\noLegalRelease\mapping.txt'
    # S2090: AGP names a mapping directory after the full variant, camel-cased - the phone rows above
    # read `vrRelease`, so the watch reads `standardRelease` / `noLegalRelease`, not a bare `release`.
    wear    = "wear\build\outputs\mapping\${WearFlavor}Release\mapping.txt"
}

if (Test-Path -LiteralPath $retainScript) {
    Write-Host "`nRetaining deobfuscation artifacts:" -ForegroundColor Cyan
    foreach ($flavor in $mappingRoots.Keys) {
        if ($flavor -notin $selected) { continue }

        $mappingPath = Join-Path $projectRoot $mappingRoots[$flavor]
        if (-not (Test-Path -LiteralPath $mappingPath)) {
            Write-Host "  $flavor : no mapping at $mappingPath - not retained" -ForegroundColor Yellow
            continue
        }

        # wear carries the 8-digit yyMMddHH code by design; app_v2 carries 9 digits.
        $codeForFlavor = if ($flavor -eq 'wear') { $wearVersionCode } else { $appVersionCode }

        # An array passed through `&` is positional, even when its items look like
        # parameter names. Keep this named so VersionCode cannot receive -Variant.
        $retainArgs = @{
            Variant     = $flavor
            VersionCode = $codeForFlavor
            VersionName = $versionName
            Mapping     = $mappingPath
        }

        # wear declares no ndk block, so it ships no native code and needs no symbols.
        if ($flavor -ne 'wear') {
            $variantName = "$($flavor)Release"
            $symbolsDir = Resolve-NativeSymbolsDir -Variant $variantName
            if ($symbolsDir) {
                $retainArgs.NativeSymbols = $symbolsDir
            } else {
                Write-Host "  $flavor : no native symbols under intermediates\native_debug_metadata\$variantName - mapping retained without them" -ForegroundColor Yellow
            }
        }

        & $retainScript @retainArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  $flavor : retention failed (exit $LASTEXITCODE)" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "`nNote: retention script not found at $retainScript - deobfuscation artifacts not retained." -ForegroundColor DarkGray
}

Write-Host "`nNext: scripts/release/publish-github-release.ps1 -Flavors $($selected -join ',') - publishes the selected assets under v$versionName." -ForegroundColor Yellow
