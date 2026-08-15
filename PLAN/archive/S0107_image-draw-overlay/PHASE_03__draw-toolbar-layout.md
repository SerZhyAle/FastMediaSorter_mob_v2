# Phase 03 — Draw Toolbar Layout

**Strategic spec:** [`../S0107_image-draw-overlay.md`](../S0107_image-draw-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-05-09
**Completed:** 2026-05-09
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Create the draw toolbar layout (tool buttons + color palette + Save/Cancel), inflate it as a `ViewStub` or direct include into the player layout, and connect it to `ImageDrawOverlayManager`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Blocker "Rotation behaviour in Draw Mode" from INDEX is resolved. *(Decision: freeze screen orientation in Draw Mode — strategic §6-3 resolved, ADR-4. Landscape toolbar is required — user may enter Draw Mode from landscape orientation.)*
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/player_draw_overlay_toolbar_content.xml` | **New** | ≤ 150 |
| `app_v2/src/main/res/layout-land/player_draw_overlay_toolbar_content.xml` | **New** | ≤ 80 |
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | ≤ current + 10 |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | ≤ current + 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 500 |

> Both portrait and landscape toolbar XML files are required — they differ only in orientation of the tool/palette strip (horizontal vs. vertical).

---

## Steps

### Step 3.1 — Create portrait draw toolbar layout XML

**Files:** `app_v2/src/main/res/layout/player_draw_overlay_toolbar_content.xml` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create `player_draw_overlay_toolbar_content.xml`. Root: `LinearLayout`, `orientation="vertical"`, `gravity="bottom"`, `background="@color/toolbar_background"` (use existing color or `#CC000000` semi-transparent). Structure:
>
> Row 1 — Tool buttons (horizontal `LinearLayout`):
> - `ImageButton` id=`btn_draw_tool_brush`, `contentDescription="@string/draw_overlay_toolbar_brush"`, src=`@drawable/ic_draw_overlay` (reuse or create tool-specific drawable stub).
> - `ImageButton` id=`btn_draw_tool_rect`, `contentDescription="@string/draw_overlay_toolbar_rect"`, src=`@drawable/ic_draw_rect` (new stub).
> - `ImageButton` id=`btn_draw_tool_eraser`, `contentDescription="@string/draw_overlay_toolbar_eraser"`, src=`@drawable/ic_eraser` (new stub or reuse if exists).
>
> Row 2 — Color palette (horizontal `LinearLayout`): 7 `View` items, each a 32 dp circle with a border. Ids: `color_white`, `color_black`, `color_gray`, `color_red`, `color_blue`, `color_green`, `color_yellow`. Use `@drawable/draw_color_swatch` shape drawable (create in next step).
>
> Row 3 — Action buttons (horizontal `LinearLayout`):
> - `Button` id=`btn_draw_cancel`, text=`@string/cancel` (use existing key).
> - `Button` id=`btn_draw_save`, text=`@string/draw_overlay_save_button`.
>
> All sizes follow existing `@dimen/` tokens where available. Visibility of the root `LinearLayout` is `gone` by default.

**Verification:**

- `Glob` — `app_v2/src/main/res/layout/player_draw_overlay_toolbar_content.xml` exists.
- `Grep` — `btn_draw_save` present in the file.
- `Grep` — `btn_draw_tool_brush`, `btn_draw_tool_rect`, `btn_draw_tool_eraser` all present.
- `Grep` — `color_white`, `color_black`, `color_gray`, `color_red`, `color_blue`, `color_green`, `color_yellow` all present (7 color swatches).

**Status:** `[ ]` not done

---

### Step 3.2 — Create landscape draw toolbar layout XML

**Files:** `app_v2/src/main/res/layout-land/player_draw_overlay_toolbar_content.xml` (New)
**Depends on:** Step 3.1

**Prompt for developer:**

> Create the landscape variant at `layout-land/player_draw_overlay_toolbar_content.xml`. Root: `LinearLayout`, `orientation="horizontal"`, anchored to the right edge. Merge tool buttons (vertical strip on the right) and color swatches (vertical strip next to it). Save/Cancel buttons at the bottom of the vertical strip. All view ids are identical to the portrait variant — the Activity code does not need to differentiate.

**Verification:**

- `Glob` — `app_v2/src/main/res/layout-land/player_draw_overlay_toolbar_content.xml` exists.
- `Grep` — `btn_draw_save` present in `layout-land/player_draw_overlay_toolbar_content.xml`.
- `Grep` — All 7 color swatch ids present in `layout-land/player_draw_overlay_toolbar_content.xml`.

**Status:** `[ ]` not done

---

### Step 3.3 — Include draw toolbar in player activity layouts

**Files:**
- `app_v2/src/main/res/layout/activity_player_unified.xml`
- `app_v2/src/main/res/layout-land/activity_player_unified.xml`

**Depends on:** Steps 3.1, 3.2

**Prompt for developer:**

> In `activity_player_unified.xml` (and its landscape counterpart), add an `<include>` tag for `player_draw_overlay_toolbar_content` immediately before the closing tag of the root layout or after the bottom panels include. Assign an id `draw_overlay_toolbar_stub` to the include. Do not change any existing child order or attributes.

**Verification:**

- `Grep` — `draw_overlay_toolbar_stub` present in `layout/activity_player_unified.xml`.
- `Grep` — `draw_overlay_toolbar_stub` present in `layout-land/activity_player_unified.xml`.

**Status:** `[ ]` not done

---

### Step 3.4 — Connect toolbar buttons to `ImageDrawOverlayManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Steps 3.1–3.3

**Prompt for developer:**

> In `ImageDrawOverlayManager`, add a method `bindToolbar(toolbarRoot: View)` that:
>
> - Finds all tool buttons and color swatch views by id.
> - Sets click listeners: tool buttons set `selectedTool`; color swatches set `selectedColor`.
> - Adds a visual "selected" indicator (e.g., 2 dp accent border drawn on the active swatch, or alpha dimming on inactive tool buttons). Use `updateToolbarSelection()` private helper.
> - `btn_draw_save` click: calls `exitDrawMode(save = true)`.
> - `btn_draw_cancel` click: calls `exitDrawMode(save = false)`.
>
> In `enterDrawMode()`, call `toolbarRoot.visibility = View.VISIBLE` (or `GONE` in `exitDrawMode`). The toolbar root must be passed to the manager either via constructor or via `bindToolbar()` call from `PlayerManagerInitializer`.

**Verification:**

- `Grep` — `fun bindToolbar` present in `ImageDrawOverlayManager.kt`.
- `Grep` — `btn_draw_save` referenced in `ImageDrawOverlayManager.kt` (click listener wiring).
- `Grep` — `btn_draw_tool_brush`, `btn_draw_tool_rect`, `btn_draw_tool_eraser` referenced in the manager.
- `Grep` — `Log\.d\(` returns zero hits in `ImageDrawOverlayManager.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.
- [ ] `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- Draw toolbar XML exists in both portrait and landscape variants; ids are stable.
- `ImageDrawOverlayManager.bindToolbar()` wires all tool/color/action buttons.
- Phase 04 can call `imageDrawOverlayManager.getOverlayBitmap()` and implement the merge + save flow.

---

## Rollback Plan

Revert phase commit(s). New XML layout files are deletable without data impact. `activity_player_unified.xml` changes are additive `<include>` only.
