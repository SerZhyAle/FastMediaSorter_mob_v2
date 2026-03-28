# Specification: X.1 — Duplicate Detection

**Status:** Draft
**Date:** 2026-03-28
**Tier:** 4 — Strategic (8h+, high risk)
**Roadmap entry:** Find duplicate files across resources

---

## 1. Problem Statement

The app manages files spread across multiple resources (local storage, SMB shares, SFTP servers, FTP servers, cloud providers), but provides no way to identify files that are stored in more than one location or copied multiple times within a single resource. Users who sort media files across network shares accumulate duplicates silently, wasting storage and making cleanup impossible without third-party tools.

There is no hashing infrastructure anywhere in the codebase (`FileMetadataCacheEntity` uses `(lastModified, fileSize)` pairs for cache invalidation, not content identity). Implementing duplicate detection therefore requires a new data layer (hash storage, streaming hash computation) and a new UI surface while carefully managing network I/O cost: hashing every file on a slow SMB share would be intolerable without a two-phase approach and cancellation support.

---

## 2. Goals

1. Scan one or more user-selected resources and identify groups of two or more files with identical content.
2. Use a **two-phase hashing strategy** — prefix hash (first 64 KB) to eliminate non-duplicates cheaply, full hash only for prefix-match groups — to minimise network I/O.
3. Persist computed hashes in a Room table keyed on `(resourceId, filePath, lastModified, fileSize)` so subsequent scans are fast for unchanged files.
4. Run the scan via a `DuplicateDetectionWorker` (WorkManager + foreground notification) so it survives app backgrounding.
5. Present results in a dedicated `DuplicatesFragment` with per-group file list and per-file delete action.
6. Gate the scan behind a confirmation dialog that warns of network I/O cost when network resources are included.
7. Support cancellation at any point; resume is automatic on next launch (cached hashes are not lost).

**Non-goals for this spec:**
- Fuzzy / near-duplicate image detection (perceptual hashing, SSIM).
- Automatic deletion or "keep one" batch actions — user must delete files individually to avoid unintended data loss.
- Duplicate detection on CLOUD resources — cloud providers return no reliable `lastModified` for cache invalidation and API rate limits make streaming infeasible. Cloud resources are excluded from scope for this tier.
- Real-time / watch-folder duplicate alerting.
- Deduplication by content-aware rename or hard-links.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Full feature — local + SMB + SFTP + FTP resources |
| `lite`     | ✅ | Local resources only (no network in lite) |
| `photos`   | ✅ | Local resources only (no cloud scope) |
| `legacy`   | ✅ | Same as standard; minSdk 23 path must avoid `MessageDigest` API differences (none — `MessageDigest` available since API 1) |

No new `BuildConfig` flag is required — duplicate detection applies to all flavors with their respective resource types.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | `MessageDigest.getInstance("SHA-256")` available since API 1; no fork needed. `ContentResolver.openInputStream()` available since API 1. No difference. |
| 26+ (standard minSdk) | Default path. WorkManager foreground notification channel creation guarded by `Build.VERSION.SDK_INT >= O` (already done in all existing workers). |
| 29 (Android 10) | Local file hashing uses `contentResolver.openInputStream(contentUri)` when `contentUri` is non-null (Scoped Storage); falls back to `FileInputStream(path)` on API < 29 or when `contentUri` is null. |
| 31+ (Android 12) | `FOREGROUND_SERVICE_TYPE_DATA_SYNC` already used by other workers — same applies here. No additional fork. |

### 3.3 Wear OS Impact

