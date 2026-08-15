# Phase 04 - Flat Return Restore

**Strategic spec:** [`../S0296_vr-immerse-video-playback.md`](../S0296_vr-immerse-video-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Return the final immersive VIDEO playback snapshot and restore it in the flat player.

---

## Prerequisites

- [x] Phase 03 is ✅ Done.
- [x] S0292 return-path warnings are resolved or explicitly owned by this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | current 1091, change <= 120, backup required |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerVrLaunchManager.kt` | Modified | current 390, change <= 120 |

---

## Steps

### Step 04.1 - Build return snapshot before panel launch

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a private helper named `buildPlayerReturnTarget(target: VrPanelReturnTarget.Player): VrPanelReturnTarget.Player`. It must copy `target.snapshot` and overwrite `videoPositionMs`, `videoPlaybackSpeed`, `videoIsPlaying` and `videoVolume` from `exoPlayer` when an XR player exists, then return `target.copy(snapshot = returnSnapshot)`.

**Verification:**

- `Grep` - `private fun buildPlayerReturnTarget(target: VrPanelReturnTarget.Player): VrPanelReturnTarget.Player` exists exactly once.
- `Grep` - `target.copy(snapshot = returnSnapshot)` exists exactly once.
- `Grep` - `videoPositionMs = player.currentPosition` or equivalent current-position snapshot assignment exists.
- `Grep` - `videoIsPlaying = player.isPlaying` or equivalent play-state snapshot assignment exists.

**Status:** `[x]` done

---

### Step 04.2 - Return updated target to flat player

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `buildReturnIntent(context, result)`, use the helper from Step 04.1 for `VrPanelReturnTarget.Player`. Pass `updatedTarget.snapshot.videoIsPlaying` into `PlayerActivity.createPanelIntent(isPlaying = updatedTarget.snapshot.videoIsPlaying)`, keep source file, playlist index, stereo mode and window id unchanged, and put `updatedTarget` into `VrLaunchInput.EXTRA_RETURN_TARGET`.

**Verification:**

- `Grep` - `val updatedTarget = buildPlayerReturnTarget(target)` exists exactly once.
- `Grep` - `isPlaying = updatedTarget.snapshot.videoIsPlaying` exists exactly once.
- `Grep` - `putExtra(VrLaunchInput.EXTRA_RETURN_TARGET, updatedTarget)` exists exactly once.
- `Grep` - `initialFilePath = updatedTarget.sourceFilePath` or equivalent updated-target source path usage exists.

**Status:** `[x]` done

---

### Step 04.3 - Restore returned video state in flat player

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerVrLaunchManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Update `applyPendingReturnIfReady()` so successful VIDEO returns restore `target.snapshot.videoPositionMs`, `target.snapshot.videoPlaybackSpeed`, `target.snapshot.videoVolume` and `target.snapshot.videoIsPlaying` on `activity._videoPlayerManager?.getPlayer()`. Do not seek or change playback state for `VrLaunchResult.Unavailable` or `VrLaunchResult.Crashed`; those cases must keep showing the existing snackbar and leave flat playback recoverable.

**Verification:**

- `Grep` - `videoPlayer?.seekTo(target.snapshot.videoPositionMs)` or equivalent guarded seek exists exactly once.
- `Grep` - `PlaybackParameters(target.snapshot.videoPlaybackSpeed)` exists exactly once or equivalent Media3 speed restore exists.
- `Grep` - `videoPlayer?.volume = target.snapshot.videoVolume` or equivalent volume restore exists exactly once.
- `Grep` - `target.snapshot.videoIsPlaying` controls play/pause or `playWhenReady` exactly once.
- `Grep` - `player_vr_return_crashed` and `player_vr_return_unavailable` still exist in `applyPendingReturnIfReady`.

**Status:** `[x]` done

---

- [x] Every `Step 04.*` above is `[x] done`.
- [x] noLegal debug source compiles through `/build` or `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1`.
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` completed after Kotlin changes.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.

---

## Handoff Notes to Next Phase

Flat player can consume the immersive return snapshot before controls are shown.

---

## Rollback Plan

Revert phase commit(s). No schema, resource or native ABI change is introduced in this phase.
