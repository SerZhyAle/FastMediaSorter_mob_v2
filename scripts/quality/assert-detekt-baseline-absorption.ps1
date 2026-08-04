#requires -Version 7.0
<#
.SYNOPSIS
    S1356 gate: refuse a detekt-baseline that ABSORBED a finding absent from the committed ID
    snapshot. Mirror of audit-detekt-baseline-drift.ps1 (S1334), which catches the opposite - entries
    that went dead.

.DESCRIPTION
    A whole-project `detektBaseline` re-freeze turns every live finding into an accepted one in a
    single call. On 2026-08-02 that silently absorbed the debt two open tickets were written about
    (S1198 TooManyFunctions:StreamsActivity, S1328 LongParameterList:StreamInlineAudioManager), and
    left no dev-log row. This gate makes the same event loud.

    Set semantics, deliberately NOT a count ratchet. In that incident the baseline SHRANK - 12656
    lines before, 12286 after - because the re-freeze pruned dead entries and absorbed live ones in
    the same pass. A gate that refuses "more baseline entries than last time" would have passed this
    very incident with a green verdict. So the comparison is over the ID SET:

      ID in baseline, absent from the snapshot  -> ABSORBED -> FAIL. New debt was accepted.
      ID in snapshot, absent from the baseline  -> pruned   -> PASS. That is S1334's subject, not this
                                                              gate's; pruning debt is always welcome.

    The snapshot lives next to the baseline it describes (config/detekt/baseline-<module>.ids), one
    raw <ID> per line, sorted. It is committed, so the accepted set is reviewable in a diff instead of
    being buried inside a 2 MB XML whose lines move whenever anything is re-frozen.

    Accepting a deliberate re-freeze is an explicit act: run with -Update -Reason '<why>'. That
    rewrites the snapshot, prints every absorbed ID, and tells you to journal the change through
    post-change.ps1 - which is the record whose absence made the original incident unattributable.

    Exit codes (S1070 contract):
      0  PASS - no absorbed ID, or -Update completed, or absorption found without -Gate.
      1  FAIL - -Gate and the baseline absorbed at least one ID missing from the snapshot.
      2  Cannot verify - a baseline or snapshot file is missing or unparseable. Never a PASS:
         "could not check" is a different fact from "checked and found nothing". A missing snapshot
         is this case on purpose - seed it once with -Update, do not let an unseeded gate read green.

.PARAMETER Module
    app_v2 or wear. Omit to check both.

.PARAMETER Gate
    Exit 1 when absorption is found. Without it the finding is reported and the exit stays 0.

.PARAMETER Update
    Re-seed the snapshot(s) from the current baseline(s) and exit 0. Requires -Reason.

.PARAMETER Reason
    Why the re-freeze is intentional. Recorded in the snapshot header and echoed for the dev-log row.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-detekt-baseline-absorption.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-detekt-baseline-absorption.ps1 -Module app_v2 -Update -Reason 'S1400 refactor re-froze the file it rewrote'
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    [switch]$Gate,
    [switch]$Update,
    [string]$Reason,
    # Ceiling on the per-ID listing. A truncated list still states how many were dropped - a silent
    # cap would read as "that was all of them".
    [int]$MaxListed = 50,
    # Path overrides, single-module only. Mirrors audit-detekt-baseline-drift.ps1 and is what lets the
    # gate be exercised against a fixture instead of the committed 2 MB baseline.
    [string]$BaselineFile,
    [string]$SnapshotFile,
    [string]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Resolve-RepoPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return (Join-Path $repoRoot $Path)
}

# Reads the raw <ID> payloads out of a detekt baseline. Returns $null when the file cannot be trusted
# so every caller fails closed rather than comparing against a half-read set.
function Read-BaselineIds {
    param([string]$Path)

    $idLineRegex = [regex]'<ID>(?<id>.*?)</ID>'
    $ids = [System.Collections.Generic.List[string]]::new()
    $rawOpenTags = 0
    foreach ($line in Get-Content -LiteralPath $Path) {
        $rawOpenTags += ([regex]::Matches($line, '<ID>')).Count
        foreach ($m in $idLineRegex.Matches($line)) {
            $ids.Add($m.Groups['id'].Value)
        }
    }
    # detekt writes one <ID> per line. A mismatch means an entry wraps, and a wrapped entry would be
    # read as absent - which this gate would report as a prune (harmless) while hiding a real
    # absorption. Refuse to judge instead.
    if ($rawOpenTags -ne $ids.Count) {
        Write-Error ("assert-detekt-baseline-absorption: cannot verify - $Path has $rawOpenTags <ID> " +
            "tag(s) but $($ids.Count) parsed on single lines. An entry spans lines; refusing to judge " +
            'a partially-read baseline.') -ErrorAction Continue
        return $null
    }
    return , $ids
}

