#requires -Version 7.0
<#
.SYNOPSIS
    S0383 umbrella gate: run all neuroslop ratchet detectors and aggregate.

.DESCRIPTION
    Thin orchestrator over the per-dimension detectors (no detection logic of its
    own). Each child enforces its own committed baseline that may only ratchet
    DOWN; this umbrella is what `post-change.ps1` calls so any Kotlin/Xml/Mixed
    change is checked in one step.

    Children:
      - assert-trivial-comments.ps1        (trivial verb-noun comments)
      - assert-empty-catch.ps1             (swallowing catch blocks)
      - assert-layout-hardcoded-colors.ps1 (hardcoded hex in layout XML)
      - assert-unsafe-collect.ps1          (non-lifecycle-aware Flow collects)
      - assert-globalscope.ps1             (Tier 1: GlobalScope coroutine usage)
      - assert-nontimber-log.ps1           (Tier 1: Log.*/println/System.out)
      - assert-stub-todo.ps1               (Tier 1: TODO()/NotImplementedError stubs)
      - assert-em-dash.ps1                 (em-dash/en-dash in *.kt -> use hyphen)
      - assert-non-null-assertion.ps1      (S1032: `!!` non-null assertions in *.kt)

    Ratchet contract: baselines only go DOWN via each child's -UpdateBaseline;
    raising a baseline is forbidden without an offsetting refactor. Cleanup of the
    catch and layout-color dimensions is still in progress (S0383 Phases 03/04),
    so their baselines are the current floors, not the final targets - they will
    ratchet further as those phases complete. This harness is shared in spirit
    with S0381 (the sibling neuroslop-hygiene-hardening ticket); extend here
    rather than duplicating a parallel runner.

    Each child runs IN-PROCESS via the call operator (`& file.ps1`), which isolates
    the child's `exit` (it sets $LASTEXITCODE and returns to the caller - only a
    DOT-sourced `. file.ps1` or a function-scoped `exit` would kill the host). This
    removes ~8 pwsh cold-starts per run (S0848) while keeping the exit-isolation
    guarantee and a 1:1 detector set / verdict with the previous fork version.

    Modes:
      (default)  Report each child's baseline-vs-actual line. Exit 0.
      -Gate      Run each child with -Gate; exit 1 if ANY child fails.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1
    pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    # S0850: forwarded to every child in gate mode - each judges only the growth the changed
    # files introduce (working vs HEAD) instead of a full scan. Report mode stays full-scan.
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$children = @(
    'assert-trivial-comments.ps1',
    'assert-empty-catch.ps1',
    'assert-layout-hardcoded-colors.ps1',
    'assert-unsafe-collect.ps1',
    'assert-globalscope.ps1',
    'assert-nontimber-log.ps1',
    'assert-stub-todo.ps1',
    'assert-em-dash.ps1',
    'assert-non-null-assertion.ps1'
)

$failures = 0
foreach ($child in $children) {
    $path = Join-Path $PSScriptRoot $child
    if (-not (Test-Path $path)) {
        Write-Host "neuroslop: MISSING child $child" -ForegroundColor Red
        $failures++
        continue
    }
    # S0848 Phase 03: in-process call (no pwsh fork). `& $path` isolates the child's `exit`.
    # A child that raises a terminating error (ErrorActionPreference=Stop) is caught and, in
    # gate mode, counted as a failure - fail-closed, matching the fork's non-zero-exit outcome.
    try {
        $global:LASTEXITCODE = 0
        if ($Gate) {
            # S0850: delta mode per child when a changed-files signal is present.
            if ($ChangedFiles) { & $path -Gate -ChangedFiles $ChangedFiles }
            else { & $path -Gate }
            if ($LASTEXITCODE -ne 0) { $failures++ }
        }
        else {
            & $path
        }
    }
    catch {
        Write-Host "neuroslop: child $child errored: $($_.Exception.Message)" -ForegroundColor Red
        if ($Gate) { $failures++ }
    }
}

if ($Gate) {
    if ($failures -gt 0) {
        Write-Host "assert-neuroslop: FAIL ($failures dimension(s) above baseline)." -ForegroundColor Red
        exit 1
    }
    Write-Host "assert-neuroslop: PASS (all dimensions at or below baseline)." -ForegroundColor Green
}
exit 0
