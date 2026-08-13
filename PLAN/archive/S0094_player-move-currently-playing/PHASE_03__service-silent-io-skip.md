# Phase 03 — Background Music Service: Silent IO Skip

**Strategic spec:** [`../S0094_player-move-currently-playing.md`](../S0094_player-move-currently-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent of Phases 01–02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

When `BackgroundMusicManager`'s ExoPlayer encounters an IO error (e.g. file moved away from Browse while service is playing it), skip silently to the next track and log to Timber only — no toast or `onMusicErrorListener` invocation. Non-IO errors retain current behavior (user-visible recovery notification).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt` | Modified | ≤ 555 |

> File is 555 lines — backup required before edit.

---

## Steps

### Step 03.1 — Backup BackgroundMusicManager and add silent IO skip in `onPlayerError`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> 1. Create a timestamped backup:
>    ```powershell
>    Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt" "temp/BackgroundMusicManager_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt"
>    ```
>
> 2. In the `onPlayerError` override inside `BackgroundMusicManager`, add an early-exit for IO errors **before** the existing log statements:
>
>    ```kotlin
>    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
>        val isIoError = error.errorCode in 2000..2999 ||
>            generateSequence<Throwable>(error) { it.cause }.any { it is java.io.IOException }
>        if (isIoError) {
>            Timber.w("BackgroundMusic: IO error on '$currentTrackPath' — silent skip (file moved/unavailable)")
>            currentTrackPath?.let { failedFiles.add(it) }
>            scope.launch { skipToNextRandomTrack() }
>            return
>        }
>        // existing code continues below unchanged
>        Timber.e(error, "BackgroundMusic: ExoPlayer ERROR detected")
>        ...
>    }
>    ```
>
>    ExoPlayer Media3 IO error codes occupy the range 2000–2999 (`ERROR_CODE_IO_UNSPECIFIED` = 2000, `ERROR_CODE_IO_FILE_NOT_FOUND` = 2004, etc.). SFTP `IOException`s wrapped through `SftpDataSource` may surface as cause-chain `IOException`s without a specific Media3 IO code, hence the cause-chain check.
>
>    The `onMusicErrorListener` is **not** invoked for IO errors — no toast is shown to the user.

**Verification:**

- `Glob` — `temp/BackgroundMusicManager_*.kt` returns at least one match.
- `Grep` — `isIoError` present in `BackgroundMusicManager.kt`.
- `Grep` — `error.errorCode in 2000..2999` present in `BackgroundMusicManager.kt`.
- `Grep` — `onMusicErrorListener` is called zero times inside the `if (isIoError)` block (the early return prevents it).
- `Grep` — `Log\.d(` returns zero hits in `BackgroundMusicManager.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 4/4 PASS. Backup exists, isIoError present, error.errorCode in 2000..2999 present, Log.d=0. onMusicErrorListener not called in IO branch (early return). Dev log recorded.

---

### Step 03.2 — Dev log entry

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt" "S0094 Phase 03" "Silent IO skip in onPlayerError: log to Timber only, no toast when file is moved/unavailable"
> ```

**Verification:**

- `Grep` — `S0094 Phase 03` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 1/1 PASS. `S0094 Phase 03` present in CHANGELOG.md at line 6325.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added (Step 03.2).

---

## Handoff Notes to Next Phase

`BackgroundMusicManager` now treats IO errors as non-fatal: it silently skips and logs, matching the expected behavior when a file is moved from Browse while the service is active. Phase 04 can proceed once all prior phases are done.

---

## Rollback Plan

Revert phase commit(s) — no interface changes, no data migration.
