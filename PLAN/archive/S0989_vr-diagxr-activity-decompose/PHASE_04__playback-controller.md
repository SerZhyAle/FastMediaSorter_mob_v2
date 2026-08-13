# Phase 04 - Playback Controller

**Strategic spec:** [`../S0989_vr-diagxr-activity-decompose.md`](../S0989_vr-diagxr-activity-decompose.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Move ExoPlayer construction, the `Player.Listener`, and the ordered teardown into `VrDiagnosticPlaybackController`, which owns the immersive `exoPlayer` and exposes it read-only for snapshotting and panel seeding.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (banner renderer available for the error path).
- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrDiagnosticPlaybackController.kt` | New | ≤ 260 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 1500 |

> Flavor placement: vr-only helper under `src/vr/java/...`.
> Naming: not `HudPlaybackController` (existing transport-button wrapper) - this owns the ExoPlayer instance.

---

## Steps

### Step 04.1 - Create VrDiagnosticPlaybackController

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrDiagnosticPlaybackController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `VrDiagnosticPlaybackController` in `...ui.xr.helpers` with constructor deps: `context: Context`, `runtime: DiagnosticXrRuntime`, `snapshotProvider: () -> VrPlaybackSnapshot?` (returns `launchInput.snapshot`), `onError: (file: File, shortErr: String) -> Unit` (UI thread; host does banner+toast), `onTracksChanged: () -> Unit`, `onCues: (CueGroup) -> Unit`, `onPlayerChanged: (ExoPlayer?) -> Unit` (host mirrors into `HudPlaybackController.updatePlayer` and seeds `hudRenderer`), and `isHostActive: () -> Boolean`. Move `startVideoPlayback(file): Boolean` and `releasePlaybackResources()` verbatim, including the full `Player.Listener` (`onVideoSizeChanged` S1113, `onPlayerError` stage-classification + decoder detail + the ordered teardown, `onTracksChanged`, `onCues`) and the `PrefetchLoadControlFactory.build(..., tag = "vr-diagnostic")` LoadControl. Replace direct references: `queueErrorHud` -> `onError`, `refreshTrackRowsAndRepaint` -> `onTracksChanged`, `subtitleController?.submit` -> `onCues`, `subtitleController?.submitText("")` in release -> keep as an `onCues`-less separate `onSubtitleClear` callback OR pass through `onPlayerChanged(null)` and have the host clear subtitles (choose one; preserve today's "clear stale cue on release" behaviour). Preserve the exact teardown ORDER (`setVideoSurfaceEnabled(false)` -> `clearVideoSurface` -> `stop` -> `release`). Expose `val player: ExoPlayer?` (read-only) and keep the snapshot-seed block (`playWhenReady`, `volume`) invoking `onPlayerChanged(vrPlayer)` at the end of `start`.

**Verification:**

- `Glob` - `VrDiagnosticPlaybackController.kt` exists.
- `Grep` - `class VrDiagnosticPlaybackController` matches exactly once.
- `Grep` - `fun start(` and `fun release(` and `val player` present.
- `Grep` - `PrefetchLoadControlFactory` returns zero hits in `DiagnosticXrActivity.kt`.

**Status:** `[x]` done

---

### Step 04.2 - Rewire Activity playback

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Instantiate the controller in `proceedWithInitialization`, wiring: `onError = { file, err -> hudBanner.queueError(file.name, err); Toast..LENGTH_LONG.show(); playbackCtrl.release() }` on the UI thread, `onTracksChanged = ::refreshTrackRowsAndRepaint`, `onCues = { subtitleController?.submit(it) }`, `onPlayerChanged = { p -> playbackController.updatePlayer(p); if (p != null) { hudRenderer.isPlaying = p.playWhenReady; hudRenderer.volume = p.volume } else subtitleController?.submitText("") }`, `isHostActive = { !isFinishing && !isDestroyed }`, `snapshotProvider = { launchInput.snapshot }`. Replace `startVideoPlayback(file)` call sites with `playbackCtrl.start(file)` and `releasePlaybackResources()` with `playbackCtrl.release()`. Everywhere the Activity read `exoPlayer` (return-target snapshot, `onPause`/`onDestroy`), read `playbackCtrl.player`. Remove the moved methods, the `exoPlayer` field, and `startVideoPlayback`/`releasePlaybackResources` bodies from the Activity. Keep the `onPause` ordering comment (ExoPlayer release before native shutdown).

**Verification:**

- `Grep` - `startVideoPlayback` / `releasePlaybackResources` return zero hits in the Activity (only `playbackCtrl.` calls).
- `Grep` - `private var exoPlayer` returns zero hits in the Activity.
- `Grep` - `playbackCtrl.player` present (used by return-target snapshot).
- `/build` - `standard debug` + `vr debug` compile.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - `/build` `standard debug` + `vr debug`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] Phase-boundary audit (CLAUDE.md §13 Player/Glide ownership): single owner of the ExoPlayer; `release()` clears surface + removes listener + happens before native shutdown; teardown order preserved.

---

## Handoff Notes to Next Phase

`VrDiagnosticPlaybackController.player` is the sole ExoPlayer accessor; Phase 05 reads it via a `() -> ExoPlayer?` provider for the player return-target snapshot.

---

## Rollback Plan

Revert phase commit(s) - teardown order and LoadControl unchanged; no data migration.