No Wear OS changes required. Duplicate detection is a resource-management feature with no wearable interaction surface.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `MediaFile` | `domain/model/Models.kt:194` | Domain model for a single file — has `size`, `path`, `resourceId`, `contentUri` needed for hashing |
| `MediaResource` | `domain/model/Models.kt:137` | Resource config — has `type: ResourceType` used to dispatch to the right hasher implementation |
| `GetMediaFilesUseCase` | `domain/usecase/GetMediaFilesUseCase.kt` | Provides `Flow<MediaFile>` per resource; used to enumerate files before hashing |
| `FileMetadataCacheEntity` | `data/local/db/FileMetadataCacheEntity.kt` | Cache pattern to follow: `(resourceId, filePath, lastModified, fileSize, cachedAt)` unique index |
| `AppDatabase` | `data/local/db/AppDatabase.kt:22` | Room DB version 20 — must be bumped to 21 for new entity |
| `SmbFileOperations.openInputStream` | `data/network/SmbFileOperations.kt:541` | Returns `InputStream` for a remote SMB file path — backbone of `SmbFileHasher` |
| `SftpClient.openInputStream` | `data/remote/sftp/SftpClient.kt:1207` | SFTP equivalent |
| `FtpClient.openInputStream` | `data/remote/ftp/FtpClient.kt:1499` | FTP equivalent |
| `ScheduledOperationsWorker` | `worker/ScheduledOperationsWorker.kt` | Pattern to replicate: `@HiltWorker`, `CoroutineWorker`, `setForeground()`, `BackoffPolicy.EXPONENTIAL` |
| `WorkManagerScheduler` | `worker/WorkManagerScheduler.kt` | Enqueues `OneTimeWorkRequest`; `enqueueUniqueWork` with `ExistingWorkPolicy.REPLACE` |
| `TransferModule` | `di/TransferModule.kt` | `@Binds @IntoSet` pattern for multi-binding strategies — same pattern for `FileHasher` |

**Key gap:** No content-hash storage or streaming hash API exists anywhere in the codebase. The `FileAccess` interface (`data/transfer/FileAccess.kt`) covers only `exists()` and `delete()` — no read stream. A new `FileHasher` interface and per-protocol implementations are needed.

---

## 5. Proposed Architecture

### 5.1 Two-Phase Hashing Strategy

All files within a candidate set (files that share the same byte `size`) are processed in two rounds:

```
Phase 0 — size grouping (zero I/O):
  Group all files by exact byte size.
  Discard groups with only one member → guaranteed unique.

Phase 1 — prefix hash (64 KB read per file):
  For each remaining group, read the first 64 KB of each file and compute SHA-256(prefix).
  Discard files whose prefix hash is unique within the group.
  Cached prefix hashes (DB) are reused if (lastModified, fileSize) unchanged.

Phase 2 — full hash (full file read):
  For files that share a prefix hash, compute SHA-256 of the entire file.
  Cached full hashes are reused when available.
  Files that share a full hash form a duplicate group.
```

This ensures that for a typical media library where most files are unique by size, zero network I/O is required. For same-size files with different content (common in burst photo sets), Phase 1 eliminates them after reading only 64 KB — not the whole file.

### 5.2 New Classes / Files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `DuplicateModels.kt` | `domain/model/` | ≤ 80 |
| `FileHasher.kt` (interface) | `domain/hash/` | ≤ 40 |
| `ComputeFileHashUseCase.kt` | `domain/usecase/` | ≤ 120 |
| `DetectDuplicatesUseCase.kt` | `domain/usecase/` | ≤ 200 |
| `DuplicateHashRepository.kt` (interface) | `domain/repository/` | ≤ 40 |
| `DuplicateHashCacheEntity.kt` | `data/local/db/` | ≤ 80 |
| `DuplicateHashCacheDao.kt` | `data/local/db/` | ≤ 80 |
| `DuplicateHashRepositoryImpl.kt` | `data/repository/` | ≤ 120 |
| `LocalFileHasher.kt` | `data/hash/` | ≤ 100 |
| `SmbFileHasher.kt` | `data/hash/` | ≤ 120 |
| `SftpFileHasher.kt` | `data/hash/` | ≤ 120 |
| `FtpFileHasher.kt` | `data/hash/` | ≤ 120 |
| `DuplicateHashModule.kt` | `di/` | ≤ 60 |
| `DuplicateDetectionWorker.kt` | `worker/` | ≤ 150 |
| `DuplicatesViewModel.kt` | `ui/duplicates/` | ≤ 350 |
| `DuplicatesFragment.kt` | `ui/duplicates/` | ≤ 200 |
| `DuplicateGroupAdapter.kt` | `ui/duplicates/` | ≤ 200 |
| `activity_duplicates.xml` | `res/layout/` | ≤ 60 |
| `fragment_duplicates.xml` | `res/layout/` | ≤ 80 |
| `item_duplicate_group.xml` | `res/layout/` | ≤ 60 |
| `item_duplicate_file.xml` | `res/layout/` | ≤ 50 |

