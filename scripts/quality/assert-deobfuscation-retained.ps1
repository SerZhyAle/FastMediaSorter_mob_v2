#!/usr/bin/env pwsh
<#
.SYNOPSIS
    S1695 - assert that the most recently published release's deobfuscation
    payload is retained and reads back intact.

.DESCRIPTION
    Before this gate, docs/RELEASE_READINESS_STANDARD.md declared retention
    REQUIRED for every production release while nothing in the repository
    performed it, and the gap surfaced only when S1156 sat in BlockExternal for
    three weeks because three obfuscated symbols from a shipped release could not
    be resolved. This gate makes the violation visible at the next release instead.

    Which release is judged: the newest release tag in git. Reading tags is
    correct here specifically because this is a release flow - the tags are what
    /skill-release itself creates, and the working tree carries no record of what
    was published.

    The check itself is delegated to scripts/release/fetch-deobfuscation.ps1
    -Verify, which reads the stored mapping back through the archive and
    recomputes its SHA-256. Presence is deliberately not accepted as proof: a
    cloud folder mid-sync presents a correctly sized placeholder, which is the
    exact "retention succeeded into the void" failure being guarded.

    Exit codes:
      0 - the judged release is retained and verified, or it predates the
          retention baseline and is therefore out of this scheme's scope.
      1 - the judged release is not retained, or a stored payload failed
          verification.
      2 - cannot verify: the archive root is unreachable, no release tag could be
          resolved, or a tag's version string could not be parsed.

.PARAMETER ArchiveRoot
    Root of the retention archive. Must match what retain-deobfuscation.ps1 wrote.
    Defaults to the resolved Deobfuscation artifact sink.

.PARAMETER VersionName
    Judge this release instead of the newest tag. Diagnostic: lets an operator ask
    about one specific release, and is the only way to exercise the gate against a
    release the tag list does not currently point at.

.PARAMETER Quiet
    Suppress per-variant progress; print only the verdict.

.PARAMETER Json
    Emit the verdict as JSON for machine consumption.
#>

[CmdletBinding()]
param(
    [string] $ArchiveRoot,
    [string] $VersionName,
    [switch] $Quiet,
    [switch] $Json,
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/quality/assert-deobfuscation-retained.ps1'
    exit $LASTEXITCODE
}

$ErrorActionPreference = 'Stop'

function Exit-Gate {
    param([Parameter(Mandatory)] [string] $Message, [Parameter(Mandatory)] [int] $Code)
    Write-Error "assert-deobfuscation-retained: $Message" -ErrorAction Continue
    exit $Code
}

# The first versionCode this retention scheme covers. Releases below it shipped
# before S1695 existed and were never retained locally; per strategic ADR-1 their
# only surviving mapping is the store's own copy, so failing the gate on them
# would report a violation no action can fix. 260815000 is 2026-08-15 00:00, the
# day the scheme shipped; the last pre-scheme release is 260812203
# (tag release/v2.60.8122.034).
$RetentionBaselineVersionCode = 260815000

. "$PSScriptRoot\..\utils\project-paths.ps1"

