# Specification: X.11 — Background Thumbnail Preload

**Status:** Draft
**Date:** 2026-03-28
**Tier:** 4 — Substantial (8-16h, notable risk)
**Roadmap entry:** WorkManager-based thumbnail pre-generation | Network traffic management; cache coordination

---

## 1. Problem Statement

Thumbnails for video and PDF files are generated **lazily** — on demand when cells become visible in `MediaFileAdapter`. For network resources (SMB/SFTP/FTP) this means each scroll event may trigger a network round-trip plus frame extraction, causing visible stutter and grey-placeholder flicker. Users who re-open a large network folder repeatedly pay the same loading cost every time.

The existing `ThumbnailCacheRepository` / `ThumbnailCacheRepositoryImpl` already provides persistent thumbnail storage (`filesDir/thumbnails/` + Room index with LRU eviction). The missing piece is a background `CoroutineWorker` that **proactively fills** that cache for a resource's file list before the user opens it.

---

## 2. Goals

1. **`ThumbnailPreloadWorker`** — `@HiltWorker CoroutineWorker` that pre-generates thumbnails for all video (+optionally PDF) files in a specified resource, using `ConnectionThrottleManager` for network safety and `ThumbnailCacheRepository` for persistence.
2. **`WorkManagerScheduler` additions** — `scheduleThumbnailPreload(resourceId)`, `cancelThumbnailPreload(resourceId)`, `cancelAllThumbnailPreloads()`.
3. **Network traffic management** — respect existing `ConnectionThrottleManager` per-protocol limits; add a user-facing "Wi-Fi only" constraint.
4. **Cache coordination** — skip files already cached; enforce size limit after each batch via `ThumbnailCacheRepository.enforceSizeLimit()`.
5. **Settings toggle** — `enableThumbnailPreload` (default: off) + `thumbnailPreloadWifiOnly` (default: on) in `AppSettings`.
6. **Automatic trigger** — enqueue preload after each successful `NetworkFilesSyncWorker` run.

Non-goals for this spec: per-resource manual "Preload now" UI button; progress notification; cloud providers (OAuth incompatible with background workers); preload for local files (extraction is instantaneous); real-time scroll-ahead prefetch in `MediaFileAdapter`.

---

## 3. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `ThumbnailCacheRepository` | `domain/repository/ThumbnailCacheRepository.kt` | Interface: `getCachedThumbnail`, `saveThumbnail`, `cleanupOldThumbnails`, `enforceSizeLimit` |
| `ThumbnailCacheRepositoryImpl` | `data/repository/ThumbnailCacheRepositoryImpl.kt` | Stores JPEG files in `filesDir/thumbnails/`; Room DB index; LRU eviction |
| `ThumbnailCacheDao` | `data/local/db/ThumbnailCacheDao.kt` | Room queries: insert, get by path, LRU order, bulk delete |
| `ThumbnailCacheEntity` | `data/local/db/ThumbnailCacheEntity.kt` | `filePath`, `thumbnailPath`, `fileSize`, `createdAt`, `lastAccessedAt` |
| `MediaFileAdapter` | `ui/browse/MediaFileAdapter.kt` | Loads thumbnails reactively via Glide on cell bind; reads `showVideoThumbnails` / `showPdfThumbnails` settings |
| `ConnectionThrottleManager` | `data/network/ConnectionThrottleManager.kt` | Per-protocol semaphores: SMB=2, SFTP=3, FTP=2, CLOUD=8 concurrent |
| `WorkManagerScheduler` | `worker/WorkManagerScheduler.kt` | Schedules all periodic/one-time WorkManager tasks (268 lines) |
| `NetworkFilesSyncWorker` | `worker/NetworkFilesSyncWorker.kt` | `@HiltWorker CoroutineWorker`; syncs file lists for all resources (162 lines) |
| `CachedFileListRepository` | `data/repository/CachedFileListRepository.kt` | Stores/loads GZIP-compressed JSON file lists per resource |
| `CalculateOptimalCacheSizeUseCase` | `domain/usecase/CalculateOptimalCacheSizeUseCase.kt` | Returns optimal Glide disk cache in MB based on available storage |
| `AppSettings` | `domain/model/AppSettings.kt` | `showVideoThumbnails`, `showPdfThumbnails`, `cacheSizeMb`, `enableBackgroundSync` exist; no preload toggle yet |

