# Phase 02 - Watchdog-triggered recovery

**Strategic spec:** [`../S0936_bugfix-stream-freeze-native-heap-snapshot.md`](../S0936_bugfix-stream-freeze-native-heap-snapshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-11
**Completed:** 2026-07-11

> ⛔ **Do not start** until both INDEX Pre-Implementation Blockers are checked: (1) a real device-repro harvest confirms the freeze is a silent stall matching Phase 01's detection, and (2) the owner has ratified the watchdog thresholds (strategic §3.3). Shipping auto-recovery on an unconfirmed mechanism risks re-preparing healthy streams (false positives) - the exact regression strategic §4.3 guards against.

---

## Objective

Turn Phase 01's stall **detection** into bounded **recovery**: on a confirmed stall, re-anchor to the live edge and re-prepare - reusing the same machinery `streamPlaybackListener.onPlayerError` already uses for `BEHIND_LIVE_WINDOW` - within a dedicated budget, surfacing the existing `RECONNECTING` wait phase.

---

## Prerequisites

- [x] Phase 01 ✅ Done.
- [x] INDEX Blocker 1 (device-repro confirmation) checked (2026-07-11, emulator throttled-connection repro).
- [x] INDEX Blocker 2 (owner threshold ratification) checked (2026-07-04).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | (add one field) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | ≤ 320 |

---

## Steps

### Step 02.1 - Add the watchdog recovery budget

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `streamWatchdogRecoveries: Int` beside the Phase 01 watchdog fields. Define `STREAM_MAX_WATCHDOG_RECOVERIES` (default per owner ratification, provisionally 3) in `StreamStallWatchdog.kt`. This budget is **separate** from the error-driven `behindLiveRecoveries` / `transientRetries` in `streamPlaybackListener` so a stall storm and an error storm cannot each mask the other's exhaustion.

**Verification:**

- `Grep` - `streamWatchdogRecoveries` present in `VideoPlayerManager.kt`.
- `Grep` - `STREAM_MAX_WATCHDOG_RECOVERIES` present in `StreamStallWatchdog.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Convert detect-log sites into bounded recovery

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamStallWatchdog.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> At both stall-detection sites from Phase 01 (position-frozen and buffering-timeout), when `streamWatchdogRecoveries < STREAM_MAX_WATCHDOG_RECOVERIES`: increment it, log `Timber.w("Stream stall - watchdog re-anchor (attempt %d) path=%s", ..)`, set the `RECONNECTING` wait phase via `playerCallback.onStreamWaitPhase(VideoPlayerManager.StreamWaitPhase.RECONNECTING)`, then `exoPlayer?.seekToDefaultPosition()` (skip the seek if `isCurrentMediaItemLive == false` - VOD/radio) followed by `exoPlayer?.prepare()`. When the budget is exhausted, stop the watchdog and surface the error path (`playerCallback.onPlaybackError` with a synthetic/last-known cause, or fall through to the existing hard-fail UI) rather than spinning. Guard the ExoPlayer reference against reassignment exactly as `onPlayerError` does (capture `erroredPlayer`, bail if `exoPlayer !== captured`).

**Verification:**

- `Grep` - `seekToDefaultPosition` now present in `StreamStallWatchdog.kt` (recovery lives here in Phase 02).
- `Grep` - `STREAM_MAX_WATCHDOG_RECOVERIES` compared before recovery (budget guard).
- `Grep` - `StreamWaitPhase.RECONNECTING` referenced from the watchdog recovery site.

**Status:** `[x]` done

---

### Step 02.3 - Reset the watchdog budget on confirmed READY

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In the `streamPlaybackListener` `STATE_READY` branch, reset `streamWatchdogRecoveries = 0` alongside the existing `behindLiveRecoveries`/`transientRetries` resets - **only** on a confirmed `READY`, never on `BUFFERING`, so a stream that flaps stall<->reconnect cannot silently refill its quota and spin forever (same invariant the error-recovery budgets already hold).

**Verification:**

- `Grep` - `streamWatchdogRecoveries = 0` present in the `STATE_READY` branch of `StreamPlaybackHelper.kt`.
- `/build` - `standard debug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fk` + `a.ps1 db` PASS (2026-07-11).
- [x] Manual device check (emulator-5556, live HLS `1+1 International`): healthy stream played minutes with zero watchdog triggers (§4.3); provoked silent stall recovered without manual exit - stall 19:25:43, re-anchor attempts 1-2 while network dead (19:25:58 / 19:26:13), network restored 19:26:26, READY+isPlaying 19:26:32 (§4.2). Budget exhaustion path also verified: 3 attempts -> existing "channel unavailable" dialog with Retry (19:23:28).
- [x] Dev log entry added (via close-and-log at ticket closure).

---

## Handoff Notes to Next Phase

Recovery is live and budgeted. Phase 03 regenerates the catalog for the new `StreamStallWatchdog` symbol and closes the dev log.

---

## Rollback Plan

Revert the Phase 02 commit(s) to fall back to Phase 01 (detect + log only) - the watchdog keeps observing without acting. No schema or user-data risk.
