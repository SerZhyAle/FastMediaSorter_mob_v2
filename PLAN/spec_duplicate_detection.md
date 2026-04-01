# Specification: X.1 — Duplicate Detection

**Status:** Draft
**Date:** 2026-03-30
**Tier:** 5 — Complex (16–50h, high risk)
**Roadmap entry:** Find duplicate files across resources

---

## 1. Problem Statement

The app manages files spread across multiple resources (local storage, SMB shares, SFTP servers, FTP servers, cloud providers), but provides no way to identify files stored in more than one location or copied multiple times within a single resource. Users who sort media files across network shares accumulate duplicates silently, wasting storage and making cleanup impossible without third-party tools.

There is no hashing infrastructure anywhere in the codebase (`FileMetadataCacheEntity` uses `(lastModified, fileSize)` pairs for cache invalidation, not content identity). Implementing duplicate detection requires a new data layer (hash storage, streaming hash computation) and a new UI surface — a "Resource Operations" overflow menu in `BrowseActivity` — while carefully managing network I/O cost via a three-phase algorithm and adaptive parallelism.

The new overflow menu in Browse is intentionally designed as a general **"Resource Operations"** entry point, with future operations (batch rename, size-based purge, etc.) added incrementally.

---

## 2. Goals

1. Add a "Resource Operations" overflow/popup menu button to the `BrowseActivity` top command bar.
2. Implement four initial operations in the menu: **Find Duplicates**, **Delete Duplicates**, **Delete Files Smaller Than…**, **Delete Files Larger Than…**.
3. Use a **three-phase hashing strategy** — size grouping → quick hash (4 KB, MD5) → full hash (full file, MD5) — to minimise network I/O.
4. Support **all resource types including CLOUD** (via `CloudStorageClient.getFileInputStream()`).
5. Persist computed hashes in a Room table keyed on `(resourceId, filePath, lastModified, fileSize)` so subsequent scans reuse cached results.
6. Run the scan via a `DuplicateDetectionWorker` (WorkManager + foreground notification) so it survives app backgrounding.
7. Present results in a dedicated `DuplicatesFragment` (launched from Browse) with per-group file list and per-file delete action.
8. Gate the scan behind a confirmation dialog warning of I/O cost on network/cloud resources.
9. Support cancellation at any point; cached hashes are preserved.
10. Zero-byte files are excluded from duplicate detection (never grouped).
11. On IO error for a single file during scan: skip silently, log Timber warning, continue scan.
12. Scan result is held in-memory only (ViewModel); navigating away loses the result — re-scan on return (v1).

**Non-goals for this spec:**
- Fuzzy / near-duplicate image detection (perceptual hashing, SSIM).
- "Keep one and delete rest" batch action — user deletes files individually.
- Real-time / watch-folder duplicate alerting.
- Deduplication by rename or hard-links.
- Persisting scan results across process death (deferred to v2).
- "Clear hash cache" UI in Settings (deferred).

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Full feature — local + SMB + SFTP + FTP + CLOUD resources |
| `lite`     | ✅ | Local resources only (no network/cloud in lite flavor) |
| `photos`   | ✅ | Local resources only |
| `legacy`   | ✅ | Same as standard; `MessageDigest("MD5")` available since API 1 |

No new `BuildConfig` flag required — duplicate detection applies to all flavors with their respective resource types.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | `MessageDigest.getInstance("MD5")` available since API 1. No fork needed. |
| 26+ (standard minSdk) | Default path. WorkManager foreground notification channel created inline in worker (API ≥ 26 guard). |
| 29 (Android 10) | Local file hashing uses `contentResolver.openInputStream(contentUri)` when non-null (Scoped Storage); falls back to `FileInputStream(path)` on API < 29 or null `contentUri`. |
| 31+ (Android 12) | `FOREGROUND_SERVICE_TYPE_DATA_SYNC` — same as other existing workers, no additional fork. |

### 3.3 Wear OS Impact

