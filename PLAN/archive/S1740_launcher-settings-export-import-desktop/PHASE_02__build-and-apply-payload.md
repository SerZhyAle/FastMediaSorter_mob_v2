# Phase 02: Build and Apply Payload

**Phase:** 02
**Slug:** build-and-apply-payload
**Status:** ✅ Done
**Completed:** 2026-08-17
**Depends on:** 01
**Files touched:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherCellEntity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BuildBackupPayloadUseCase.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyBackupPayloadUseCase.kt`

---

## Steps

### [x] 02.1 - Add getAllCellsSync to LauncherCellDao
- **Prompt for developer:** Add `@Query("SELECT * FROM launcher_cells ORDER BY rowIndex ASC, colIndex ASC") suspend fun getAllCellsSync(): List<LauncherCellEntity>` to `LauncherCellDao` in `LauncherCellEntity.kt`.
- **Why:** Allows synchronous retrieval of all launcher desktop items across orientations for payload building.
- **Verification:** Grep for `getAllCellsSync` in `LauncherCellEntity.kt`. (PASS)

### [x] 02.2 - Update BuildBackupPayloadUseCase to include launcher cells
- **Prompt for developer:** Inject `LauncherCellDao` into `BuildBackupPayloadUseCase` and populate `launcherCells` in `BackupPayload`.
- **Why:** Ensures exported JSON backups contain all current desktop cells.
- **Verification:** Grep for `launcherCellDao` in `BuildBackupPayloadUseCase.kt`. (PASS)

### [x] 02.3 - Update ApplyBackupPayloadUseCase to restore launcher cells and filter uninstalled apps
- **Prompt for developer:** In `ApplyBackupPayloadUseCase.kt`, inject `LauncherCellDao` and `Context`. In the restore transaction, when `payload.launcherCells != null`, clear existing cells and insert restored cells. Skip any shortcut targeting an uninstalled application (`app:<pkg>` or `pin:<pkg>:...`) using `PackageManagerCompat`.
- **Why:** Restores launcher desktop layout while honoring the rule that shortcuts to non-existent packages leave the slot empty without breaking import.
- **Verification:** Grep for `launcherCells` in `ApplyBackupPayloadUseCase.kt`. (PASS)

---

## Phase Done Criteria
- [x] Project compiles
- [x] Unit tests pass
