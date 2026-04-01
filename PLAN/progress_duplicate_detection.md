# Implementation Progress: X.1 — Duplicate Detection

**Spec:** `PLAN/spec_duplicate_detection.md`
**Started:** 2026-03-30
**Status:** COMPLETED

---

## Completed Steps

- [x] Step 1: DuplicateModels.kt
- [x] Step 2: FileHasher.kt interface
- [x] Step 3: DuplicateHashRepository.kt interface
- [x] Step 4: Backup AppDatabase.kt
- [x] Step 5: DuplicateHashCacheEntity.kt
- [x] Step 6: DuplicateHashCacheDao.kt
- [x] Step 7: AppDatabase.kt — version bump + migration
- [x] Step 8: DuplicateHashRepositoryImpl.kt
- [x] Step 9: LocalFileHasher.kt
- [x] Step 10: SmbFileHasher.kt
- [x] Step 11: SftpFileHasher.kt
- [x] Step 12: FtpFileHasher.kt
- [x] Step 13: CloudFileHasher.kt
- [x] Step 14: DuplicateHashModule.kt
- [x] Step 15: ComputeFileHashUseCase.kt
- [x] Step 16: DetectDuplicatesUseCase.kt
- [x] Step 17: DeleteByFileSizeUseCase.kt
- [x] Step 18: DuplicateDetectionWorker.kt
- [x] Step 19: WorkManagerScheduler.kt — add enqueue/cancel
- [x] Step 20: DuplicatesViewModel.kt
- [x] Step 21: DuplicateGroupAdapter.kt
- [x] Step 22: DuplicatesFragment.kt
- [x] Step 23: ResourceOpsMenuManager.kt
- [x] Step 24: menu_resource_ops.xml
- [x] Step 25: activity_duplicates.xml + fragment_duplicates.xml + item_duplicate_group.xml + item_duplicate_file.xml
- [x] Step 26: activity_browse.xml — add btnResourceOps
- [x] Step 27: BrowseActivity.kt — wire btnResourceOps
- [x] Step 28: String resources (EN/RU/UK)
- [x] Step 29: docs/FEATURES.md + RU + UK update

---

## Key Paths

**Package root:** `app_v2/src/main/java/com/sza/fastmediasorter/`
**Res root:** `app_v2/src/main/res/`

| New file | Full path |
|----------|-----------|
| DuplicateModels.kt | `domain/model/DuplicateModels.kt` |
| FileHasher.kt | `domain/hash/FileHasher.kt` |
| DuplicateHashRepository.kt | `domain/repository/DuplicateHashRepository.kt` |
| DuplicateHashCacheEntity.kt | `data/local/db/DuplicateHashCacheEntity.kt` |
| DuplicateHashCacheDao.kt | `data/local/db/DuplicateHashCacheDao.kt` |
| DuplicateHashRepositoryImpl.kt | `data/repository/DuplicateHashRepositoryImpl.kt` |
| LocalFileHasher.kt | `data/hash/LocalFileHasher.kt` |
| SmbFileHasher.kt | `data/hash/SmbFileHasher.kt` |
| SftpFileHasher.kt | `data/hash/SftpFileHasher.kt` |
| FtpFileHasher.kt | `data/hash/FtpFileHasher.kt` |
| CloudFileHasher.kt | `data/hash/CloudFileHasher.kt` |
| DuplicateHashModule.kt | `di/DuplicateHashModule.kt` |
| ComputeFileHashUseCase.kt | `domain/usecase/ComputeFileHashUseCase.kt` |
| DetectDuplicatesUseCase.kt | `domain/usecase/DetectDuplicatesUseCase.kt` |
| DeleteByFileSizeUseCase.kt | `domain/usecase/DeleteByFileSizeUseCase.kt` |
| DuplicateDetectionWorker.kt | `worker/DuplicateDetectionWorker.kt` |
| DuplicatesViewModel.kt | `ui/duplicates/DuplicatesViewModel.kt` |
| DuplicateGroupAdapter.kt | `ui/duplicates/DuplicateGroupAdapter.kt` |
| DuplicatesFragment.kt | `ui/duplicates/DuplicatesFragment.kt` |
| ResourceOpsMenuManager.kt | `ui/browse/managers/ResourceOpsMenuManager.kt` |
| menu_resource_ops.xml | `res/menu/menu_resource_ops.xml` |
| activity_duplicates.xml | `res/layout/activity_duplicates.xml` |
| fragment_duplicates.xml | `res/layout/fragment_duplicates.xml` |
| item_duplicate_group.xml | `res/layout/item_duplicate_group.xml` |
| item_duplicate_file.xml | `res/layout/item_duplicate_file.xml` |

