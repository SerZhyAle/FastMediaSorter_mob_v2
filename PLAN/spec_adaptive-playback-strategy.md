# Specification: Adaptive Playback Strategy — Physics-Based Pre-Cache + Offload Fallback

**Status:** Draft
**Date:** 2026-04-21
**Tier:** 3 — Moderate (medium risk)
**Roadmap entry:** Adaptive playback strategy for progressive video (local / SMB / SFTP / FTP / Cloud) — physics-based pre-cache duration formula with a streaming-not-viable fallback that offers local download. No code in this task, specification only.

---

## 1. Problem Statement

FastMediaSorter streams video from wildly different sources (local, SMB, SFTP, FTP, Cloud) with fixed buffer constants: `VideoPlayerManager.MIN_BUFFER_MS = 15 s`, `MAX_BUFFER_MS = 30 s`, `CLOUD_MIN_BUFFER_MS = 20 s`, `CLOUD_MAX_BUFFER_MS = 45 s`. These hardcoded values in [VideoPlayerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt), [SmbPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt), [SftpPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt), [FtpPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt), [CloudPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt) ignore three facts the app already knows: the measured read speed of the resource ([ConnectionThrottleManager.kt:69](app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt#L69) `speedMbpsCache`), the device's actual memory and storage budget, and the file's own bitrate.

Two distinct gaps result:

1. **Under normal conditions** — pre-cache duration is wrong for the situation (heavy files on slow NAS stall, lightweight files on fast networks wait too long), and there is no user-visible feedback about what is being cached.
2. **Under bad conditions** — when the network is genuinely too slow for the bitrate (`bitrate/speed ≥ 0.9`), no amount of pre-cache helps; the buffer drains faster than it fills. The app currently refuses gracefully or stalls indefinitely. A better outcome exists: offer to download the file first to local storage, then play smoothly from disk.

Quality / resolution switching is explicitly **out of scope**: the app plays single-variant progressive files. The only real dials are **how many seconds of movie to cache ahead** and, when streaming is not viable, **whether to offload the whole file to disk first**.

---

## 2. Goals

1. Replace fixed buffer constants with a computed `prefetchSeconds` derived from a transparent **physics-based formula**: `targetSec = safetyMargin(protocol) / (1 − bitrate_to_speed_ratio)`, with device-byte-budget and file-duration ceilings.
2. Classify every playback session into a `StreamViabilityState` — `VIABLE / MARGINAL / NOT_VIABLE` — from the same ratio.
3. When `NOT_VIABLE`, offer a **local offload** flow: download the file to `Downloads/FMS/streaming/<hash>/<filename>` using existing `*ToLocalStrategy` classes, then play from disk.
4. Show a **pre-cache progress indicator** (seconds cached / seconds target · protocol label) while pre-caching is active.
5. After an offloaded file's player session ends (or the user switches to another file), prompt to **delete the downloaded copy**, with a `Don't ask again` option.
6. Garbage-collect streaming-cache files older than a TTL on app startup as a safety net.
7. Expose a user override in Settings → General: `Auto` (default) / `Less cache` / `More cache`.
8. Specification only — no code in this task.

**Non-goals for this spec:**

- Auto-switching video resolution for single-file playback (MP4 / MKV / AVI).
- HLS / DASH / SmoothStreaming adaptive logic.
- Server-side transcoding or a proxy layer.
- Deriving bitrate savings from device screen size.
- Background / ambient download of files the user did not ask to play.
- Partial-file / range-resume download (Phase 1 downloads the whole file; resume is future scope).
- Changing audio-only playback buffering.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Full formula + offload path for SMB/SFTP/FTP/Cloud + Local |
| `lite`     | ✅ | Formula + offload for Local/SMB/SFTP/FTP only — Cloud branch gated off by `FEATURE_CLOUD` |
| `photos`   | ❌ | No video playback in this flavor |
| `legacy`   | ✅ | Formula + offload for Local/SMB/SFTP/FTP; offload dialog uses legacy storage path (see 3.2) |

No new `BuildConfig` flag required. Existing `FEATURE_CLOUD` and `FEATURE_VIDEO` gates in [app_v2/build.gradle.kts](app_v2/build.gradle.kts) already scope the affected code paths.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | `ActivityManager.getMemoryClass()` for device budget; streaming cache writes to `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` directly; requires `WRITE_EXTERNAL_STORAGE` runtime permission (already declared for legacy) |
| 26+ (standard minSdk) | Default path — same as 23 until API 29 |
| 29+ (Android 10, scoped storage) | Must use `MediaStore.Downloads` (`IS_PENDING = 1` → copy → `IS_PENDING = 0`) instead of direct file I/O in `Downloads/`. Pattern already used in [PdfExportHelper.kt:39](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfExportHelper.kt#L39) — reuse it verbatim |
| 30+ (Android 11) | MediaStore relative path `Download/FMS/streaming/` works without `MANAGE_EXTERNAL_STORAGE` |
| 33+ | `POST_NOTIFICATIONS` already handled; offload progress dialog is in-app, not a notification |
| 34+ | Predictive-back compatible: offload dialog is a `BottomSheetDialogFragment` that handles `onBackPressed` cleanly |

### 3.3 Wear OS Impact

No Wear OS changes required. The `wear/` module does not contain a video player — it is a companion control surface only. Streaming cache is main-app scope.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `VideoPlayerManager` | [ui/player/VideoPlayerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt) | Owns ExoPlayer lifecycle; declares buffer constants (lines 124–133); passes them unchanged to every `DefaultLoadControl` |
| `SmbPlaybackHelper` | [ui/player/helpers/SmbPlaybackHelper.kt:76](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt#L76) | Builds SMB `LoadControl` with fixed constants |
| `SftpPlaybackHelper` | [ui/player/helpers/SftpPlaybackHelper.kt:57](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt#L57) | SFTP pattern |
| `FtpPlaybackHelper` | [ui/player/helpers/FtpPlaybackHelper.kt:60](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt#L60) | FTP pattern |
| `CloudPlaybackHelper` | [ui/player/helpers/CloudPlaybackHelper.kt:42](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt#L42) | Cloud-specific constants |
| `PlayerSetupHelper` | [ui/player/helpers/PlayerSetupHelper.kt:34](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt#L34) | Local-file `LoadControl` |
| `ConnectionThrottleManager` | [data/network/ConnectionThrottleManager.kt:69](app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt#L69) | Stores `speedMbpsCache`, `recommendedThreadsCache`, `recommendedBufferSizeCache` per resource — already populated by `NetworkSpeedTestUseCase` |
| `SettingsRepositoryImpl` | [data/repository/SettingsRepositoryImpl.kt:36](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt#L36) | Owns `KEY_NETWORK_PARALLELISM` |
| `SmbToLocalStrategy` / `SftpToLocalStrategy` / `FtpToLocalStrategy` | `data/transfer/strategies/` | Existing download primitives for SMB/SFTP/FTP → local file |
| `CloudOperationStrategy.downloadCloudToLocal` | [data/transfer/strategy/CloudOperationStrategy.kt:266](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt#L266) | Cloud download primitive |
| `FileCopyProgressDialog` | [ui/player/helpers/FileCopyProgressDialog.kt:11](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FileCopyProgressDialog.kt#L11) | Existing progress UI — reusable for offload |
| `PdfExportHelper` MediaStore pattern | [utils/PdfExportHelper.kt:39](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfExportHelper.kt#L39) | Reference implementation for Android 10+ Downloads writes |
| `AppDatabase` | [data/local/db/AppDatabase.kt:24](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt#L24) | Room version `23` — must bump to `24` for the new cache-entry table |
| `PlayerViewModel` | [ui/player/PlayerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt) | Source of truth for player state; target for pre-cache progress + viability state |

**Gap:** every buffer value is a compile-time constant; no StateFlow carries pre-cache progress to the UI; there is no path from "streaming would never work" to "download first, play from disk"; no place records that a file was offloaded or schedules its cleanup.

---

## 5. Proposed Architecture

### 5.1 Core Artifact — `PrefetchPlan` + `StreamViabilityState`

Every playback session produces a single plan object:

```kotlin
data class PrefetchPlan(
    val viability: StreamViabilityState,   // VIABLE / MARGINAL / NOT_VIABLE
    val targetPrefetchSec: Int,            // seconds of movie to cache ahead
    val minPrefetchSec: Int,               // playback starts when reached
    val rebufferPrefetchSec: Int,          // resume threshold after a stall
    val maxBufferSec: Int,                 // upper cap (memory + storage guard)
    val sourceLabel: String,               // "SMB"/"SFTP"/"FTP"/"Cloud"/"Local"
    val derivation: PrefetchDerivation     // audit trail
)

enum class StreamViabilityState { VIABLE, MARGINAL, NOT_VIABLE }

data class PrefetchDerivation(
    val speedMbps: Double?,
    val bitrateKbps: Int?,
    val ratio: Double?,                    // bitrateKbps / (speedMbps × 1000)
    val cacheBudgetBytes: Long,
    val maxBufferSecByDevice: Int,
    val maxBufferSecByFile: Int,
    val protocol: Protocol,
    val userMultiplier: PrefetchCacheMultiplier,
    val formulaVersion: Int = 2            // v2: physics model
)

enum class Protocol { LOCAL, SMB, SFTP, FTP, CLOUD }
enum class PrefetchCacheMultiplier { LESS, AUTO, MORE }
```

### 5.2 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `PrefetchPlan.kt` | `domain/model/` | ≤ 90 |
| `PrefetchFormula.kt` | `domain/playback/` | ≤ 200 |
| `DeviceCapabilityProbe.kt` | `core/util/` | ≤ 140 |
| `PrefetchProgressTracker.kt` | `ui/player/helpers/` | ≤ 160 |
| `PrefetchOverlayView.kt` | `ui/player/views/` | ≤ 180 |
| `StreamOffloadOfferDialog.kt` | `ui/player/helpers/` | ≤ 200 |
| `StreamOffloadUseCase.kt` | `domain/usecase/` | ≤ 250 |
| `StreamingCacheEntry.kt` | `data/local/db/` | ≤ 60 |
| `StreamingCacheDao.kt` | `data/local/db/` | ≤ 80 |
| `StreamingCacheRepository.kt` | `data/repository/` | ≤ 180 |
| `StreamingCacheCleanupHelper.kt` | `ui/player/helpers/` | ≤ 160 |
| `StreamingCacheStartupGcWorker.kt` | `worker/` | ≤ 120 |
| `PrefetchCacheMultiplier.kt` | `domain/model/` | ≤ 40 |

All new files stay under the 1000-line limit; the largest (`StreamOffloadUseCase`) is under 250 by design — its responsibilities split into formula evaluation, path resolution, and progress plumbing.

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Formula in `PrefetchFormula`; offload orchestration in `StreamOffloadUseCase`; Activity only observes flows and shows dialogs |
| New classes follow naming | ✅ | `DeviceCapabilityProbe`, `PrefetchProgressTracker`, `StreamOffloadUseCase`, `StreamingCacheRepository`, `StreamingCacheCleanupHelper` |
| Data flow `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | `StreamOffloadUseCase` calls existing `SmbToLocalStrategy` / `CloudOperationStrategy.downloadCloudToLocal`; `StreamingCacheRepository` wraps the DAO |
| No `Log.d()` — Timber only | ✅ | Enforced project-wide |
| Room schema version incremented | ✅ | `AppDatabase.version` 23 → 24; migration `MIGRATION_23_24` adds `streaming_cache_entries` table |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ | `prefetchPlan`, `prefetchProgress`, `offloadProgress`: StateFlow. `offloadOffer`, `offloadCompleted`, `cleanupPrompt`: SharedFlow |
| Hilt DI: new bindings declared in module file | ✅ | `DeviceCapabilityProbe`, `StreamingCacheRepository` bound in `di/CoreModule.kt`; DAO provided in `di/DatabaseModule.kt` |

### 5.4 The Pre-Cache Formula (physics-based, v2)

Pure function — no side effects:

```
fun computePrefetchPlan(derivation: PrefetchDerivation): PrefetchPlan
```

**Step 1 — ratio.** The physical core: how much of one second of network we need to play one second of movie.

```
speedKbps = (speedMbps ?: baselineMbps(protocol)) × 1000
ratio = bitrateKbps?.toDouble()?.let { it / speedKbps } ?: baselineRatio(protocol)
    // baselineMbps: LOCAL=1000, SMB=50, SFTP=40, FTP=30, CLOUD=25 — only used before speed-test
    // baselineRatio: LOCAL=0.05, SMB=0.3, SFTP=0.35, FTP=0.4, CLOUD=0.45 — before onTracksChanged
```

**Step 2 — viability classification.**

```
viability = when {
    ratio < 0.7   -> VIABLE       // plenty of headroom
    ratio < 0.9   -> MARGINAL     // tight, large pre-cache needed
    else          -> NOT_VIABLE   // buffer drains faster than fills — offer offload
}
```

**Step 3 — target pre-cache duration (VIABLE / MARGINAL only).** Classic buffer math:

```
safetyMarginSec = protocolSafetyMargin(protocol)
    // LOCAL=2, SMB=4, SFTP=5, FTP=6, CLOUD=7 — accounts for request latency + jitter

targetBase = safetyMarginSec / (1.0 - ratio).coerceAtLeast(0.1)
    // ratio 0.2 → 2.5×margin; ratio 0.6 → 2.5×margin; ratio 0.85 → 6.7×margin
```

**Step 4 — user override.** Additive headroom, not compounding multiplier:

```
targetWithOverride = when (userMultiplier) {
    LESS -> targetBase × 0.7
    AUTO -> targetBase
    MORE -> targetBase + 10.0   // +10 s on top of physics — user chose caution
}
```

**Step 5 — device byte budget.**

```
cacheBudgetBytes = min(
    freeHeapBytes × 0.15,           // JVM heap portion safe to use for buffers
    freeStorageBytes × 0.25,        // free cache-dir storage fraction
    256L * 1024 * 1024              // hard cap
)
maxBufferSecByDevice = cacheBudgetBytes.toDouble() / (bitrateKbps × 125.0)
    // (bitrateKbps × 1000 / 8) bytes per second of movie
```

**Step 6 — file duration ceiling.**

```
remainingSec = fileDurationSec - currentPositionSec
maxBufferSecByFile = (remainingSec * 0.8).roundToInt()
    // no point caching more than 80 % of what remains
```

**Step 7 — final clamp.**

```
targetPrefetchSec = targetWithOverride
    .roundToInt()
    .coerceIn(2, minOf(maxBufferSecByDevice.toInt(), maxBufferSecByFile, 90))

minPrefetchSec      = (targetPrefetchSec * 0.35).roundToInt().coerceAtLeast(1)
rebufferPrefetchSec = (targetPrefetchSec * 0.55).roundToInt().coerceAtLeast(2)
maxBufferSec        = (targetPrefetchSec * 3.0).roundToInt()
                      .coerceAtMost(maxBufferSecByDevice.toInt())
```

**Threads setting is intentionally absent** from the formula: the user's `KEY_NETWORK_PARALLELISM` already influenced the measured `speedMbps`, so including it again would double-count (ADR-3).

**Worked examples (validation in unit tests):**

| Scenario | bitrateKbps | speedMbps | ratio | viability | target |
|----------|-------------|-----------|-------|-----------|--------|
| Fast local 4K | 18000 | — (local) | 0.05 | VIABLE | 2 s (floor) |
| Fast SMB HD | 5000 | 120 | 0.042 | VIABLE | 4 s (safety floor) |
| Slow SMB HD | 5000 | 8 | 0.625 | VIABLE | `4 / 0.375` = 11 s |
| Marginal SFTP | 8000 | 12 | 0.667 | VIABLE | `5 / 0.333` = 15 s |
| Tight Cloud HD | 6000 | 8 | 0.75 | MARGINAL | `7 / 0.25` = 28 s |
| Very slow Cloud 4K | 12000 | 3 | 4.0 | **NOT_VIABLE** | (offer offload) |
| User "MORE" on slow SMB | 5000 | 8 | 0.625 | VIABLE | `11 + 10` = 21 s |

**Mapping to ExoPlayer `DefaultLoadControl`** (only when `viability != NOT_VIABLE`):

```kotlin
DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        minBufferMs                      = minPrefetchSec * 1000,
        maxBufferMs                      = maxBufferSec * 1000,
        bufferForPlaybackMs              = minPrefetchSec * 1000,
        bufferForPlaybackAfterRebufferMs = rebufferPrefetchSec * 1000
    )
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()
```

### 5.5 Runtime Escalation (session-only)

`PrefetchProgressTracker` attaches a `Player.Listener` and counts `STATE_BUFFERING` transitions **after** the first `STATE_READY`.

- stallCount ≥ 2 → recompute plan with `ratio` inflated by +0.1 (moves toward MARGINAL), rebuild `LoadControl`.
- stallCount ≥ 4 → recompute with `ratio` inflated by +0.2; if this crosses 0.9 threshold → switch to `NOT_VIABLE` and surface the offload offer mid-session.

Escalation is session-only, does not modify the user's `cacheOverride`, and emits a one-shot `SharedFlow` event for a toast: `"Network is unstable — cache increased to 28 s"`.

### 5.6 Stream Offload Path (`NOT_VIABLE`)

When `viability == NOT_VIABLE` at player open — or escalation crosses the threshold mid-session — `PlayerViewModel` emits a `SharedFlow<OffloadOffer>` event. `PlayerActivity` observes it and shows `StreamOffloadOfferDialog`.

**Dialog contents (all values computed in VM, formatted in dialog):**

- Title: `This file is too heavy for your network`
- Body:
  - `File size: 2.4 GB`
  - `Your speed: 3 Mbps   ·   Needed for smooth play: ~12 Mbps`
  - `Estimated download time: ~ 1 h 55 min`
  - `Free space: 14.2 GB (enough)`
  - `Destination: Downloads/FMS/streaming/`
- Actions:
  - **Primary:** `Download and play` — starts `StreamOffloadUseCase.run(fileRef)`
  - **Secondary:** `Try streaming anyway` — proceeds with `MARGINAL` plan (`ratio` clamped to 0.89), pre-cache pegged to its max
  - **Dismiss:** `Cancel` — returns to browse

**Download time estimate:**

```
estDownloadSec = fileBytes × 8.0 / (speedMbps × 1_000_000)
```

**Free-space gate:** refuse to start if `fileBytes × 1.1 > freeStorageBytes`; replace primary action with a disabled state and a `Free up storage` link.

**Destination path:**

- Pre-API 29: `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS) + "/FMS/streaming/<resourceHash>/<filename>"`.
- API 29+: `MediaStore.Downloads` with `RELATIVE_PATH = Download/FMS/streaming/<resourceHash>/` and `IS_PENDING = 1` during the transfer (pattern from [PdfExportHelper.kt:39](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfExportHelper.kt#L39)).

`resourceHash = sha256(uri).take(16)` — isolates caches per original source so two files with the same filename on different servers don't collide.

**Transfer implementation:** `StreamOffloadUseCase` selects the correct existing strategy:

| Source protocol | Strategy used |
|-----------------|---------------|
| SMB | [SmbToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SmbToLocalStrategy.kt) |
| SFTP | [SftpToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SftpToLocalStrategy.kt) |
| FTP | [FtpToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/FtpToLocalStrategy.kt) |
| Cloud | [`CloudOperationStrategy.downloadCloudToLocal`](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt#L266) |

Progress surfaced via `StateFlow<OffloadProgress>` (bytes done, bytes total, ETA recomputed every 2 s). UI reuses existing [FileCopyProgressDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FileCopyProgressDialog.kt) — no new progress-UI class.

**On completion:**

1. `StreamingCacheRepository.record(entry)` inserts into `streaming_cache_entries`.
2. `PlayerViewModel.switchToLocalFile(localPath)` — swap the media source. New `PrefetchPlan` is computed for the local protocol (ratio ≈ 0, tiny pre-cache).
3. Playback starts as if the file had always been local.

**Cancellation:** if the user aborts mid-download, partial file is deleted immediately and no DB record is created.

### 5.7 Cleanup Of Offloaded Files

Three triggers, in priority order:

1. **Player exit** (`onDestroy` on a session that played an offloaded file): show `CleanupPromptDialog` with options `Keep`, `Delete`, `Don't ask again for this session`.
2. **File switch** (navigate to next file in the same player session): show the same dialog for the previous offloaded file before loading the new one.
3. **App startup GC**: `StreamingCacheStartupGcWorker` (OneTimeWorkRequest on `AppStartupInitializer`) scans entries where `lastPlayedAt < now - TTL`; TTL default = 7 days, configurable in Settings → General → `Streaming cache TTL` (off / 1 / 3 / 7 / 30 days, default 7).

**Persisted preference:** `KEY_STREAMING_CACHE_CLEANUP_MODE`: `ASK` (default) / `AUTO_DELETE` / `AUTO_KEEP`. "Don't ask again for this session" is session-scoped only; to make the choice permanent the user opens Settings.

**DB schema** (new table, Room v24):

```kotlin
@Entity(tableName = "streaming_cache_entries")
data class StreamingCacheEntry(
    @PrimaryKey val resourceHash: String,   // sha256(originalUri).take(16)
    val localPath: String,                  // absolute path OR MediaStore content URI
    val originalUri: String,                // source for "play again" / re-offload decisions
    val resourceKey: String,                // protocol-normalized key for speed-test lookup
    val sizeBytes: Long,
    val downloadedAt: Long,                 // epoch ms
    val lastPlayedAt: Long,                 // epoch ms — updated on every playback
    val sourceProtocol: String              // "SMB" / "SFTP" / "FTP" / "CLOUD"
)
```

**Migration** `MIGRATION_23_24` in [AppDatabase.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt):

```
CREATE TABLE streaming_cache_entries (
    resourceHash TEXT PRIMARY KEY NOT NULL,
    localPath TEXT NOT NULL,
    originalUri TEXT NOT NULL,
    resourceKey TEXT NOT NULL,
    sizeBytes INTEGER NOT NULL,
    downloadedAt INTEGER NOT NULL,
    lastPlayedAt INTEGER NOT NULL,
    sourceProtocol TEXT NOT NULL
)
```

### 5.8 UI — Pre-Cache Progress Indicator

Overlay on the player surface, top-center, semi-transparent pill. Visible only while `cachedSec < minPrefetchSec` (startup) OR during `STATE_BUFFERING` mid-playback.

```
Pre-caching 8 / 20 s · SMB
▰▰▰▰▰▰▱▱▱▱▱▱▱▱▱
```

- Line 1: current cached seconds / target / source label (EN/RU/UK).
- Line 2: thin progress bar.
- Auto-dismiss 600 ms after `STATE_READY` + `isPlaying`.
- Dismissible by tap (honoured for rest of session).
- **If `viability == MARGINAL`:** pill becomes amber and adds a second line `Network is tight — caching longer`.
- **If offload is offered:** overlay is hidden; dialog takes precedence.
- **If playing from offloaded local copy:** overlay shows `Playing from local copy · 2.4 GB` for 3 s then dismisses.

**State source:** `PlayerViewModel.prefetchProgress: StateFlow<PrefetchProgress>` sampled at ~4 Hz.

### 5.9 Settings UI

New section in [GeneralSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt), below the network-parallelism group:

- **Video pre-cache** — radio: `Less cache` / `Auto` (default) / `More cache`. Info `?` opens derivation dialog for last-played resource.
- **Streaming cache cleanup** — radio: `Ask` (default) / `Auto-delete on player exit` / `Auto-keep`.
- **Streaming cache TTL** — radio: `Off` / `1 day` / `3 days` / `7 days` (default) / `30 days`.
- **Clear streaming cache now** — button: lists current entries with total size, confirms deletion.

---

## 6. Data Flow

```
┌───────────────────────────────────────────────────────────────────┐
│ Player launch                                                     │
└───────────────────────────────────────────────────────────────────┘
            │
            ▼
  PlayerViewModel.openMedia(uri)
            │
            ├─► ConnectionThrottleManager.getLastSpeedMbps(resourceKey) ──┐
            ├─► DeviceCapabilityProbe.currentBudget()                     │──► PrefetchDerivation
            ├─► Format.averageBitrate (deferred until onTracksChanged)    │
            └─► SettingsRepositoryImpl.getPrefetchCacheMultiplier()       ┘
                        │
                        ▼
              PrefetchFormula.compute(derivation) ─► PrefetchPlan
                        │
             ┌──────────┴──────────────┐
             │                         │
        VIABLE / MARGINAL          NOT_VIABLE
             │                         │
             ▼                         ▼
     (Smb|…)PlaybackHelper     PlayerViewModel.offloadOffer (SharedFlow)
             │                         │
             ▼                         ▼
     DefaultLoadControl         StreamOffloadOfferDialog
             │                     │
             │           ┌─────────┼──────────┐
             │           ▼         ▼          ▼
             │      Download     Try      Cancel
             │           │     anyway      (back)
             │           ▼         │
             │   StreamOffloadUseCase  → MARGINAL plan
             │           │         │
             │           ▼         │
             │   SmbToLocal /      │
             │   CloudOperationStrategy.downloadCloudToLocal
             │           │
             │           ├─► FileCopyProgressDialog (progress bar)
             │           │
             │           ▼
             │   StreamingCacheRepository.record(entry)
             │           │
             │           ▼
             │   PlayerViewModel.switchToLocalFile(localPath)
             │           │
             └───────────┴───► ExoPlayer (new source, tiny pre-cache)
                         │
                         ▼
              PrefetchProgressTracker
                         │
      ┌──────────────────┼─────────────────┐
      ▼                  ▼                 ▼
  Stall count       bufferedPosition   UI overlay
  (escalation)        sampler         PrefetchOverlayView
      │                  │                 ▲
      └───► recompute plan, rebuild LoadControl
                         │
                         ▼
              (on player exit) StreamingCacheCleanupHelper
                         │
                         ▼
              CleanupPromptDialog (Keep / Delete / Don't ask again)
                         │
                         ▼
              StreamingCacheRepository.delete(entry) + file I/O
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| [VideoPlayerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt) | Drop buffer constants; accept `PrefetchPlan`; attach `PrefetchProgressTracker` | ~850 — **backup to `temp/` required** |
| [SmbPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt) | Replace constants with `plan.*Sec * 1000` | ~120 |
| [SftpPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt) | Same | ~100 |
| [FtpPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt) | Same | ~105 |
| [CloudPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt) | Same | ~95 |
| [PlayerSetupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt) | Same (local branch) | ~90 |
| [PlayerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt) | `prefetchPlan`, `prefetchProgress`, `offloadOffer`, `offloadProgress`, `cleanupPrompt` flows; `switchToLocalFile` method | ~750 — **backup to `temp/` required** |
| [PlayerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt) | Mount `PrefetchOverlayView`; observe offload/progress/cleanup flows; host dialogs | +55 lines — **backup to `temp/` required** |
| [SettingsRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt) | New keys `KEY_PREFETCH_CACHE_MULTIPLIER`, `KEY_STREAMING_CACHE_CLEANUP_MODE`, `KEY_STREAMING_CACHE_TTL_DAYS` | +60 |
| [GeneralSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt) | Three new preference rows + "Clear now" button + derivation dialog | +140 — **backup to `temp/` required** |
| [fragment_settings_general.xml](app_v2/src/main/res/layout/fragment_settings_general.xml) + `layout-land` | New rows | +45 each |
| [strings.xml](app_v2/src/main/res/values/strings.xml) + `values-ru` + `values-uk` | New strings (step 14) | — |
| [SettingsSearchIndex.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt) | Register new prefs | +18 |
| [AppDatabase.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt) | Version bump 23→24; add `streamingCacheDao()`; register `MIGRATION_23_24` | +20 |
| [AppStartupInitializer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt) | Enqueue `StreamingCacheStartupGcWorker` | +10 |
| `di/CoreModule.kt` | Bind `DeviceCapabilityProbe`, `StreamingCacheRepository` | +20 |
| `di/DatabaseModule.kt` | Provide `StreamingCacheDao` | +8 |

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Wrong bitrate on first open (before `onTracksChanged`) | High | Use `baselineRatio(protocol)` until tracks load; recompute once real bitrate arrives |
| Speed-test stale (different network than last measurement) | High | Runtime escalation + live-calibration comment in code; Phase 2 adds TTL on speed |
| Ratio near 0.9 oscillates between MARGINAL and NOT_VIABLE | Medium | Hysteresis: once `NOT_VIABLE` is declared, require ratio to drop below 0.8 before returning to `MARGINAL` |
| User accepts offload, download fails mid-way | Medium | Delete partial on failure; show retryable error; no DB record until success |
| Disk fills during download | Medium | Free-space pre-check (`× 1.1`); per-chunk `StatFs.getAvailableBytes()` recheck every 64 MB |
| User forgets to clean up → cache grows | Medium | GC worker on app startup with TTL; "Clear now" button in Settings |
| `LoadControl` rebuild requires ExoPlayer recreation | Medium | Media3 params not live-tunable; escalation uses controlled source-rebuild at current position (existing pattern) |
| OOM on low-end device with heavy file | Medium | `cacheBudgetBytes` cap from `DeviceCapabilityProbe`; `maxBufferSec` hard-clamped |
| Android 10+ scoped storage breaks direct writes | High | Use MediaStore path via `PdfExportHelper` pattern; covered by manual test case 9 |
| Legacy flavor (API 23) runtime permission missing | Low | `WRITE_EXTERNAL_STORAGE` is already in manifest for legacy; runtime check in `StreamOffloadUseCase` before starting download |
| Offloaded file path becomes stale (user deleted via system Files) | Medium | `StreamingCacheRepository.verifyAndPrune()` on app startup drops entries where file no longer exists |
| User plays the same file twice | Low | `resourceHash` primary-key lookup: if `StreamingCacheEntry` exists and file is present, skip download, play from local; update `lastPlayedAt` |
| Predictive-back on dialog mid-download | Low | `StreamOffloadOfferDialog` as `BottomSheetDialogFragment` with `onCancel` wired to abort download cleanly |

---

## 9. Testing Plan

### 9.1 Unit Tests

- `PrefetchFormulaTest` in `app_v2/src/test/java/com/sza/fastmediasorter/domain/playback/`:
  - Parametrised over all seven worked examples in section 5.4 — exact `targetPrefetchSec` + viability match.
  - `ratio = 0.899` → MARGINAL; `ratio = 0.900` → NOT_VIABLE; hysteresis test: starting in NOT_VIABLE, `ratio = 0.85` stays NOT_VIABLE; at `ratio = 0.79` transitions back.
  - Boundary: `speedMbps = 0.0` → NOT_VIABLE regardless of bitrate.
  - Boundary: `bitrateKbps = null` uses protocol baseline.
  - Device budget: `freeHeapBytes = 32 MB` → `maxBufferSec` ≤ small number; formula never exceeds it.
  - File duration ceiling: 30 s clip → `target ≤ 24 s` even on very slow connection.
- `DeviceCapabilityProbeTest`: simulated `memoryClass = 64 MB`, free storage = 100 MB → `cacheBudgetBytes ≤ 25 MB`.
- `PrefetchProgressTrackerTest`: 2 stalls → ratio +0.1; 4 stalls → +0.2 and viability transition.
- `StreamOffloadUseCaseTest` (integration with mocked strategies): happy-path SMB download completes → entry recorded; failure mid-download → partial file deleted, no entry; cancel → same; disk-full simulation → early error before starting.
- `StreamingCacheRepositoryTest`: `verifyAndPrune()` drops entries for missing files; `record()` replaces on duplicate `resourceHash`.
- `StreamingCacheStartupGcWorkerTest`: entries older than TTL deleted; entries within TTL preserved; corrupt file paths handled.

### 9.2 Manual Test Cases

1. **Happy path fast SMB**: open HD MKV on fast NAS → overlay briefly → smooth playback.
2. **Slow SMB heavy file — MARGINAL**: open 4K HEVC on 10 Mbps SMB → overlay amber, target ~30 s → playback OK after wait.
3. **Slow SMB too-heavy file — NOT_VIABLE offer**: open 4K HDR on 3 Mbps SMB → offload dialog appears with correct size / ETA / free-space.
4. **Accept offload**: tap `Download and play` → progress dialog → completion → playback from local copy with `Playing from local copy · X GB` toast.
5. **Try streaming anyway**: from the same dialog → MARGINAL mode; stalls expected; no crash.
6. **Cancel offload mid-download**: verify partial file deleted from `Downloads/FMS/streaming/`; no DB row.
7. **Cleanup on exit (Ask mode)**: after offload playback, close player → dialog `Keep / Delete / Don't ask again`.
8. **Cleanup on file switch**: during offloaded playback, navigate to next file → prompt for previous file.
9. **Auto-delete mode**: set Settings → `Auto-delete on player exit` → offload and close → file silently removed.
10. **Auto-keep mode + TTL GC**: set TTL to 1 day → change device date +2 days → app startup → entry removed automatically.
11. **"Play again" optimisation**: offload a file, choose `Keep`, exit, open the same file → no dialog, no download, plays immediately from cache; `lastPlayedAt` updated.
12. **Missing file recovery**: offload a file, then delete it manually via system Files → open the same file again → entry pruned, fresh offload offered.
13. **Disk full before start**: fill storage to > 90 % → offload dialog → primary action disabled with `Free up storage` link.
14. **Disk full during download**: start download, fill storage concurrently → download aborts with clear error; partial file cleaned.
15. **Android 10+ scoped-storage write**: on API 29 device verify file appears in system Downloads/FMS/streaming via MediaStore.
16. **Android 6 legacy flavor**: on API 23 emulator verify direct-file-I/O write works; WRITE permission prompted on first offload.
17. **Rotation during offload progress**: rotate device mid-download → progress dialog persists with correct value.
18. **No speed-test data**: open file on never-tested resource → formula uses baseline; overlay visible; no false NOT_VIABLE on unknown data.
19. **Session escalation**: throttle Wi-Fi during VIABLE playback → two stalls → ratio +0.1 toast; four stalls cross threshold → mid-session offload offer.
20. **Overlay a11y**: TalkBack reads "Pre-caching 8 of 20 seconds, SMB"; amber MARGINAL state adds "Network is tight".

### 9.3 Maestro E2E

Add `maestro/critical/video_offload_flow.yaml`:

- Launch player against a seeded heavy-file / slow-network fixture (Maestro network-shaping stub).
- Assert `offload_dialog_title` visible; tap `download_and_play`.
- Assert progress dialog advances; wait up to 5 min for completion.
- Assert `prefetch_overlay` shows `Playing from local copy`.
- Close player → assert `cleanup_prompt_dialog` visible; tap `delete`.
- Assert `Downloads/FMS/streaming/<hash>/` is empty via `runFlow: deviceFilesAssertion`.

Also `maestro/smoke/video_prefetch_indicator.yaml`: basic overlay visibility on a local file.

---

## 10. Accessibility

The pre-cache overlay and two new dialogs are all a11y-reviewed:

- **Pre-cache overlay**: dynamic `contentDescription` recomputed on each progress update (`"Pre-caching 8 of 20 seconds from SMB"`); min 48×48 dp tap target for dismiss; `sp` units for numeric text; high-contrast outline; TalkBack auto-dismiss delay doubled to 1200 ms; amber MARGINAL state adds explicit text `Network is tight`, not colour-only.
- **Offload dialog**: each row (`Size`, `Speed`, `ETA`, `Free space`, `Destination`) is a standalone `TextView` with its own semantic label — TalkBack reads them in order; primary/secondary/dismiss buttons have explicit accessibility actions.
- **Cleanup prompt**: three-option dialog with `Keep` as the safe default (accessibility guideline: destructive action never pre-focused); "Don't ask again" is a `CheckBox` with clear description.
- **Settings rows**: standard `Preference` components — inherit platform a11y; derivation info `?` icon has `contentDescription = "Show pre-cache calculation details"`.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN):
  - `Adaptive video pre-cache — the player calculates how many seconds of movie to cache ahead based on network speed test, file bitrate, and your device's free memory, using a transparent physics-based formula; a small overlay shows pre-caching progress.`
  - `Streaming offload — when the network is too slow to stream a file smoothly (bitrate higher than ~90 % of bandwidth), the player offers to download the file first to Downloads/FMS/streaming/ and then play it smoothly from disk; after playback you can keep or delete the local copy, with an optional auto-cleanup TTL.`
- `docs/FEATURES_RU.md` (RU):
  - `Адаптивная предзагрузка видео — плеер рассчитывает, сколько секунд фильма закешировать вперёд, исходя из speed-теста сети, битрейта файла и свободной памяти устройства, по прозрачной физической формуле; маленькая панель показывает прогресс предзагрузки.`
  - `Локальная загрузка потока — когда сеть слишком медленная для стабильного стрима (битрейт ближе ~90 % к скорости канала), плеер предлагает сначала скачать файл в Downloads/FMS/streaming/ и затем воспроизвести его локально; после просмотра можно оставить или удалить копию, есть автоочистка по TTL.`
- `docs/FEATURES_UK.md` (UK):
  - `Адаптивне попереднє кешування відео — плеєр обчислює, скільки секунд фільму закешувати наперед, виходячи зі speed-тесту мережі, бітрейту файлу та вільної пам'яті пристрою, за прозорою фізичною формулою; невелика панель показує прогрес кешування.`
  - `Локальне завантаження потоку — коли мережа надто повільна для стабільного стрімінгу (бітрейт близько ~90 % до швидкості каналу), плеєр пропонує спочатку завантажити файл у Downloads/FMS/streaming/ і потім відтворити його локально; після перегляду можна залишити або видалити копію, є автоочищення за TTL.`

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Physics-based formula instead of multiplicative heuristic**
- **Decision:** `targetSec = safetyMargin / (1 − bitrate/speed ratio)`.
- **Alternatives considered:** Six-factor multiplicative model (prior draft); flat per-protocol constants.
- **Reason:** Multiplicative compounding produces silent clamp-hits (values saturating at 90 s with no signal that the network is truly insufficient). The ratio model has a direct physical meaning and a natural `viability` threshold, and the formula is inspectable by users via the derivation dialog.

**ADR-2: Introduce `NOT_VIABLE` → offer local offload instead of streaming**
- **Decision:** When `ratio ≥ 0.9`, present a download-then-play flow, not a generic "streaming failed" error.
- **Alternatives considered:** (a) always retry streaming; (b) silent background pre-download; (c) refuse playback.
- **Reason:** (a) wastes user time on a physically impossible goal. (b) surprises users and fills storage without consent. (c) strands the user — they chose this file for a reason. The explicit offer gives them information (size, ETA, free space) and agency (three clear actions).

**ADR-3: Drop threads from the formula — they already shape speedMbps**
- **Decision:** `KEY_NETWORK_PARALLELISM` is not an input to `PrefetchFormula`.
- **Alternatives considered:** Keep `threadsFactor` (prior draft).
- **Reason:** Speed tests run with the current thread count, so their influence is already captured in `speedMbps`. Including threads again double-counts and makes the formula drift at the `MORE` end.

**ADR-4: Device budget in bytes, not tier enum**
- **Decision:** `cacheBudgetBytes = min(heap × 0.15, storage × 0.25, 256 MB)` directly feeds `maxBufferSec`.
- **Alternatives considered:** `LOW / MID / HIGH` tier as multiplicative factor.
- **Reason:** Bytes are the real constraint. Tier labels are good for UI ("your device: Medium") but poor for the formula. The `DeviceCapabilityProbe` still exposes a tier string for the derivation dialog.

**ADR-5: File duration as a hard ceiling**
- **Decision:** `maxBufferSec ≤ remainingSec × 0.8`.
- **Alternatives considered:** Let the formula go up to 90 s unconditionally.
- **Reason:** Caching 90 s of a 30 s clip wastes memory and misleads the progress indicator. Tying the ceiling to the file is free and obviously correct.

**ADR-6: Offloaded files live in public `Downloads/FMS/streaming/`, not app-private cache**
- **Decision:** Public `DIRECTORY_DOWNLOADS` subfolder.
- **Alternatives considered:** `context.externalCacheDir` (private, auto-cleaned by OS).
- **Reason:** Users explicitly asked for visibility — they want to know what the app stored, to manage it via the system Files app, and to keep a local copy after deletion from memory. App-private cache hides all three. Downside (needing MediaStore for API 29+) is mitigated by the existing `PdfExportHelper` pattern.

**ADR-7: Cleanup prompt on every offloaded-file session (default "Ask")**
- **Decision:** Three-choice prompt on player exit / file switch; user can opt into `Auto-delete` or `Auto-keep` in Settings.
- **Alternatives considered:** Silent keep; silent delete; only prompt via the TTL worker.
- **Reason:** Silent keep fills disk. Silent delete surprises users who wanted to rewatch. TTL-only prompts arrive out of context. The in-session prompt is the most honest default; the Settings flag gives power users one-time consent.

---

## 13. Implementation Steps

Sequential — each step depends on the previous.

1. Create `domain/model/PrefetchPlan.kt` (plan + derivation + viability enum + protocol enum + multiplier enum). Log dev-log.
2. Create `core/util/DeviceCapabilityProbe.kt` — `memoryClass`, core count, `StatFs(cacheDir)`, `cacheBudgetBytes` byte cap. Bind as `@Singleton` in `di/CoreModule.kt`. Log both.
3. Create `domain/playback/PrefetchFormula.kt` — pure ratio-based `compute(derivation): PrefetchPlan` with viability classification, protocol safety margins, device/file ceilings. Log.
4. Add `PrefetchFormulaTest` + `DeviceCapabilityProbeTest` in `app_v2/src/test/java/`. Run `.\gradlew.bat testStandardDebugUnitTest` — must pass. Log tests.
5. Create `domain/model/PrefetchCacheMultiplier.kt` + add `KEY_PREFETCH_CACHE_MULTIPLIER` Flow to [SettingsRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt). Log both.
6. Create `ui/player/helpers/PrefetchProgressTracker.kt` — `Player.Listener` with stall counter, `StateFlow<Int>`, `SharedFlow<String>` for escalation toasts, ratio-inflation recomputation. Log.
7. **Backup** [VideoPlayerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt) → `temp/VideoPlayerManager_YYYYMMDD_HHmm.kt.backup`. Remove buffer constants; accept `PrefetchPlan`; attach `PrefetchProgressTracker`. Log.
8. Update `Smb/Sftp/Ftp/Cloud/PlayerSetupHelper.kt` — replace hardcoded constants with `plan.*Sec * 1000`. Log each.
9. Create `data/local/db/StreamingCacheEntry.kt` (entity) + `data/local/db/StreamingCacheDao.kt` (DAO with `upsert`, `findByHash`, `allOlderThan`, `deleteByHash`, `allEntries`). Log both.
10. Update [AppDatabase.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt): bump `version = 24`; register entity + DAO accessor; add `MIGRATION_23_24` creating the table. Provide DAO via `di/DatabaseModule.kt`. Log both.
11. Create `data/repository/StreamingCacheRepository.kt` — wraps DAO, exposes suspend `record / find / delete / verifyAndPrune / listAllForUi`, resolves `resourceHash`, handles API-29+ MediaStore I/O + legacy direct-file I/O (follow [PdfExportHelper.kt:39](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfExportHelper.kt#L39) pattern). Bind in `di/CoreModule.kt`. Log.
12. Create `domain/usecase/StreamOffloadUseCase.kt` — dispatches to `SmbToLocalStrategy / SftpToLocalStrategy / FtpToLocalStrategy / CloudOperationStrategy.downloadCloudToLocal`; exposes `Flow<OffloadProgress>`; handles free-space gate, cancellation, partial cleanup, DB record on success. Log.
13. Create `worker/StreamingCacheStartupGcWorker.kt` — OneTimeWorkRequest; reads TTL from settings; calls `repository.verifyAndPrune` + deletes entries older than TTL. Enqueue from [AppStartupInitializer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt). Log both.
14. Update [SettingsRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt): add `KEY_STREAMING_CACHE_CLEANUP_MODE` (ASK / AUTO_DELETE / AUTO_KEEP) + `KEY_STREAMING_CACHE_TTL_DAYS` (0/1/3/7/30). Log.
15. Update [PlayerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt) (**backup first**): `prefetchPlan: StateFlow<PrefetchPlan?>`, `prefetchProgress: StateFlow<PrefetchProgress>`, `offloadOffer: SharedFlow<OffloadOffer>`, `offloadProgress: StateFlow<OffloadProgress?>`, `cleanupPrompt: SharedFlow<CleanupPromptRequest>`, methods `acceptOffload / declineOffload / cancelOffload / switchToLocalFile / requestCleanupIfNeeded`. Log.
16. Create `ui/player/helpers/StreamOffloadOfferDialog.kt` as `BottomSheetDialogFragment` — size, speed, required speed, ETA, free-space, destination; three buttons. Log.
17. Create `ui/player/helpers/StreamingCacheCleanupHelper.kt` — shows `CleanupPromptDialog` (Keep / Delete / Don't ask again session-scope); reads/writes cleanup-mode pref. Log.
18. Create `ui/player/views/PrefetchOverlayView.kt` — pill + progress bar; amber state on MARGINAL; "Playing from local copy" variant. Log.
19. Update [PlayerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt) (**backup first**): mount overlay, observe all VM flows, host offload/cleanup dialogs, reuse [FileCopyProgressDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FileCopyProgressDialog.kt) for download progress. Log.
20. Update [GeneralSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt) (**backup first**): add video-pre-cache radios, streaming-cleanup radios, TTL radios, "Clear now" button + entries-list dialog, derivation-info dialog. Log.
21. Update [fragment_settings_general.xml](app_v2/src/main/res/layout/fragment_settings_general.xml) + `layout-land/` version — add rows. Log both.
22. Update [SettingsSearchIndex.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt) — register new prefs. Log.
23. Add strings to `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`:
    - `prefetch_overlay_format` / `prefetch_overlay_local_copy_format` / `prefetch_margin_hint`
    - `prefetch_escalated_format`
    - `offload_dialog_title` / `offload_dialog_body_format` / `offload_action_download` / `offload_action_try_anyway` / `offload_action_cancel` / `offload_insufficient_storage`
    - `cleanup_prompt_title` / `cleanup_prompt_body_format` / `cleanup_keep` / `cleanup_delete` / `cleanup_dont_ask`
    - `pref_prefetch_cache_title` / `pref_prefetch_cache_less` / `pref_prefetch_cache_auto` / `pref_prefetch_cache_more`
    - `pref_streaming_cleanup_title` / `pref_streaming_cleanup_ask` / `pref_streaming_cleanup_auto_delete` / `pref_streaming_cleanup_auto_keep`
    - `pref_streaming_ttl_title` / `pref_streaming_ttl_off` / `pref_streaming_ttl_1d` / `pref_streaming_ttl_3d` / `pref_streaming_ttl_7d` / `pref_streaming_ttl_30d`
    - `pref_streaming_clear_now` / `pref_streaming_clear_confirm_format`
    - `pref_prefetch_info_title`
    Use `..` (two dots) not `...` for ellipsis; keep `ё` in Russian where correct. Log each strings file.
24. Add Maestro flows `maestro/critical/video_offload_flow.yaml` + `maestro/smoke/video_prefetch_indicator.yaml` per section 9.3. Log both.
25. Update `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` per section 11. Log all three.

**Mandatory step checklist at the end:**

- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (two features: adaptive pre-cache + streaming offload)
- [ ] Room DB migration `MIGRATION_23_24` added; `version = 24`
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file
- [ ] `.\gradlew.bat testStandardDebugUnitTest` passes
- [ ] `.\gradlew.bat lintStandardDebug` passes on all touched files
- [ ] Manual test cases 1–20 (section 9.2) executed on at least one standard device + one API 23 legacy device + one API 29+ device
- [ ] `..` style used in all new user-facing strings; `ё` preserved in Russian
- [ ] MediaStore scoped-storage path verified on API 29+
- [ ] `WRITE_EXTERNAL_STORAGE` runtime permission flow verified on legacy flavor

---

## 14. Out of Scope (future items)

- **Resume partially-downloaded streaming files** across sessions — Phase 1 restarts on failure. Phase 2 adds HTTP / SMB range-resume.
- **Background pre-download** of files the user is likely to open next. Privacy + bandwidth implications; separate spec.
- **Per-resource stall history in DB** feeding back into `PrefetchFormula` as a stability score.
- **Time-to-first-frame telemetry** as secondary viability signal.
- **Live first-seconds bandwidth calibration** correcting stale speed-test within 3 s of playback.
- **Proactive suggestion "This resource is often slow — offload by default?"** — requires stall history.
- **TTL on `speedMbpsCache`.** Currently values never expire; 7-day TTL is a Phase 2 item.
- **HLS / DASH / SmoothStreaming adaptive bitrate.** Separate future feature; requires multi-variant sources.
- **Transcoding proxy for incompatible codecs.** Separate scope.
- **Audio-only pre-cache tuning.** Audio files are small; current behaviour is fine.
- **Formula v3 with learned coefficients** from accumulated session telemetry.
- **Shared cleanup UI in system Files integration.** Users can already manage `Downloads/FMS/streaming/` via system Files.
