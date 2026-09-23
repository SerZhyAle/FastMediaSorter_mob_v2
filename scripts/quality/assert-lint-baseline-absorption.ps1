#requires -Version 7.0
<#
.SYNOPSIS
    S3459 gate: refuse an Android lint baseline that ABSORBED a finding absent from the committed
    identifier snapshot. The lint twin of assert-detekt-baseline-absorption.ps1 (S1356).

.DESCRIPTION
    `check-lint.ps1 -Regenerate` deletes <module>/lint-baseline.xml and AGP lint rewrites it from
    every live finding in one call - the same wholesale re-freeze that absorbed two tickets' detekt
    debt on 2026-08-02 (contract CHECK-BASELINE rules 4-6). The S3438 audit carried both lint XML
    baselines as a dated exception until this gate existed.

    Set semantics, never a count: a re-freeze can prune dead entries and absorb live ones in the same
    pass, so the file can shrink while accepting new debt.

      identifier in the XML, absent from the snapshot -> ABSORBED -> FAIL.
      identifier in the snapshot, absent from the XML -> pruned   -> PASS.

    Identifier: `<issue id>|<first location file>|<digest>`, digest = first 12 hex chars of SHA-256
    over `message` + LF + trimmed `errorLine1`. Line and column stay out on purpose - they move on
    every unrelated edit above the finding and would make each such edit read as an absorption.
    A repeated identifier gets `#2`, `#3`.. in document order, so a second copy of the same finding
    in the same file is a new identifier rather than a set duplicate.

    The snapshot (<module>/lint-baseline.ids) is committed, sorted, one identifier per line; accepting
    a deliberate re-freeze is `-Update -Reason '<why>'`, which prints every newly accepted identifier.

    Exit codes (S1070 contract):
      0  PASS - no absorbed identifier, or -Update completed, or absorption found without -Gate.
      1  FAIL - -Gate and the baseline absorbed at least one identifier missing from the snapshot.
      2  Cannot verify - a baseline or snapshot is missing or unparseable, -Update without -Reason, or
         a path override without -Module. A missing snapshot is this case on purpose: seed it once.
      4  the target's code domain is held by another session, so -Update wrote nothing. The queue
         place is held - wait for the turn in the background and rerun (S2635).

.PARAMETER Module
    app_v2 or wear. Omit to check both.

.PARAMETER Gate
    Exit 1 when absorption is found. Without it the finding is reported and the exit stays 0.

.PARAMETER Update
    Re-seed the snapshot(s) from the current baseline(s) and exit 0. Requires -Reason.

.PARAMETER Reason
    Why the re-freeze is intentional. Recorded in the snapshot header and echoed for the dev-log row.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-lint-baseline-absorption.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-lint-baseline-absorption.ps1 -Module wear -Update -Reason 'S3500 fixed three findings and re-froze'
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    [switch]$Gate,
    [switch]$Update,
    [string]$Reason,
    # A truncated list still states how many were dropped - a silent cap would read as "that was all".
    [int]$MaxListed = 50,
    # Single-module path overrides; they let the gate run against a fixture instead of the 30k-line XML.
    [string]$BaselineFile,
    [string]$SnapshotFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gateName = 'assert-lint-baseline-absorption'

function Resolve-RepoPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return (Join-Path $repoRoot $Path)
}

function Get-Digest {
    param([string]$Text)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $hash = [System.Security.Cryptography.SHA256]::HashData($bytes)
    return ([System.Convert]::ToHexString($hash).Substring(0, 12).ToLowerInvariant())
}

# Returns $null when the file cannot be trusted, so every caller fails closed instead of comparing
# against a half-read set.
function Read-BaselineIds {
    param([string]$Path)
    try {
        [xml]$doc = Get-Content -LiteralPath $Path -Raw
    }
    catch {
        Write-Error "${gateName}: cannot verify - $Path is not well-formed XML: $($_.Exception.Message)" -ErrorAction Continue
        return $null
    }
    # get_LocalName(), not .Name: the pwsh XML adapter resolves .Name to the root's name="AGP .." attribute.
    if ($null -eq $doc.DocumentElement -or $doc.DocumentElement.get_LocalName() -ne 'issues') {
        Write-Error "${gateName}: cannot verify - $Path has no <issues> root element." -ErrorAction Continue
        return $null
    }

    $seen = @{}
    $ids = [System.Collections.Generic.List[string]]::new()
    foreach ($issue in $doc.DocumentElement.SelectNodes('issue')) {
        $issueId = $issue.GetAttribute('id')
        $location = $issue.SelectSingleNode('location')
        $file = if ($null -ne $location) { $location.GetAttribute('file') -replace '\\', '/' } else { '' }
        $digestInput = $issue.GetAttribute('message') + "`n" + $issue.GetAttribute('errorLine1').Trim()
        $key = "$issueId|$file|$(Get-Digest $digestInput)"
        $n = if ($seen.ContainsKey($key)) { $seen[$key] + 1 } else { 1 }
        $seen[$key] = $n
        $ids.Add($(if ($n -eq 1) { $key } else { "$key#$n" }))
    }
    return , $ids
}

# Built by hand: the HashSet(IEnumerable) constructor is ambiguous on an empty collection in pwsh.
function New-StringSet {
    param([object]$Items)
    $set = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in $Items) { [void]$set.Add([string]$item) }
    return , $set
}

function Read-SnapshotIds {
    param([string]$Path)
    $ids = [System.Collections.Generic.List[string]]::new()
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $ids.Add($line)
    }
    return , $ids
}

