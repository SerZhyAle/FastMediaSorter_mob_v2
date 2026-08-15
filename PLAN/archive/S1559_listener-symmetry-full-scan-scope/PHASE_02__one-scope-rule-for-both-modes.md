# Phase 02 - One scope rule for both modes

**Strategic spec:** [`../S1559_listener-symmetry-full-scan-scope.md`](../S1559_listener-symmetry-full-scan-scope.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Give the full scan the scope the delta mode already judges, and move the baseline to the measured number.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and the report reads 102.
- [ ] `temp/CODE.LOCK` acquired before the edit and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-listener-symmetry.ps1` | Modified | ≤ 260 |
| `scripts/quality/listener-symmetry-baseline.txt` | Modified | n/a - single integer |

---

## Steps

### Step 02.1 - Derive the scan roots from the delta mode's own rule

**Files:** `scripts/quality/assert-listener-symmetry.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the two hard-coded scan roots with every immediate source-set directory under `app_v2/src` and `wear/src` whose name does not begin with `test` or `androidTest` - the same predicate the delta-mode filter applies. Write the rule once and have both modes read it, so a source set added later joins both at once. Keep the existing build-output filter.

**Why:**

Strategic §1 records the whole defect as one gate with two scopes: a delta run refuses an unbalanced registration added to a flavor source set while the integer baseline can never see the same registration once it is in HEAD, and §5.1 requires the rule to live in one place so the two cannot drift apart again.

**Verification:**

- `Grep` - `app_v2/src/main` and `wear/src/main` no longer appear as literal scan roots. PASS - both modes call `Test-InSymmetryScope`.
- Report run prints `actual 106`. PASS - `baseline 115 | actual 106 | delta -9`.
- `-List` names the four `vr` files strategic §4 predicted and no others outside `src/main`. PASS - exactly those four.

**Status:** `[x]` done

---

### Step 02.2 - Ratchet the baseline down to the measured count

**Files:** `scripts/quality/listener-symmetry-baseline.txt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run the gate's own `-UpdateBaseline` so the file moves from 115 to 106. Do not hand-edit the number and do not touch the refusal to raise it.

**Why:**

Strategic ADR-3 keeps the upward refusal intact precisely because the measurement removed the need to raise anything - the widened scope with the corrected discount lands below the old cap, so the ordinary downward ratchet is the whole operation.

**Verification:**

- `scripts/quality/listener-symmetry-baseline.txt` reads `106`. PASS.
- The script printed `listener-symmetry baseline ratcheted DOWN: 115 -> 106`, not a hand edit. PASS.
- `assert-listener-symmetry.ps1 -Gate` - `expected: 0 | actual: 0`, `baseline 106 | actual 106 | delta 0`.

**Status:** `[x]` done

---

### Step 02.3 - Make the `-List` detail explain the imbalance beside it

**Files:** `scripts/quality/assert-listener-symmetry.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> The `-List` detail line recomputes its per-category counts from the raw text, so it ignores both the import filter and every discount. Have it count the same text the imbalance did and print add counts net of their discounts.

**Why:**

Widening the scope made the mismatch visible on the four `vr` files - `Callback: 2 vs 0` printed beside `imbalance: 1` - and a gate whose own explanation contradicts its own number is read as broken, which strategic §2 goal 2 is written to avoid.

**Verification:**

- `-List` now prints `Callback: 1 vs 0` beside `imbalance: 1` on `DiagnosticXrActivity.kt`. PASS.
- The total is unchanged: `actual 106` before and after, so the fix is display-only.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin touched.
- [x] Dev log entry deferred to Phase 03 - one entry per logical change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The widened scope adds directories only; the build-output filter is unchanged, and the run stays inside the fast battery's budget.

---

## Handoff Notes to Next Phase

Both modes now read one scope rule and the baseline is the measured 106. Phase 03 pins each of those two properties with a case that fails if it is reverted.

---

## Rollback Plan

Revert the scan-root hunk and restore `115` to the baseline file - no other file changes.
