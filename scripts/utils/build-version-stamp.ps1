#requires -Version 7.0
<#
.SYNOPSIS
    Single home of the build-version derivation: one instant -> one versionName and both
    modules' versionCode.

.DESCRIPTION
    Dot-source this file to get Get-BuildVersionStamp. No top-level side effects, no exit,
    no preference variables assigned - a caller's own error mode is left alone.

    The scheme, previously spelled out inline in about twenty places in two different coding
    styles (S1873):

      versionName      Y.YM.MDDH.Hmm   - identical for app_v2 and wear, byte for byte. It is the
                                         string the user reads on the device and the string a
                                         support log carries.
      AppVersionCode   yyMMddHH + the first minute digit (9 digits).
      WearVersionCode  yyMMddHH (8 digits), i.e. floor(AppVersionCode / 10).

    The two codes MUST differ: both modules publish under one applicationId (S1681), and Play
    refuses a release whose artifacts repeat a versionCode. The derivation above is the
    documented relationship that assert-module-version-parity.ps1 enforces on the checked-in
    constants.

    Worked example - 2026-08-21 03:12 local:
      VersionName 2.60.8210.312, AppVersionCode 260821031, WearVersionCode 26082103.

    Exit codes: none. This file defines functions and returns nothing; it is dot-sourced, never
    invoked as a script.
#>

function Get-BuildVersionStamp {
    <#
    .SYNOPSIS
        Derive versionName and both modules' versionCode from one instant.
    .PARAMETER Now
        The instant to encode. Defaults to the current local time. Pass an explicit value when
        several modules of one release must agree on the version byte for byte.
    .OUTPUTS
        PSCustomObject with VersionName [string], AppVersionCode [int], WearVersionCode [int].
    #>
    [CmdletBinding()]
    param(
        [datetime] $Now = (Get-Date)
    )

    $yy = $Now.ToString('yy')
    $mon = $Now.ToString('MM')
    $dd = $Now.ToString('dd')
    $hh = $Now.ToString('HH')
    $mm = $Now.ToString('mm')

    $stamp = $Now.ToString('yyMMddHH')
    $wearCode = [int]$stamp
    $appCode = [int]($stamp + $mm.Substring(0, 1))
    $versionName = '{0}.{1}{2}.{3}{4}{5}.{6}{7}' -f `
        $yy.Substring(0, 1), $yy.Substring(1, 1), $mon.Substring(0, 1), `
        $mon.Substring(1, 1), $dd, $hh.Substring(0, 1), $hh.Substring(1, 1), $mm

    [pscustomobject]@{
        VersionName     = $versionName
        AppVersionCode  = $appCode
        WearVersionCode = $wearCode
    }
}

function ConvertFrom-BuildVersionName {
    <#
    .SYNOPSIS
        Decode a versionName back into the instant it encodes, or $null when it encodes none.
    .DESCRIPTION
        The inverse of Get-BuildVersionStamp's VersionName. A variant suffix appended by the build
        (-DEBUG, -NoLegal, -Lite, ..) is ignored - it carries no time information.

        $null means "this string is not a stamped version", which is a different answer from
        "this version is stale". A caller that collapses the two reports "did not look" as
        "looked and found nothing" (S1873).
    .PARAMETER VersionName
        The string to decode, with or without a variant suffix.
    .OUTPUTS
        [datetime] truncated to the minute, or $null.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory, Position = 0)] [AllowEmptyString()] [string] $VersionName
    )

    # Y . Y M . M D D H . H m m  - four groups of 1, 2, 4 and 3 digits, then an optional suffix.
    $m = [regex]::Match($VersionName, '^(?<g1>\d)\.(?<g2>\d{2})\.(?<g3>\d{4})\.(?<g4>\d{3})(?:-.*)?$')
    if (-not $m.Success) { return $null }

    $g1 = $m.Groups['g1'].Value
    $g2 = $m.Groups['g2'].Value
    $g3 = $m.Groups['g3'].Value
    $g4 = $m.Groups['g4'].Value

    $year = 2000 + [int]($g1 + $g2.Substring(0, 1))
    $month = [int]($g2.Substring(1, 1) + $g3.Substring(0, 1))
    $day = [int]$g3.Substring(1, 2)
    $hour = [int]($g3.Substring(3, 1) + $g4.Substring(0, 1))
    $minute = [int]$g4.Substring(1, 2)

    if ($month -lt 1 -or $month -gt 12) { return $null }
    if ($day -lt 1 -or $day -gt 31) { return $null }
    if ($hour -gt 23 -or $minute -gt 59) { return $null }

    try { return [datetime]::new($year, $month, $day, $hour, $minute, 0) }
    catch { return $null }
}