**Key limitation:** `MediaFileAdapter` triggers thumbnail extraction reactively via Glide. Glide's `NetworkFileDataFetcher` and `NetworkPdfThumbnailLoader` run on Glide's internal thread pool, which is designed for UI-responsive loading, not bulk background pre-generation. `ThumbnailCacheRepository` is ready to serve pre-cached files but nothing fills it proactively.

---

## 4. Proposed Architecture

### 4.1 `ThumbnailPreloadWorker`

New `@HiltWorker CoroutineWorker` in `worker/`. Input data: `resourceId: Long`. Optional input: `maxItems: Int` (default 200, caps work duration per run).

```kotlin
@HiltWorker
class ThumbnailPreloadWorker @AssistedInject constructor(
    @Assisted applicationContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val cachedFileListRepository: CachedFileListRepository,
    private val resourceRepository: ResourceRepository,
    private val thumbnailCacheRepository: ThumbnailCacheRepository,
    private val calculateOptimalCacheSizeUseCase: CalculateOptimalCacheSizeUseCase,
    private val thumbnailExtractorHelper: ThumbnailExtractorHelper
) : CoroutineWorker(applicationContext, workerParams) {

    companion object {
        const val KEY_RESOURCE_ID = "resource_id"
        const val KEY_MAX_ITEMS = "max_items"
        const val DEFAULT_MAX_ITEMS = 200
        fun workName(resourceId: Long) = "thumbnail_preload_$resourceId"
    }

    override suspend fun doWork(): Result { ... }
}
```

**Worker logic:**
1. Read `AppSettings`; abort with `Result.success()` if `enableThumbnailPreload == false`.
2. Load file list from `CachedFileListRepository.getCachedFiles(resourceId)`; if empty/null, return `Result.success()` (preload is opportunistic — no-op if no cached list).
3. Verify resource still exists in `ResourceRepository`; if deleted, return `Result.success()`.
4. Filter to video + PDF files respecting `showVideoThumbnails` / `showPdfThumbnails` settings. Take up to `maxItems`.
5. For each candidate file:
   - `thumbnailCacheRepository.getCachedThumbnail(file.path)` — skip if already cached.
   - Determine `ConnectionThrottleManager.ProtocolLimits` from resource type (SMB → `SMB`, SFTP → `SFTP`, FTP → `FTP`, local → `LOCAL`).
   - `ConnectionThrottleManager.withThrottle(protocol, host) { thumbnailExtractorHelper.extract(file) }` → `File?`
   - If non-null: `thumbnailCacheRepository.saveThumbnail(file.path, jpegFile)`.
   - Check `isStopped` after every file — exit cleanly if WorkManager cancelled the job.
6. After batch: `thumbnailCacheRepository.enforceSizeLimit(maxBytes)` where `maxBytes = calculateOptimalCacheSizeUseCase() * 1024L * 1024L / 2` (50% of optimal Glide cache size).

### 4.2 `ThumbnailExtractorHelper`

New injectable class in `worker/`. Extracts a thumbnail from a network file **without Glide**. The Glide loaders (`NetworkPdfThumbnailLoader`, `NetworkFileDataFetcher`) are not suitable for direct use in workers — they use `runBlocking` and are designed for RecyclerView-bound lifecycles.

```kotlin
@Singleton
class ThumbnailExtractorHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val unifiedFileCache: UnifiedFileCache
) {
    /** Download file from network and extract a thumbnail JPEG. Returns null on any error. */
    suspend fun extract(file: MediaFile): File? { ... }

    private suspend fun extractVideoThumbnail(localFile: File, outputFile: File): Boolean { ... }
    private suspend fun extractPdfThumbnail(localFile: File, outputFile: File): Boolean { ... }
}
```

