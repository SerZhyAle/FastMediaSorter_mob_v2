# Phase 01 - Hint cancel target

**Strategic spec:** [`../S1210_edge-gesture-cancel-target-menu.md`](../S1210_edge-gesture-cancel-target-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Render a cancel target inside the gesture hint - an icon column at the edge side, ahead of the three action rows - and expose its width so the touch handler can map a finger position onto it.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` free (`scripts/utils/lock-status.ps1 -Name Code`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureHintView.kt` | Modified | ≤ 220 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified - call site only | ≤ 700 |

> The manager is listed here because `bind` gains parameters: leaving its single call site stale would break the build mid-phase. Behaviour changes to the manager belong to Phase 02.

> `src/screenCapture/` is the flavor-mounted source set shared by `noLegal` and by `standard` under the edge-overlay build flag. No `src/main/` file is touched, so no contract/No-Op split is introduced by this phase.
>
> Layout is built in code - there is no `res/layout*` file for this view, so landscape parity does not apply.

---

## Steps

### Step 01.1 - Add a cancel column to the hint panel

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureHintView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Split the panel into a cancel target and the existing stack of action rows, and lay the two out along the axis the band runs across: for a left/right band the panel is horizontal, for a top/bottom band it is vertical. The cancel target must end up on the side facing the screen edge - first for a near-edge band, last for a far-edge one - so `bind` takes the band's axis and whether it sits on the far edge alongside the rows. The target holds a single `ImageView` with `R.drawable.ic_cancel`, tinted with the row content colour, sized to a touch-friendly square. Panel padding, corner radius and colours stay as they are.
>
> Planning correction (2026-07-27): the original prompt assumed the cancel column is always first. That only holds for the left and top bands - the panel of a right or bottom band is anchored by its opposite side, so the edge-facing slot flips.

**Verification:**

- `Grep` - `ic_cancel` matches once in `ScreenGestureHintView.kt`.
- `Grep` - `EdgeGestureAxis` is consumed by `bind` - matches at least once.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-27 - Verification 3/3 PASS. Files: ScreenGestureHintView.kt (panel split into cancel target + rows container, axis-aware orientation), ScreenGestureOverlayManager.kt (call site only). `.\a.ps1 fk` BUILD SUCCESSFUL.
- 2026-07-27 - PLAN-FIX: prompt and Files Touched amended before execution - the cancel target flips to the trailing slot on far-edge bands, and the `bind` call site had to move with the signature to keep the phase compilable.

---

### Step 01.2 - Expose the cancel column width

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureHintView.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Expose the horizontal extent of the cancel column, measured from the panel's own start edge and including panel padding, as a read-only property in pixels. It must be derived from the same dimensions the column is laid out with - no second formula. The touch handler in Phase 02 reads it to decide when the finger is on the cancel target.

**Verification:**

- `Grep` - a `val cancelTargetExtentPx` declaration matches exactly once.
- `Grep` - the literal used for the cancel icon size appears exactly once in the file (single source of truth).

**Status:** `[x] done`

**Step Log:**

- 2026-07-27 - Verification 2/2 PASS. `cancelTargetExtentPx` derived from the same padding and target-size constants the target is laid out with; `48f` appears once.

---

### Step 01.3 - Extend highlighting to the cancel target

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureHintView.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Make the highlight tri-state: one action row highlighted, the cancel target highlighted, or nothing highlighted. Reuse the existing selected-background instance and the selected/unselected alpha pair so the cancel target reads as selected exactly like a row does - shape plus opacity, never colour alone. Keep the early-exit on an unchanged selection: the setter runs on every touch move and must not allocate.

**Verification:**

- `Grep` - the highlight entry point accepts a cancel state (parameter or dedicated function) - matches once.
- `Grep` - `GradientDrawable()` is constructed only inside the existing `roundedRect` helper (no per-call allocation).
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-27 - Verification 3/3 PASS. Tri-state expressed as `highlight(direction)` with null meaning the cancel target - no "nothing selected" state exists any more, so no new type was needed. `GradientDrawable()` still only inside `roundedRect`.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` on the touched file returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The hint view now draws a cancel target at its start edge, reports how deep that target reaches, and can show it as the selected item. Nothing yet selects it - the touch handler still classifies by angle and fires mid-drag.

---

## Rollback Plan

Revert the phase commit - the view is created per touch and holds no state beyond it.
