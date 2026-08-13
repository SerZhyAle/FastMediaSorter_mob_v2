# Phase 03 - Browse UX

**Strategic spec:** [`../S0302_file-manager-mode.md`](../S0302_file-manager-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 - Manifest Integration
**Blocks:** Phase 04 - Docs & Catalog Cleanup
**Steps done:** 2 / 2
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Add a visual indicator in the Browse screen showing that the resource is currently in File Manager Mode.

---

## Prerequisites

- [ ] Phase 02 completed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt` | Modified | ≤ 20 |

---

## Steps

### Step 03.1 - Add File Manager Mode Indicator in Resource Info

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Modify `BrowseUtilityManager.kt`. In `buildResourceInfo(state)` function, check if the resource is in `allFiles` mode.
> If `resource.allFiles` is `true`, append a localized "File Manager Mode" badge (`• R.string.all_files` - which translates to `"File Manager"`) to the returned status line so the user clearly sees they are in File Manager Mode.
>
> Example changes:
> ```kotlin
> val modeLabel = if (resource.allFiles) {
>     " • " + context.getString(R.string.all_files)
> } else {
>     ""
> }
> return "${resource.name}$fileCount • $pathDisplay • $sortMode$modeLabel$selected"
> ```

**Verification:**

- `Grep` - `resource.allFiles` is checked inside `buildResourceInfo` of `BrowseUtilityManager.kt`.
- `Grep` - `context.getString(R.string.all_files)` is used inside `buildResourceInfo`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. Added resource.allFiles check and context.getString(R.string.all_files) inside BrowseUtilityManager.kt to add visual indicator when in File Manager Mode. Dev logs recorded.

---

### Step 03.2 - Confirm Binary File Menu and Handoff UX

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Verify that `BrowseBinaryFileHandler.kt` correctly handles click actions for other unsupported formats (like executables, disks, etc.) by displaying the `bottom_sheet_binary_file` menu. This allows users to share, open externally with `btnOpenWith`, copy, move, rename, and delete unsupported files as part of the core File Manager contract. Ensure that the strings updated in Step 01.1 show up correctly without compile-time issues.

**Verification:**

- Project compiles successfully.
- Code audit confirms `bottom_sheet_binary_file` has options for open with and operations.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. BrowseBinaryFileHandler.kt compiles successfully and showBinaryFileMenu displays bottom_sheet_binary_file dialog for unsupported binary file formats, enabling all file management actions and external opening. Dev logs recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] Dev log entries added for modified files.

---

## Handoff Notes to Next Phase

Phase 03 completed. File Manager Mode indicator successfully integrated into Browse. Proceeding to Documentation and Catalog Cleanup phase.

---

## Rollback Plan

Revert the changes in `BrowseUtilityManager.kt`.
