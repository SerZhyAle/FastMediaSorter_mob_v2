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
      AppVersionCode   yyMMddHH * 10 + floor(minute / 10)      - last digit 0..5, 9 digits.
      WearVersionCode  yyMMddHH * 10 + 6 + floor(minute / 15)  - last digit 6..9, 9 digits.

    The two codes MUST differ: both modules publish under one applicationId (S1681), and Play
    refuses a release whose artifacts repeat a versionCode. They differ by a PARTITION of the last
    digit rather than by an arithmetic relation between them (S2721): the phone owns 0..5, the
    watch owns 6..9, so the two can never collide whether they come from one joint stamp or from
    the watch's own independent release cadence, where no phone code exists to derive from.

    Both codes are full 9-digit date-time values of the same magnitude. The watch used to carry the
    bare 8-digit yyMMddHH, ten times smaller than the phone's code under one applicationId, so a
    fresh watch build read as older than a phone build from months earlier - and its hour
    resolution allowed only one watch release per hour.

    Worked example - 2026-08-21 03:12 local:
      VersionName 2.60.8210.312, AppVersionCode 260821031, WearVersionCode 260821036.

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
    $appCode = [int]($stamp + $mm.Substring(0, 1))
    # 6.. rather than 0..: the separator digit is a partition, not an offset, so the watch code is
    # clear of every phone code of the same hour without knowing what that phone code is (S2721).
    $wearCode = [int]$stamp * 10 + 6 + [int][math]::Floor([int]$mm / 15)
    $versionName = '{0}.{1}{2}.{3}{4}{5}.{6}{7}' -f `
        $yy.Substring(0, 1), $yy.Substring(1, 1), $mon.Substring(0, 1), `
        $mon.Substring(1, 1), $dd, $hh.Substring(0, 1), $hh.Substring(1, 1), $mm

    [pscustomobject]@{
        VersionName     = $versionName
        AppVersionCode  = $appCode
        WearVersionCode = $wearCode
    }
}

function Get-ArtifactVersion {
    <#
    .SYNOPSIS
        Read the versionName and versionCode a build actually packaged, from the AGP
        output-metadata.json beside the artifact.
    .DESCRIPTION
        The successor to reading a version back out of build.gradle.kts. After S1873 ADR-4 nothing
        writes those constants, so a consumer that still read them would report the sentinel as the
        version it just built. The metadata is written by the build that produced the artifact, so
        it cannot disagree with it.

        $null means "no metadata to read" - absent file, unreadable JSON, or no elements. That is a
        different answer from "the version is wrong", and a caller that collapses the two reports
        "did not look" as "looked and found nothing".
    .PARAMETER Dir
        Directory holding output-metadata.json - an AGP output directory such as
        app_v2/build/outputs/bundle/standardRelease.
    .OUTPUTS
        PSCustomObject with VersionName [string], VersionCode [int], MetadataPath [string]; or $null.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory, Position = 0)] [string] $Dir
    )

    if ([string]::IsNullOrWhiteSpace($Dir)) { return $null }
    $metadataPath = Join-Path $Dir 'output-metadata.json'
    if (-not (Test-Path -LiteralPath $metadataPath)) { return $null }

    try { $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json }
    catch { return $null }

    # Any element answers: AGP gives every ABI slice of one build the same versionCode and
    # versionName (S1972 §6.2), and this function judges the version, not a particular file.
    $element = @($metadata.elements) | Select-Object -First 1
    if (-not $element) { return $null }
    if ([string]::IsNullOrWhiteSpace([string]$element.versionName)) { return $null }

    [pscustomobject]@{
        VersionName  = [string]$element.versionName
        VersionCode  = [int]$element.versionCode
        MetadataPath = $metadataPath
    }
}

function Get-ReleaseMetadataCandidate {
    <#
    .SYNOPSIS
        The directories a completed app_v2 release may have written output-metadata.json into, in
        the order they should be tried.
    .DESCRIPTION
        There is more than one because AGP moves the bundle listing file between versions: on 9.2.1
        it is under intermediates rather than beside the bundle, which made -ReuseVersion report
        "run a.ps1 r first" about a release whose AAB was in the directory the message named
        (S3029). The list is ordered, not versioned - a lookup keyed to an AGP version breaks again
        at the next move, while trying the known places in turn survives both layouts.

        Last is the APK output directory. It is a different artifact, but AGP stamps every artifact
        of one build with the same versionCode and versionName, and publish-github-release.ps1
        already reads that file - so it is a true answer when the two bundle locations are empty.
    .PARAMETER ProjectRoot
        Repository root holding app_v2/build.
    .OUTPUTS
        String[] - absolute directory paths, most authoritative first.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory, Position = 0)] [string] $ProjectRoot
    )

    @(
        (Join-Path $ProjectRoot 'app_v2\build\outputs\bundle\standardRelease'),
        (Join-Path $ProjectRoot 'app_v2\build\intermediates\bundle_ide_model\standardRelease\produceStandardReleaseBundleIdeListingFile'),
        (Join-Path $ProjectRoot 'app_v2\build\outputs\apk\standard\release')
    )
}

function Get-ReleaseArtifactVersion {
    <#
    .SYNOPSIS
        Read back the version a completed app_v2 release build packaged, from whichever known
        location this AGP wrote its metadata into.
    .DESCRIPTION
        Get-ArtifactVersion with one directory answers "no metadata here", which a caller that knows
        only one directory turns into "no build happened" - the S3029 failure. This asks each
        candidate in turn and returns the first that answers.

        $null still means "no metadata in any known location", which is a different answer from "the
        version is wrong"; the caller reports the candidates, which Get-ReleaseMetadataCandidate
        hands it from the same list this function searched.
    .PARAMETER ProjectRoot
        Repository root holding app_v2/build.
    .OUTPUTS
        The Get-ArtifactVersion object (VersionName, VersionCode, MetadataPath), or $null.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory, Position = 0)] [string] $ProjectRoot
    )

    foreach ($dir in (Get-ReleaseMetadataCandidate -ProjectRoot $ProjectRoot)) {
        $found = Get-ArtifactVersion -Dir $dir
        if ($found) { return $found }
    }
    return $null
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
