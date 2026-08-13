# Phase 03 - Snapshot Engine

**Strategic spec:** [`../S0675_stream-grid-frame-capture.md`](../S0675_stream-grid-frame-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Capture one current frame per visible video stream via a short-lived, muted, texture-rendered ExoPlayer using an "open → first frame → grab → release" lifecycle, bounded by a concurrency limit and a per-capture timeout, writing results into `StreamFrameCache`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`StreamFrameCache` exists).
- [ ] Capture targets http(s) VIDEO only - AUDIO and RTSP sources are out of scope for snapshotting (fall back to favicon/placeholder in Phase 04). This keeps the engine in `src/main` with no flavor-gated RTSP module.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt` | New | ≤ 320 |

---

## Steps

### Step 03.1 - Create StreamFrameSnapshotManager capture lifecycle

**Files:** `ui/streams/helpers/StreamFrameSnapshotManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class StreamFrameSnapshotManager(private val context: Context, private val cache: StreamFrameCache, private val scope: CoroutineScope)`. It captures one frame for a single (url, TextureView) pair:
> - `suspend fun capture(url: String, textureView: TextureView): Bitmap?` - build a minimal `ExoPlayer` (muted: `volume = 0f`; modest live buffer like `StreamPlaybackHelper` but smaller - `setBufferDurationsMs(2_000, 8_000, 1_000, 1_000)`, `setPrioritizeTimeOverSizeThresholds(true)`; `DefaultMediaSourceFactory` http(s) auto-detect with `LiveConfiguration` near the live edge). Attach the supplied `TextureView` as the video surface via `setVideoTextureView(textureView)`. `prepare()` + `playWhenReady = true`. Suspend until `Player.Listener.onRenderedFirstFrame` (resume a `CompletableDeferred`) or the timeout elapses, then call `textureView.getBitmap()` on the main thread, `put` it into `cache`, and **always** release the player in a `finally`. Return the bitmap, or null on timeout/error.
> - Per-capture timeout const `CAPTURE_TIMEOUT_MS = 6_000L`; wrap the await in `withTimeoutOrNull`.
> - Mark the file `@UnstableApi` (Media3) like the other player helpers.
> - On any `PlaybackException` (`onPlayerError`) resume the deferred with null - do not retry; the queue (Step 03.2) moves on. Log at `Timber.i`/`Timber.w` (expected fallback, not an error) with a plain-English message and no `S0675:` prefix.

**Verification:**

- `Glob` - `StreamFrameSnapshotManager.kt` exists.
- `Grep` - `class StreamFrameSnapshotManager` matches exactly once.
- `Grep` - `suspend fun capture(` present.
- `Grep` - `onRenderedFirstFrame` present.
- `Grep` - `withTimeoutOrNull` present.
- `Grep` - `volume = 0f` present (muted).
- `Grep -n "Log\.d\("` returns zero hits in the file (Timber only).
- `.\a.ps1 fk` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Created `StreamFrameSnapshotManager` (@UnstableApi). `capture(url, textureView)` builds a muted (`volume = 0f`) ExoPlayer with a small live buffer + LiveConfiguration, attaches the TextureView surface, awaits `onRenderedFirstFrame` via `CompletableDeferred` wrapped in `withTimeoutOrNull(CAPTURE_TIMEOUT_MS=6000)`, grabs `textureView.bitmap` on main, caches it, and releases in `finally`. `onPlayerError` resolves with null (Timber.w, no S0675 prefix). All grep predicates matched; `.\a.ps1 fk` exit 0.

---

### Step 03.2 - Add bounded capture queue over visible cells

**Files:** `ui/streams/helpers/StreamFrameSnapshotManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add an ordered request queue so capture requests for visible cells run with bounded concurrency (`MAX_CONCURRENT_CAPTURES = 2`, honoring the decoder limit from research). Expose `fun request(url: String, textureViewProvider: () -> TextureView?)` that enqueues a capture, skipping it when `cache.isFresh(url)`; and `fun cancelAll()` that clears the queue and cancels in-flight captures. Drain the queue through a `Semaphore(MAX_CONCURRENT_CAPTURES)` on `scope`; resolve the `TextureView` lazily from the provider at drain time (the cell may have been recycled - if the provider returns null, drop the request). Never block the main thread. Each completed capture invokes an injected `onCaptured: (url: String) -> Unit` callback (set via constructor or a `var`) so the adapter can repaint that cell.

**Verification:**

- `Grep` - `fun request(`, `fun cancelAll(` present.
- `Grep` - `Semaphore` present.
- `Grep` - `MAX_CONCURRENT_CAPTURES` present and set to `2`.
- `Grep` - `isFresh` referenced (skip fresh entries).
- `.\a.ps1 fk` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Added ordered `ConcurrentLinkedQueue` + `Semaphore(MAX_CONCURRENT_CAPTURES=2)` drain on `scope`. `request(url, provider)` skips fresh/duplicate-pending urls and enqueues; `cancelAll()` clears the queue + cancels in-flight jobs. Provider resolved lazily on main at drain time (null -> drop). `onCaptured` var invoked on main after a successful capture. All grep predicates matched; `.\a.ps1 fk` exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exit 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`StreamFrameSnapshotManager.request(url, provider)` is the only call the grid cell needs; `onCaptured` tells the adapter which url to repaint from `StreamFrameCache`. `cancelAll()` must be called when leaving grid mode / on stop.

---

## Rollback Plan

Revert phase commit - new file, wired only in Phase 05.
