# Phase 04 - Filter UI

**Strategic spec:** [`../S0580_streams-filter-category-language.md`](../S0580_streams-filter-category-language.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

> **GATE CLEARED (2026-06-21, `/ui-clarify`, owner):** surface = a filter dialog opened from the existing `btnFilter` hosting a category row, a language row, an AND/OR toggle, and a Clear action (strategic §6.5). Active state = a non-color marker on `btnFilter` (runtime icon swap to a tune-with-dot drawable) plus Clear in the dialog; no chip row, no on-screen `activity_streams` layout change. The icon swap applies to both orientations at runtime, so neither `activity_streams.xml` nor its land counterpart is edited.

---

## Objective

Wire the category and language filters into the Streams screen: tapping the filter control opens pickers for category and language, an AND/OR toggle chooses combine mode, and the active filter / reset is visible to the user.

---

## Prerequisites

- [ ] Phase 01, 02, 03 are ✅ Done.
- [ ] **Pre-Implementation Blocker cleared:** strategic §6.5 resolved via `/ui-clarify S0580`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 335 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsFilterDialogManager.kt` | New | ≤ 130 |
| `app_v2/src/main/res/layout/dialog_streams_filter.xml` | New | ≤ 110 |
| `app_v2/src/main/res/drawable/ic_tune_active.xml` | New | ≤ 25 |
| `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` | Modified (1 key: `streams_filter_active`) | n/a |

> **Delegation (Rule 3/5):** `StreamsActivity` is already at its size cap and must hold no dialog logic, so the filter-dialog marshalling lives in `StreamsFilterDialogManager` (mirrors the existing `StreamInlineAudioManager`); the Activity only calls `show(state, onApply)` and toggles the button marker. This refines Step 04.2 (still "rewrite the filter dialog") without putting logic in the Activity.

> **Landscape parity:** the chosen surface is a dialog + a runtime icon swap on `btnFilter`; `activity_streams.xml` is NOT touched, so its land counterpart is not touched either. No portrait-only layout edit exists in this phase.

---

## Steps

### Step 04.1 - Filter dialog layout

**Files:** `app_v2/src/main/res/layout/dialog_streams_filter.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `dialog_streams_filter.xml`: a custom dialog view with a "Category" selector row (label `streams_filter_category` + current value, opens the category picker), a "Language" selector row (label `streams_filter_language` + current value, opens the language picker), an AND/OR control (`MaterialButtonToggleGroup` or `SwitchMaterial`) captioned `streams_filter_match_mode` with options `streams_filter_match_all` / `streams_filter_match_any`, and a "Clear" action (`streams_filter_clear`). All rows focusable/clickable with 48dp targets, keyboard + D-pad + mouse usable, `?attr/`/`@color/` only (no hex). Active state distinguishable not by color alone (e.g. value text or check).

**Verification:**

- `Glob` - `dialog_streams_filter.xml` exists.
- `Grep` - `streams_filter_match_mode` and `streams_filter_clear` referenced in the layout.
- `Grep` - no `="#` hardcoded color literal in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Created `dialog_streams_filter.xml`: category row + language row (each with value text), match-mode `MaterialButtonToggleGroup` (All/Any), Clear text button. Rows focusable/clickable, 48dp via `list_item_height`, `?attr`/`@dimen` only, no hex. Active value shown as text (not color alone).

---

### Step 04.2 - Replace showFilterDialog with category+language+mode

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Put the dialog marshalling in `StreamsFilterDialogManager.show(state, onApply)` (Rule 3/5 - Activity holds no dialog logic): inflate `DialogStreamsFilterBinding`, seed the category/language value text and the toggle from `state.filter`. The category row opens `SearchableOptionPickerDialog` over `StreamLanguageOptionMapper.categoryOptions(state.facets.categories)` (flag-less); the language row opens it over `StreamLanguageOptionMapper.languageOptions(state.facets.languages)` (flag-bearing), shown via `activity.supportFragmentManager`. On any pick/toggle/clear, invoke `onApply(category, language, matchMode)`; Clear resets to `(null, null, ALL)`. In `StreamsActivity`, `showFilterDialog()` just delegates: `filterDialogManager.show(latestState) { c, l, m -> viewModel.onFilter(category = c, language = l, matchMode = m) }`. Keep `showSortDialog` untouched.

**Verification:**

- `Grep` - `SearchableOptionPickerDialog` referenced in `StreamsFilterDialogManager.kt`.
- `Grep` - `DialogStreamsFilterBinding` referenced in `StreamsFilterDialogManager.kt`.
- `Grep` - `onFilter(` call includes `matchMode` in `StreamsActivity.kt`.
- `Grep -n "Log\.d\("` - zero hits in `StreamsActivity.kt` and `StreamsFilterDialogManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. `StreamsFilterDialogManager.show(state, onApply)` hosts the dialog (DialogStreamsFilterBinding, category/language pickers via SearchableOptionPickerDialog, AND/OR toggle, Clear). `StreamsActivity.showFilterDialog` delegates to it -> `viewModel.onFilter(category, language, matchMode)`. Activity 318 LOC. Files: StreamsFilterDialogManager.kt (new), StreamsActivity.kt.

---

### Step 04.3 - Surface active filters and reset (per /ui-clarify)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`, `app_v2/src/main/res/drawable/ic_tune_active.xml`, `app_v2/src/main/res/values{,-ru,-uk}/strings.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Per the `/ui-clarify` outcome (dialog + non-color button marker): create `ic_tune_active.xml` as a `layer-list` over `@drawable/ic_tune` plus a small `oval` shape marker (themed `?attr/colorPrimary`, no hex) so the active filter is distinguishable by SHAPE, not color alone. In the state render of `StreamsActivity`, when `state.filter.category != null || state.filter.language != null`, set `btnFilter` to `ic_tune_active` and its `contentDescription` to `streams_filter_active`; otherwise `ic_tune` and `streams_filter`. Add the trilingual key `streams_filter_active` (EN/RU/UK) via `set-android-string.ps1`. The dialog "Clear" already provides one-tap reset; no on-screen chip/affordance is added. No `activity_streams` layout edit (the swap is runtime and covers both orientations).

**Verification:**

- `Glob` - `ic_tune_active.xml` exists; `Grep` - no `="#` literal in it.
- `Grep` - `ic_tune_active` referenced in `StreamsActivity.kt` (active-marker bound to `state.filter`).
- `Grep` - `streams_filter_active` present in all three `strings.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Created `ic_tune_active.xml` (layer-list: ic_tune + oval dot, ?attr/colorPrimary, no hex). `updateFilterIndicator(filter)` swaps the button icon + contentDescription when category/language active; called from the state render. Added trilingual `streams_filter_active`. Files: ic_tune_active.xml, StreamsActivity.kt, strings.xml x3.

---

### Step 04.4 - Build and smoke verify

**Files:** (no new file)
**Depends on:** Step 04.3

**Prompt for developer:**

> Build the standard debug APK and confirm the Streams filter opens, the category and language pickers filter as you type, the AND/OR toggle changes results, language-less rows remain visible under a language filter, and Clear resets. Insert one `Timber.d("S0580: <flow>")` tag at the filter-apply entry point only when the ticket transitions to `BlockNeedUserTest` (handled at spec close, not here).

**Verification:**

- Build: `.\a.ps1 d` - APK builds.
- Manual: pickers type-filter; toggle switches AND/OR; null-language rows visible under language filter; Clear resets. Record `expected | actual`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Build PASS. `check-standard-fast.ps1 -Mode Assemble` -> assembleStandardDebug BUILD SUCCESSFUL (full APK; validates Phase 04 code + new layouts/drawable/strings). Manual on-device checks (pickers type-filter, AND/OR toggle, null-language rows visible, Clear resets) deferred to the BlockNeedUserTest device test. The `Timber.d("S0580: ..")` probe is NOT inserted here - the ticket-log gate rejects it while status != BlockNeedUserTest; it is inserted at the BlockNeedUserTest finalization (then revalidated by a compile).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Assemble` assembleStandardDebug BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Landscape parity satisfied - dialog + runtime icon swap, no `activity_streams` layout change (portrait or land).
- [x] Dev log entry added (post-change.ps1, ChangeType Mixed).

---

## Handoff Notes to Next Phase

- Feature is user-complete after this phase; Phase 05 finalizes catalog/docs and capability inventory.

---

## Rollback Plan

Revert the phase commit(s); Phases 01-03 remain intact (dormant model + unused picker). No data migration involved.
