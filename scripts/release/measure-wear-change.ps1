#!/usr/bin/env pwsh
#requires -Version 7.0
<#
.SYNOPSIS
    Measure how much the watch sources changed since the watch's last store release.

.DESCRIPTION
    A watch artifact lengthens the review of every Play submission it rides in, so the watch
    campaign publishes the watch only when it changed (contract GOOGLE-PLAY rule 31, S4799). This
    script answers "did it change" from the tree instead of from memory.

    The last release is the versionName on the `wear:production` track, read through
    read-play-tracks.ps1 -Json unless -SinceVersionName names it. A watch versionName encodes the
    local instant it was stamped (scripts/utils/build-version-stamp.ps1), so the base is the last
    commit made before that instant. The comparison runs from that commit to the working tree, so
    uncommitted and untracked watch files count too. A release built from a tree with uncommitted
    edits is approximated by the commit before it.

    Paths measured: wear/src and wear/build.gradle.kts. Files under src/test, src/androidTest and
    src/debug do not ship and are counted apart; every other file is a shipping file.

.PARAMETER SinceVersionName
    The versionName of the last watch release. Skips the Play read; use it offline or to measure
    against an older release.

.PARAMETER RepoRoot
    The git work tree to measure. Defaults to this repository.

.PARAMETER Json
    Emit one object: sinceVersionName, sinceInstant, baseCommit, shipping, tests, verdict.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/measure-wear-change.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/release/measure-wear-change.ps1 -SinceVersionName 2.61.1121.738

.NOTES
    Exit codes:
      0 - CHANGED: at least one shipping watch file differs from the last release's base.
      3 - UNCHANGED: no shipping watch file differs (test-only changes land here too).
      2 - could not verify: the Play track holds no watch release, the versionName encodes no
          instant, no commit precedes it, or git failed.
#>
[CmdletBinding()]
param(
    [string] $SinceVersionName,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\utils\build-version-stamp.ps1')

$measuredPaths = @('wear/src', 'wear/build.gradle.kts')

function Write-CouldNotVerify([string] $reason) {
    Write-Host "measure-wear-change: COULD NOT VERIFY - $reason"
    exit 2
}

if ([string]::IsNullOrWhiteSpace($SinceVersionName)) {
    $raw = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'read-play-tracks.ps1') -Json
    if ($LASTEXITCODE -ne 0) { Write-CouldNotVerify 'read-play-tracks.ps1 could not read the Play tracks' }
    $state = ($raw -join [Environment]::NewLine) | ConvertFrom-Json
    if (-not $state.wear_production) { Write-CouldNotVerify 'wear:production holds no completed release' }
    $SinceVersionName = [string]$state.wear_production.version_name
}

$instant = ConvertFrom-BuildVersionName $SinceVersionName
if ($null -eq $instant) { Write-CouldNotVerify "versionName '$SinceVersionName' encodes no instant" }
$instantText = $instant.ToString('yyyy-MM-dd HH:mm')

$base = & git -C $RepoRoot rev-list -1 "--before=$instantText" HEAD 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($base)) {
    Write-CouldNotVerify "no commit precedes $instantText"
}
$base = $base.Trim()

$numstat = & git -C $RepoRoot diff --numstat $base -- @measuredPaths
if ($LASTEXITCODE -ne 0) { Write-CouldNotVerify 'git diff failed' }
$untracked = & git -C $RepoRoot ls-files --others --exclude-standard -- @measuredPaths
if ($LASTEXITCODE -ne 0) { Write-CouldNotVerify 'git ls-files failed' }

$files = [System.Collections.Generic.List[object]]::new()
foreach ($line in @($numstat)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $parts = $line -split "`t", 3
    # Binary files report '-' for both counts.
    $added = if ($parts[0] -eq '-') { 0 } else { [int]$parts[0] }
    $deleted = if ($parts[1] -eq '-') { 0 } else { [int]$parts[1] }
    $files.Add([pscustomobject]@{ Path = $parts[2]; Added = $added; Deleted = $deleted })
}
foreach ($path in @($untracked)) {
    if ([string]::IsNullOrWhiteSpace($path)) { continue }
    $count = @(Get-Content -LiteralPath (Join-Path $RepoRoot $path)).Count
    $files.Add([pscustomobject]@{ Path = $path; Added = $count; Deleted = 0 })
}

$nonShipping = '^wear/src/(test|androidTest|debug)/'
$shipping = @($files | Where-Object { $_.Path -notmatch $nonShipping })
$tests = @($files | Where-Object { $_.Path -match $nonShipping })
$verdict = if ($shipping.Count -gt 0) { 'CHANGED' } else { 'UNCHANGED' }

if ($Json) {
    [pscustomobject]@{
        sinceVersionName = $SinceVersionName
        sinceInstant     = $instantText
        baseCommit       = $base
        shipping         = $shipping
        tests            = $tests
        verdict          = $verdict
    } | ConvertTo-Json -Depth 4
}
else {
    $sum = { param($set) '+{0} -{1}' -f (($set | Measure-Object Added -Sum).Sum ?? 0), (($set | Measure-Object Deleted -Sum).Sum ?? 0) }
    Write-Host "measure-wear-change: since watch $SinceVersionName ($instantText), base $($base.Substring(0, 9))"
    Write-Host ("  shipping files changed: {0} ({1})" -f $shipping.Count, (& $sum $shipping))
    Write-Host ("  test/debug files changed: {0}" -f $tests.Count)
    foreach ($f in ($shipping | Sort-Object Path | Select-Object -First 40)) {
        Write-Host ("    {0}  +{1} -{2}" -f $f.Path, $f.Added, $f.Deleted)
    }
    if ($shipping.Count -gt 40) { Write-Host "    .. and $($shipping.Count - 40) more" }
    Write-Host "verdict: $verdict"
}

if ($verdict -eq 'CHANGED') { exit 0 }
exit 3
