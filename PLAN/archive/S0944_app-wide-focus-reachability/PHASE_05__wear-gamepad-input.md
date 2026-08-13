# Phase 05 - Wear + gamepad input

**Strategic spec:** [`../S0944_app-wide-focus-reachability.md`](../S0944_app-wide-focus-reachability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 01
**Steps done:** 0 / 2

> **BLOCKED on research §6.2** (Wear rotary + gamepad HAT/stick routing into the directional-navigation contract). Do not implement while unchecked.

---

## Objective

Route Wear rotary/buttons and gamepad HAT/analog-stick (which arrive as `MotionEvent`, not `KeyEvent`) into the same directional focus navigation.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/<GamepadMotionAdapter>.kt` | New | ≤ 200 |
| `wear/src/main/java/.../<WearInputAdapter>.kt` | New | ≤ 200 |

---

## Steps

### Step 05.1 - Gamepad motion-to-direction adapter

**Prompt for developer:**

> Per §6.2, translate gamepad HAT/stick `MotionEvent` axes into directional focus moves routed through the same path as D-pad keys.

**Verification:**

- `Glob` - adapter exists; `/build` compiles.

**Status:** `[ ]` not done

### Step 05.2 - Wear rotary/button adapter

**Prompt for developer:**

> Route Wear rotary/side-button input into directional focus / activation on `wear` screens.

**Verification:**

- `Glob` - adapter exists under `wear/src/main`; `wear` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Steps `[x]`; `/build` (both modules) passes; dev log; `wear.jsonl` regenerated.

---

## Rollback Plan

Revert - additive per device class. No data migration.
