# Phase 03 - Direction highlight

**Strategic spec:** [`../S1162_edge-gesture-direction-preview.md`](../S1162_edge-gesture-direction-preview.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Make the hint show which of the three directions will fire, using the same classifier that decides
the outcome.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureHintView.kt` | Modified | ≤ 200 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified | ≤ 480 |

---

## Steps

### Step 03.1 - Implement the highlight

**Files:** `ScreenGestureHintView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Implement `highlight(direction: ScreenshotGestureDirection?)`: the matching row becomes prominent,
> the other two recede, and `null` means no row is selected (the state before the finger has moved).
>
> Distinguish the selected row by more than colour - strategic §3.2 requires this. Change the row's
> background shape and keep the label fully opaque while the unselected labels dim, so the selection
> survives a colourblind user and a washed-out panel alike.
>
> Make the call cheap and idempotent: it runs on every MOVE event. Bail out when the requested
> direction equals the current one, and never allocate a drawable per call - build the two states once
> in the constructor.

**Verification:**

- `Grep` - `fun highlight` no longer contains a `TODO`.
- `Grep` - an early-return guard compares against the stored current direction.

**Status:** `[x]` done

---

### Step 03.2 - Drive the highlight from the touch handler

**Files:** `ScreenGestureOverlayManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the `ACTION_MOVE` branch, after computing `inwardDx` and `dy`, feed the current angle through the
> **existing** `directionForAngle` and pass its result to `highlight`. Do not add a second classifier
> or a second set of angle constants - strategic §5.3 requires the hint and the outcome to read the
> same rule, and two copies would drift the first time the windows are tuned on device.
>
> Call `highlight` before the travel-distance gate, so the preview tracks the finger from the first
> move rather than only after the gesture is already long enough to fire. An angle that falls in no
> window highlights nothing - that is honest: at that angle nothing would fire either.

**Verification:**

- `Grep` - `directionForAngle` has exactly one call site in the manager: its result feeds `highlight` and, past the distance gate, the fire path. One call rather than two makes divergence structurally impossible instead of merely discouraged.
- `Grep` - the `highlight` call sits above the `GESTURE_DISTANCE_PX` check.
- `Grep` - no angle-window constant appears outside the existing companion block.
- `.\a.ps1 fk` + `.\a.ps1 fkn` - exit 0 (both overlay hosts).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Feature complete. What is left is the mechanical closure and the on-device confirmation, which is the
only way to judge the visual weight of an overlay drawn over other apps.

---

## Rollback Plan

Make `highlight` a no-op again; the hint degrades to a static three-row legend.