No file approaches 1000 lines. `DetectDuplicatesUseCase` holds all orchestration logic and is the most complex; at ≤ 200 lines it is safely within limits.

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | All logic in `DetectDuplicatesUseCase`, `ComputeFileHashUseCase`, and `DuplicatesViewModel` |
| Naming conventions | ✅ | `DetectDuplicatesUseCase`, `ComputeFileHashUseCase`, `DuplicateHashRepository`, `DuplicatesViewModel` |
| Data flow `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | `DuplicatesFragment` → `DuplicatesViewModel` → `DetectDuplicatesUseCase` → `DuplicateHashRepository` → `DuplicateHashCacheDao` |
| Timber only, no `Log.d()` | ✅ | All log calls use `Timber.d/i/w/e` |
| Room schema version incremented | ✅ | Version 20 → 21; migration adds `duplicate_hash_cache` table |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ | `DuplicatesViewModel` uses `_state: MutableStateFlow<DuplicatesState>` and `_events: MutableSharedFlow<DuplicatesEvent>` |
| Hilt DI: new bindings declared in module file | ✅ | New `DuplicateHashModule` in `di/`; `@Binds @IntoSet` for each `FileHasher` |

### 5.4 Domain Models (`DuplicateModels.kt`)

```kotlin
/** One group of files with identical content. */
data class DuplicateGroup(
    val fullHash: String,           // SHA-256 hex of full file content
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

enum class ScanPhase { LISTING, PREFIX_HASH, FULL_HASH, DONE }
```

### 5.5 `FileHasher` Interface

```kotlin
/** Computes SHA-256 hash of a file's content, streaming through the appropriate protocol. */
interface FileHasher {
    /** Returns true if this hasher handles the given resource type. */
    fun supports(resourceType: ResourceType): Boolean

    /**
     * Reads the first [maxBytes] bytes of [file] and returns their SHA-256 hex digest.
     * Pass [maxBytes] = -1 to hash the entire file.
     */
    suspend fun computeHash(
        file: MediaFile,
        resource: MediaResource,
        maxBytes: Long = -1L
    ): String
}
```

`LocalFileHasher` opens via `contentResolver.openInputStream(Uri.parse(file.contentUri))` when `contentUri` is non-null, else `FileInputStream(file.path)`. `SmbFileHasher` calls `SmbFileOperations.openInputStream(share, file.path)`. `SftpFileHasher` calls `SftpClient.openInputStream(...)`. `FtpFileHasher` calls `FtpClient.openInputStream(...)`.

All implementations stream through a fixed 32 KB read buffer, feeding bytes into `MessageDigest.getInstance("SHA-256")`, then close the stream. If `maxBytes != -1L`, reading stops after `maxBytes` bytes.

### 5.6 `DuplicateHashCacheEntity` (Room)

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
    val prefixHash: String?,    // SHA-256 of first 64 KB; null if not yet computed
    val fullHash: String?,       // SHA-256 of full file; null if not yet computed
    val cachedAt: Long           // epoch ms — for future TTL cleanup
)
```

Cache invalidation rule: a cached row is valid when `lastModified` and `fileSize` still match the live `MediaFile`. If either changes, the row is ignored and recomputed.

### 5.7 `DuplicatesViewModel` State & Events

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
User selects resources + taps "Scan"
        │
        ▼
DuplicatesFragment
        │  startScan(selectedResourceIds)
        ▼
DuplicatesViewModel
        │  enqueue DuplicateDetectionWorker via WorkManager
        │  observe WorkInfo.progress → update DuplicatesState.scanState
        ▼
DuplicateDetectionWorker   (runs in background)
        │  invoke DetectDuplicatesUseCase(resources)
        ▼
DetectDuplicatesUseCase
        │  1. GetMediaFilesUseCase(resource) ──────► list all MediaFile per resource
        │  2. group by fileSize (in memory)
        │  3. for same-size groups:
        │       ComputeFileHashUseCase(file, resource, maxBytes=65536)
        │             │  DuplicateHashRepository.getCachedPrefixHash(file)
        │             │    └─ DuplicateHashCacheDao.getByKey(resourceId, path, mod, size)
        │             │  if cache miss → FileHasher.computeHash(file, resource, 65536)
        │             │  DuplicateHashRepository.savePrefixHash(file, hash)
        │  4. discard prefix-unique; for prefix-match groups:
        │       ComputeFileHashUseCase(file, resource, maxBytes=-1)   [full hash]
        │  5. group by fullHash → DuplicateDetectionResult
        ▼
DuplicateDetectionWorker
        │  setProgress(workDataOf("phase", "done", "groupCount", N))
        │  store result in DuplicateHashRepository
        ▼
DuplicatesViewModel  ◄──── WorkInfo.State.SUCCEEDED + output data
        │  update DuplicatesState.result
        ▼
DuplicatesFragment
        │  DuplicateGroupAdapter renders groups
        ▼
User taps delete on a file
        │
DuplicatesViewModel.deleteFile(file, resource)
        │  DeleteFilesUseCase(file, resource)
        │  DuplicateHashRepository.deleteHashEntry(file)
        ▼
DuplicatesState updated (file removed from group; empty groups pruned)
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `data/local/db/AppDatabase.kt` | Add `DuplicateHashCacheEntity` to entities list; bump version 20 → 21; add `Migration_20_21`; expose `duplicateHashCacheDao()` | ~560 lines |
| `worker/WorkManagerScheduler.kt` | Add `enqueueDuplicateScan(resourceIds)` and `cancelDuplicateScan()` methods | ~180 lines |
| `ui/main/MainActivity.kt` (or navigation entry point) | Add menu item / FAB entry point that opens `DuplicatesFragment` | ~unchanged (menu XML only) |

`AppDatabase.kt` is currently 542 lines and will reach ~560 — still under 600, no extraction needed. Create a timestamped backup before modifying.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Hashing a 4 GB video over a 2 MB/s SMB link takes ~30 min | High | Two-phase strategy + prefix-only for Phase 1 limits network reads to 64 KB per file in the common case; show time estimate in UI |
| Worker killed by OS during long scan on low-RAM devices | Med | WorkManager `setForeground()` keeps process alive; partial progress saved to DB (cached hashes persist); next scan reuses cached results |
| SHA-256 collision falsely marks distinct files as duplicates | Very Low | Probability ≈ 10⁻⁷⁷ for two specific files; no mitigation needed beyond confirming "confirm delete" per file |
| Network connection drops mid-hash stream | Med | `try/catch` in each `FileHasher`; failed file gets `null` hash entry; reported as "skipped" in results summary |
| DB grows unboundedly with hash entries for deleted files | Low | `DuplicateHashCacheDao.deleteByResourceId(id)` called when a resource is removed; manual "Clear hash cache" in Settings → Advanced (future) |
| `SmbFileOperations.openInputStream` holds a connection open for full-file reads | Med | Uses existing `SmbConnectionManager` pooling; no new connection per file; same pattern used for thumbnails |
| `AppDatabase` migration fails on upgrade | Low | Migration is additive (new table only); `fallbackToDestructiveMigration` is not set, so migration must be correct — covered by unit test |

---

## 9. Testing Plan

### 9.1 Unit Tests

**`DetectDuplicatesUseCaseTest`** (`test/domain/usecase/`)
- `givenUniqueFileSizes_thenNoDuplicatesReturned` — size pre-filter works
- `givenSameSizeDifferentContent_thenNoDuplicatesAfterPrefixHash` — Phase 1 eliminates non-identical files
- `givenSameSizeSameContent_thenDuplicateGroupFormed` — Phase 2 identifies true duplicates
- `givenCachedHashValid_thenNoFileHasherCallMade` — cache hit avoids I/O
- `givenCachedHashStale_thenFileHasherCalled` — cache invalidated when `lastModified` changes

**`ComputeFileHashUseCaseTest`** (`test/domain/usecase/`)
- `givenLocalFile_hashMatchesExpectedSha256` — verifiable with known test asset
- `givenMaxBytes64k_thenOnlyPrefixRead` — `FileHasher.computeHash` invoked with `maxBytes=65536`

**`DuplicateHashRepositoryImplTest`** (`test/data/repository/`)
- `getCachedPrefixHash_returnsCachedEntry_whenKeyMatches`
- `getCachedPrefixHash_returnsNull_whenLastModifiedDiffers`
- `savePrefixHash_insertsRow_thenGetReturnsIt`

**`AppDatabaseMigrationTest`** (`androidTest/`)
- `migration_20_to_21_addsTable` — verify table creation via `MigrationTestHelper`

### 9.2 Manual Test Cases

1. **Happy path — local resource**: Create a folder with two identical images, add as resource, run scan → duplicate group appears with correct file sizes and paths.
2. **Happy path — SMB resource**: Copy a file to two paths on a Samba share, run scan → duplicate group appears after ~seconds (cached after first scan).
3. **Cache reuse**: After a successful scan, immediately run again → completion is near-instant (all hashes cached, no network reads).
4. **Cache invalidation**: Modify one file in a duplicate pair (change content, same name) → re-scan correctly removes it from the group.
5. **Cancellation**: Start scan on a large SMB resource, tap Cancel → worker stops, cached hashes so far are preserved, re-scan resumes from cached entries.
6. **Error state**: Disconnect SMB share mid-scan → error Snackbar appears; partial results shown for completed files.
7. **Delete action**: In a duplicate group of 3 files, delete file #2 → group shrinks to 2 files; file is no longer on disk.
8. **Network warning dialog**: Select a network resource → warning dialog appears before scan starts; cancel → no scan launched.
9. **No duplicates**: Scan a resource with all unique files → "No duplicates found" empty state shown.
10. **Legacy flavor (API 23)**: Run on an Android 6.0 emulator → scan completes without crash; hash computation works correctly.

### 9.3 Maestro E2E

A Maestro smoke test is not suitable for this feature — the test requires pre-seeded duplicate files and background worker timing, which are not reliably controllable in Maestro. No Maestro test added for this spec.

---

## 10. Accessibility

`DuplicatesFragment` adds interactive UI elements. All requirements:
- Each file entry in a duplicate group must have a content description: `"$filename, $size, in $resourceName"`.
- The "Delete" button per file must have `contentDescription = getString(R.string.action_delete_file, filename)`.
- The expand/collapse button per duplicate group must toggle `contentDescription` between `R.string.cd_expand_group` and `R.string.cd_collapse_group`.
- Group headers are non-focusable decorative headers; the first file card in each group receives focus order 1.
- Minimum touch target 48 dp is met by standard Material `MaterialButton` and `CardView` defaults.
- No color-only affordances — file count badge uses a number label, not color alone.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): Add under "File Operations" — "**Duplicate Detection** — scan one or more resources to find files with identical content; review duplicates grouped by size and delete unwanted copies."
- `docs/FEATURES_RU.md` (RU): "**Поиск дубликатов** — сканирование одного или нескольких ресурсов для поиска файлов с одинаковым содержимым; просмотр дубликатов, сгруппированных по размеру, с возможностью удаления."
- `docs/FEATURES_UK.md` (UK): "**Пошук дублікатів** — сканування одного або кількох ресурсів для виявлення файлів з однаковим вмістом; перегляд дублікатів, згрупованих за розміром, із можливістю видалення."

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: SHA-256 over MD5 for hashing**
- **Decision:** Use SHA-256 (`MessageDigest.getInstance("SHA-256")`) for both prefix and full hashes.
- **Alternatives considered:** MD5 (faster, 128-bit output); XXHash (fastest, requires third-party JNI dependency).
- **Reason:** SHA-256 is available on all Android API levels with zero dependencies. The performance difference on a mobile CPU for sequential file reads is negligible compared to network latency. MD5's higher collision probability (though still very low) adds unnecessary user-trust risk for a "safe to delete" assertion. No new library dependency is justified.

