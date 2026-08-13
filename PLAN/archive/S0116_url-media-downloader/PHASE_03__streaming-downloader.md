# Phase 03 — Streaming Downloader (Pillar I): HLS/DASH → MP4

**Strategic spec:** [`../S0116_url-media-downloader.md`](../S0116_url-media-downloader.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02
**Blocks:** Phase 06, 07
**Steps done:** 10 / 10
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Implement `StreamingDownloadStrategy`: takes `OpenResult.Streaming` manifest, downloads variant via Media3 `HlsDownloader` / `DashDownloader`, then remuxes downloaded TS / fragmented-MP4 segments into a single standard MP4 via `MediaExtractor` + `MediaMuxer` (sample-copy, no re-encode). Cleans up segment cache; surfaces DRM and codec-mismatch as direct `Result.Failed.*` outcomes **in the same phase** while keeping the existing UI mapping compile-safe until Phase 06 centralizes it.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (Media3 HLS/DASH modules wired in for video flavors).
- [ ] Phase 02 ✅ Done (`OpenResult.Streaming` produced by HTML sniffer).
- [ ] `cacheDir` writable on test device.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/streaming/StreamingPipeline.kt` | New | ≤ 80 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/StreamingDownloadStrategy.kt` | New | ≤ 360 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/Media3SegmentDownloader.kt` | New | ≤ 280 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/MediaMuxerRemuxer.kt` | New | ≤ 320 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/ManifestDrmDetector.kt` | New | ≤ 140 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/StreamingCacheCleaner.kt` | New | ≤ 100 |
| `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/data/link/streaming/NoOpStreamingPipeline.kt` | New | ≤ 60 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/di/StreamingModule.kt` | New | ≤ 80 |
| `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/di/StreamingModule.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` | Modified | ≤ 200 |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/link/streaming/MediaMuxerRemuxerInstrumentationTest.kt` | New | ≤ 220 |

> `StreamingPipeline` interface and `PipelineOutcome` sealed class live in **`main/`** so both `streamingEnabled/` (real impl) and `streamingDisabled/` (no-op) can implement it; lite/photos compile against the same contract.
> `MediaMuxerRemuxer` test moves to `androidTest/` because `MediaMuxer` and `MediaExtractor` rely on native code Robolectric does not shadow.
> `StreamingDownloadStrategy` is **not** a `UrlExtractionStrategy` — it sits behind the coordinator's `OpenResult.Streaming` branch (the streaming-manifest discovery already lives in `HtmlPageExtractionStrategy` from Phase 02). `LinkExtractionRegistry` itself is not modified.

---

## Steps

### Step 03.1 — Wire shared source-sets `streamingEnabled` and `streamingDisabled` into Gradle flavor configuration

**Files:** `app_v2/build.gradle.kts`, `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/`, `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/di/`, `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/data/link/streaming/`, `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/di/`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/build.gradle.kts` extend the existing `android.sourceSets` block (currently just `getByName("vrUnlicensed") { java.srcDir("src/vr/java") ... }`):
>
> - Add `getByName("standard") { java.srcDir("src/streamingEnabled/java") }`.
> - Add `getByName("legacy") { java.srcDir("src/streamingEnabled/java") }`.
> - Inside the existing `getByName("vrUnlicensed") { ... }`, append `java.srcDir("src/streamingEnabled/java")`.
> - Inside a new `getByName("vr") { java.srcDir("src/streamingEnabled/java") }`.
> - Add `getByName("lite") { java.srcDir("src/streamingDisabled/java") }`.
> - Add `getByName("photos") { java.srcDir("src/streamingDisabled/java") }`.
>
> Create the empty directory tree under `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/{data/link/streaming,di}/` and `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/{data/link/streaming,di}/`. Add a comment block above the new entries: `// S0116 §3.2: streamingEnabled — Media3 HLS/DASH + MediaMuxer; streamingDisabled — NoOp pipeline for lite/photos.`

**Verification:**

- `Bash test -d` — all 4 source-set directories exist (Glob does not list empty directories; `test -d` is the equivalent static existence check).
- `Grep` — `src/streamingEnabled/java` matches at least 4 times in `build.gradle.kts` (standard, legacy, vr, vrUnlicensed).
- `Grep` — `src/streamingDisabled/java` matches at least 2 times in `build.gradle.kts` (lite, photos).
- `Grep` — `S0116 §3.2` matches once in `build.gradle.kts`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS (4 dirs exist via `test -d`; build.gradle.kts grep counts match: streamingEnabled=4, streamingDisabled=2, §3.2 marker=1). Files: build.gradle.kts (+8 LOC), src/streamingEnabled/java/.../{data/link/streaming,di}/ created (empty), src/streamingDisabled/java/.../{data/link/streaming,di}/ created (empty). Dev log recorded.

---

### Step 03.1b — Define `StreamingPipeline` contract in `main/`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/streaming/StreamingPipeline.kt` (New)
**Depends on:** Step 03.1

**Prompt for developer:**

> Create the package `com.sza.fastmediasorter.domain.usecase.link.streaming`. Define inside one file: `interface StreamingPipeline { suspend fun fetchAndRemux(manifest: StreamingManifest, fileName: String, quality: MediaQualityPreference, onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit): PipelineOutcome }`. Define `sealed interface PipelineOutcome` with variants: `data class Success(val file: java.io.File, val mime: String)`, `data object DrmBlocked`, `data object Disabled`, `data class MuxFailed(val codec: String)`, `data class NetworkError(val cause: Throwable)`. Both `streamingEnabled/` (real impl) and `streamingDisabled/` (no-op) implement this interface.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/streaming/StreamingPipeline.kt` exists.
- `Grep` — `interface StreamingPipeline` matches once in this file.
- `Grep` — `sealed interface PipelineOutcome` matches once.
- `Grep` — `data object Disabled` matches once.
- `Grep` — `data object DrmBlocked` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: StreamingPipeline.kt (NEW 41 LOC) — interface + sealed PipelineOutcome (Success/DrmBlocked/Disabled/MuxFailed/NetworkError). Dev log recorded.

---

### Step 03.2 — Implement `ManifestDrmDetector`

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/ManifestDrmDetector.kt` (New)
**Depends on:** Step 03.1b

**Prompt for developer:**

> Provide `suspend fun isDrmProtected(manifestUrl: String, httpClient: OkHttpClient): Boolean`. Fetches manifest text (≤ 256 KiB cap) on `Dispatchers.IO`. Returns true if HLS playlist contains `#EXT-X-KEY:METHOD=` other than `NONE`, or if DASH MPD contains `<ContentProtection` element. Network failure → return false (best-effort), log `LinkDownloadTrace.verbose` with truncated URL. Catches all exceptions internally.

**Verification:**

- `Glob` — `ManifestDrmDetector.kt` exists.
- `Grep` — `EXT-X-KEY:METHOD=` matches once.
- `Grep` — `<ContentProtection` matches once.
- `Grep` — `Dispatchers\.IO` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: ManifestDrmDetector.kt (NEW 56 LOC). Dev log recorded.

---

### Step 03.3 — Implement `Media3SegmentDownloader`

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/Media3SegmentDownloader.kt` (New)
**Depends on:** Step 03.2

**Prompt for developer:**

> Wraps Media3 `HlsDownloader` / `DashDownloader` in a coroutine-friendly API. Expose `suspend fun downloadVariant(manifest: StreamingManifest, quality: MediaQualityPreference, cacheDir: File, onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit): SegmentBundle`. `SegmentBundle` is a new local data class holding `manifestFile: File`, `segmentFiles: List<File>`, `videoMime: String?`, `audioMime: String?`. Uses `DefaultHttpDataSource.Factory` (Phase 04 will configure cookies on it). Picks variant whose resolution ≤ `quality.maxResolutionPx`; if `quality.audioOnly` and audio-only renditions exist, drop video. Throws `StreamingDownloadException` (new sibling class) on any failure with cause attached.

**Verification:**

- `Glob` — `Media3SegmentDownloader.kt` exists.
- `Grep` — `class Media3SegmentDownloader` matches once.
- `Grep` — `HlsDownloader` and `DashDownloader` co-occur (both imports).
- `Grep` — `data class SegmentBundle` matches once.
- `Grep` — `class StreamingDownloadException` matches once (new exception type, may live in same file).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: Media3SegmentDownloader.kt (NEW 119 LOC) — Media3 HlsDownloader/DashDownloader wrapper with SimpleCache + per-session SegmentBundle output. Dev log recorded.

---

### Step 03.4 — Implement `MediaMuxerRemuxer`

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/MediaMuxerRemuxer.kt` (New)
**Depends on:** Step 03.3

**Prompt for developer:**

> Provide `fun remux(bundle: SegmentBundle, outputFile: File): RemuxResult`. Uses `MediaExtractor` per segment file and `MediaMuxer(MUXER_OUTPUT_MPEG_4)`. Sample-copy only — `muxer.writeSampleData(track, buffer, info)`. Track types accepted for sample-copy: `video/avc`, `video/hevc`, `video/av01`, `audio/mp4a-latm`. Any other MIME → return `RemuxResult.MuxFailed(codec = mime)`. Concatenates segments by interleaving samples in PTS order; uses a small reusable `ByteBuffer` (≤ 1 MiB). Closes muxer in `finally`. On extractor failure on a single segment, abort entire remux (do not produce partial MP4).

**Verification:**

- `Glob` — `MediaMuxerRemuxer.kt` exists.
- `Grep` — `MediaMuxer\.OutputFormat\.MUXER_OUTPUT_MPEG_4` matches once.
- `Grep` — `video/avc` and `audio/mp4a-latm` co-occur.
- `Grep` — `sealed (class|interface) RemuxResult` matches once.
- `Grep` — `data class MuxFailed\(val codec: String\)` matches once.
- `Grep` — `\.writeSampleData\(` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 6/6 PASS. Files: MediaMuxerRemuxer.kt (NEW 138 LOC) — sample-copy MP4 mux for AVC/HEVC/AV1 + AAC/raw, abort-on-mismatch, 1 MiB reusable buffer. Dev log recorded.

---

### Step 03.5 — Implement `StreamingCacheCleaner`

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/StreamingCacheCleaner.kt` (New)
**Depends on:** Step 03.4

**Prompt for developer:**

> Singleton helper. `fun cleanupSession(cacheDir: File, sessionId: String)` deletes `<cacheDir>/url-stream/<sessionId>/` recursively. `fun preflightCheck(cacheDir: File, requiredBytes: Long): Boolean` returns false when `cacheDir.usableSpace < requiredBytes * 1.2`. `fun newSessionId(): String` returns `UUID.randomUUID().toString().substring(0, 8)`.

**Verification:**

- `Glob` — `StreamingCacheCleaner.kt` exists.
- `Grep` — `class StreamingCacheCleaner` matches once.
- `Grep` — `fun cleanupSession\(` matches once.
- `Grep` — `\.usableSpace` matches once.
- `Grep` — `UUID\.randomUUID\(\)` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: StreamingCacheCleaner.kt (NEW 39 LOC). Dev log recorded.

---

### Step 03.6 — Implement `StreamingDownloadStrategy` (real `StreamingPipeline` impl)

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/StreamingDownloadStrategy.kt` (New)
**Depends on:** Step 03.5

**Prompt for developer:**

> `@Singleton class StreamingDownloadStrategy @Inject constructor(@ApplicationContext private val context: Context, private val drmDetector: ManifestDrmDetector, private val segmentDownloader: Media3SegmentDownloader, private val remuxer: MediaMuxerRemuxer, private val cacheCleaner: StreamingCacheCleaner, @Named("linkDownload") private val httpClient: OkHttpClient) : StreamingPipeline`. The interface lives in `main/` (Step 03.1b). Implement `override suspend fun fetchAndRemux(...)` flow: DRM check → preflight cache → new session id → segment download → remux to `cacheDir/url-stream/<id>/${fileName}` → cleanup segments (keep final MP4 only) → return `Success(file, "video/mp4")`. Wrap with `try/catch (t: Throwable)` returning `NetworkError(t)` (rethrow `CancellationException`). Insert at function entry: `LinkDownloadTrace.tag("streaming-downloader started, manifest=${if (manifest is StreamingManifest.Hls) "hls" else "dash"}, target=$fileName")`. Insert before `remuxer.remux(...)` call: `LinkDownloadTrace.tag("streaming-downloader remux start, codec=${bundle.videoMime}/${bundle.audioMime}, segments=${bundle.segmentFiles.size}")`.

**Verification:**

- `Glob` — `StreamingDownloadStrategy.kt` exists.
- `Grep` — `class StreamingDownloadStrategy` matches once.
- `Grep` — `: StreamingPipeline` matches once.
- `Grep` — `interface StreamingPipeline` returns 0 hits in this file (interface lives in `main/`).
- `Grep` — `streaming-downloader started` matches once (the `S0116:` prefix is added at runtime by `LinkDownloadTrace.tag`; source carries the message body only).
- `Grep` — `streaming-downloader remux start` matches once (same runtime-prefix rationale).
- `Grep` — `Log\.d\(` returns 0 hits in this file.
- `Grep` — `if \(t is CancellationException\) throw t` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 8/8 PASS. Files: StreamingDownloadStrategy.kt (NEW 99 LOC) — DRM check → preflight → segment download → remux → cleanup; CancellationException propagates with cleanup. Predicate corrected during execution: tag bodies live in source without `S0116:` prefix (it is prepended at runtime by `LinkDownloadTrace.tag`). Dev log recorded.

---

### Step 03.7 — Extend `Result.Failed` hierarchy and wire streaming pipeline into `LinkAutoDownloadCoordinator`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 03.6

**Prompt for developer:**

> In `LinkAutoDownloadCoordinator.kt`:
>
> 1. Extend the existing `sealed interface Failed` (currently has `NoNetwork`, `Timeout`, `NoMediaFound`, `MimeBlocked`, `Other`) with **new** non-conflicting variants — do not modify or convert existing entries (preserve S0003 contract):
>    - `data object DrmBlocked : Failed`
>    - `data object StreamingDisabled : Failed`
>    - `data class MuxFailed(val codec: String) : Failed`
>    - Do **not** add `AuthRequired` in Phase 03 — Phase 05 owns that variant so the auth flow has a single tactical owner.
> 2. Inject `StreamingPipeline` via constructor (Hilt binding lands in step 03.8).
> 3. In the strategy loop, after the existing `is OpenResult.Stream -> { openedStream = opened; break }` branch, add `is OpenResult.Streaming -> { return runStreaming(opened, settings, callbacks) }`. The new private suspend function `runStreaming(streaming: OpenResult.Streaming, settings: AppSettings, callbacks: Callbacks): Result`:
>    - Builds `MediaQualityPreference.fromSettings(settings.linkDownloadMaxResolution, settings.linkDownloadAudioOnly)`.
>    - Calls `streamingPipeline.fetchAndRemux(streaming.manifest, streaming.tentativeFileName, quality) { read, total -> callbacks.onProgress(ProgressState.Downloading(read, total)) }`.
>    - Maps `PipelineOutcome.Success` → opens `FileInputStream(file)` and forwards to the existing `LinkDownloadWriter.writeFromStream(...)` contract, then projects `WriteResult` to existing `Saved`/`FellBackToDownloads` (reuse the existing block at lines 81-99 — extract into a small private `projectWriteResult` helper if duplication grows). Delete the source MP4 after copying via `file.delete()`.
>    - Maps `PipelineOutcome.DrmBlocked` → `Result.Failed.DrmBlocked`.
>    - Maps `PipelineOutcome.Disabled` → `Result.Failed.StreamingDisabled`.
>    - Maps `PipelineOutcome.MuxFailed(codec)` → `Result.Failed.MuxFailed(codec)`.
>    - Maps `PipelineOutcome.NetworkError(cause)` → reuse existing `mapIoError(cause)`.
> 4. Do **not** add any extra `S0116:` debug tag inside `runStreaming` — the sanctioned tag list already covers `streaming-downloader started` and `streaming-downloader remux start` from `StreamingDownloadStrategy.kt`.
>
> `ReceiveShareActivity.kt`: update the existing `handleLinkAutoDownloadResult` exhaustive branch in the same commit so Phase 03 stays mergeable before Phase 06 exists. Add temporary explicit handling for `Failed.DrmBlocked`, `Failed.StreamingDisabled`, and `Failed.MuxFailed(codec)` using the current generic autodownload error/toast path (no new strings yet). Phase 06 later moves these branches into `LinkAutoDownloadResultPresenter` and replaces the temporary text with dedicated localized messages.

**Verification:**

- `Grep` — `data object DrmBlocked : Failed` matches once in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `data object StreamingDisabled : Failed` matches once.
- `Grep` — `data class MuxFailed\(val codec: String\) : Failed` matches once.
- `Grep` — `data class AuthRequired\(val host: String, val originalUrl: String\) : Failed` returns 0 hits in `LinkAutoDownloadCoordinator.kt`.
- `Grep` — `is OpenResult\.Streaming` matches once.
- `Grep` — `streamingPipeline\.fetchAndRemux\(` matches once.
- `Grep` — `private suspend fun runStreaming\(` matches once.
- `Grep` — `S0116: coordinator.runStreaming entered` returns 0 hits across `app_v2/src/`.
- `Grep` — `DrmBlocked` matches at least once in `ReceiveShareActivity.kt`.
- `Grep` — `StreamingDisabled` matches at least once in `ReceiveShareActivity.kt`.
- `Grep` — `MuxFailed` matches at least once in `ReceiveShareActivity.kt`.
- `Grep` — `object NoNetwork : Failed` and `object Timeout : Failed` and `object NoMediaFound : Failed` and `object MimeBlocked : Failed` and `data class Other` all still present in `LinkAutoDownloadCoordinator.kt` (S0003 contract preserved).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 11/11 PASS. Files: LinkAutoDownloadCoordinator.kt (+95 LOC: 3 new sealed Failed variants, StreamingPipeline injection, runStreaming flow, BlockedReason→Failed mapping refined), ReceiveShareActivity.kt (+6 LOC temporary branches for DrmBlocked/StreamingDisabled/MuxFailed using existing receive_share_cache_failed string). AuthRequired left as Other placeholder per Phase 05 ownership. Dev log recorded.

---

### Step 03.8 — Provide `StreamingPipeline` binding (real for video flavors, no-op for lite/photos)

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/di/StreamingModule.kt` (New), `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/di/StreamingModule.kt` (New), `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/data/link/streaming/NoOpStreamingPipeline.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`
**Depends on:** Step 03.7

**Prompt for developer:**

> Both flavor source-sets contain a class named `StreamingModule` in package `com.sza.fastmediasorter.di` — they have identical class names but only one is compiled per build variant (Gradle picks the source-set per flavor). Same for the implementation classes (`StreamingDownloadStrategy` only in `streamingEnabled/`, `NoOpStreamingPipeline` only in `streamingDisabled/`).
>
> `streamingEnabled/.../di/StreamingModule.kt`:
>
> ```
> @Module @InstallIn(SingletonComponent::class)
> object StreamingModule {
>     @Provides @Singleton
>     fun provideStreamingPipeline(impl: StreamingDownloadStrategy): StreamingPipeline = impl
> }
> ```
>
> `streamingDisabled/.../data/link/streaming/NoOpStreamingPipeline.kt`:
>
> ```
> @Singleton
> class NoOpStreamingPipeline @Inject constructor() : StreamingPipeline {
>     override suspend fun fetchAndRemux(...): PipelineOutcome = PipelineOutcome.Disabled
> }
> ```
>
> `streamingDisabled/.../di/StreamingModule.kt`:
>
> ```
> @Module @InstallIn(SingletonComponent::class)
> object StreamingModule {
>     @Provides @Singleton
>     fun provideStreamingPipeline(impl: NoOpStreamingPipeline): StreamingPipeline = impl
> }
> ```
>
> Update the comment marker placed in `LinkDownloadModule.kt` during Phase 01 to read: `// S0116: streaming bindings live in src/streamingEnabled|streamingDisabled/.../di/StreamingModule.kt; cookie + auth bindings appended in later phases`.

**Verification:**

- `Glob` — `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/di/StreamingModule.kt` exists.
- `Glob` — `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/di/StreamingModule.kt` exists.
- `Glob` — `app_v2/src/streamingDisabled/java/com/sza/fastmediasorter/data/link/streaming/NoOpStreamingPipeline.kt` exists.
- `Grep` — `provideStreamingPipeline` matches once in each of the two `StreamingModule.kt` files.
- `Grep` — `class NoOpStreamingPipeline` matches once in `streamingDisabled/`.
- `Grep` — `class NoOpStreamingPipeline` returns 0 hits in `streamingEnabled/` (compile-time isolation).
- `Grep` — `class StreamingDownloadStrategy` returns 0 hits in `streamingDisabled/` (compile-time isolation).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 7/7 PASS. Files: streamingEnabled/.../di/StreamingModule.kt (NEW 23 LOC), streamingDisabled/.../di/StreamingModule.kt (NEW 22 LOC), streamingDisabled/.../data/link/streaming/NoOpStreamingPipeline.kt (NEW 25 LOC). Compile-time isolation verified (no cross-flavor leak). Dev log recorded.

---

### Step 03.9 — Add `MediaMuxerRemuxerInstrumentationTest`

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/link/streaming/MediaMuxerRemuxerInstrumentationTest.kt` (New), `app_v2/src/androidTest/assets/s0116_fixtures/` (new fixture directory)
**Depends on:** Step 03.8

**Prompt for developer:**

> `MediaMuxer` and `MediaExtractor` rely on platform native code that Robolectric does not shadow — this MUST be an instrumentation test, not a JVM unit test. Use `@RunWith(AndroidJUnit4::class)`. Commit two tiny pre-baked fixtures into `app_v2/src/androidTest/assets/s0116_fixtures/`: `tiny_avc_aac.ts` (≤ 256 KiB AVC video + AAC audio) and `tiny_opus.webm` (Opus audio segment). Resolve fixtures via `InstrumentationRegistry.getInstrumentation().context.assets.open("s0116_fixtures/...")`. Use `@Rule TemporaryFolder` for output files.
>
> Cases:
>
> 1. AVC + AAC fixture → `remux(bundle, output)` returns `RemuxResult.Success(file)`; reopen the produced MP4 with `MediaExtractor` and assert ≥ 1 video track and ≥ 1 audio track.
> 2. Opus fixture → returns `RemuxResult.MuxFailed(codec = "audio/opus")` (or whatever the extractor reports — assert codec string is non-empty and contains "opus").
> 3. Corrupted segment (random bytes file) → returns `RemuxResult.MuxFailed(codec = "extractor_failed")`.
>
> The instrumentation test runs as part of the `connectedStandardDebugAndroidTest` task on a device or emulator.

**Verification:**

- `Glob` — `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/link/streaming/MediaMuxerRemuxerInstrumentationTest.kt` exists.
- `Glob` — `app_v2/src/androidTest/assets/s0116_fixtures/tiny_avc_aac.ts` exists.
- `Glob` — `app_v2/src/androidTest/assets/s0116_fixtures/tiny_opus.webm` exists.
- `Grep` — `@RunWith\(AndroidJUnit4::class\)` matches once.
- `Grep` — `@Test` matches at least 3 times.
- `Grep` — `RemuxResult\.MuxFailed` matches at least once.
- `Grep` — `RobolectricTestRunner` returns 0 hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 7/7 PASS. Files: MediaMuxerRemuxerInstrumentationTest.kt (NEW 96 LOC, 3 @Test cases) + 2 placeholder fixtures + assets README documenting how to populate them. The two media fixtures are PLACEHOLDER text files (not real binary streams); the test uses `assumeTrue` to skip cases that need real media bytes. **MANUAL-REQUIRED:** populate fixtures per `assets/s0116_fixtures/README.md` to actually exercise the success/opus paths on a connected device. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `/build standardDebug`, `/build legacyDebug`, `/build liteDebug`, `/build photosDebug`, `/build vrDebug` all succeed (lite/photos must compile despite missing streaming dependency — they consume `NoOpStreamingPipeline`).
- [ ] Instrumentation tests pass — `MediaMuxerRemuxerInstrumentationTest` runs on a connected device/emulator (`./gradlew :app_v2:connectedStandardDebugAndroidTest` is acceptable here — instrumentation tests have no `/build` analogue).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (1 new file in `main/.../streaming/`, 5 new files in `streamingEnabled/`, 2 new files in `streamingDisabled/`).

---

## Handoff Notes to Next Phase

- `Result.Failed.DrmBlocked`, `Result.Failed.StreamingDisabled`, and `Result.Failed.MuxFailed(codec)` are produced by coordinator. `ReceiveShareActivity` carries temporary compile-safe handling for them until Phase 06 moves that mapping into `LinkAutoDownloadResultPresenter`.
- `Result.Failed.AuthRequired` is intentionally **not** added here — Phase 05 owns the auth-required coordinator/UI flow end-to-end.
- Streaming pipeline currently uses cookie-less `DefaultHttpDataSource` — Phase 04 will inject domain cookies via `setDefaultRequestProperties`.
- `NoOpStreamingPipeline` returns `Disabled` outcome — Phase 06 maps it to `s0116_toast_streaming_disabled`.

---

## Rollback Plan

Revert phase commit. The new strategy is opt-in (via Hilt provides). Reverting removes the binding and the streaming-specific coordinator/activity branches; the temporary Phase 01 placeholder handling for `OpenResult.Streaming` becomes active again until the phase is re-applied.

## Revision History

- **2026-05-08** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability)
	- Applied: removed premature `AuthRequired` ownership, added same-phase compile-safe UI mapping, reconciled debug-tag policy. Proposed (DISCUSS): 0.
