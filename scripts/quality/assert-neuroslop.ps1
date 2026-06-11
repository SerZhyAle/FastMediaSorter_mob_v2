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

    Ratchet contract: baselines only go DOWN via each child's -UpdateBaseline;
    raising a baseline is forbidden without an offsetting refactor. Cleanup of the
    catch and layout-color dimensions is still in progress (S0383 Phases 03/04),
    so their baselines are the current floors, not the final targets - they will
    ratchet further as those phases complete. This harness is shared in spirit
    with S0381 (the sibling neuroslop-hygiene-hardening ticket); extend here
    rather than duplicating a parallel runner.

    Each child is run as a SEPARATE process so its `exit` cannot terminate this
    orchestrator (a dot-sourced `exit` would kill the host).

    Modes:
      (default)  Report each child's baseline-vs-actual line. Exit 0.
      -Gate      Run each child with -Gate; exit 1 if ANY child fails.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1
    pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    'pwsh'
}

$children = @(
    'assert-trivial-comments.ps1',
    'assert-empty-catch.ps1',
    'assert-layout-hardcoded-colors.ps1',
    'assert-unsafe-collect.ps1',
    'assert-globalscope.ps1',
    'assert-nontimber-log.ps1',
    'assert-stub-todo.ps1'
)

$failures = 0
foreach ($child in $children) {
    $path = Join-Path $PSScriptRoot $child
    if (-not (Test-Path $path)) {
        Write-Host "neuroslop: MISSING child $child" -ForegroundColor Red
        $failures++
        continue
    }
    if ($Gate) {
        & $pwshExe -NoProfile -File $path -Gate | Write-Host
        if ($LASTEXITCODE -ne 0) { $failures++ }
    }
    else {
        & $pwshExe -NoProfile -File $path | Write-Host
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
