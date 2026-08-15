# Phase 03 - Shoulder-button page-jump fallback

**Strategic spec:** [`../S0508_gamepad-navigation-parity.md`](../S0508_gamepad-navigation-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-18
**Completed:** 2026-06-18

**Step Log:**

- 2026-06-18 - Step 03.1 Verification PASS: `onGamepadPageJump`, `KEYCODE_BUTTON_L1/R1` present; fallback positioned after `tvKeyRouter.route` (line 357 < 364).
- 2026-06-18 - Step 03.2 build gate satisfied by the shared Phase 02+03 build (`.\a.ps1 d` SUCCESSFUL) - BaseActivity is the only Phase 03 file, built together with Phase 02 to avoid a redundant second gradle run.

---

## Objective

Give unconsumed L1/R1 a generic page-jump in the shared base Activity (page-scroll the active container by default; pager/tab screens override), without regressing screens that already consume L1/R1.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] `shouldHandleGamepadNavigation()` hook present in `BaseActivity`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified | ≤ 540 |

---

## Steps

### Step 03.1 - Add page-jump hook + L1/R1 fallback

**Files:** `core/ui/BaseActivity.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Add `protected open fun onGamepadPageJump(forward: Boolean): Boolean`. Default implementation: page-scroll `getGamepadScrollTargetView()` by roughly one viewport (scroll by the target's measured height, sign from `forward`); return true if a scroll target existed, false otherwise. In `BaseActivity.dispatchKeyEvent`, after the existing S0506 guard and `TvKeyRouter` routing (so a subclass that already consumed L1/R1 via its own override pre-empts this), add a fallback: on `ACTION_DOWN` `KEYCODE_BUTTON_L1`/`KEYCODE_BUTTON_R1` from a gamepad source, when `shouldHandleGamepadNavigation()` is true, call `onGamepadPageJump(forward = R1)` and return its result. Keep the existing return path unchanged when the keys are not L1/R1 or the screen opted out.

**Verification:**

- `Grep` - `fun onGamepadPageJump` present in `BaseActivity.kt`.
- `Grep` - `KEYCODE_BUTTON_L1` and `KEYCODE_BUTTON_R1` present in `BaseActivity.kt`.
- `Grep` - the L1/R1 fallback is positioned after the `tvKeyRouter.route` call (read the method; fallback must not pre-empt subclass consumption).
- `.\a.ps1 fk` -> exit 0.

**Status:** `[x]` done

---

### Step 03.2 - Build gate

**Files:** (no new edits)
**Depends on:** Step 03.1

**Prompt for developer:**

> Build the standard debug APK. `.\a.ps1 d`.

**Verification:**

- `.\a.ps1 d` -> BUILD SUCCESSFUL.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles and packages - `.\a.ps1 d` SUCCESSFUL.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `BaseActivity.kt`.

---

## Handoff Notes to Next Phase

Generic page-jump default page-scrolls any active container; pager/tab screens may later override `onGamepadPageJump` for true page/tab change. Final phase records the capability and regenerates the catalog.

---

## Rollback Plan

Revert phase commit; the L1/R1 fallback is additive and only fires on otherwise-unconsumed gamepad shoulder buttons.
