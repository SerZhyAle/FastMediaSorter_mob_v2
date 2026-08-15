# Phase 02 - Hint window

**Strategic spec:** [`../S1162_edge-gesture-direction-preview.md`](../S1162_edge-gesture-direction-preview.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Show three rows - icon plus label - next to the touched band, from touch-down until the touch ends.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureHintView.kt` | New | ≤ 200 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified | ≤ 480 |

---

## Steps

### Step 02.1 - Build the hint view

**Files:** `ScreenGestureHintView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ScreenGestureHintView(context: Context) : LinearLayout` - vertical, three rows in the order
> UP, RIGHT, DOWN (top, straight ahead, bottom), each an icon `ImageView` plus a label `TextView`.
> Build the views in code, not from XML: the hosts are Services without an app theme, and a Material
> widget inflated from an unthemed context crashes at construction.
>
> Expose `fun bind(rows: List<HintRow>)` where a row carries a `ScreenshotGestureDirection`, a
> `@DrawableRes` icon and a `CharSequence` label, and `fun highlight(direction: ScreenshotGestureDirection?)`
> - a stub in this phase, implemented in Phase 03.
>
> Pull icon and label from `ScreenshotGestureActionCatalog.metaFor(action)` (`iconRes` / `labelRes`) -
> S1166 put them there precisely so a second list of icons never appears. Always render three rows,
> including a slot whose action is "do nothing": per ADR-3 that is an action with its own label and
> icon, not an absence, and a variable row count would make the hint reshape between zones.
>
> Give the view a rounded translucent dark background and enough contrast for a light app underneath.
> Do not hardcode a hex colour in a layout resource - there is no layout resource here; define the
> colours as constants in this class with a comment on why an overlay cannot use `?attr/`.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class ScreenGestureHintView` matches exactly once.
- `Grep` - `ScreenshotGestureActionCatalog` is referenced.
- `Grep` - no `inflate(` call in the file.

**Status:** `[x]` done

---

### Step 02.2 - Add and remove the hint window around a touch

**Files:** `ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> On `ACTION_DOWN`, build a `ScreenGestureHintView`, bind it from the stored action map for that zone,
> and add it as its own window with the same `overlayWindowType` as the bands plus
> `FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCH_MODAL`, `FLAG_LAYOUT_IN_SCREEN` **and `FLAG_NOT_TOUCHABLE`**.
> The last one is load-bearing: the hint sits exactly where the finger is heading, and without it the
> hint would swallow the gesture it exists to explain.
>
> Remove the window on `ACTION_UP`, `ACTION_CANCEL`, when the drag turns outward (the existing
> `inwardDx <= 0f` branch), and immediately after `onGestureMatched` fires. Removal must be
> idempotent - all four paths can be reached in one gesture.
>
> Skip the hint entirely when the zone has no entry in the action map, rather than showing three empty
> rows.

**Verification:**

- `Grep` - `FLAG_NOT_TOUCHABLE` appears in the hint window's flags.
- `Grep` - the hint removal helper is called from the DOWN-failure, MOVE-outward, gesture-fired and UP/CANCEL paths.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Step 02.3 - Place the hint beside the band

**Files:** `ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Position the hint window against the touched band, inward (ADR-1): for a left-edge zone its left
> edge starts where the band ends; for a right-edge zone its right edge ends where the band starts.
> Centre it vertically on the band's frame rather than on the touch point - a hint that jumps with the
> finger is harder to read than one that stays put.
>
> Reuse `bandFrame(zone, computeGeometry())` for the anchor instead of re-deriving band coordinates.
> S1048 already split into two competing geometry formulas once; do not open that door again.
>
> Clamp the window so it stays inside the safe bounds used for the bands (CLAUDE.md Rule 17) - measure
> the hint first, then clamp both axes.

**Verification:**

- `Grep` - `bandFrame(` is called from the hint placement code.
- `Grep` - no second literal copy of `BAND_TOP_START` / `BAND_HEIGHT` arithmetic outside `bandFrame`.
- `.\a.ps1 fc` - exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new class).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Watch the window-leak edge: a hint window must not survive `hide()` or a screen-off teardown.

---

## Handoff Notes to Next Phase

The hint appears and disappears with the touch. No direction is highlighted yet - all three rows look
the same for the whole gesture.

---

## Rollback Plan

Delete the hint view class and the add/remove calls; the touch handler returns to its previous shape.
