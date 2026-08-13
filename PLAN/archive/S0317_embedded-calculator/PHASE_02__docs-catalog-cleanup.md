# Phase 02 - Docs Catalog Cleanup

**Strategic spec:** [`../S0317_embedded-calculator.md`](../S0317_embedded-calculator.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** `/spec-check S0317`
**Steps done:** 3 / 3
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Close user-facing documentation, functionality log, catalog, and final validation for S0317.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Calculator code builds in `app_v2`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | any |
| `docs/FEATURES_RU.md` | Modified | any |
| `docs/FEATURES_UK.md` | Modified | any |
| `dev/FUNCTIONALITY.log` | Modified | generated |
| `dev/CATALOG/app_v2.jsonl` | Modified | generated |
| `dev/CATALOG/app_v2.md` | Modified | generated |

---

## Steps

### Step 02.1 - Update Feature Docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 01 Done

**Prompt for developer:**

> Add a concise trilingual feature entry stating that the embedded calculator can be enabled in General settings under Other functionality and launched from the main menu or the calculator widget. Check user-facing wording against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - `calculator` appears in `docs/FEATURES.md`.
- `Grep` - `Калькулятор` appears in `docs/FEATURES_RU.md`.
- `Grep` - `Калькулятор` appears in `docs/FEATURES_UK.md`.
- `Manual` - Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x] done`

Verified 2026-05-31:

- `Select-String` found `calculator` in `docs/FEATURES.md`.
- `Select-String` found `Калькулятор` in `docs/FEATURES_RU.md`.
- `Select-String` found `Калькулятор` in `docs/FEATURES_UK.md`.
- Manual wording check against `docs/COMMUNICATION_POLICY.md` §2/§6 passed.

---

### Step 02.2 - Record Functionality And Catalog

**Files:** `dev/FUNCTIONALITY.log`, `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Record S0317 in the functionality log as an added opt-in embedded calculator. Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` after Kotlin changes and keep the generated catalog files.

**Verification:**

- `Grep` - `S0317` appears in `dev/FUNCTIONALITY.log`.
- `Grep` - `CalculatorActivity` appears in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `CalculatorEngine` appears in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `CalculatorWidgetProvider` appears in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

Verified 2026-05-31:

- `Select-String` found `S0317` in `dev/FUNCTIONALITY.log`.
- `Select-String` found `CalculatorActivity` in `dev/CATALOG/app_v2.jsonl`.
- `Select-String` found `CalculatorEngine` in `dev/CATALOG/app_v2.jsonl`.
- `Select-String` found `CalculatorWidgetProvider` in `dev/CATALOG/app_v2.jsonl`.

---

### Step 02.3 - Run Final Validation

**Files:** `PLAN/S0317_embedded-calculator/INDEX.md`, `PLAN/S0317_embedded-calculator/PHASE_01__calculator-feature.md`, `PLAN/S0317_embedded-calculator/PHASE_02__docs-catalog-cleanup.md`, `PLAN/S0317_embedded-calculator.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Run final static and build validation: string localization checks, calculator unit test, `.\build-debug.PS1`, catalog grep, and no stale `TODO(phase-02)` markers. When validation passes, mark tactical phases Done and advance strategic status according to `/spec-dev`: `Implemented`, then `BlockNeedUserTest` with temporary `Timber.d("S0317: <entry-point>")` probes at changed flow entry points because calculator UI and widget launch require on-device verification.

**Verification:**

- `Command` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "calculator"` exits 0.
- `Command` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_calculator"` exits 0.
- `Command` - `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*CalculatorEngineTest"` exits 0, unless `/build` prompt selects a stricter wrapper.
- `Command` - `.\build-debug.PS1` exits 0.
- `Grep` - `TODO(phase-02)` returns zero hits.
- `Grep` - `Timber.d("S0317:` appears at least once after status flips to `BlockNeedUserTest`.

**Status:** `[x] done`

Verified 2026-05-31:

- `pwsh -NoProfile -Command '& { ./scripts/check_strings_localized.ps1 -KeyPrefix "calculator"; ./scripts/check_strings_localized.ps1 -KeyPrefix "widget_calculator"; ./scripts/check_strings_localized.ps1 -KeyPrefix "setting_calculator" }'` exited 0.
- `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*CalculatorEngineTest"` exited 0, `BUILD SUCCESSFUL in 36s`.
- `.\build-debug.PS1` exited 0, `BUILD SUCCESSFUL in 38s`; APK `app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_v2.60.5311.252-DEBUG.apk`; log `temp/build_debug_20260531_125231.log`.
- `rg -n 'TODO\(phase-02\)' app_v2` returned zero implementation hits.
- `Select-String` found `Timber.d("S0317:` probes in `MainActivity.kt` and `CalculatorActivity.kt` after status moved to `BlockNeedUserTest`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `.\build-debug.PS1`.
- [x] `Grep` for `TODO(phase-02)` returns zero implementation hits in `app_v2`.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` contains `CalculatorActivity`, `CalculatorEngine`, and `CalculatorWidgetProvider`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. On-device verification is required before `/spec-check S0317` can close the ticket as Verified.

---

## Rollback Plan

Revert the phase commit(s). Documentation and generated catalog/log entries can be removed with the same revert.