No Wear OS changes required. Duplicate detection is a resource-management feature with no wearable interaction surface.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `BrowseActivity` | `ui/browse/BrowseActivity.kt` (2399 lines) | Host screen; top command bar uses `binding.btnSort` → `PopupMenu` pattern to replicate for new menu button |
| `activity_browse.xml` | `res/layout/activity_browse.xml` | Layout file — new `btnResourceOps` button added to the top `layoutControls` bar |
| `MediaFile` | `domain/model/Models.kt:194` | Domain model — has `size`, `path`, `resourceId`, `contentUri` needed for hashing |
| `MediaResource` | `domain/model/Models.kt:137` | Resource config — has `type: ResourceType` for hasher dispatch |
| `ResourceType` | `domain/model/Models.kt:6` | Enum: `LOCAL, SMB, SFTP, FTP, CLOUD`; `isNetworkResource` = SMB/SFTP/FTP/CLOUD |
| `GetMediaFilesUseCase` | `domain/usecase/GetMediaFilesUseCase.kt` | `operator fun invoke(...)` — enumerates `MediaFile` per resource |
| `DeleteFilesUseCase` | `domain/usecase/DeleteFilesUseCase.kt` | `suspend operator fun invoke(files: List<File>): FileOperationResult` |
| `FileFilter` | `domain/model/Models.kt:97` | Has `minSizeMb: Float?`, `maxSizeMb: Float?` — reused for size-based deletion dialogs |
| `FileMetadataCacheEntity` | `data/local/db/FileMetadataCacheEntity.kt` | Cache pattern to follow: unique index on `(resourceId, filePath, lastModified, fileSize, cachedAt)` |
| `AppDatabase` | `data/local/db/AppDatabase.kt:22` | Room DB version 20 — bumped to 21 |
| `SmbFileOperations.openInputStream` | `data/network/SmbFileOperations.kt:541` | SMB streaming. Note: `SmbClient` also has `openInputStream` at :1229 — use `SmbFileOperations`, not `SmbClient`. |
| `SftpClient.openInputStream` | `data/remote/sftp/SftpClient.kt:1207` | SFTP streaming |
| `FtpClient.openInputStream` | `data/remote/ftp/FtpClient.kt:1499` | FTP streaming |
| `CloudStorageClient.getFileInputStream` | `data/cloud/CloudStorageClient.kt:265` | Cloud streaming — `(fileId, position, length): CloudResult<InputStream>`; supported by Drive, Dropbox, OneDrive |
| `ScheduledOperationsWorker` | `worker/ScheduledOperationsWorker.kt` | Worker pattern: `@HiltWorker`, `CoroutineWorker`, `setForeground()`, inline `createNotificationChannel()` |
| `WorkManagerScheduler` | `worker/WorkManagerScheduler.kt` | Enqueues `OneTimeWorkRequest`; `enqueueUniqueWork` + `ExistingWorkPolicy.REPLACE` |
| `TransferModule` | `di/TransferModule.kt` | `@Binds @IntoSet` multi-binding pattern — replicated for `FileHasher` |
| `BrowseDialogHelper` | `ui/browse/managers/BrowseDialogHelper.kt` | Dialog helper pattern to replicate for new dialogs |

**Key gap:** No content-hash storage or streaming hash API exists anywhere. `FileAccess` (`data/transfer/FileAccess.kt`) covers only `exists()` and `delete()`. A new `FileHasher` interface + per-protocol implementations are needed.

---

## 5. Proposed Architecture

### 5.1 Three-Phase Hashing Strategy

Duplicates are defined as files with identical `(fileSize, fullMd5Hash)`. File name and path are irrelevant.

```
Phase 0 — size grouping (zero I/O):
  Enumerate all MediaFile objects for selected resources.
  Exclude files with size == 0 (zero-byte files are never duplicates).
  Group all files by exact byte size.
  Discard groups with only one member → guaranteed unique.

Phase 1 — quick hash (4 KB read per file):
  For each remaining group, read the first 4 KB of each file → MD5(prefix).
  Cached quick hashes reused if (lastModified, fileSize) unchanged.
  Discard files whose quick hash is unique within the group.

Phase 2 — full hash (full file read):
  For files that share a quick hash, compute MD5 of the entire file.
  Cached full hashes reused when available.
  Files sharing the same full MD5 form a duplicate group.
```

**Parallelism:** File listing (Phase 0) runs resources in parallel with a bounded dispatcher — `Dispatchers.IO.limitedParallelism(maxConcurrentResources)` where `maxConcurrentResources` is calculated at scan start. For network resources, the limit is conservatively set to `min(resourceCount, 3)` for v1 (no live bandwidth measurement; see Q2 note in §15). For local-only scans, parallelism is `min(resourceCount, cpuCores)`. Hashing phases (1 & 2) run sequentially per file within a group to avoid saturating the connection.

### 5.2 Resource Operations Menu (New UI Surface)

A new `btnResourceOps` `MaterialButton` (icon-only, `ic_more_vert`) is added to the `layoutControls` top bar in `activity_browse.xml`. On click, a `PopupMenu` anchored to the button shows:

| Menu item | ID | Action |
|-----------|-----|--------|
| Find Duplicates | `action_find_duplicates` | Start duplicate scan flow |
| Delete Duplicates | `action_delete_duplicates` | Jump to DuplicatesFragment if results cached; else prompt to scan first |
| Delete Files Smaller Than… | `action_delete_smaller_than` | Input dialog → size threshold → confirm → delete |
| Delete Files Larger Than… | `action_delete_larger_than` | Input dialog → size threshold → confirm → delete |

The menu is defined in `res/menu/menu_resource_ops.xml` and inflated via `PopupMenu(context, binding.btnResourceOps)`. All click handling is delegated to a new `ResourceOpsMenuManager` (in `ui/browse/managers/`), keeping `BrowseActivity` free of logic.

