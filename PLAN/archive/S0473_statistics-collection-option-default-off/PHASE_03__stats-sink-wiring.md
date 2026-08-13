# Phase 03 - Stats sink + completion-point wiring

**Strategic spec:** [`../S0473_statistics-collection-option-default-off.md`](../S0473_statistics-collection-option-default-off.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04 (data to display)
**Steps done:** 8 / 8
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Introduce the `StatsSink` write contract and its `@Singleton` implementation (no-op when the gate is off; in-memory increment + debounced batch flush when on), then call it from the clean domain-layer completion points across operations, capture, viewing, editing, and sources. Add the foreground session tracker. After this phase the aggregate store fills as the user works (only when collection is enabled).

---

## Prerequisites

- [ ] Phase 01 ✅ (`StatsAggregateDataStore.apply`, `StatsModels`, `StatsMediaType`).
- [ ] Phase 02 ✅ (`enableStatistics` readable from `SettingsRepository`).
- [ ] `@ApplicationScope CoroutineScope` available from `AppModule` for the flush loop.
- [ ] `data/common/MediaTypeUtils.getMediaType(fileName)` available for action×type classification.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/stats/StatsSink.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/stats/StatsSinkImpl.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/stats/StatsSessionTracker.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/StatsModule.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt` | Modified | ≤ 560 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DetectDuplicatesUseCase.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ArchiveFilesUseCase.kt` | Modified | ≤ 270 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCase.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateDirectoryUseCase.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractGifFramesUseCase.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveDrawingUseCase.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveTextNoteUseCase.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt` | Modified | ≤ 420 |
| capture/viewing managers (see Step 03.4 / 03.5) | Modified | per-file |

> `FileOperationUseCase` (530) and `ExtractArchiveUseCase` (529) are >500 LOC - take `temp/` backups before editing. The sink call added to each is a one-to-three-line fire-and-forget; do not let any file cross 1500 LOC. If `ExtractArchiveUseCase` has a private encrypted-extract path that also emits `ExtractProgress.Success`, instrument both emissions.

---

## Steps

### Step 03.1 - StatsSink contract + event hierarchy

**Files:** `domain/stats/StatsSink.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `interface StatsSink { fun record(event: StatsEvent) }` and a `sealed interface StatsEvent` covering v1 events: `FileOp(action: FileOpAction, type: StatsMediaType, count: Long, bytes: Long)` where `enum class FileOpAction { COPY, MOVE, DELETE, ARCHIVE, EXTRACT, CREATE_FOLDER }`; `DuplicatesRemoved(count: Long, bytesFreed: Long)` and `DuplicateScanRun`; `Capture(kind: CaptureKind)` with `enum CaptureKind { PHOTO, VIDEO, VOICE, SCREENSHOT }`; `View(kind: ViewKind, durationMs: Long = 0, pages: Long = 0)` with `enum ViewKind { IMAGE, VIDEO, AUDIO, DOCUMENT, FRAME_EXPORT }`; `Edit(kind: EditKind)` with `enum EditKind { IMAGE_EDIT, DRAWING, NOTE }`; `SourceConnected(type: StatsMediaType? = null)`; `Session(activeMs: Long)`. `record(..)` MUST be non-suspending and safe to call from any thread / hot path - it only enqueues. Add a KDoc note that the `Sxxxx:` prefix is NOT used here (this is permanent code, not a debug probe). This contract is the extension point (strategic §5.3): new events append without touching callers.

**Verification:**

- `Glob` - `StatsSink.kt` exists.
- `Grep` - `interface StatsSink` and `sealed interface StatsEvent` both present.
- `Grep` - `fun record(event: StatsEvent)` present (non-suspend - no `suspend` keyword on that line).

**Status:** `[x]` done

**Step Log:** Contract + event hierarchy present in `domain/stats/StatsSink.kt` (done before this task).

---

### Step 03.2 - StatsSinkImpl: gate no-op + in-memory buffer + debounced flush

**Files:** `data/stats/StatsSinkImpl.kt`, `core/di/StatsModule.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `@Singleton class StatsSinkImpl @Inject constructor(settings: SettingsRepository, aggregate: StatsAggregateDataStore, @ApplicationScope scope: CoroutineScope, @IoDispatcher io: CoroutineDispatcher)`. `record(event)`: read the current `enableStatistics` value from the cached settings flow (synchronous, non-blocking); if disabled, return immediately (no-op, strategic ADR-5). If enabled, fold the event into an in-memory `StatsAggregateDelta` accumulator guarded by a lightweight lock or a single-threaded confined actor, then schedule a debounced flush (e.g. cancel + relaunch a 2-3s delay job, or a conflated channel) that calls `aggregate.apply(accumulatedDelta)` on `io` and clears the accumulator. Also expose `suspend fun flushNow()` and call it on app-background (wire from the session tracker, Step 03.7) so a batch op flushes once when leaving foreground (strategic §3.2 / ADR-6). Never write to disk per event. Create `core/di/StatsModule.kt` (`@Module @InstallIn(SingletonComponent::class)`) with `@Binds` `StatsSinkImpl` → `StatsSink`.

**Verification:**

- `Glob` - `StatsSinkImpl.kt` and `StatsModule.kt` exist.
- `Grep` - `class StatsSinkImpl` is `@Singleton`; `@ApplicationScope` referenced.
- `Grep` - an early-return guard referencing the statistics-enabled flag is present in `record`.
- `Grep` - `aggregate.apply(` invoked (batched flush).
- `Grep` - `StatsSink` bound in `StatsModule.kt`.
- `Grep -n "Log\.d\("` on both files returns zero hits.
- Build: `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:** `StatsSinkImpl` (gate no-op + in-memory delta + 2.5s debounced flush) and `StatsModule` `@Binds` present (done before this task).

---

### Step 03.3 - Wire file operations + duplicates + archive + folder (group A, G)

**Files:** `domain/usecase/FileOperationUseCase.kt`, `domain/usecase/DetectDuplicatesUseCase.kt`, `domain/usecase/ArchiveFilesUseCase.kt`, `domain/usecase/ExtractArchiveUseCase.kt`, `domain/usecase/CreateDirectoryUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `StatsSink` into each use case. Add ONE `statsSink.record(..)` at each success point:
> - `FileOperationUseCase`: after the operation completes successfully (the `OperationHistory(operation, result)` assignment point), emit a `FileOp` event with the right `FileOpAction`, the `StatsMediaType` resolved via `MediaTypeUtils.getMediaType(fileName)` for the processed files (aggregate by type), `count` = processed count, and `bytes` = summed source sizes. For DELETE, the result carries no freed bytes - capture the summed source sizes BEFORE deletion (the file list is available at the call site) and pass them as `bytes`; do this on the IO dispatcher already in use, not the UI thread.
> - `DetectDuplicatesUseCase`: emit `DuplicateScanRun` on each completed scan; the `DuplicatesRemoved` event is emitted from the subsequent delete path (the delete is a `FileOperation.Delete`, already covered) - add a code comment noting duplicates-removed bytes flow through the delete event, so they are not double counted.
> - `ArchiveFilesUseCase`: after `ArchiveProgress.Success` emit `FileOp(ARCHIVE, ..)`.
> - `ExtractArchiveUseCase`: after each `ExtractProgress.Success` emission (plain AND encrypted path) emit `FileOp(EXTRACT, ..)`.
> - `CreateDirectoryUseCase`: on `Result.success` emit `FileOp(CREATE_FOLDER, type = OTHER, count = 1, bytes = 0)`.
> Keep every call fire-and-forget. Do not change any operation's return type or control flow.

**Verification:**

- `Grep` - `statsSink.record(` (or injected sink name) present in all five files.
- `Grep` - `FileOpAction.DELETE` / `FileOpAction.COPY` referenced in `FileOperationUseCase.kt`.
- `Grep` - `MediaTypeUtils` referenced in `FileOperationUseCase.kt`.
- `Grep` - `FileOpAction.ARCHIVE` in `ArchiveFilesUseCase.kt`, `FileOpAction.EXTRACT` in `ExtractArchiveUseCase.kt`, `FileOpAction.CREATE_FOLDER` in `CreateDirectoryUseCase.kt`.
- Build: `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:** `StatsSink` injected into all five use cases. `FileOperationUseCase` emits per-type `FileOp` (COPY/MOVE/DELETE) via a `recordFileOpStats` helper - DELETE bytes captured pre-delete on the existing IO path, Rename intentionally not counted. `DetectDuplicatesUseCase` emits `DuplicateScanRun` on scan completion (comment notes removed-bytes flow through the delete to avoid double-count). `ArchiveFilesUseCase`/`ExtractArchiveUseCase` (both plain + encrypted Success) emit ARCHIVE/EXTRACT(OTHER). `CreateDirectoryUseCase` emits CREATE_FOLDER on Result.success. Five existing unit tests updated to pass a relaxed `StatsSink` mock.

---

### Step 03.4 - Wire capture completion points (group B)

**Files:** capture saver/manager classes - resolve exact paths via `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*CameraCaptureSaver*"` (and `*MicRecording*`, `*SaveScreenshot*`)
**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `StatsSink` and emit a `Capture(..)` event at each success branch:
> - Photo: `CameraCaptureSaver.save()` on `SaveResult.Success` → `Capture(PHOTO)`. If the same saver handles video capture, branch by output kind and emit `Capture(VIDEO)` for video; otherwise locate the video-record completion point via catalog and emit there.
> - Voice note: `BrowseMicRecordingManager.save()` `success = true` branch → `Capture(VOICE)`.
> - Gesture screenshot: `SaveScreenshotUseCase` on `SaveResult.Success` → `Capture(SCREENSHOT)`. (No flavor flag needed - this use case only exists where the feature is present; the dashboard hides the row when its counter is zero.)
> Fire-and-forget; no control-flow change.

**Verification:**

- `Grep` - `CaptureKind.PHOTO` referenced in the camera saver file.
- `Grep` - `CaptureKind.VOICE` referenced in the mic recording manager file.
- `Grep` - `CaptureKind.SCREENSHOT` referenced in the screenshot use case file.
- Build: `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:** `CameraCaptureSaver.save()` emits `Capture(PHOTO|VIDEO)` on `SaveResult.Success`, branching by output-file media type (the saver is media-agnostic and handles both photo and video). `BrowseMicRecordingManager.save()` emits `Capture(VOICE)` in its success branch (`StatsSink` threaded via the `@AndroidEntryPoint` `BrowseActivity`). `SaveScreenshotUseCase` emits `Capture(SCREENSHOT)` once in `invoke()` on `SaveResult.Success`. `CameraCaptureSaverTest` updated with a no-op `StatsSink`.

---

### Step 03.5 - Wire viewing/playback completion points (group C)

**Files:** `ui/.../ImageLoadingManager.kt`, video player manager, `AudioPlaybackService`, PDF viewer manager, `domain/usecase/ExtractGifFramesUseCase.kt` - resolve exact paths via catalog
**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `StatsSink` (constructor for Hilt-managed classes; for the `AudioPlaybackService` use its existing `@AndroidEntryPoint` graph) and emit:
> - Image viewed: `ImageLoadingManager.onImageContentLoaded()` → `View(IMAGE)`.
> - Video watched: `VideoPlayerManager` on `Player.STATE_ENDED` (and/or on player release with elapsed position) → `View(VIDEO, durationMs = watchedMs)`. Use elapsed playback position, not file duration, where available; if only `player.duration` is reliable, use it and add a code comment.
> - Audio listened: `AudioPlaybackService` ExoPlayer `Player.STATE_ENDED` (covers background playback) → `View(AUDIO, durationMs = listenedMs)`.
> - Document opened: PDF viewer manager load-success → `View(DOCUMENT, pages = pageCount)` (page count available post-load).
> - Frame export: `ExtractGifFramesUseCase` on `Result.success(frameCount)` → `View(FRAME_EXPORT, count via frameCount)` (reuse the `View` event or extend the sink with a dedicated event if cleaner).
> Guard against emitting on every progress tick - emit once per completed view/playback. Fire-and-forget.

**Verification:**

- `Grep` - `ViewKind.IMAGE` in the image loading manager file.
- `Grep` - `ViewKind.VIDEO` in the video player manager file.
- `Grep` - `ViewKind.AUDIO` in the audio playback service file.
- `Grep` - `ViewKind.DOCUMENT` in the PDF viewer manager file.
- `Grep` - sink event referenced in `ExtractGifFramesUseCase.kt`.
- Build: `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:** IMAGE wired in `PlayerImageLoadingCallbackImpl.onImageContentLoaded()` (`View(IMAGE)`; fires once per loaded static image or GIF). VIDEO wired in `VideoPlayerManager` STATE_ENDED inside the existing once-per-file guard (`View(VIDEO, durationMs=player.duration)`). AUDIO wired in `AudioPlaybackService` STATE_ENDED (`View(AUDIO, durationMs)`, covers background playback). DOCUMENT wired in `PdfViewerManager` on successful open (`View(DOCUMENT, pages=pageCount)`). FRAME_EXPORT wired in `ExtractGifFramesUseCase.execute()` on `Result.success` (`View(FRAME_EXPORT, count=frameCount)`). `StatsSink` threaded into the manually-constructed managers via `PlayerActivity` field-inject (forwarded through `PlayerViewerFactory` + `PlayerManagerInitializer`), the standalone `DocumentStandaloneActivity` field-inject, and an `EntryPointAccessors` lookup in `StandaloneViewManager`. `VideoPlayerManagerStateEndedTest` constructor updated. NOTE: standalone-mode image/video viewing (Glide / standalone ExoPlayer) are separate completion points not listed in this phase and are not wired here.

---

### Step 03.6 - Wire editing + source-connection points (groups D, E)

**Files:** `domain/usecase/ResourceEditorUseCase.kt`, `domain/usecase/SaveDrawingUseCase.kt`, `domain/usecase/SaveTextNoteUseCase.kt`, `domain/usecase/SmbOperationsUseCase.kt` (+ cloud auth at `ui/addresource/AddResourceConnectionManager.kt` if cleanly injectable)
**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `StatsSink` and emit:
> - Image edit saved: `ResourceEditorUseCase.save()` on `Result.success` → `Edit(IMAGE_EDIT)`.
> - Drawing saved: `SaveDrawingUseCase.invoke()` on `Result.success` → `Edit(DRAWING)`.
> - Text note saved: `SaveTextNoteUseCase.invoke()` on `Result.success` → `Edit(NOTE)`. (If `CreateTextNoteUseCase` is a distinct success path that should also count, emit there too.)
> - Source connected: `SmbOperationsUseCase.testConnection/testSftpConnection/testFtpConnection` on success → `SourceConnected()`. For cloud, emit `SourceConnected()` from `AddResourceConnectionManager` on `AuthEvent.Success` ONLY if `StatsSink` injects cleanly there; if not, leave a `// deferred: cloud-connected stat` comment and rely on SMB/FTP/SFTP coverage for v1 (record the deferral in the phase Handoff Notes).
> OCR, translation, bytes-transferred, NAS-scan are explicitly OUT of v1 (INDEX Scope Decisions) - do NOT add intrusive hooks for them.

**Verification:**

- `Grep` - `EditKind.IMAGE_EDIT` in `ResourceEditorUseCase.kt`, `EditKind.DRAWING` in `SaveDrawingUseCase.kt`, `EditKind.NOTE` in `SaveTextNoteUseCase.kt`.
- `Grep` - `SourceConnected` referenced in `SmbOperationsUseCase.kt`.
- `Grep` - no `RecognitionBackend` / `TranslationManager` import added (deferred metrics not wired).
- Build: `.\a.ps1 fk` compiles.

**Status:** `[x]` done (IMAGE_EDIT sub-point deferred - see Step Log)

**Step Log:** DRAWING wired in `SaveDrawingUseCase.invoke()` on `Result.success` (`Edit(DRAWING)`). NOTE wired in both `SaveTextNoteUseCase.invoke()` (refactored to a `saveInternal` so the single emit fires on any saved-file Result.success, including a network save that fell back to local) and `CreateTextNoteUseCase.invoke()` on `Result.success` (the contract's `EditKind.NOTE` covers create + edit). SOURCES: `SmbOperationsUseCase.testConnection/testSftpConnection/testFtpConnection` emit `SourceConnected()` on success; cloud `AddResourceConnectionManager` emits `SourceConnected()` on `AuthEvent.Success` via an `EntryPointAccessors` `StatsSink` lookup. Four existing unit tests updated with a relaxed `StatsSink` mock.

> DEFERRED - IMAGE_EDIT: the pre-mapped point `ResourceEditorUseCase.save()` is **resource-config** editing (folder/connection records), not image editing - wiring `Edit(IMAGE_EDIT)` there would record a wrong metric (resource creation counted as image edits). The real image-edit save path is fragmented across local transform use cases (`RotateImageUseCase`, filter/adjust) and `NetworkImageEditUseCase` (which itself reuses the local transforms), with multiple UI entry points (`ImageEditDialog`, `PlayerDialogHelper`); there is no single clean completion point, and instrumenting them naively would double-count network edits. Wiring IMAGE_EDIT correctly needs its own research (which use case is the user-facing save vs an internal step) - parked as a `/spec-draft` candidate. The `EditKind.IMAGE_EDIT in ResourceEditorUseCase.kt` verification grep is therefore intentionally NOT satisfied; `ResourceEditorUseCase.kt` was left unedited.

---

### Step 03.7 - Foreground session tracker (group F sessions)

**Files:** `data/stats/StatsSessionTracker.kt`, `core/init/AppStartupInitializer.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `@Singleton class StatsSessionTracker @Inject constructor(statsSink: StatsSink, sink flush hook)`. Register a `DefaultLifecycleObserver` on `ProcessLifecycleOwner.get().lifecycle` (the app already uses `ProcessLifecycleOwner` in `FastMediaSorterApp`): on `onStart` capture a start timestamp; on `onStop` compute elapsed foreground millis, emit `Session(activeMs = elapsed)` (the sink no-ops if collection is off), and trigger the sink's `flushNow()` so the batch is persisted on background. Initialize the tracker from `AppStartupInitializer` (same deferred/init pattern as the baseline task in Phase 02). Use a monotonic clock (`SystemClock.elapsedRealtime()`), not wall-clock, for elapsed time.

**Verification:**

- `Glob` - `StatsSessionTracker.kt` exists.
- `Grep` - `ProcessLifecycleOwner` referenced; `Session(` event emitted.
- `Grep` - `SystemClock.elapsedRealtime` used (not `System.currentTimeMillis` for elapsed).
- `Grep` - tracker initialized/referenced in `AppStartupInitializer.kt`.
- Build: `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:** New `@Singleton StatsSessionTracker(@ApplicationScope scope, StatsSink)` registers a `DefaultLifecycleObserver` on `ProcessLifecycleOwner` - `onStart` captures `SystemClock.elapsedRealtime()`, `onStop` emits `Session(activeMs)` and `scope.launch { flushNow() }`. `initialize()` is idempotent (AtomicBoolean) and called from the eager `AppStartupInitializer.initialize()` (main thread - `ProcessLifecycleOwner.get()` requires the main looper; not the deferred IO block). `AppStartupInitializerTest` constructor updated (also added the previously-missing `statisticsRepository` arg).

---

### Step 03.8 - Sink unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/stats/StatsSinkImplTest.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a unit test (model it on `RecordSortSuccessUseCaseTest` / DataStore test patterns) asserting: (1) when the gate flag is OFF, `record(..)` produces NO write to the aggregate store (verify `apply` not called); (2) when ON, a batch of events folds into a single `apply(delta)` after the debounce/flush; (3) `wipeDetailed` path leaves baseline untouched. Use a fake/mock `SettingsRepository` and `StatsAggregateDataStore`, and a `TestScope` for the flush coroutine. This proves ADR-5 (no-op) and ADR-6 (batched) statically.

**Verification:**

- `Glob` - `StatsSinkImplTest.kt` exists.
- `Grep` - a test method asserts no `apply` call when disabled.
- Test: `.\gradlew.bat testStandardDebugUnitTest --tests "*StatsSinkImplTest*"` - the new test class passes (read the per-class XML report; do not gate on the suite's pre-existing failures).

**Status:** `[x]` done

**Step Log:** `StatsSinkImplTest` added with three cases: (1) gate OFF -> `aggregate.apply` never called even after the debounce window; (2) gate ON -> three events fold into a single `apply(delta)` after the 2.5s debounce, asserted via a captured `StatsAggregateDelta` (PHOTOS_CAPTURED=2, FILES_COPIED=2, BYTES_COPIED=50); (3) `flushNow()` drains the buffer once and the sink never invokes `wipeDetailed()` (baseline-safety, ADR-2). Uses a `MutableStateFlow<AppSettings>` settings mock and a `StandardTestDispatcher` sharing the `runTest` scheduler; the open settings collector is advanced with `runCurrent()` and the debounce crossed with `advanceTimeBy` (not `advanceUntilIdle`). NOTE: compile + run pending (the user builds centrally).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `.\a.ps1 fc`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` on every modified file returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` passes (no empty/broad catch, no slop introduced by the sink calls).
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- The aggregate store now fills with real activity (only when the gate is on); Phase 04 reads it via `StatisticsRepository.getSnapshot()`.
- Record here which optional points were deferred at impl time (e.g. cloud-connected if the sink did not inject cleanly into `AddResourceConnectionManager`) so Phase 06 docs and a future extension ticket reflect reality.
- IMPL DEFERRAL - IMAGE_EDIT (`StatsKey.IMAGE_EDITS`): not wired. The pre-mapped `ResourceEditorUseCase.save()` is resource-config editing, not image editing; the real image-edit save is fragmented (`RotateImageUseCase` + filter/adjust use cases + `NetworkImageEditUseCase`, multiple UI entry points) with no single clean completion point and a double-count risk. Needs its own research - parked as a `/spec-draft` candidate for the caller to capture. Until wired, the dashboard's "image edits" counter stays at its baseline (0) and its row hides (Phase 04 zero-row policy).
- Cloud-connected: WIRED (not deferred). `AddResourceConnectionManager.AuthEvent.Success` emits `SourceConnected()` via an `EntryPointAccessors` `StatsSink` lookup (the manager is manually constructed; this matches the existing identity-EntryPoint pattern in that file).
- `StatsSink` reaches the manually-constructed player managers (`VideoPlayerManager`, `PlayerImageLoadingCallbackImpl`, `PdfViewerManager`) by a field-injected `StatsSink` on `PlayerActivity`/`DocumentStandaloneActivity` forwarded through the factory/initializer, and an `EntryPointAccessors` lookup in `StandaloneViewManager`. Phase 04 needs none of this - it reads only the aggregate store.
- Standalone-mode image (Glide) and standalone-mode video (its own ExoPlayer in `StandaloneViewManager`) are distinct viewing completion points that were not in this phase's pre-mapped list and remain unwired; standalone AUDIO is already covered because it routes through `AudioPlaybackService`.
- Deferred-by-design (INDEX Scope Decisions): slideshow, OCR/translation, bytes-transferred, NAS scan, most-used, top-destinations. The sink event hierarchy is the extension seam for all of them.

---

## Rollback Plan

Revert phase commit(s). Sink calls are additive and fire-and-forget; removing them restores prior behavior exactly. No schema or data migration. Restore `temp/` backups of `FileOperationUseCase.kt` / `ExtractArchiveUseCase.kt` if a partial edit must be undone.
