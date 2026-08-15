# Phase 03 - XR Owned Playback

**Strategic spec:** [`../S0296_vr-immerse-video-playback.md`](../S0296_vr-immerse-video-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Bind user VIDEO playback to the existing native external-OES surface with an XR-owned ExoPlayer instance.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] `DiagnosticXrActivity.kt` backup exists in `temp/` for this phase because the file is over 500 lines.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | current 1091, change <= 180, backup required |

> Keep all new player lifecycle logic local to `DiagnosticXrActivity` for this phase. Do not introduce a new manager unless the projected file size crosses 1500 lines.

---

## Steps

### Step 03.1 - Apply launch snapshot to XR player

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Update `startVideoPlayback(file: File)` to apply `launchInput.snapshot` to the XR-owned ExoPlayer before playback starts. Seek to `snapshot.videoPositionMs`, set `playWhenReady` from `snapshot.videoIsPlaying`, set playback speed from `snapshot.videoPlaybackSpeed`, and set volume from `snapshot.videoVolume`; keep default values when snapshot is null.

**Verification:**

- `Grep` - `launchInput.snapshot` exists inside `startVideoPlayback`.
- `Grep` - `seekTo(snapshot.videoPositionMs)` or an equivalent guarded seek exists exactly once in `DiagnosticXrActivity.kt`.
- `Grep` - `playWhenReady = snapshot.videoIsPlaying` or equivalent snapshot play-state assignment exists.
- `Grep` - `volume = snapshot.videoVolume` or equivalent snapshot volume assignment exists.

**Status:** `[x]` done

---

### Step 03.2 - Surface failure returns DecoderFailed

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Change `startVideoPlayback(file: File)` to return `Boolean`. Return `false` when `runtime.getVideoSurface()` is null. Update both call sites in `loadCurrentMediaItem()` and `onRenderThreadSessionReady()` to call `deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.DecoderFailed))` when VIDEO playback cannot attach to the native surface.

**Verification:**

- `Grep` - `private fun startVideoPlayback(file: File): Boolean` exists exactly once.
- `Grep` - `return false` exists in the null-surface branch.
- `Grep` - `VrLaunchResult.Unavailable(VrLaunchUnavailableReason.DecoderFailed)` exists in both VIDEO start call paths.
- `Grep` - `runtime.setVideoSurfaceEnabled(true)` still happens only after `setVideoSurface(videoSurface)`.

**Status:** `[x]` done

---

### Step 03.3 - Map ExoPlayer errors to typed return

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a `Player.Listener` to the XR-owned ExoPlayer in `startVideoPlayback(file: File)` that handles `onPlayerError`. On error, log with `Timber.w(error, "DiagnosticXrActivity: VR video playback failed")` and call `deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.DecoderFailed))` on the UI thread if the Activity is still alive.

**Verification:**

- `Grep` - `object : Player.Listener` exists exactly once in `DiagnosticXrActivity.kt`.
- `Grep` - `override fun onPlayerError(error:` exists exactly once.
- `Grep` - `VR video playback failed` exists exactly once.
- `Grep` - `VrLaunchUnavailableReason.DecoderFailed` is referenced from the listener.

**Status:** `[x]` done

---

### Step 03.4 - Preserve release ordering

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Keep `releasePlaybackResources()` clearing the video surface, stopping and releasing ExoPlayer before native teardown. Ensure it remains called from existing teardown paths and that the listener added in Step 03.3 cannot dispatch a second return after `deliverReturnAndFinish` has started.

**Verification:**

- `Grep` - `exoPlayer?.clearVideoSurface()` still exists before `exoPlayer?.release()`.
- `Grep` - `playbackController.updatePlayer(null)` still exists in `releasePlaybackResources`.
- `Grep` - `panelReturnDispatched.compareAndSet(false, true)` still guards panel return.
- `Grep` - `Log.d(` returns zero hits in `DiagnosticXrActivity.kt`.

**Status:** `[x]` done

---

- [x] Every `Step 03.*` above is `[x] done`.
- [x] noLegal debug source compiles through `/build` or `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1`.
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` completed after Kotlin changes.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.

---

## Handoff Notes to Next Phase

The immersive host owns VIDEO playback and can report typed decoder/surface failure.

---

## Rollback Plan

Revert phase commit(s). No native source or ABI change is introduced in this phase.
