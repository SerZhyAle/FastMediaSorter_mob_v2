# Phase 02 — Detection Path Guard

**Strategic spec:** [`../spec_vr-stereo-state.md`](../spec_vr-stereo-state.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-04-26
**Completed:** 2026-04-26

---

## Objective

Thread a `forFilePath: String` token through the stereo detection callback chain so `PlayerStereoModeCoordinator` can reject stale auto-detection results from a previous file's `onTracksChanged` firing after `currentFilePath` was already advanced to the next file.

**Root cause:** In `VideoPlayerManager.onTracksChanged`, `val requestedPath = currentFilePath` is captured after `playVideo` already updated `currentFilePath` to file B. If the previous file's `onTracksChanged` event fires late, detection runs with file B's path but file A's `Format` (from the `tracks` argument), and the path guard `requestedPath == currentFilePath` passes incorrectly. The coordinator has no independent way to reject the stale result.

**Fix strategy:** Pass the detection path all the way from `onTracksChanged` to the coordinator, which compares it against `currentStereoOverridePath` (set atomically during `resetStereoModeForNewFile`).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 850 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt` | Modified | ≤ 230 |

> `VideoPlayerManager.kt` (828 lines) and `PlayerViewModel.kt` (691 lines) are >500 lines → backup required before editing each.

---

## Steps

### Step 2.1 — Backup large files before editing

**Files:** none modified — backup only
**Depends on:** — start of phase

**Prompt for developer:**

> Create timestamped backups of the two files that exceed 500 lines:
>
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" `
>     "temp/VideoPlayerManager_$ts.kt.bak"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" `
>     "temp/PlayerViewModel_$ts.kt.bak"
> ```

**Verification:**

- `Glob` — `temp/VideoPlayerManager_*.kt.bak` returns at least one match.
- `Glob` — `temp/PlayerViewModel_*.kt.bak` returns at least one match.

**Status:** `[x] done`

**Step Log:**

- 2026-04-26 — Verification 2/2 PASS. Backups: VideoPlayerManager_20260426_235724.kt.bak, PlayerViewModel_20260426_235724.kt.bak.

---

### Step 2.2 — Extend `PlayerCallback.onStereoDetected` with `filePath` param

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `VideoPlayerManager.PlayerCallback`, change the default-implementation method signature:
>
> ```kotlin
> // Before:
> fun onStereoDetected(mode: StereoMode) {}
>
> // After:
> fun onStereoDetected(mode: StereoMode, forFilePath: String) {}
> ```
>
> Then, in the inner `playerListener` (`Player.Listener` anonymous object), inside `onTracksChanged`,
> update the detection coroutine so it captures the file path at the moment the track event fires
> (before the coroutine suspends) and passes it to the callback:
>
> ```kotlin
> override fun onTracksChanged(tracks: Tracks) {
>     val videoFormat = tracks.groups
>         .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
>         ?.getTrackFormat(0)
>     if (videoFormat != null) {
>         val detectionPath = currentFilePath ?: return   // capture path at event time
>         managerScope.launch {
>             val detected = withContext(Dispatchers.IO) {
>                 stereoDetector.detectForVideo(detectionPath, videoFormat)
>             }
>             if (detectionPath == currentFilePath && detected != StereoMode.UNKNOWN) {
>                 Timber.d("VideoPlayerManager: onTracksChanged → detected stereo=$detected path=$detectionPath")
>                 playerCallback.onStereoDetected(detected, detectionPath)
>             }
>         }
>     }
> }
> ```
>
> Key changes vs current code: `requestedPath` renamed to `detectionPath`; early-return when null;
> `detectionPath` passed as second argument to the callback; log line updated to include path.

**Verification:**

- `Grep` — `fun onStereoDetected\(mode: StereoMode, forFilePath: String\)` present in `VideoPlayerManager.kt`.
- `Grep` — `val detectionPath = currentFilePath` present in `VideoPlayerManager.kt`.
- `Grep` — `playerCallback.onStereoDetected\(detected, detectionPath\)` present in `VideoPlayerManager.kt`.
- `Grep` — `requestedPath` returns zero hits in `VideoPlayerManager.kt` (old name fully replaced).
- `Grep` — `Log\.d\(` returns zero hits in `VideoPlayerManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-26 — Verification 5/5 PASS. Files: VideoPlayerManager.kt (interface + onTracksChanged). Dev log pending.

---

### Step 2.3 — Update `PlayerPlaybackCallbackImpl.onStereoDetected`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> Change the `onStereoDetected` override to accept and forward the new `forFilePath` parameter:
>
> ```kotlin
> // Before:
> override fun onStereoDetected(mode: StereoMode) {
>     viewModel.setAutoDetectedStereoMode(mode)
>     ...
> }
>
> // After:
> override fun onStereoDetected(mode: StereoMode, forFilePath: String) {
>     viewModel.setAutoDetectedStereoMode(mode, forFilePath)
>     ...
> }
> ```
>
> No other logic in this method changes.

**Verification:**

- `Grep` — `override fun onStereoDetected\(mode: StereoMode, forFilePath: String\)` present in `PlayerPlaybackCallbackImpl.kt`.
- `Grep` — `viewModel.setAutoDetectedStereoMode\(mode, forFilePath\)` present in `PlayerPlaybackCallbackImpl.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerPlaybackCallbackImpl.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-26 — Verification 3/3 PASS. Files: PlayerPlaybackCallbackImpl.kt.

---

### Step 2.4 — Update `PlayerViewModel.setAutoDetectedStereoMode`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 2.3

**Prompt for developer:**

> In `PlayerViewModel`, update the delegation method to accept and forward the path:
>
> ```kotlin
> // Before:
> fun setAutoDetectedStereoMode(mode: StereoMode) = stereoCoordinator.setAutoDetectedStereoMode(mode)
>
> // After:
> fun setAutoDetectedStereoMode(mode: StereoMode, forFilePath: String = "") =
>     stereoCoordinator.setAutoDetectedStereoMode(mode, forFilePath)
> ```
>
> Default value `""` preserves backward-compatibility with callers that do not yet pass the path
> (e.g. `StandalonePlayerViewModel`'s delegation chain). The coordinator treats an empty `forFilePath`
> as "no guard" and applies the detection unconditionally.

**Verification:**

- `Grep` — `fun setAutoDetectedStereoMode\(mode: StereoMode, forFilePath: String = ""\)` present in `PlayerViewModel.kt`.
- `Grep` — `stereoCoordinator.setAutoDetectedStereoMode\(mode, forFilePath\)` present in `PlayerViewModel.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-26 — Verification 3/3 PASS. Files: PlayerViewModel.kt (+1 LOC).

---

### Step 2.5 — Add path guard in `PlayerStereoModeCoordinator.setAutoDetectedStereoMode`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt`
**Depends on:** Step 2.4

**Prompt for developer:**

> Update the `setAutoDetectedStereoMode` signature and add a stale-detection guard at the top of the method:
>
> ```kotlin
> // Before signature:
> fun setAutoDetectedStereoMode(mode: StereoMode) {
>     if (mode == StereoMode.UNKNOWN || mode == StereoMode.AUTO) return
>     ...
> }
>
> // After:
> fun setAutoDetectedStereoMode(mode: StereoMode, forFilePath: String = "") {
>     if (mode == StereoMode.UNKNOWN || mode == StereoMode.AUTO) return
>     // Reject if detection was triggered for a different file (stale onTracksChanged race).
>     if (forFilePath.isNotEmpty() && forFilePath != currentStereoOverridePath) {
>         Timber.w(
>             "PlayerStereoModeCoordinator: discarding stale detection mode=$mode " +
>                 "for=$forFilePath current=$currentStereoOverridePath"
>         )
>         return
>     }
>     ... // existing logic unchanged
> }
> ```
>
> `currentStereoOverridePath` is already set to the new file's path inside `resetStereoModeForNewFile`,
> which runs before `currentFilePath` is updated in `VideoPlayerManager.playVideo`. The guard therefore
> rejects any detection result whose path does not match the current session.

**Verification:**

- `Grep` — `fun setAutoDetectedStereoMode\(mode: StereoMode, forFilePath: String = ""\)` present in `PlayerStereoModeCoordinator.kt`.
- `Grep` — `forFilePath != currentStereoOverridePath` present in `PlayerStereoModeCoordinator.kt`.
- `Grep` — `discarding stale detection` present in `PlayerStereoModeCoordinator.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerStereoModeCoordinator.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-26 — Verification 4/4 PASS. Files: PlayerStereoModeCoordinator.kt (+8 LOC).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-04-26.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for each modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `PlayerStereoModeCoordinator.setAutoDetectedStereoMode` now guards against stale detections by comparing `forFilePath` with `currentStereoOverridePath`.
- All callers passing `forFilePath` = `""` (or not updated) continue to work without guarding — this is intentional for `StandalonePlayerViewModel` which has no navigation races.
- `VideoPlayerManager.PlayerCallback.onStereoDetected` now requires two arguments; all known implementers updated.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. Backups in `temp/` allow manual recovery if needed.
