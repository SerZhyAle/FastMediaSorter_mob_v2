#!/usr/bin/env pwsh
#requires -Version 7.0
<#
.SYNOPSIS
    Rewrite the measured half of docs/PLAY_PUBLISHING_STATE.md from live reads (S2272).

.DESCRIPTION
    The state record has three blocks and two of them are machine-readable: what the store serves
    (read-play-public-serve.ps1) and what each track holds (read-play-tracks.ps1). This script is the
    only writer of those two. It replaces exactly the regions between the `s2272:measured:*` marker
    comments and touches no byte outside them, so the surrounding prose, the recovery plan and the
    drift notes survive a refresh.

    The third block is `Policy status`. The Play Developer API exposes no policy or rejection surface,
    so that block is transcribed by the owner and no script can refresh it. Silently carrying it
    forward would make the record read as current when its most consequential half is not, so this
    script instead reports the transcription date it finds and how old it is. That is a line to read,
    not a failure.

    A reader that cannot verify leaves its own block untouched and makes this script exit 2. A block
    is never written from a failed read: strategic risk 7 of S2272 is that the record ages silently
    and is believed anyway, and a block stamped with today's date from yesterday's data is exactly
    that failure.

.PARAMETER Check
    Compute the same replacement and write nothing. Exit 1 if either block would change. This is the
    staleness probe - safe in a gate, safe on a dirty tree.

.PARAMETER Package
    Application id to read. Defaults to com.sza.fastmediasorter.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/refresh-play-publishing-state.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/release/refresh-play-publishing-state.ps1 -Check

.NOTES
    Exit codes:
      0 - both measured blocks refreshed, or -Check found both already current
      1 - -Check found at least one block out of date; nothing was written
      2 - could not verify: a reader failed, the document is missing, or a marker pair is absent
#>
[CmdletBinding()]
param(
    [switch] $Check,
    [string] $Package = 'com.sza.fastmediasorter'
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$docPath = Join-Path $repoRoot 'docs\PLAY_PUBLISHING_STATE.md'
$tracksReader = Join-Path $PSScriptRoot 'read-play-tracks.ps1'
$serveReader = Join-Path $PSScriptRoot 'read-play-public-serve.ps1'
$pwshExe = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName

foreach ($required in @($docPath, $tracksReader, $serveReader)) {
    if (-not (Test-Path -LiteralPath $required)) {
        $msg = "refresh-play-publishing-state: required file not found - $required"
        Write-Error $msg -ErrorAction Continue
        exit 2
    }
}

$measuredUtc = [DateTime]::UtcNow.ToString('yyyy-MM-dd')

function Invoke-ReaderJson {
    param([string] $ScriptPath, [string[]] $Arguments)
    $raw = & $pwshExe -NoProfile -File $ScriptPath @Arguments 2>&1
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        return [pscustomobject] @{ Ok = $false; ExitCode = $code; Data = $null; Output = ($raw -join "`n") }
    }
    $jsonText = ($raw | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] }) -join "`n"
    try {
        $data = $jsonText | ConvertFrom-Json
    } catch {
        return [pscustomobject] @{ Ok = $false; ExitCode = 2; Data = $null; Output = $jsonText }
    }
    return [pscustomobject] @{ Ok = $true; ExitCode = 0; Data = $data; Output = $jsonText }
}

# A track release's `name` is free text: the internal track carries "260622232 (2.60.6222.324)".
# Prefer the four-segment version inside it, so the column holds a version and not a label.
function Get-VersionName {
    param([string] $RawName)
    if (-not $RawName) { return '-' }
    $match = [regex]::Match($RawName, '\d+\.\d+\.\d+\.\d+')
    if ($match.Success) { return $match.Value }
    return $RawName
}

$serve = Invoke-ReaderJson -ScriptPath $serveReader -Arguments @('-Json', '-Package', $Package)
$tracks = Invoke-ReaderJson -ScriptPath $tracksReader -Arguments @('-Json', '-Package', $Package)

