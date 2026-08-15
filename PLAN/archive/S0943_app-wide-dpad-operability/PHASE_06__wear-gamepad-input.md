# Phase 06 - Wear + gamepad input

**Strategic spec:** [`../S0943_app-wide-dpad-operability.md`](../S0943_app-wide-dpad-operability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

> **BLOCKED on research §6.4** (Wear rotary + gamepad HAT/stick routing into the directional-navigation contract). Do not implement while the blocker is unchecked; the routing below is provisional.

---

## Objective

Bring Wear OS hardware input (rotary crown, side buttons) and gamepad directional input (HAT / analog stick, which may not reach standard key handlers) into the same directional-navigation contract as D-pad/keyboard.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Research §6.4 Resolved (input routing understood per device class).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/.../<WearInputAdapter>.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/<GamepadMotionAdapter>.kt` | New | ≤ 200 |

> `wear` module edits require `-Module wear` on catalog/post-change. Gamepad motion (HAT/stick) arrives as `MotionEvent`, not `KeyEvent`, so it needs a motion-to-direction adapter.

---

## Steps

### Step 06.1 - Gamepad motion-to-direction adapter

**Prompt for developer:**

> Per the §6.4 decision, translate gamepad HAT/analog-stick `MotionEvent` axes into directional focus moves routed through the same contract used by D-pad keys, so controllers whose D-pad arrives as motion still navigate.

**Verification:**

- `Glob` - the gamepad motion adapter file exists.
- `/build` - project compiles.

**Status:** `[ ]` not done

### Step 06.2 - Wear rotary / button input adapter

**Prompt for developer:**

> Route Wear rotary crown and side-button input into directional focus moves / activation on the `wear` module screens, following the same contract semantics.

**Verification:**

- `Glob` - the Wear input adapter file exists under `wear/src/main`.
- `/build` - `wear` module compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] Project compiles - run `/build` (both modules).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/wear.jsonl` regenerated via `scan.ps1 -Module wear` (new class).

---

## Handoff Notes to Next Phase

All hardware input classes route through one directional contract; final cleanup can document the full input matrix.

---

## Rollback Plan

Revert phase commit(s) - adapters are additive per device class. No data migration.
