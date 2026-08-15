# Phase 01 - Stream detection

**Strategic spec:** [`../S0631_video-stream-player-view.md`](../S0631_video-stream-player-view.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Introduce a single computed `isLiveVideoStream` signal on `PlayerViewModel.PlayerState` so every
control-visibility and share consumer can branch on "this is a live video stream" without re-deriving it.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 920 |

> `PlayerViewModel.kt` is 909 LOC (> 500): create a timestamped backup in `temp/` before editing (Constraints).

---

## Steps

### Step 01.1 - Add `isLiveVideoStream` computed property to `PlayerState`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `PlayerViewModel.kt` to `temp/` first (file > 500 LOC). In the `PlayerState` body, beside the
> existing computed `resourceId`/`currentFile` getters, add a computed property:
> `val isLiveVideoStream: Boolean get() = resource?.id == SyntheticResourceIds.STREAM && currentFile?.type == MediaType.VIDEO`.
> Import `com.sza.fastmediasorter.domain.model.SyntheticResourceIds` if not already imported. No other
> behavior changes. WHY this gate: a stream is launched into the player with the synthetic resource id
> `SyntheticResourceIds.STREAM` (-200); the `MediaType.VIDEO` clause scopes the profile to video
> transляations and leaves audio (radio) streams on their existing control set.

**Verification:**

- `Grep` - `val isLiveVideoStream: Boolean` matches exactly once in `PlayerViewModel.kt`.
- `Grep` - `SyntheticResourceIds.STREAM` present in `PlayerViewModel.kt`.
- `Glob` - `temp/PlayerViewModel.kt.*.bak` (or similar) exists.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `PlayerViewModel.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`PlayerState.isLiveVideoStream` is the single source of truth for the stream profile. Phases 02 and 03
read `state.isLiveVideoStream` - never re-derive the stream check from the path.

---

## Rollback Plan

Revert phase commit - one additive computed property, no data migration or user-facing surface changed.
