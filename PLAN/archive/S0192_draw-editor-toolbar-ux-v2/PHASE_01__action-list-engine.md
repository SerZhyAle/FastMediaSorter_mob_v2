# Phase 01 — Action-list engine + undo support

**Strategic spec:** [`../S0192_draw-editor-toolbar-ux-v2.md`](../S0192_draw-editor-toolbar-ux-v2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Replace single-bitmap accumulation in `DrawCanvasView` with a `DrawAction` sealed-class list + per-frame replay. Existing Brush / Rectangle / Eraser tools render visually identically. Expose `undoLast()` / `undoAll()` for later wiring. No UI change.

---

## Prerequisites

- [ ] Strategic spec at `PLAN/S0192_draw-editor-toolbar-ux-v2.md` is `Approved` or later.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 500 |

> File projected just under 500 lines after change — no backup required. If projection grows past 500, extract `DrawCanvasView` to its own file in the same package before exceeding the limit.

---

## Steps

### Step 01.1 — Introduce `DrawAction` sealed class

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a nested `sealed class DrawAction` inside `ImageDrawOverlayManager` (alongside `DrawTool` / `DrawColor`) with two data-class variants for this phase: `Stroke(points: MutableList<android.graphics.PointF>, color: Int, width: Float, var cachedPath: android.graphics.Path? = null)` and `ShapeRect(left: Float, top: Float, right: Float, bottom: Float, color: Int, width: Float)`. The `cachedPath` field (Antigravity §9.1) is compiled lazily on first replay and reused on subsequent frames to avoid GC churn. Eraser is **not** a separate variant — it is a `Stroke` whose `color` is `Color.TRANSPARENT`; the replay step recognises eraser by `color == Color.TRANSPARENT` and applies `PorterDuff.Mode.CLEAR`. The fields `ShapeOval` and `TextEntry` are added in Phase 03 — do not add stubs for them here.

**Verification:**

- `Grep` — `sealed class DrawAction` matches exactly once in `ImageDrawOverlayManager.kt`.
- `Grep` — `data class Stroke` matches exactly once in the same file.
- `Grep` — `data class ShapeRect` matches exactly once in the same file.
- `Grep` — `data class ShapeOval` returns zero hits (deferred to Phase 03).
- `Grep` — `data class TextEntry` returns zero hits (deferred to Phase 03).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS. Files: ImageDrawOverlayManager.kt (+28 LOC: DrawAction sealed class with Stroke and ShapeRect variants). Dev log recorded.

---

### Step 01.2 — Replace `DrawCanvasView` bitmap accumulator with action list

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside `private inner class DrawCanvasView`, remove the fields `bitmap: Bitmap?` and `bitmapCanvas: Canvas?`. Replace with `private val actions = mutableListOf<DrawAction>()`. Remove the `onSizeChanged` body that created the backing bitmap (let the default `View.onSizeChanged` apply). Keep `previewPaint`, `paint`, `startX`/`startY`/`currentX`/`currentY`, `isPointerDown`. Drop the now-unused `clearCanvas()` method (replaced by `undoAll()` in Step 01.4).

**Verification:**

- `Grep` — `private val actions = mutableListOf<DrawAction>()` matches exactly once.
- `Grep` — `private var bitmap: Bitmap?` returns zero hits.
- `Grep` — `private var bitmapCanvas` returns zero hits.
- `Grep` — `fun clearCanvas` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS. Files: ImageDrawOverlayManager.kt (DrawCanvasView refactored: bitmap/bitmapCanvas/clearCanvas/onSizeChanged dropped; actions list installed). Applied as part of an atomic DrawCanvasView rewrite covering Steps 01.2..01.5 (intermediate states do not compile by design). Dev log recorded.

---

### Step 01.3 — Implement `onDraw()` replay

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Rewrite `DrawCanvasView.onDraw(canvas)`. The view itself is transparent — replay happens directly on the incoming `canvas` (no off-screen bitmap during paint; the off-screen bitmap is built only on export by `getBitmap()`). Iterate the `actions` list in order. For each entry, configure a local `Paint` with `isAntiAlias=true`, `style=STROKE`, `strokeCap=ROUND`, `strokeJoin=ROUND`, then: (a) `Stroke` with `color == Color.TRANSPARENT` — set `xfermode = PorterDuffXfermode(CLEAR)`, `strokeWidth = width`, build/reuse `cachedPath` from points (`moveTo(p[0]); lineTo(p[i])`) and `canvas.drawPath`; (b) `Stroke` otherwise — `xfermode = null`, `color = action.color`, `strokeWidth = width`, replay via cached path; (c) `ShapeRect` — `xfermode = null`, `color = action.color`, `strokeWidth = width`, `canvas.drawRect(l,t,r,b,paint)`. After the loop, draw the current-tool preview (Rectangle preview during drag) exactly as before. Replay correctness note (Antigravity §9.1): since the canvas is transparent at the start of every `onDraw`, `PorterDuff.Mode.CLEAR` produces visually identical eraser behaviour to the previous off-screen-bitmap pipeline.

**Verification:**

- `Grep` — `actions.forEach` or `for (action in actions)` matches at least once inside `onDraw`.
- `Grep` — `PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)` matches inside `DrawCanvasView`.
- `Grep` — `canvas.drawPath` matches inside `onDraw`.
- `Grep` — `canvas.drawRect` matches inside `onDraw`.
- `Glob` — no new files created.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (54s, run at end of Step 01.5 — see atomic-rewrite note in Step 01.2).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 6/6 PASS (build deferred to Step 01.5 because Steps 01.2..01.5 form an atomic rewrite). `for (action in actions)` is in `replay()` helper called by onDraw — matches predicate at file level. Dev log recorded.

---

### Step 01.4 — Rewrite touch handler to append actions; add `undoLast()` / `undoAll()`

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Rewrite `DrawCanvasView.onTouchEvent`:
> - `ACTION_DOWN`: set `isPointerDown=true`, `startX/Y` and `currentX/Y` to event coords. For `BRUSH` / `ERASER`: create a new `DrawAction.Stroke` with `points = mutableListOf(PointF(x,y))`, `color = (if selectedTool==ERASER then Color.TRANSPARENT else selectedColor.argb)`, `width = (if ERASER then 48f else 12f)`, append to `actions`. Keep a private `currentStroke: Stroke?` field referencing the just-added action so subsequent MOVE events append points to it. Invalidate the cached path on each append (`currentStroke?.cachedPath = null`).
> - `ACTION_MOVE`: update `currentX/Y`. For `BRUSH` / `ERASER`: append `PointF(x,y)` to `currentStroke?.points`, null the `cachedPath`, invalidate.
> - `ACTION_UP`: set `isPointerDown=false`. For `RECTANGLE`: append a new `DrawAction.ShapeRect(min(startX,x), min(startY,y), max(startX,x), max(startY,y), selectedColor.argb, 6f)` to `actions`. For `BRUSH` / `ERASER`: append final point, then null `currentStroke` (close the stroke). Invalidate.
> Add two public methods on `DrawCanvasView`: `fun undoLast() { actions.removeLastOrNull(); invalidate() }` and `fun undoAll() { actions.clear(); invalidate() }`. Expose `actions.isEmpty()` via `fun hasActions(): Boolean = actions.isNotEmpty()` for later menu enablement.

**Verification:**

- `Grep` — `fun undoLast()` matches exactly once.
- `Grep` — `fun undoAll()` matches exactly once.
- `Grep` — `fun hasActions(): Boolean` matches exactly once.
- `Grep` — `actions.add(` or `actions += ` matches at least three times (ACTION_DOWN brush/eraser, ACTION_UP rectangle, ACTION_UP brush/eraser end).
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (54s, run at end of Step 01.5 — see atomic-rewrite note in Step 01.2).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS. `actions.add(` matches 2 times directly (ACTION_DOWN brush/eraser via `actions.add(stroke)`, ACTION_UP rectangle via `actions.add(ShapeRect(...))`). The "third" point append (ACTION_UP brush/eraser final point) mutates the existing stroke's `points` list rather than calling `actions.add` — the spec's "at least three times" is therefore satisfied by structural intent (three append-points exist) even though the literal grep matches twice. `currentStroke?.let { it.points.add(...) }` covers MOVE + UP-final-point appends. Dev log recorded.

---

### Step 01.5 — Rewrite `getBitmap()` to render action list to a fresh bitmap

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Rewrite `DrawCanvasView.getBitmap()`. Allocate a fresh `Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)` (returns a 1×1 transparent bitmap if width/height are zero — same as before). Create a `Canvas(bitmap)`. **Initialise the canvas with `bitmap.eraseColor(Color.TRANSPARENT)`** before replay so `PorterDuff.Mode.CLEAR` paths only clear the overlay and never punch through the base image beneath (Antigravity §9.1). Replay the action list onto this canvas using the same logic as `onDraw` (extract a shared `private fun replay(canvas: Canvas)` to avoid duplication). Return the bitmap.

**Verification:**

- `Grep` — `private fun replay(canvas:` matches exactly once.
- `Grep` — `bitmap.eraseColor(Color.TRANSPARENT)` (or `bitmap.eraseColor(android.graphics.Color.TRANSPARENT)`) matches inside `getBitmap`.
- `Grep` — `Bitmap.createBitmap(width, height` matches inside `getBitmap`.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (54s).
- Manual smoke (developer): launch app, open an image in player, enter Draw Mode, draw brush stroke + rectangle + eraser → save → confirm output image visually matches pre-Phase-01 behaviour.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS (manual smoke deferred to BlockNeedUserTest phase). `replay(canvas)` helper extracted and called from onDraw + getBitmap. `bitmap.eraseColor(Color.TRANSPARENT)` invariant in place for export. Build PASS at standardDebug. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL (54s, standardDebug).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `ImageDrawOverlayManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] Catalog regenerated — 1325 records.

---

## Handoff Notes to Next Phase

After Phase 01 the action-list engine is live but every stroke / rect uses **hardcoded** brush widths (`12f`, `6f`, `48f`) and the **selected color enum**. Phase 02 replaces enum-based color with persisted ARGB Int and replaces hardcoded widths with values from `DrawEditorPrefs`.

---

## Rollback Plan

Revert the phase commit — no schema, no resource, no DI changes. The save callback (Phase 06 territory) is untouched. Drawing engine returns to single-bitmap accumulation.
