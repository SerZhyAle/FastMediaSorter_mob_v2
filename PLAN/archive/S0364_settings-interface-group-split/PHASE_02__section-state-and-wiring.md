# Phase 02 - Section state and wiring

**Strategic spec:** [`../S0364_settings-interface-group-split.md`](../S0364_settings-interface-group-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Make the new file-browser group collapse/expand with persisted state like every other settings section, and keep settings-search section expansion and general-section reset consistent with the split.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (both layouts have `headerFileBrowser` / `containerFileBrowser`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Modified | ≤ 120 |

> Search source (`LayoutSettingsSearchSource`) auto-walks layouts and emits a `SECTION_HEADER` entry for any `CollapsibleSectionHeader` with `csh_title`; the new header is picked up with no code change. Reset (`GeneralSettingsResetHelper` → `SettingsViewModel.resetGeneralSection`) operates on settings values, not on view containers; moving rows between cards does not change which values reset. No edits needed in those files - verified in Step 02.3.

---

## Steps

### Step 02.1 - Add persisted expand state for the file-browser section

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `const val KEY_FILE_BROWSER_EXPANDED = "section_file_browser_expanded"` to the companion object. In `setup()`, add an `ExpandableSection(binding.headerFileBrowser, binding.containerFileBrowser, KEY_FILE_BROWSER_EXPANDED, false)` entry to the `sections` list, placed right after the `headerInterface` entry. In `getSavedSectionStates()`, add `KEY_FILE_BROWSER_EXPANDED to prefs.getBoolean(KEY_FILE_BROWSER_EXPANDED, false)` to the returned map. Default expanded = false (collapsed), matching the interface section.

**Verification:**

- `Grep` - `KEY_FILE_BROWSER_EXPANDED` matches 3 times (declaration, setup entry, saved-states map).
- `Grep` - `binding.headerFileBrowser, binding.containerFileBrowser` present once.
- `Grep` - `Log\.d\(` returns zero hits in the file (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS. KEY_FILE_BROWSER_EXPANDED ×3 (decl + setup entry + saved-states map; expected 3 | actual 3); binding.headerFileBrowser/containerFileBrowser entry ×1; Log.d ×0. Default expanded = false (collapsed). Dev log + catalog sync recorded.

---

### Step 02.2 - Build and confirm both sections toggle independently

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Build standardDebug via `/build`. Confirm `GeneralSettingsSectionsHelper.setup()` references both `binding.containerInterface` and `binding.containerFileBrowser`, so each header toggles only its own container and writes its own pref key.

**Verification:**

- `/build` standardDebug compiles.
- `Grep` - `containerInterface` and `containerFileBrowser` both present in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS. `.\a.ps1 dq` standardDebug BUILD SUCCESSFUL (exit 0). setup() references both binding.containerInterface and binding.containerFileBrowser; each header toggles only its own container + pref key.

---

### Step 02.3 - Confirm search and reset consistency (no-change verification)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsResetHelper.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Confirm no edits are required: the search source emits a `SECTION_HEADER` for the new `headerFileBrowser` automatically (it walks the layout and keys off `csh_title`), and the general-section reset path resets settings values regardless of which card a row sits in. If on-device search-to-result must expand the containing card, verify the existing navigation logic resolves the section from the nearest preceding `CollapsibleSectionHeader`; if it relies on a hard-coded header id list, extend that list to include `headerFileBrowser` (document the file touched here).

**Verification:**

- `Grep` - `kindFromTag` maps `CollapsibleSectionHeader -> EntryKind.SECTION_HEADER` (unchanged) in `LayoutSettingsSearchSource.kt`.
- `Grep` - no hard-coded section-header id allowlist that omits `headerFileBrowser` anywhere under `ui/settings/` (if found, extend it and note the file).
- `/build` standardDebug compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS. kindFromTag maps CollapsibleSectionHeader -> EntryKind.SECTION_HEADER (LayoutSettingsSearchSource.kt:76, unchanged). No hard-coded section-header id allowlist under ui/settings/ - only GeneralSettingsSectionsHelper (now includes headerFileBrowser) and GeneralSettingsViewSetupHelper (debug visibility) reference header ids. Reset operates on settings values, not view containers - unaffected. `.\a.ps1 dq` BUILD SUCCESSFUL. No code edits required.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`. (standardDebug BUILD SUCCESSFUL)
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] Catalog regen deferred to Phase 07.

---

## Handoff Notes to Next Phase

Both interface sub-groups persist their own expand state. The new group's title string `settings_category_file_browser` is added in Phase 03; until then the header may reference a placeholder string.

---

## Rollback Plan

Revert the `GeneralSettingsSectionsHelper.kt` edit - the persisted pref key is additive and harmless if orphaned.
