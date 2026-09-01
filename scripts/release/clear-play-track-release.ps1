#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Remove the release records from one Google Play track - a one-way write, gated by -Confirm.

.DESCRIPTION
    Wraps scripts/release/clear-play-track-release.py in the project virtual environment. The Play
    Developer API has no "cancel release" and no "discard changes" endpoint: the only way to take a
    release record off a track is to update that track with an empty release list, which is what the
    Python half does for the single named track.

    It refuses unless every release on that track is a `draft`, because a draft was never submitted
    and never distributed. A `completed` or `inProgress` release is live or in review, and clearing
    it off a track is NOT the Console's "cancel release" - use -AllowNonDraft only when that
    difference is understood and intended.

    Nothing else is touched: no bundle is uploaded, no other track is read or written, no listing is
    edited. Read the resulting state back with scripts/release/read-play-tracks.ps1.

.PARAMETER Track
    Track to clear, e.g. wear:internal.

.PARAMETER Confirm
    Required for the write. Without it the run is a dry run that reports what would be cleared.

.PARAMETER AllowNonDraft
    Also clear a track whose releases are not all drafts.

.PARAMETER Package
    Application id. Defaults to com.sza.fastmediasorter.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/clear-play-track-release.ps1 -Track wear:internal

.EXAMPLE
    pwsh -NoProfile -File scripts/release/clear-play-track-release.ps1 -Track wear:internal -Confirm

.NOTES
    Exit codes:
      0 - the track was cleared (or, without -Confirm, the dry run reported what it would clear)
      1 - refused: the track carries a non-draft release and -AllowNonDraft was not given
      2 - could not verify: no virtual environment, no service-account key, or the API call failed
      3 - nothing to do: the track already holds no release records
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Track,
    [switch] $Confirm,
    [switch] $AllowNonDraft,
    [string] $Package = 'com.sza.fastmediasorter'
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
