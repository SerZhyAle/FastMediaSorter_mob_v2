# Phase 04 - Manager Wiring

**Strategic spec:** [`../S0331_calculator-memory-and-modulo.md`](../S0331_calculator-memory-and-modulo.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** Trilingual strings (mod, 4 memory descriptions, toggle, indicator format) - audit 48/48. Memory buttons bound; mod added to Function submenu; indicator rendered (visible only when memory ≠ 0); collapsible row with persisted expanded flag; memory value + row state loaded on open and persisted on change. mod-by-zero reuses DIVISION_BY_ZERO render branch. Main build PASS.

---

## Objective

Wire the memory buttons, the collapse toggle, the memory indicator, the persisted memory + row state, and the `mod` Function submenu item into `CalculatorInputManager`, with trilingual strings.

---

## Prerequisites

- [ ] Phases 01, 02, 03 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt` | Modified | ≤ 440 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 04.1 - Trilingual strings for mod, memory buttons, toggle

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `calculator_fn_mod` (label "mod" - same glyph all locales), `contentDescription` strings for the four memory buttons (`calculator_action_memory_add` "Memory add" / RU / UK, `_memory_subtract`, `_memory_recall`, `_memory_clear`) and `calculator_action_memory_toggle` ("Show/hide memory keys" / RU / UK), and `calculator_memory_indicator` as a format string with the memory value placeholder (e.g. `"M %1$s"`). Apply `docs/COMMUNICATION_POLICY.md` §2/§6 to any user-facing phrasing. Place keys next to the existing `calculator_*` block in each file.

**Verification:**

- `Grep` - `calculator_fn_mod`, `calculator_action_memory_toggle`, `calculator_memory_indicator` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "calculator_"` - exit 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

---

### Step 04.2 - Bind memory buttons and the mod menu item

**Files:** `app_v2/.../helpers/CalculatorInputManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `bind()` set click listeners: M+ → `update { memoryAdd() }`, M− → `update { memorySubtract() }`, MR → `update { memoryRecall() }`, MC → `update { memoryClear() }`. Add a `mod` item to the existing "Функция" submenu (id + `R.string.calculator_fn_mod`) dispatched via `update { inputOperator("mod") }`. After each memory mutation, persist the memory value to `CalculatorMemoryStore` off the main thread and refresh the indicator.

**Verification:**

- `Grep` - `memoryAdd()`, `memoryRecall()`, `memoryClear()` referenced in `CalculatorInputManager.kt`.
- `Grep` - `calculator_fn_mod` referenced in `CalculatorInputManager.kt`.

**Status:** `[x] done`

---

### Step 04.3 - Memory indicator + collapsible row with persisted state

**Files:** `app_v2/.../helpers/CalculatorInputManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Render the memory indicator (`calculatorMemoryIndicator`): visible with `R.string.calculator_memory_indicator` formatted with the memory value when memory ≠ 0, `gone` otherwise - update it inside `render()`. Wire `btnCalculatorMemoryToggle` to flip `calculatorMemoryRow` visibility, persist the new expanded flag via `CalculatorMemoryStore`, and update the toggle's state/contentDescription. On `bind()`, load the persisted memory value (→ `engine.restoreMemory`) and the persisted row-expanded flag off the main thread, then apply both on the main thread (default collapsed when nothing persisted).

**Verification:**

- `Grep` - `calculatorMemoryIndicator` and `calculatorMemoryToggle` (or `btnCalculatorMemoryToggle`) referenced in `CalculatorInputManager.kt`.
- `Grep` - `restoreMemory` referenced in `CalculatorInputManager.kt`.
- `Grep -n "Log\.d\("` - zero hits in `CalculatorInputManager.kt`.

**Status:** `[x] done`

---

### Step 04.4 - Render mod error + compile gate

**Files:** `app_v2/.../helpers/CalculatorInputManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Confirm `render()` already maps `DIVISION_BY_ZERO` (mod-by-zero reuses it) and `MATH_DOMAIN` to their messages - no new branch needed unless a new error type was introduced. Ensure no business logic leaked into the Activity. Build the project.

**Verification:**

- `Grep` - no new `when` arm gaps (project compiles).
- Project compiles - run `/build`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `check_strings_localized.ps1 -KeyPrefix "calculator_"` exit 0.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Memory, mod, indicator, and collapsible row are fully wired and persisted. Final phase handles docs/catalog/changelog.

---

## Rollback Plan

Revert phase commit(s) - listeners, menu item, and string keys only; persisted prefs file is additive.