### 5.3 New Classes / Files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `DuplicateModels.kt` | `domain/model/` | ≤ 80 |
| `FileHasher.kt` (interface) | `domain/hash/` | ≤ 50 |
| `ComputeFileHashUseCase.kt` | `domain/usecase/` | ≤ 120 |
| `DetectDuplicatesUseCase.kt` | `domain/usecase/` | ≤ 220 |
| `DeleteByFileSizeUseCase.kt` | `domain/usecase/` | ≤ 80 |
| `DuplicateHashRepository.kt` (interface) | `domain/repository/` | ≤ 50 |
| `DuplicateHashCacheEntity.kt` | `data/local/db/` | ≤ 80 |
| `DuplicateHashCacheDao.kt` | `data/local/db/` | ≤ 80 |
| `DuplicateHashRepositoryImpl.kt` | `data/repository/` | ≤ 120 |
| `LocalFileHasher.kt` | `data/hash/` | ≤ 100 |
| `SmbFileHasher.kt` | `data/hash/` | ≤ 120 |
| `SftpFileHasher.kt` | `data/hash/` | ≤ 120 |
| `FtpFileHasher.kt` | `data/hash/` | ≤ 120 |
| `CloudFileHasher.kt` | `data/hash/` | ≤ 120 |
| `DuplicateHashModule.kt` | `di/` | ≤ 70 |
| `DuplicateDetectionWorker.kt` | `worker/` | ≤ 150 |
| `DuplicatesViewModel.kt` | `ui/duplicates/` | ≤ 350 |
| `DuplicatesFragment.kt` | `ui/duplicates/` | ≤ 200 |
| `DuplicateGroupAdapter.kt` | `ui/duplicates/` | ≤ 200 |
| `ResourceOpsMenuManager.kt` | `ui/browse/managers/` | ≤ 180 |
| `menu_resource_ops.xml` | `res/menu/` | ≤ 30 |
| `activity_duplicates.xml` | `res/layout/` | ≤ 60 |
| `fragment_duplicates.xml` | `res/layout/` | ≤ 80 |
| `item_duplicate_group.xml` | `res/layout/` | ≤ 60 |
| `item_duplicate_file.xml` | `res/layout/` | ≤ 50 |

No file approaches 1000 lines. `DetectDuplicatesUseCase` is the most complex at ≤ 220 lines.

### 5.4 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Menu logic → `ResourceOpsMenuManager`; scan logic → `DetectDuplicatesUseCase`; state → `DuplicatesViewModel` |
| Naming conventions | ✅ | `DetectDuplicatesUseCase`, `ComputeFileHashUseCase`, `DeleteByFileSizeUseCase`, `DuplicateHashRepository`, `DuplicatesViewModel`, `ResourceOpsMenuManager` |
| Data flow `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | `DuplicatesFragment` → `DuplicatesViewModel` → `DetectDuplicatesUseCase` → `DuplicateHashRepository` → `DuplicateHashCacheDao` |
| Timber only, no `Log.d()` | ✅ | All log calls use `Timber.d/i/w/e` |
| Room schema version incremented | ✅ | Version 20 → 21; migration adds `duplicate_hash_cache` table |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ | `DuplicatesViewModel` uses `_state: MutableStateFlow<DuplicatesState>` and `_events: MutableSharedFlow<DuplicatesEvent>` |
| Hilt DI: new bindings in module file | ✅ | New `DuplicateHashModule` in `di/`; explicit `when(resource.type)` dispatch in `ComputeFileHashUseCase` (ADR-5) |

### 5.5 Domain Models (`DuplicateModels.kt`)

```kotlin
/** One group of files with identical content. */
data class DuplicateGroup(
    val fullHash: String,           // MD5 hex of full file content
    val fileSize: Long,             // Byte size (same for all members)
    val files: List<MediaFile>      // ≥ 2 members
)

/** Result of a duplicate detection run. */
data class DuplicateDetectionResult(
    val groups: List<DuplicateGroup>,
    val totalFilesScanned: Int,
    val totalWastedBytes: Long,     // Sum of (group.files.size - 1) * group.fileSize
    val durationMs: Long
)

/** Progress update emitted during a scan. */
data class DuplicateScanProgress(
    val phase: ScanPhase,
    val filesProcessed: Int,
    val totalFiles: Int
)

enum class ScanPhase { LISTING, QUICK_HASH, FULL_HASH, DONE }
```

### 5.6 `FileHasher` Interface

```kotlin
/** Computes MD5 hash of a file's content, streaming through the appropriate protocol. */
interface FileHasher {
    /**
     * Reads the first [maxBytes] bytes of [file] and returns the MD5 hex digest.
     * Pass [maxBytes] = -1 to hash the entire file (full hash).
     * Pass [maxBytes] = QUICK_HASH_BYTES (4096) for Phase 1.
     */
    suspend fun computeHash(
        file: MediaFile,
        resource: MediaResource,
        maxBytes: Long = -1L
    ): String
}
```

`ComputeFileHashUseCase` dispatches to the correct hasher via explicit `when(resource.type)` (ADR-5). Each implementation streams through a 32 KB read buffer into `MessageDigest.getInstance("MD5")`, stopping at `maxBytes` if set.

- `LocalFileHasher`: `contentResolver.openInputStream(Uri.parse(file.contentUri))` or `FileInputStream(file.path)`
- `SmbFileHasher`: `SmbFileOperations.openInputStream(share, file.path)`
- `SftpFileHasher`: `SftpClient.openInputStream(...)`
- `FtpFileHasher`: `FtpClient.openInputStream(...)`
- `CloudFileHasher`: `CloudStorageClient.getFileInputStream(fileId, position=0, length=maxBytes)`

### 5.7 `DuplicateHashCacheEntity` (Room)

```kotlin
@Entity(
    tableName = "duplicate_hash_cache",
    indices = [
        Index(value = ["resourceId", "filePath", "lastModified", "fileSize"], unique = true),
        Index(value = ["resourceId"]),
        Index(value = ["cachedAt"])
    ]
)
data class DuplicateHashCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resourceId: Long,
    val filePath: String,
    val lastModified: Long,
    val fileSize: Long,
    val quickHash: String?,     // MD5 of first 4 KB; null if not yet computed
    val fullHash: String?,      // MD5 of full file; null if not yet computed
    val cachedAt: Long          // epoch ms — for future TTL cleanup
)
```

Cache invalidation: a row is valid when `lastModified` and `fileSize` match the live `MediaFile`.

### 5.8 `DuplicatesViewModel` State & Events

```kotlin
data class DuplicatesState(
    val availableResources: List<MediaResource> = emptyList(),
    val selectedResourceIds: Set<Long> = emptySet(),
    val scanState: ScanState = ScanState.Idle,
    val result: DuplicateDetectionResult? = null
)

