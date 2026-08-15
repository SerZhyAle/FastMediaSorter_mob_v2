# Phase 01 - Stall detection watchdog

**Strategic spec:** [`../S0936_bugfix-stream-freeze-native-heap-snapshot.md`](../S0936_bugfix-stream-freeze-native-heap-snapshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Add a stream-playback stall watchdog that **detects and logs** a silent freeze (position not advancing while READY, or `BUFFERING` that never reaches `READY`) without emitting a `PlaybackException`. This phase only observes and logs - it triggers **no** recovery, so it is behavior-neutral and safe to ship as the instrument that confirms the stall shape on-device.

---

## Prerequisites

- [ ] S0937 (`Verified`) stream state logging is in `StreamPlaybackHelper.streamPlaybackListener` (`Stream state=..` / `Stream isPlaying=..`).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | (near ceiling - add only fields; back up if >500 delta risk) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | ≤ 320 |

> Mirror the existing `PlaybackHealthHelper` pattern: extension functions on `VideoPlayerManager` + a few manager-held state fields + the shared `retryHandler`. No new class instance, no Hilt, no new Handler.

---

## Steps

### Step 01.1 - Create the watchdog extension file (detect + log only)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamStallWatchdog.kt` with three `internal fun VideoPlayerManager.*` extensions mirroring `PlaybackHealthHelper`: `startStreamStallWatchdog()`, `checkStreamStall()`, `cancelStreamStallWatchdog()`. `startStreamStallWatchdog` seeds `streamStallLastPosition` from `exoPlayer?.currentPosition`, resets `streamStallPolls`, and posts `checkStreamStall` on the shared `retryHandler` after `STALL_POLL_INTERVAL_MS`. `checkStreamStall` returns early unless the player `isPlaying` and `playbackState == STATE_READY`; otherwise computes `positionDelta = currentPosition - streamStallLastPosition`; if `< STALL_MIN_PROGRESS_MS` it increments `streamStallPolls` and, once `>= STALL_MAX_POLLS`, logs `Timber.w("Stream stall detected (position frozen) polls=%d path=%s", ..)` - **no recovery in this phase** - then reschedules; a healthy delta resets `streamStallPolls = 0`. `cancelStreamStallWatchdog` removes the runnable and zeroes the counters. Define the constants as a private `companion`/top-level with the DEFAULT values noted in the strategic §3.3 owner-input (poll interval, min progress, max polls) - these are provisional pending owner ratification (Blocker 2). Timber only; keep every log/probe line ≤120 chars; no bare numeric literals beyond the named constants.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt` exists.
- `Grep` - `fun VideoPlayerManager.startStreamStallWatchdog` matches once.
- `Grep` - `fun VideoPlayerManager.checkStreamStall` matches once.
- `Grep` - `fun VideoPlayerManager.cancelStreamStallWatchdog` matches once.
- `Grep` - `seekToDefaultPosition` returns **zero** hits in this file (recovery is Phase 02, not here).

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 5/5 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt` (new, 94 LOC). Single shared `streamStallRunnable` field reused for both the READY-poll and the buffering-timeout (mutually exclusive states), avoiding a second field beyond Step 01.2's declared list; buffered-duration-at-arm snapshot passed via Runnable closure param instead of a manager field, for the same reason.

---

### Step 01.2 - Add watchdog state fields to VideoPlayerManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the manager-held mutable fields the watchdog extensions read/write, alongside the existing `PlaybackHealthHelper` fields (`playbackHealthCheckRunnable`, `lastCheckedPosition`, `playbackStuckCount`): `streamStallRunnable: Runnable?`, `streamStallLastPosition: Long`, `streamStallPolls: Int`, and `streamBufferingSince: Long` (0 = not buffering). Scope them the same way as the existing health-check fields (internal/private to the manager). Do not add recovery-budget state yet (`streamWatchdogRecoveries` is Phase 02).

**Verification:**

- `Grep` - `streamStallRunnable` present in `VideoPlayerManager.kt`.
- `Grep` - `streamStallPolls` present.
- `Grep` - `streamBufferingSince` present.
- `/build` - `standard debug` compiles (fields resolve against Step 01.1 references).

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` (+8 LOC). `.\a.ps1 fk` (compileStandardDebugKotlin) - BUILD SUCCESSFUL.

---

### Step 01.3 - Wire start/cancel to stream READY / IDLE / ENDED + release

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `streamPlaybackListener.onPlaybackStateChanged`: call `startStreamStallWatchdog()` in the `STATE_READY` branch (after the existing budget resets) and `cancelStreamStallWatchdog()` in the `STATE_IDLE` and `STATE_ENDED` branches. Confirm the watchdog is also cancelled when the stream player is torn down - `VideoPlayerManager.releasePlayer()` must call `cancelStreamStallWatchdog()` on the same edge it removes `activeExtraPlayerListener` (listener symmetry - the watchdog is a de-facto listener side-channel). Do not change any recovery or callback logic.

**Verification:**

- `Grep` - `startStreamStallWatchdog()` present in `StreamPlaybackHelper.kt` inside the `STATE_READY` branch.
- `Grep` - `cancelStreamStallWatchdog()` present (state branch) and referenced from `releasePlayer` teardown.
- `Grep` - `activeExtraPlayerListener` still removed on release (symmetry preserved, not regressed).

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` (+9/-2 LOC), `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt` (+6 LOC, +1 import). Cancelled in both `releasePlayer()` AND `onDestroy()` (below API 24 the player survives to `onDestroy()` without an intervening `releasePlayer()` call) - matches the existing `cancelPlaybackHealthCheck()` precedent already symmetric across both edges in this file. Build deferred to Step 01.4 (single compile covers both).
- 2026-07-04 - Incidental gate fix: post-change closure's scoped detekt gate surfaced a pre-existing, never-baselined `ReturnCount` finding in this file's unrelated `onStart()` (3 early returns) - per project precedent the scoped gate treats any un-baselined finding in a touched file as blocking, not just lines touched by this step. Merged the first two guard conditions with `||` (3 returns -> 2), no behavior change. `.\a.ps1 fk` + fresh `:app_v2:detekt --rerun-tasks` + scoped gate re-run - all PASS.

---

### Step 01.4 - Buffering-without-ready timeout

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Cover the second stall shape: a stream stuck in `STATE_BUFFERING` that never reaches `READY`. In the `STATE_BUFFERING` branch record `streamBufferingSince` (via a small `armStreamBufferingTimeout()` helper in the watchdog file that schedules a check after `BUFFERING_STALL_TIMEOUT_MS`); in `STATE_READY` clear it (`streamBufferingSince = 0` + remove the timeout). When the timeout fires while still buffering, **distinguish healthy slow buffering from a true stall before logging** (strategic §3, §4.3 regression guard): only declare a stall when the player is **not** making buffer progress - i.e. `exoPlayer?.isLoading == false` **or** `totalBufferedDuration` has not grown since the timeout was armed (snapshot it in `armStreamBufferingTimeout`). A stream still downloading (`isLoading == true` and buffered duration rising) on a weak link is legitimate - re-arm the timeout instead of logging. On a confirmed stall log `Timber.w("Stream stall detected (buffering timeout) path=%s", ..)` - again **no recovery** here. Reuse the shared `retryHandler`.

**Verification:**

- `Grep` - `armStreamBufferingTimeout` (or equivalent) present in `StreamStallWatchdog.kt`.
- `Grep` - `isLoading` referenced in the buffering-timeout check (false-positive guard for legitimate slow buffering).
- `Grep` - `streamBufferingSince` cleared on `STATE_READY` - implemented via `StreamPlaybackHelper.kt`'s `STATE_READY` branch calling `startStreamStallWatchdog()`, which calls `cancelStreamStallWatchdog()` first (zeroes `streamBufferingSince` + removes the pending runnable) before arming the poll - single reset point in `StreamStallWatchdog.kt` instead of a duplicate inline reset in `StreamPlaybackHelper.kt` (avoids two places that must independently stay in sync). Verify: `cancelStreamStallWatchdog()` zeroes `streamBufferingSince` (`StreamStallWatchdog.kt`) AND is reachable from the `STATE_READY` branch (`StreamPlaybackHelper.kt`).
- `Grep` - `buffering timeout` string present (the detect-log line).
- `/build` - `standard debug` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 5/5 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt` (buffering-timeout functions already written in Step 01.1), `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` (`armStreamBufferingTimeout()` call added to `STATE_BUFFERING` branch in Step 01.3's edit). `.\a.ps1 fk` (compileStandardDebugKotlin) - BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` + `.\a.ps1 fc` (standard debug) - BUILD SUCCESSFUL.
- [x] `Grep` for `seekToDefaultPosition` in `StreamStallWatchdog.kt` returns zero hits (no recovery leaked into the detection phase).
- [x] `Grep -n "Log\.d\("` in every modified file returns zero hits (Timber only).
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Phase 01 leaves the watchdog **detecting and logging** stalls with zero behavior change. Phase 02 turns the two `Timber.w("Stream stall detected ..")` sites into recovery-trigger sites. The state fields, scheduling, and lifecycle wiring are all in place; Phase 02 adds only the recovery budget field and the `seekToDefaultPosition()+prepare()` call.

---

## Rollback Plan

Revert the phase commit(s). No data migration, no user-facing surface, no schema change - the watchdog is additive and only logs.
