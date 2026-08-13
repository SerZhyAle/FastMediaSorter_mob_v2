# Phase 03 - Function Menu

**Strategic spec:** [`../S0329_calculator-history-and-functions.md`](../S0329_calculator-history-and-functions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (engine functions must exist)
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** All 3 steps PASS. Trilingual strings (`calculator_action_function` + 13 `calculator_fn_*`) localized (audit 41/41). "Функция" submenu added to popup with full dispatch to engine ops; xʸ routes to `inputOperator("^")`. `calculator_error_math_domain` render branch was already pulled forward in Phase 01. Debug build PASS.

---

## Objective

Add a "Функция" submenu to the calculator popup menu, wire each item to the engine operations from Phase 01, and ship trilingual strings for every new label and error message.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt` | Modified | ≤ 360 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 03.1 - Add trilingual strings for functions and domain error

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the parent label `calculator_action_function` ("Function" / "Функция" / "Функція") and one string per operation: sin, cos, tg, ctg, square root, cube root, x², xʸ (power), 1/x, log₁₀, ln, n! (factorial), π. Add `calculator_error_math_domain` for the generic domain error (e.g. EN "Can't do that with this number.", RU/UK in the same friendly register). Apply `docs/COMMUNICATION_POLICY.md` §2 (message formula for errors) and §6 (tone checklist) to the error string. Keep operation labels short (they are menu items); use the math glyphs already used on buttons where natural. Place all keys next to the existing `calculator_*` block in each file.

**Verification:**

- `Grep` - `calculator_action_function` present in all three `strings.xml` files.
- `Grep` - `calculator_error_math_domain` present in all three files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "calculator_"` - exit 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

---

### Step 03.2 - Build the "Функция" submenu in the popup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `showCalculatorMenu()`, add a "Функция" entry using `menu.addSubMenu(..)` placed before "Save history"/"Clear history", and populate the sub-menu with one item per operation (sin, cos, tg, ctg, √, ∛, x², xʸ, 1/x, log₁₀, ln, n!, π) using the Step 03.1 string keys and stable item ids. Keep the existing top-level items (Copy/Paste/Round/Send result/Save history/Clear history) intact and in order.

**Verification:**

- `Grep` - `addSubMenu` present in `CalculatorInputManager.kt`.
- `Grep` - `calculator_action_function` referenced in `CalculatorInputManager.kt`.

**Status:** `[x] done`

---

### Step 03.3 - Dispatch submenu items to engine operations

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Route each sub-menu item id to the matching engine call through the existing `update { .. }` helper: sin→`sine()`, cos→`cosine()`, tg→`tangent()`, ctg→`cotangent()`, √→`squareRoot()`, ∛→`cubeRoot()`, x²→`square()`, 1/x→`reciprocal()`, log₁₀→`log10()`, ln→`naturalLog()`, n!→`factorial()`, π→`inputPi()`. For xʸ, trigger the power operator (engine `inputOperator("^")`) so the user then types the exponent and presses `=`. Extend `render()` so `CalculatorError.MATH_DOMAIN` shows `R.string.calculator_error_math_domain` (mirror the existing division-by-zero branch). Completed function results flow through the same append-to-store path from Phase 02 with no extra wiring.

**Verification:**

- `Grep` - `sine()`, `cubeRoot()`, `factorial()`, `inputPi()` all referenced in `CalculatorInputManager.kt`.
- `Grep` - `calculator_error_math_domain` referenced in `CalculatorInputManager.kt`.
- `Grep -n "Log\.d\("` - zero hits in `CalculatorInputManager.kt`.
- Project compiles - run `/build`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `check_strings_localized.ps1 -KeyPrefix "calculator_"` exit 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All scientific operations are reachable from the popup "Функция" submenu and persist through Phase 02. Phase 04 only changes spatial layout; no new wiring.

---

## Rollback Plan

Revert phase commit(s) - menu additions and string keys only; no data surface changed.