sealed class ScanState {
    object Idle : ScanState()
    data class Running(val progress: DuplicateScanProgress) : ScanState()
    data class Error(val message: String) : ScanState()
}

sealed class DuplicatesEvent {
    data class ShowNetworkWarning(val networkResourceCount: Int) : DuplicatesEvent()
    data class ShowError(val message: String) : DuplicatesEvent()
    data class FileDeleted(val path: String) : DuplicatesEvent()
    object ScanComplete : DuplicatesEvent()
}
```

---

## 6. Data Flow

```
BrowseActivity toolbar
        │  user taps btnResourceOps
        ▼
ResourceOpsMenuManager.showMenu()
        │  PopupMenu with 4 items
        │
        ├─ "Find Duplicates" ──────────────────────────────────────────────────┐
        │                                                                       │
        ├─ "Delete Files Smaller Than…"                                         │
        │       InputDialog (size MB) → confirm                                 │
        │       BrowseViewModel.deleteBySize(maxSizeMb)                        │
        │       DeleteByFileSizeUseCase(files, maxSizeMb=X, minSizeMb=null)    │
        │                                                                       │
        └─ "Delete Files Larger Than…" (same, minSizeMb=X, maxSizeMb=null)    │
                                                                               ▼
                                                          DuplicatesFragment (launched from Browse)
                                                                  │  user selects resources + taps Scan
                                                                  ▼
                                                          DuplicatesViewModel.startScan()
                                                                  │  enqueue DuplicateDetectionWorker
                                                                  │  observe WorkInfo.progress
                                                                  ▼
                                                          DuplicateDetectionWorker (background)
                                                                  │  DetectDuplicatesUseCase(resources)
                                                                  ▼
                                                          DetectDuplicatesUseCase
                                                                  │
                                                          Phase 0: GetMediaFilesUseCase(resource) × N
                                                                  │  parallel, limitedParallelism(3)
                                                                  │  group by size; drop size==0; drop singletons
                                                                  │
                                                          Phase 1: ComputeFileHashUseCase(file, maxBytes=4096)
                                                                  │  check DuplicateHashRepository.getCachedQuickHash
                                                                  │  cache miss → FileHasher.computeHash(4096)
                                                                  │  save → DuplicateHashRepository.saveQuickHash
                                                                  │  drop quick-hash-unique files
                                                                  │
                                                          Phase 2: ComputeFileHashUseCase(file, maxBytes=-1)
                                                                  │  check getCachedFullHash / save saveFullHash
                                                                  │  group survivors by fullHash
                                                                  ▼
                                                          DuplicateDetectionResult
                                                                  │
                                                          DuplicatesViewModel ◄── WorkInfo.SUCCEEDED
                                                                  │  update DuplicatesState.result
                                                                  ▼
                                                          DuplicatesFragment / DuplicateGroupAdapter
                                                                  │
                                                          user taps Delete on file
                                                                  │
                                                          DuplicatesViewModel.deleteFile(file, resource)
                                                                  │  DeleteFilesUseCase(listOf(file))
                                                                  │  DuplicateHashRepository.deleteHashEntry(file)
                                                                  ▼
                                                          DuplicatesState updated (file removed; empty groups pruned)
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `data/local/db/AppDatabase.kt` | Add `DuplicateHashCacheEntity` to entities; bump version 20 → 21; add `Migration_20_21`; expose `duplicateHashCacheDao()` | ~560 lines |
| `worker/WorkManagerScheduler.kt` | Add `enqueueDuplicateScan(resourceIds)` and `cancelDuplicateScan()` | ~180 lines |
| `ui/browse/BrowseActivity.kt` | Add `btnResourceOps` click handler; delegate to `ResourceOpsMenuManager` | ~2410 lines |
| `res/layout/activity_browse.xml` | Add `btnResourceOps` MaterialButton to `layoutControls` | +8 lines |

`AppDatabase.kt` (542 lines → ~560) and `BrowseActivity.kt` (2399 → ~2410) both stay under their current sizes. Backup `AppDatabase.kt` before modifying (>500 lines rule).

