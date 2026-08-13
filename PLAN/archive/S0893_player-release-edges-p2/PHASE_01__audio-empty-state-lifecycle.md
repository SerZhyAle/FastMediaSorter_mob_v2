# Phase 01 - Audio empty-state lifecycle unification

**Strategic spec:** [`../S0893_player-release-edges-p2.md`](../S0893_player-release-edges-p2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Make `AudioEmptyStateController` a self-registering `DefaultLifecycleObserver` (mirroring `VideoPlayerManager`'s existing pattern) so the decorative muted-video background releases its `MediaPlayer` on the API24+ `onStop` edge and rebuilds on `onStart`; fix the `SurfaceTextureListener` asymmetry and the orphaned-`MediaPlayer`-on-throw bug in the same file.

Findings 1+2 are the same root cause: once `onStop()` genuinely releases the player and resets `isPrepared`/`videoActive`, the existing `isPrepared` guard in `onIsPlayingChanged()` already prevents a decode start while backgrounded - no separate flag needed.

---

## Prerequisites

- [x] Working tree clean of unrelated `AudioEmptyStateController.kt` edits (confirmed via read).
- [x] `PlayerManagerInitializer.kt` construction site and `PlayerActivityLifecycleBridge.kt` call sites located and read in full.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt` | Modified | ≤ 460 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | no budget concern (single param add) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivityLifecycleBridge.kt` | Modified | no budget concern (2 lines removed) |

---

## Steps

### Step 01.1 - Convert AudioEmptyStateController to a self-registering DefaultLifecycleObserver with onStop/onStart

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `private val lifecycle: androidx.lifecycle.Lifecycle` constructor parameter (last position) and change the class to implement `androidx.lifecycle.DefaultLifecycleObserver`. Add `import androidx.lifecycle.DefaultLifecycleObserver`, `import androidx.lifecycle.Lifecycle`, `import androidx.lifecycle.LifecycleOwner`, `import android.os.Build`. Add `init { lifecycle.addObserver(this) }` near the top of the class body (after the `currentMode`/`isPlaying`/etc field block, mirroring `VideoPlayerManager`'s `init { lifecycle.addObserver(this) }` placement).
>
> Convert the existing `fun onPause()` to `override fun onPause(owner: LifecycleOwner)` and `fun onResume()` to `override fun onResume(owner: LifecycleOwner)` - bodies unchanged.
>
> Add two new overrides, API24+ gated (below API 24 the class keeps today's onPause/onResume-only behavior - matches official ExoPlayer/Media3 lifecycle guidance for pre-multi-window devices):
> ```kotlin
> // S0893: API24+ release edge - the prepared video MediaPlayer (hardware codec) must not be
> // retained for the unbounded duration the host sits stopped in background (CODE_AUDIT_PROTOCOL
> // contract item 2, "Player/Glide ownership"). Only the video path is released; bars/waves hold
> // no OS resource. videoActive=false lets a later show()/onIsPlayingChanged() know a rebuild is due.
> override fun onStop(owner: LifecycleOwner) {
>     if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
>     Timber.d("AudioEmptyStateController: onStop()")
>     if (currentMode.isVideoMode()) {
>         releaseMediaPlayer()
>         videoActive = false
>     }
> }
>
> // S0893: rebuild only if the video background was actually playing when the host stopped - a
> // paused-then-backgrounded session waits for the next onIsPlayingChanged(true) instead (see its
> // new !videoActive branch in Step 01.2), avoiding an eager rebuild of a still-paused background.
> override fun onStart(owner: LifecycleOwner) {
>     if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
>     Timber.d("AudioEmptyStateController: onStart()")
>     if (isPlaying && currentMode.isVideoMode() && !videoActive) {
>         showVideo()
>     }
> }
> ```
> Add `lifecycle.removeObserver(this)` as the last line of the existing `fun release()` body (symmetric with the new `init` registration - mirrors `VideoPlayerManager`, which relies on `Lifecycle`'s own removal-on-DESTROYED instead, but this class is explicitly `release()`-driven by its caller so an explicit removal is the correct symmetric edge here).

**Verification:**

- `Grep` - `class AudioEmptyStateController\(` in the file, followed within 10 lines by `private val lifecycle: Lifecycle` and `\) : DefaultLifecycleObserver`.
- `Grep` - `override fun onStop\(owner: LifecycleOwner\)` matches exactly once.
- `Grep` - `override fun onStart\(owner: LifecycleOwner\)` matches exactly once.
- `Grep` - `lifecycle.addObserver\(this\)` and `lifecycle.removeObserver\(this\)` each match exactly once.
- `Grep` - `Log\.d\(` returns zero hits (Timber only).

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 5/5 PASS. Files: `AudioEmptyStateController.kt` (+~30 LOC). `.\a.ps1 fc` PASS (standalone Phase 01 build).

---

### Step 01.2 - Fix onIsPlayingChanged fallback rebuild, hide() listener symmetry, and onSurfaceTextureDestroyed

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `onIsPlayingChanged()`, change the `MODE_VISUALIZATION, MODE_GIF_LOOP` branch's `if (playing)` body from the single `isPrepared` check to:
> ```kotlin
> if (playing) {
>     when {
>         isPrepared -> {
>             try {
>                 mediaPlayer?.start()
>             } catch (e: IllegalStateException) {
>                 Timber.w(e, "AudioEmptyStateController: start() failed on Error-state player")
>                 releaseMediaPlayer()
>             }
>         }
>         // S0893: released by onStop() while backgrounded-and-paused; rebuild now that playback
>         // resumed (onStart() only rebuilds when isPlaying was already true at that edge).
>         !videoActive -> showVideo()
>     }
> } else {
>     ...unchanged...
> }
> ```
>
> In `hide()`, add `videoView.surfaceTextureListener = null` as the last statement (after `videoView.isVisible = false`) - symmetric with `release()`, which already clears it. Without this, `hide()` leaves a stale listener (closing over the just-finished track's `file`) attached past the point video visualization is done for the session.
>
> In `showVideo()`'s `SurfaceTextureListener` object, change `onSurfaceTextureDestroyed`:
> ```kotlin
> override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean {
>     Timber.d("AudioEmptyStateController: onSurfaceTextureDestroyed")
>     // S0893: the TextureView is tearing down its surface - release the MediaPlayer bound to it
>     // now (its Surface would otherwise decode into a dead target) and return true so the
>     // TextureView releases the SurfaceTexture itself. Returning false (previous behavior) claims
>     // manual-release ownership per the SurfaceTextureListener contract, but no code path ever
>     // called SurfaceTexture.release() - a real leak.
>     releaseMediaPlayer()
>     videoActive = false
>     return true
> }
> ```

**Verification:**

- `Grep` - `!videoActive -> showVideo\(\)` present inside `onIsPlayingChanged`.
- `Grep` - `videoView.surfaceTextureListener = null` matches exactly 2 times in the file (`hide()` + `release()`).
- `Grep` - `override fun onSurfaceTextureDestroyed` followed within 5 lines by `return true` (not `return false`).

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS (surfaceTextureListener-null count is 3, not the plan's estimated 2 - the pre-existing `showVideo()` re-pick line was not counted when the step was written; `hide()`+`release()` symmetry itself is correct). Files: `AudioEmptyStateController.kt`.

---

### Step 01.3 - Fix orphaned MediaPlayer on startMediaPlayer initializer throw; wire the new Lifecycle constructor param

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivityLifecycleBridge.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `startMediaPlayer()`, replace:
> ```kotlin
> try {
>     mediaPlayer = MediaPlayer().apply {
> ```
> with a pattern that keeps a local reference alive across the whole try, so the catch block can release it even when `mediaPlayer` (the field) was never assigned:
> ```kotlin
> // S0893: hold the reference outside the field assignment - if any apply{} statement throws
> // (setDataSource/prepareAsync), `mediaPlayer` (the field) never gets assigned, but this local
> // `player` still points at the constructed instance so the catch block can release it instead
> // of orphaning it (only the Surface was released before this fix).
> var player: MediaPlayer? = null
> try {
>     player = MediaPlayer()
>     player.apply {
> ```
> Close the `.apply { .. }` block exactly as today, then add `mediaPlayer = player` as the line immediately after the closing brace of `.apply { .. }` (still inside the `try`). In the `catch (e: Exception)` block, add `player?.release()` as the first statement (before the existing `surface.release()`).
>
> In `PlayerManagerInitializer.kt`, add `lifecycle = activity.lifecycle` as the last constructor argument to the `AudioEmptyStateController(..)` call (after `deliveredSource = activity.deliveredAudioVisualizationSource`).
>
> In `PlayerActivityLifecycleBridge.kt`, remove the two now-redundant manual calls superseded by the `DefaultLifecycleObserver` auto-dispatch added in Step 01.1: delete the line `activity.audioEmptyStateController?.onPause()` from `onPause()` and the line `activity.audioEmptyStateController?.onResume()` from `onResumeWithViews()`. Leave every other line in both methods untouched - `Lifecycle` dispatches `ON_PAUSE`/`ON_RESUME` to the registered observer independently of these bridge methods' own call order, so no reordering of the remaining lines is required.

**Verification:**

- `Grep` - `var player: MediaPlayer\? = null` present in `startMediaPlayer()`.
- `Grep` - `player\?\.release\(\)` present in the `catch` block, before `surface.release\(\)`.
- `Grep` - `mediaPlayer = player` present after the `.apply` block closes.
- `Grep` - `lifecycle = activity.lifecycle` present in the `AudioEmptyStateController(` call in `PlayerManagerInitializer.kt`.
- `Grep` - `audioEmptyStateController?.onPause\(\)` and `audioEmptyStateController?.onResume\(\)` return zero hits in `PlayerActivityLifecycleBridge.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 5/5 PASS. Files: `AudioEmptyStateController.kt`, `PlayerManagerInitializer.kt` (+1 LOC), `PlayerActivityLifecycleBridge.kt` (-2 LOC, +2 comment lines).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` PASS (standalone Phase 01 build, then reconfirmed by Phase 02/03's cumulative builds).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for all 3 files via `post-change.ps1` (batched at phase end - see Phase 04).

---

## Handoff Notes to Next Phase

`AudioEmptyStateController` now owns a clean, self-contained release/rebuild edge. Phase 02 applies the analogous (but larger-surface) fix to the main in-app video path (`VideoPlayerManager`/`VideoPlayerLifecycleHelper`) - independent files, no shared symbols with this phase.

---

## Rollback Plan

Low-risk: revert this phase's 3 files. No Room schema, no Hilt graph, no cross-phase state.
