# Phase 01 - Safe discount and benign forms

**Strategic spec:** [`../S1559_listener-symmetry-full-scan-scope.md`](../S1559_listener-symmetry-full-scan-scope.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Make a discount shrink an imbalance instead of shrinking the add count, and teach the gate the three forms measured to be benign - so the count drops below the baseline before the scope grows.

---

## Prerequisites

- [ ] `temp/CODE.LOCK` acquired before the edit and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-listener-symmetry.ps1` | Modified | ≤ 260 |

---

## Steps

### Step 01.1 - Route every category through a discount that cannot manufacture an imbalance

**Files:** `scripts/quality/assert-listener-symmetry.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a helper taking the add count, the remove count and a discount count, returning the imbalance: when adds do not exceed removes, the absolute difference unchanged; otherwise the excess of adds over removes reduced by the discount and floored at zero. Rewrite `Get-FileImbalance` so all five categories go through it, and pass the two existing discounts - the null receiver and the back-pressed dispatcher callback - as the discount argument rather than subtracting them from the add count.

**Why:**

Strategic §4 measured that subtracting from the add count and then taking the absolute difference reports a balanced file as imbalanced - `LauncherStatusStripManager.kt`, which S1501 had already fixed, gained a phantom imbalance of one the moment its observer was discounted; both existing discounts carry the same defect today.

**Verification:**

- `Grep` - `Get-FileImbalance` contains no `[Math]::Abs(` call of its own; every category returns through the new helper. PASS.
- Report run on the unchanged scope prints `actual 115` - this step alone must not move the number, because neither existing discount has a paired site today. PASS, measured in isolation: `expected: 115 | actual: 115`. So the safe form is prophylaxis for the two old discounts and a precondition for the new ones, not a source of the drop.

**Status:** `[x]` done

---

### Step 01.2 - Discount the three benign forms

**Files:** `scripts/quality/assert-listener-symmetry.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Drop `import` lines from the text before counting. Add two discounts alongside the existing ones: a `lifecycle.addObserver(..)` call, which the lifecycle itself drops on destruction, and an `addEventListener(..)` call, which is a DOM registration inside an HTML string literal and not Kotlin at all. Record next to each what it is and why it is benign.

**Why:**

Strategic §4 found half the imbalance outside `src/main` is not a registration at all - an `import androidx.activity.addCallback` line, a `document.addEventListener` inside a string literal, and two observers handed to a lifecycle - so counting them makes the gate assert leaks that cannot exist.

**Verification:**

- Report run on the unchanged scope prints `actual 102`, matching `research/02__discount-candidates.ps1`. PASS - `baseline 115 | actual 102 | delta -13`.
- The gate and the probe agree: both read 102 for `src/main` and 106 across every source set.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin touched.
- [x] Dev log entry deferred to Phase 03 - one entry per logical change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The discount can only shrink an imbalance and only in the leak direction, so no file that was flagged before can go unflagged unless one of the three named forms explains it; the -13 was attributed file by file against the probe.

---

## Handoff Notes to Next Phase

The count sits at 102 against a baseline of 115, so Phase 02 can widen the scope to 106 and still ratchet the baseline down rather than up.

---

## Rollback Plan

Revert the phase's hunks - the gate returns to its previous counting and the baseline is untouched.
