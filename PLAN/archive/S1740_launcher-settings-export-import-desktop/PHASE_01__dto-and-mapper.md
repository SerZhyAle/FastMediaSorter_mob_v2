# Phase 01: DTO and Mapper

**Phase:** 01
**Slug:** dto-and-mapper
**Status:** ✅ Done
**Completed:** 2026-08-17
**Depends on:** none
**Files touched:**
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/BackupMapperTest.kt`

---

## Steps

### [x] 01.1 - Add launcher settings and BackupLauncherCell DTO to BackupData.kt
- **Prompt for developer:** Add launcher settings fields to `BackupSettings` and create `data class BackupLauncherCell(...)`. Add `val launcherCells: List<BackupLauncherCell>? = null` to `BackupPayload`.
- **Why:** Extends the backup payload schema to accommodate launcher preferences and desktop items while maintaining backward compatibility.
- **Verification:** Grep for `BackupLauncherCell` in `BackupData.kt`. (PASS)

### [x] 01.2 - Update BackupMapper.kt for launcher settings and launcher cells
- **Prompt for developer:** In `BackupMapper.kt`, map launcher fields between `AppSettings` and `BackupSettings` in `toBackupSettings` and `toAppSettings`. Implement `toBackupLauncherCell` and `toLauncherCellEntity`.
- **Why:** Provides bidirectional mapping between domain/DB models and backup DTOs.
- **Verification:** Grep for `toBackupLauncherCell` in `BackupMapper.kt`. (PASS)

### [x] 01.3 - Add unit tests to BackupMapperTest.kt
- **Prompt for developer:** Add unit tests in `BackupMapperTest.kt` verifying round-trip serialization and deserialization of launcher settings and launcher cells.
- **Why:** Ensures test coverage and regression prevention for backup mapping.
- **Verification:** Run `.\gradlew.bat testStandardDebugUnitTest --tests com.sza.fastmediasorter.domain.usecase.BackupMapperTest`. (PASS)

---

## Phase Done Criteria
- [x] Project compiles
- [x] Unit tests pass
