# Phase 09 - Remaining activities multimodal

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05, Phase 06, Phase 07
**Blocks:** Phase 10
**Steps done:** 4 / 4
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Roll the shared multimodal contract out to the already-focused form/list surfaces and complete the app_v2 activity audit for any remaining in-house interactive screens.

---

## Prerequisites

- [ ] Phase 05 progress is retained.
- [ ] Phase 06 progress is retained.
- [ ] Phase 07 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 430 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 430 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsActivity.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt` | Modified | ≤ 260 |
| `dev/ACTIVITY_CATALOG/app_v2.md` | Verification-only reference | n/a |

---

## Steps

### Step 09.1 - Apply default multimodal hooks to form-heavy activities

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Adopt the shared multimodal foundation in the form-heavy screens. Keep focus-chain work from Phase 05 intact and add only the minimum surface declarations / overrides needed for wheel, context-click and back behaviour.

**Verification:**

- `Grep` - `getInitialFocusView` still matches exactly once in each touched Activity.
- `Grep` - `InputSurface` or equivalent surface declaration matches in each touched Activity.
- `Grep` - `TODO(phase-09)` returns zero hits in the touched files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS for all six form-heavy Activities. Files: SettingsActivity.kt (+3 LOC, `getMouseScrollTargetView` → `binding.viewPager`), AuthSessionsActivity.kt (+10 LOC, surface marker + `getMouseScrollTargetView` → `rvAuthSessions`), KeybindingRemapActivity.kt (+9 LOC, surface marker + `getMouseScrollTargetView` → `binding.recyclerView`), WelcomeActivity.kt (+10 LOC, surface marker + `getMouseScrollTargetView` → `binding.viewPager`). `AddResourceActivity.kt` and `ResourceEditorActivity.kt` already referenced `InputSurface` via their existing `InputHelpDialogFragment.show()` paths and required no edits. Phase 05 focus chains untouched. Dev log + catalog sync via `post-change.ps1` for each modified file.

---

### Step 09.2 - Apply default multimodal hooks to list-heavy activities

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt`
**Depends on:** Step 09.1

**Prompt for developer:**

> Roll the shared multimodal defaults out to the list-centric screens. Wheel must target the primary list container, and back/context inputs must not regress the list-item activation contract established in Phase 06.

**Verification:**

- `Grep` - `getInitialFocusView` still matches exactly once in each touched Activity.
- `Grep` - `RecyclerView|LazyColumn|composeView` still matches in each touched Activity.
- `Grep` - `TODO(phase-09)` returns zero hits in the touched files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS for all five list-heavy Activities. Files: DropboxFolderPickerActivity.kt (+3 LOC, `getMouseScrollTargetView` → `binding.rvFolders` + KDoc naming RecyclerView), OneDriveFolderPickerActivity.kt (+3 LOC, same shape), ResourceLaunchWidgetConfigActivity.kt (+10 LOC, surface marker + `getMouseScrollTargetView` → `widgetConfigComposeView`). `DuplicatesActivity.kt` and `GoogleDriveFolderPickerActivity.kt` already exposed both an `InputSurface` reference and a RecyclerView in their existing code paths and required no edits. Phase 06 focus chains untouched. Dev log + catalog sync via `post-change.ps1` for each modified file.

---

### Step 09.3 - Audit all remaining in-house app_v2 activities

**Files:** `dev/ACTIVITY_CATALOG/app_v2.md` (reference only), `PLAN/S0289_tv-keyboard-dpad-navigation/INDEX.md` (if handoff note is needed)
**Depends on:** Step 09.2

**Prompt for developer:**

> Query the activity catalog and confirm every in-house app_v2 Activity falls into one of three buckets: supported by complex-surface routing, supported by default foundation, or `n/a` because it has no interactive UI. If a real interactive Activity is found outside the current phase coverage, stop and extend the tactical plan before Phase 10.

**Verification:**

- `Grep` - `ReceiveShareActivity` is documented as `n/a` in the strategic or tactical artefacts.
- No uncovered interactive app_v2 Activity remains after the audit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 2/2 PASS. Audit via `dev/CATALOG/scripts/query.ps1 -ClassMatches *Activity` returned 18 records (incl. `BaseActivity` shared host). Coverage map:
  - **Complex surfaces (Phases 02-04, 08):** MainActivity, BrowseActivity, PlayerActivity, StandalonePlayerActivity.
  - **Default foundation (Phases 05, 06, 09):** SettingsActivity, AddResourceActivity, ResourceEditorActivity, AuthSessionsActivity, KeybindingRemapActivity, WelcomeActivity, DuplicatesActivity, GoogleDriveFolderPickerActivity, DropboxFolderPickerActivity, OneDriveFolderPickerActivity, ResourceLaunchWidgetConfigActivity.
  - **n/a:** ReceiveShareActivity (transparent share intent host - strategic §3.2 already documents this) and DiagnosticXrActivity (VR-flavor OpenXR session host whose standard-Android input surface is exit-gesture-only; OpenXR controllers are vendor-protocol and excluded by strategic Non-goals).
  - Strategic §3.2 extended on 2026-05-22 to record DiagnosticXrActivity as `n/a` alongside ReceiveShareActivity. Dev log entry added via `post-change.ps1`.

---

### Step 09.4 - Validate remaining-activity multimodal build

**Files:** build output only
**Depends on:** Step 09.3

**Prompt for developer:**

> Run the target build after the remaining activity changes. If the audit in Step 09.3 found an uncovered interactive Activity, do not build yet; extend the plan first.

**Verification:**

- Build command exits `0`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 1/1 PASS. `.\a.ps1 bd` exited 0 (`BUILD SUCCESSFUL in 48s`). Build log: `temp/build_debug_20260522_024431.log`. Audit from Step 09.3 confirmed every interactive Activity is covered before the build ran.

---

## Phase Done Criteria

- [x] Every `Step 09.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 bd` exited `0` on 2026-05-22 (`BUILD SUCCESSFUL in 48s`).
- [x] `Grep` for `TODO(phase-09)` returns zero hits in touched files.
- [x] Dev log entries added for every modified file via `post-change.ps1` (Settings, AuthSessions, KeybindingRemap, Welcome, Dropbox, OneDrive, WidgetConfig, strategic spec).
- [x] No uncovered interactive app_v2 Activity remains after the audit; ReceiveShare + DiagnosticXr are `n/a`.

---

## Handoff Notes to Next Phase

Phase 09 closes the gap between the already-focused screens and the new multimodal contract. After this phase, only final cleanup, catalog verification and `BlockNeedUserTest` preparation should remain.

---

## Rollback Plan

Revert the phase commit(s). The phase is UI-input only and does not alter persistence.