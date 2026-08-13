# Phase 03 - Stream colour lifecycle (device-gated)

**Strategic spec:** [`../S1083_bugfix-stream-playback-controls.md`](../S1083_bugfix-stream-playback-controls.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

> **Skipped 2026-07-20 by owner decision.** The §6.2 device experiment (colour GL-effects on the live decode path) was not run - the owner chose to ship the honest-hiding end state instead of taking on the live-edge / rebuffering regression risk. Phase 02's hidden-sections state is final: HUE/BRIGHTNESS stay hidden for streams, SPEED hidden for live. To revisit colour-on-streams later, reopen this phase, run the §6.2 experiment on a real live HLS/DASH stream, and gate the outcome on owner sign-off (§6.5).

> **Original block note (superseded):** Blocked by strategic §6.2 (device experiment). If the experiment shows colour GL-effects regress live-edge tracking or cause rebuffering, mark this phase ⏭️ Skipped - the Phase 02 hidden-sections state becomes final and HUE/BRIGHTNESS stay hidden for streams.

---

## Objective

Make the stream playback path join the video-effects lifecycle so HUE and BRIGHTNESS render on a stream, reapply the composed pipeline on the stream player's first frame and after each reconnect rebuild, and flip `supportsColorAdjustmentForActiveSource` to true so Phase 02 re-admits the colour sections for streams.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Strategic §6.2 device experiment completed with a green result (no live-edge / rebuffering regression). Record the run in the INDEX Blockers Log.
- [ ] Strategic §6.3 approach decided (reapply-after-reconnect scope).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1500 |

> Back up `VideoPlayerManager.kt` and `PlayerActivity.kt` to `temp/S1083/` before editing (CLAUDE.md Rule 5).

---

## Steps

### Step 03.1 - Feed frame size from the stream listener into the effects lifecycle

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `onVideoSizeChanged` override to the lean `streamPlaybackListener` that mirrors the full `playerListener` path: set `lastVideoWidth`/`lastVideoHeight`, set `videoSizeKnown = true` on the first valid size, and trigger the deferred effects apply (call the same manager entry the full path uses when size becomes known). Keep the listener lean otherwise - do not pull in poster/watch-clock logic.

**Verification:**

- `Grep` - `onVideoSizeChanged` present in `StreamPlaybackHelper.kt`.
- `Grep` - `videoSizeKnown` present in `StreamPlaybackHelper.kt`.

**Status:** `[ ]` not done

---

### Step 03.2 - Reapply the composed pipeline on stream READY and reconnect

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> `playStreamVideo` builds a fresh `ExoPlayer` on every (re)start, so any previously active effects pipeline is lost. After the fresh player is assigned and the surface attached, reset the effects-pipeline state (mirror `PlayerSetupHelper`'s new-instance reset) and call `applyConfiguredVideoEffects()` once size is known, so the persisted HUE/brightness reinstall on the new engine. Ensure the same reapply runs after the recovery re-prepare paths in the stream listener (behind-live re-anchor and transient retry) that reuse the same player instance.

**Verification:**

- `Grep` - `applyConfiguredVideoEffects` present in `StreamPlaybackHelper.kt`.
- `Grep` - `effectsPipelineActive` reset referenced on the stream (re)start path (in `StreamPlaybackHelper.kt` or the manager entry it calls).

**Status:** `[ ]` not done

---

### Step 03.3 - Report colour support for the active stream source

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Expose from the manager that colour adjustment is now honoured on the stream path, and override `supportsColorAdjustmentForActiveSource` in `PlayerActivity` to return `true` for streams (it was left at the default in Phase 01). Phase 02's `activeSections` guard then re-admits HUE and BRIGHTNESS for streams automatically - no further dialog edit.

**Verification:**

- `Grep` - `override val supportsColorAdjustmentForActiveSource` present in `PlayerActivity.kt`.
- `Grep -n "Log\.d\("` - zero hits in every file modified this phase.

**Status:** `[ ]` not done

---

### Step 03.4 - Insert the device-verification probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add exactly one `Timber.d("S1083: stream colour effect applied hue=.. brightness=.. live=..")` at the point the stream effects pipeline is (re)installed, so the on-device sweep can confirm colour reaches the stream engine. This probe is bound to the `BlockNeedUserTest` status and is removed when the ticket leaves it.

**Verification:**

- `Grep` - `"S1083:` matches exactly once across `app_v2/src` (single flow-entry probe).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - Player/Glide-ownership + concurrency rungs of `docs/CODE_AUDIT_PROTOCOL.md` applied (new listener override on the stream player); no unresolved P0/P1.

---

## Handoff Notes to Next Phase

HUE/BRIGHTNESS now render on streams and survive reconnect; the dialog re-admits the sections via the Phase 02 guard. If this phase was skipped (§6.2 red), colour stays hidden for streams and this file is left as the record of the decision.

---

## Rollback Plan

Revert the phase commit(s). The stream player reverts to no effects pipeline and `supportsColorAdjustmentForActiveSource` falls back to the Phase 01 default, so Phase 02 hides the colour sections again - a safe, honest state with no data change.