> **Note:** `BrowseActivity.kt` is 2399 lines — already far above the 1000-line limit but pre-existing. This spec adds only ~11 lines (one button click delegation). No extraction of existing code is in scope here.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Hashing a 4 GB video over a 2 MB/s SMB link takes ~30 min | High | Three-phase strategy: Phase 1 reads only 4 KB; full hash only for confirmed candidates. UI shows phase + file count. |
| Worker killed by OS during long scan | Med | `setForeground()` keeps process alive; partial hashes persist in Room; next scan resumes from cache |
| MD5 collision falsely marks distinct files as duplicates | Very Low | Negligible for media files in practice; per-file confirm-delete dialog is the safety net |
| Network connection drops mid-hash stream | Med | `try/catch` in each `FileHasher`; failed file is silently skipped (Timber.w); scan continues |
| CLOUD rate limits / quota during full-file hashing | Med | `CloudFileHasher` uses existing `getFileInputStream()` — same path used by ExoPlayer streaming; no new connection overhead. No rate-limit handling in current clients — risk accepted for v1. |
| DB grows unboundedly (hash entries for deleted files) | Low | `deleteByResourceId()` called on resource removal; full TTL cleanup deferred to v2 |
| `AppDatabase` migration fails | Low | Additive-only migration; covered by `AppDatabaseMigrationTest` |
| SMB connection pool exhausted by parallel listing | Med | `limitedParallelism(3)` cap on network resource parallelism |

---

## 9. Testing Plan

### 9.1 Unit Tests

**`DetectDuplicatesUseCaseTest`** (`test/domain/usecase/`)
- `givenUniqueFileSizes_thenNoDuplicatesReturned`
- `givenZeroByteFiles_thenExcludedFromScan`
- `givenSameSizeDifferentContent_thenNoDuplicatesAfterQuickHash`
- `givenSameSizeSameContent_thenDuplicateGroupFormed`
- `givenCachedHashValid_thenNoFileHasherCallMade`
- `givenCachedHashStale_thenFileHasherCalled` — `lastModified` change invalidates cache
- `givenIOErrorOnFile_thenFileSkippedAndScanContinues`

**`ComputeFileHashUseCaseTest`** (`test/domain/usecase/`)
- `givenLocalFile_hashMatchesExpectedMd5` — verifiable with known test asset
- `givenMaxBytes4k_thenOnlyQuickHashRead` — `FileHasher.computeHash` called with `maxBytes=4096`

**`DeleteByFileSizeUseCaseTest`** (`test/domain/usecase/`)
- `givenMaxSizeMb_thenOnlyFilesUnderThresholdDeleted`
- `givenMinSizeMb_thenOnlyFilesOverThresholdDeleted`

**`DuplicateHashRepositoryImplTest`** (`test/data/repository/`)
- `getCachedQuickHash_returnsCachedEntry_whenKeyMatches`
- `getCachedQuickHash_returnsNull_whenLastModifiedDiffers`
- `saveQuickHash_insertsRow_thenGetReturnsIt`

**`AppDatabaseMigrationTest`** (`androidTest/`)
- `migration_20_to_21_addsTable` — via `MigrationTestHelper`

### 9.2 Manual Test Cases

1. **Happy path — local resource**: Two identical images in a folder → scan → duplicate group shown with correct sizes and paths.
2. **Happy path — SMB resource**: Same file copied to two paths on a Samba share → duplicate group appears after scan.
3. **Happy path — CLOUD resource**: Same file in two folders on Google Drive → duplicate group detected.
4. **Cache reuse**: After scan, re-scan immediately → near-instant (all hashes cached).
5. **Cache invalidation**: Modify one file in a duplicate pair → re-scan removes it from the group.
6. **Zero-byte files**: Folder with empty files → scan completes, no duplicate groups for zero-byte files.
7. **Cancellation**: Start scan on large SMB resource → Cancel → worker stops, cached hashes preserved.
8. **IO error mid-scan**: Disconnect SMB mid-scan → skipped files logged, partial results shown for completed files.
9. **Delete action**: Duplicate group of 3 → delete file #2 → group shrinks to 2.
10. **Network warning dialog**: Select network/cloud resource → warning shown before scan starts.
11. **No duplicates**: All unique files → "No duplicates found" empty state.
12. **Delete smaller than**: Enter 1 MB → confirm → all files < 1 MB deleted; snackbar with count.
13. **Delete larger than**: Enter 500 MB → confirm → all files > 500 MB deleted.
14. **Legacy flavor (API 23)**: Scan on Android 6.0 emulator → completes without crash.

### 9.3 Maestro E2E

Not suitable — requires pre-seeded duplicate files and background worker timing. No Maestro test added.

---

## 10. Accessibility

`DuplicatesFragment` adds interactive UI:
- Each file entry: `contentDescription = "$filename, $size, in $resourceName"`.
- Delete button per file: `contentDescription = getString(R.string.cd_delete_file, filename)`.
- Expand/collapse per group: toggles `contentDescription` between `R.string.cd_expand_group` / `R.string.cd_collapse_group`.
- Group headers are decorative (non-focusable); first file card in each group gets focus order 1.
- `btnResourceOps`: `contentDescription = getString(R.string.cd_resource_ops_menu)`.
- All interactive elements use Material components with ≥ 48 dp touch targets.
- No color-only affordances — file count badge uses a number label.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): Under "File Operations" — "**Duplicate Detection** — scan one or more resources (local, SMB, SFTP, FTP, cloud) to find files with identical content; review grouped results and delete unwanted copies."
- `docs/FEATURES_RU.md` (RU): "**Поиск дубликатов** — сканирование ресурсов (локальных, SMB, SFTP, FTP, облачных) для выявления файлов с одинаковым содержимым; просмотр сгруппированных результатов и удаление лишних копий."
- `docs/FEATURES_UK.md` (UK): "**Пошук дублікатів** — сканування ресурсів (локальних, SMB, SFTP, FTP, хмарних) для виявлення файлів з однаковим вмістом; перегляд згрупованих результатів і видалення зайвих копій."

