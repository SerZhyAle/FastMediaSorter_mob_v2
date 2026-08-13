# Phase 03 — Refactor Media Loader

**Strategic spec:** [`../S0173_refactor-playback-position-persistence.md`](../S0173_refactor-playback-position-persistence.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Replace inline SFTP restore code in `PlayerMediaLoaderManager` with `PlaybackPositionRestorer.restoreAndNotify()`; add save and restore for the cloud-audio branch; remove the now-redundant private `formatTimeMs()` function.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/` directory exists (for backup files).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 975 |

> `PlayerMediaLoaderManager.kt` is 970 LOC → backup required before edit.

---

## Steps

### Step 3.1 — Replace SFTP restore with `PlaybackPositionRestorer`; remove `formatTimeMs`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** — start of phase (after Phase 01 done)

**Prompt for developer:**

> First, create a timestamped backup: copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` to `temp/PlayerMediaLoaderManager_backup_<YYYYMMDD_HHMMSS>.kt`.
>
> Then make two changes to `PlayerMediaLoaderManager.kt`:
>
> **Change A — Replace inline SFTP restore** in `playAudioViaService()`, the SMB/SFTP/FTP branch (after `val cachedFile = preCacheNetworkAudio(...)`). Locate the block that reads the saved position and shows the toast:
> ```kotlin
> // S0172: read saved position before handing off to the controller;
> // seek after prepare so the user resumes where they left off.
> val savedPositionMs = withContext(Dispatchers.IO) {
>     playbackPositionRepository?.getPosition(path) ?: 0L
> }
>
> controller.playAudioWithMetadata(uri, netTitle) { player ->
>     if (savedPositionMs > 0L) {
>         player.seekTo(savedPositionMs)
>         val timeStr = formatTimeMs(savedPositionMs)
>         activity.runOnUiThread {
>             Toast.makeText(
>                 activity,
>                 activity.getString(R.string.playback_resumed_from, timeStr),
>                 Toast.LENGTH_SHORT
>             ).show()
>         }
>         Timber.d("S0172: SFTP audio resume seekTo $savedPositionMs ms for $path")
>     }
>     activity.runOnUiThread { bindServicePlayerToView(player) }
> }
> ```
> Replace with:
> ```kotlin
> controller.playAudioWithMetadata(uri, netTitle) { player ->
>     val repo = playbackPositionRepository
>     if (repo != null) {
>         val savedPositionMs = kotlinx.coroutines.runBlocking {
>             PlaybackPositionRestorer.restoreAndNotify(
>                 path = path,
>                 repository = repo,
>                 context = activity,
>                 resumedFromStringResId = R.string.playback_resumed_from
>             )
>         }
>         if (savedPositionMs > 0L) player.seekTo(savedPositionMs)
>     }
>     activity.runOnUiThread { bindServicePlayerToView(player) }
> }
> ```
> Note: the callback `{ player -> ... }` runs on a background thread (it is the `onPlayerReady` lambda for `AudioServiceController.playAudioWithMetadata`), so `runBlocking` is safe here. If the callback runs on the main thread in practice, switch to a coroutine launch instead — check the signature of `playAudioWithMetadata`.
>
> **Change B — Remove `formatTimeMs()`** private function (around line 944–955). Verify it has no remaining call sites before removing.
>
> Add import `import com.sza.fastmediasorter.ui.player.helpers.PlaybackPositionRestorer` if not present. Remove `import android.widget.Toast` if no longer used after Change A.

**Verification:**

- `Grep` — `PlaybackPositionRestorer.restoreAndNotify` present in `PlayerMediaLoaderManager.kt`.
- `Grep` — `fun formatTimeMs` returns zero hits in `PlayerMediaLoaderManager.kt`.
- `Grep` — `formatTimeMs(` returns zero hits in `PlayerMediaLoaderManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerMediaLoaderManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 4/4 PASS. Files: PlayerMediaLoaderManager.kt (backup created, SFTP restore refactored, formatTimeMs removed). Dev log recorded.

---

### Step 3.2 — Add cloud-audio restore and save

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> In `PlayerMediaLoaderManager.kt`, in `playAudioViaService()`, locate the CLOUD branch (inside `if (resourceType == ResourceType.CLOUD)`). Within the `lifecycleScope.launch { }` block, after `val cachedFile = preCacheCloudAudio(path, currentFile)` and inside the `if (cachedFile != null)` arm, add save and restore symmetrically with the SFTP branch.
>
> Replace the existing `if (cachedFile != null)` arm:
> ```kotlin
> if (cachedFile != null) {
>     Timber.d("playAudioViaService: CLOUD pre-cache OK — playing via service: ${cachedFile.absolutePath}")
>     val uri = Uri.fromFile(cachedFile)
>     val title = currentFile.name.substringBeforeLast('.')
>     controller.playAudioWithMetadata(uri, title) { player ->
>         activity.runOnUiThread { bindServicePlayerToView(player) }
>     }
> }
> ```
> with:
> ```kotlin
> if (cachedFile != null) {
>     Timber.d("playAudioViaService: CLOUD pre-cache OK — playing via service: ${cachedFile.absolutePath}")
>     // Set original cloud path as the position key (ADR-3, S0173)
>     AudioPlaybackService.currentOriginalPath = path
>     val uri = Uri.fromFile(cachedFile)
>     val title = currentFile.name.substringBeforeLast('.')
>     controller.playAudioWithMetadata(uri, title) { player ->
>         val repo = playbackPositionRepository
>         if (repo != null) {
>             val savedPositionMs = kotlinx.coroutines.runBlocking {
>                 PlaybackPositionRestorer.restoreAndNotify(
>                     path = path,
>                     repository = repo,
>                     context = activity,
>                     resumedFromStringResId = R.string.playback_resumed_from
>                 )
>             }
>             if (savedPositionMs > 0L) player.seekTo(savedPositionMs)
>         }
>         activity.runOnUiThread { bindServicePlayerToView(player) }
>     }
> }
> ```
>
> No other changes. Do not touch the `else` (fall-through to `playVideoWithResourceType`) arm.

**Verification:**

- `Grep` — `AudioPlaybackService.currentOriginalPath = path` appears exactly twice in `PlayerMediaLoaderManager.kt` (SFTP branch + CLOUD branch).
- `Grep` — `PlaybackPositionRestorer.restoreAndNotify` appears exactly twice in `PlayerMediaLoaderManager.kt` (SFTP + CLOUD).
- `Grep` — `Log\.d\(` returns zero hits in `PlayerMediaLoaderManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 2/2 PASS. Files: PlayerMediaLoaderManager.kt (cloud branch added save+restore). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles — run `.\build-debug.PS1`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `PlayerMediaLoaderManager` uses `PlaybackPositionRestorer` for both SFTP and cloud-audio restore.
- Cloud-audio branch now sets `AudioPlaybackService.currentOriginalPath` so the save loop keys correctly.
- `formatTimeMs()` duplication is eliminated.
- Phase 04 handles docs and catalog cleanup.

---

## Rollback Plan

Revert phase commits. Restore `PlayerMediaLoaderManager.kt` from `temp/` backup if needed. No data migration or new user-facing surface.