# Built by hand rather than through the HashSet(IEnumerable) constructor: an empty collection makes
# PowerShell report "multiple ambiguous overloads" because null binds to both the IEnumerable and the
# IEqualityComparer overload, and the empty case is the normal one on a first -Update run.
function New-StringSet {
    param([object]$Items)
    $set = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($item in $Items) { [void]$set.Add([string]$item) }
    # Comma operator: PowerShell unrolls an enumerable on output, so a bare `return $set` emits the
    # ELEMENTS - and an empty set emits nothing at all, handing the caller $null.
    return , $set
}

function Read-SnapshotIds {
    param([string]$Path)
    $ids = [System.Collections.Generic.List[string]]::new()
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line.StartsWith('#')) { continue }
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $ids.Add($line)
    }
    return , $ids
}

# A baseline ID is Rule:File$Class$snippet (or Rule:File$snippet). The snippet is what makes the
# entry 2 KB long; for a human-facing verdict the rule and file are the whole story.
function Get-IdLabel {
    param([string]$Raw)
    $colonIdx = $Raw.IndexOf(':')
    if ($colonIdx -lt 0) { return $Raw }
    $rule = $Raw.Substring(0, $colonIdx)
    $afterRule = $Raw.Substring($colonIdx + 1)
    $dollarIdx = $afterRule.IndexOf('$')
    $file = if ($dollarIdx -lt 0) { $afterRule } else { $afterRule.Substring(0, $dollarIdx) }
    return "$rule in $file"
}

$modules = if ($PSBoundParameters.ContainsKey('Module')) { @($Module) } else { @('app_v2', 'wear') }

if ($Update -and -not $Reason) {
    $why = 'assert-detekt-baseline-absorption: -Update requires -Reason. Re-freezing accepts ' +
    "another ticket's debt as permanent; an unexplained snapshot rewrite is the exact event this " +
    'gate exists to prevent.'
    Write-Error $why -ErrorAction Continue
    exit 2
}

$anyAbsorbed = $false
$cannotVerify = $false
$report = @()

if (($BaselineFile -or $SnapshotFile) -and -not $PSBoundParameters.ContainsKey('Module')) {
    $why = 'assert-detekt-baseline-absorption: -BaselineFile/-SnapshotFile need an explicit -Module - ' +
    'a path override cannot describe both modules at once.'
    Write-Error $why -ErrorAction Continue
    exit 2
}

