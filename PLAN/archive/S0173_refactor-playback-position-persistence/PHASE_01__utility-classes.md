# Phase 01 — Utility Classes

**Strategic spec:** [`../S0173_refactor-playback-position-persistence.md`](../S0173_refactor-playback-position-persistence.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Create `PositionSaveLoop` and `PlaybackPositionRestorer` as standalone utilities in `ui/player/helpers/`; no existing files are modified.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PositionSaveLoop.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionRestorer.kt` | New | ≤ 70 |

---

## Steps

### Step 1.1 — Create `PositionSaveLoop`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PositionSaveLoop.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `PositionSaveLoop.kt` in package `com.sza.fastmediasorter.ui.player.helpers`.
>
> Class `PositionSaveLoop` with constructor params:
> - `intervalMs: Long`
> - `getPath: () -> String?`
> - `getPositionMs: () -> Long`
> - `getDurationMs: () -> Long`
> - `scope: CoroutineScope`
> - `onSave: suspend (path: String, positionMs: Long, durationMs: Long) -> Unit`
>
> Internal state: `private val handler = Handler(Looper.getMainLooper())`, `private var runnable: Runnable? = null`, `private var lastSaved: Long = -1L`.
>
> `fun start()`: calls `stop()`, resets `lastSaved = -1L`, creates an anonymous `Runnable` that calls `saveNow()` then re-schedules itself via `handler.postDelayed(this, intervalMs)`, then posts it with `handler.postDelayed(runnable!!, intervalMs)`. Logs `Timber.d("PositionSaveLoop: started interval=${intervalMs}ms")`.
>
> `fun stop()`: if `runnable != null`, calls `handler.removeCallbacks(runnable!!)`, sets `runnable = null`, logs `Timber.d("PositionSaveLoop: stopped")`.
>
> `fun saveNow()`: reads `val path = getPath() ?: return`, `val position = getPositionMs()`, `val duration = getDurationMs()`. Returns if `position == lastSaved`. Returns if `duration <= 0 || position < 0`. Sets `lastSaved = position`. Calls `scope.launch(Dispatchers.IO) { try { onSave(path, position, duration) } catch (e: CancellationException) { throw e } catch (e: Exception) { Timber.e(e, "PositionSaveLoop: failed to save") } }`.
>
> Imports: `android.os.Handler`, `android.os.Looper`, `kotlinx.coroutines.*`, `timber.log.Timber`. No other dependencies.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PositionSaveLoop.kt` exists.
- `Grep` — `class PositionSaveLoop` matches exactly once.
- `Grep` — `fun start()` present.
- `Grep` — `fun stop()` present.
- `Grep` — `fun saveNow()` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 6/6 PASS. Files: ui/player/helpers/PositionSaveLoop.kt (+72 LOC). Dev log recorded.

---

### Step 1.2 — Create `PlaybackPositionRestorer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionRestorer.kt`
**Depends on:** — independent of Step 1.1

**Prompt for developer:**

> Create `PlaybackPositionRestorer.kt` in package `com.sza.fastmediasorter.ui.player.helpers`.
>
> `object PlaybackPositionRestorer` with two members:
>
> 1. `suspend fun restoreAndNotify(path: String, repository: PlaybackPositionRepository, context: Context, resumedFromStringResId: Int): Long`
>    - `val pos = withContext(Dispatchers.IO) { repository.getPosition(path) } ?: 0L`
>    - if `pos > 0L`: `withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(resumedFromStringResId, formatTimeMs(pos)), Toast.LENGTH_SHORT).show() }`; log `Timber.d("PlaybackPositionRestorer: restored %dms for path=%s", pos, path)`.
>    - returns `pos`.
>
> 2. `fun formatTimeMs(millis: Long): String` — format as `[H:]MM:SS` using `Locale.getDefault()`. Hours field included only when `millis >= 3_600_000`. Use `String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)` or `"%d:%02d"` without hours.
>
> Imports: `android.content.Context`, `android.widget.Toast`, `com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository`, `kotlinx.coroutines.*`, `timber.log.Timber`, `java.util.Locale`. No other dependencies.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionRestorer.kt` exists.
- `Grep` — `object PlaybackPositionRestorer` matches exactly once.
- `Grep` — `suspend fun restoreAndNotify` present.
- `Grep` — `fun formatTimeMs` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 5/5 PASS. Files: ui/player/helpers/PlaybackPositionRestorer.kt (+57 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `.\build-debug.PS1`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new public classes added).

---

## Handoff Notes to Next Phase

- `PositionSaveLoop` is ready for use by `PlaybackPositionHelper` and `AudioPlaybackService` (Phase 02).
- `PlaybackPositionRestorer` is ready for use by `PlayerMediaLoaderManager` (Phase 03).
- `formatTimeMs()` in `PlaybackPositionRestorer` is the canonical implementation; Phase 02 will make `PlaybackPositionHelper.formatTime()` delegate to it.

---

## Rollback Plan

Revert phase commit(s) — no existing files modified, no data migration, no user-facing surface changed.
