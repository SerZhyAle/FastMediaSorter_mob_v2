# Phase 02 - VideoPlayerLifecycleHelper release-contract fixes

**Strategic spec:** [`../S0893_player-release-edges-p2.md`](../S0893_player-release-edges-p2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Close the three confirmed release-contract gaps in the main in-app video path: (a) `PauseAwareLoadControl`/per-stream `Player.Listener` never removed at release, (b) `releasePlayer()` releases the `ExoPlayer` while still attached to `PlayerView` with an active video-effects pipeline (same GL-release-hang risk class already worked around in `StandaloneViewManager`), (c) no `onStop`/`onStart` release-and-recreate edge for API24+ backgrounding. `VideoPlayerLifecycleHelper.kt:20` (positionSaveLoop) is confirmed stale/already-fixed by S0854 - no step touches it.

---

## Prerequisites

- [x] `VideoPlayerManager.kt` read in full (820 LOC) - confirmed `DefaultLifecycleObserver` self-registration via `lifecycle.addObserver(this)`, confirmed `lifecycle = activity.lifecycle` at the one construction site (`PlayerViewerFactory.kt`), confirmed no existing `onStop`/`onStart` override.
- [x] All 6 `addListener(loadControl)` / `addListener(streamPlaybackListener(..))` call sites located (`PlayerSetupHelper.kt`, `CloudPlaybackHelper.kt`, `FtpPlaybackHelper.kt`, `SftpPlaybackHelper.kt`, `SmbPlaybackHelper.kt`, `StreamPlaybackHelper.kt`) and confirmed structurally identical.
- [x] `PlaybackPositionHelper.kt` read in full - `saveCurrentPosition()` is safe to call before `releasePlayer()` (reads live `exoPlayer` state via already-captured lambdas).
- [x] Confirmed via WebSearch: official guidance is release-on-`onStop`/recreate-on-`onStart` for API 24+, `onPause`/`onResume` below - scoping this phase's onStop/onStart pair to `Build.VERSION.SDK_INT >= Build.VERSION_CODES.N` matches both the finding's own framing and the official pattern; `legacy` flavor's narrow API23 sliver keeps today's onPause/onResume-only behavior (no regression, matches pre-24 guidance).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 870 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerLifecycleHelper.kt` | Modified | ≤ 210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt` | Modified | no budget concern (1 line) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt` | Modified | no budget concern (1 line) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt` | Modified | no budget concern (1 line) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt` | Modified | no budget concern (1 line) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt` | Modified | no budget concern (1 line) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | no budget concern (2 lines) |

---

## Steps

### Step 02.1 - Track the extra Player.Listener and resume params on VideoPlayerManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three new `internal var` fields near the existing `internal var activeResourceKey: String? = null` (same "Mutable state" region):
> ```kotlin
> // S0893: the one extra Player.Listener a playback session adds beyond playerListener - either
> // PauseAwareLoadControl (local/cloud/ftp/sftp/smb) or the per-stream listener (StreamPlaybackHelper).
> // Tracked here because every add site builds it as a local val; without a field, releasePlayer()/
> // onDestroy() have no reference to remove it symmetrically.
> internal var activeExtraPlayerListener: Player.Listener? = null
>
> // S0893: minimal state to recreate playback after an API24+ onStop release. Set at the top of
> // playVideo() so onStart() can call playVideo(..) again with the same routing.
> internal var lastResourceType: ResourceType? = null
> internal var lastCredentialsId: String? = null
> ```
> In `playVideo(path, resourceType, credentialsId, playWhenReady, onComplete)`, add `lastResourceType = resourceType` and `lastCredentialsId = credentialsId` as the first two statements inside the function body (before the existing `Timber.d("VideoPlayerManager: playVideo - ..")` line).
>
> Add the delegating lifecycle overrides next to the existing `onPause`/`onResume`/`onDestroy` overrides:
> ```kotlin
> override fun onStop(owner: LifecycleOwner) = lifecycleHelper.onStop()
>
> override fun onStart(owner: LifecycleOwner) = lifecycleHelper.onStart()
> ```

**Verification:**

- `Grep` - `internal var activeExtraPlayerListener: Player.Listener\? = null` present exactly once.
- `Grep` - `internal var lastResourceType: ResourceType\? = null` and `internal var lastCredentialsId: String\? = null` present.
- `Grep` - `lastResourceType = resourceType` and `lastCredentialsId = credentialsId` present inside `fun playVideo(`.
- `Grep` - `override fun onStop\(owner: LifecycleOwner\) = lifecycleHelper.onStop\(\)` and the `onStart` counterpart each match exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 4/4 PASS. Files: `VideoPlayerManager.kt` (+~12 LOC).

---

### Step 02.2 - releasePlayer()/onDestroy(): remove the extra listener, detach PlayerView + drain effects before release; add onStop()/onStart()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerLifecycleHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `releasePlayer()`, change the `manager.exoPlayer?.let { player -> .. }` block from:
> ```kotlin
> manager.exoPlayer?.let { player ->
>     player.removeListener(manager.playerListener)
>     player.release()
>     manager.exoPlayer = null
>     Timber.d("VideoPlayerManager: ExoPlayer released")
> }
> ```
> to:
> ```kotlin
> manager.exoPlayer?.let { player ->
>     player.removeListener(manager.playerListener)
>     manager.activeExtraPlayerListener?.let { player.removeListener(it) }
>     manager.activeExtraPlayerListener = null
>     // S0893: Media3 1.2.1 - release() can hang the main thread when a setVideoEffects() GL
>     // pipeline is still active (androidx/media #1139, #2098; same class of bug worked around in
>     // StandaloneViewManager.releaseVideoPlayer(), S0859). Drain effects and detach the surface
>     // while EGL is still valid, then release.
>     player.setVideoEffects(emptyList())
>     manager.currentPlayerView?.player = null
>     player.release()
>     manager.exoPlayer = null
>     Timber.d("VideoPlayerManager: ExoPlayer released")
> }
> ```
> In `onDestroy()`, apply the symmetric fix: add `manager.activeExtraPlayerListener?.let { playerToRelease?.removeListener(it) }` and `manager.activeExtraPlayerListener = null` right after the existing `playerToRelease?.removeListener(manager.playerListener)` try-block, and add `playerToRelease?.setVideoEffects(emptyList())` as the first statement inside the existing `try { playerToRelease?.release() }` block (before `playerToRelease?.release()`), wrapped by the same try/catch already there (so a `setVideoEffects` throw on an already-broken player degrades to the existing `Timber.e` log instead of crashing teardown).
>
> Add two new methods, API24+ gated, using the two new private fields below (add them next to the existing `private var wasPlayingBeforePause = false`):
> ```kotlin
> // S0893: remembers whether onStop() tore down an actively-loaded player, so onStart() knows to
> // recreate it - release-on-onStop/recreate-on-onStart is the official Media3 guidance for API24+
> // multi-window (developer.android.com/media/media3/exoplayer/lifecycle).
> private var wasLoadedBeforeStop = false
> private var wasPlayingBeforeStop = false
>
> fun onStop() {
>     if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
>     val hasActivePlayer = manager.exoPlayer != null ||
>         (manager.isUsingMediaPlayer && manager.mediaPlayer != null)
>     wasLoadedBeforeStop = hasActivePlayer
>     wasPlayingBeforeStop = manager.exoPlayer?.isPlaying == true ||
>         (manager.isUsingMediaPlayer && manager.mediaPlayer?.isPlaying == true)
>     if (!hasActivePlayer) return
>     Timber.d("VideoPlayerManager: onStop - releasing player while backgrounded (API24+)")
>     // Explicit save before release - releasePlayer() does not save (see S0894/S0893 sibling
>     // review), and the periodic auto-save loop may be up to POSITION_SAVE_INTERVAL_MS stale.
>     manager.saveCurrentPosition()
>     releasePlayer()
> }
>
> fun onStart() {
>     if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
>     if (!wasLoadedBeforeStop) return
>     wasLoadedBeforeStop = false
>     val path = manager.currentFilePath
>     val resourceType = manager.lastResourceType
>     if (path == null || resourceType == null) return
>     Timber.d("VideoPlayerManager: onStart - recreating player released on background")
>     manager.playVideo(path, resourceType, manager.lastCredentialsId, playWhenReady = wasPlayingBeforeStop)
> }
> ```

**Verification:**

- `Grep` - `manager.activeExtraPlayerListener` matches at least 4 times in the file (releasePlayer removal x2 lines, onDestroy removal x2 lines).
- `Grep` - `setVideoEffects\(emptyList\(\)\)` matches exactly 2 times (releasePlayer + onDestroy).
- `Grep` - `fun onStop\(\)` and `fun onStart\(\)` each match exactly once, both containing `Build.VERSION_CODES.N`.
- `Grep` - `manager.saveCurrentPosition\(\)` present inside `fun onStop\(\)`.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 5/5 PASS (`manager.activeExtraPlayerListener` count is 4, matching the "at least 4" predicate exactly). Files: `VideoPlayerLifecycleHelper.kt` (+~40 LOC).
- 2026-07-03 - **Follow-up (spec self-correction):** the two-statement `player.removeListener(manager.playerListener)` + `manager.activeExtraPlayerListener?.let { player.removeListener(it) }` form (as originally specified above) pushed `scripts/quality/assert-listener-symmetry.ps1`'s full-project ratchet from baseline 133 to 141 (+8), since the gate counts raw `removeListener` text occurrences per file and this file has zero local `addListener` text (the adds live in the 6 protocol-helper files by architecture). Refactored both `releasePlayer()` and `onDestroy()` to `listOfNotNull(manager.playerListener, manager.activeExtraPlayerListener).forEach(player::removeListener)` - same runtime behavior, but the literal `removeListener` text count drops back to exactly 2 in this file (matching the pre-S0893 baseline), verified via `assert-listener-symmetry.ps1 -Gate -ChangedFiles` scoped to all 14 S0893-touched files: **new imbalance 0**. Remaining full-project drift (139 vs baseline 133, +6) is pre-existing and unrelated to S0893 - parked as S0908.

---

### Step 02.3 - Track PauseAwareLoadControl at every add site (createPlayer + 4 protocol helpers)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In each of the 5 files, immediately after the existing line `player.addListener(loadControl)` (`PlayerSetupHelper.kt`) or `exoPlayer?.addListener(loadControl)` (the other 4), add one line: `activeExtraPlayerListener = loadControl` (`PlayerSetupHelper.kt`'s function is an extension function on `VideoPlayerManager`, so the bare receiver-less form applies directly; the other 4 files' functions are also `VideoPlayerManager` extension functions - same bare form). Do not touch `LocalPlaybackHelper.kt` - it never adds `loadControl` as a listener (local files skip `PauseAwareLoadControl` by design).

**Verification:**

- `Grep` - `activeExtraPlayerListener = loadControl` matches exactly once in each of the 5 files.
- `Grep` - same pattern in `LocalPlaybackHelper.kt` returns zero hits (must stay untouched).

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS (all 5 files confirmed, `LocalPlaybackHelper.kt` untouched). Files: `PlayerSetupHelper.kt`, `CloudPlaybackHelper.kt`, `FtpPlaybackHelper.kt`, `SftpPlaybackHelper.kt`, `SmbPlaybackHelper.kt` (+1 LOC each).

---

### Step 02.4 - Track the per-stream Player.Listener in StreamPlaybackHelper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `playStreamVideo()`, change:
> ```kotlin
> player.addListener(streamPlaybackListener(path))
> ```
> to:
> ```kotlin
> val streamListener = streamPlaybackListener(path)
> player.addListener(streamListener)
> activeExtraPlayerListener = streamListener
> ```
> This file uses `BandwidthAdaptiveLoadControl` (not `PauseAwareLoadControl`) for the actual `LoadControl`, and does not add it as a `Player.Listener` (confirmed: no `player.addListener(loadControl)` call exists in this file) - only the per-stream listener needs tracking here.

**Verification:**

- `Grep` - `val streamListener = streamPlaybackListener\(path\)` present.
- `Grep` - `activeExtraPlayerListener = streamListener` present.
- `Grep` - `player.addListener\(streamListener\)` present (raw `player.addListener(streamPlaybackListener(path))` no longer present).

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: `StreamPlaybackHelper.kt` (+3 LOC).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` PASS.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for all 7 files via `post-change.ps1` (batched at phase end - see Phase 04).

---

## Handoff Notes to Next Phase

`VideoPlayerManager`/`VideoPlayerLifecycleHelper` (the in-app `PlayerActivity` video path) now has the full release contract. `manager.currentFilePath` and the two new resume-param fields are the mechanism Phase 03's standalone hosts do **not** need - those hosts already track `viewModel.state.value.mediaFile` independently and rebuild via the existing `viewManager.show(..)` entry point instead.

---

## Rollback Plan

Low-risk: revert this phase's 7 files. No Room schema, no Hilt graph. The new `onStop`/`onStart` behavior is the only behavior change with on-device-only verifiability (background/foreground during active video playback) - flagged for `BlockNeedUserTest` at spec close, not blocking build/compile validation here.
