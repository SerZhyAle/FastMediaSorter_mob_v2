# Phase 03 — focus-traversal

**Strategic spec:** [`../S0081_tv-remote-key-coverage.md`](../S0081_tv-remote-key-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Fix two focus-traversal defects: (1) `FocusManager` currently consumes DPAD events even when focus is already at the list boundary, preventing Android's default focus search from moving focus to toolbar/controls above or below the list; (2) player controls lack a closed `nextFocusLeft/Right` loop, which can leave focus stranded on the first or last button.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt` | Modified | ≤ 210 |
| `app_v2/src/main/res/layout/custom_player_controls.xml` | Modified | as-is |

> `app_v2/src/main/res/layout-land/custom_player_controls.xml` — landscape variant absent (confirmed). No landscape parity step needed.

---

## Steps

### Step 3.1 — Return false when FocusManager hits a list boundary

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `FocusManager.applyAction`, inside the `is InputAction.MoveFocus` branch, the current code computes `target` and clamps it with `coerceIn(0, itemCount - 1)`, then always calls `moveFocus(target)` and returns `true`.
>
> Add a boundary check **before** calling `moveFocus`: if `target == cur` (focus is already at the boundary and cannot move further), return `false` instead of `true`. This allows Android's default focus search to run and transfer focus to views outside the list (e.g., the toolbar when pressing DPAD_UP on the first item).
>
> Do not change the `coerceIn` call itself — it is still correct for the non-boundary case.

**Verification:**

- `Grep` — `if (target == cur) return false` present in `FocusManager.kt`.
- `Grep` — `coerceIn(0, itemCount - 1)` still present in `FocusManager.kt` (unchanged).
- `Grep` — `Log\.d\(` returns zero hits in `FocusManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: FocusManager.kt (+1 LOC). Dev log recorded.

---

### Step 3.2 — Close the focus loop in custom_player_controls.xml

**Files:** `app_v2/src/main/res/layout/custom_player_controls.xml`
**Depends on:** — start of phase (independent of 3.1)

**Prompt for developer:**

> Open `app_v2/src/main/res/layout/custom_player_controls.xml`. Identify all interactive controls (Buttons, ImageButtons, SeekBar) that have `android:focusable="true"` or inherit focusability. Verify that:
>
> 1. The leftmost focusable control has `android:nextFocusLeft="@id/<rightmost_control_id>"` (wraps to the right end).
> 2. The rightmost focusable control has `android:nextFocusRight="@id/<leftmost_control_id>"` (wraps to the left end).
> 3. Every focusable control that has a left/right neighbor already set also has a correct `nextFocusRight`/`nextFocusLeft` pointing to that neighbor.
>
> Add any missing `nextFocusLeft` / `nextFocusRight` attributes to close the loop. Do not add `nextFocusUp` / `nextFocusDown` — vertical escape is handled by the Step 3.1 change via Android's default focus search.

**Verification:**

- `Grep` — `nextFocusLeft` present in `custom_player_controls.xml`.
- `Grep` — `nextFocusRight` present in `custom_player_controls.xml`.
- `Grep` — at least two distinct `@id/` values referenced in `nextFocusLeft`/`nextFocusRight` attributes, confirming both ends of the chain are wired.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: custom_player_controls.xml (+2 attributes). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 3.* above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for both modified files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`FocusManager` no longer traps focus at list boundaries — DPAD_UP on item 0 now passes control to the toolbar/controls above the list via Android's natural focus search. Player control bar forms a closed lateral loop, so DPAD_LEFT/RIGHT never dead-ends. Phase 04 adds acceleration on top of this corrected traversal.

---

## Rollback Plan

Revert phase commit(s). The `FocusManager` change is a one-line addition; the layout change is additive attribute-only — no data or schema involved.
