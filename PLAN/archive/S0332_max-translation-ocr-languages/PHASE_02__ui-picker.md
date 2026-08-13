# Phase 02 - ui-picker

**Strategic spec:** [`../S0332_max-translation-ocr-languages.md`](../S0332_max-translation-ocr-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Create the custom layout and `DialogFragment` implementation for the new Searchable Language Picker Dialog, including visible capability labels.

---

## Prerequisites

- [ ] Phase 01 foundations is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_searchable_language_picker.xml` | New | ≤ 100 |
| `app_v2/src/main/res/layout/item_searchable_language.xml` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableLanguagePickerDialog.kt` | New | ≤ 250 |

---

## Steps

### Step 02.1 - Create Dialog Layout and Item Layout XML

**Files:** `app_v2/src/main/res/layout/dialog_searchable_language_picker.xml`, `app_v2/src/main/res/layout/item_searchable_language.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create XML layouts for the searchable language picker:
> 1. `dialog_searchable_language_picker.xml` containing a search `EditText` at the top (with a search hint) and a `RecyclerView` below it for listing the languages. Use modern card design and padding.
> 2. `item_searchable_language.xml` representing a single row in the language picker: a text view for the flag emoji, a main text view for the formatted language name, and a container style indicating the selection state.
> 3. Verify that these layouts render nicely and follow modern UI styling guidelines.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout/dialog_searchable_language_picker.xml` exists.
- `Glob` - `app_v2/src/main/res/layout/item_searchable_language.xml` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 2/2 PASS. Expected: dialog layout exists = True, item layout exists = True. Actual: True / True. Item row includes a second line for language capability labels. Landscape counterparts expected: absent for dialog item layouts; actual: absent. Dev log recorded.

---

### Step 02.2 - Create SearchableLanguagePickerDialog class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableLanguagePickerDialog.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `SearchableLanguagePickerDialog.kt` extending `androidx.fragment.app.DialogFragment` (or building a custom AlertDialog):
> 1. It should take arguments: currently selected language code, source vs target mode (to configure title and "Auto-detect" item presence), and a callback for selection.
> 2. Initialize the adapter with languages retrieved from `TranslationLanguageCatalog`.
> 3. Bind a text watcher to the search `EditText`. Filtering should update the adapter list in real-time, performing a case-insensitive search matching query prefix or content against both the localized name, native name, and language code.
> 4. Ensure proper focus, keyboard visibility, D-pad, and accessibility support for the list items.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableLanguagePickerDialog.kt` exists.
- `Grep` - `class SearchableLanguagePickerDialog` matches exactly once.
- `Grep` - `fun filter` or similar filtering mechanism present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. Expected: dialog class count = 1, filter function present. Actual: 1 / present. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableLanguagePickerDialog.kt` (+234 LOC). Source picker shows translation/basic OCR/quality OCR capability labels and noLegal OCR only in noLegal flavor. Dev log recorded; catalog sync PASS.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles successfully - `.\gradlew.bat :app_v2:assembleStandardDebug "-Pchaquopy.enabled=false"` exit 0.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Catalog sync completed via `catalog_sync.ps1`.

---

## Handoff Notes to Next Phase

Searchable language picker dialog is fully implemented and styled with capability labels. Ready for integration across settings, player, and Camera OCR.

---

## Rollback Plan

Revert UI picker files. No database schema or business logic affected.
