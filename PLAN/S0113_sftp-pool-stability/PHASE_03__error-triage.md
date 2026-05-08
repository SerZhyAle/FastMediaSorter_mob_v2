# Phase 03 — Error Triage

**Strategic spec:** [`../S0113_sftp-pool-stability.md`](../S0113_sftp-pool-stability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Add a reconnect attempt inside `SftpDataSource.open()` for recoverable `JSchException` failures, and gate the `onPlaybackError` → toast path in `VideoPlayerManager` so that SFTP IO errors that are auto-recovered (channel reconnected) never produce a red toast for the user.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] No open research items block this phase.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` | Modified | ≤ 290 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ existing + 20 |

> `SftpDataSource.kt` is 257 lines — no backup required. `VideoPlayerManager.kt` — confirm line count before editing; if >500 lines, create a timestamped backup.

---

## Steps

### Step 03.1 — Backup VideoPlayerManager.kt if needed

**Files:** `temp/VideoPlayerManager_<timestamp>.kt` (conditional)
**Depends on:** — start of phase

**Prompt for developer:**

> Check the line count of `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`. If it exceeds 500 lines, copy it to `temp/VideoPlayerManager_<YYYYMMDD_HHmmss>.kt`. If it is ≤500 lines, this step is a no-op (mark done regardless).

**Verification:**

- If file > 500 lines: `Glob` — `temp/VideoPlayerManager_*.kt` exists.
- If file ≤ 500 lines: step passes unconditionally.

**Status:** `[ ]` not done

---

### Step 03.2 — Reconnect on open() failure in SftpDataSource

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `SftpDataSource.open()`, the current `catch` block (lines ~103–108) sets `channelBroken = true`, calls `close()`, and rethrows. Extend it with one transparent reconnect attempt before rethrowing:
>
> 1. In the `catch (e: Exception)` handler, check whether the exception's cause chain contains a `com.jcraft.jsch.JSchException` (use `generateSequence<Throwable>(e) { it.cause }.any { it is JSchException }`).
> 2. If yes, and if `connectionAcquired` is true: call `releaseExoPlayerConnection(channel, broken = true)` (same as `close()` would do), clear `channel`/`session`/`connectionAcquired`, then make one retry: call `sftpClient.getConnectionForExoPlayer(connectionInfo)` again, update `session`/`channel`/`connectionAcquired`, and repeat the `channel.stat()` + `openStream()` calls (extract a private `attemptOpen(connectionInfo, remotePath, dataSpec)` helper if needed to avoid duplication).
> 3. If the retry succeeds — return normally. If the retry also throws — set `channelBroken = true`, call `close()`, and rethrow the original exception (not the retry exception) so ExoPlayer's error code is preserved.
> 4. If the exception is NOT a `JSchException`, do not attempt a retry — fall through to the existing `channelBroken = true` → `close()` → rethrow.
>
> Add: `Timber.d("S0113: SftpDataSource.open retry after JSchException — ${e.cause?.message}")` before the retry attempt.

**Verification:**

- `Grep` — `JSchException` imported in `SftpDataSource.kt`.
- `Grep` — `S0113: SftpDataSource.open retry` appears in `SftpDataSource.kt`.
- `Grep` — `generateSequence` appears in `SftpDataSource.kt`.
- `Grep -n "Log\.d\("` — zero hits in `SftpDataSource.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 — Suppress SFTP IO toast when playback continues

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `VideoPlayerManager.onPlayerError()`, after the existing `isThreadInterrupted` check (which already returns early), add a second early-return guard for SFTP-origin IO errors:
>
> ```kotlin
> val isSftpIoError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED &&
>     generateSequence<Throwable>(error) { it.cause }
>         .any { it is java.io.IOException && it.message?.contains("SFTP", ignoreCase = true) == true }
> if (isSftpIoError && exoPlayer?.playbackState != Player.STATE_IDLE) {
>     Timber.w("VideoPlayerManager: suppressing SFTP IO error toast — player not idle (will retry)")
>     return
> }
> ```
>
> This suppresses the toast only when ExoPlayer is still attempting recovery (not `STATE_IDLE`). When ExoPlayer exhausts retries and enters `STATE_IDLE`, the error propagates normally and the user sees the toast — which is correct for a fatal failure.
>
> Place the guard immediately after the `isThreadInterrupted` block, before `playerCallback.onBuffering(false)`.

**Verification:**

- `Grep` — `isSftpIoError` appears in `VideoPlayerManager.kt`.
- `Grep` — `suppressing SFTP IO error toast` appears in `VideoPlayerManager.kt`.
- `Grep` — `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` appears in `VideoPlayerManager.kt`.
- `Grep -n "Log\.d\("` — zero hits in `VideoPlayerManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.4 — Add S0113 debug tag at error-triage entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add `Timber.d("S0113: VideoPlayerManager.onPlayerError code=${error.errorCode} sftp=$isSftpIoError")` immediately after the `isSftpIoError` variable is defined (before the `if (isSftpIoError …)` guard). This is the spec verification tag; it will be removed on transition to `Verified`.

**Verification:**

- `Grep` — `S0113: VideoPlayerManager.onPlayerError` appears in `VideoPlayerManager.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `SftpDataSource.open()` now performs one transparent retry on `JSchException` before propagating to ExoPlayer.
- `VideoPlayerManager` suppresses the red toast for SFTP IO errors while ExoPlayer is still in recovery state.
- After Phase 01 the primary trigger for these errors is eliminated; Phase 03 adds belt-and-suspenders.

---

## Rollback Plan

Revert `SftpDataSource.kt` and `VideoPlayerManager.kt` to their pre-phase state via git. No data migration.
