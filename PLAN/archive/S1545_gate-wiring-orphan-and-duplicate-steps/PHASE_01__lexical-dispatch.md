# Phase 01 - Lexical Dispatch

**Strategic spec:** [`../S1545_gate-wiring-orphan-and-duplicate-steps.md`](../S1545_gate-wiring-orphan-and-duplicate-steps.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-14

---

## Objective

Make the common lexical runner the sole automatic closure route for rules it already registers, with a regression guard for coverage and diagnostic identity.

## Prerequisites

- [ ] Strategic §6 research items are Resolved.
- [ ] `scripts/quality/assert-source-gates.ps1` and `scripts/quality/assert-neuroslop.ps1` exist.
- [ ] CODE.LOCK acquired immediately before source edits.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/post-change.ps1` | Modified | ≤ 150 |
| `scripts/quality/source-matchers.tests/Run-Tests.ps1` | Modified | ≤ 100 |
| `scripts/post-change.tests/Run-Tests.ps1` | New | ≤ 180 |
| `scripts/quality/gate-recovery-hints.psd1` | Modified | ≤ 40 |

## Steps

### Step 01.1 - Remove duplicate automatic lexical routes

**Files:** `scripts/post-change.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Retain the unfiltered lexical umbrella for Kotlin and XML resource changes. Remove only the facade invocations and routing flags for the flavour-flag, public-mutable-flow and deprecated-package-manager wrappers; do not change their rule declarations, baselines or direct command interfaces.

**Why:**

The strategic specification requires every lexical invariant to run no more than once during a closure while preserving compatible manual entry points.

**Verification:**

- `rg -n 'Invoke-Gate "(flavor-flag-gate|public-mutable-flow-gate|deprecated-pm-flags-gate)" scripts/post-change.ps1` returns zero matches.
- `rg -n 'Invoke-Gate "neuroslop-gate"' scripts/post-change.ps1` returns exactly one match.
- `pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Gate` completes with an explicit verdict.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Removed three duplicate facade routes; one neuroslop route remains and the 19-rule common runner passed over 4119 files.

### Step 01.2 - Prove common-runner coverage

**Files:** `scripts/quality/source-matchers.tests/Run-Tests.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the matcher regression suite to assert that the common rule registry exposes `flavor-flags`, `public-mutable-flow` and `deprecated-pm-flags` with their expected baseline-bearing rule records.

**Why:**

The removed facade calls are safe only while the common lexical registry remains the owner of all three rules.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/source-matchers.tests/Run-Tests.ps1` exits 0.
- The test names all three rule identifiers.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Added seven-case matcher suite coverage for the three rules retained by the common runner; suite passed.

### Step 01.3 - Guard closure dispatch and diagnostic labels

**Files:** `scripts/post-change.tests/Run-Tests.ps1`, `scripts/quality/gate-recovery-hints.psd1`
**Depends on:** Steps 01.1, 01.2

**Prompt for developer:**

> Remove the recovery hints for the deleted routes. Add a hermetic PowerShell regression test that inspects the closure dispatch contract: it must contain the one umbrella route, no removed duplicate route, and exactly one recovery-hint-compatible label for every remaining invoked gate. Do not execute a mutating closure facade from the test.

**Why:**

The strategic specification requires the report to retain usable diagnostic identity without paying for a second source traversal.

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.tests/Run-Tests.ps1` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-gate-hints-sync.ps1 -Gate` exits 0.
- The test fails when a removed wrapper invocation, unregistered gate label or orphan hint is reintroduced.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Added hermetic facade dispatch and recovery-hint contract test; it passed for 23 routed labels.
- 2026-08-14 - Removed three stale recovery hints and added a contract test; dispatch test and strict hint-sync gate both passed.

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/quality/assert-gate-hints-sync.ps1` exits 0.
- [x] Dev log entry added for every file in Files Touched.
- [x] Phase-boundary audit run with no unresolved P0/P1 finding.

## Handoff Notes to Next Phase

The common lexical runner remains the sole automatic route; direct wrappers remain intact for explicit callers.

## Rollback Plan

Revert the phase commit(s); no data migration or user-facing surface changes.
