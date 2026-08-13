# Phase 02 - Overlay axis aware

**Strategic spec:** [`../S1188_bugfix-edge-gesture-position-follows-android-bars.md`](../S1188_bugfix-edge-gesture-position-follows-android-bars.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 6 / 6
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Make `ScreenGestureOverlayManager` place, paint, hint and classify against the axis `EdgeGestureAxis.forInsets` reports, so the live bands sit on whichever edge pair the system bars leave free.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified | ≤ 640 |

> File is 527 LOC before this phase - over the 500-line threshold, so Step 02.1 takes a timestamped backup first (CLAUDE.md Rule 5).
>
> `src/screenCapture` is mounted by `standard` (when `fms.screenCapture` is on, the default) and by `noLegal`. No `src/main` flavor guard is introduced.

---

## Steps

### Step 02.1 - Back up the overlay manager

**Files:** `temp/S1188/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` to `temp/S1188/ScreenGestureOverlayManager.<yyyyMMdd-HHmmss>.kt.bak` before any edit. The file is over 500 LOC.

**Verification:**

- `Glob` - at least one file matches `temp/S1188/ScreenGestureOverlayManager.*.kt.bak`.

**Status:** `[x]` done

---

### Step 02.2 - Carry both screen dimensions and the axis in Geometry

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the `Geometry` fields `safeHeight` with `safeBottom` and add `screenHeight` plus `axis: EdgeGestureAxis`, exposing `safeHeight` and `safeWidth` as derived `get()` properties coerced to at least 1. Fill `axis` in `computeGeometry()` from `EdgeGestureAxis.forInsets(insets.left, insets.right)` on API 30+, and from `EdgeGestureAxis.VERTICAL` in the pre-R fallback where no per-edge inset data is available. Add a `bandAxis` field on the manager, assigned wherever `stripWidthPx` is assigned (`addBands`, `relayout`), so the touch handler and `setStripVisible` can read the axis without a `Geometry` in hand.

**Verification:**

- `Grep` - `val axis: EdgeGestureAxis` present in the `Geometry` declaration.
- `Grep` - `EdgeGestureAxis.forInsets(` matches exactly once in the file.
- `Grep` - `private var bandAxis` present.
- `Grep` - `val safeWidth: Int` and `val safeHeight: Int` both present.

**Status:** `[x]` done

---

### Step 02.3 - Place bands on the free edge pair

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Branch `bandFrame` on `geom.axis`. Under `VERTICAL` keep today's result exactly: `x` on the safe left edge or `screenWidth - safeRight - stripWidth`, `y = safeTop + safeHeight * startFraction`, `width = stripWidth`, `height = safeHeight * BAND_HEIGHT`. Under `HORIZONTAL` transpose it: `y` on the safe top edge or `screenHeight - safeBottom - stripWidth`, `x = safeLeft + safeWidth * startFraction`, `width = safeWidth * BAND_HEIGHT`, `height = stripWidth`. Replace the bare `* 4` minimum-length factor with a named companion constant so the touched line carries no magic number.

**Verification:**

- `Grep` - `when (geom.axis)` present inside `bandFrame`.
- `Grep` - `EdgeGestureAxis.HORIZONTAL ->` present in the file.
- `Grep` - `geom.stripWidth * 4` returns zero hits.
- `Grep` - a companion `const val MIN_BAND_LENGTH_STRIPS` is declared.

**Status:** `[x]` done

---

### Step 02.4 - Draw the edge guide along the band's own edge

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Give `EdgeGuideDrawable` an `axis` parameter and branch `draw`: under `VERTICAL` keep the existing vertical sliver aligned left or right, under `HORIZONTAL` draw a horizontal sliver of the same thickness aligned to the top or bottom of the bounds. `alignEnd` keeps meaning "the far edge of the active pair", so it stays `zone.isRightEdge`. In `applyBandBackground`, take the fallback thickness from `view.height` instead of `view.width` when the axis is `HORIZONTAL`.

**Verification:**

- `Grep` - `private val axis: EdgeGestureAxis` present in the `EdgeGuideDrawable` constructor.
- `Grep` - `alignEnd = zone.isRightEdge` still present in `applyBandBackground`.

**Status:** `[x]` done

---

### Step 02.5 - Anchor the gesture hint to the band's inner side

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Make `hintX`/`hintY` axis-aware and keep both clamped inside the safe bounds (CLAUDE.md Rule 17). Under `VERTICAL` keep today's behaviour: `hintX` sits on the band's inner side, `hintY` centres on the band. Under `HORIZONTAL` swap the roles - `hintX` centres on the band, `hintY` sits on the band's inner side (below a top band, above a bottom band). `hintY` needs the zone to pick that side, so add the parameter and update the call site. Express the vertical clamp ceiling as `screenHeight - safeBottom - height` now that `safeBottom` exists.

**Verification:**

- `Grep` - `private fun hintY(zone: ScreenshotGestureZone` present.
- `Grep` - `geom.screenHeight - geom.safeBottom` present.

**Status:** `[x]` done

---

### Step 02.6 - Decompose the drag against the band's own edge

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.5

**Prompt for developer:**

> Extract a private `dragComponents(zone, dx, dy)` returning an `inward` / `lateral` pair: under `VERTICAL` `inward = if (zone.isRightEdge) -dx else dx` and `lateral = dy`; under `HORIZONTAL` `inward = if (zone.isRightEdge) -dy else dy` and `lateral = dx`. Read the axis from the `bandAxis` field. In `ACTION_MOVE` replace `inwardDx`/`dy` with those two values in the cancel test, the `atan2` call and the `hypot` distance test, leaving `directionForAngle` and its angle windows untouched - the classifier keeps working in band-local coordinates, so `UP` stays "lateral negative" and `DOWN` "lateral positive" in both axes and no stored binding changes meaning. Keep the extraction small enough that `handleTouch` does not grow past its current length.

**Verification:**

- `Grep` - `private fun dragComponents(` present.
- `Grep` - `atan2(components.lateral, components.inward)` present.
- `Grep` - `val inwardDx` returns zero hits.
- `Grep` - `private fun directionForAngle` still present and unchanged in signature.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`standard`, plus `noLegal` because `src/screenCapture` is mounted by both).
- [ ] `Grep -n "Log\.d\("` returns zero hits in the touched file.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The overlay now renders and classifies per `EdgeGestureAxis`. Phase 03 must reproduce exactly this mapping in the settings diagram - same far-edge rule, same along-edge fractions, same lateral-to-direction assignment - or the diagram will promise a layout the overlay does not render.

---

## Rollback Plan

Restore `temp/S1188/ScreenGestureOverlayManager.*.kt.bak` - no persisted format or user setting is touched by this phase.
