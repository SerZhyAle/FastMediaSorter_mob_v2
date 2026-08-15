# Phase 01 - Settings group split (layout)

**Strategic spec:** [`../S0364_settings-interface-group-split.md`](../S0364_settings-interface-group-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Split the single "Interface" settings card into two collapsible cards - general interface settings and file-browser interface settings - in both portrait and landscape layouts, preserving every existing row and its view id.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 720 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 720 |

> Landscape parity MANDATORY: both files edited in this phase, identical structural change.
> Each file is already large; no backup needed (XML, not >1500 LOC source). Keep diffs structural - move existing blocks, do not rewrite rows.

---

## Steps

### Step 01.1 - Add file-browser card and move browser rows (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the portrait layout, keep the existing Interface `MaterialCardView` (`headerInterface` / `containerInterface`) for GENERAL rows only: language (`layoutLanguageSelection`), color theme (`layoutColorThemeSelection`), device profile (`rowDeviceProfile`), small controls (`rowSmallControls`), compact elements (`rowCompactElements`), enable favorites (`rowEnableFavorites`), allow separate window (`rowAllowSeparateWindow`). Add a second `MaterialCardView` immediately after it with a new `CollapsibleSectionHeader` `@+id/headerFileBrowser` (`app:csh_title="@string/settings_category_file_browser"`) and a new container `@+id/containerFileBrowser`, then MOVE these BROWSER rows into it unchanged (same ids): all files (`layoutAllFiles`/`rowAllFiles`), show hidden files (`layoutShowHiddenFiles`/`rowShowHiddenFiles`), show subfolders (`layoutShowSubfoldersAsItems`/`rowShowSubfoldersAsItems`), icon size + default grid mode row (`tilIconSize`/`etIconSize`/`iconHelpGridSize`/`rowDefaultGridMode`), hide grid action buttons (`rowHideGridActionButtons`), file ops in overflow (`rowFileOpsInOverflowMenu`), resource ops in overflow (`layoutResourceOpsInOverflowMenu`/`rowResourceOpsInOverflowMenu`). Preserve all `app:str_*` attributes, help icons, and the two-column row groupings. Do not delete or rename any view id.

**Verification:**

- `Grep` - `headerFileBrowser` matches exactly once in the portrait file.
- `Grep` - `containerFileBrowser` matches exactly once in the portrait file.
- `Grep` - each id `rowAllFiles`, `rowShowHiddenFiles`, `rowShowSubfoldersAsItems`, `rowDefaultGridMode`, `rowHideGridActionButtons`, `rowFileOpsInOverflowMenu`, `rowResourceOpsInOverflowMenu`, `rowSmallControls`, `rowCompactElements`, `rowEnableFavorites`, `rowAllowSeparateWindow` still matches exactly once.
- `Grep` - `csh_title="@string/settings_category_file_browser"` present once.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. headerFileBrowser/containerFileBrowser/csh_title each ×1; 11 moved/kept ids each ×1 (expected 11 | actual 11). Files: layout/fragment_settings_general.xml. Browser rows moved to new card; subfolders split out of mixed two-col; general two-col is compact-elements + small-controls. Dev log recorded.

---

### Step 01.2 - Mirror the split in landscape

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Apply the identical structural split to the landscape variant: same `headerFileBrowser` / `containerFileBrowser` card with the same browser rows moved over, same general rows remaining in the original Interface card. Match the landscape file's existing dimension references and column groupings; do not introduce portrait-only dimens.

**Verification:**

- `Grep` - `headerFileBrowser` matches exactly once in the landscape file.
- `Grep` - `containerFileBrowser` matches exactly once in the landscape file.
- `Grep` - `csh_title="@string/settings_category_file_browser"` present once in the landscape file.
- `Grep` - `rowAllFiles` and `rowDefaultGridMode` each match exactly once in the landscape file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. headerFileBrowser/containerFileBrowser/csh_title/rowAllFiles/rowDefaultGridMode each ×1 (expected 5 | actual 5). All 10 landscape row ids preserved (expected 10 | actual 10 - landscape has no rowCompactElements, pre-existing asymmetry). Browser 3-col row + subfolders + icon/grid + hide/file-ops moved to new card. Dev log recorded.

---

### Step 01.3 - Confirm binding generation

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Build the standard debug variant via `/build` so `FragmentSettingsGeneralBinding` regenerates with `headerFileBrowser` and `containerFileBrowser` fields. The new string `settings_category_file_browser` is added in Phase 03; if building before Phase 03, temporarily reference `@string/settings_category_interface` and switch in 03 - prefer ordering Phase 03 before this build. No Kotlin changes here.

**Verification:**

- `/build` standardDebug compiles (layout inflation + binding generation succeed).
- `Grep` - `Log\.d\(` returns zero hits in both layout files (XML; trivially zero).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS. `.\a.ps1 dq` standardDebug BUILD SUCCESSFUL (exit 0); FragmentSettingsGeneralBinding regenerated with headerFileBrowser/containerFileBrowser (Kotlin compile referencing them succeeded). Log.d ×0 in both layouts. Phase 03 strings landed before this build, so headers reference final @string/settings_category_file_browser.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build`. (standardDebug BUILD SUCCESSFUL)
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for both layout files via `.\scripts\add_to_dev_log.ps1`.
- [x] No public Kotlin API changed - catalog regen deferred to Phase 07.

---

## Handoff Notes to Next Phase

Two collapsible cards exist with ids `headerInterface`/`containerInterface` (general) and `headerFileBrowser`/`containerFileBrowser` (file browser). All moved rows keep their original ids, so existing binding code in the view-setup and observers helpers stays valid. Phase 02 wires the new section's expand/collapse persistence.

---

## Rollback Plan

Revert the two layout file edits - no data migration or user-facing string changed yet.
