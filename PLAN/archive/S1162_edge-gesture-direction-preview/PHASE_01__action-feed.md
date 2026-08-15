# Phase 01 - Action feed

**Strategic spec:** [`../S1162_edge-gesture-direction-preview.md`](../S1162_edge-gesture-direction-preview.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Get the twelve configured slot actions to the overlay manager before any touch happens. No visible
change.

---

## Prerequisites

- [x] On a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt` | Modified | ≤ 400 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified | ≤ 480 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | Modified | ≤ 200 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenshotAccessibilityService.kt` | Modified | ≤ 400 |

---

## Steps

### Step 01.1 - Batch action lookup on the dispatcher

**Files:** `ScreenshotGestureActionDispatcher.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `suspend fun actionsForZones(zones: Set<ScreenshotGestureZone>): Map<ScreenshotGestureZone, Map<ScreenshotGestureDirection, ScreenshotGestureAction>>`
> next to the existing `actionFor`. Read the settings snapshot **once** and resolve every
> zone × direction slot from it - calling `actionFor` twelve times would re-read settings twelve
> times for one overlay show.
>
> Reuse whatever per-slot resolution `actionFor` already performs rather than duplicating the slot-key
> logic; if that means extracting a small private helper taking an already-loaded settings object,
> extract it and have `actionFor` call it too, so the two paths cannot disagree about what a slot
> resolves to.

**Verification:**

- `Grep` - `fun actionsForZones` matches exactly once.
- `Grep` - `getSettings()` appears at most once inside `actionsForZones`.

**Status:** `[x]` done

---

### Step 01.2 - Accept the action map in the overlay manager

**Files:** `ScreenGestureOverlayManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend `show(stripVisibleZones, enabledZones)` with a third parameter carrying the resolved map,
> defaulted to empty so an absent feed degrades to "no hint" rather than to a crash. Store it in a
> field beside `requestedZones` - it must survive the S1167 screen-off/screen-on band teardown, which
> drops the windows but keeps the request.

**Verification:**

- `Grep` - `fun show(` in the manager takes a third parameter typed as the zone→direction→action map.
- `Grep` - the new field is reset in `hide()` alongside `requestedZones`.

**Status:** `[x]` done

---

### Step 01.3 - Feed the map from both hosts

**Files:** `OverlayHostService.kt`, `ScreenshotAccessibilityService.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Both hosts already resolve `enabledZones()` and `stripVisibleZones()` from the dispatcher inside a
> coroutine immediately before `show(..)`. Add one `actionsForZones(enabledZones)` call in the same
> block and pass the result through.
>
> Keep the two call sites textually parallel. They are the one place where the two overlay hosts can
> drift apart, and every past divergence in this subsystem started as a change applied to one of them.

**Verification:**

- `Grep` - `actionsForZones` matches exactly once in each host file.
- `.\a.ps1 fk` - exit 0, and `.\a.ps1 fkn` - exit 0 (noLegal host compiles too).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles on both standard and noLegal.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The manager can answer "what are the three actions for this zone" synchronously. Nothing renders yet.

---

## Rollback Plan

Drop the third `show` parameter and the dispatcher method - both hosts revert to their previous call.
