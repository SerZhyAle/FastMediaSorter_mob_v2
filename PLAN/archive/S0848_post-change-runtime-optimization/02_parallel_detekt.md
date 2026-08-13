# Phase 02 - Parallel detekt

**Goal:** In `post-change.ps1`, the gradle-backed detekt gate (line ~283) runs strictly after the fast lexical gates. Start detekt as a background job at the top of the gate section and join it at the end, so wall-clock ~= max(lexical, detekt). Verdict and exit code must be identical to the serial run.

## Steps

- [x] **1. Start detekt as a background job before the lexical gates (only when `$runsDetektGate`).**
  - Implemented with `Start-ThreadJob` (in-process thread, lighter than `Start-Job`; same `& $pwsh assert-detekt.ps1 -Gate` payload with Module and `-ChangedFiles $File` under `-ScopeToFile`). The job returns `[pscustomobject]@{ ExitCode; Output }` so both the exit code and the streamed rule lines survive the join.
  - `$detektJob` stays `$null` when `$runsDetektGate` is false (SKIP line unchanged at the join).

- [x] **2. Run all lexical/ratchet gates as today (serial), in place.**
  - Order and fatality unchanged; the inline detekt block was removed - detekt now only joins at the end.

- [x] **3. Join detekt at the end and translate to the same PASS/FAIL.**
  - `Receive-Job -Wait -AutoRemoveJob`; the job's `Output` is printed (failing rule lines preserved) and its `ExitCode` is mapped onto the existing `Invoke-Step "detekt-gate"` verdict (fatal on FAIL; detekt stays fatal even under `-ScopeToFile`, per current code).

- [x] **4. Guarantee cleanup on early exit.**
  - The gate section is wrapped in `try { .. } finally { Stop-Job/Remove-Job }`. Verified empirically that a `finally` runs before an `exit` (called from within `Invoke-Step`) propagates, so a failing lexical gate cannot orphan the detekt job. `$detektJob` is nulled right after a successful drain, keeping the finally a no-op on the happy path.

## Parity check (phase acceptance)

- [x] `post-change -ChangeType Kotlin -ScopeToFile` on a clean file: PASS, all step lines present, `detekt-gate PASS`, exit 0. (`temp/S0848_phase02_integration.log`.)
- [x] Detekt-only violation -> FAIL with the same exit code and the detekt rule surfaced. (Mechanism harness `temp/test_phase02_mech.ps1 -Mode DetektFail` -> `[detekt-gate] FAIL (3)`, output surfaced, process exit 3.)
- [x] Lexical-gate failure with detekt still running: process exits non-zero AND no leftover job (`Get-Job` empty). (Harness `-Mode LexicalFail` -> gate-2 FAIL(1), `JOBS_AFTER=0`, exit 1.)
- [x] Wall-clock below serial: the detekt-gate join waited only ~3.1s (detekt overlapped the ~62s of lexical gates) instead of adding a full standalone detekt step. (Integration log.)