**Protocol-specific download path:**
- SMB (`smb://`) → `smbClient.downloadToTemp(file.path, unifiedFileCache)` — returns a local `File` in `UnifiedFileCache`.
- SFTP (`sftp://`) → `sftpClient.downloadToTemp(...)`.
- FTP (`ftp://`) → `ftpClient.downloadToTemp(...)`.
- Local (`file://`) → use file directly, no download.

For video: `MediaMetadataRetriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)` → compress to JPEG 80%, write to `filesDir/thumbnails/<uuid>.jpg`.
For PDF: `PdfRenderer(ParcelFileDescriptor.open(localFile, READ_ONLY)).openPage(0)` → render at 256×256 → compress to JPEG 80%.

Failures (network error, unsupported format, OOM) are caught per-file and return `null` — the worker continues to the next file.

### 4.3 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `ThumbnailPreloadWorker.kt` | `worker/` | ≤ 220 |
| `ThumbnailExtractorHelper.kt` | `worker/` | ≤ 200 |

### 4.4 New `AppSettings` fields

```kotlin
val enableThumbnailPreload: Boolean = false,       // Background thumbnail pre-generation
val thumbnailPreloadWifiOnly: Boolean = true,      // Restrict preload to unmetered (Wi-Fi) connections
```

Default `false` for `enableThumbnailPreload` — feature is opt-in because it can consume significant network bandwidth.

### 4.5 `WorkManagerScheduler` additions

```kotlin
fun scheduleThumbnailPreload(resourceId: Long, wifiOnly: Boolean = true) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()
    val inputData = Data.Builder()
        .putLong(ThumbnailPreloadWorker.KEY_RESOURCE_ID, resourceId)
        .build()
    val request = OneTimeWorkRequestBuilder<ThumbnailPreloadWorker>()
        .setConstraints(constraints)
        .setInputData(inputData)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag("thumbnail_preload")
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        ThumbnailPreloadWorker.workName(resourceId),
        ExistingWorkPolicy.KEEP,   // Don't re-queue if already running
        request
    )
}

fun cancelThumbnailPreload(resourceId: Long) {
    WorkManager.getInstance(context).cancelUniqueWork(ThumbnailPreloadWorker.workName(resourceId))
}

fun cancelAllThumbnailPreloads() {
    WorkManager.getInstance(context).cancelAllWorkByTag("thumbnail_preload")
}
```

### 4.6 Automatic trigger in `NetworkFilesSyncWorker`

After a successful resource sync (currently `Result.success()` at end of `doWork()`), enqueue a preload for each synced network resource:

```kotlin
// Inject WorkManagerScheduler and SettingsRepository into NetworkFilesSyncWorker
if (settings.enableThumbnailPreload) {
    networkResources.forEach { resource ->
        workManagerScheduler.scheduleThumbnailPreload(resource.id, settings.thumbnailPreloadWifiOnly)
    }
}
```

`NetworkFilesSyncWorker` already injects `SettingsRepository`; add `WorkManagerScheduler` injection.

---

## 5. Data Flow

```
[Trigger A: NetworkFilesSyncWorker success]
[Trigger B: First browse scan complete (future, out of scope for this spec)]
  ↓
WorkManagerScheduler.scheduleThumbnailPreload(resourceId, wifiOnly)
  ↓ (WorkManager: background, constraints: wifi/battery/storage)
ThumbnailPreloadWorker.doWork()
  ↓ load CachedFileListRepository.getCachedFiles(resourceId)
  ↓ filter: video + PDF, not yet in ThumbnailCacheRepository
  ↓ for each file:
      ConnectionThrottleManager.withThrottle(protocol, host) {
          ThumbnailExtractorHelper.extract(file)
              → download to UnifiedFileCache (SMB/SFTP/FTP/local)
              → MediaMetadataRetriever or PdfRenderer → JPEG
              → filesDir/thumbnails/<uuid>.jpg
      }
  ↓ ThumbnailCacheRepository.saveThumbnail(filePath, jpegFile)
  ↓ (Room insert + path stored)
  ↓ after batch: ThumbnailCacheRepository.enforceSizeLimit(maxBytes)
                                               ↑
MediaFileAdapter.onBindViewHolder():
  ThumbnailCacheRepository.getCachedThumbnail(filePath)
    → cache HIT → load from filesDir/thumbnails/ via Glide → instant display
    → cache MISS → fall back to on-demand Glide network load (existing behaviour)
```

