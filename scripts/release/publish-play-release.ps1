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

.PARAMETER Aab
    Path to the bundle to publish. Default: the standard phone AAB in DOWNLOADS.
    Supply the watch bundle to publish the Wear OS form factor (S1707).

.PARAMETER VersionCode
    The versionCode carried by -Aab. Only needed with -Aab: without it the script reads
    app_v2/build.gradle.kts, which describes the phone artifact and not this one.

.PARAMETER NotesVersionCode
    Read the fastlane changelogs filed under this versionCode instead of the artifact's own.
    A form-factor release ships the phone release's notes, which are filed under the phone code.

.EXAMPLE
    pwsh -File scripts/release/publish-play-release.ps1

.EXAMPLE
    pwsh -File scripts/release/publish-play-release.ps1 -Track internal -Status draft

.EXAMPLE
    pwsh -File scripts/release/publish-play-release.ps1 -Track 'wear:production' `
        -Aab DOWNLOADS/FastMediaSorter_wear_release.aab -VersionCode 26082322 -NotesVersionCode 260823225
#>

[CmdletBinding()]
param(
    [string] $Track = "production",
    [string] $Status = "completed",
    [string] $Aab,
    [int] $VersionCode,
    [int] $NotesVersionCode
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$venvPython = Join-Path $repoRoot ".venv\Scripts\python.exe"
$pyScript = Join-Path $PSScriptRoot "publish-play-release.py"

if (-not (Test-Path $venvPython)) {
    throw "Virtual environment not found at $venvPython. Configure the project virtual environment first."
}

$extraArgs = @()
if (-not [string]::IsNullOrWhiteSpace($Aab)) {
    if (-not (Test-Path $Aab)) { throw "AAB not found at $Aab" }
    if ($VersionCode -le 0) { throw "-VersionCode is required with -Aab: the fallback reads app_v2, which describes a different artifact." }
    $extraArgs += @('--aab', (Resolve-Path $Aab).Path, '--version-code', "$VersionCode")
}
if ($NotesVersionCode -gt 0) { $extraArgs += @('--notes-code', "$NotesVersionCode") }

Write-Host "Invoking Google Play Console uploader (Track: $Track, Status: $Status)..." -ForegroundColor Cyan
& $venvPython $pyScript $Track $Status @extraArgs

if ($LASTEXITCODE -ne 0) {
    throw "Google Play Console publication failed."
}

Write-Host "Google Play Console publication completed." -ForegroundColor Green
