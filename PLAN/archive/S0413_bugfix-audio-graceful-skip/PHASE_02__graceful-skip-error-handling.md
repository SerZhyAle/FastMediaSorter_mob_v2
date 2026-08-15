# Phase 02 - Graceful-skip error handling

**Strategic spec:** [`../S0413_bugfix-audio-graceful-skip.md`](../S0413_bugfix-audio-graceful-skip.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Completed:** 2026-06-13
**Started:** -
**Completed:** -

---

## Objective

Replace the unconditional `stopSelf()` in the audio service error handler with error classification: skip undecodable tracks, advance the queue, show a debounced message, and stop only on fatal errors or queue exhaustion.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (both string keys exist in EN/RU/UK).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | ≤ 600 |

> `AudioPlaybackService.kt` is 534 LOC (>500) - take a timestamped backup into `temp/` before editing (Step 02.1).

---

## Steps

### Step 02.1 - Backup the service file

**Files:** `temp/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` to `temp/AudioPlaybackService_<yyyyMMdd_HHmmss>.kt.bak` before editing (file is >500 LOC).

**Verification:**

- `Glob` - a `temp/AudioPlaybackService_*.kt.bak` file exists.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 1/1 PASS. Backup at `temp/AudioPlaybackService_20260613_161410.kt.bak`.

---

### Step 02.2 - Add error classification + consecutive-skip counter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a private member `consecutiveSkipCount: Int = 0` and a private helper that classifies a `PlaybackException` as skippable: `private fun PlaybackException.isSkippable(): Boolean = errorCode in 3000..4999` (parsing + decoding family - see research/01). Reset `consecutiveSkipCount = 0` inside the existing `Player.STATE_READY` branch of `onPlaybackStateChanged` (a track started → queue is progressing). Do not add trivial comments; the range check is self-documenting against the research artifact.

**Verification:**

- `Grep` - `fun PlaybackException.isSkippable` matches once.
- `Grep` - `errorCode in 3000..4999` present.
- `Grep` - `consecutiveSkipCount = 0` present inside the `STATE_READY` handling.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 3/3 PASS. Added `consecutiveSkipCount` field, `PlaybackException.isSkippable()` (errorCode in 3000..4999), STATE_READY reset.

---

### Step 02.3 - Branch onPlayerError: skip vs stop

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Rewrite `onPlayerError` to branch instead of always calling `stopSelf()`:
>
> - If `error.isSkippable()` AND the player has a next item to advance to (`player.hasNextMediaItem()` for a real playlist; treat `mediaItemCount <= 1` single-file mode as no-next) AND `consecutiveSkipCount < player.mediaItemCount`: increment `consecutiveSkipCount`, request the skip toast (Step 02.4) for the failed item's display name, then `player.seekToNextMediaItem()` and `player.prepare()` to resume on the next track. Cancel the auto-stop handler as today.
> - Else (fatal error per classification, single file, or `consecutiveSkipCount` reached `mediaItemCount` = whole queue failed in a row): keep today's behavior - clear the now-playing snapshot, and on queue-exhaustion show the `s0413_audio_queue_unplayable` toast once, then `stopSelf()`.
>
> Keep the existing `Timber` log, but the permanent log line must NOT embed the ticket id and must describe the branch taken in plain English (e.g. "skippable source error - advancing to next track" vs "fatal playback error - stopping service"). Derive the failed item's display name from the current `MediaItem` metadata title, falling back to the URI last path segment (same pattern already used in `publishWidgetSnapshot`).

**Verification:**

- `Grep` - `error.isSkippable()` referenced in `onPlayerError`.
- `Grep` - `seekToNextMediaItem` present.
- `Grep` - `R.string.s0413_audio_queue_unplayable` referenced.
- `Grep -n "Timber" ` on the file - no `Timber.(i|w|e)` line contains `S0413`.
- `Grep` - exactly the fatal/exhaustion branch calls `stopSelf()` (skip branch does not).

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 5/5 PASS. `onPlayerError` now branches: skip+advance on skippable error with next item and counter under `mediaItemCount`; else fatal stop (queue-unplayable toast on full-queue exhaustion). Skip branch returns before `stopSelf()`. No `S0413` in permanent logs.

---

### Step 02.4 - Debounced skip toast from the service

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a private `showSkipMessage(fileName: String)` that posts a text `Toast` on `Looper.getMainLooper()` with `getString(R.string.s0413_audio_track_skipped, fileName)`. Debounce: suppress if a skip toast was already shown within a short window (track last-shown timestamp via `SystemClock.elapsedRealtime()`, suppress window e.g. `SKIP_TOAST_DEBOUNCE_MS = 3_000L`) so a run of consecutive bad files does not spam identical toasts (strategic §3.1, research/02). Use the existing `autoStopHandler` (main-looper) or a main-thread post; do not introduce `GlobalScope` or non-Timber logging.

**Verification:**

- `Grep` - `fun showSkipMessage` matches once.
- `Grep` - `Toast.makeText` present.
- `Grep` - `R.string.s0413_audio_track_skipped` referenced.
- `Grep` - `SKIP_TOAST_DEBOUNCE_MS` present.
- `Grep -n "GlobalScope"` on the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 5/5 PASS. Added `showSkipMessage` (debounced via `SKIP_TOAST_DEBOUNCE_MS`, main-looper post) + `currentItemDisplayName` + imports (`Toast`, `SystemClock`). No GlobalScope, no Log.d.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `.\a.ps1 fk` (escalate to `.\a.ps1 fc` if resource refs need proof).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` on the file returns zero hits (Timber only).
- [ ] No `Timber.(i|w|e)` line in the file embeds `S0413`.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Error handling now skips per-file parsing/decoding errors, advances the queue, debounces the skip toast, and stops only on fatal errors or full-queue exhaustion. Phase 03 regenerates catalog + dev log and inserts the `BlockNeedUserTest` debug tag.

---

## Rollback Plan

Restore `AudioPlaybackService.kt` from the `temp/` backup (Step 02.1) - no data migration or persisted-state change involved.
