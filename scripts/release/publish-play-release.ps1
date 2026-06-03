#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Publish a FastMediaSorter standard AAB release to Google Play Console.

.DESCRIPTION
    Wired as part of the automated Google Play Console publishing pipeline.
    Calls publish-play-release.py in the project virtual environment.

.PARAMETER Track
    Target track in Google Play. Default: production.
    Supported: internal, alpha, beta, production.

.PARAMETER Status
    Rollout status. Default: completed (automated rollout).
    Supported: completed, draft.

.EXAMPLE
    pwsh -File scripts/release/publish-play-release.ps1

.EXAMPLE
    pwsh -File scripts/release/publish-play-release.ps1 -Track internal -Status draft
#>

[CmdletBinding()]
param(
    [string] $Track = "production",
    [string] $Status = "completed"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$venvPython = Join-Path $repoRoot ".venv\Scripts\python.exe"
$pyScript = Join-Path $PSScriptRoot "publish-play-release.py"

if (-not (Test-Path $venvPython)) {
    throw "Virtual environment not found at $venvPython. Configure the project virtual environment first."
}

Write-Host "Invoking Google Play Console uploader (Track: $Track, Status: $Status)..." -ForegroundColor Cyan
& $venvPython $pyScript $Track $Status

if ($LASTEXITCODE -ne 0) {
    throw "Google Play Console publication failed."
}

Write-Host "Google Play Console publication completed." -ForegroundColor Green
