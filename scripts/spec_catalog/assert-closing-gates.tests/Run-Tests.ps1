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
# Pre-declared because case E reads both, and under StrictMode a variable only ever assigned inside
# a branch that did not run is an error rather than an empty list.
$roots = @()
$parked = @()
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

Write-Host "E: the EXIT from BlockNeedUserTest is gated (S2934)" -ForegroundColor Yellow
# The direction the function never judged: it read $NewStatus only, so BlockNeedUserTest ->
# In Progress left through the early return with no checker reached, and the probes stayed in
# source owned by nobody. Cases below use live tickets rather than fixtures for the same reason
# case D does - the subject of check-probe-absent.ps1 is the source tree, and a fixture tree would
# certify a path no session walks.
$exitFixPresent = (Select-String -LiteralPath $libPath -Pattern 'S2934' -SimpleMatch -Quiet) -eq $true

# A parked ticket excused in the baseline: it has no executable path to instrument, so it carries
# no probe and is the natural negative fixture - nothing to leave behind, nothing to refuse.
$excusedSubject = $null
if ($probeLib -and (Test-Path -LiteralPath $probeLib) -and $roots.Count -gt 0) {
    $excusedIds = Get-ExcusedProbeTickets -BaselinePath (Get-ProbeBaselinePath -RepoRoot $repoRoot)
    foreach ($id in ($parked | Where-Object { $excusedIds.Contains($_) } | Select-Object -First 4)) {
        if (-not (Test-TicketProbeInSource -Id $id -SourceRoots $roots).Found) { $excusedSubject = $id; break }
    }
}

if (-not $exitFixPresent) {
    Skip-Case 'E1' 'S2934 not in the resolved harness _lib.ps1 - the canon change is not deployed yet'
    Skip-Case 'E2' 'S2934 not in the resolved harness _lib.ps1 - the canon change is not deployed yet'
    Skip-Case 'E3' 'S2934 not in the resolved harness _lib.ps1 - the canon change is not deployed yet'
} else {
    if (-not $subject) {
        Skip-Case 'E1' 'no BlockNeedUserTest ticket with a probe in the first 8 - no fixture for the refusal'
    } else {
        $outcomeE1 = Get-GateOutcome 'BlockNeedUserTest' 'In Progress' $subject
        Assert-That "E1 [$subject] leaving with its probe still in source is refused" ($outcomeE1 -eq 'threw') "outcome=$outcomeE1"
    }

    if (-not $excusedSubject) {
        Skip-Case 'E2' 'no excused parked ticket without a probe - no fixture for the clean exit'
    } else {
        $outcomeE2 = Get-GateOutcome 'BlockNeedUserTest' 'In Progress' $excusedSubject
        Assert-That "E2 [$excusedSubject] leaving with nothing in source is accepted" ($outcomeE2 -eq 'silent') "outcome=$outcomeE2"
    }

    # The branch must not fire on an exit from anything else. S9999 names no record, so the exit
    # checker would exit 2 and throw if it ran - silence is the proof it did not.
    $outcomeE3 = Get-GateOutcome 'Implemented' 'In Progress' $absentId
    Assert-That 'E3 an exit from another status runs nothing' ($outcomeE3 -eq 'silent') "outcome=$outcomeE3"
}

