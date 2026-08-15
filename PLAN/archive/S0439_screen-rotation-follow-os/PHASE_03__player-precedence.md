# Phase 03 - Player precedence

**Strategic spec:** [`../S0439_screen-rotation-follow-os.md`](../S0439_screen-rotation-follow-os.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Make the player follow OS when either the program flag or the player flag is on, by feeding `programFollowSystemRotation || playerFollowSystemRotation` into the existing `ScreenRotationManager`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivityLifecycleBridge.kt` | Modified | ≤ 500 |

---

## Steps

### Step 03.1 - Carry the player flag into PlayerState

**Files:** `ui/player/PlayerViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `PlayerState`, add `val playerFollowSystemRotation: Boolean = false` next to the existing `programFollowSystemRotation`, populated from the same settings subscription. Add a computed `val effectiveFollowSystemRotation: Boolean get() = programFollowSystemRotation || playerFollowSystemRotation`.

**Verification:**

- `Grep` - `playerFollowSystemRotation` present in `PlayerViewModel.kt`.
- `Grep` - `effectiveFollowSystemRotation` defined as `programFollowSystemRotation || playerFollowSystemRotation`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS. PlayerState gains playerFollowSystemRotation + computed effectiveFollowSystemRotation (program || player); populated from settings; showRotationToggle + toggleRotationSensor guard now keyed on effective (in-player sensor button shows only when the player is not following the OS). The two RotationSensorToggled apply() sites (PlayerActivity/PhotoVideoStandaloneActivity) intentionally keep hardcoded followSystem=false - sensor-toggle path runs only when effective is false.

---

### Step 03.2 - Feed the effective flag into both apply() call sites

**Files:** `ui/player/PlayerObserverManager.kt`, `ui/player/PlayerActivityLifecycleBridge.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `PlayerObserverManager`, change the `screenRotationManager.apply(..)` first boolean argument from `settings.programFollowSystemRotation` to `settings.programFollowSystemRotation || settings.playerFollowSystemRotation`. In `PlayerActivityLifecycleBridge.onResumeWithViews()`, pass `rs.effectiveFollowSystemRotation` to `apply(..)`. Do not change `ScreenRotationManager` itself - its `followSystem=true → UNSPECIFIED; else sensor → SENSOR/LOCKED` mapping is correct as-is.

**Verification:**

- `Grep` - `programFollowSystemRotation || ` `playerFollowSystemRotation` present in `PlayerObserverManager.kt`.
- `Grep` - `effectiveFollowSystemRotation` passed to `apply(` in `PlayerActivityLifecycleBridge.kt`.
- `/build` - standard debug compiles, exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS. PlayerObserverManager apply() now passes program||player; PlayerActivityLifecycleBridge passes rs.effectiveFollowSystemRotation. `a.ps1 fk` PASS (after kapt-stall recovery cleared stale incremental state - transient collectOnLifecycle false error, not a code defect).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Player orientation precedence is live: program on OR player on → player follows OS; both off → player uses its own sensor/locked control (`playerRotationSensorEnabled`).
- The player flag has no UI yet - Phase 04 adds the toggle and its visibility coupling.

---

## Rollback Plan

Revert phase commit(s). The player reverts to reacting to the program flag only; no data surface touched.