**ADR-2: Two-phase hashing (prefix then full) over single-pass full hash**
- **Decision:** Phase 1 reads only the first 64 KB; Phase 2 reads the full file only for prefix-matching candidates.
- **Alternatives considered:** Single-pass full hash (simpler code); size-only deduplication (zero I/O but too many false positives for burst photo sets).
- **Reason:** The primary risk in X.1 is network I/O. In practice, files of the same size with different content (e.g., burst photos shot in the same second) diverge within the first few KB. The 64 KB prefix eliminates >99% of same-size non-duplicates without reading multi-GB video files in full. Implementation complexity is low (two calls to the same `computeHash` API with different `maxBytes`).

**ADR-3: `FileHasher` as a new interface rather than extending `MediaScanner` or `FileAccess`**
- **Decision:** New `FileHasher` interface in `domain/hash/`, with per-protocol implementations in `data/hash/`, bound via Hilt `@IntoSet`.
- **Alternatives considered:** Adding `openInputStream()` to `MediaScanner` (would force all scanner implementations to be changed); adding `computeHash()` to `FileAccess` (that interface is for existence/delete only, mixing concerns).
- **Reason:** Single-responsibility. `MediaScanner` is already very large (Local: 785 LOC, SMB: 728 LOC). `FileHasher` implementations are thin wrappers (≤ 120 LOC each) that delegate to already-existing `openInputStream()` methods on `SmbFileOperations`, `SftpClient`, and `FtpClient`. Following the `TransferStrategy` multi-binding pattern keeps DI consistent.

