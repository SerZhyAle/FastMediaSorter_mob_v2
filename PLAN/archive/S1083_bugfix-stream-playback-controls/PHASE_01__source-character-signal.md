# Phase 01 - Source-character signal

**Strategic spec:** [`../S1083_bugfix-stream-playback-controls.md`](../S1083_bugfix-stream-playback-controls.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-18
**Completed:** 2026-07-18

---

## Objective

Expose two read-only signals from the player layer to the shared control dialog - "the active source is a stream" and "the active source is live" - plus a colour-support capability flag defaulting to true. No dialog behaviour change yet.

---

## Prerequisites

- [ ] Strategic §6.1 resolved (see `research/01__live-stream-detection.md`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1500 |

> `VideoPlayerManager.kt` and `PlayerActivity.kt` are large - back up each to `temp/S1083/` before editing (CLAUDE.md Rule 5).

---

## Steps

### Step 01.1 - Record active-source stream flag on the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the playback-dispatch `when (resourceType)` block, set a `@Volatile internal var activeSourceIsStream: Boolean` to `true` for `ResourceType.HTTP_STREAM`/`ResourceType.RTSP_STREAM` and `false` for every other branch (reuse the existing stream-type check that already computes `isDynamicStream`). Add a public `fun isActiveSourceLive(): Boolean = exoPlayer?.isCurrentMediaItemLive == true`. Reset `activeSourceIsStream = false` in `releasePlayer()`.

**Verification:**

- `Grep` - `activeSourceIsStream` matches ≥ 2 times in `VideoPlayerManager.kt` (declaration + assignment).
- `Grep` - `fun isActiveSourceLive` present in `VideoPlayerManager.kt`.
- `Grep` - `isCurrentMediaItemLive` present in `VideoPlayerManager.kt`.

**Status:** `[x]` done

---

### Step 01.2 - Add capability signals to the host contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add three default-implemented members to `PlayerHostCapabilities`: `val activeSourceIsStream: Boolean get() = false`, `val activeSourceIsLive: Boolean get() = false`, and `val supportsColorAdjustmentForActiveSource: Boolean get() = true`. Document each in one line: the defaults describe a seekable local file, so a host that does not play streams inherits the correct behaviour unchanged. Do not reference `VideoPlayerManager` from the contract.

**Verification:**

- `Grep` - `val activeSourceIsStream` present in `PlayerHostCapabilities.kt`.
- `Grep` - `val activeSourceIsLive` present in `PlayerHostCapabilities.kt`.
- `Grep` - `val supportsColorAdjustmentForActiveSource` present in `PlayerHostCapabilities.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Wire the in-app host to the manager signals

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> In `PlayerActivity`, override `activeSourceIsStream` and `activeSourceIsLive` to read from the active `VideoPlayerManager` (`activeSourceIsStream` property and `isActiveSourceLive()`). Until Phase 03 proves the effects lifecycle on a device, override `supportsColorAdjustmentForActiveSource` to false for streams. Leave `StandalonePlayerActivity` on the contract defaults - standalone never plays internet streams, so all three defaults are already correct.

**Verification:**

- `Grep` - `override val activeSourceIsStream` present in `PlayerActivity.kt`.
- `Grep` - `override val activeSourceIsLive` present in `PlayerActivity.kt`.
- `Grep -n "Log\.d\("` - zero hits in every file modified this phase.

**Status:** `[x]` done

**Step Log:**

- 2026-07-18 - Verification 3/3 PASS. Files: VideoPlayerManager.kt, PlayerHostCapabilities.kt, PlayerActivity.kt.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (public contract changed).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

The dialog can now read `host().activeSourceIsStream`, `host().activeSourceIsLive`, and `host().supportsColorAdjustmentForActiveSource`. `supportsColorAdjustmentForActiveSource` stays `true` everywhere until Phase 03; Phase 02 gates colour off `activeSourceIsStream` directly (streams cannot honour colour yet), so it does not depend on the capability flag flipping.

---

## Rollback Plan

Revert the phase commit(s) - no data migration or user-facing surface changed; the new members are unread until Phase 02.