function Write-IdList {
    param([string[]]$Ids, [string]$Prefix, [string]$Color)
    foreach ($id in ($Ids | Select-Object -First $MaxListed)) { Write-Host "$Prefix$id" }
    if ($Ids.Count -gt $MaxListed) {
        Write-Host "$Prefix.. and $($Ids.Count - $MaxListed) more (raise -MaxListed to see them all)" -ForegroundColor $Color
    }
}

if ($Update -and -not $Reason) {
    Write-Error ("${gateName}: -Update requires -Reason. Re-freezing accepts every live lint finding as " +
        'permanent; an unexplained snapshot rewrite is the event this gate exists to prevent.') -ErrorAction Continue
    exit 2
}
if (($BaselineFile -or $SnapshotFile) -and -not $PSBoundParameters.ContainsKey('Module')) {
    Write-Error "${gateName}: -BaselineFile/-SnapshotFile need an explicit -Module." -ErrorAction Continue
    exit 2
}

$modules = if ($PSBoundParameters.ContainsKey('Module')) { @($Module) } else { @('app_v2', 'wear') }
$anyAbsorbed = $false
$cannotVerify = $false

foreach ($m in $modules) {
    $baselinePath = if ($BaselineFile) { Resolve-RepoPath $BaselineFile } else { Resolve-RepoPath "$m/lint-baseline.xml" }
    $snapshotPath = if ($SnapshotFile) { Resolve-RepoPath $SnapshotFile } else { Resolve-RepoPath "$m/lint-baseline.ids" }

    if (-not (Test-Path -LiteralPath $baselinePath)) {
        Write-Error "${gateName}: cannot verify - baseline not found: $baselinePath" -ErrorAction Continue
        $cannotVerify = $true
        continue
    }
    $baselineIds = Read-BaselineIds -Path $baselinePath
    if ($null -eq $baselineIds) { $cannotVerify = $true; continue }

    if ($Update) {
        $hadSnapshot = Test-Path -LiteralPath $snapshotPath
        $previousSet = New-StringSet -Items $(if ($hadSnapshot) { Read-SnapshotIds -Path $snapshotPath } else { @() })
        # The first seed lists nothing: every entry is "new", and thousands of lines bury the point.
        $absorbedNow = @(if ($hadSnapshot) { $baselineIds | Where-Object { -not $previousSet.Contains($_) } })
        $header = @(
            "# Android lint baseline identifier snapshot for $m - S3459.",
            '# Generated by scripts/quality/assert-lint-baseline-absorption.ps1 -Update.',
            '# One identifier per line, sorted: <issue id>|<file>|<digest of message + errorLine1>[#n].',
            '# Never hand-edit: re-seed through the script so the reason is recorded and the accepted',
            '# identifiers are printed for the dev-log row.',
            "# Reason: $Reason"
        )
        $sorted = [string[]]$baselineIds.ToArray()
        [System.Array]::Sort($sorted, [System.StringComparer]::Ordinal)
        $codeScope = $null
        try {
            $codeScope = Enter-CodeLockOrExit -Path $snapshotPath -Reason "$gateName.ps1 -Update ($m snapshot)"
            Set-Content -LiteralPath $snapshotPath -Value ($header + $sorted) -Encoding utf8NoBOM
        }
        finally { Exit-CodeLockScope -Scope $codeScope }

        $seedWord = if ($hadSnapshot) { 're-seeded' } else { 'seeded (initial)' }
        Write-Host "${gateName}: snapshot $seedWord for $m - $($sorted.Count) identifier(s)." -ForegroundColor Green
        if ($absorbedNow.Count -gt 0) {
            Write-Host "  newly accepted lint findings ($($absorbedNow.Count)) - name these in the dev-log row:" -ForegroundColor Yellow
            Write-IdList -Ids $absorbedNow -Prefix '    + ' -Color Yellow
        }
        continue
    }

    if (-not (Test-Path -LiteralPath $snapshotPath)) {
        Write-Error ("${gateName}: cannot verify - snapshot not found: $snapshotPath. Seed it once with " +
            "-Module $m -Update -Reason '<why>'.") -ErrorAction Continue
        $cannotVerify = $true
        continue
    }

    $snapshotIds = Read-SnapshotIds -Path $snapshotPath
    $snapshotSet = New-StringSet -Items $snapshotIds
    $baselineSet = New-StringSet -Items $baselineIds
    $absorbed = @($baselineIds | Where-Object { -not $snapshotSet.Contains($_) })
    $prunedCount = @($snapshotIds | Where-Object { -not $baselineSet.Contains($_) }).Count
    $counts = "baseline $($baselineIds.Count), snapshot $($snapshotIds.Count), pruned $prunedCount"

    if ($absorbed.Count -eq 0) {
        Write-Host "${gateName}: PASS [$m] - no absorbed finding ($counts)." -ForegroundColor Green
        continue
    }
    $anyAbsorbed = $true
    Write-Host "${gateName}: ABSORBED findings in [$m] ($($absorbed.Count)):" -ForegroundColor Red
    Write-IdList -Ids $absorbed -Prefix '  + ' -Color Red
    Write-Host "  $counts - the baseline can shrink while absorbing, so the gate compares sets." -ForegroundColor Yellow
}

if ($cannotVerify) { exit 2 }
if ($Update) { exit 0 }

if ($anyAbsorbed) {
    Write-Host ''
    Write-Host 'Fix the finding rather than freezing it. If the re-freeze is intentional, accept it:' -ForegroundColor Yellow
    Write-Host "-Update -Reason '<why>', then close through post-change.ps1 so the accepted findings" -ForegroundColor Yellow
    Write-Host 'have a dev-log row naming them.' -ForegroundColor Yellow
    if ($Gate) { exit 1 }
}
exit 0
