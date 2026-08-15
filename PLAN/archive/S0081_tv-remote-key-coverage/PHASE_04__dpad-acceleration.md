# Phase 04 — dpad-acceleration

**Strategic spec:** [`../S0081_tv-remote-key-coverage.md`](../S0081_tv-remote-key-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

When the user holds DPAD_UP or DPAD_DOWN, switch from single-step focus movement to page-jump after a repeat-count threshold — so navigating long lists does not require hundreds of individual presses.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt` | Modified | ≤ 215 |

---

## Steps

### Step 4.1 — Add DPAD_ACCEL_REPEAT_THRESHOLD constant to FocusManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `FocusManager`'s companion object (or top-level `private const val` if the class has no companion object), add:
> ```kotlin
> private const val DPAD_ACCEL_REPEAT_THRESHOLD = 6
> ```
> This value means: after 6 auto-repeat events (roughly 300 ms at the default Android key-repeat rate), the DPAD UP/DOWN switches to page-jump mode.

**Verification:**

- `Grep` — `DPAD_ACCEL_REPEAT_THRESHOLD` present in `FocusManager.kt`.
- `Grep` — `DPAD_ACCEL_REPEAT_THRESHOLD = 6` present (confirming the value was not omitted).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: FocusManager.kt (+1 LOC). Dev log pending end-of-phase.

---

### Step 4.2 — Switch to PageJump for held DPAD UP/DOWN in handleArrowKey

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> In `FocusManager.handleArrowKey`, the `when (keyCode)` block currently maps `KEYCODE_DPAD_UP` → `InputAction.MoveFocus(FocusDirection.UP)` and `KEYCODE_DPAD_DOWN` → `InputAction.MoveFocus(FocusDirection.DOWN)` unconditionally.
>
> Change both cases to check `event.repeatCount`:
> ```kotlin
> KeyEvent.KEYCODE_DPAD_UP ->
>     if (event.repeatCount > DPAD_ACCEL_REPEAT_THRESHOLD)
>         InputAction.PageJump(-1)
>     else
>         InputAction.MoveFocus(FocusDirection.UP)
> KeyEvent.KEYCODE_DPAD_DOWN ->
>     if (event.repeatCount > DPAD_ACCEL_REPEAT_THRESHOLD)
>         InputAction.PageJump(+1)
>     else
>         InputAction.MoveFocus(FocusDirection.DOWN)
> ```
> `PageJump(-1)` / `PageJump(+1)` reuse the existing `applyAction` logic which already computes a page-size jump (`spanCount * 3` for grids, `PAGE_SIZE` for lists). No changes to `applyAction` itself.

**Verification:**

- `Grep` — `event.repeatCount > DPAD_ACCEL_REPEAT_THRESHOLD` present in `FocusManager.kt`.
- `Grep` — `InputAction.PageJump(-1)` present in `FocusManager.kt`.
- `Grep` — `InputAction.PageJump(+1)` present (note: `+1` may be stored as `1` — accept either).
- `Grep` — `Log\.d\(` returns zero hits in `FocusManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Files: FocusManager.kt (+4 LOC total for both steps). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 4.* above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt" "FocusManager" "S0081 Phase 04: DPAD hold acceleration via PageJump"`.

---

## Handoff Notes to Next Phase

`FocusManager` now provides single-step navigation on tap and page-jump on held DPAD UP/DOWN. Acceleration is purely in the focus layer — no changes to adapters, repositories, or thumbnail loading. Phase 05 closes out documentation and catalog.

---

## Rollback Plan

Revert phase commit. Two small additions to `FocusManager` — no data, no schema, no user-visible surface other than scrolling speed.
