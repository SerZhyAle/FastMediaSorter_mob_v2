# Phase 02 - Searchable picker

**Strategic spec:** [`../S0580_streams-filter-category-language.md`](../S0580_streams-filter-category-language.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase (independent of Phase 01)
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Introduce a reusable type-to-filter single-choice picker dialog that takes a list of options with an optional flag glyph (strategic ADR-1), plus a mapper turning catalog language names into flag-bearing options via the existing translator flag rendering. No streams-screen wiring yet.

---

## Prerequisites

- [ ] Read `ui/dialog/SearchableLanguagePickerDialog.kt` (pattern to generalize), `ui/player/helpers/LanguageFlagFormatter.kt`, `ui/player/helpers/TranslationLanguageCatalog.kt` (`findLanguage`, `LanguageItem`).
- [ ] Read `ui/dialog/DialogKeyboardDelegate` usage for Esc/focus parity.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableOptionPickerDialog.kt` | New | ≤ 230 |
| `app_v2/src/main/res/layout/dialog_searchable_option_picker.xml` | New | ≤ 60 |
| `app_v2/src/main/res/layout/item_searchable_option.xml` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamLanguageOptionMapper.kt` | New | ≤ 110 |

> No landscape counterparts: these are dialog/item layouts (not orientation-specific activity layouts) - landscape variant not needed.

---

## Steps

### Step 02.1 - Generic picker option model and dialog layout

**Files:** `app_v2/src/main/res/layout/dialog_searchable_option_picker.xml`, `app_v2/src/main/res/layout/item_searchable_option.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `dialog_searchable_option_picker.xml`: a vertical container with a single-line search `TextInputEditText` (or `EditText`) on top and a `RecyclerView` below, mirroring `dialog_searchable_language_picker.xml` but without translator-specific views. Create `item_searchable_option.xml`: a row with an optional leading flag `TextView` and a primary label `TextView`; the row is `focusable`, `clickable`, with a minimum 48dp touch target. Use `?attr/` / `@color/` references only - no hardcoded hex. No capability-label view.

**Verification:**

- `Glob` - both `dialog_searchable_option_picker.xml` and `item_searchable_option.xml` exist.
- `Grep` - no `="#` hardcoded color literal in either file.
- `Grep` - `RecyclerView` present in `dialog_searchable_option_picker.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Created `dialog_searchable_option_picker.xml` (search field + RecyclerView + gone empty-state) and `item_searchable_option.xml` (flag TextView + label, focusable/clickable). No hex literals; `?attr`/`@color`/`@dimen` only. Files: 2 new layouts.

---

### Step 02.2 - SearchableOptionPickerDialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableOptionPickerDialog.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `SearchableOptionPickerDialog : DialogFragment` (no `@AndroidEntryPoint` - it needs no injection). Define a public `data class Option(val id: String, val label: String, val flag: LanguageItem? = null)`. Provide `newInstance(title: String, options: List<Option>, selectedId: String?, onPicked: (Option?) -> Unit)` - include a leading "All / reset" choice that returns `null`. The RecyclerView filters as the user types: match is case-insensitive `startsWith` OR `contains` over `label` and `id` (mirror `SearchableLanguagePickerDialog`'s `matches`). When `Option.flag` is non-null, render the glyph via `LanguageFlagFormatter.applyFlagGlyph`; otherwise hide the flag view. Apply `DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})` for Esc/focus; request focus + show IME on the search field in `post {}`. Set each row's `contentDescription` to its label (plus selected suffix when selected). Pass options/selection/title through arguments + a retained callback field, like the existing picker.

**Verification:**

- `Glob` - `SearchableOptionPickerDialog.kt` exists.
- `Grep` - `data class Option(` matches once.
- `Grep` - `fun newInstance(` present with an `onPicked` parameter.
- `Grep` - `applyFlagGlyph` referenced (flag reuse).
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 5/5 PASS. Created `SearchableOptionPickerDialog` with `data class Option`, `newInstance(title, options, selectedId, onPicked)`, leading All/reset row -> null, startsWith||contains filter over label+id, flag via `applyFlagGlyph`, empty-state toggle, DialogKeyboardDelegate + IME focus. options/onPicked retained (LanguageItem not Parcelable); title/selectedId in args. Files: SearchableOptionPickerDialog.kt.

---

### Step 02.3 - Stream language option mapper (name -> flag)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamLanguageOptionMapper.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `StreamLanguageOptionMapper` (object or `@Inject`-free class) that converts a list of catalog language names (lowercase English, from `StreamsFacets.languages`) into `SearchableOptionPickerDialog.Option`s. Build a reverse index ONCE from `TranslationLanguageCatalog.supportedCodes`: for each code, `Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).lowercase()` -> code. For a given language name, look up the code, then `TranslationLanguageCatalog.findLanguage(code)` to obtain a `LanguageItem` for the flag; the option `id` is the original lowercase name (so it matches `StreamsFilter.language`), the `label` is the display-cased name. Names with no match (e.g. `brazilian portuguese`, `sanskrit`, `tagalog`) get `flag = null` (plain text) - graceful degradation per strategic §3.2. Also expose a helper to map plain category strings to flag-less `Option`s.

**Verification:**

- `Glob` - `StreamLanguageOptionMapper.kt` exists.
- `Grep` - `findLanguage` referenced.
- `Grep` - `getDisplayLanguage` referenced (name -> code reverse index).

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Created `StreamLanguageOptionMapper` (object): `languageOptions` (name->code reverse index over supportedCodes -> findLanguage flag, lowercase id, display-cased label, null flag for unknown), `categoryOptions` (flag-less, verbatim id). Files: StreamLanguageOptionMapper.kt.

---

### Step 02.4 - Compile check

**Files:** (no new file) - compile the new components.
**Depends on:** Step 02.3

**Prompt for developer:**

> Ensure the new dialog, layouts, and mapper compile against the standard flavor. No wiring into `StreamsActivity` yet (Phase 04).

**Verification:**

- Build: `.\a.ps1 fk` (Kotlin compile) - succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 1/1 PASS. `check-standard-fast.ps1 -Mode Code` -> BUILD SUCCESSFUL (compileStandardDebugKotlin; new ViewBinding classes generated). No StreamsActivity wiring yet (Phase 04).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Code` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added (post-change.ps1, ChangeType Mixed).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (catalog_sync via post-change.ps1).

---

## Handoff Notes to Next Phase

- `SearchableOptionPickerDialog.newInstance(title, options, selectedId, onPicked)` is the picker Phase 04 shows for both category and language.
- `StreamLanguageOptionMapper` turns `StreamsFacets.languages` into flag-bearing options and category strings into plain options.

---

## Rollback Plan

Revert the phase commit(s) - all files are new and unreferenced until Phase 04; no user-facing surface changes.