if (-not $serve.Ok -and -not $tracks.Ok) {
    $msg = "refresh-play-publishing-state: both readers failed (public-serve exit $($serve.ExitCode), " +
        "tracks exit $($tracks.ExitCode)). Nothing written."
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$newLine = if ((Get-Content -LiteralPath $docPath -Raw) -match "`r`n") { "`r`n" } else { "`n" }
$content = Get-Content -LiteralPath $docPath -Raw

function Build-ServeBlock {
    param($Reader)
    $d = $Reader.Data
    $updated = if ($d.updatedOn) { $d.updatedOn } else { 'unknown' }
    $rows = @(
        '| Served version | Store `Updated on` | Detected by | Measured (UTC) |'
        '|----------------|--------------------|-------------|----------------|'
        "| ``$($d.servedVersion)`` | $updated | $($d.detectedBy) | $($d.measuredUtc) |"
        ''
        'Reader exit code: 0.'
    )
    return $rows
}

function Build-TracksBlock {
    param($Reader, [string] $Stamp)
    $rows = @(
        '| Track | versionName | versionCode | Status | Measured (UTC) |'
        '|-------|-------------|-------------|--------|----------------|'
    )
    foreach ($track in $Reader.Data.tracks) {
        $release = @($track.releases)[0]
        if ($null -eq $release) {
            $rows += "| ``$($track.track)`` | - | - | no release | $Stamp |"
            continue
        }
        $version = Get-VersionName -RawName $release.name
        $code = @($release.versionCodes)[0]
        $rows += "| ``$($track.track)`` | ``$version`` | ``$code`` | $($release.status) | $Stamp |"
    }
    $rows += ''
    $rows += 'Reader exit code: 0.'
    return $rows
}

function Set-MarkedRegion {
    param([string] $Text, [string] $Marker, [string[]] $Body, [string] $Eol)
    $begin = "<!-- s2272:measured:$Marker`:begin -->"
    $end = "<!-- s2272:measured:$Marker`:end -->"
    $beginIndex = $Text.IndexOf($begin)
    $endIndex = $Text.IndexOf($end)
    if ($beginIndex -lt 0 -or $endIndex -lt 0 -or $endIndex -lt $beginIndex) {
        return $null
    }
    $head = $Text.Substring(0, $beginIndex + $begin.Length)
    $tail = $Text.Substring($endIndex)
    $middle = $Eol + $Eol + ($Body -join $Eol) + $Eol + $Eol
    return $head + $middle + $tail
}

$updatedContent = $content
$refreshed = @()
$skipped = @()

if ($serve.Ok) {
    $next = Set-MarkedRegion -Text $updatedContent -Marker 'public-serve' -Body (Build-ServeBlock -Reader $serve) -Eol $newLine
    if ($null -eq $next) {
        $msg = 'refresh-play-publishing-state: the public-serve marker pair is missing from the document.'
        Write-Error $msg -ErrorAction Continue
        exit 2
    }
    $updatedContent = $next
    $refreshed += 'public-serve'
} else {
    $skipped += "public-serve (reader exit $($serve.ExitCode))"
}

if ($tracks.Ok) {
    $body = Build-TracksBlock -Reader $tracks -Stamp $measuredUtc
    $next = Set-MarkedRegion -Text $updatedContent -Marker 'tracks' -Body $body -Eol $newLine
    if ($null -eq $next) {
        $msg = 'refresh-play-publishing-state: the tracks marker pair is missing from the document.'
        Write-Error $msg -ErrorAction Continue
        exit 2
    }
    $updatedContent = $next
    $refreshed += 'tracks'
} else {
    $skipped += "tracks (reader exit $($tracks.ExitCode))"
}

$wouldChange = $updatedContent -ne $content

if ($Check) {
    if ($wouldChange) {
        $msg = "refresh-play-publishing-state: -Check found the measured blocks out of date " +
            "($($refreshed -join ', ')). Run without -Check to rewrite them."
        Write-Error $msg -ErrorAction Continue
        exit 1
    }
    Write-Host 'refresh-play-publishing-state: -Check - measured blocks are current.'
} else {
    if ($wouldChange) {
        [System.IO.File]::WriteAllText($docPath, $updatedContent, [System.Text.UTF8Encoding]::new($false))
        Write-Host "refresh-play-publishing-state: rewrote $($refreshed -join ', ') in docs/PLAY_PUBLISHING_STATE.md."
    } else {
        Write-Host 'refresh-play-publishing-state: measured blocks already current, nothing written.'
    }
}

# The transcribed block is never written here; its age is reported so the record cannot look fresher
# than its least fresh half.
$transcribedMatch = [regex]::Match($updatedContent, 's2272:transcribed:policy-status.*?\*\*Transcribed:\*\*\s*(\d{4}-\d{2}-\d{2})', 'Singleline')
if ($transcribedMatch.Success) {
    $transcribedOn = $transcribedMatch.Groups[1].Value
    $ageDays = [int] ([DateTime]::UtcNow.Date - [DateTime]::Parse($transcribedOn)).TotalDays
    Write-Host "refresh-play-publishing-state: Policy status transcribed $transcribedOn - $ageDays day(s) old. Only the owner can refresh it."
} else {
    Write-Host 'refresh-play-publishing-state: Policy status block carries no transcription date - read it as unmeasured.'
}

if ($skipped.Count -gt 0) {
    $msg = "refresh-play-publishing-state: left untouched - $($skipped -join '; '). A block is never " +
        'written from a failed read.'
    Write-Error $msg -ErrorAction Continue
    exit 2
}

exit 0
