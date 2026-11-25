#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Publish one application's FastMediaSorter Google Play release batch.

.DESCRIPTION
    Stage tracks, notes and optional listing changes in one guarded edit. A completed phone
    production record that was sent for review triggers the existing FGS snapshot check.

.PARAMETER Track
    Exact Google Play track for a single release. Defaults to production.

.PARAMETER Status
    Single-track release status. Supports completed and draft. Both commit the edit.

.PARAMETER Aab
    Single-track bundle. Defaults to the standard phone AAB in DOWNLOADS.

.PARAMETER VersionCode
    Required with a custom AAB in single-track mode.

.PARAMETER VersionName
    Version name from the artifact in single-track mode. The default uses matching build metadata.

.PARAMETER NotesVersionCode
    Fall back to the fastlane changelog for this versionCode if the artifact has no changelog.

.PARAMETER Package
    Single-track application id. The default phone id includes its Wear tracks.

.PARAMETER NotesFile
    Play Console paste-format release notes. Required for apps other than the default package.

.PARAMETER ListingRoot
    Stage listing changes from this root in the same edit as the single-track release.

.PARAMETER Batch
    JSON file with one packageName, a releases array and optional listingRoot.
    Paths in this file are relative to the file. Supply only Batch, Preflight and WhatIf in this mode.

.PARAMETER Preflight
    Approved exact operations, accepted artifact and foreground-service declaration evidence.

.PARAMETER WhatIf
    Print the local release plan without credentials or an API call.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/publish-play-release.ps1 -Batch batch.json -WhatIf

.EXAMPLE
    pwsh -NoProfile -File scripts/release/publish-play-release.ps1 -Track internal -Status draft -WhatIf

.NOTES
    Exit codes: 0 planned or committed, 1 invalid or rejected payload, 2 could not verify.
#>

[CmdletBinding(SupportsShouldProcess)]
param(
    [string] $Track = 'production',
    [string] $Status = 'completed',
    [string] $Aab,
    [int] $VersionCode,
    [string] $VersionName,
    [int] $NotesVersionCode,
    [string] $Package,
    [string] $NotesFile,
    [string] $ListingRoot,
    [string] $Batch,
    [string] $Preflight
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$venvPython = Join-Path $repoRoot '.venv\Scripts\python.exe'
$pyScript = Join-Path $PSScriptRoot 'publish-play-release.py'

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Warning "Virtual environment not found at $venvPython. Configure the project virtual environment first."
    exit 2
}

if ($Batch) {
    $singleParameters = 'Track', 'Status', 'Aab', 'VersionCode', 'VersionName', 'NotesVersionCode', 'Package', 'NotesFile', 'ListingRoot'
    foreach ($parameter in $singleParameters) {
        if ($PSBoundParameters.ContainsKey($parameter)) {
            throw "-Batch supplies the release inputs. Remove -$parameter."
        }
    }
    $uploaderArgs = @('--batch', $Batch)
} else {
    $uploaderArgs = @($Track, $Status)
    if ($Aab) { $uploaderArgs += @('--aab', $Aab) }
    if ($VersionCode) { $uploaderArgs += @('--version-code', "$VersionCode") }
    if ($VersionName) { $uploaderArgs += @('--version-name', $VersionName) }
    if ($NotesVersionCode) { $uploaderArgs += @('--notes-code', "$NotesVersionCode") }
    if ($Package) { $uploaderArgs += @('--package', $Package) }
    if ($NotesFile) { $uploaderArgs += @('--notes-file', $NotesFile) }
    if ($ListingRoot) { $uploaderArgs += @('--listing-root', $ListingRoot) }
}
if ($Preflight) { $uploaderArgs += @('--preflight', $Preflight) }
if ($WhatIfPreference) { $uploaderArgs += '--dry-run' }

$commits = @(& $venvPython -B -u $pyScript @uploaderArgs | ForEach-Object {
    $line = [string]$_
    Write-Host $line
    if ($line -match '^PLAY-COMMITTED versionCode=(?<code>\d+) versionName=(?<name>.*?) package=(?<package>\S+) track=(?<track>\S+) status=(?<status>\S+) held=(?<held>[01])$') {
        [pscustomobject]@{
            VersionCode = [long]$Matches.code
            VersionName = $Matches.name
            Package = $Matches.package
            Track = $Matches.track
            Status = $Matches.status
            Held = ($Matches.held -eq '1')
        }
    }
})
$pyExit = $LASTEXITCODE
if ($pyExit -eq 2) {
    Write-Warning "Google Play publication stopped. Follow the uploader's instructions before another attempt."
    exit 2
}
if ($pyExit -ne 0) {
    Write-Error "Google Play publication failed (uploader exit $pyExit)." -ErrorAction Continue
    exit 1
}
if ($WhatIfPreference) {
    exit 0
}

# The ledger and FGS baseline are keyed by the committed record, including in an app-wide batch.
$phonePackage = 'com.sza.fastmediasorter'
$ledgerScript = Join-Path $PSScriptRoot '..\utils\log-release-ledger-publish.ps1'
foreach ($commit in $commits) {
    $ledgerKey = switch ("$($commit.Package)/$($commit.Track)") {
        "$phonePackage/production" { 'play' }
        "${phonePackage}/wear:production" { 'play-wear' }
        "${phonePackage}.watchface/wear:production" { 'play-watchface' }
        "${phonePackage}.watchface/production" { 'play-watchface' }
    }
    if ($ledgerKey -and $commit.VersionName) {
        $state = if ($commit.Held) { 'pending' } else { 'submitted' }
        & $ledgerScript -Key $ledgerKey -Version $commit.VersionName -State $state -Note "track $($commit.Track) status $($commit.Status)"
    } elseif ($ledgerKey) {
        Write-Warning "Release ledger: no versionName in the committed record for $($commit.Track)."
    }

    if ($commit.Package -eq $phonePackage -and $commit.Track -eq 'production' -and $commit.Status -eq 'completed') {
        if ($commit.Held) {
            Write-Host "Confirm the FGS snapshot after sending the held release (versionCode $($commit.VersionCode))."
        } else {
            $confirmScript = Join-Path $PSScriptRoot 'confirm-fgs-snapshot.ps1'
            & $confirmScript -VersionCode $commit.VersionCode
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "FGS snapshot needs confirmation. Run confirm-fgs-snapshot.ps1 -VersionCode $($commit.VersionCode)."
            }
        }
    }
}
exit 0
