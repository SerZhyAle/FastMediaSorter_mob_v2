#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Read-only pre-send check for Play review batches: refuses a plan in which the phone and the
    watch would travel in one batch, or in which one rides out beside a held artifact of the other.

.DESCRIPTION
    S4083. Release 42 publishes the phone app, the watch app and the watch face at one moment.
    Play groups every unsent change of ONE app into ONE review batch, and the phone
    (track `production`) and the watch (track `wear:production`) are the same app,
    com.sza.fastmediasorter. A watch rejection therefore holds the phone with it - 2026-08-31 and
    2026-09-25 (docs/PLAY_PUBLISHING_STATE.md, Recovery plan step 1, ADR-1). While a rejection
    stands, edits().commit() fails with HTTP 400 and every upload lands in Publishing overview as
    an unsent change, so the batch is formed by the owner's click, not by the uploader.

    The API cannot list the unsent changes and cannot show review state. What it CAN read is which
    artifact each track holds, and that is enough to judge a plan: the owner states what he is
    about to send (-Send) and what stays behind with "Save for later" (-Hold), and this script
    compares the plan with the held artifacts. The watch face is its own Play app
    (com.sza.fastmediasorter.watchface), so it is a separate batch by construction and never
    conflicts with the others.

    Nothing is uploaded, committed or changed. Sending, "Save for later" and reading the unsent
    list in Publishing overview are owner-only Console actions; the checklist at the end names them.

.PARAMETER Send
    Groups the owner is about to send for review in ONE batch: phone, watch, face.

.PARAMETER Hold
    Groups that stay behind with "Save for later" in the same Publishing overview.

.PARAMETER PhoneCode
    versionCode of the phone artifact uploaded in this session. When the live `production` track holds it, the phone group is pending.

.PARAMETER WearCode
    versionCode of the watch artifact uploaded in this session; pending when `wear:production` holds it.

.PARAMETER StateFile
    Reader JSON (read-play-tracks.ps1 -Json) instead of a live read; for tests and offline review.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-play-review-batches.ps1 -Send phone -Hold watch -PhoneCode 260926004 -WearCode 260926008

.NOTES
    Exit codes:
      0 - the plan keeps the phone and the watch in separate batches
      1 - the plan lets the watch and the phone share a batch (reasons printed)
      2 - could not verify: bad arguments or the track state could not be read
#>
[CmdletBinding()]
param(
    [string[]] $Send = @(),
    [string[]] $Hold = @(),
    [long] $PhoneCode,
    [long] $WearCode,
    [string] $StateFile
)

$ErrorActionPreference = 'Stop'
$valid = @('phone', 'watch', 'face')

function Stop-Unverified([string] $Message) {
    Write-Host 'assert-play-review-batches: COULD NOT VERIFY' -ForegroundColor Yellow
    Write-Error "assert-play-review-batches: $Message" -ErrorAction Continue
    exit 2
}

# `pwsh -File` hands "phone,watch" over as one string, so split it here.
$Send = @($Send | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { $_.Trim().ToLowerInvariant() })
$Hold = @($Hold | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { $_.Trim().ToLowerInvariant() })
foreach ($g in @($Send + $Hold)) {
    if ($valid -notcontains $g) { Stop-Unverified "unknown group '$g' - use phone, watch or face." }
}
if ($Send.Count -eq 0) { Stop-Unverified 'name at least one group in -Send.' }
foreach ($g in $Send) {
    if ($Hold -contains $g) { Stop-Unverified "group '$g' is in both -Send and -Hold." }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ($StateFile) {
    if (-not (Test-Path -LiteralPath $StateFile)) { Stop-Unverified "state file '$StateFile' not found." }
    $raw = Get-Content -LiteralPath $StateFile -Raw
}
else {
    $reader = Join-Path $PSScriptRoot 'read-play-tracks.ps1'
    $raw = (& pwsh -NoProfile -File $reader -Json) -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0) { Stop-Unverified 'could not read the live track state (read-play-tracks.ps1 failed).' }
}
try { $state = $raw | ConvertFrom-Json }
catch { Stop-Unverified "track state is not JSON: $($_.Exception.Message)" }

function Test-TrackHolds($State, [string] $TrackName, [long] $Code) {
    if ($Code -le 0) { return $false }
    foreach ($track in @($State.tracks)) {
        if ($track.track -ne $TrackName) { continue }
        foreach ($release in @($track.releases)) {
            if (@($release.versionCodes | ForEach-Object { [long]$_ }) -contains $Code) { return $true }
        }
    }
    return $false
}

# A group is pending when its artifact from this session sits on its track. The face lives in
# another app and is read with its own -Package; it never shares a batch with phone or watch.
$pending = @{
    phone = (Test-TrackHolds $state 'production' $PhoneCode)
    watch = (Test-TrackHolds $state 'wear:production' $WearCode)
}

$problems = New-Object System.Collections.Generic.List[string]
if (($Send -contains 'phone') -and ($Send -contains 'watch')) {
    $problems.Add('phone and watch are in one -Send batch: a watch rejection would hold the phone (ADR-1).')
}
foreach ($pair in @(@('phone', 'watch'), @('watch', 'phone'))) {
    $sent = $pair[0]; $other = $pair[1]
    if (($Send -contains $sent) -and ($Send -notcontains $other) -and $pending[$other] -and ($Hold -notcontains $other)) {
        $problems.Add("'$sent' is sent while the $other artifact is held on its track and not in -Hold: it would ride in the same batch. Use 'Save for later' on the $other group and add it to -Hold.")
    }
}

Write-Host "Review batch plan: send [$($Send -join ', ')], hold [$($Hold -join ', ')]"
Write-Host ("  phone artifact pending on production:      {0}" -f $pending['phone'])
Write-Host ("  watch artifact pending on wear:production: {0}" -f $pending['watch'])
Write-Host '  face: own Play app (com.sza.fastmediasorter.watchface) - separate batch by construction'

Write-Host ''
Write-Host 'Owner-only Console steps (nothing here can do them):' -ForegroundColor Cyan
Write-Host '  1. Publishing overview: read the whole unsent-change list; the phone and the watch groups must be distinct entries.'
Write-Host '  2. "Save for later" on every group in -Hold; confirm it left the list to be sent.'
Write-Host '  3. Send ONLY the -Send group; re-read the list - the held group must still be unsent.'
Write-Host '  4. Send the next group separately, after the first is accepted into review.'
Write-Host '  Fallback if the watch is rejected: phone ships alone; the watch is re-submitted on its own (docs/PLAY_PUBLISHING_STATE.md, Release batches).'

if ($problems.Count -gt 0) {
    Write-Host 'assert-play-review-batches: FAIL' -ForegroundColor Red
    foreach ($p in $problems) { Write-Error "assert-play-review-batches: $p" -ErrorAction Continue }
    exit 1
}
Write-Host 'assert-play-review-batches: PASS - plan keeps the phone and the watch in separate batches.' -ForegroundColor Green
exit 0
