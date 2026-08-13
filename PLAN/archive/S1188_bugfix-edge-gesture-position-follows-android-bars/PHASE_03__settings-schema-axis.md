# Phase 03 - Settings schema axis

**Strategic spec:** [`../S1188_bugfix-edge-gesture-position-follows-android-bars.md`](../S1188_bugfix-edge-gesture-position-follows-android-bars.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Turn the settings diagram 90° when the system bars sit on the side edges, so `EdgeGestureSchemaView` shows the band layout the overlay actually renders; zone identities and tap targets stay as they are.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureSchemaView.kt` | Modified | ≤ 340 |

> No layout XML changes: the transposed outline is derived from the view's existing measured box, which already accommodates it at the current `280x200dp` default. `res/layout/dialog_edge_gesture_config.xml` and its `res/layout-land` counterpart are therefore untouched, so landscape parity is preserved by not editing either.
>
> No new or changed strings: `edge_gesture_schema_content_description` and the four tab labels keep their wording, per the owner's A+ decision that zone identities do not change.

---

## Steps

### Step 03.1 - Resolve the axis from the view's own window insets

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureSchemaView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `axis` field defaulting to `EdgeGestureAxis.VERTICAL` and a private `resolveAxis()` that reads `ViewCompat.getRootWindowInsets(this)`, takes `getInsetsIgnoringVisibility(systemBars() or displayCutout())` and feeds `left`/`right` to `EdgeGestureAxis.forInsets`; return `VERTICAL` when the insets are unavailable. Call `resolveAxis()` at the top of `computeGeometry`. Register a `ViewCompat.setOnApplyWindowInsetsListener` in `init` that recomputes the geometry and invalidates when the view already has a size, returning the insets unmodified - a bar moving to the side edges without changing the dialog's size would otherwise leave the diagram stale.

**Verification:**

- `Grep` - `private fun resolveAxis()` present.
- `Grep` - `EdgeGestureAxis.forInsets(` present.
- `Grep` - `setOnApplyWindowInsetsListener` present.

**Status:** `[x]` done

---

### Step 03.2 - Transpose the phone outline

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureSchemaView.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Compute the outline's short and long side once from `PHONE_WIDTH_FRACTION` and `PHONE_VERTICAL_INSET` as today, then assign them to width/height straight under `VERTICAL` and swapped under `HORIZONTAL`, centring `phoneRect` in the view both ways and coercing each side to the view's own extent. The `VERTICAL` result must stay pixel-identical to the current rendering.

**Verification:**

- `Grep` - `phoneRect.set(` matches exactly once.
- `Grep` - `PHONE_WIDTH_FRACTION` still present.

**Status:** `[x]` done

---

### Step 03.3 - Lay out bands and direction cells on the active edge pair

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureSchemaView.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Branch `layoutZone` on the axis. Under `VERTICAL` keep today's geometry. Under `HORIZONTAL` transpose it: the band runs along the outline's width starting at `BAND_TOP_START`/`BAND_BOTTOM_START` of that width with length `BAND_HEIGHT_FRACTION`, its thickness `bandW` sits on the outline's top edge for `isRightEdge == false` and on the bottom edge for `true`, and the three direction cells step along the band's length with their centre offset by `CELL_INSET_DP` toward the inside of the outline. Leave `bandRects`/`directionRects` and `handleTap` untouched - hit testing stays rectangle-based and keeps working in both axes.

**Verification:**

- `Grep` - `EdgeGestureAxis.HORIZONTAL` present in the file.
- `Grep` - `private fun handleTap(x: Float, y: Float)` still present - hit testing untouched.
- `Grep` - `phoneRect.bottom - bandW` present (the bottom-edge band placement).

**Status:** `[x]` done

---

### Step 03.4 - Point the arrows along the active axis

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureSchemaView.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Replace the three hand-written chevron paths with one generic chevron built from a unit direction vector - tip on the vector, two arms swept back along its perpendicular - and a private `arrowVector(direction, zone)` that mutates a single reusable `PointF` field rather than allocating per arrow, since this runs inside `onDraw`. Under `VERTICAL` return `UP = (0,-1)`, `DOWN = (0,1)`, `RIGHT = (inward,0)`; under `HORIZONTAL` return `UP = (-1,0)`, `DOWN = (1,0)`, `RIGHT = (0,inward)`, where `inward` is `-1` for `isRightEdge` and `+1` otherwise. This must mirror the Phase 02 classifier exactly: `UP` is lateral-negative, `DOWN` lateral-positive, `RIGHT` straight inward. Confirm the `VERTICAL` chevrons still render the same three shapes as the replaced code.

**Verification:**

- `Grep` - `private fun arrowVector(` present.
- `Grep` - `arrowPath.moveTo(cx - half, cy + half)` returns zero hits (hand-written per-direction paths gone).
- `Grep` - `PointF()` matches exactly once - the single reusable instance, so `onDraw` allocates nothing.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep -n "Log\.d\("` returns zero hits in the touched file.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] No hardcoded `="#hex"` introduced - colours stay on `R.color.error_red` and `colorOutline`.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Diagram and overlay now derive their layout from the same `EdgeGestureAxis` rule. Phase 04 records the capability and regenerates the catalog for the type added in Phase 01.

---

## Rollback Plan

Revert the phase commit - the view is stateless between draws and no setting or persisted binding is touched.