Also document new size-based deletion:
- EN: "**Size-Based File Deletion** — delete all files in a resource smaller or larger than a specified size threshold (accessible via Resource Operations menu in Browse)."
- RU: "**Удаление файлов по размеру** — удаление всех файлов ресурса, меньших или больших заданного порога размера (меню операций с ресурсом в Browse)."
- UK: "**Видалення файлів за розміром** — видалення всіх файлів ресурсу, менших або більших за заданий поріг розміру (меню операцій з ресурсом у Browse)."

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: MD5 for both quick hash and full hash**
- **Decision:** `MessageDigest.getInstance("MD5")` for both phases.
- **Alternatives considered:** SHA-256 (~30% slower); XXHash (fastest, requires JNI dependency).
- **Reason:** Available on all API levels, zero new dependencies. For duplicate *detection* (not cryptographic signing), MD5 collision risk is negligible. Speed advantage over SHA-256 matters for full-file reads over slow links.

**ADR-2: Three-phase hashing (size → quick 4 KB → full MD5)**
- **Decision:** Phase 0 groups by size; Phase 1 reads 4 KB; Phase 2 reads full file for survivors only.
- **Alternatives considered:** Single-pass full hash; larger prefix (64 KB).
- **Reason:** 4 KB eliminates >99% of same-size non-duplicates for media files (headers diverge within first 4 KB). 16× less I/O in Phase 1 vs 64 KB.

**ADR-3: `FileHasher` interface in `domain/hash/` with explicit `when` dispatch**
- **Decision:** New `FileHasher` interface; `ComputeFileHashUseCase` dispatches via `when(resource.type)`.
- **Alternatives considered:** `@IntoSet` multi-binding (as in `TransferModule`).
- **Reason:** Only 5 resource types (LOCAL, SMB, SFTP, FTP, CLOUD) — explicit `when` is simpler, equally testable, zero Hilt multi-binding boilerplate for a closed enum. `@IntoSet` adds value when implementations are open-ended; here the set is fixed and small.

**ADR-4: WorkManager for scan, not ViewModel coroutine**
- **Decision:** Scan runs via `DuplicateDetectionWorker` (WorkManager), not `viewModelScope`.
- **Alternatives considered:** `viewModelScope.launch { }`.
- **Reason:** Scans can take several minutes on large SMB/cloud libraries. WorkManager with foreground service survives process death; partial hashes persist in Room.

**ADR-5: CLOUD resources included in scope**
- **Decision:** CLOUD resources are supported; `CloudFileHasher` uses `CloudStorageClient.getFileInputStream()`.
- **Alternatives considered:** Excluding CLOUD (original spec draft).
- **Reason:** The `CloudStorageClient` interface already supports `getFileInputStream(fileId, position, length)` returning `InputStream`, used by ExoPlayer. No new cloud API calls needed. Rate limiting is not implemented in the current clients, so the risk is accepted for v1.

**ADR-6: Resource Operations as a PopupMenu in BrowseActivity toolbar**
- **Decision:** New `btnResourceOps` icon button in the existing top command bar; `PopupMenu` pattern matching `btnSort`.
- **Alternatives considered:** Bottom sheet; separate Settings screen entry; FAB.
- **Reason:** The command bar already has per-resource action buttons and a `PopupMenu` precedent (`btnSort`). Future resource operations (batch rename, etc.) will extend the same menu without changing the entry point. Consistent with existing UX patterns.

**ADR-7: `ResourceOpsMenuManager` for menu logic**
- **Decision:** New manager class `ui/browse/managers/ResourceOpsMenuManager.kt` holds all menu setup and click logic.
- **Alternatives considered:** Adding logic directly to `BrowseActivity`.
- **Reason:** `BrowseActivity` is already 2399 lines (above the 1000-line limit). All new logic must go to a manager. Matches `BrowseDialogHelper`, `BrowseSortManager`, and other existing managers.

---

## 13. Implementation Steps

1. **Backup `AppDatabase.kt`** (>500 lines rule):
   ```powershell
   Copy-Item "app_v2/src/.../data/local/db/AppDatabase.kt" "temp/AppDatabase_backup_$(Get-Date -Format 'yyyyMMddHHmm').kt"
   ```

2. **Create `DuplicateModels.kt`** — `DuplicateGroup`, `DuplicateDetectionResult`, `DuplicateScanProgress`, `ScanPhase`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/model/DuplicateModels.kt" "DuplicateModels" "Add domain models for duplicate detection"
   ```

3. **Create `FileHasher.kt`** interface in `domain/hash/`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/hash/FileHasher.kt" "FileHasher" "Add FileHasher interface"
   ```

