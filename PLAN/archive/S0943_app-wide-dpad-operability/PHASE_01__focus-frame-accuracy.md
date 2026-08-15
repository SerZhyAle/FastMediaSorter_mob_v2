# Phase 01 - Focus indicator (in-place decoration, platform-standard)

**Strategic spec:** [`../S0943_app-wide-dpad-operability.md`](../S0943_app-wide-dpad-operability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none (indicator mechanism; reachability phases are independent)
**Steps done:** 4 / 4
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Replace the travelling `decorView`-overlay focus frame (retired S0819) with the platform-standard indicator: the focused view is decorated IN PLACE with an accent outline, applied app-wide by one window-level controller, in non-touch mode only. Because the outline is drawn by the focused view in its own bounds, it can never be offset (root cause of the overlay's misalignment - see [`research/01__tv-focus-indicator-approach.md`](research/01__tv-focus-indicator-approach.md), [`research/02__focus-frame-offset-root-cause.md`](research/02__focus-frame-offset-root-cause.md)).

---

## Prerequisites

- [x] Research §6.1 resolved - platform-standard is per-view/in-place, not a coordinate-computed overlay.
- [x] S0819 archived (overlay approach superseded).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `core/ui/focus/FocusDecorationController.kt` | New | ≤ 90 |
| `core/ui/focus/FocusDecorationActivityCallbacks.kt` | New | ≤ 60 |
| `core/ui/focus/FocusDecorationFragmentCallbacks.kt` | New | ≤ 40 |
| `core/ui/focus/FocusDecorationExcluded.kt` | New | ≤ 10 |
| `res/drawable/focus_decoration_outline.xml` | New | - |
| `FastMediaSorterApp.kt` | Modified | ≤ 5 |
| `ui/common/input/FocusRingHelper.kt` | Modified | ≤ 5 (KDoc) |
| `core/ui/focus/FocusFrame*.kt` (5 files) | Deleted | - |
| `res/values/{attrs,themes,integers,dimens,colors}.xml` | Modified | - |

> No layout files touched - no landscape parity needed. Shared `src/main` infra, no flavor divergence.

---

## Steps

### Step 01.1 - In-place decoration controller + outline drawable

**Prompt for developer:**

> Add `FocusDecorationController(window)` that registers one `OnGlobalFocusChangeListener` + `OnTouchModeChangeListener` on the window decor, and on focus-in (non-touch) sets an accent outline (`res/drawable/focus_decoration_outline.xml`, `?attr/colorPrimary` stroke) as the focused view's `foreground`, restoring the prior foreground on focus-out / touch-mode / detach. No coordinate math, no overlay.

**Verification:**

- `Glob` - `FocusDecorationController.kt` and `focus_decoration_outline.xml` exist.
- `Grep` - `OnGlobalFocusChangeListener` present; `offsetDescendantRectToMyCoords` and `decorView.overlay` absent in the focus package.

**Status:** `[x]` done

### Step 01.2 - App-wide wiring (Activity + dialog windows)

**Prompt for developer:**

> Add `FocusDecorationActivityCallbacks` (+ `FocusDecorationFragmentCallbacks` for DialogFragment/bottom-sheet windows, + `FocusDecorationExcluded` opt-out marker) and register the Activity callbacks once in `FastMediaSorterApp`. One controller per window.

**Verification:**

- `Grep` - `FocusDecorationActivityCallbacks` registered in `FastMediaSorterApp.kt`.

**Status:** `[x]` done

### Step 01.3 - Remove the overlay approach

**Prompt for developer:**

> Delete `FocusFrameController`, `FocusFrameOverlay`, `FocusFrameActivityCallbacks`, `FocusFrameFragmentCallbacks`, `FocusFrameExcluded`, and their res tokens (`focusFrame*` attrs + theme items, `focus_frame_anim_ms`, `focus_frame_fallback`); rename the reused dimens to `focus_decoration_*`. Update `FocusRingHelper` KDoc.

**Verification:**

- `Grep` - `FocusFrame`, `focusFrame`, `focus_frame` return zero hits under `app_v2/src`.
- `/build` - project compiles.

**Status:** `[x]` done

### Step 01.4 - Device verification on TV emulator

**Prompt for developer:**

> Install standard-debug on the Android TV emulator, navigate with D-pad across the welcome pager (bottom-nav buttons, two-column toggle rows) and at least one other screen. The outline must hug each focused control exactly (both columns, bottom bar), with no offset and no floating frame.

**Verification:**

- Outline sits on the focused control within a few px on a bottom-bar button.
- Outline sits on the focused control within a few px on a right-column toggle row (the case the overlay got wrong).

**Result (measured on TV emulator, Android 16, standard-debug):** the outline hugs each focused row exactly in its own bounds (e.g. `rowSourceCloud [460,450][1460,546]`), with zero offset on both columns and the bottom bar. Views that carry their own foreground focus affordance (nav buttons) keep it (decorator skips them). No coordinate math involved.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `FocusFrame` returns zero hits under `app_v2/src`.
- [ ] Dev log entry added for the change.

---

## Handoff Notes to Next Phase

The indicator is now a per-view in-place outline with zero coordinate math - trustworthy on every screen. Reachability phases (02+) can verify visually by watching where the outline lands. The controller is a `src/main` window-level singleton per window, no flavor divergence.

---

## Rollback Plan

Revert the phase commit(s) - new classes + drawable are additive; the deleted overlay would be restored from history. No data migration, no persisted state.