---

## 6. Files to Modify

| File | Change |
|------|--------|
| `domain/model/AppSettings.kt` | Add `enableThumbnailPreload`, `thumbnailPreloadWifiOnly` fields |
| `data/repository/SettingsRepositoryImpl.kt` | Add DataStore `booleanPreferencesKey` entries; read/write for 2 new settings (642 lines → backup required) |
| `worker/WorkManagerScheduler.kt` | Add 3 new methods: `scheduleThumbnailPreload`, `cancelThumbnailPreload`, `cancelAllThumbnailPreloads` |
| `worker/NetworkFilesSyncWorker.kt` | Inject `WorkManagerScheduler`; enqueue preload per network resource on success |
| `ui/settings/fragments/GeneralSettingsFragment.kt` | Add "Preload thumbnails" toggle + "Wi-Fi only" sub-toggle (near cache size and sync settings) |
| `domain/usecase/BackupData.kt` | Add `enableThumbnailPreload`, `thumbnailPreloadWifiOnly` fields with safe defaults |
| `domain/usecase/BackupMapper.kt` | Map 2 new fields in both directions |
| `domain/usecase/ExportSettingsUseCase.kt` | Export 2 new fields as XML elements |
| `domain/usecase/ImportSettingsUseCase.kt` | Parse 2 new fields from XML |

---

## 7. Risk Analysis

| Risk | Mitigation |
|------|-----------|
| Excessive network bandwidth on metered connections | `thumbnailPreloadWifiOnly = true` by default; `NetworkType.UNMETERED` WorkManager constraint enforces this |
| Worker exceeds 10-minute WorkManager limit | Cap at `maxItems = 200`; check `isStopped` after every file; partially completed runs are free to resume (already-cached files skipped) |
| SMB connection pool exhaustion during preload | `ConnectionThrottleManager.withThrottle(SMB, host)` limits to 2 concurrent; worker processes files sequentially (one protocol call at a time) |
| Cache grows unbounded | `enforceSizeLimit()` called after each batch; cap = 50% of `CalculateOptimalCacheSizeUseCase` result |
| Preload interferes with active browse session | WorkManager runs on background thread; `ConnectionThrottleManager` semaphores limit per-host concurrency; Glide UI requests have their own thread pool and are unaffected |
| `UnifiedFileCache` fills up with worker temp files | `ThumbnailExtractorHelper` deletes temp file from `UnifiedFileCache` after thumbnail JPEG is written (same pattern as `NetworkPdfThumbnailLoader`) |
| Resource deleted while worker runs | Worker checks `ResourceRepository.getResourceById(resourceId)` at start; null → `Result.success()` immediately |
| `MediaMetadataRetriever` OOM for large video files | Catch `OutOfMemoryError` per file; log via Timber; return `null` from extractor; worker continues to next file |
| Circular enqueue: sync → preload → sync? | `ThumbnailPreloadWorker` does not trigger sync; one-directional dependency only |

---

## 8. Implementation Steps

1. **Backup `SettingsRepositoryImpl.kt`** (642 lines, >500 threshold):
   ```powershell
   cp app_v2/.../SettingsRepositoryImpl.kt temp/SettingsRepositoryImpl_backup_20260328.kt
   ```
2. **Add `AppSettings` fields** — `enableThumbnailPreload: Boolean = false`, `thumbnailPreloadWifiOnly: Boolean = true`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/.../domain/model/AppSettings.kt" "AppSettings" "Add enableThumbnailPreload + thumbnailPreloadWifiOnly fields"
   ```
3. **Update `SettingsRepositoryImpl`** — add `KEY_ENABLE_THUMBNAIL_PRELOAD` and `KEY_THUMBNAIL_PRELOAD_WIFI_ONLY` DataStore keys; add read/write in `getSettings()` and `saveSettings()`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/.../data/repository/SettingsRepositoryImpl.kt" "SettingsRepositoryImpl" "Add DataStore keys for thumbnail preload settings"
   ```
