#requires -Version 7.0
<#
.SYNOPSIS
    Regression tests for post-change gate dispatch and recovery hints.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$facadePath = Join-Path $repoRoot 'scripts/post-change.ps1'
$hintsPath = Join-Path $repoRoot 'scripts/quality/gate-recovery-hints.psd1'
$facade = Get-Content -LiteralPath $facadePath -Raw

foreach ($removedLabel in @(
    'flavor-flag-gate',
    'public-mutable-flow-gate',
    'deprecated-pm-flags-gate'
)) {
    if ($facade -match ('Invoke-Gate "' + [regex]::Escape($removedLabel) + '"')) {
        throw "Duplicate lexical dispatch '$removedLabel' is present."
    }
}

$neuroslopRoutes = [regex]::Matches($facade, 'Invoke-Gate "neuroslop-gate"').Count
if ($neuroslopRoutes -ne 1) {
    throw "Expected one neuroslop-gate route, got $neuroslopRoutes."
}

if ($facade -notmatch '\$runsDocIconGate = Test-DocIconGateRoute -ChangedFiles \$normChangedFiles') {
    throw 'Document-icon classifier is not wired to the complete changed set.'
}
if ($facade -notmatch '(?s)if \(\$runsDocIconGate\) \{\s*Invoke-Gate "doc-icons-sync-gate"') {
    throw 'Document-icon gate is missing or not conditional.'
}

$helpOutput = & pwsh -NoProfile -File $facadePath -? 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $helpOutput -notmatch '-Deleted') {
    throw 'post-change help does not expose the declared-deletion parameter.'
}

$rejectedDeletion = & pwsh -NoProfile -File $facadePath `
    -Deleted 'scripts/post-change.ps1' `
    -Target 'post-change-tests' `
    -Description 'reject an existing declared deletion' `
    -ChangeType Script 2>&1 | Out-String
if ($LASTEXITCODE -ne 2 -or $rejectedDeletion -notmatch 'named as deleted but still on disk') {
    throw 'post-change did not reject an existing path declared as deleted.'
}

if ($facade -notmatch '\$deletedFiles = @\(' -or $facade -notmatch '\$deletedLogEntries = @\(') {
    throw 'post-change does not route an accepted declared deletion into closure bookkeeping.'
}

if ($facade -notmatch '\$catalogChangedFiles = @\(\$changedFiles\) \+ @\(\$deletedFiles\)') {
    throw 'Declared deletions are not included in catalog-sync input.'
}

$hints = Import-PowerShellDataFile -LiteralPath $hintsPath
$labels = [regex]::Matches($facade, 'Invoke-Gate "([^"]+)"') |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique
$missingHints = @($labels | Where-Object { -not $hints.ContainsKey($_) })
if ($missingHints.Count -gt 0) {
    throw "Missing recovery hint(s): $($missingHints -join ', ')."
}

foreach ($removedLabel in @(
    'flavor-flag-gate',
    'public-mutable-flow-gate',
    'deprecated-pm-flags-gate'
)) {
    if ($hints.ContainsKey($removedLabel)) {
        throw "Removed route '$removedLabel' still has a recovery hint."
    }
}

Write-Output "post-change tests: PASS ($($labels.Count) routed labels with hints)"