foreach ($m in $modules) {
    $baselinePath = if ($BaselineFile) { Resolve-RepoPath $BaselineFile } else { Resolve-RepoPath "config/detekt/baseline-$m.xml" }
    $snapshotPath = if ($SnapshotFile) { Resolve-RepoPath $SnapshotFile } else { Resolve-RepoPath "config/detekt/baseline-$m.ids" }

    if (-not (Test-Path -LiteralPath $baselinePath)) {
        Write-Error "assert-detekt-baseline-absorption: cannot verify - baseline not found: $baselinePath" -ErrorAction Continue
        $cannotVerify = $true
        continue
    }

    $baselineIds = Read-BaselineIds -Path $baselinePath
    if ($null -eq $baselineIds) { $cannotVerify = $true; continue }

    if ($Update) {
        $hadSnapshot = Test-Path -LiteralPath $snapshotPath
        $previous = if ($hadSnapshot) { Read-SnapshotIds -Path $snapshotPath } else { @() }
        $previousSet = New-StringSet -Items $previous
        # On the very first seed every entry is "new", so listing them is 12k lines of noise that
        # buries nothing. Only a re-seed over an existing snapshot has a meaningful delta to name.
        # S1191 trap: the @(..) MUST wrap the whole if-expression. An empty array returned from an
        # if-expression collapses to $null, and $null.Count throws under Set-StrictMode -Version Latest -
        # which is the normal path here, because the initial seed has no delta to report.
        $absorbedNow = @(
            if ($hadSnapshot) {
                $baselineIds | Where-Object { -not $previousSet.Contains($_) }
            }
        )

        $header = @(
            "# detekt baseline ID snapshot for $m - S1356.",
            '# Generated by scripts/quality/assert-detekt-baseline-absorption.ps1 -Update.',
            '# One raw <ID> per line, sorted. Never hand-edit: re-seed through the script so the',
            '# reason below is recorded and the absorbed entries are printed for the dev-log row.',
            "# Reason: $Reason"
        )
        $sorted = @($baselineIds | Sort-Object)
        Set-Content -LiteralPath $snapshotPath -Value ($header + $sorted) -Encoding utf8NoBOM

        $seedWord = if ($hadSnapshot) { 're-seeded' } else { 'seeded (initial)' }
        Write-Host "assert-detekt-baseline-absorption: snapshot $seedWord for $m - $($sorted.Count) ID(s)." -ForegroundColor Green
        if ($absorbedNow.Count -gt 0) {
            Write-Host "  newly accepted debt ($($absorbedNow.Count)) - name these in the dev-log row:" -ForegroundColor Yellow
            foreach ($id in ($absorbedNow | Select-Object -First $MaxListed)) { Write-Host "    + $(Get-IdLabel $id)" }
            if ($absorbedNow.Count -gt $MaxListed) {
                Write-Host "    .. and $($absorbedNow.Count - $MaxListed) more (raise -MaxListed to see them all)" -ForegroundColor Yellow
            }
        }
        continue
    }

    if (-not (Test-Path -LiteralPath $snapshotPath)) {
        Write-Error ("assert-detekt-baseline-absorption: cannot verify - snapshot not found: $snapshotPath. " +
            "Seed it once with -Module $m -Update -Reason '<why>'. Refusing to report PASS against a " +
            'snapshot that does not exist.') -ErrorAction Continue
        $cannotVerify = $true
        continue
    }

    $snapshotIds = Read-SnapshotIds -Path $snapshotPath
    $snapshotSet = New-StringSet -Items $snapshotIds
    $baselineSet = New-StringSet -Items $baselineIds

    $absorbed = @($baselineIds | Where-Object { -not $snapshotSet.Contains($_) })
    $pruned = @($snapshotIds | Where-Object { -not $baselineSet.Contains($_) })

    $report += [pscustomobject]@{
        Module        = $m
        BaselineCount = $baselineIds.Count
        SnapshotCount = $snapshotIds.Count
        Absorbed      = @($absorbed | ForEach-Object { Get-IdLabel $_ })
        PrunedCount   = $pruned.Count
    }

    if ($absorbed.Count -eq 0) {
        Write-Host ("assert-detekt-baseline-absorption: PASS [$m] - no absorbed finding " +
            "(baseline $($baselineIds.Count), snapshot $($snapshotIds.Count), pruned $($pruned.Count)).") -ForegroundColor Green
        continue
    }

    $anyAbsorbed = $true
    Write-Host "assert-detekt-baseline-absorption: ABSORBED findings in [$m] ($($absorbed.Count)):" -ForegroundColor Red
    foreach ($id in ($absorbed | Select-Object -First $MaxListed)) { Write-Host "  + $(Get-IdLabel $id)" }
    if ($absorbed.Count -gt $MaxListed) {
        Write-Host "  .. and $($absorbed.Count - $MaxListed) more (raise -MaxListed to see them all)" -ForegroundColor Red
    }
    Write-Host ("  baseline $($baselineIds.Count) | snapshot $($snapshotIds.Count) | pruned $($pruned.Count) - " +
        'note the baseline can SHRINK while absorbing, which is why this gate compares sets, not counts.') -ForegroundColor Yellow
}

if ($Json) {
    $jsonPath = Resolve-RepoPath $Json
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM
    Write-Host "Written: $jsonPath"
}

if ($cannotVerify) { exit 2 }
if ($Update) { exit 0 }

if ($anyAbsorbed) {
    Write-Host ''
    Write-Host 'Fix the finding rather than freezing it. If the re-freeze is genuinely intentional,' -ForegroundColor Yellow
    Write-Host "accept it explicitly: -Update -Reason '<why>', then close through post-change.ps1 so" -ForegroundColor Yellow
    Write-Host 'the accepted debt has a dev-log row naming it.' -ForegroundColor Yellow
    if ($Gate) { exit 1 }
}

exit 0