Write-Host "F: a closing transition relocates small temp/ evidence before the gate (S3515)" -ForegroundColor Yellow
# Hermetic: a throwaway project root (SZA_PROJECT_ROOT) holding one catalog record and its spec, so
# the repair that REWRITES a spec never touches a live one. The relocator and the gate run in a child
# process each, exactly as Assert-ClosingGates runs them.
# Beside the resolved _lib.ps1, not through Get-SzaHarnessScript: that throws on a missing script,
# and a missing script is the undeployed state this case must SKIP on.
$relocator = Join-Path (Split-Path -Parent $libPath) 'relocate-temp-evidence.ps1'
$relocatorWired = (Select-String -LiteralPath $libPath -Pattern 'relocate-temp-evidence.ps1' -SimpleMatch -Quiet) -eq $true
if (-not $relocator -or -not (Test-Path -LiteralPath $relocator) -or -not $relocatorWired) {
    Skip-Case 'F1' 'relocate-temp-evidence.ps1 not in the resolved harness - the canon change is not deployed yet'
    Skip-Case 'F2' 'relocate-temp-evidence.ps1 not in the resolved harness - the canon change is not deployed yet'
    Skip-Case 'F3' 'relocate-temp-evidence.ps1 not in the resolved harness - the canon change is not deployed yet'
} else {
    $fx = Join-Path $repoRoot ('temp/scratch/closing-gates-relocate-' + [guid]::NewGuid().ToString('N'))
    $gate = (Get-SzaHarnessScript 'spec_catalog/check-evidence-durable.ps1')
    $pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
    $inheritedRoot = $env:SZA_PROJECT_ROOT
    try {
        New-Item -ItemType Directory -Force -Path (Join-Path $fx 'PLAN'), (Join-Path $fx 'temp/S9001') | Out-Null
        Copy-Item -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Destination $fx
        Set-Content -LiteralPath (Join-Path $fx 'PLAN/spec-catalog.jsonl') -Encoding utf8NoBOM `
            -Value '{"id":"S9001","name":"fixture","file":"PLAN/S9001_fixture.md","status":"In Progress"}'
        Set-Content -LiteralPath (Join-Path $fx 'temp/S9001/small.log') -Value 'verdict: PASS' -Encoding utf8NoBOM
        Set-Content -LiteralPath (Join-Path $fx 'temp/S9001/big.log') -Value ('x' * 70000) -Encoding utf8NoBOM
        $specFile = Join-Path $fx 'PLAN/S9001_fixture.md'
        Set-Content -LiteralPath $specFile -Encoding utf8NoBOM -Value @(
            '# S9001', '', '## 0. Captured', '', 'raw note temp/S9001/small.log', '',
            '## Last Audit', '', '- PASS - log `temp/S9001/small.log`.', '- PASS - big `temp/S9001/big.log`.')
        $env:SZA_PROJECT_ROOT = $fx
        & $pwshExe -NoProfile -File $relocator -Id S9001 *> $null
        $gateFirst = @(& $pwshExe -NoProfile -File $gate -Id S9001 2>&1 | ForEach-Object { [string]$_ })
        $gateFirstExit = $LASTEXITCODE
        $specText = Get-Content -LiteralPath $specFile -Raw
        Assert-That 'F1 a small cited file is copied into attachments/ and the citation rewritten' `
            ((Test-Path -LiteralPath (Join-Path $fx 'PLAN/S9001_fixture/attachments/small.log')) -and
            $specText -match '`PLAN/S9001_fixture/attachments/small\.log`') "spec: $specText"
        Assert-That 'F2 section 0 and an oversized file are left, and the gate still refuses the big one' `
            ($specText -match 'raw note temp/S9001/small\.log' -and $gateFirstExit -eq 1 -and
            ($gateFirst -join "`n") -match 'big\.log' -and ($gateFirst -join "`n") -notmatch 'small\.log') `
            "gate exit $gateFirstExit; $($gateFirst -join ' | ')"
        Set-Content -LiteralPath $specFile -Encoding utf8NoBOM -Value (($specText -split '\r?\n') |
                Where-Object { $_ -notmatch 'big\.log' })
        & $pwshExe -NoProfile -File $relocator -Id S9001 *> $null
        & $pwshExe -NoProfile -File $gate -Id S9001 *> $null
        $gateSecondExit = $LASTEXITCODE
        Assert-That 'F3 a second run reuses the identical attachment and the gate passes' `
            ($gateSecondExit -eq 0 -and @(Get-ChildItem -LiteralPath (Join-Path $fx 'PLAN/S9001_fixture/attachments')).Count -eq 1) `
            "gate exit $gateSecondExit"
    } finally {
        $env:SZA_PROJECT_ROOT = $inheritedRoot
        Remove-Item -LiteralPath $fx -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
$summary = "assert-closing-gates tests: $script:pass passed, $script:skip skipped"
if ($script:fail -eq 0) {
    Write-Host $summary -ForegroundColor Green
    exit 0
}
Write-Host "$summary, $script:fail FAILED" -ForegroundColor Red
exit 1