**ADR-4: WorkManager for scan rather than inline coroutine in ViewModel**
- **Decision:** Scan always runs via `DuplicateDetectionWorker` (WorkManager one-time work), never as a ViewModel-launched coroutine.
- **Alternatives considered:** `viewModelScope.launch { }` inline (simpler, no worker class needed).
- **Reason:** Scanning a large SMB library can take several minutes. If the user navigates away or the process is killed, a ViewModel coroutine is cancelled and all progress is lost. WorkManager with foreground service survives process death; partially computed hashes are already persisted in Room, so the next scan resumes cheaply.

---

## 13. Implementation Steps

1. **Create `DuplicateModels.kt`** in `domain/model/` with `DuplicateGroup`, `DuplicateDetectionResult`, `DuplicateScanProgress`, `ScanPhase`.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/model/DuplicateModels.kt" "DuplicateModels" "Add domain models for duplicate detection"
   ```

2. **Create `FileHasher.kt`** interface in `domain/hash/`.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/hash/FileHasher.kt" "FileHasher" "Add FileHasher interface"
   ```

3. **Create `DuplicateHashRepository.kt`** interface in `domain/repository/` with methods: `getCachedPrefixHash`, `getCachedFullHash`, `savePrefixHash`, `saveFullHash`, `deleteByResourceId`.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/DuplicateHashRepository.kt" "DuplicateHashRepository" "Add DuplicateHashRepository interface"
   ```

4. **Backup `AppDatabase.kt`** before modification (file is 542 lines, > 500):
   ```powershell
   Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt" "temp/AppDatabase_backup_$(Get-Date -Format 'yyyyMMddHHmm').kt"
   ```

5. **Create `DuplicateHashCacheEntity.kt`** in `data/local/db/` with the schema from §5.6.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheEntity.kt" "DuplicateHashCacheEntity" "Add Room entity for duplicate hash cache"
   ```

