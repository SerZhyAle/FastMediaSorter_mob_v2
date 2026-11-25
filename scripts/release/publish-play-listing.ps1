#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Publish the FastMediaSorter Google Play store listing (texts + images) from play/listing/.

.DESCRIPTION
    Wraps publish-play-listing.py. Separate from publish-play-release.ps1 (AAB + changelogs).
    Uses SZA_PLAY_SERVICE_ACCOUNT_JSON for an existing key file outside the repositories.

.PARAMETER Mode
    validate (default) - push listing+images into an edit and validate, without committing.
    commit             - publish the listing live (owner-gated; Play may route via review).

.PARAMETER Package
    Play application whose listing is published. Default: the phone package the uploader declares.

.PARAMETER ListingRoot
    Listing tree to publish. Default: play/listing, which must hold every declared locale. Any other
    root (the watch face's play/watchface/listing, S4009) publishes exactly the locale folders it
    holds and leaves every other Play language untouched.

.PARAMETER Preflight
    Approved application-scoped operation list and first-use evidence. Required for every edit.

.PARAMETER WhatIf
    Check the local listing and print its operation list without opening a Play edit.

.EXAMPLE
    pwsh -File scripts/release/publish-play-listing.ps1 -WhatIf
    pwsh -File scripts/release/publish-play-listing.ps1 -Mode commit -Preflight <approved JSON>
    pwsh -File scripts/release/publish-play-listing.ps1 -Mode validate -Package <face package> -ListingRoot play/watchface/listing -Preflight <approved JSON>

.NOTES
    Exit codes (mirrors publish-play-listing.py, S2345):
      0 - the listing was validated, or committed in commit mode.
      1 - the listing is at fault: a missing text file, a text over its Play limit, a listing root
          with no publishable locale, or a payload Play rejected. Fix the listing.
      2 - could not verify: the virtual environment is absent, Play refuses to validate under
          enforcement, or a sustained transient failure (5xx / rate limit / network). The listing
          is NOT implicated - re-run later.

    Code 2 must survive the trip out of Python. Under $ErrorActionPreference = 'Stop' a `throw`
    kills the process with 1, so wrapping the child's exit code in one collapses the distinction
    the Python side just made and the whole fix becomes invisible to any caller.
#>

[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateSet('validate', 'commit')]
    [string] $Mode = 'validate',
    [string] $Package,
    [string] $ListingRoot,
    [string] $Preflight
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$venvPython = Join-Path $repoRoot ".venv\Scripts\python.exe"
$pyScript = Join-Path $PSScriptRoot "publish-play-listing.py"

if (-not (Test-Path $venvPython)) {
    Write-Warning "Virtual environment not found at $venvPython. Configure the project virtual environment first."
    Write-Warning "COULD NOT VERIFY the listing without it - this says nothing about the listing itself."
    exit 2
}

Write-Host "Invoking Google Play listing uploader (Mode: $Mode)..." -ForegroundColor Cyan
$extraArgs = @()
if (-not [string]::IsNullOrWhiteSpace($Package)) { $extraArgs += @('--package', $Package) }
if (-not [string]::IsNullOrWhiteSpace($ListingRoot)) { $extraArgs += @('--listing-root', $ListingRoot) }
if ($Preflight) { $extraArgs += @('--preflight', $Preflight) }
if ($WhatIfPreference) { $extraArgs += '--dry-run' }
& $venvPython $pyScript $Mode @extraArgs
$pyExit = $LASTEXITCODE

# The child already distinguishes "the listing is at fault" from "could not verify"; this only has
# to carry the distinction outward intact. Anything unexpected is reported as a fault rather than
# silently promoted to "could not verify" - an unknown code is not evidence of innocence.
if ($pyExit -eq 2) {
    Write-Warning "Google Play listing step stopped (Mode: $Mode). Follow the uploader's instructions before another attempt."
    exit 2
}

if ($pyExit -ne 0) {
    Write-Error "Google Play listing publication failed (Mode: $Mode, uploader exit $pyExit)." -ErrorAction Continue
    exit 1
}

Write-Host "Google Play listing step completed (Mode: $Mode)." -ForegroundColor Green
exit 0
