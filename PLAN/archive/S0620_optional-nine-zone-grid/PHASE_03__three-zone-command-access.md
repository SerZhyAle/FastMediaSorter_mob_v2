# Phase 03 - Command-panel access in the 3-zone fallback

**Strategic spec:** [`../S0620_optional-nine-zone-grid.md`](../S0620_optional-nine-zone-grid.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

In the fullscreen 3-zone fallback (grid off), preserve touch access to file operations: a dedicated left edge area opens the command panel (`COMMAND_PANEL`), replacing the `BACK` slot the 9-zone layout used; the vertical swipe stays zoom (image) / seek (video) as today; keyboard and D-pad continue to reach the command panel. Strategic §6 left-area geometry is pinned here.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (fullscreen returns the 3-zone map when the grid is off).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt` | Modified | ≤ 750 |

---

## Steps

### Step 03.1 - Left-edge command-panel zone in the 3-zone fullscreen tap map

**Files:** `ui/player/TouchZoneConfig.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a fullscreen-fallback tap resolver for the 3-zone maps (REG_3100 / REG_375) that splits the existing left column: the leftmost **edge band = 8% of screen width** maps to `TouchZoneAction.COMMAND_PANEL`; the remainder of the previous "previous" left zone keeps `TouchZoneAction.PREVIOUS`; center keeps `PHOTOVIEW_GESTURE` (image) / `PAUSE_RESUME` (video); right keeps `NEXT`. Implement as a new helper (e.g. `get3ZoneFullscreenTapAction(xFraction: Float, zoneMap: TouchZoneMap)`) so the command-panel-mode 3-zone path (`get3ZoneImageTapAction` / `get3ZoneVideoTapAction`) is untouched. The 8% band is the pinned geometry from strategic §6 / Quiz 2026-06-23 - device-test confirms it does not steal too much of the "previous" target.

**Verification:**

- `Grep` - `get3ZoneFullscreenTapAction` declared once in `TouchZoneConfig.kt`.
- `Grep` - `TouchZoneAction.COMMAND_PANEL` present in that helper.

**Status:** `[ ]` not done

---

### Step 03.2 - Route fullscreen 3-zone taps through the new helper

**Files:** `ui/player/helpers/TouchZoneGestureManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the tap-dispatch path, when the active map is a 3-zone map **and** the state is fullscreen (grid off), resolve the tap action via `get3ZoneFullscreenTapAction` (using the touch x as a fraction of width) instead of the plain column-based 3-zone resolver. The command-panel-mode 3-zone path keeps the column resolver. Vertical-swipe handling is unchanged (still zoom/seek per `getSwipeAction` for REG_3100 / REG_375) - do not reroute swipe to the panel.

**Verification:**

- `Grep` - `get3ZoneFullscreenTapAction` referenced in `TouchZoneGestureManager.kt`.
- `Grep` - no new `getSwipeAction` override that maps a swipe to `COMMAND_PANEL` for REG_3100/REG_375.

**Status:** `[ ]` not done

---

### Step 03.3 - Build gate + keyboard/D-pad confirmation

**Files:** (none - validation only)
**Depends on:** Steps 03.1-03.2

**Prompt for developer:**

> Run `/build` -> `standard debug`. Confirm by Grep that the existing keyboard/D-pad command-panel entry point (the key handler that toggles the command panel) is not gated on the 9-zone layout - it must keep working when the grid is off. If it is gated, remove that gate.

**Verification:**

- `/build` standard debug PASS.
- `Grep` - keyboard/D-pad command-panel toggle handler has no `nineZoneGridEnabled`/9-zone guard.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The grid-off player is now functionally complete (3-zone layout + left-edge command panel + unchanged zoom/seek swipe + keyboard/D-pad). Phase 04 adds the user-facing toggle and the settings-block visibility/explanation. The left-edge band (8%) is a device-test item - record it on the BlockNeedUserTest note.

---

## Rollback Plan

Revert phase commit(s) - the new helper is only reached in the grid-off fullscreen state; reverting leaves Phase 02's 3-zone fallback without panel access (still no data impact).
