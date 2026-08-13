# Phase 03 - Layout & Styles

**Strategic spec:** [`../S0331_calculator-memory-and-modulo.md`](../S0331_calculator-memory-and-modulo.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (resources only; safe alongside engine phases)
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** OperatorButton (?attr/colorOnSurface), PlusButton (red + larger), MemoryButton styles; dimens + red color added. Portrait + landscape: memory indicator above display, collapsible memory row above keypad (default gone), corner toggle, operator/plus style swaps. Same ids in both orientations. Main build PASS.

---

## Objective

Add the collapsible memory row (`M+ M− MR MC` + a corner toggle), the memory indicator text above the display, and operator-button styling (theme color for `− × ÷ %`, red + larger `+`) - in both portrait and landscape, with all ids/styles preserved.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/themes.xml` | Modified | - |
| `app_v2/src/main/res/values/dimens.xml` | Modified | - |
| `app_v2/src/main/res/values/colors.xml` | Modified | - |
| `app_v2/src/main/res/layout/activity_calculator.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/activity_calculator.xml` | Modified | - |

> Landscape counterpart exists and is edited in the same phase (CLAUDE.md Rule 12).

---

## Steps

### Step 03.1 - Add operator/plus button styles, plus-text dimen, operator color

**Files:** `values/themes.xml`, `values/dimens.xml`, `values/colors.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add style `Widget.FastMediaSorter.Calculator.OperatorButton` (parent `Widget.FastMediaSorter.Calculator.Button`) setting `android:textColor="?attr/colorOnSurface"` (black on light theme, white on dark - theme-driven, no hardcode). Add style `Widget.FastMediaSorter.Calculator.PlusButton` (same parent) setting `android:textColor` to a new red color and `android:textSize` to a new larger dimen. Add `calculator_plus_button_text_size` to `dimens.xml` (larger than `calculator_button_text_size`). Add a `calculator_plus_button_text` red color to `colors.xml` (or reuse an existing red such as `delete_button` if it reads as red - confirm value first).

**Verification:**

- `Grep` - `Widget.FastMediaSorter.Calculator.OperatorButton` and `Widget.FastMediaSorter.Calculator.PlusButton` present in `themes.xml`.
- `Grep` - `calculator_plus_button_text_size` present in `dimens.xml`.
- `Grep` - the red color key present in `colors.xml`.

**Status:** `[x] done`

---

### Step 03.2 - Portrait: memory row + toggle + indicator + operator styles applied

**Files:** `app_v2/src/main/res/layout/activity_calculator.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Above the display/history block add a small memory indicator `TextView` (`@+id/calculatorMemoryIndicator`, secondary/different color, small text size, `visibility="gone"` by default, text prefixed with "M"). Add a memory row container (`@+id/calculatorMemoryRow`) holding four `MaterialButton`s `@+id/btnCalculatorMemoryAdd` (M+), `btnCalculatorMemorySubtract` (M−), `btnCalculatorMemoryRecall` (MR), `btnCalculatorMemoryClear` (MC), placed directly above the `calculatorGrid` (i.e. above the number keys); default `visibility="gone"` (collapsed). Add a small toggle button `@+id/btnCalculatorMemoryToggle` in a corner (e.g. top-end of the keypad area) to show/hide the memory row, `focusable`/`clickable` true with `contentDescription`. Change the `style` of `btnCalculatorSubtract`, `btnCalculatorMultiply`, `btnCalculatorDivide`, `btnCalculatorPercent` to `Widget.FastMediaSorter.Calculator.OperatorButton`, and `btnCalculatorAdd` to `Widget.FastMediaSorter.Calculator.PlusButton`. Do not change digit/`=`/`⌫`/`≡`/`+/−`/decimal styles or any ids.

**Verification:**

- `Grep` - `calculatorMemoryRow`, `btnCalculatorMemoryAdd`, `btnCalculatorMemoryRecall`, `btnCalculatorMemoryToggle`, `calculatorMemoryIndicator` present in `layout/activity_calculator.xml`.
- `Grep` - `Calculator.OperatorButton` and `Calculator.PlusButton` referenced in `layout/activity_calculator.xml`.

**Status:** `[x] done`

---

### Step 03.3 - Landscape parity

**Files:** `app_v2/src/main/res/layout-land/activity_calculator.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Apply the equivalent additions to the landscape layout: the memory indicator above the display, the collapsible memory row above the keypad grid (default collapsed), the corner toggle button, and the same operator/plus style swaps. Use the same ids as portrait so the manager binds once. Preserve all existing ids and the horizontal split structure.

**Verification:**

- `Grep` - `calculatorMemoryRow`, `btnCalculatorMemoryToggle`, `calculatorMemoryIndicator` present in `layout-land/activity_calculator.xml`.
- `Grep` - `Calculator.OperatorButton` and `Calculator.PlusButton` referenced in `layout-land/activity_calculator.xml`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Both `layout/` and `layout-land/` edited (no portrait-only change).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Views and styles exist (memory row collapsed by default, indicator gone by default, operators restyled). Phase 04 binds the new views to the engine and persistence.

---

## Rollback Plan

Revert phase commit(s) - resource XML only; no code or data surface changed.
