# Phase 01 - Calculator Result Contract

**Strategic spec:** [`../S0321_text-editor-calculator-integration.md`](../S0321_text-editor-calculator-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Add a reusable calculator input/result contract without changing existing widget or main-menu launch behaviour.

---

## Prerequisites

- [ ] Working tree is clean or existing dirty files in the touched area are understood.
- [ ] Strategic §6 research items are Resolved.
- [ ] Existing `S0317` debug probes remain untouched while `S0317` is `BlockNeedUserTest`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt` | Modified | ≤ 450 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngineTest.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/CalculatorActivity.kt` | Modified | ≤ 140 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Expose calculator input parseability

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngineTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a public `canParseInput(text: String): Boolean` method to `CalculatorEngine` that reuses the existing pasted-expression parser and returns true only when at least one number can be parsed. Add unit coverage proving noisy valid input returns true and non-numeric noise returns false. Do not change existing calculation semantics.

**Verification:**

- `Grep` - `fun canParseInput(text: String): Boolean` exists exactly once in `CalculatorEngine.kt`.
- `Grep` - `canParseInput` appears in `CalculatorEngineTest.kt`.
- `Grep` - `Log.d(` returns zero hits in both modified Kotlin files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 3/3 PASS. Expected: one `canParseInput` method, test coverage references, zero `Log.d(` hits | actual: 1 method, 4 test references, 0 `Log.d(` hits. Files: `CalculatorEngine.kt`, `CalculatorEngineTest.kt`.

---

### Step 01.2 - Add initial input and returnable result API

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `applyInitialInput(text: String?)` and `currentResultOrNull(): String?` to `CalculatorInputManager`. `applyInitialInput` must ignore blank or unparsable text, use `CalculatorEngine.inputNumber()` for valid input, and render the updated display. `currentResultOrNull` must return a non-blank result only after initial input or a user action has produced a non-error value.

**Verification:**

- `Grep` - `fun applyInitialInput(text: String?)` exists exactly once in `CalculatorInputManager.kt`.
- `Grep` - `fun currentResultOrNull(): String?` exists exactly once in `CalculatorInputManager.kt`.
- `Grep` - `engine.canParseInput` appears in `CalculatorInputManager.kt`.
- `Grep` - `Log.d(` returns zero hits in `CalculatorInputManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 4/4 PASS. Expected: one `applyInitialInput` method, one `currentResultOrNull` method, `engine.canParseInput` usage, zero `Log.d(` hits | actual: 1 initial-input method, 1 result method, 1 parser guard, 0 `Log.d(` hits. File: `CalculatorInputManager.kt`.

---

### Step 01.3 - Return calculator result from Activity close paths

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/CalculatorActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Extend `CalculatorActivity.createIntent()` with optional `initialInput` and `returnResult` arguments while preserving the existing `fromWidget` default. Add extras for initial input, result mode, and result value. Apply initial input after binding the input manager. Route toolbar navigation and system Back through a single close method that sets `RESULT_OK` with the result only when result mode is enabled, calculator content is visible, and `currentResultOrNull()` is non-null; otherwise set `RESULT_CANCELED`.

**Verification:**

- `Grep` - `EXTRA_INITIAL_INPUT` exists in `CalculatorActivity.kt`.
- `Grep` - `EXTRA_RETURN_RESULT` exists in `CalculatorActivity.kt`.
- `Grep` - `EXTRA_RESULT` exists in `CalculatorActivity.kt`.
- `Grep` - `fun readResult(data: Intent?): String?` exists in `CalculatorActivity.kt`.
- `Grep` - `OnBackPressedCallback` appears in `CalculatorActivity.kt`.
- `Grep` - `Log.d(` returns zero hits in `CalculatorActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 6/6 PASS. Expected: `EXTRA_INITIAL_INPUT`, `EXTRA_RETURN_RESULT`, `EXTRA_RESULT`, `readResult`, `OnBackPressedCallback`, zero `Log.d(` hits | actual: all result-contract markers present, 0 `Log.d(` hits. File: `CalculatorActivity.kt`.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Targeted unit test passes: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.ui.calculator.helpers.CalculatorEngineTest"`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

## Phase Validation Log

- 2026-05-31 - Static checks PASS. Expected: no `TODO(phase-01)` in source | actual: zero hits in `app_v2/src/main/java` and `app_v2/src/test/java`.
- 2026-05-31 - Targeted test BLOCKED by local build intermediates. Expected: `CalculatorEngineTest` executes | actual: Gradle failed before tests because `app_v2/build` and `app_v2/build/intermediates` files were open or recreated during clean/resource processing. Retried after `gradlew --stop` and `:app_v2:clean`; final cleanup still failed on open Hilt unit-test class files.
- 2026-05-31 - Targeted test PASS after daemon reset. Expected: `CalculatorEngineTest` executes | actual: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.ui.calculator.helpers.CalculatorEngineTest"` exited 0.

---

## Handoff Notes to Next Phase

The calculator can now receive initial text and return a plain result through Activity result data.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persisted setting changes.
