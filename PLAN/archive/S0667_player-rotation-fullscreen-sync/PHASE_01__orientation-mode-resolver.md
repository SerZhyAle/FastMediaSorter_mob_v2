# Phase 01 - Orientation-to-mode resolver

**Strategic spec:** [`../S0667_player-rotation-fullscreen-sync.md`](../S0667_player-rotation-fullscreen-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Introduce the single shared decision point that maps device orientation to a player display mode, plus a "rotation follows device" query on the existing rotation manager. No host wiring yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerOrientationModeManager.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ScreenRotationManager.kt` | Modified | ≤ 110 |

---

## Steps

### Step 01.1 - Create the orientation-to-mode resolver

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerOrientationModeManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `PlayerOrientationModeManager` as the single reusable decision point for both player hosts (strategic ADR-1). Declare a public enum `PlayerDisplayMode { FULLSCREEN, COMMAND_PANEL }`. Add a pure function `fun resolve(isLandscape: Boolean, followsDevice: Boolean, isVisualMedia: Boolean): PlayerDisplayMode?` that returns `null` when `followsDevice` is false (orientation locked/forced - §6.1) or `isVisualMedia` is false (audio/text/document - out of scope), otherwise `FULLSCREEN` when `isLandscape` else `COMMAND_PANEL` (rotation always dictates the mode - §6.2). No Android framework imports, no state, no logging - keep it a pure resolver so both hosts share identical behaviour.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerOrientationModeManager.kt` exists.
- `Grep` - `class PlayerOrientationModeManager` matches exactly once.
- `Grep` - `enum class PlayerDisplayMode` present with `FULLSCREEN` and `COMMAND_PANEL`.
- `Grep` - `fun resolve(` present.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 5/5 PASS. Files: PlayerOrientationModeManager.kt (New, +38 LOC).

---

### Step 01.2 - Expose "rotation follows device" on ScreenRotationManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ScreenRotationManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun followsDevice(): Boolean = currentFollowSystem || currentSensorEnabled` to `ScreenRotationManager`. This reports whether the player currently rotates with the device (OS auto-rotate via `followSystem`, or app sensor via `sensorEnabled`) versus a locked/forced orientation. Reuse the existing cached `currentFollowSystem` / `currentSensorEnabled` fields; do not add new state. Keep a short KDoc explaining it gates the S0667 auto mode-switch.

**Verification:**

- `Grep` - `fun followsDevice()` present in `ScreenRotationManager.kt`.
- `Grep` - `currentFollowSystem || currentSensorEnabled` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Files: ScreenRotationManager.kt (+9 LOC).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new class).

---

## Handoff Notes to Next Phase

`PlayerOrientationModeManager.resolve(..)` is the only place that decides the mode; both host phases must call it rather than re-deriving orientation logic. `ScreenRotationManager.followsDevice()` supplies the gate input for the stream host; the standalone host has no app-level orientation lock and passes `true`.

---

## Rollback Plan

Revert phase commit(s) - new class is unreferenced until Phase 02/03; no data migration or user-facing surface changed.
