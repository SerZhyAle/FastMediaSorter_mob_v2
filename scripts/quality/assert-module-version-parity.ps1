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
        build, after the signing, after the release notes. The project's rule, implemented once in
        Get-BuildVersionStamp (scripts/utils/build-version-stamp.ps1), derives both from the build
        instant: app_v2 is yyMMddHH * 10 + floor(minute / 10) and wear is
        yyMMddHH * 10 + 6 + floor(minute / 15), so the phone owns last digits 0..5 and the watch
        owns 6..9 (S2721).

    Because the shared versionName encodes the full minute, this gate does not approximate the rule
    - it decodes the name back into its instant and re-derives BOTH codes from the one function the
    builds use. A structural fallback (same yyMMddHH prefix, disjoint last-digit ranges) covers a
    versionName that does not decode, because passing an undecodable name silently would make the
    gate answer "clean" about a pair it never checked.

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

# The one home of the derivation. Re-implementing it here would recreate exactly the two-formulas
# problem this gate exists to catch.
. (Join-Path $root 'scripts/utils/build-version-stamp.ps1')

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

if ($wear.Code -eq $app.Code) {
    $violations.Add("versionCode is identical in both modules ($($app.Code)). Play refuses a release that repeats a versionCode under one applicationId - the submission fails, the build does not.")
} else {
    # The name is shared, so decoding either one answers for both. app_v2 is read because it is the
    # module the version is authored against; a name mismatch is already reported above.
    $instant = ConvertFrom-BuildVersionName $app.Name
    if ($instant) {
        $expected = Get-BuildVersionStamp -Now $instant
        if ($app.Code -ne $expected.AppVersionCode) {
            $violations.Add("app_v2 versionCode $($app.Code) does not match its own versionName '$($app.Name)' - expected $($expected.AppVersionCode). The code and the name encode one instant (S1873); a pair that disagrees means one of the two was hand-edited alone.")
        }
        if ($wear.Code -ne $expected.WearVersionCode) {
            $violations.Add("wear versionCode $($wear.Code) does not match the shared versionName '$($app.Name)' - expected $($expected.WearVersionCode) (yyMMddHH * 10 + 6 + floor(minute / 15), S2721). A stale wear versionCode prevents installing debug wear builds (INSTALL_FAILED_VERSION_DOWNGRADE) on any device or emulator running a newer build.")
        }
    } else {
        # Not "clean" - a weaker check, reported as such. The structural invariant is what makes the
        # two codes shippable together even when the instant behind them is unreadable.
        Write-Host "assert-module-version-parity: versionName '$($app.Name)' does not decode to an instant - falling back to the structural invariant."
        if ([long][math]::Floor($app.Code / 10) -ne [long][math]::Floor($wear.Code / 10)) {
            $violations.Add("versionCode prefixes differ: app_v2 $($app.Code) and wear $($wear.Code) do not share one yyMMddHH hour. Both modules ship from one build instant.")
        }
        if (($app.Code % 10) -gt 5) {
            $violations.Add("app_v2 versionCode $($app.Code) ends in $($app.Code % 10); the phone owns last digits 0..5 and the watch owns 6..9 (S2721).")
        }
        if (($wear.Code % 10) -lt 6) {
            $violations.Add("wear versionCode $($wear.Code) ends in $($wear.Code % 10); the watch owns last digits 6..9 and the phone owns 0..5 (S2721).")
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Error "assert-module-version-parity: $($violations.Count) violation(s) - app_v2 and wear state versions that cannot both ship." -ErrorAction Continue
    foreach ($violation in $violations) {
        Write-Host "  $violation"
    }
    # S1873 ADR-4: no script writes these constants any longer, so this is a hand edit by
    # construction. The hint used to name build-release-spectrum.ps1 -SkipBuild, which after that
    # ticket resolves a version and writes nothing - it would have sent the reader to a no-op.
    Write-Host '  Fix: edit both build files by hand - keep versionName byte-identical, then take both codes from `. scripts/utils/build-version-stamp.ps1; Get-BuildVersionStamp -Now <the instant that versionName encodes>`. No build writes these constants (S1873).'
    exit 1
}

if (-not $Quiet) {
    Write-Host "assert-module-version-parity: OK - app_v2 $($app.Name) ($($app.Code)) / wear $($wear.Name) ($($wear.Code))."
}
exit 0
