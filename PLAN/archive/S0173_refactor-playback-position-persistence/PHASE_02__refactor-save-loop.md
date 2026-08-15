# Phase 02 — Refactor Save Loop

**Strategic spec:** [`../S0173_refactor-playback-position-persistence.md`](../S0173_refactor-playback-position-persistence.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Replace the duplicated Handler–Runnable save-loop in `PlaybackPositionHelper` and `AudioPlaybackService` with `PositionSaveLoop`; make `PlaybackPositionHelper.formatTime()` delegate to `PlaybackPositionRestorer.formatTimeMs()`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/` directory exists (for backup files).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 860 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionHelper.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | ≤ 345 |

> `VideoPlayerManager.kt` is 855 LOC → backup required before edit.

---

## Steps

### Step 2.1 — Update `VideoPlayerManager` fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** — start of phase (after Phase 01 done)

**Prompt for developer:**

> First, create a timestamped backup: copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` to `temp/VideoPlayerManager_backup_<YYYYMMDD_HHMMSS>.kt`.
>
> Then apply three changes to `VideoPlayerManager.kt`:
>
> 1. In the "Playback position saving" field block (around lines 223–226), replace:
>    ```
>    internal var positionSaveRunnable: Runnable? = null
>    internal var lastSavedPosition: Long = -1L
>    ```
>    with:
>    ```
>    internal var positionSaveLoop: PositionSaveLoop? = null
>    ```
>    Add the import `import com.sza.fastmediasorter.ui.player.helpers.PositionSaveLoop` if not already present.
>
> 2. Remove the line `lastSavedPosition = -1L` that appears just before the `stopPositionSaving()` call in the `playVideo` / file-load method (around line 685). Do not remove or change the `stopPositionSaving()` call on the next line.
>
> No other changes. Do not modify any logic, listeners, or coroutine code.

**Verification:**

- `Grep` — `positionSaveLoop` present in `VideoPlayerManager.kt`.
- `Grep` — `positionSaveRunnable` returns zero hits in `VideoPlayerManager.kt`.
- `Grep` — `lastSavedPosition` returns zero hits in `VideoPlayerManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `VideoPlayerManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 4/4 PASS. Files: VideoPlayerManager.kt (backup created in temp/). Dev log recorded.

---

### Step 2.2 — Rewrite save-loop extensions in `PlaybackPositionHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionHelper.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Rewrite the three functions in `PlaybackPositionHelper.kt` that manage the position save loop, and update `formatTime`. Keep the file header comment and the seek-helper functions (`seekForward`, `seekBackward`) unchanged.
>
> Replace `startPositionSaving()` with:
> ```kotlin
> internal fun VideoPlayerManager.startPositionSaving() {
>     positionSaveLoop = PositionSaveLoop(
>         intervalMs = VideoPlayerManager.POSITION_SAVE_INTERVAL_MS,
>         getPath = { currentFilePath },
>         getPositionMs = { exoPlayer?.currentPosition ?: -1L },
>         getDurationMs = { exoPlayer?.duration ?: -1L },
>         scope = managerScope,
>         onSave = { path, pos, dur -> playbackPositionRepository.savePosition(path, pos, dur) }
>     )
>     positionSaveLoop!!.start()
>     Timber.d("VideoPlayerManager: Started position auto-save")
> }
> ```
>
> Replace `stopPositionSaving()` with:
> ```kotlin
> internal fun VideoPlayerManager.stopPositionSaving() {
>     positionSaveLoop?.stop()
>     positionSaveLoop = null
>     Timber.d("VideoPlayerManager: Stopped position auto-save")
> }
> ```
>
> Replace `saveCurrentPosition()` with:
> ```kotlin
> internal fun VideoPlayerManager.saveCurrentPosition() {
>     positionSaveLoop?.saveNow()
>     onPositionSaved?.invoke()
> }
> ```
>
> Replace the body of `formatTime()` with a delegation:
> ```kotlin
> internal fun VideoPlayerManager.formatTime(millis: Long): String =
>     PlaybackPositionRestorer.formatTimeMs(millis)
> ```
>
> Remove any imports that are now unused (specifically: `java.util.Locale` if only `formatTime` used it; `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.CancellationException` if only `saveCurrentPosition` used them). Add import `com.sza.fastmediasorter.ui.player.helpers.PlaybackPositionRestorer` if not already present.

**Verification:**

- `Grep` — `fun startPositionSaving` present in `PlaybackPositionHelper.kt`.
- `Grep` — `fun stopPositionSaving` present in `PlaybackPositionHelper.kt`.
- `Grep` — `fun saveCurrentPosition` present in `PlaybackPositionHelper.kt`.
- `Grep` — `PositionSaveLoop(` present in `PlaybackPositionHelper.kt`.
- `Grep` — `positionSaveRunnable` returns zero hits in `PlaybackPositionHelper.kt`.
- `Grep` — `lastSavedPosition` returns zero hits in `PlaybackPositionHelper.kt`.
- `Grep` — `retryHandler` returns zero hits in `PlaybackPositionHelper.kt`.
- `Grep` — `PlaybackPositionRestorer.formatTimeMs` present in `PlaybackPositionHelper.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlaybackPositionHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 9/9 PASS. Files: PlaybackPositionHelper.kt (rewritten). Dev log recorded.

---

### Step 2.3 — Migrate `AudioPlaybackService` to `PositionSaveLoop`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `AudioPlaybackService.kt`, replace the private save-loop fields and methods with a `PositionSaveLoop` instance. Changes are scoped to the field declarations and the three private helper methods; do not touch any other code.
>
> 1. **Field changes** — remove the three S0172 save-loop fields:
>    ```kotlin
>    private val positionSaveHandler = Handler(Looper.getMainLooper())
>    private var positionSaveRunnable: Runnable? = null
>    private var lastSavedPosition: Long = -1L
>    ```
>    Add in their place (in the same field block, after the `autoStopRunnable` declarations):
>    ```kotlin
>    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
>    private var positionSaveLoop: PositionSaveLoop? = null
>    ```
>
> 2. **`onDestroy` cleanup** — in `onDestroy()`, after `stopPositionSaving()` and before `mediaSession?.run { ... }`, add `serviceScope.cancel()`. Remove the manual `lastSavedPosition` comparison block:
>    ```kotlin
>    if (path != null && finalDur > 0 && finalPos >= 0 && finalPos != lastSavedPosition) {
>        @OptIn(DelicateCoroutinesApi::class)
>        GlobalScope.launch(Dispatchers.IO) { ... }
>    }
>    ```
>    Replace it with a direct unconditional save (captures position before player is released, same as before):
>    ```kotlin
>    if (path != null && finalDur > 0 && finalPos >= 0) {
>        serviceScope.launch(Dispatchers.IO) {
>            try {
>                playbackPositionRepository.savePosition(path, finalPos, finalDur)
>            } catch (e: Exception) {
>                Timber.e(e, "AudioPlaybackService: onDestroy save position failed")
>            }
>        }
>    }
>    ```
>    Note: `serviceScope` must be cancelled AFTER this launch (move `serviceScope.cancel()` to after this block).
>
> 3. **Replace the three private methods** — remove the existing `startPositionSaving()`, `stopPositionSaving()`, and `saveCurrentPosition()` private methods (in the `// ─── S0172` block). Replace with:
>    ```kotlin
>    private fun startPositionSaving() {
>        val p = player ?: return
>        positionSaveLoop = PositionSaveLoop(
>            intervalMs = POSITION_SAVE_INTERVAL_MS,
>            getPath = { currentOriginalPath.takeIf { it.isNotEmpty() } },
>            getPositionMs = { p.currentPosition },
>            getDurationMs = { p.duration },
>            scope = serviceScope,
>            onSave = { path, pos, dur -> playbackPositionRepository.savePosition(path, pos, dur) }
>        )
>        positionSaveLoop!!.start()
>        Timber.d("AudioPlaybackService: position auto-save started for path=$currentOriginalPath")
>    }
>
>    private fun stopPositionSaving() {
>        positionSaveLoop?.stop()
>        positionSaveLoop = null
>        Timber.d("AudioPlaybackService: position auto-save stopped")
>    }
>
>    private fun saveCurrentPosition() {
>        positionSaveLoop?.saveNow()
>    }
>    ```
>
> 4. **Imports** — add `import com.sza.fastmediasorter.ui.player.helpers.PositionSaveLoop` and `import kotlinx.coroutines.SupervisorJob` if not present. Remove `import kotlinx.coroutines.DelicateCoroutinesApi` and `import kotlinx.coroutines.GlobalScope` if no longer used elsewhere in the file.

**Verification:**

- `Grep` — `class PositionSaveLoop` absent from `AudioPlaybackService.kt` (uses, not defines).
- `Grep` — `PositionSaveLoop(` present in `AudioPlaybackService.kt`.
- `Grep` — `positionSaveHandler` returns zero hits in `AudioPlaybackService.kt`.
- `Grep` — `lastSavedPosition` returns zero hits in `AudioPlaybackService.kt`.
- `Grep` — `serviceScope` present in `AudioPlaybackService.kt`.
- `Grep` — `GlobalScope` returns zero hits in `AudioPlaybackService.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `AudioPlaybackService.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 7/7 PASS. Files: AudioPlaybackService.kt (updated). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `.\build-debug.PS1`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `PlaybackPositionHelper` and `AudioPlaybackService` are fully migrated to `PositionSaveLoop`.
- `PlaybackPositionHelper.formatTime()` now delegates to `PlaybackPositionRestorer.formatTimeMs()`.
- Phase 03 migrates `PlayerMediaLoaderManager` to use `PlaybackPositionRestorer` and adds cloud-branch save/restore.

---

## Rollback Plan

Revert phase commits — no Room schema, no new UI surfaces. Restore `VideoPlayerManager.kt` from `temp/` backup if needed.
