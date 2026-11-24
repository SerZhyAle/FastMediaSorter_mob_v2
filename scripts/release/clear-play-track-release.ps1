#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Remove the draft release records from one Google Play track - a one-way write, gated by -Confirm.

.DESCRIPTION
    Wraps scripts/release/clear-play-track-release.py in the project virtual environment. The Play
    Developer API has no "cancel release" and no "discard changes" endpoint. Updating a track with an
    empty release list, which is what the Python half does for the single named track, removes the
    track's `draft` releases and nothing else.

    It therefore refuses unless every release on that track is a `draft`, because a draft was never
    submitted and never distributed. A `completed` or `inProgress` release is live or in review, and
    the empty-release update does not remove it: a `completed` record survived the committed edit on
    every measurement (internal 2026-09-12, 2026-11-19 and 2026-11-24; wear:production 2026-09-12),
    and each run only queued one more pending change. The refusal holds whatever -AllowNonDraft says.

    To take a live release off a track, publish a new `completed` release on it (Console: Create new
    release, then Add from library); the new record replaces the old one.

    Nothing else is touched: no bundle is uploaded, no other track is read or written, no listing is
    edited. Read the resulting state back with scripts/release/read-play-tracks.ps1.

.PARAMETER Track
    Track to clear, e.g. wear:internal.

.PARAMETER Confirm
    Required for the write. Without it the run is a dry run that reports what would be cleared.

.PARAMETER AllowNonDraft
    Retired: it no longer overrides anything. Play removes drafts only, so a track holding any other
    status is refused (exit 1) with or without this switch. Kept so an old invocation reaches the
    refusal and its reason instead of a parameter-binding error.

.PARAMETER Package
    Application id. Defaults to com.sza.fastmediasorter.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/clear-play-track-release.ps1 -Track wear:internal

.EXAMPLE
    pwsh -NoProfile -File scripts/release/clear-play-track-release.ps1 -Track wear:internal -Confirm

.NOTES
    Exit codes:
      0 - the track was cleared (or, without -Confirm, the dry run reported what it would clear)
      1 - refused: the track carries a release that is not a draft, and Play removes drafts only
      2 - could not verify: no virtual environment, no service-account key, or the API call failed
      3 - nothing to do: the track already holds no release records
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Track,
    [switch] $Confirm,
    [switch] $AllowNonDraft,
    [string] $Package = 'com.sza.fastmediasorter',
    [string] $Preflight
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$venvPython = Join-Path $repoRoot '.venv\Scripts\python.exe'
$pyScript = Join-Path $PSScriptRoot 'clear-play-track-release.py'

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Error "clear-play-track-release: virtual environment not found at $venvPython." -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $pyScript)) {
    Write-Error "clear-play-track-release: worker not found at $pyScript." -ErrorAction Continue
    exit 2
}

$pyArgs = @($pyScript, '--track', $Track, '--package', $Package)
if (-not $Confirm) { $pyArgs += '--dry-run' }
if ($AllowNonDraft) { $pyArgs += '--allow-non-draft' }
if ($Preflight) { $pyArgs += @('--preflight', (Resolve-Path -LiteralPath $Preflight).Path) }

if (-not $Confirm) {
    Write-Host "clear-play-track-release: DRY RUN - pass -Confirm to actually clear '$Track'." -ForegroundColor Yellow
}

Push-Location $repoRoot
try {
    & $venvPython @pyArgs
    $pyExit = $LASTEXITCODE
}
finally {
    Pop-Location
}

exit $pyExit
