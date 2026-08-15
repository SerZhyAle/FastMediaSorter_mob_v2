# S0850 - Extend -ChangedFiles delta to neuroslop children + listener-symmetry

**Ticket:** S0850
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-01
**Tier:** 3 - Medium

<!-- discovered by /spec-all - 2026-07-01 (S0848 Phase 04 remainder) -->

## Goal

S0848 Phase 04 landed the `-ChangedFiles` delta contract plus the shared helper (`scripts/quality/lib/changed-files-delta.ps1`) and wired it into the two pure occurrence-count standalone gates (`assert-flavor-flags-not-growing.ps1`, `assert-deprecated-pm-flags.ps1`). Extend the same delta to the remaining ratchet gates so `post-change -ScopeToFile` judges a real delta everywhere instead of an advisory full scan for them.

Deliberately deferred from S0848 to avoid a rushed bulk edit of 9 heterogeneous critical detectors under the coverage-1:1 invariant. Those gates keep today's `-ScopeToFile` advisory-full-scan behaviour until this ticket lands - no regression in the meantime.

## Scope

Ratchet gates still on the full-scan-only path:

1. The 8 neuroslop children (each needs `-ChangedFiles` + its OWN count logic as the helper callback, threaded through `assert-neuroslop.ps1 -ChangedFiles`):
   - Simple `$rx.Matches($text)` (reuse regex verbatim): `assert-empty-catch`, `assert-globalscope`, `assert-nontimber-log`, `assert-stub-todo`, `assert-em-dash`.
   - Per-line / bespoke count (wrap existing logic in the callback): `assert-trivial-comments` (`Test-TrivialLine`), `assert-layout-hardcoded-colors` (XML, `res` root, per-line), `assert-unsafe-collect` (brace-scan).
2. `assert-listener-symmetry.ps1` - a BALANCE gate (register vs remove), not a simple occurrence count. Needs a delta contract of its own (e.g. per-file net-registration growth vs HEAD), so it is the trickiest and must not be forced into the occurrence-count helper blindly.

## Direction

- Add `[string[]]$ChangedFiles` to each child; when set, dot-source the shared helper and judge growth over the changed files with the child's exact count logic (mirrors flavor-flags/deprecated-pm from S0848).
- `assert-neuroslop.ps1`: accept `-ChangedFiles`, forward to every child (all must accept the param even if a child ultimately falls back).
- `post-change.ps1`: under `-ScopeToFile`, switch `neuroslop-gate` (and `listener-symmetry-gate` once its delta exists) from `$ratchetRunner` advisory to a FATAL `Invoke-Step` with `-ChangedFiles $File`, matching the flavor-flags/deprecated-pm treatment.
- Parity per gate: full-scan verdict unchanged when `-ChangedFiles` omitted; clean changed file PASS; a new violation in the changed file caught; a pre-existing violation in another file does not fail.

## Related

- S0848 (parent; delivered the helper + contract + first two wired gates).

## Last Audit

**Date:** 2026-07-03
**Verdict:** Verified

Delivered:

- All 8 neuroslop children accept `-ChangedFiles` and judge growth via `Measure-ChangedFileGrowth` with their OWN count logic as the callback: 5 regex children reuse `$rx` verbatim; `assert-trivial-comments` wraps `Test-TrivialLine` per line; `assert-unsafe-collect` wraps `$launchRx` + `Test-UnsafeLaunchBody`; `assert-layout-hardcoded-colors` scopes to `res/layout(-land)/` `.xml`.
- `assert-listener-symmetry.ps1`: per-file imbalance extracted into `Get-FileImbalance` (shared by full scan and delta - byte-for-byte the same logic); delta = per-file imbalance growth vs HEAD, which preserves the balance-ratchet guarantee without forcing occurrence-count semantics.
- `assert-neuroslop.ps1` forwards `-ChangedFiles` to every child in gate mode; report mode stays full-scan.
- `post-change.ps1`: `neuroslop-gate` + `listener-symmetry-gate` under `-ScopeToFile` are now FATAL `Invoke-Step` with `-ChangedFiles $File` (was advisory full scan); only `icon-inventory-sync-gate` still uses the advisory runner; stale S0826 comments updated.

Validation (harness `temp/S0850_delta_harness.ps1`, ALL PASS 29/29):

- Parse: 11 edited `.ps1` - expected: 0 syntax errors | actual: 0.
- Full-scan parity: umbrella + listener report output before/after edits - expected: identical | actual: identical (incl. listener actual 139 vs baseline 133 from other tickets' WIP).
- Clean changed file: delta gate - expected: PASS despite pre-existing findings elsewhere | actual: PASS (predicates "clean PASS" + "unrelated violations do not fail").
- Seeded violations (tracked-clean `.kt` + layout `.xml`, byte-exact restore verified by git): each of the 7 kt dimensions caught growth=1; listener imbalance caught; layout hex caught - expected: exit 1 each | actual: exit 1 each.
- Post-restore delta - expected: PASS | actual: PASS.
