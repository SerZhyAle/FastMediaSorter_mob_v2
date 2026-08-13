# Phase 04 - Custom surfaces input

**Strategic spec:** [`../S0943_app-wide-dpad-operability.md`](../S0943_app-wide-dpad-operability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Make dialogs, bottom sheets, and the player transport fully operable by directional keys and OK/Center under the same contract: initial focus set, directional traversal between on-screen controls, activation on Center, no swallowed navigation.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| dialog / bottom-sheet host base | Modified | ≤ 250 |
| player transport control host | Modified | ≤ 300 |
| `core/ui/focus/FocusFrameController.kt` (dialog-window attach) | Modified | ≤ 140 |

> The focus frame must attach to dialog / bottom-sheet windows too (strategic §5.3, S0819 Phase 03). Shared `src/main`; no flavor divergence.

---

## Steps

### Step 04.1 - Frame + initial focus for dialogs and bottom sheets

**Prompt for developer:**

> Attach the focus frame to dialog and bottom-sheet windows and apply the initial-focus resolver so these surfaces show a correct frame and a predictable starting focus in non-touch mode.

**Verification:**

- `Grep` - dialog/bottom-sheet window attach path present for the focus frame.
- `/build` - project compiles.

**Status:** `[ ]` not done

### Step 04.2 - Player transport directional navigation

**Prompt for developer:**

> Ensure the player transport moves focus between on-screen controls with D-pad Left/Right/Up/Down (not raw seek/volume), activates the focused control on Center, and keeps the controls overlay visible while navigating in non-touch. Reuse the existing player directional dispatch; do not duplicate it.

**Verification:**

- `Grep` - directional dispatch routes to on-screen transport controls.
- `/build` - project compiles.

**Status:** `[ ]` not done

### Step 04.3 - No-swallow audit for custom key handlers

**Prompt for developer:**

> Audit custom `dispatchKeyEvent`/`onKeyDown` handlers on these surfaces so they do not consume directional keys they do not handle - unhandled keys fall through to normal focus traversal.

**Verification:**

- `Grep` - no custom handler returns `true` unconditionally for directional keys on these surfaces.
- `/build` - project compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All interactive surfaces obey the same directional contract; Phase 05 can audit them uniformly.

---

## Rollback Plan

Revert phase commit(s) - dialog/player wiring reverts to prior behavior. No data migration.
