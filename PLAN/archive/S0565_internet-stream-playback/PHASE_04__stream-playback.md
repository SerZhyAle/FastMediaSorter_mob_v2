# Phase 04 - Stream Playback

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Route `http(s)://` and `rtsp://` URLs into ExoPlayer: detect the scheme before the LOCAL fallback, dispatch to the stream playback helper, build a streaming-tuned `DataSource.Factory` + `LoadControl`, read ICY radio metadata, retry transient network errors, and suppress position save/restore for dynamic streams.

> **Seam reconciliation (2026-06-21, `/spec-all`).** The original draft of this phase routed streams via a new `StreamUri.isStream(path)` short-circuit, leaving `ResourceType` unextended. The actually-implemented Phase 01/02 took the opposite seam: `ResourceType` was extended with `HTTP_STREAM` / `RTSP_STREAM` and `VideoPlayerManager.playVideo()` already dispatches `HTTP_STREAM, RTSP_STREAM -> playStreamVideo(...)`. `StreamUri` was never built. This phase is reconciled to the enum seam: the missing link is `determineResourceType()` classifying the scheme (the `Models.kt` comment already declares this intent). File names follow reality: `StreamPlaybackHelper.kt` / `playStreamVideo` (the original draft's `HttpStreamPlaybackHelper` / `playHttpStreamVideo` were never created).

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`StreamProtocolSupport` injectable; RTSP isolated to streamingEnabled).
- [ ] Reviewed `CloudPlaybackHelper.kt` (template), `VideoPlayerManager.playVideo()` (lines ~586-657, dispatch ~624-630), `determineResourceType()` (`PlayerMediaViewVisibilityHelper.kt:57-63`), `NetworkAwareMediaSourceFactory.dataSourceFactoryFor()` (`else -> null` at line ~113), `VideoPlayerManager` position-save calls (~603, 644).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaViewVisibilityHelper.kt` | Modified | ≤ +4 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ +30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified (replace minimal stub) | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamDataSourceFactoryProvider.kt` | New | ≤ 70 |

> **Routing seam (enum).** `ResourceType.HTTP_STREAM` / `RTSP_STREAM` already exist and `playVideo()` already dispatches them to `playStreamVideo`. The seam closes in `determineResourceType()` (`PlayerMediaViewVisibilityHelper.kt`): classify `http(s)://` -> `HTTP_STREAM`, `rtsp://` -> `RTSP_STREAM` before the LOCAL fallback. The 28-site exhaustiveness fan-out is already paid (the enum constants were added in Phase 01); reverting it would be pure churn, so the enum seam is kept.

> `VideoPlayerManager.kt` is ~666 lines and `PlayerMediaLoaderManager.kt` ~1009 lines (research §4) - keep new playback logic in the new `StreamPlaybackHelper.kt` extension file, not inline, to stay clear of the 1500 LOC ceiling.

---

## Steps

### Step 04.1 - Classify stream schemes in `determineResourceType()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaViewVisibilityHelper.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> In `determineResourceType()` (the `when` at ~line 57), add two branches before the `else` LOCAL fallback: `path.startsWith("http://") || path.startsWith("https://") -> ResourceType.HTTP_STREAM` and `path.startsWith("rtsp://") -> ResourceType.RTSP_STREAM`. This is the missing link: `playVideo()` already dispatches `HTTP_STREAM, RTSP_STREAM -> playStreamVideo`, but the classifier still returned LOCAL for stream URLs, so they fell through and failed `File.exists()` (research §2 gap). `determineResourceType()` is player-local (only the playback path calls it), so the classification is scoped to playback and does not affect resource browsing.

**Verification:**

- `Grep` - `ResourceType.HTTP_STREAM` and `ResourceType.RTSP_STREAM` present in `PlayerMediaViewVisibilityHelper.kt`, before the `else` branch.
- `Grep` - the `playVideo()` dispatch `HTTP_STREAM, RTSP_STREAM -> playStreamVideo` still present in `VideoPlayerManager.kt`.

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

### Step 04.2 - Streaming `DataSource.Factory` provider

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamDataSourceFactoryProvider.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create a small provider that builds a `DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setUserAgent("FastMediaSorter/<version> (Android)").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000).setKeepPostFor302Redirects(false).setDefaultRequestProperties(mapOf("Icy-MetaData" to "1")))`. Cross-protocol redirects + `Icy-MetaData:1` are mandatory for radio relays (research §3.2). Derive the user-agent version from `BuildConfig.VERSION_NAME`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `setAllowCrossProtocolRedirects(true)` and `"Icy-MetaData"` present.

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