4. **Create `DuplicateHashRepository.kt`** interface in `domain/repository/` — methods: `getCachedQuickHash`, `getCachedFullHash`, `saveQuickHash`, `saveFullHash`, `deleteByResourceId`, `deleteHashEntry(file: MediaFile)`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/DuplicateHashRepository.kt" "DuplicateHashRepository" "Add DuplicateHashRepository interface"
   ```

5. **Create `DuplicateHashCacheEntity.kt`** in `data/local/db/` — schema from §5.7.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheEntity.kt" "DuplicateHashCacheEntity" "Add Room entity for duplicate hash cache"
   ```

6. **Create `DuplicateHashCacheDao.kt`** in `data/local/db/` — CRUD + lookup queries.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheDao.kt" "DuplicateHashCacheDao" "Add DAO for duplicate hash cache"
   ```

7. **Modify `AppDatabase.kt`** — add entity, bump version 20 → 21, add `Migration_20_21`, expose DAO.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt" "AppDatabase" "Bump DB version to 21; add duplicate_hash_cache table"
   ```

8. **Create `DuplicateHashRepositoryImpl.kt`** in `data/repository/`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/DuplicateHashRepositoryImpl.kt" "DuplicateHashRepositoryImpl" "Add DuplicateHashRepository implementation"
   ```

9. **Create `LocalFileHasher.kt`** in `data/hash/`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/LocalFileHasher.kt" "LocalFileHasher" "Add MD5 hasher for local files"
   ```

10. **Create `SmbFileHasher.kt`** — delegates to `SmbFileOperations.openInputStream`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/SmbFileHasher.kt" "SmbFileHasher" "Add MD5 hasher for SMB files"
    ```

11. **Create `SftpFileHasher.kt`** — delegates to `SftpClient.openInputStream`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/SftpFileHasher.kt" "SftpFileHasher" "Add MD5 hasher for SFTP files"
    ```

12. **Create `FtpFileHasher.kt`** — delegates to `FtpClient.openInputStream`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/FtpFileHasher.kt" "FtpFileHasher" "Add MD5 hasher for FTP files"
    ```

13. **Create `CloudFileHasher.kt`** — delegates to `CloudStorageClient.getFileInputStream(fileId, 0, maxBytes)`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/CloudFileHasher.kt" "CloudFileHasher" "Add MD5 hasher for CLOUD files via getFileInputStream"
    ```

14. **Create `DuplicateHashModule.kt`** in `di/` — `@Binds` for `DuplicateHashRepository`; inject 5 `FileHasher` instances by concrete type (no `@IntoSet` — see ADR-3).
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/di/DuplicateHashModule.kt" "DuplicateHashModule" "Add Hilt module for duplicate hash bindings"
    ```

15. **Create `ComputeFileHashUseCase.kt`** — `when(resource.type)` dispatch; cache check → hasher on miss → save.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ComputeFileHashUseCase.kt" "ComputeFileHashUseCase" "Add UseCase for per-file MD5 computation with caching"
    ```

16. **Create `DetectDuplicatesUseCase.kt`** — orchestrates Phases 0/1/2; `QUICK_HASH_BYTES = 4096L`; emits `DuplicateScanProgress` via `Flow`; returns `DuplicateDetectionResult`; skips size==0 files; skips on IO error with `Timber.w`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DetectDuplicatesUseCase.kt" "DetectDuplicatesUseCase" "Add orchestrating UseCase for three-phase duplicate detection"
    ```

17. **Create `DeleteByFileSizeUseCase.kt`** — filters `MediaFile` list by `minSizeMb` / `maxSizeMb` then calls `DeleteFilesUseCase`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteByFileSizeUseCase.kt" "DeleteByFileSizeUseCase" "Add UseCase for size-threshold file deletion"
    ```

18. **Create `DuplicateDetectionWorker.kt`** in `worker/` — `@HiltWorker`, `CoroutineWorker`, inline `createNotificationChannel()`, `NOTIFICATION_CHANNEL_ID = "duplicate_scan_channel"`, `setForeground()`, `setProgress()`, reads `resourceIds` from `inputData`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/worker/DuplicateDetectionWorker.kt" "DuplicateDetectionWorker" "Add WorkManager worker for background duplicate scan"
    ```

19. **Modify `WorkManagerScheduler.kt`** — add `enqueueDuplicateScan(resourceIds: List<Long>)` and `cancelDuplicateScan()`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt" "WorkManagerScheduler" "Add duplicate scan enqueue/cancel methods"
    ```

20. **Create `DuplicatesViewModel.kt`** — state/events from §5.8.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesViewModel.kt" "DuplicatesViewModel" "Add ViewModel for duplicates screen"
    ```

21. **Create `DuplicateGroupAdapter.kt`** — expandable `RecyclerView` adapter, nested file items, per-file delete button.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt" "DuplicateGroupAdapter" "Add RecyclerView adapter for duplicate groups"
    ```

22. **Create `DuplicatesFragment.kt`** — thin Fragment; observes `DuplicatesState`; delegates to ViewModel.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesFragment.kt" "DuplicatesFragment" "Add Duplicates screen Fragment"
    ```

23. **Create `ResourceOpsMenuManager.kt`** in `ui/browse/managers/` — builds `PopupMenu` anchored to `btnResourceOps`; handles all 4 menu item clicks; navigates to `DuplicatesFragment` or shows input dialogs for size operations.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt" "ResourceOpsMenuManager" "Add manager for Resource Operations popup menu in Browse"
    ```

