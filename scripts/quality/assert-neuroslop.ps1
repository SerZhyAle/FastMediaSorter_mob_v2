#requires -Version 7.0
<#
.SYNOPSIS
    S0383 umbrella gate: run all neuroslop ratchet detectors and aggregate.

.DESCRIPTION
    Thin orchestrator over the per-dimension detectors (no detection logic of its
    own). Each child enforces its own committed baseline that may only ratchet
    DOWN; this umbrella is what `post-change.ps1` calls so any Kotlin/Xml/Mixed
    change is checked in one step.

    Rule set: whatever scripts/quality/lib/source-matchers.ps1 defines, applied
    unfiltered - it is deliberately NOT restated here. Since S1338 this script
    forwards to assert-source-gates.ps1 with no -Only filter, so the set grew past
    the nine dimensions S0383 shipped and a list in this header could only go
    stale. Run the runner with -List to see the live set and its baselines.

    Ratchet contract: baselines only go DOWN via -UpdateBaseline; raising one is
    forbidden without an offsetting refactor. Every baseline is a current floor,
    not a final target. S1543 corrected the previous framing here, which described
    the catch and layout-color cleanup as in progress under "S0383 Phases 03/04" -
    S0383 has been Archived since 2026-06-09. This harness is shared in spirit
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

# S1338: one call, one walk. Every dimension used to be its own script performing its own
# recursive walk of app_v2/src/main and re-reading every file - nine traversals of a tree that
# cannot change between them. assert-source-gates.ps1 applies all nine rules to each file's
# text in a single pass (lib/source-scan.ps1), and the rules themselves live once in
# lib/source-matchers.ps1, so the full scan and the changed-files delta cannot drift apart.
$runner = Join-Path $PSScriptRoot 'assert-source-gates.ps1'
$failures = 0
if (-not (Test-Path $runner)) {
    Write-Host 'neuroslop: MISSING assert-source-gates.ps1 - cannot judge anything.' -ForegroundColor Red
    Write-Error 'assert-neuroslop: the combined source-gate runner is absent; nothing was scanned.' -ErrorAction Continue
    exit 2
}

try {
    $global:LASTEXITCODE = 0
    if ($Gate) {
        if ($ChangedFiles) { & $runner -Gate -ChangedFiles $ChangedFiles }
        else { & $runner -Gate }
        if ($LASTEXITCODE -ne 0) { $failures++ }
    }
    else {
        & $runner
    }
}
catch {
    Write-Host "neuroslop: source-gate runner errored: $($_.Exception.Message)" -ForegroundColor Red
    if ($Gate) { $failures++ }
}

if ($Gate) {
    if ($failures -gt 0) {
        Write-Host "assert-neuroslop: FAIL ($failures dimension(s) above baseline)." -ForegroundColor Red
        exit 1
    }
    Write-Host "assert-neuroslop: PASS (all dimensions at or below baseline)." -ForegroundColor Green
}
exit 0
