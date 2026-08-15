# Phase 04 - Reconcile existing focus visuals

**Strategic spec:** [`../S0819_tv-dpad-focus-visibility.md`](../S0819_tv-dpad-focus-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-07-01
**Completed:** 2026-07-01 (FocusManagerTest PASS; scoped detekt/neuroslop PASS)

---

## Objective

Remove the double focus indicator: neutralize the runtime `FocusRingHelper` ring (superseded by the app-wide overlay) while preserving `requestFocus()` and selection/activation state that `FocusManager` relies on.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/FocusRingHelper.kt` | Modified | ≤ 79 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/FocusManagerTest.kt` | Modified | ≤ +20 |

---

## Steps

### Step 04.1 - Neutralize the `FocusRingHelper` ring

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/FocusRingHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The app-wide overlay now draws the single travelling frame on any real-focus View, including `MainActivity` resource items (which call `view.requestFocus()`). Make `FocusRingHelper.setFocused(view, focused)` STOP assigning the `GradientDrawable` ring to `view.foreground` (remove the ring build + foreground mutation), so there is no second ring stacked under the overlay. Preserve any `view.isActivated` / selection-state mutation the callers depend on (keep the activation toggle if present). Add a one-line WHY comment: ring superseded by app-wide `FocusFrameOverlay` (S0819). If, after removing the ring, the class has no remaining behavior, keep the public method as a no-op shim (callers in `FocusManager` still invoke it) rather than deleting call sites this phase.

**Verification:**

- `Grep` - no `GradientDrawable(` construction remains in `FocusRingHelper.kt`.
- `Grep` - `foreground =` ring assignment removed (or replaced by activation-only logic).
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - Rewrote `FocusRingHelper` to `isActivated`-only; removed the `GradientDrawable` ring build, `foreground` mutation, elevation bump, tag save/restore and the hardcoded `RING_COLOR` (`0xFF2196F3`) - all superseded by the app-wide `FocusFrameOverlay` (Rule 21 dead-weight removal). `setFocused`/`attach` public API kept so `FocusManager`'s 4 call sites need no change. Predicates PASS (no `GradientDrawable(`, no ring `foreground =`, 0 `Log.d`).

---

### Step 04.2 - Update `FocusManagerTest` expectations

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/FocusManagerTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Adjust any assertion that expected `FocusRingHelper` to set a foreground ring; assert the retained behavior instead (`requestFocus` called, activation/selection state, position tracking). Do not assert on the removed ring drawable.

**Verification:**

- `./gradlew.bat testStandardDebugUnitTest --tests "*FocusManagerTest*"` (or `.\a.ps1 fu` scoped) - PASS.
- `Grep` - no assertion references the removed ring foreground.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - `FocusManagerTest` never asserted on the ring/foreground (grep: 0 references to foreground/GradientDrawable/FocusRingHelper/isActivated), so no test edit was required. Forced `testStandardDebugUnitTest --tests *FocusManagerTest* --rerun-tasks` -> BUILD SUCCESSFUL (exit 0). Verified retained behavior (position tracking + requestFocus) still passes.

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] Project compiles + `FocusManagerTest` passes (fk PASS; test rerun PASS).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added.

---

## Handoff Notes to Next Phase

Only one focus indicator (the overlay) renders app-wide. `FocusManager.moveFocus()`'s dead `&& false` sub-expression is out of scope here (tracked separately) - do not touch unless already editing that line.

---

## Rollback Plan

Revert the phase commit - restores the prior ring; overlay keeps working (cosmetic double-ring returns, no functional break).
