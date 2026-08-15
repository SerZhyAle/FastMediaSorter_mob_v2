# Phase 03 — browse-settings-pickers

**Strategic spec:** [`../S0196_activity-render-priority-research.md`](../S0196_activity-render-priority-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Audit list, settings, dialog, and picker hosts and document what blocks first meaningful content outside the player family.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/S0196/01_surface_matrix.md` defines the non-player targets.
- [ ] Working tree is clean or on a feature branch.
- [ ] No production code changes are planned inside this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0196/03_browse_settings_pickers.md` | New | ≤ 360 |
| `temp/S0196/03_dialog_entrypoints.md` | New | ≤ 220 |
| `temp/S0196/03_cloud_pickers.md` | New | ≤ 240 |

---

## Steps

### Step 03.1 — Audit `BrowseActivity` and initializer order

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`, `app_v2/src/main/res/layout/activity_browse.xml`, `temp/S0196/03_browse_settings_pickers.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `temp/S0196/03_browse_settings_pickers.md` and document the `BrowseActivity` path from `onCreate` through initializer setup. Record launcher registration, capture-manager restoration, initializer construction, and the first list-ready indicator that corresponds to visible content.

**Verification:**

- `Glob` — `temp/S0196/03_browse_settings_pickers.md` exists.
- `Grep` — `BrowseActivity` present in that file.
- `Grep` — `BrowseManagerInitializer` present in that file.
- `Grep` — `first list item` present in that file.
- `Grep` — `launcher registration` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. Files: temp/S0196/03_browse_settings_pickers.md §1 (BrowseActivity audit). Dev log recorded.

---

### Step 03.2 — Audit `SettingsActivity` first-page path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsPagerAdapter.kt`, `app_v2/src/main/res/layout/activity_settings.xml`, `temp/S0196/03_browse_settings_pickers.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Append the settings host section to `temp/S0196/03_browse_settings_pickers.md`. Record toolbar and inset preparation, pager adapter creation, fragment selection path, tab restoration, and any work that can delay the first visible settings page.

**Verification:**

- `Grep` — `SettingsActivity` present in `temp/S0196/03_browse_settings_pickers.md`.
- `Grep` — `SettingsPagerAdapter` present in `temp/S0196/03_browse_settings_pickers.md`.
- `Grep` — `GeneralSettingsFragment` present in `temp/S0196/03_browse_settings_pickers.md`.
- `Grep` — `first visible page` present in `temp/S0196/03_browse_settings_pickers.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: temp/S0196/03_browse_settings_pickers.md §2 (SettingsActivity audit appended). Dev log recorded.

---

### Step 03.3 — Audit resource-type dialog entrypoint and reachability

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourceTypeSelectorDialog.kt`, `app_v2/src/main/res/layout/dialog_resource_type_selector.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`, `app_v2/src/main/res/layout/activity_add_resource.xml`, `temp/S0196/03_dialog_entrypoints.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `temp/S0196/03_dialog_entrypoints.md`. Confirm whether `ResourceTypeSelectorDialog` has a live caller, identify the host path if it does, and record whether the bottom sheet is active UI or dead-path code that should be excluded from measurement.

**Verification:**

- `Glob` — `temp/S0196/03_dialog_entrypoints.md` exists.
- `Grep` — `ResourceTypeSelectorDialog` present in that file.
- `Grep` — `live caller:` present in that file.
- `Grep` — `measurement verdict` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: temp/S0196/03_dialog_entrypoints.md (+49 LOC). ResourceTypeSelectorDialog confirmed as dead-path code (3 self-references, zero external callers); dropped from Phase 04 measurement set. Dev log recorded.

---

### Step 03.4 — Audit cloud folder picker hosts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt`, `app_v2/src/main/res/layout/activity_google_drive_folder_picker.xml`, `app_v2/src/main/res/layout/activity_dropbox_folder_picker.xml`, `app_v2/src/main/res/layout/activity_onedrive_folder_picker.xml`, `temp/S0196/03_cloud_pickers.md`
**Depends on:** Step 03.3

**Prompt for developer:**

> Create `temp/S0196/03_cloud_pickers.md`. Compare the three cloud picker hosts for initial load order, first selectable item, empty or loading shell exposure, toolbar and back wiring, and any re-auth path that can pre-empt first useful content.

**Verification:**

- `Glob` — `temp/S0196/03_cloud_pickers.md` exists.
- `Grep` — `GoogleDriveFolderPickerActivity` present in that file.
- `Grep` — `DropboxFolderPickerActivity` present in that file.
- `Grep` — `OneDriveFolderPickerActivity` present in that file.
- `Grep` — `first selectable item` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. Files: temp/S0196/03_cloud_pickers.md (+103 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `temp/S0196/03_browse_settings_pickers.md` resolves the static-audit part of strategic §6.5 and §6.6.
- [x] `temp/S0196/03_dialog_entrypoints.md` records whether `ResourceTypeSelectorDialog` is in or out of measurement scope.
- [x] `temp/S0196/03_cloud_pickers.md` compares all three picker hosts on one scale.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 04 must measure only hosts confirmed as live in Step 03.3. If the dialog is dead-path code, Phase 04 records it as excluded rather than inventing a synthetic scenario.

---

## Rollback Plan

Delete `temp/S0196/03_*` files — no production code or persisted app data changed.