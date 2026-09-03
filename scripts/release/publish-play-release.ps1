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
    The versionCode carried by -Aab. Only needed with -Aab: without it the script reads the phone
    bundle's own AGP output-metadata.json, which describes that artifact and not this one.

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

.NOTES
    Exit codes (mirrors publish-play-release.py, S2346):
      0 - the bundle is on the track and the edit was committed.
      1 - the release is at fault: the AAB is missing, an argument contradicts the artifact, or
          Play rejected the payload. The Foreground-service-permissions 403 on commit lands here
          on purpose - it names an owner action and must stay visible as a finding.
      2 - could not verify: the virtual environment is absent, or a sustained transient failure
          (5xx, rate limit, network). The release is NOT implicated - re-run later.

    Code 2 must survive the trip out of Python. Under $ErrorActionPreference = 'Stop' a `throw`
    kills the process with 1, so wrapping the child's exit code in one collapses the distinction
    the Python side just made and no caller - including /skill-release, which files a non-zero
    code as a failed publication - can ever see it.
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
    Write-Warning "Virtual environment not found at $venvPython. Configure the project virtual environment first."
    Write-Warning "CANNOT VERIFY the publication without it - this says nothing about the release itself."
    exit 2
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
$pyExit = $LASTEXITCODE

# The child already distinguishes "the release is at fault" from "could not verify"; this only has
# to carry the distinction outward intact. Anything unexpected is reported as a fault rather than
# silently promoted to "could not verify" - an unknown code is not evidence of innocence.
if ($pyExit -eq 2) {
    Write-Warning "Google Play publication could NOT be verified (Track: $Track). The release is not implicated - read the uploader's output above before re-running."
    exit 2
}

if ($pyExit -ne 0) {
    Write-Error "Google Play Console publication failed (Track: $Track, uploader exit $pyExit)." -ErrorAction Continue
    exit 1
}

Write-Host "Google Play Console publication completed." -ForegroundColor Green
exit 0
