# Phase 04 - ChangedFiles ratchet delta

**Goal:** Today `-ScopeToFile` only softens the verdict (advisory) but still scans all of `src/main` in each ratchet gate. Add a real `-ChangedFiles` delta mode to the ratchet gates so they judge only the changed files, and wire it from `post-change.ps1 -ScopeToFile`. For "count must not grow" rules, judging the delta over changed files preserves the guarantee fully.

Ratchet gates in scope (all count-vs-baseline):
- `assert-flavor-flags-not-growing.ps1`
- `assert-neuroslop.ps1` children (trivial-comments, empty-catch, layout-colors, unsafe-collect, globalscope, nontimber-log, stub-todo, em-dash)
- `assert-deprecated-pm-flags.ps1`
- `assert-listener-symmetry.ps1`

## Scope note (partial - remainder -> S0850)

The delta contract, the shared helper, and the two pure occurrence-count STANDALONE gates are done here. The 8 neuroslop children (heterogeneous count logic) and `assert-listener-symmetry.ps1` (a balance gate, not an occurrence count) are deliberately deferred to **S0850** to avoid a rushed bulk edit of 9 critical detectors under the coverage-1:1 invariant. Those gates keep today's `-ScopeToFile` advisory full-scan behaviour until S0850 - no regression.

## Steps

- [x] **1. Define the delta contract + shared helper.**
  - `new occurrences = max(0, count(working copy) - count(HEAD copy))` summed over the changed files. A file absent from HEAD counts all its occurrences as new (fail-closed). Full scan (no `-ChangedFiles`) is the release/CI default.
  - Implemented once in `scripts/quality/lib/changed-files-delta.ps1` (`Measure-ChangedFileGrowth`, `Get-GitHeadText`). Unit-tested: unchanged tracked file -> New 0 (pre-existing occurrences never fail); new file with 2 occurrences -> New 2; extension filter honoured.

- [x] **2. Implement per gate (occurrence-count standalone gates).**
  - `assert-flavor-flags-not-growing.ps1` and `assert-deprecated-pm-flags.ps1`: added `-ChangedFiles`; when set they dot-source the helper and pass their EXACT existing regex as the count callback, scoped to the gate's own root (`src/main/java` / `src/main`, compat seam still allow-listed).
  - Verified per gate: no `-ChangedFiles` -> identical verdict to today (flavor 178|37, pm 0|0); `-ChangedFiles <clean file>` -> PASS (new 0); `-ChangedFiles <scratch with a new violation>` -> FAIL (+1, file named), exit 1.
  - [ ] neuroslop children + listener-symmetry -> **S0850**.

- [x] **3. Wire from `post-change.ps1` under `-ScopeToFile`.**
  - `flavor-flag-gate` and `deprecated-pm-flags-gate` now run FATAL `Invoke-Step` with `-ChangedFiles $File` under `-ScopeToFile` (real delta), instead of the advisory full scan.
  - `neuroslop-gate` and `listener-symmetry-gate` stay on the advisory `$ratchetRunner` until S0850 - no regression.

## Parity check (phase acceptance)

- [x] Full-scan verdict unchanged when `-ChangedFiles` omitted (both wired gates).
- [x] `post-change -ScopeToFile` on a clean file: PASS with real delta - `flavor-flag-gate` 335 ms and `deprecated-pm-flags-gate` 323 ms showing `[delta over changed files]` (vs ~2886/1758 ms full scan), overall PASS exit 0. (`temp/S0848_phase04_integration.log`.)
- [x] A new violation in the changed file is caught (both gates FAIL +1 on the scratch), while pre-existing violations in other files do not fail (delta is scoped to the changed files; helper T1 confirms in-file pre-existing occurrences also do not fail).