**Modified files:**
| File | Change |
|------|--------|
| `data/local/db/AppDatabase.kt` | +entity, version 20→21, Migration_20_21, +DAO method |
| `worker/WorkManagerScheduler.kt` | +enqueueDuplicateScan, +cancelDuplicateScan |
| `ui/browse/BrowseActivity.kt` | +btnResourceOps click → ResourceOpsMenuManager |
| `res/layout/activity_browse.xml` | +btnResourceOps button in layoutControls |
| `res/values/strings.xml` | +duplicate detection strings |
| `res/values-ru/strings.xml` | +RU strings |
| `res/values-uk/strings.xml` | +UK strings |
| `docs/FEATURES.md` | +Duplicate Detection, +Size-Based Deletion |
| `docs/FEATURES_RU.md` | +RU entries |
| `docs/FEATURES_UK.md` | +UK entries |

---

## Key Facts for Next Agent

- **AppDatabase version:** currently 20 → must become 21
- **DB entities list** (lines 9-24 in AppDatabase.kt): ResourceEntity, NetworkCredentialsEntity, ResourceFtsEntity, FavoritesEntity, PlaybackPositionEntity, ThumbnailCacheEntity, CachedFileListEntity, FileMetadataCacheEntity, PendingRevocationEntity, ScheduledOperationEntity — add DuplicateHashCacheEntity
- **Worker pattern:** copy ScheduledOperationsWorker — @HiltWorker, @AssistedInject, inline createNotificationChannel(), NOTIFICATION_CHANNEL_ID = "duplicate_scan_channel"
- **Hasher dispatch:** explicit `when(resource.type)` in ComputeFileHashUseCase — NO @IntoSet
- **Cloud hashing:** CloudStorageClient.getFileInputStream(fileId, position=0, length=maxBytes) returns CloudResult<InputStream>
- **SMB hashing:** use SmbFileOperations.openInputStream (NOT SmbClient.openInputStream)
- **QUICK_HASH_BYTES = 4096L** — companion const in DetectDuplicatesUseCase
- **Zero-byte files:** skip in Phase 0 (size == 0)
- **IO error per file:** Timber.w + skip, continue scan
- **BrowseActivity btnSort** pattern (line 1823): `val popup = PopupMenu(this, binding.btnSort)` — replicate for btnResourceOps
- **Existing managers package:** `com.sza.fastmediasorter.ui.browse.managers`
- **activity_browse.xml layoutControls** ends with btnPlay — add btnResourceOps AFTER btnPlay
- **DeleteFilesUseCase** takes `List<File>` (java.io.File), not MediaFile
- **DuplicatesFragment** launched as Activity intent from ResourceOpsMenuManager (no Navigation Component in project — all navigation is Intent-based)

---

## Notes / Gotchas

1. `BrowseActivity` is 2399 lines — do NOT add logic there, only wire `ResourceOpsMenuManager`
2. `CloudFileHasher` must inject a `Map<String, CloudStorageClient>` or use the same injection pattern as other cloud-using classes — check how cloud clients are injected in existing code (e.g. BrowseCloudAuthManager)
3. For `DeleteByFileSizeUseCase` — MediaFile has `.size: Long` (bytes); input from dialog is MB (Float) — convert: `thresholdBytes = (thresholdMb * 1024 * 1024).toLong()`
4. `DuplicatesFragment` needs its own Activity wrapper (`DuplicatesActivity`) since the project uses Activities, not a single-Activity nav graph
5. After ALL files are written, run ONE build: `.\gradlew.bat assembleStandardDebug`