6. **Create `DuplicateHashCacheDao.kt`** in `data/local/db/` with CRUD + lookup queries.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheDao.kt" "DuplicateHashCacheDao" "Add DAO for duplicate hash cache"
   ```

7. **Modify `AppDatabase.kt`**: add `DuplicateHashCacheEntity` to `entities`, bump `version` 20 → 21, add `Migration_20_21` (CREATE TABLE statement), expose `fun duplicateHashCacheDao(): DuplicateHashCacheDao`.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt" "AppDatabase" "Bump DB version to 21; add duplicate_hash_cache table"
   ```

8. **Create `DuplicateHashRepositoryImpl.kt`** in `data/repository/` implementing `DuplicateHashRepository`.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/DuplicateHashRepositoryImpl.kt" "DuplicateHashRepositoryImpl" "Add DuplicateHashRepository implementation"
   ```

9. **Create `LocalFileHasher.kt`** in `data/hash/` — streams via `contentResolver` or `FileInputStream`.
   ```
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/LocalFileHasher.kt" "LocalFileHasher" "Add SHA-256 hasher for local files"
   ```

10. **Create `SmbFileHasher.kt`** in `data/hash/` — delegates to `SmbFileOperations.openInputStream`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/SmbFileHasher.kt" "SmbFileHasher" "Add SHA-256 hasher for SMB files"
    ```

