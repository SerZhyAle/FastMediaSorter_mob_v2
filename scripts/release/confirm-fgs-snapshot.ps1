#requires -Version 7.0
<#
.SYNOPSIS
    Promote a release's pending foreground-service type snapshot to the confirmed baseline
    once Google Play production carries that versionCode (S4499).

.DESCRIPTION
    retain-deobfuscation.ps1 writes <ArchiveRoot>/<versionCode>/fgs-types.pending.json when a
    standard bundle is built, before Play has accepted it. assert-fgs-types-vs-previous.ps1 takes
    its baseline from fgs-types.json only, so a bundle Play refused (release 47: HTTP 403 over
    dataSync) never becomes "the previous release". This script is the single step that turns a
    pending snapshot into fgs-types.json.

    publish-play-release.ps1 calls it after a production commit that is not held. Run it by hand
    when the release reached production some other way, or after the held changes were sent for
    review in the Console.

    A confirmed fgs-types.json that already exists for the versionCode wins: the pending file is
    removed and nothing else changes.

.PARAMETER VersionCode
    The versionCode Play production now carries; the archive key of the snapshot.

.PARAMETER ArchiveRoot
    The deobfuscation archive root that holds <versionCode>/fgs-types.pending.json. Default: the
    resolved Deobfuscation artifact sink - the root retain-deobfuscation.ps1 writes to.

.PARAMETER Help
    Print this help and exit 0.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/confirm-fgs-snapshot.ps1 -VersionCode 261030100

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - the snapshot is confirmed: promoted now, or fgs-types.json already existed.
      1 - there is no snapshot of that versionCode, pending or confirmed: nothing to confirm.
      2 - could not verify: the archive root is unreachable, or the pending file is unreadable,
          has no types, or names another versionCode.
#>
[CmdletBinding()]
param(
    [long] $VersionCode,
    [string] $ArchiveRoot,
    [switch] $Help
)

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($VersionCode -le 0) {
    Write-Host 'confirm-fgs-snapshot: COULD NOT VERIFY (-VersionCode is required)' -ForegroundColor Red
    exit 2
}

if (-not $ArchiveRoot) {
    . (Join-Path $PSScriptRoot '../utils/project-paths.ps1')
    $ArchiveRoot = Get-ArtifactSink -Kind Deobfuscation -Quiet
}
if (-not $ArchiveRoot -or -not (Test-Path -LiteralPath $ArchiveRoot -PathType Container)) {
    Write-Host "confirm-fgs-snapshot: COULD NOT VERIFY (archive root not found: $ArchiveRoot)" -ForegroundColor Red
    exit 2
}

$releaseDir = Join-Path $ArchiveRoot "$VersionCode"
$pendingPath = Join-Path $releaseDir 'fgs-types.pending.json'
$confirmedPath = Join-Path $releaseDir 'fgs-types.json'
$hasPending = Test-Path -LiteralPath $pendingPath -PathType Leaf
$hasConfirmed = Test-Path -LiteralPath $confirmedPath -PathType Leaf

if ($hasConfirmed) {
    if ($hasPending) { Remove-Item -LiteralPath $pendingPath -Force }
    Write-Host "confirm-fgs-snapshot: PASS (versionCode $VersionCode already confirmed: $confirmedPath)" -ForegroundColor Green
    exit 0
}
if (-not $hasPending) {
    Write-Host "confirm-fgs-snapshot: FAIL (no fgs-types snapshot for versionCode $VersionCode under $ArchiveRoot - the bundle was built without retention, or retention could not read its merged manifest)" -ForegroundColor Red
    exit 1
}

$snap = $null
try { $snap = [System.IO.File]::ReadAllText($pendingPath) | ConvertFrom-Json } catch { $snap = $null }
if (-not $snap -or $snap.PSObject.Properties.Name -notcontains 'types' -or $snap.PSObject.Properties.Name -notcontains 'versionCode') {
    Write-Host "confirm-fgs-snapshot: COULD NOT VERIFY (pending snapshot unreadable or has no types/versionCode: $pendingPath)" -ForegroundColor Red
    exit 2
}
if ([long]$snap.versionCode -ne $VersionCode) {
    Write-Host "confirm-fgs-snapshot: COULD NOT VERIFY (pending snapshot names versionCode $($snap.versionCode), expected ${VersionCode}: $pendingPath)" -ForegroundColor Red
    exit 2
}

Move-Item -LiteralPath $pendingPath -Destination $confirmedPath
$typeList = if (@($snap.types).Count -gt 0) { @($snap.types) -join ', ' } else { '(none)' }
Write-Host "confirm-fgs-snapshot: PASS (versionCode $VersionCode confirmed; types: $typeList -> $confirmedPath)" -ForegroundColor Green
exit 0
