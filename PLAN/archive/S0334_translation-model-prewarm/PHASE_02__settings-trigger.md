# Phase 02 - Settings trigger

**Strategic spec:** [`../S0334_translation-model-prewarm.md`](../S0334_translation-model-prewarm.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Drive the prewarm role from the settings layer: trigger `prewarm(...)` when the target translation language changes, or when translation is enabled while a target language is already set. Guard against duplicate triggers for the same language. Expose the prewarm status flow for the UI.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] `PrewarmTranslationModelUseCase` is injectable.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 500 |

> If `SettingsViewModel.kt` exceeds 500 lines after edit, create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 02.1 - Inject use case and expose status

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `PrewarmTranslationModelUseCase` to the `SettingsViewModel` constructor injection. Re-expose its `StateFlow<TranslationModelPrewarmStatus>` as a public read-only flow on the ViewModel so the fragment can collect it. Add a public `fun retryTranslationModelPrewarm()` that re-invokes `prewarm(...)` with the current target language (used by the UI retry action in Phase 03).

**Verification:**

- `Grep` - `PrewarmTranslationModelUseCase` present in `SettingsViewModel.kt`.
- `Grep` - `TranslationModelPrewarmStatus` exposed as a flow property.
- `Grep` - `fun retryTranslationModelPrewarm(` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. Files: ui/settings/SettingsViewModel.kt (+ injection, status flow, retry API). Backup: temp/SettingsViewModel.kt.20260603_105129.backup.

---

### Step 02.2 - Trigger prewarm on language change and on enable

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Observe settings within the ViewModel scope. Call `prewarm(targetCode)` when `translationTargetLanguage` changes value, and also when `enableTranslation` transitions to `true` while `translationTargetLanguage` is non-empty. Track the last code prewarm was requested for and skip a repeat request for the same code (debounce duplicate emissions). Do not block the settings save path; prewarm runs independently in the ViewModel scope.

**Verification:**

- `Grep` - `prewarm(` invoked in `SettingsViewModel.kt`.
- `Grep` - `translationTargetLanguage` referenced in the trigger logic.
- `Grep` - `enableTranslation` referenced in the trigger logic.
- `Grep -n "Log\.d\("` returns zero hits in `SettingsViewModel.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS. Files: ui/settings/SettingsViewModel.kt (settings observer + duplicate guard). `Log.d()` expected: 0 hits | actual: 0 hits. Dev log recorded; catalog sync exit 0.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\build-debug.PS1` exit 0, `assembleStandardDebug` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-02)` returns zero hits. Expected: 0 | actual: 0.
- [x] Dev log entry added for `SettingsViewModel.kt`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if the ViewModel public API changed.

---

## Handoff Notes to Next Phase

The settings ViewModel now triggers prewarm and exposes the status flow plus a retry entry point. Phase 03 renders the status and wires the retry action in the translation settings UI.

---

## Rollback Plan

Revert phase commit - single modified file, no persistent state changed.
