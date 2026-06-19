#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Publish the FastMediaSorter Google Play store listing (texts + images) from play/listing/.

.DESCRIPTION
    Wraps publish-play-listing.py. Separate from publish-play-release.ps1 (AAB + changelogs).
    Reuses the same service-account key (.secrets/play-console-key.json).

.PARAMETER Mode
    validate (default) - push listing+images into an edit and validate, without committing.
    commit             - publish the listing live (owner-gated; Play may route via review).

.EXAMPLE
    pwsh -File scripts/release/publish-play-listing.ps1
    pwsh -File scripts/release/publish-play-listing.ps1 -Mode commit
#>

[CmdletBinding()]
param(
    [ValidateSet('validate', 'commit')]
    [string] $Mode = 'validate'
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$venvPython = Join-Path $repoRoot ".venv\Scripts\python.exe"
$pyScript = Join-Path $PSScriptRoot "publish-play-listing.py"

if (-not (Test-Path $venvPython)) {
    throw "Virtual environment not found at $venvPython. Configure the project virtual environment first."
}

Write-Host "Invoking Google Play listing uploader (Mode: $Mode)..." -ForegroundColor Cyan
& $venvPython $pyScript $Mode

if ($LASTEXITCODE -ne 0) {
    throw "Google Play listing publication failed (Mode: $Mode)."
}

Write-Host "Google Play listing step completed (Mode: $Mode)." -ForegroundColor Green
