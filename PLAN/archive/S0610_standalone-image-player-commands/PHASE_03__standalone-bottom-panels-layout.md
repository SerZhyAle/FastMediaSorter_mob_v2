# Phase 03 - Add Copy/Move bottom panels to the standalone image layout

**Strategic spec:** [`../S0610_standalone-image-player-commands.md`](../S0610_standalone-image-player-commands.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - layout reuses the pre-existing shared include
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Include the shared `player_bottom_panels_container_content` (Copy/Move grids + collapsible headers) at the bottom of the
standalone image layout, in both portrait and landscape variants, so Phase 04 can populate it.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_standalone_photo_video.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/activity_standalone_photo_video.xml` | Modified | n/a |

> Landscape variant exists - both must be edited (CLAUDE.md Rule 11). Reuse the existing shared include `@layout/player_bottom_panels_container_content` (already used by `activity_player_unified.xml` line 334) - do not author new copy/move view ids.

---

## Steps

### Step 03.1 - Include the shared bottom panels in portrait

**Files:** `app_v2/src/main/res/layout/activity_standalone_photo_video.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `<include layout="@layout/player_bottom_panels_container_content" />` to the bottom of the standalone image layout so the `bottomPanelsContainer` (with `copyToPanel` / `moveToPanel` / grids / headers) sits below the media content area and stays inside the system-bar safe area. Anchor it at the bottom; the media content area must not overlap it. Do not hardcode colours - the shared include already uses theme attributes. Ensure the included panels are focusable/keyboard-reachable as authored.

**Verification:**

- `Grep` - `player_bottom_panels_container_content` present in `res/layout/activity_standalone_photo_video.xml`.
- Build compiles - run `/build` (ViewBinding regenerates with `bottomPanelsContainer`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification PASS (include present; resources BUILD SUCCESSFUL). Files: layout/activity_standalone_photo_video.xml.

---

### Step 03.2 - Include the shared bottom panels in landscape

**Files:** `app_v2/src/main/res/layout-land/activity_standalone_photo_video.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Mirror Step 03.1 in the landscape variant: add the same `<include layout="@layout/player_bottom_panels_container_content" />`, positioned so the grids stay inside `systemBars` + `displayCutout` safe bounds in landscape (the host already pads `topCommandPanel` / `mediaContentArea` for insets). Keep the portrait and landscape view ids identical so the binding field is non-null in both orientations.

**Verification:**

- `Grep` - `player_bottom_panels_container_content` present in `res/layout-land/activity_standalone_photo_video.xml`.
- Build compiles - run `/build`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification PASS (include present in landscape; resources BUILD SUCCESSFUL). Files: layout-land/activity_standalone_photo_video.xml.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Both portrait and landscape variants include the shared panels (parity).
- [ ] Dev log entry added for both layout files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The standalone image layout now exposes `bottomPanelsContainer` + copy/move grids by id in both orientations. Phase 04
wires the manager and operations to them.

---

## Rollback Plan

Revert the phase commit - remove the two includes; no persistent surface affected.