24. **Create `menu_resource_ops.xml`** in `res/menu/` — 4 items (IDs from §5.2).
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/menu/menu_resource_ops.xml" "menu" "Add Resource Operations popup menu XML"
    ```

25. **Create layout files**: `activity_duplicates.xml`, `fragment_duplicates.xml`, `item_duplicate_group.xml`, `item_duplicate_file.xml`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_duplicates.xml" "layout" "Add layouts for duplicates screen"
    ```

26. **Modify `activity_browse.xml`** — add `btnResourceOps` `MaterialButton` (icon `ic_more_vert`, 48 dp) to `layoutControls` bar.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/activity_browse.xml" "layout" "Add btnResourceOps icon button to Browse toolbar"
    ```

27. **Modify `BrowseActivity.kt`** — wire `binding.btnResourceOps.setOnClickListener { resourceOpsMenuManager.showMenu() }`; inject `ResourceOpsMenuManager`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt" "BrowseActivity" "Wire btnResourceOps to ResourceOpsMenuManager"
    ```

28. **Add string resources** in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`:
    - `menu_resource_ops` / "Resource Operations" / "Операции с ресурсом" / "Операції з ресурсом"
    - `action_find_duplicates` / "Find Duplicates" / "Найти дубликаты" / "Знайти дублікати"
    - `action_delete_duplicates` / "Delete Duplicates" / "Удалить дубликаты" / "Видалити дублікати"
    - `action_delete_smaller_than` / "Delete Files Smaller Than…" / "Удалить файлы меньше…" / "Видалити файли менше…"
    - `action_delete_larger_than` / "Delete Files Larger Than…" / "Удалить файлы больше…" / "Видалити файли більше…"
    - `duplicate_detection_title` / "Duplicate Detection" / "Поиск дубликатов" / "Пошук дублікатів"
    - `duplicate_scan_start` / "Scan for Duplicates" / "Найти дубликаты" / "Знайти дублікати"
    - `duplicate_scan_network_warning` / "This scan will read data from network/cloud resources. Continue?" / …
    - `duplicate_none_found` / "No duplicates found" / "Дубликаты не найдены" / "Дублікатів не знайдено"
    - `duplicate_wasted_bytes` / "%1$s wasted" / "%1$s занято дубликатами" / "%1$s займають дублікати"
    - `cd_expand_group`, `cd_collapse_group`, `cd_delete_file`, `cd_resource_ops_menu`
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Add EN strings for duplicate detection and resource ops menu"
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "strings" "Add RU strings for duplicate detection and resource ops menu"
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "strings" "Add UK strings for duplicate detection and resource ops menu"
    ```

29. **Update `docs/FEATURES.md`**, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md** with bullets from §11.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "docs" "Document Duplicate Detection and Size-Based Deletion features"
    ```

**Mandatory step checklist:**
- [ ] String resources added: `values/`, `values-ru/`, `values-uk/`
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated
- [ ] Room DB migration `Migration_20_21` added; version bumped to 21 in `AppDatabase.kt`
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file

---

## 14. Out of Scope (future items)

- **Perceptual / fuzzy image deduplication** — pHash/dHash; entirely different algorithm.
- **"Keep one and delete rest" batch action** — risk of unintended mass deletion.
- **TTL-based hash cache cleanup** — `Clear Hash Cache` in Settings → Advanced.
- **Last scan result persistence** — storing `DuplicateDetectionResult` in Room for instant reload.
- **Duplicate badge in Browse thumbnail grid** — requires persistent result storage (see above).
- **Export duplicates report** — CSV/JSON export of found groups.
- **Live bandwidth measurement for parallelism tuning** — v1 uses fixed cap of 3 for network resources.
- **More Resource Operations menu items** — batch rename, tag, etc. (menu is the extensible entry point).

---

## 15. Open Questions — Resolved

| Q | Decision |
|---|----------|
| Q1 — UI entry point | `btnResourceOps` PopupMenu in `BrowseActivity` top bar (ADR-6) |
| Q2 — Parallel listing | Parallel, `limitedParallelism(3)` for network resources; no live bandwidth test in v1 |
| Q3 — Prefix size | 4 KB fixed; `QUICK_HASH_BYTES = 4096L` const in `DetectDuplicatesUseCase` |
| Q4 — Zero-byte files | Excluded from scan (size == 0 files are never grouped) |
| Q5 — IO error per file | Skip silently + `Timber.w`; scan continues |
| Q6 — Cross-resource delete UX | Standard confirm dialog; surviving copies not enumerated in v1 |
| Q7 — Result persistence | In-memory only (v1); re-scan on return |
| Q8 — Hasher dispatch | Explicit `when(resource.type)` in `ComputeFileHashUseCase` (ADR-3) |
| Q9 — SMB connection pool | Reuses `SmbConnectionManager` pooling already in `SmbFileOperations` |
| Q10 — FTP hashing | Supported in v1 via `FtpClient.openInputStream` |
| Q11 — CLOUD resources | **Included** — `CloudStorageClient.getFileInputStream()` used (ADR-5) |
| Q12 — Tier 4 vs 5 | **Tier 5** — roadmap updated |
