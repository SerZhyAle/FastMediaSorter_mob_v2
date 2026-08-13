# Phase 02 - Binding-free translation/OCR settings dialog

**Strategic spec:** [`../S0410_standalone-image-action-parity.md`](../S0410_standalone-image-action-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-13
**Completed:** 2026-06-13

---

## Objective

Lift the translation/OCR settings dialog out of the binding-coupled `TranslationButtonManager` into a binding-free helper that any host can call; `TranslationButtonManager` delegates to it with no behaviour change in the in-app player.

---

## Prerequisites

- [ ] Strategic §6.1 research item Resolved (extraction without main-player regression confirmed).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/TranslationSettingsDialog.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationButtonManager.kt` | Modified | ≤ 500 |

---

## Steps

### Step 02.1 - Extract the dialog into TranslationSettingsDialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/TranslationSettingsDialog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Move the body of `TranslationButtonManager.showTranslationSettingsDialog()` into a new binding-free helper `TranslationSettingsDialog` whose entry point takes only `context`, `lifecycleOwner`, and `settingsRepository` (plus the existing language-picker collaborators it already uses - `TranslationLanguageCatalog`, `SearchableLanguagePickerDialog`, the `dialog_translation_settings` layout). It must not reference `ActivityPlayerUnifiedBinding` or `PlayerBindingSafeViews`. Keep the `BuildConfig.ENABLE_TRANSLATION` guard. Preserve the exact dialog behaviour: source/target language pickers, swap, lens-style checkbox, font size/family spinners, OK/Cancel persistence.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/TranslationSettingsDialog.kt` exists.
- `Grep` - `dialog_translation_settings` present in the new file.
- `Grep` - `ActivityPlayerUnifiedBinding` returns zero hits in the new file.
- `Grep` - `ENABLE_TRANSLATION` present in the new file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 4/4 PASS. Extracted dialog UI + persistence to TranslationSettingsDialog (object); player-specific apply moved to onApplied hook. Fixed applyAndPersist to end on dialog.dismiss() so it is () -> Unit. Files: TranslationSettingsDialog.kt (New). Compile PASS.

---

### Step 02.2 - Delegate TranslationButtonManager.showTranslationSettingsDialog to the helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationButtonManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the inlined dialog body in `showTranslationSettingsDialog()` with a call to the new `TranslationSettingsDialog` helper, passing the manager's existing `context`, `lifecycleOwner`, `settingsRepository`. Do not change the method signature or any caller. The in-app player must behave identically.

**Verification:**

- `Grep` - `TranslationSettingsDialog` referenced in `TranslationButtonManager.kt`.
- `Grep` - inside `TranslationButtonManager.kt`, the `dialog_translation_settings` inflate is no longer duplicated (zero hits of `dialog_translation_settings` in this file, or only via the helper call).

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS. showTranslationSettingsDialog delegates to TranslationSettingsDialog.show with onApplied apply block; removed showLanguagePicker/applyLanguageLabel + deliveryEnableInterceptor field + 5 unused imports. Files: TranslationButtonManager.kt. Compile PASS.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public dialog helper).

---

## Handoff Notes to Next Phase

The translation/OCR settings dialog is now callable from any host with `context` + `lifecycleOwner` + `settingsRepository`. Phase 03 wires the standalone host's overflow item to it.

---

## Rollback Plan

Revert phase commit(s). The extraction is behaviour-preserving; reverting restores the inlined dialog in `TranslationButtonManager`. No data migration or user-facing surface changed.