### Step 04.3 - `StreamPlaybackHelper` (route + media source + LoadControl)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Following the `CloudPlaybackHelper` template, create `internal suspend fun VideoPlayerManager.playStreamVideo(path: String, playWhenReady: Boolean)`. Release the previous player; build the streaming `DataSource.Factory` (Step 04.2); build the ExoPlayer with a streaming `DefaultLoadControl` (`setBufferDurationsMs(30_000, 60_000, 2_500, 5_000).setPrioritizeTimeOverSizeThresholds(true)`, research §3.5). For `rtsp://`: ask the injected `StreamProtocolSupport.createRtspMediaSource(uri, factory)`; if it returns `null` (unsupported flavor), surface the existing error-display path with a localized "stream type not supported in this build" message (string added in Phase 06) and return. For `http(s)://`: use `DefaultMediaSourceFactory(context).setDataSourceFactory(factory)` so the core auto-detects progressive/HLS/DASH (segmented only resolves where the modules exist). Set `MediaItem.fromUri(path)`, `prepare()`, apply `playWhenReady`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `fun VideoPlayerManager.playStreamVideo` present.
- `Grep` - `createRtspMediaSource` invoked and `setPrioritizeTimeOverSizeThresholds(true)` present.
- `Grep` - `Log.d(` returns zero hits (Timber only).

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

### Step 04.4 - Inject `StreamProtocolSupport` into `VideoPlayerManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add `StreamProtocolSupport` to the `VideoPlayerManager` constructor (Hilt `@Inject`) and expose it (property) so the `playStreamVideo` extension (Step 04.3) can read `supportsRtsp` / `createRtspMediaSource`. The scheme classification itself lives in `determineResourceType()` (Step 04.1) - this step only wires the dependency. Do not alter the existing `when(resourceType)` branches.

**Verification:**

- `Grep` - `StreamProtocolSupport` referenced (constructor-injected) in `VideoPlayerManager.kt`.
- `Grep` - the `HTTP_STREAM, RTSP_STREAM -> playStreamVideo` dispatch still present in `VideoPlayerManager.kt`.

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

### Step 04.5 - ICY metadata listener + transient-error retry + position suppression

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> In the helper, add a `Player.Listener`: `onMetadata` reads `IcyHeaders` (station name/genre/bitrate) and `IcyInfo` (now-playing title), pushing the title into `MediaMetadata` via `replaceMediaItem` (research §3.3); `onPlayerError` retries `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`/`_TIMEOUT` once after a 3s delay on the player scope, and logs anything else at `Timber.w` (research §3.6). Do NOT add BehindLiveWindow/live recovery - HLS/DASH live is deferred (INDEX Deferred list). In `VideoPlayerManager`, guard the position save/restore calls (~603, 644) so they are skipped when the current item is dynamic (`player.isCurrentMediaItemDynamic`) or the path is a stream (`StreamUri.isStream(path)`) - prevents save/restore of `C.TIME_UNSET` (research §4 Med risk).

**Verification:**

- `Grep` - `IcyInfo` and `IcyHeaders` present in `StreamPlaybackHelper.kt`.
- `Grep` - `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` present; `ERROR_CODE_BEHIND_LIVE_WINDOW` returns zero hits (live deferred).
- `Grep` - `isCurrentMediaItemDynamic` (or a `StreamUri.isStream` guard) present around the position-save call in `VideoPlayerManager.kt`.

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles for a streamingEnabled flavor (standard, `.\a.ps1 fk`) and a streamingDisabled flavor (lite, `compileLiteDebugKotlin`) - the RTSP branch resolves via the injected interface, lite builds without the RTSP/HLS modules.
- [ ] `Grep` for `RtspMediaSource` in `src/main` returns zero hits (RTSP stays behind `StreamProtocolSupport`).
- [ ] `Grep` for `Log\.d\(` in touched files returns zero hits.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

- `playStreamVideo` plays both fullscreen video and progressive audio through the player engine; the screen (Phase 06) decides inline-audio vs fullscreen-video by `StreamSourceEntity.mediaKind`.
- Background radio that must survive screen-off uses the existing `AudioServiceController.playAudioWithMetadata(...)` path (already http-capable; user-agent now provided by the shared factory) - wired from the screen in Phase 06.

---

## Rollback Plan

Revert phase commit(s) - new helpers plus additive dispatch branches; no schema or migration. Existing LOCAL/SMB/cloud playback paths untouched.