11. **Create `SftpFileHasher.kt`** in `data/hash/` — delegates to `SftpClient.openInputStream`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/SftpFileHasher.kt" "SftpFileHasher" "Add SHA-256 hasher for SFTP files"
    ```

12. **Create `FtpFileHasher.kt`** in `data/hash/` — delegates to `FtpClient.openInputStream`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/hash/FtpFileHasher.kt" "FtpFileHasher" "Add SHA-256 hasher for FTP files"
    ```

13. **Create `DuplicateHashModule.kt`** in `di/` with `@Binds` for `DuplicateHashRepository` and `@Binds @IntoSet` for all four `FileHasher` implementations.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/di/DuplicateHashModule.kt" "DuplicateHashModule" "Add Hilt module for duplicate hash bindings"
    ```

14. **Create `ComputeFileHashUseCase.kt`** in `domain/usecase/` — selects `FileHasher` by `resource.type`, checks cache first, calls hasher on miss, saves to cache.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ComputeFileHashUseCase.kt" "ComputeFileHashUseCase" "Add UseCase for per-file SHA-256 computation with caching"
    ```

15. **Create `DetectDuplicatesUseCase.kt`** in `domain/usecase/` — orchestrates Phase 0/1/2 as described in §5.1; emits `DuplicateScanProgress` via `Flow`; returns `DuplicateDetectionResult`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DetectDuplicatesUseCase.kt" "DetectDuplicatesUseCase" "Add orchestrating UseCase for duplicate detection"
    ```

16. **Create `DuplicateDetectionWorker.kt`** in `worker/` — `@HiltWorker`, `CoroutineWorker`, reads `resourceIds` from `inputData`, calls `DetectDuplicatesUseCase`, sets `setForeground()`, emits progress via `setProgress()`, stores result count in `outputData`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/worker/DuplicateDetectionWorker.kt" "DuplicateDetectionWorker" "Add WorkManager worker for background duplicate scan"
    ```

