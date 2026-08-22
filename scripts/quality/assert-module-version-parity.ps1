#requires -Version 7.0
<#
.SYNOPSIS
    The `wear` module's checked-in version must follow the same system as `app_v2`: one identical
    versionName, and a versionCode derived from the app's by the documented rule.

.DESCRIPTION
    Both modules publish under the same applicationId (`com.sza.fastmediasorter`, S1681 - Play
    Services routes the Data Layer by package + signing certificate, so the ids may never diverge).
    That makes the version fields a cross-module contract with two halves, and each half fails in a
    different, silent way:

      - versionName MUST be identical. It is the string the user reads, the string a support log
        carries and the string the release notes are written against. A watch reporting a different
        version than the phone it is paired with is indistinguishable from a stale install.

      - versionCode MUST NOT be identical. Play refuses a release whose artifacts repeat a
        versionCode under one applicationId, and the refusal arrives at submission time - after the
        build, after the signing, after the release notes. The project's rule, implemented in
        scripts/release/build-release-spectrum.ps1, derives one from the other: app_v2 uses
        yyMMddHH + the first minute digit (9 digits), wear uses the same yyMMddHH (8 digits), i.e.
        wear = floor(app / 10).

    Both halves held only as long as whoever edited one build file remembered the other one existed.
    They did not: the tree carried `wear` = 260815161, byte-identical to app_v2's 9-digit code, so
    the first Wear submission would have been rejected and no local build could tell.

.NOTES
    Run from anywhere; paths are resolved relative to the repo root.

    This gate reads the checked-in defaults only. A `-Pfms.versionCode` / `-Pfms.versionName`
    override is a per-invocation release concern and belongs to the release script, not here.

    Exit codes:
      0 - clean, both modules state one version under the documented derivation
      1 - versionName differs, or the versionCode derivation is broken
      2 - cannot verify: a build file is missing or states no version constants
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot/../..").Path

# app_v2 is the source of the version; wear is derived from it, never the other way round.
$appGradle  = Join-Path $root 'app_v2/build.gradle.kts'
$wearGradle = Join-Path $root 'wear/build.gradle.kts'

function Get-ModuleVersion {
    param([string]$Path, [string]$Module)

    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Error "assert-module-version-parity: build file not found - '$Path'." -ErrorAction Continue
        exit 2
    }

    $text = Get-Content -LiteralPath $Path -Raw

    # Anchored on the declaration, not on a bare `versionCode`: both files also assign
    # `versionCode = overrideAppVersionCode ?: defaultAppVersionCode` inside defaultConfig, and a
    # loose match would read that line and find no number at all.
    $codeMatch = [regex]::Match($text, '(?m)^\s*val\s+defaultAppVersionCode\s*=\s*(?<value>\d+)\s*$')
    $nameMatch = [regex]::Match($text, '(?m)^\s*val\s+defaultAppVersionName\s*=\s*"(?<value>[^"]+)"\s*$')

    if (-not $codeMatch.Success -or -not $nameMatch.Success) {
        Write-Error "assert-module-version-parity: '$Module' states no defaultAppVersionCode/defaultAppVersionName pair in '$Path'." -ErrorAction Continue
        exit 2
    }

    return [pscustomobject]@{
        Module = $Module
        Code   = [long]$codeMatch.Groups['value'].Value
        Name   = $nameMatch.Groups['value'].Value
    }
}

$app  = Get-ModuleVersion -Path $appGradle  -Module 'app_v2'
$wear = Get-ModuleVersion -Path $wearGradle -Module 'wear'

$violations = [System.Collections.Generic.List[string]]::new()

if ($app.Name -ne $wear.Name) {
    $violations.Add("versionName differs: app_v2 '$($app.Name)' vs wear '$($wear.Name)'. The two modules ship one product under one applicationId - the user-visible version string must match exactly.")
}

$expectedWearCode = [long][math]::Floor($app.Code / 10)
if ($wear.Code -ne $expectedWearCode) {
    if ($wear.Code -eq $app.Code) {
        $violations.Add("versionCode is identical in both modules ($($app.Code)). Play refuses a release that repeats a versionCode under one applicationId - the submission fails, the build does not. Expected wear = $expectedWearCode.")
    } else {
        $violations.Add("versionCode derivation broken: wear is $($wear.Code), expected $expectedWearCode (app_v2 $($app.Code) without its trailing minute digit, per scripts/release/build-release-spectrum.ps1).")
    }
}

if ($violations.Count -gt 0) {
    Write-Error "assert-module-version-parity: $($violations.Count) violation(s) - app_v2 and wear state versions that cannot both ship." -ErrorAction Continue
    foreach ($violation in $violations) {
        Write-Host "  $violation"
    }
    Write-Host '  Fix: stamp both modules together - pwsh -NoProfile -File scripts/release/build-release-spectrum.ps1 -SkipBuild - or hand-align wear to app_v2 under the rule above.'
    exit 1
}

if (-not $Quiet) {
    Write-Host "assert-module-version-parity: OK - app_v2 $($app.Name) ($($app.Code)) / wear $($wear.Name) ($($wear.Code))."
}
exit 0
