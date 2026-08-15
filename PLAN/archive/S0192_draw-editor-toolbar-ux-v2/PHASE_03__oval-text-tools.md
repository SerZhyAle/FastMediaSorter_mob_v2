# Phase 03 — Oval + Text tools

**Strategic spec:** [`../S0192_draw-editor-toolbar-ux-v2.md`](../S0192_draw-editor-toolbar-ux-v2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-05-16
**Completed:** 2026-05-16

**Notes:** Steps 03.1–03.4 applied as one atomic edit to ImageDrawOverlayManager.kt (enum + sealed-class + replay + touch-handler all touch the same file; safe to coalesce — build only at the end). Steps 03.5 (drawables) and 03.6 (strings) applied independently. All Grep predicates checked individually before phase close. Step 03.3 "`selectedTool == DrawTool.OVAL` matches at least twice" — touch handler uses `DrawTool.OVAL ->` when-arm inside `when (selectedTool)` (functionally equivalent to `==`) so literal text count is 1; intent (OVAL handled in both touch + preview) preserved.

---

## Objective

Extend `DrawTool` and `DrawAction` to support Oval + Text. Implement preview / commit logic in `DrawCanvasView`. Add the text-input `AlertDialog`. Ship `ic_draw_oval` and `ic_draw_text` vector drawables and supporting strings. Tools are reachable programmatically only — toolbar UI ships in Phase 05.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/drawable/ic_draw_oval.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_draw_text.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

> If `ImageDrawOverlayManager.kt` exceeds 500 LOC after this phase, extract `DrawCanvasView` to a separate file `DrawCanvasView.kt` in the same package as a top-level class (drop the `inner` modifier — pass `manager: ImageDrawOverlayManager` via constructor for `selectedTool` / `selectedColorArgb` access). Decision threshold: re-measure after Step 03.4.

---

## Steps

### Step 03.1 — Extend `DrawTool` enum

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add two members to the existing `enum class DrawTool`: `OVAL` and `TEXT`. Order: `BRUSH, RECTANGLE, OVAL, ERASER, TEXT` (Brush / Rectangle / Oval are stroke tools grouped together; Eraser is the destructive tool; Text is the textual tool). The order matters only for the popup-menu ordering in Phase 05.

**Verification:**

- `Grep` — `enum class DrawTool { BRUSH, RECTANGLE, OVAL, ERASER, TEXT }` matches exactly once.

**Status:** `[x] done`

---

### Step 03.2 — Extend `DrawAction` sealed class

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inside `sealed class DrawAction`, add two `data class` variants: `ShapeOval(left: Float, top: Float, right: Float, bottom: Float, color: Int, width: Float)` and `TextEntry(x: Float, y: Float, text: String, color: Int, textSizePx: Float)`. Mirror the structure of `ShapeRect`. No `cachedPath` field for these (drawn directly via `canvas.drawOval` / `canvas.drawText`).

**Verification:**

- `Grep` — `data class ShapeOval` matches exactly once.
- `Grep` — `data class TextEntry` matches exactly once.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 03.3 — Add OVAL preview + commit to `DrawCanvasView`

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Extend `DrawCanvasView.onTouchEvent`:
> - `ACTION_DOWN` for `OVAL`: same as `RECTANGLE` — store `startX/Y`, set `isPointerDown=true`.
> - `ACTION_UP` for `OVAL`: append `DrawAction.ShapeOval(min(startX,x), min(startY,y), max(startX,x), max(startY,y), opacityBakedArgb, brushSizePx)` to `actions`. `opacityBakedArgb` and `brushSizePx` use the same calculation as `RECTANGLE` (Phase 02 Step 02.3).
>
> Extend `DrawCanvasView.onDraw` preview branch: when `selectedTool == OVAL && isPointerDown`, draw `canvas.drawOval(RectF(startX, startY, currentX, currentY), previewPaint)` after configuring `previewPaint.color = selectedColorArgb` (raw, no opacity-bake for preview) and `previewPaint.strokeWidth = DrawEditorPrefs.getBrushSize(context).toFloat()`. Existing rectangle preview behaviour is preserved.
>
> Extend the `replay(canvas)` helper (added in Phase 01 Step 01.5) to handle `DrawAction.ShapeOval`: `paint.color = action.color; paint.strokeWidth = action.width; paint.xfermode = null; canvas.drawOval(RectF(action.left, action.top, action.right, action.bottom), paint)`.

**Verification:**

- `Grep` — `is DrawAction.ShapeOval` matches exactly once inside the replay function.
- `Grep` — `canvas.drawOval` matches at least twice (preview branch + replay branch).
- `Grep` — `selectedTool == DrawTool.OVAL` matches at least twice (touch handler + preview branch).
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 03.4 — Add TEXT commit + AlertDialog to `DrawCanvasView`

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Extend `DrawCanvasView.onTouchEvent`:
> - For `selectedTool == TEXT`, ignore `ACTION_DOWN` / `ACTION_MOVE` (no preview). On `ACTION_UP`, capture tap coordinates `(x, y)` and call a new private method `promptTextEntry(x, y)`.
>
> Implement `promptTextEntry(tapX: Float, tapY: Float)`:
> 1. Create an `EditText` configured with `setSingleLine(true)` (Antigravity §9.3 — multiline deferred), `hint = activity.getString(R.string.draw_text_input_hint)`.
> 2. Build an `AlertDialog` with that `EditText` as the content view. Positive button OK: read `editText.text.toString().trim()`; if empty, do nothing (no action appended). If non-empty: compute `argb = (selectedColorArgb and 0x00FFFFFF) or (DrawEditorPrefs.opacityAlpha(context) shl 24)`, `textSizePx = DrawEditorPrefs.textSizePx(context)`, then append `DrawAction.TextEntry(tapX, tapY, text, argb, textSizePx)` to `actions`, and `invalidate()`. Negative button Cancel: dismiss without action.
> 3. The dialog is the only allocation per tap (no leaked listeners or references; the dialog releases itself on dismiss).
>
> Extend `replay(canvas)` to handle `DrawAction.TextEntry`: configure `paint.style = FILL`, `paint.color = action.color`, `paint.textSize = action.textSizePx`, `paint.xfermode = null`, then `canvas.drawText(action.text, action.x, action.y, paint)`. Reset `paint.style = STROKE` after the call so subsequent stroke / shape entries replay correctly.
>
> Text bounds note: if the user taps near the right or bottom edge, the text glyphs may clip outside the canvas — undo handles correction (ADR-5 makes undo cheap). No clipping safeguard implemented.

**Verification:**

- `Grep` — `private fun promptTextEntry` matches exactly once.
- `Grep` — `setSingleLine(true)` matches at least once inside `promptTextEntry`.
- `Grep` — `is DrawAction.TextEntry` matches exactly once inside the replay function.
- `Grep` — `canvas.drawText` matches exactly once inside the replay function.
- `Grep` — `R.string.draw_text_input_hint` matches at least once in the file.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 03.5 — Add `ic_draw_oval.xml` + `ic_draw_text.xml` drawables

**Files:** `app_v2/src/main/res/drawable/ic_draw_oval.xml`, `app_v2/src/main/res/drawable/ic_draw_text.xml`
**Depends on:** — independent

**Prompt for developer:**

> Create two Android vector drawables matching the visual weight of `ic_draw_rect.xml` and `ic_eraser.xml` (24dp viewport, single white path, line-art style).
>
> `ic_draw_oval.xml`: a hollow ellipse stroke centred in a 24×24 viewport (`pathData` like `M12,4 C7,4 3,8 3,12 C3,16 7,20 12,20 C17,20 21,16 21,12 C21,8 17,4 12,4 Z` with `strokeColor="#FFFFFFFF"`, `strokeWidth="2"`, `fillColor="#00000000"`).
>
> `ic_draw_text.xml`: a serif/block uppercase `T` glyph in 24×24 viewport — either as a `pathData` block letter or as two filled rectangles (one horizontal at top, one vertical centred). Use `fillColor="#FFFFFFFF"`. Keep file ≤ 30 lines.

**Verification:**

- `Glob` — both files exist under `res/drawable/`.
- `Grep` (target: `ic_draw_oval.xml`) — `<vector` element present; `android:viewportWidth="24"` matches.
- `Grep` (target: `ic_draw_text.xml`) — `<vector` element present; `android:viewportWidth="24"` matches.
- Manual visual check (developer): preview in Android Studio drawable preview pane — icons recognisable at 24dp.

**Status:** `[x] done`

---

### Step 03.6 — Add new strings (EN / RU / UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — independent

**Prompt for developer:**

> Add the following keys to all three `strings.xml` files. Strings pass `docs/COMMUNICATION_POLICY.md` §2 (formula for short UI labels) and §6 tone checklist.
>
> | Key | EN | RU | UK |
> |-----|----|----|-----|
> | `draw_tool_oval` | Oval | Овал | Овал |
> | `draw_tool_text` | Text | Текст | Текст |
> | `draw_text_input_hint` | Enter text | Введите текст | Введіть текст |
>
> Russian text must use `ё`/`Ё` where applicable (none required here, but verify on review).

**Verification:**

- `Grep` (target: `values/strings.xml`) — each of the 3 keys present exactly once.
- `Grep` (target: `values-ru/strings.xml`) — each of the 3 keys present exactly once.
- `Grep` (target: `values-uk/strings.xml`) — each of the 3 keys present exactly once.
- Strings pass COMMUNICATION_POLICY §6 checklist (developer self-check).
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_tool_"` returns exit 0. expected: 0 missing | actual: <fill in after run>.
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_text_"` returns exit 0. expected: 0 missing | actual: <fill in after run>.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL (1m, standardDebug).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] String locale audit returns 0 for all 3 keys.
- [x] Catalog regenerated.

---

## Handoff Notes to Next Phase

The drawing engine now supports all 5 tools and the full `DrawAction` taxonomy. Tools, swatches, undo, and settings exist programmatically; the toolbar UI in Phase 05 is the consumer. Phase 04 (Keep export) is independent — runs in parallel.

---

## Rollback Plan

Revert the phase commit — `DrawTool.OVAL` / `TEXT` and the two new `DrawAction` variants disappear. `DrawCanvasView` returns to Brush / Rectangle / Eraser only.
