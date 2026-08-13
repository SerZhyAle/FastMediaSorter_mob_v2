# Phase 06 - Lists group

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Apply the established focus pattern to the five list-heavy Activities: Duplicates, three cloud folder pickers (Google Drive / Dropbox / OneDrive), and the launcher widget config. Pattern is mechanically uniform; one step per screen.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] No file exceeds 1500 LOC.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_duplicates.xml` | Verification-only (hosts fragment container only) | unchanged structure |
| `app_v2/src/main/res/layout/fragment_duplicates.xml` | Modified (attrs) | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt` | Modified | ≤ 80 (current 48) |
| `app_v2/src/main/res/layout/activity_google_drive_folder_picker.xml` | Modified (attrs) | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt` | Modified | ≤ 200 (current 161) |
| `app_v2/src/main/res/layout/activity_dropbox_folder_picker.xml` | Modified (attrs) | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt` | Modified | ≤ 175 (current 139) |
| `app_v2/src/main/res/layout/activity_onedrive_folder_picker.xml` | Modified (attrs) | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt` | Modified | ≤ 175 (current 137) |
| `app_v2/src/main/res/layout/activity_resource_launch_widget_config.xml` | New (`ComposeView` host) | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt` | Modified | ≤ 240 (current 200) |

> Landscape parity (Strict Rule 12): none of these five layouts has a `layout-land/` counterpart. Document "landscape variant absent" in each step. Do not create landscape variants in this phase.

---

## Steps

> **Repeating sub-pattern (lists variant):**
> 1. In layout XML: every action button gets `android:focusable="true"`, `android:clickable="true"`, `android:background="@drawable/focus_button_background"` (layered). The `RecyclerView` itself is focusable by default - leave it. List items inherit `item_focus_selector` only if the item layout already references it; do **not** rewire item layouts in this phase.
> 2. In Activity Kotlin: override `getInitialFocusView()` to return the first list item's view if the list is non-empty, else the back/cancel button. Use `recyclerView.findViewHolderForAdapterPosition(0)?.itemView` with null-safety.
> 3. Add `Timber.d("S0289: <screen> initial-focus")`.

---

### Step 06.1 - DuplicatesActivity

**Files:** `app_v2/src/main/res/layout/activity_duplicates.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Apply the repeating sub-pattern (top of file). Landscape variant absent.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `DuplicatesActivity.kt`.
- `Grep` - `Timber.d("S0289: duplicates initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 2/2 PASS. Files: DuplicatesActivity.kt (+15 LOC), fragment_duplicates.xml (+12 attrs). Activity-level initial focus now resolves the first duplicate row when present and otherwise falls back to the scan controls hosted in the fragment. Dev-log entries recorded via `post-change.ps1`.

---

### Step 06.2 - GoogleDriveFolderPickerActivity

**Files:** `app_v2/src/main/res/layout/activity_google_drive_folder_picker.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Apply the repeating sub-pattern. Note: this Activity already has `getInitialFocusView()` per S0230 - verify the target and adjust if needed.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `GoogleDriveFolderPickerActivity.kt`.
- `Grep` - `Timber.d("S0289: gdrive-picker initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 2/2 PASS. Files: GoogleDriveFolderPickerActivity.kt (+17 LOC), activity_google_drive_folder_picker.xml (+11 attrs). Picker now resolves first-item focus after list bind and falls back to toolbar-navigation / checkbox controls while loading. Dev-log entries recorded via `post-change.ps1`.

---

### Step 06.3 - DropboxFolderPickerActivity

**Files:** `app_v2/src/main/res/layout/activity_dropbox_folder_picker.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Apply the repeating sub-pattern. Existing `getInitialFocusView()` from S0230 - verify and adjust.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `DropboxFolderPickerActivity.kt`.
- `Grep` - `Timber.d("S0289: dropbox-picker initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 2/2 PASS. Files: DropboxFolderPickerActivity.kt (+17 LOC), activity_dropbox_folder_picker.xml (+11 attrs). Picker now re-requests focus onto the first folder row after bind and keeps a deterministic checkbox ↔ list chain while loading. Dev-log entries recorded via `post-change.ps1`.

---

### Step 06.4 - OneDriveFolderPickerActivity

**Files:** `app_v2/src/main/res/layout/activity_onedrive_folder_picker.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> Apply the repeating sub-pattern. Existing `getInitialFocusView()` from S0230 - verify and adjust.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `OneDriveFolderPickerActivity.kt`.
- `Grep` - `Timber.d("S0289: onedrive-picker initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 2/2 PASS. Files: OneDriveFolderPickerActivity.kt (+17 LOC), activity_onedrive_folder_picker.xml (+11 attrs). Picker now upgrades initial focus from the list container to the first concrete row as soon as the adapter binds data. Dev-log entries recorded via `post-change.ps1`.

---

### Step 06.5 - ResourceLaunchWidgetConfigActivity

**Files:** layout TBD (read `ResourceLaunchWidgetConfigActivity.kt` to find the `setContentView` / `binding` reference for the actual layout id), `app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt`
**Depends on:** Step 06.4

**Prompt for developer:**

> 1. Read `ResourceLaunchWidgetConfigActivity.kt` and identify the layout id used. Apply the repeating sub-pattern to that layout file.
> 2. Override `getInitialFocusView()` to return the first focusable element of the resource-selection list.
> 3. Insert `Timber.d("S0289: widget-config initial-focus")`.
> 4. Build: `.\a.ps1 bd` exits `0` after the full Phase 06.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `ResourceLaunchWidgetConfigActivity.kt`.
- `Grep` - `Timber.d("S0289: widget-config initial-focus` matches exactly once.
- Build: `.\a.ps1 bd` exits `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS. Files: ResourceLaunchWidgetConfigActivity.kt (+48 LOC), activity_resource_launch_widget_config.xml (new). The Compose-only screen was wrapped in a `BaseActivity` + `ComposeView` host so the shared initial-focus hook can participate; Compose then transfers focus to the first resource card or the cancel button. Build: `.\a.ps1 bd` → PASS, `.\a.ps1 nd` → PASS.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 bd` and `.\a.ps1 nd` exited `0` on 2026-05-22.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entries added.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- All 15 in-scope Activities now have:
  - layout-level focus attrs (`focusable` + `focus_button_background` + `nextFocus*` where applicable),
  - an Activity-level `getInitialFocusView()` override (or confirmed pre-existing S0230 override),
  - a `Timber.d("S0289: …")` probe (per BlockNeedUserTest invariant).
- ReceiveShareActivity remains explicitly out-of-scope (transparent, no UI) - documented in strategic §3.2 + §2.7.

---

## Rollback Plan

Revert phase commit(s). Attribute-only changes; trivial textual revert.
