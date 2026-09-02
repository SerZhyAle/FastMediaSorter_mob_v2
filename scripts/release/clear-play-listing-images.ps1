#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Delete one image type from the Play store listing - a one-way write, gated by -Confirm.

.DESCRIPTION
    Wraps scripts/release/clear-play-listing-images.py in the project virtual environment. Written
    for the phone-only submission of 2026-09-02, whose listing still carried Wear OS screenshots
    uploaded in an earlier window by publish-play-listing.ps1.

    Read this before using it: deleting the images does NOT remove the pending row from Publishing
    overview. The row becomes a deletion instead of an upload and the batch keeps its size, because
    the Play Developer API has no discard operation at all. Use this when the material must go, not
    to shorten a pending-changes list.

    Reversible: the sources stay under play/listing/<locale>/images/, so publish-play-listing.ps1
    puts them back when the watch is submitted on its own.

.PARAMETER ImageType
    Play image type, e.g. wearScreenshots.

.PARAMETER Locales
    Comma-separated BCP-47 tags. Omit to act on every locale the listing carries.

.PARAMETER Confirm
    Required for the write. Without it the run is a dry run that reports what it would delete.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/clear-play-listing-images.ps1 -ImageType wearScreenshots

.EXAMPLE
    pwsh -NoProfile -File scripts/release/clear-play-listing-images.ps1 -ImageType wearScreenshots -Confirm

.NOTES
    Exit codes:
      0 - the images were deleted (or, without -Confirm, the dry run reported what it would delete)
      2 - could not verify: no virtual environment, no service-account key, or the API call failed
      3 - nothing to do: no image of that type exists in any requested locale
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ImageType,
    [string] $Locales = '',
    [switch] $Confirm,
    [string] $Package = 'com.sza.fastmediasorter'
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$venvPython = Join-Path $repoRoot '.venv\Scripts\python.exe'
$pyScript = Join-Path $PSScriptRoot 'clear-play-listing-images.py'

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Error "clear-play-listing-images: virtual environment not found at $venvPython." -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $pyScript)) {
    Write-Error "clear-play-listing-images: worker not found at $pyScript." -ErrorAction Continue
    exit 2
}

$pyArgs = @($pyScript, '--image-type', $ImageType, '--package', $Package)
if ($Locales) { $pyArgs += @('--locales', $Locales) }
if (-not $Confirm) {
    $pyArgs += '--dry-run'
    Write-Host "clear-play-listing-images: DRY RUN - pass -Confirm to actually delete '$ImageType'." -ForegroundColor Yellow
}

Push-Location $repoRoot
try {
    & $venvPython @pyArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
