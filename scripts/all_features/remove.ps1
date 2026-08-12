<#
.SYNOPSIS
    Remove one record from the ALL_FEATURES inventory (docs/ALL_FEATURES.jsonl), by id.

.DESCRIPTION
    The counterpart to add.ps1's upsert: a physical delete, not a status flip.
    patch.ps1 -Status removed keeps the record in the file, where /skill-release
    reads it as a capability that was withdrawn and prints it in the release
    notes - which is right for a real feature that shipped and then went away,
    and wrong for a record that should never have existed (a mistaken add, or a
    probe written by a test harness).

    Deliberately hard to fire by accident, mirroring scripts/spec_catalog/delete.ps1:
    without -Confirm the call only reports what it would remove and exits 1. An id
    that is not in the inventory is not an error - the call is a no-op and exits 0,
    so repeated cleanup is idempotent.

    Write convention (line order, LF, UTF-8 without BOM) is add.ps1's, so a file
    round-tripped through add + remove is byte-identical to the original.

.PARAMETER Id
    Record id to remove, exactly as it appears in the inventory ('<area>.<feature>').

.PARAMETER Confirm
    Apply the removal. Without it, the call is a dry run that exits 1.

.PARAMETER NoLegal
    Operate on the gitignored docs/ALL_FEATURES_noLegal.jsonl instead.

.PARAMETER Quiet
    Suppress the success line.

.EXAMPLE
    .\scripts\all_features\remove.ps1 -Id "spec-tooling.sandbox_probe" -Confirm

.NOTES
    Exit codes:
      0 record removed, or no such id (no-op).
      1 invalid invocation, -Confirm withheld, or the inventory could not be read or written.
#>
[CmdletBinding()]
param(
    [string]$Id,
    [switch]$Confirm,
    [switch]$NoLegal,
    [switch]$Quiet,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
trap { Write-Host $_ -ForegroundColor Red; exit 1 }

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/all_features/remove.ps1'
    exit $LASTEXITCODE
}
if (-not $Id) {
    Write-Error 'remove.ps1 requires -Id <record id>. Run with -Help for the parameter list.' -ErrorAction Continue
    exit 1
}

# Resolve repo root (shared with add.ps1 / patch.ps1 via _lib.ps1)
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
. (Join-Path $scriptDir '_lib.ps1')
$repoRoot = Resolve-FeatureRepoRoot -ScriptDir $scriptDir
$fileName = if ($NoLegal) { "ALL_FEATURES_noLegal.jsonl" } else { "ALL_FEATURES.jsonl" }
$dataFile = Get-FeatureInventoryPath -RepoRoot $repoRoot -NoLegal:$NoLegal

if (-not (Test-Path $dataFile)) {
    if (-not $Quiet) { Write-Host "[ALL_FEATURES] docs/$fileName does not exist - nothing to remove." -ForegroundColor DarkGray }
    exit 0
}

# S1537: read -> filter -> write is one critical section. The dry run and the two no-op exits
# stay outside it, so a call that changes nothing never makes another writer wait.
$hit = $null
Enter-FeatureLock -RepoRoot $repoRoot
try {
    $existing = Read-FeatureLines -Path $dataFile

    $kept = New-Object System.Collections.Generic.List[string]
    foreach ($line in $existing) {
        try { $obj = $line | ConvertFrom-Json } catch { $obj = $null }
        if ($obj -and $obj.id -eq $Id) { $hit = $obj } else { $kept.Add($line) }
    }

    if ($hit -and $Confirm) { Write-FeatureLines -Path $dataFile -Lines $kept }
}
finally { Exit-FeatureLock }

if (-not $hit) {
    if (-not $Quiet) { Write-Host "[ALL_FEATURES] no record '$Id' in docs/$fileName (no-op)." -ForegroundColor DarkGray }
    exit 0
}

if (-not $Confirm) {
    Write-Output "Would remove '$Id' ($($hit.name) - area: $($hit.area), status: $($hit.status)) from docs/$fileName. Re-run with -Confirm to apply."
    exit 1
}

if (-not $Quiet) {
    Write-Host "[ALL_FEATURES] removed '$Id' -> docs/$fileName" -ForegroundColor Green
}
exit 0
