# Run-Tests.ps1 (S2656) - regression suite for WHICH checkers Assert-ClosingGates runs on a
# re-affirmation, i.e. a status written again over itself.
#
# Regression origin: the function's early return skipped every checker whenever
# $OldStatus -eq $NewStatus. For most of them that is right - they ask what the ticket achieved,
# which was answered at the entry transition. For check-probe-present.ps1 it is wrong: its subject
# is the source tree, which changes under a standing BlockNeedUserTest ticket by other hands (a
# release sweep, remove-ticket-probes.ps1 before S2639, a probe deleted with the code it sat in), so
# its answer goes stale with no status transition at all. Measured 2026-09-06: three tickets - S2156,
# S2487, S2498 - had stood in the status with no probe, and only the project-wide `.\a.ps1 fg` run
# noticed, red for whichever session happened to run it over debt it could not fix.
#
# Why the function can be called directly: Assert-ClosingGates reads state and runs checkers, and
# writes nothing. No catalog mutation, no sandbox, no restore - unlike update.tests, which drives the
# mutator and has to put the subject's note back in a finally block.
#
# Why an id no record carries is the probe: check-probe-present.ps1 exits 2 on it ("this ticket does
# not exist" is a bad invocation, not a missing probe), and Assert-ClosingGates fails the transition
# on any non-zero exit. So a throw proves the checker RAN, and silence proves it did not - the
# distinction this suite exists to pin - without depending on any live ticket being in a given shape.
#
# The mechanism lives in the canon harness (S2402) and arrives here by a plugin deploy no project
# session performs. While the RESOLVED harness predates the fix, the two cases that need it are
# skipped by name rather than failed, or every sibling session goes red over a deploy it cannot run
# (the rule S2577/S2578 established). Run with $env:SZA_HARNESS_ROOT pointed at the canon checkout to
# see them execute before the deploy.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/assert-closing-gates.tests/Run-Tests.ps1
#
# Exit codes:
#   0   every case passed (skips are not failures).
#   1   at least one case failed.
#   2   could not look - the harness _lib.ps1 did not resolve, or the catalog is unreadable.

[CmdletBinding(PositionalBinding = $false)]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path

$script:pass = 0
$script:fail = 0
$script:skip = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Skip-Case([string]$name, [string]$why) {
    Write-Host "  SKIP  $name - $why" -ForegroundColor Yellow
    $script:skip++
}

# Returns 'threw' when a checker refused the transition, 'silent' when no checker did.
function Get-GateOutcome([string]$oldStatus, [string]$newStatus, [string]$id) {
    try {
        Assert-ClosingGates -Id $id -OldStatus $oldStatus -NewStatus $newStatus 2>&1 | Out-Null
        return 'silent'
    } catch {
        return 'threw'
    }
}

. (Join-Path $repoRoot 'scripts/spec_catalog/_lib.ps1')

# Read the file the forwarder will actually run. Get-Command on a function is no use here: the fix
# is a branch inside an existing function, not a new symbol, so only the text answers.
$libPath = (Get-SzaHarnessScript 'spec_catalog/_lib.ps1')
if (-not $libPath -or -not (Test-Path -LiteralPath $libPath)) {
    Write-Host "Cannot resolve spec_catalog/_lib.ps1 in the harness." -ForegroundColor Red
    exit 2
}
Write-Host "resolved harness _lib: $libPath" -ForegroundColor DarkGray
$fixPresent = (Select-String -LiteralPath $libPath -Pattern 'S2656' -SimpleMatch -Quiet) -eq $true

# 'S9999' is reserved as the never-a-record id across these suites (update.tests uses it too).
$absentId = 'S9999'

Write-Host "A: a BlockNeedUserTest re-affirmation runs the probe gate" -ForegroundColor Yellow
if (-not $fixPresent) {
    Skip-Case 'A1' 'S2656 not in the resolved harness _lib.ps1 - the canon change is not deployed yet'
} else {
    $outcomeA = Get-GateOutcome 'BlockNeedUserTest' 'BlockNeedUserTest' $absentId
    Assert-That 'A1 re-affirmation reaches check-probe-present.ps1' ($outcomeA -eq 'threw') "outcome=$outcomeA"
}

Write-Host "B: a re-affirmation of any other status still runs nothing" -ForegroundColor Yellow
foreach ($status in @('Verified', 'Implemented', 'In Progress', 'BlockExternal')) {
    $outcome = Get-GateOutcome $status $status $absentId
    Assert-That "B1 [$status -> $status] silent" ($outcome -eq 'silent') "outcome=$outcome"
}

Write-Host "C: the entry transition is unchanged" -ForegroundColor Yellow
$outcomeC1 = Get-GateOutcome 'Approved' 'BlockNeedUserTest' $absentId
Assert-That 'C1 entry into BlockNeedUserTest still gated' ($outcomeC1 -eq 'threw') "outcome=$outcomeC1"
$outcomeC2 = Get-GateOutcome 'In Progress' 'Approved' $absentId
Assert-That 'C2 entry into an ungated status still runs nothing' ($outcomeC2 -eq 'silent') "outcome=$outcomeC2"

Write-Host "D: a re-affirmation of a ticket that HAS a probe passes" -ForegroundColor Yellow
$probeLib = (Get-SzaHarnessScript 'spec_catalog/lib/blockneedusertest-probes.ps1')
$subject = $null
if ($probeLib -and (Test-Path -LiteralPath $probeLib)) {
    . $probeLib
    $roots = @(Get-ProbeSourceRoot -RepoRoot $repoRoot)
    if ($roots.Count -gt 0) {
        $parked = @(Get-Content -LiteralPath (Get-SzaPath 'journal') |
            ForEach-Object { $_ | ConvertFrom-Json } |
            Where-Object { $_.status -eq 'BlockNeedUserTest' } |
            ForEach-Object { $_.id })
        # Bounded: each miss costs a full source scan, and the case only needs one live example.
        foreach ($id in ($parked | Select-Object -First 8)) {
            if ((Test-TicketProbeInSource -Id $id -SourceRoots $roots).Found) { $subject = $id; break }
        }
    }
}
if (-not $fixPresent) {
    Skip-Case 'D1' 'S2656 not in the resolved harness _lib.ps1 - the canon change is not deployed yet'
} elseif (-not $subject) {
    # A tree state, not a defect: with no parked ticket carrying a probe there is no fixture, and
    # passing the case silently would certify a path this run never walked.
    Skip-Case 'D1' 'no BlockNeedUserTest ticket with a probe in the first 8 - no fixture for the positive case'
} else {
    $outcomeD = Get-GateOutcome 'BlockNeedUserTest' 'BlockNeedUserTest' $subject
    Assert-That "D1 [$subject] re-affirmation accepted" ($outcomeD -eq 'silent') "outcome=$outcomeD"
}

Write-Host ""
$summary = "assert-closing-gates tests: $script:pass passed, $script:skip skipped"
if ($script:fail -eq 0) {
    Write-Host $summary -ForegroundColor Green
    exit 0
}
Write-Host "$summary, $script:fail FAILED" -ForegroundColor Red
exit 1
