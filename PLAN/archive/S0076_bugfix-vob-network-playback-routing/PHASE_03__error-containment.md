# Phase 03 - Error Containment

**Strategic spec:** [`../S0076_bugfix-vob-network-playback-routing.md`](../S0076_bugfix-vob-network-playback-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Intercept VOB route failures before the generic playback-error skip path and stop the cascade on the current file.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Timestamped backup of `VideoPlayerManager.kt` created in `temp/` before the first code edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/VideoPlayerManager_*.kt` | New | <= 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | <= 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` | Modified | <= 120 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step 03.1 - Create a backup and extend the player callback contract

**Files:** `temp/VideoPlayerManager_*.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Before editing the giant player file, create a timestamped backup in `temp/`. Extend `VideoPlayerManager.PlayerCallback` with a dedicated network-container route error callback that can carry the current path and container hint without falling through to the generic error flow.

**Verification:**

- `Glob` - `temp/VideoPlayerManager_*.kt` exists.
- `Grep` - `fun onNetworkContainerRouteError` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`.
- `Grep` - `NetworkPlaybackContainerHint` present in that callback signature or surrounding branch.

**Status:** `[ ]` not done

---

### Step 03.2 - Branch VOB route errors before generic skip handling

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the ExoPlayer listener, keep the existing `.m2ts/.m2t` informative dialog path, add a dedicated `.vob` / `DVD_PS_VOB` route-error branch, and let all other failures continue to `onPlaybackError(error)`. Do not change the generic navigation flow in this step.

**Verification:**

- `Grep` - `DVD_PS_VOB` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`.
- `Grep` - `playerCallback.onNetworkContainerRouteError` present in that file.
- `Grep` - `playerCallback.onPlaybackError(error)` still present in that file.

**Status:** `[ ]` not done

---

### Step 03.3 - Handle route errors without delegating to auto-next

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Implement the new callback in `PlayerPlaybackCallbackImpl` so VOB route errors show a dedicated blocking dialog path and return without delegating to `activity.handleMediaLoadErrorAndSkip()`. Keep `onPlaybackError()` unchanged for generic failures.

**Verification:**

- `Grep` - `override fun onNetworkContainerRouteError` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`.
- `Grep` - `AlertDialog.Builder(activity)` present in that file.
- `Grep` - `handleMediaLoadErrorAndSkip()` still present in `onPlaybackError` path.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] VOB route errors are intercepted before the generic `handleMediaLoadErrorAndSkip()` path.

---

## Handoff Notes to Next Phase

The player now distinguishes route errors from generic playback errors; localized user-facing text and regression tests can be added without reopening protocol helpers.

---

## Rollback Plan

Revert phase commit(s) and restore the `temp/` backup if the callback branching causes an unexpected player-state regression.# Phase 03 - Error Containment

**Strategic spec:** [`../S0076_bugfix-vob-network-playback-routing.md`](../S0076_bugfix-vob-network-playback-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Surface VOB route failures through a dedicated callback path and stop the current-file cascade before the generic auto-next error handler runs.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Timestamped backup of `VideoPlayerManager.kt` created in `temp/` before the first edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/VideoPlayerManager_<timestamp>.kt` | New | one full backup |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | <= 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` | Modified | <= 120 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step 03.1 - Backup the giant player file and extend the callback contract

**Files:** `temp/VideoPlayerManager_<timestamp>.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a timestamped backup of `VideoPlayerManager.kt` in `temp/` before editing. Then extend `VideoPlayerManager.PlayerCallback` with one dedicated callback for network container route failures so VOB-specific route errors can bypass the generic `onPlaybackError()` skip path.

**Verification:**

- `Glob` - `temp/VideoPlayerManager*.kt` exists.
- `Grep` - `fun onNetworkContainerRouteError` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.2 - Branch route failures before the generic playback-error path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the ExoPlayer listener, keep the existing `.m2ts/.m2t` informative dialog path, add a dedicated `.vob` / `DVD_PS_VOB` route-error branch, and preserve the generic `playerCallback.onPlaybackError(error)` path for unrelated failures. The VOB branch must not fall through to the generic auto-skip handler.

**Verification:**

- `Grep` - `DVD_PS_VOB` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`.
- `Grep` - `playerCallback.onNetworkContainerRouteError` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`.
- `Grep` - `playerCallback.onPlaybackError(error)` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 - Handle the new route callback without auto-next

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Implement the new callback in `PlayerPlaybackCallbackImpl` so route errors stay on the current file and show a dedicated dialog path. Keep `onPlaybackError()` unchanged for generic failures; the new override must not delegate to `activity.handleMediaLoadErrorAndSkip()`.

**Verification:**

- `Grep` - `override fun onNetworkContainerRouteError` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`.
- `Grep` - `AlertDialog.Builder(activity)` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`.
- `Grep` - `activity.handleMediaLoadErrorAndSkip()` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

VOB route failures now stop before the generic skip handler. The next phase must supply localized strings and automated regression coverage for this new branch.

---

## Rollback Plan

Revert phase commit(s) and restore the `temp/` backup if the callback contract or listener branch regresses unrelated playback errors.