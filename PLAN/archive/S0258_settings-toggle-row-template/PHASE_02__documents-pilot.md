# Phase 02 - Documents Pilot

**Strategic spec:** [`../S0258_settings-toggle-row-template.md`](../S0258_settings-toggle-row-template.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Migrate the Documents settings screen to the new row component as the first production pilot.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_documents.xml` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/DocumentsSettingsFragment.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3600 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 3600 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 3600 |

---

## Steps

### Step 02.1 - Replace ad-hoc document toggles with row components

**Files:** `app_v2/src/main/res/layout/fragment_settings_documents.xml`
**Depends on:** Phase 01

**Prompt for developer:**

> Replace the manual document switch rows with `SettingsToggleRow` instances while preserving the nested text/pdf dependent rows and existing container visibility behavior.

**Verification:**

- `Grep` - `SettingsToggleRow` appears in `fragment_settings_documents.xml`.
- `Grep` - `switchSupportText` absent from `fragment_settings_documents.xml`.
- `Grep` - `rowSupportText` present in `fragment_settings_documents.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS (5 SettingsToggleRow occurrences). Layout rewritten: 5 rows (rowSupportText, rowShowTextLineNumbers, rowSupportPdf, rowShowPdfThumbnails, rowSupportEpub). Helper for line-numbers moved into row via str_showHelp + str_helpTitle/Message. Dev log recorded.

---

### Step 02.2 - Move fragment bindings to row API

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/DocumentsSettingsFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update the documents fragment to bind and read the new row component instead of direct `SwitchMaterial` references. Preserve setting updates and dependent-row visibility exactly as before.

**Verification:**

- `Grep` - `binding.rowSupportText` present in `DocumentsSettingsFragment.kt`.
- `Grep` - `binding.switchSupportText` absent from `DocumentsSettingsFragment.kt`.
- `Grep` - `binding.rowShowTextLineNumbers` present in `DocumentsSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Fragment switched to row API (bindSwitch overload), helper for line-numbers handled inline by row, separate iconHelpLineNumbers click handler removed. Dev log recorded.

---

### Step 02.3 - Fill missing document subtitles and pilot help copy if needed

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Ensure every migrated document toggle row has a subtitle key in EN/RU/UK. If you add or rewrite user-visible copy, apply `docs/COMMUNICATION_POLICY.md` §6 and keep wording brief.

**Verification:**

- `Grep` - `setting_support_pdf_desc` present in all three locale files.
- `Grep` - `setting_support_epub_desc` present in all three locale files.
- `Grep` - `setting_show_pdf_thumbnails_desc` present in all three locale files.
- `Grep` - `Strings pass COMMUNICATION_POLICY §6 checklist` satisfied by review.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. setting_show_pdf_thumbnails_desc added to EN/RU/UK via set-android-string.ps1; check_strings_localized.ps1 -> OK 1/1. Copy is direct, concrete, short (COMMUNICATION_POLICY §6 compliant). Dev log recorded.

---

### Step 02.4 - Validate pilot screen

**Files:** `app_v2/src/main/res/layout/fragment_settings_documents.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/DocumentsSettingsFragment.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Run a target build and confirm the pilot screen is structurally ready for runtime validation.

**Verification:**

- `/build` - `standard debug` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 1/1 PASS (after 1 build retry). Initial build FAILED due to SettingsSearchIndex.kt referencing removed switchSupportText/Pdf IDs - patched both viewId refs to rowSupportText/rowSupportPdf; added SettingsSearchIndex.kt to Files Touched. Retry: BUILD SUCCESSFUL in 1m 20s (assembleStandardDebug). Version: 2.60.5191.853. Dev log recorded for SettingsSearchIndex.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] String locale parity check passes for the new document-setting keys.

---

## Handoff Notes to Next Phase

The pilot defines the migration recipe for larger settings fragments with paired portrait/landscape layouts.

---

## Rollback Plan

Revert phase commit(s) - no schema or stored-setting format changed.
