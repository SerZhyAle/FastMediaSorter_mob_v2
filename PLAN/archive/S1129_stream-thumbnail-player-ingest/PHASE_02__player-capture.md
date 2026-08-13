# Phase 02 - Player Capture

**Strategic spec:** [`../S1129_stream-thumbnail-player-ingest.md`](../S1129_stream-thumbnail-player-ingest.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-20
**Completed:** 2026-07-20

---

## Objective

Capture one stable fullscreen stream frame and pass it to the shared ingestor without affecting playback.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Existing PlayerView remains `surface_type="texture_view"` in portrait and landscape.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerTextureFrameCapture.kt` | New | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt` | Modified | <= 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerDependencies.kt` | New | <= 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | <= 950 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | <= 390 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt` | Modified | <= 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | <= 1500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerStateEndedTest.kt` | Modified | <= 180 |

---

## Steps

### Step 02.1 - Extract shared TextureView capture

**Files:** `PlayerTextureFrameCapture.kt`, `SaveVideoFrameManager.kt`

**Prompt for developer:**

> Add one helper that obtains the `TextureView` from `PlayerView.getVideoSurfaceView()`, checks availability, and captures either native size or requested dimensions. Reuse it from Save Frame and preserve existing error behavior.

**Verification:**

- `SaveVideoFrameManager` has no private recursive TextureView finder.
- The helper returns null on unavailable or failed capture.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Verification 2/2 PASS. Shared capture replaces recursive view traversal and preserves OOM feedback.

### Step 02.2 - Add player session state

**Files:** `VideoPlayerManager.kt`

**Prompt for developer:**

> Accept `StreamFrameIngestor`, track one capture job/attempt per stream playback session, cancel it on session replacement/release, and expose a success callback carrying the stream URL.

**Verification:**

- Manager has one owner for the capture job and one reset method.
- Release or replacement cannot ingest a frame for a stale player instance.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Verification 2/2 PASS. One job/attempt owner resets on every release or replacement.

### Step 02.3 - Capture after the stable-frame delay

**Files:** `StreamPlaybackHelper.kt`

**Prompt for developer:**

> Handle `onRenderedFirstFrame()` in the stream listener. Schedule exactly one 640x360 capture 750 ms later, verify that the same ExoPlayer session is still active, then call the ingestor and report success. Do not gate path B on headless capture settings.

**Verification:**

- Repeated `onRenderedFirstFrame()` calls schedule at most one attempt per open.
- The player remains attached and playing while the bitmap is read.
- Failure is best-effort and does not surface a playback error.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Verification 3/3 PASS. Stable-delay capture is guarded by player identity and URL.

### Step 02.4 - Wire the manually-created manager

**Files:** `PlayerViewerFactory.kt`

**Prompt for developer:**

> Forward the Activity-injected ingestor into `VideoPlayerManager` and route successful adoption to an Activity callback. Keep all existing manager constructor dependencies unchanged.

**Verification:**

- `PlayerViewerFactory.createVideoPlayerManager()` supplies the ingestor.
- The success callback is assigned exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Verification 2/2 PASS. Factory supplies the ingestor and assigns one Activity result callback.

Implementation note: the previously-added endpoint dependency had already invalidated the manager's
detekt baseline. Existing dependencies were grouped into host/network/store bundles without changing
their ownership or values; the focused state-ended regression test remained green.

---

## Phase Done Criteria

- [x] Every Step 02.* is `[x] done`.
- [x] `./a.ps1 fk` passes.
- [x] Listener symmetry and player-release audit finds no P0/P1 issue.

## Last Audit

- 2026-07-20 - P0: none; P1: none; P2: none; P3: none.
- Ownership: one delayed capture job is cancelled and rearmed by `releasePlayer()`; stale player and URL guards run before capture.
- Listener symmetry: the stream listener remains the tracked `activeExtraPlayerListener` and is removed on release/destroy.
- Memory/performance: capture is bounded to one 640x360 bitmap per open; no view or Activity is retained by a singleton.
- Evidence: `a.ps1 fk` PASS, focused manager regression test PASS, listener delta `expected: 0 | actual: 0`,
  scoped detekt PASS, minified standardRelease smoke `r8Markers=0`, `appErrors=0`.

---

## Handoff Notes to Next Phase

Successful adoption now reaches the PlayerActivity callback; Phase 03 owns the caller result and grid repaint.

---

## Rollback Plan

Revert player capture wiring and restore Save Frame's local capture helper; no data migration.
