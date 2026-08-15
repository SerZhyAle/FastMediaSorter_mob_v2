# Phase 03 - One-process neuroslop

**Goal:** `assert-neuroslop.ps1` forks a separate `pwsh` per child detector (8 children) because each child `exit`s and a dot-sourced `exit` would kill the host. Run the detectors in one process without losing the `exit`-isolation guarantee. Detector set and verdict must stay 1:1.

## Chosen approach (lower risk than Option A/B)

Neither the function-refactor (Option A) nor the shared module (Option B) was needed. Empirically, the call operator on a script FILE - `& detector.ps1` - already isolates the child's `exit`: it sets `$LASTEXITCODE` and returns to the caller. Only a DOT-sourced `. detector.ps1` or a function-scoped `exit` kills the host. So the exit-isolation guarantee is satisfied by invocation style alone, with ZERO edits to the 8 detector files (their detection logic stays byte-for-byte identical, which is the strongest possible coverage-1:1 guarantee). Only `assert-neuroslop.ps1` changed: the per-child `& $pwshExe -File $path` fork became an in-process `& $path`, wrapped in try/catch so a child's terminating error is caught and (in gate mode) counted as a failure - fail-closed, matching the fork's non-zero-exit outcome.

## Steps

- [x] **1. In-process, exit-isolated invocation.**
  - Verified `& file.ps1` isolates a child `exit` in the current process (`temp/exit_isolation_direct.ps1`: child `exit 5` -> caller reads `$LASTEXITCODE=5`, host reaches end, exits 0). Detector files untouched, so `pwsh -File assert-<child>.ps1 -Gate` standalone behaviour is unchanged by definition.

- [x] **2. Rewrite `assert-neuroslop.ps1` to run detectors in-process.**
  - Replaced the `$pwshExe` fork with `& $path` (+`-Gate`), same child list/order, MISSING-child guard kept, terminating-error try/catch counts as failure.
  - Verification: default and `-Gate` output byte-identical to the fork baseline (`diff` clean, exit 0 in both). Runtime dropped ~20.7s (fork) -> ~8.4s (in-process).

- [x] **3. Preserve exit isolation.**
  - A failing first child (`trivial-comments` -> `exit 1`) did NOT stop the orchestrator: the remaining 7 detectors still ran in the seeded-violation test. Host survives every child `exit`.

## Parity check (phase acceptance)

- [x] Current tree: fork vs in-process produce identical per-dimension baseline-vs-actual lines and identical final exit code (default + `-Gate`, `diff` clean).
- [x] Seeded violations (`nontimber-log` + `trivial-comments` in a scratch `.kt`): in-process gate failed exactly those two dimensions (`FAIL (2 dimension(s) above baseline)`, exit 1); clean again after removal (exit 0).
- [x] `assert-fast-gates.ps1` still forks `assert-neuroslop.ps1` and it passes through: `assert-neuroslop.ps1 PASS (8614 ms)` in the summary (overall fast-gates FAIL is the unrelated pre-existing `listener-symmetry` WIP, not neuroslop).