17. **Modify `WorkManagerScheduler.kt`**: add `enqueueDuplicateScan(resourceIds: List<Long>)` and `cancelDuplicateScan()`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt" "WorkManagerScheduler" "Add duplicate scan enqueue/cancel methods"
    ```

18. **Create `DuplicatesViewModel.kt`** in `ui/duplicates/` with state/events from §5.7.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesViewModel.kt" "DuplicatesViewModel" "Add ViewModel for duplicates screen"
    ```

19. **Create `DuplicateGroupAdapter.kt`** in `ui/duplicates/` — expandable `RecyclerView` adapter with nested file items and per-file delete button.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt" "DuplicateGroupAdapter" "Add RecyclerView adapter for duplicate groups"
    ```

20. **Create `DuplicatesFragment.kt`** in `ui/duplicates/` — thin Fragment; observes `DuplicatesState`; delegates all actions to ViewModel.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesFragment.kt" "DuplicatesFragment" "Add Duplicates screen Fragment"
    ```

21. **Create layout files**: `activity_duplicates.xml`, `fragment_duplicates.xml`, `item_duplicate_group.xml`, `item_duplicate_file.xml`.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_duplicates.xml" "layout" "Add layouts for duplicates screen"
    ```

22. **Add string resources** in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`:
    - `duplicate_detection_title` / "Duplicate Detection" / "Поиск дубликатов" / "Пошук дублікатів"
    - `duplicate_scan_start` / "Scan for Duplicates" / "Найти дубликаты" / "Знайти дублікати"
    - `duplicate_scan_network_warning` / "This will read files from network resources. Continue?" / etc.
    - `duplicate_none_found` / "No duplicates found" / etc.
    - `duplicate_wasted_bytes` / "%1$s wasted" / etc.
    - `cd_expand_group`, `cd_collapse_group`, `cd_delete_file` (for TalkBack)
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Add EN string resources for duplicate detection"
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "strings" "Add RU string resources for duplicate detection"
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "strings" "Add UK string resources for duplicate detection"
    ```

23. **Add notification channel** for `DuplicateDetectionWorker` (similar to `NOTIFICATION_CHANNEL_ID` in `ScheduledOperationsWorker`). Register in `AppStartupInitializer` or inline in the worker.

24. **Add menu entry** in the main navigation (toolbar menu or bottom navigation) to open `DuplicatesFragment`. Add `item` to the appropriate menu XML file.
    ```
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/menu/<menu_file>.xml" "menu" "Add Duplicate Detection entry to navigation"
    ```

25. **Update `docs/FEATURES.md`**, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` with the bullet from §11.
    ```
    .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "docs" "Document Duplicate Detection feature"
    ```

**Mandatory step checklist:**
- [ ] String resources added in EN (`values/`), RU (`values-ru/`), UK (`values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated
- [ ] Room DB migration `Migration_20_21` added and `version` bumped to 21 in `AppDatabase.kt`
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file

---

## 14. Out of Scope (future items)

- **Perceptual / fuzzy image deduplication** — requires pHash/dHash or a vision model; entirely different algorithm and risk profile.
- **"Keep one and delete rest" batch action** — risk of unintended mass deletion; deferred until user-trust is established via per-file delete.
- **CLOUD resource hashing** — cloud SDKs have per-API rate limits; streaming large files via Drive/OneDrive/Dropbox is cost-prohibitive without download quotas.
- **TTL-based hash cache cleanup** — hash entries for deleted files accumulate silently; a `Clear Hash Cache` option in Settings → Advanced is natural follow-up.
- **Progress persistence across process death** — the worker saves hashes to DB, but `DuplicateDetectionResult` is recomputed from scratch on next launch. Storing the full result set in DB would allow instant "last scan results" view.
- **Duplicate detection integrated into BrowseViewModel** — inline badge on thumbnail for known duplicates would require persistent result storage (see above).
- **Export duplicates report** — CSV/JSON export of found groups.