4. **Create `ThumbnailExtractorHelper.kt`** — injectable `@Singleton`; download via protocol-specific client + `UnifiedFileCache`; extract via `MediaMetadataRetriever` (video) or `PdfRenderer` (PDF); return JPEG `File?`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/.../worker/ThumbnailExtractorHelper.kt" "ThumbnailExtractorHelper" "New: extract thumbnail from network file without Glide"
   ```
5. **Create `ThumbnailPreloadWorker.kt`** — `@HiltWorker CoroutineWorker`; reads settings; loads file list; skips cached; throttles via `ConnectionThrottleManager`; saves via `ThumbnailCacheRepository`; enforces size limit.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/.../worker/ThumbnailPreloadWorker.kt" "ThumbnailPreloadWorker" "New: background thumbnail pre-generation worker"
   ```
6. **Update `WorkManagerScheduler`** — add `scheduleThumbnailPreload()`, `cancelThumbnailPreload()`, `cancelAllThumbnailPreloads()`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/.../worker/WorkManagerScheduler.kt" "WorkManagerScheduler" "Add thumbnail preload schedule/cancel methods"
   ```
7. **Update `NetworkFilesSyncWorker`** — inject `WorkManagerScheduler`; after successful sync, enqueue `ThumbnailPreloadWorker` for each network resource if `settings.enableThumbnailPreload`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/.../worker/NetworkFilesSyncWorker.kt" "NetworkFilesSyncWorker" "Trigger thumbnail preload after successful resource sync"
   ```
8. **Update `BackupData.kt`** — add `enableThumbnailPreload: Boolean = false`, `thumbnailPreloadWifiOnly: Boolean = true`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/.../domain/usecase/BackupData.kt" "BackupData" "Add thumbnail preload fields to backup payload"
   ```
9. **Update `BackupMapper.kt`**, `ExportSettingsUseCase.kt`, `ImportSettingsUseCase.kt` — map, export, import 2 new fields.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/.../domain/usecase/BackupMapper.kt" "BackupMapper" "Map enableThumbnailPreload + thumbnailPreloadWifiOnly"
   ```
10. **Add string resources** — 4 strings in EN/RU/UK (`strings.xml`, `strings-ru.xml`, `strings-uk.xml`):
    - `thumbnail_preload_title` / `thumbnail_preload_summary`
    - `thumbnail_preload_wifi_only_title` / `thumbnail_preload_wifi_only_summary`
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Add thumbnail preload EN strings"
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "strings-ru" "Add thumbnail preload RU strings"
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "strings-uk" "Add thumbnail preload UK strings"
    ```
11. **Update `GeneralSettingsFragment`** — add "Preload thumbnails" `SwitchCompat` toggle and "Wi-Fi only" sub-toggle (below sync interval, matching visual pattern of `enableBackgroundSync`). Bind to `viewModel.settings`.
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/.../ui/settings/fragments/GeneralSettingsFragment.kt" "GeneralSettingsFragment" "Add thumbnail preload toggle and Wi-Fi only sub-option"
    ```
12. **Add layout elements** to `fragment_settings_general.xml` — 2 new switch rows (same style as existing sync toggle rows).
    ```powershell
    .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_settings_general.xml" "fragment_settings_general" "Add thumbnail preload toggle rows"
    ```

---

## 9. Out of Scope (future items)

- Per-resource manual "Preload now" button in the resource edit or info dialog.
- Progress notification showing how many thumbnails have been pre-generated.
- Cloud providers (Google Drive, Dropbox, OneDrive) — OAuth token management is incompatible with unattended background workers.
- First-browse trigger (enqueueing preload at end of `BrowseViewModel.scanFiles()`) — deferred to keep this spec focused on the sync trigger.
- Priority ordering (preloading recently-accessed resources first).
- Preload budget per resource (e.g. limit to first N files sorted by name/date).
- Real-time scroll-ahead prefetch in `MediaFileAdapter` (that requires an in-process queue, not a WorkManager task).
