# Phase 02 - SAF File Destinations

**Strategic spec:** [`../S0379_standard-nolegal-storage-surface.md`](../S0379_standard-nolegal-storage-surface.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Allow browse/player file copy and move flows to target writable SAF tree destinations when direct local paths are unavailable.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6.8 and §6.9 remain Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFolderPickerHandler.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperation.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalCopyFileOperation.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalMoveFileOperation.kt` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Preserve SAF tree destinations through picker and UI operation entry points

**Files:** `ui/browse/managers/BrowseFolderPickerHandler.kt`, `ui/player/helpers/PlayerFolderPickerHandler.kt`, `ui/browse/managers/BrowseFileOperationsManager.kt`, `ui/player/FileOperationsHandler.kt`, `ui/player/fileops/PlayerFileOperation.kt`
**Depends on:** 01.2

**Prompt for developer:**

- Keep current path-based flow unchanged when the picked URI resolves to a writable local path.
- When the picked URI does not resolve to a writable path but the persisted SAF tree itself is writable, pass the normalized `content://` tree URI downstream instead of rejecting the selection.
- Preserve `lastSelectedLocalFolder` persistence.
- Keep browse and player flows aligned.
- Preserve current network/cloud path handling.

**Verification:**

- Browse picker code can choose either resolved local path or normalized `content://` tree URI.
- Player picker code can choose either resolved local path or normalized `content://` tree URI.
- Player destination file factory treats `content://` as a preserved protocol-like path.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification PASS. Files: `BrowseFolderPickerHandler.kt`, `PlayerFolderPickerHandler.kt`, `BrowseFileOperationsManager.kt`, `PlayerFileOperation.kt`, `FileOperationsHandler.kt`. Dev log recorded. Build: `build-debug.PS1` PASS after import fix.

### Step 02.2 - Write local copy and move outputs into SAF tree children

**Files:** `domain/usecase/LocalCopyFileOperation.kt`, `domain/usecase/LocalMoveFileOperation.kt`
**Depends on:** 02.1

**Prompt for developer:**

- Keep current local-file and content-source behavior unchanged.
- Detect when `operation.destination` is a SAF tree URI.
- For copy: create or reuse the destination child document under that tree and stream bytes into it.
- For move: reuse the same SAF destination write path, then delete the source using the existing local / MediaStore / SAF logic.
- Keep overwrite semantics explicit.
- Do not broaden this step to directory copy/move.

**Verification:**

- `LocalCopyFileOperation.kt` has a destination-tree branch for `content://`.
- `LocalMoveFileOperation.kt` has a destination-tree branch for `content://`.
- Existing non-`content://` destination logic remains present in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification PASS. Files: `LocalCopyFileOperation.kt`, `LocalMoveFileOperation.kt`. Dev log recorded. Build: `build-debug.PS1` PASS.

---

## Phase Done Criteria

- [x] Browse copy/move can accept writable SAF tree destinations without regressing writable local-path destinations.
- [x] Player copy/move can accept writable SAF tree destinations without regressing writable local-path destinations.
- [x] Local file copy/move supports `content://` destination trees for files.