if (-not $ArchiveRoot) {
    # An unreachable archive is the gate's own exit 2, not a pass: it means the evidence was
    # never consulted, which is a different answer from "this release is not retained".
    $ArchiveRoot = Get-ArtifactSink -Kind Deobfuscation -Quiet
    if (-not $ArchiveRoot) {
        Exit-Gate -Message ("deobfuscation archive not reachable on this machine - " +
            "set FMS_SINK_DEOBFUSCATION or pass -ArchiveRoot.") -Code 2
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fetchScript = Join-Path $repoRoot 'scripts\release\fetch-deobfuscation.ps1'
if (-not (Test-Path -LiteralPath $fetchScript)) {
    Exit-Gate -Message "fetch-deobfuscation.ps1 not found at $fetchScript - the gate cannot check anything." -Code 2
}

# ----------------------------------------------------------------------
# versionName -> versionCode for app_v2, mirroring Get-BuildVersionStamp's
# AppVersionCode. versionName is Y.YM.MDDH.Hmm; the code is yyMMddHH plus the
# first minute digit. Both are derived from the same build timestamp, so the
# code is recoverable from the name without consulting the archive - which
# matters because the baseline decision has to be made for releases that were
# never retained and therefore have no manifest to read.
#
# app_v2 only, and since S2722 used for the retention-baseline decision alone -
# not to choose a directory. The wear code follows a different rule since S2721
# (yyMMddHH * 10 + 6 + floor(minute / 15), so the phone owns last digits 0..5
# and the watch owns 6..9) and is not recoverable from the app code, so a gate
# that opened one derived code opened the phone's directory and silently ignored
# the watch mapping filed beside it. The archive is selected by versionName
# below instead; the baseline question ("does this release predate the scheme")
# is about the phone's numbering and needs no archive lookup, which is why this
# formula survives.
# ----------------------------------------------------------------------
function ConvertTo-VersionCode {
    param([Parameter(Mandatory)] [string] $Name)

    $parts = $Name.Split('.')
    if ($parts.Count -ne 4) { return $null }
    if ($parts[0].Length -ne 1 -or $parts[1].Length -ne 2 -or $parts[2].Length -ne 4 -or $parts[3].Length -ne 3) {
        return $null
    }

    # Reconstruct explicitly rather than by slicing, so the mapping stays readable:
    #   parts[1] = <last year digit><first month digit>
    #   parts[2] = <second month digit><day 2><first hour digit>
    #   parts[3] = <second hour digit><minute 2>
    $year   = "2$($parts[1][0])"
    $month  = "$($parts[1][1])$($parts[2][0])"
    $day    = "$($parts[2][1])$($parts[2][2])"
    $hour   = "$($parts[2][3])$($parts[3][0])"
    $minute = "$($parts[3][1])$($parts[3][2])"

    $digits = "$year$month$day$hour$($minute[0])"
    $parsed = 0
    if (-not [int]::TryParse($digits, [ref]$parsed)) { return $null }
    return $parsed
}

# ----------------------------------------------------------------------
# Resolve the newest published release from the tags /skill-release created.
# ----------------------------------------------------------------------
if ($VersionName) {
    $judgedVersion = $VersionName.Trim()
    $tag = "(supplied via -VersionName)"
}
else {
    # Collect every tag before selecting: piping git straight into
    # `Select-Object -First 1` terminates git early, which leaves a non-zero
    # $LASTEXITCODE and makes a healthy tag list look like an empty one.
    $tags = @(& git -C $repoRoot tag -l 'release/v*' --sort=-creatordate)
    $gitExit = $LASTEXITCODE
    if ($gitExit -ne 0) {
        Exit-Gate -Message "git tag lookup failed with exit $gitExit - cannot tell which release to judge." -Code 2
    }
    if ($tags.Count -eq 0) {
        Exit-Gate -Message 'no release/v* tag found - cannot tell which release to judge.' -Code 2
    }
    $tag = $tags[0]
    $judgedVersion = "$tag".Trim() -replace '^release/v', ''
}

$versionCode = ConvertTo-VersionCode -Name $judgedVersion
if (-not $versionCode) {
    Exit-Gate -Message "release version '$judgedVersion' (from $tag) is not parsable - cannot resolve its versionCode." -Code 2
}

if (-not $Quiet) {
    Write-Host "Judging release: $judgedVersion (code $versionCode), from tag $tag" -ForegroundColor Cyan
}

if ($versionCode -lt $RetentionBaselineVersionCode) {
    $msg = "assert-deobfuscation-retained: PASS - release $judgedVersion (code $versionCode) predates the retention baseline ($RetentionBaselineVersionCode); its mapping is recoverable only from the store's copy of the bundle."
    if ($Json) {
        [ordered]@{ verdict = 'pre-baseline'; versionName = $judgedVersion; versionCode = $versionCode } | ConvertTo-Json
    } else {
        Write-Host $msg -ForegroundColor Yellow
    }
    exit 0
}

if (-not (Test-Path -LiteralPath $ArchiveRoot)) {
    # Exit 2, not 1: an unreachable archive is not evidence that the release was
    # left unretained, and the two call for different operator actions.
    Exit-Gate -Message "archive root not found: $ArchiveRoot - retention cannot be confirmed or denied." -Code 2
}

# ----------------------------------------------------------------------
# Which variants to judge. 'standard' is mandatory - a.ps1 r always builds it, so
# a release without a retained standard payload is a retention failure regardless
# of what else shipped. Everything else is judged from what the manifests record,
# because strategic section 3.3 scopes retention to the variants the release
# actually published, and only the release itself knows which those were.
#
# S2722: a release occupies as many archive directories as it has artifacts with
# their own versionCode - retain-deobfuscation.ps1 keys the directory by the code
# it is handed, and build-release-spectrum.ps1 hands it the WEAR code for the
# watch mapping. The two modules stamp the SAME versionName from the same build
# instant, so the versionName is what identifies a release across the archive and
# the derived phone code is not. Each variant carries the code of the directory
# it was found in, because that is what fetch-deobfuscation.ps1 -Verify is keyed
# by.
# ----------------------------------------------------------------------
$targets = @([pscustomobject]@{ Variant = 'standard'; VersionCode = $versionCode })

foreach ($dir in @(Get-ChildItem -LiteralPath $ArchiveRoot -Directory -ErrorAction SilentlyContinue)) {
    $manifestPath = Join-Path $dir.FullName 'manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) { continue }
    try {
        $manifest = [System.IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
    }
    catch {
        # Unreadable rather than absent: an archive directory that cannot be parsed
        # may be the one holding this release, so it is a failure to verify, never
        # a silent skip.
        Exit-Gate -Message "archive manifest is unreadable: $manifestPath ($_)" -Code 1
    }

    if ("$($manifest.versionName)".Trim() -ne $judgedVersion) { continue }

    $dirCode = if ($manifest.versionCode) { [int]$manifest.versionCode } else { $versionCode }
    foreach ($record in @($manifest.variants)) {
        if (-not $record.variant) { continue }
        $known = $targets | Where-Object { $_.Variant -eq $record.variant } | Select-Object -First 1
        if ($known) {
            # 'standard' is seeded with the derived code before any manifest is read,
            # so its real directory replaces that assumption when found.
            $known.VersionCode = $dirCode
        }
        else {
            $targets += [pscustomobject]@{ Variant = $record.variant; VersionCode = $dirCode }
        }
    }
}

# A watch-only release publishes no phone artifact at all, so the mandatory
# 'standard' seed above would report it as unretained when nothing is wrong. Only
# a diagnostic -VersionName run can reach one: /skill-release-wear creates no
# release/v* tag (checked 2026-09-08 - the tag list holds none of the watch-only
# versions), so the tag-driven path this gate runs on keeps 'standard' mandatory
# unconditionally. The relaxation needs every payload found to be a watch payload:
# a phone release that lost its standard directory while keeping lite or vr still
# fails.
$foundStandard = $targets | Where-Object { $_.Variant -eq 'standard' -and $_.VersionCode -ne $versionCode }
$onlyWear = @($targets | Where-Object { $_.Variant -ne 'standard' }).Count -gt 0 -and
            @($targets | Where-Object { $_.Variant -ne 'standard' -and $_.Variant -ne 'wear' }).Count -eq 0
if ($VersionName -and -not $foundStandard -and $onlyWear) {
    $targets = @($targets | Where-Object { $_.Variant -ne 'standard' })
    if (-not $Quiet) {
        Write-Host "  standard : not published by this release - judging the watch payload alone" -ForegroundColor Yellow
    }
}

$failures = @()
foreach ($target in $targets) {
    # Delegated rather than reimplemented: fetch-deobfuscation.ps1 -Verify already
    # streams the stored mapping back and recomputes its hash, and two copies of
    # that logic would be two chances to disagree about what "retained" means.
    & $fetchScript -Verify -VersionCode $target.VersionCode -Variant $target.Variant -ArchiveRoot $ArchiveRoot 2>&1 |
        ForEach-Object { if (-not $Quiet) { Write-Host "  $_" } }

    if ($LASTEXITCODE -ne 0) {
        $failures += $target.Variant
    }
    elseif (-not $Quiet) {
        Write-Host "  $($target.Variant) (code $($target.VersionCode)) : verified" -ForegroundColor Green
    }
}

$variants = @($targets | ForEach-Object { $_.Variant })

if ($failures.Count -gt 0) {
    if ($Json) {
        [ordered]@{ verdict = 'not-retained'; versionName = $judgedVersion; versionCode = $versionCode; failed = $failures } |
            ConvertTo-Json -Depth 4
    }

    # The gate fires at pre-release time, long after the build that would have
    # retained this automatically. Naming the repair command is the difference
    # between a blocked release and a blocked release someone can unblock.
    Write-Host ''
    Write-Host 'To repair, re-run retention against the shipped artifact for each failed variant:' -ForegroundColor Yellow
    foreach ($variant in $failures) {
        $sourceFlag = if ($variant -eq 'standard') { '-Bundle <path-to-shipped.aab>' } else { '-Mapping <path-to-mapping.txt>' }
        # The code is the failed variant's own, not the release's phone code: the
        # watch mapping is filed under the wear code and repairing it under the
        # phone's would write a second directory nothing reads.
        $repairCode = ($targets | Where-Object { $_.Variant -eq $variant } | Select-Object -First 1).VersionCode
        Write-Host "  pwsh -NoProfile -File scripts/release/retain-deobfuscation.ps1 -Variant $variant -VersionCode $repairCode -VersionName $judgedVersion $sourceFlag" -ForegroundColor Yellow
    }

    Exit-Gate -Message "FAIL - release $judgedVersion (code $versionCode) is not retained for: $($failures -join ', ')." -Code 1
}

# S2722: name the absence rather than passing over it. The archive cannot tell a
# release that published no watch artifact from one whose watch retention was
# lost, and phone-only releases are the common case, so this is stated and not
# failed - but stating it is the whole point: a bare PASS listing 'standard' read
# as "looked and found nothing" when it meant "did not look".
$wearRetained = 'wear' -in $variants
if ($Json) {
    [ordered]@{
        verdict      = 'ok'
        versionName  = $judgedVersion
        versionCode  = $versionCode
        variants     = $variants
        wearRetained = $wearRetained
    } | ConvertTo-Json -Depth 4
} else {
    $verdict = "assert-deobfuscation-retained: PASS - release $judgedVersion (code $versionCode) retained and verified for: $($variants -join ', ')."
    if (-not $wearRetained) {
        $verdict += " No wear payload is retained under this versionName - either the release published no watch artifact, or its retention was lost."
    }
    Write-Host $verdict -ForegroundColor Green
}
exit 0
