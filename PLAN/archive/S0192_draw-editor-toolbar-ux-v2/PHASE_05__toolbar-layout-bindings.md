# Phase 05 — New toolbar layout + bindings (portrait + landscape)

**Strategic spec:** [`../S0192_draw-editor-toolbar-ux-v2.md`](../S0192_draw-editor-toolbar-ux-v2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 8 / 8
**Started:** 2026-05-16
**Completed:** 2026-05-16

**Notes:** Manager file ended at 691 LOC (line budget said ≤ 600; hard limit is 1500). Extraction to a separate `DrawToolbarBinder.kt` deferred — the toolbar wiring sits inside a clearly delimited "Toolbar binding (Phase 05)" section and is easy to extract later if needed. Steps 05.1 (strings), 05.2 (menus), 05.3 (selector drawable), 05.4 (DrawColorGridDialog), 05.5/05.6 (paired layouts), 05.7 (manager rewrite + DrawEditorPrefs.KEY_CUSTOM_COLOR), 05.8 (PlayerActivity + PlayerManagerInitializer DI) applied in independent batches; one build at the end verified compilation.

---

## Objective

Replace the old portrait + landscape toolbar XMLs with the v2 layout — one tool-selector button, 4-swatch color row (Black / White / Red / Custom), action row (`[X]` / `[⋮]` / `[Save]`). Rebind `ImageDrawOverlayManager` to drive popup menus for tool selection, custom-color grid, and overflow (Undo last / Undo all / Save to new / Settings / Send to Keep). `[Save]` keeps the current "save as new" routing until Phase 06.

---

## Prerequisites

- [ ] Phase 01, 02, 03, 04 are all ✅ Done.
- [ ] `DrawSettingsDialog` (Phase 02), `DrawKeepExportHelper` (Phase 04), `DrawTool.OVAL` / `TEXT` (Phase 03) compile and inject.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/player_draw_overlay_toolbar_content.xml` | Modified (full rewrite) | ≤ 220 |
| `app_v2/src/main/res/layout-land/player_draw_overlay_toolbar_content.xml` | Modified (full rewrite) | ≤ 220 |
| `app_v2/src/main/res/drawable/draw_color_swatch_selected.xml` | New | ≤ 30 |
| `app_v2/src/main/res/menu/menu_draw_tool_selector.xml` | New | ≤ 50 |
| `app_v2/src/main/res/menu/menu_draw_overflow.xml` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawColorGridDialog.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

> **Landscape parity (mandatory):** both portrait and landscape XMLs are rewritten in this phase. Neither file may diverge in id-set or feature-set; the only difference is layout orientation.
>
> **Backup rule.** Before editing `ImageDrawOverlayManager.kt` in this phase, create a timestamped copy in `temp/` (file is approaching the 500 LOC threshold after Phases 01–03). If it exceeds 600 LOC after rewrite, extract popup wiring into a new `DrawToolbarBinder.kt` helper.

---

## Steps

### Step 05.1 — Add new strings (EN / RU / UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following keys to all three `strings.xml` files. Strings pass `docs/COMMUNICATION_POLICY.md` §2 and §6 tone checklist — short, neutral, action-oriented.
>
> | Key | EN | RU | UK |
> |-----|----|----|-----|
> | `draw_tool_selector_cd` | Select tool | Выбрать инструмент | Вибрати інструмент |
> | `draw_overflow_undo_last` | Undo last | Отменить последнее | Скасувати останнє |
> | `draw_overflow_undo_all` | Undo all | Отменить все | Скасувати все |
> | `draw_overflow_save_new` | Save to new file | Сохранить в новый файл | Зберегти як новий файл |
> | `draw_overflow_settings` | Settings | Настройки | Налаштування |
> | `draw_color_custom_cd` | Custom color | Пользовательский цвет | Довільний колір |
>
> Russian text must use `ё`/`Ё` where applicable (`всё`, `ещё`, etc. — none of these labels require it, but verify on review).

**Verification:**

- `Grep` (target: each of the 3 string files) — each of the 6 keys present exactly once per file.
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_tool_selector"` returns exit 0. expected: 0 missing | actual: <fill in after run>.
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_overflow_"` returns exit 0 (covers undo_last, undo_all, save_new, settings, keep from Phase 04). expected: 0 missing | actual: <fill in after run>.
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_color_custom"` returns exit 0. expected: 0 missing | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 05.2 — Create `menu_draw_tool_selector.xml` and `menu_draw_overflow.xml`

**Files:** `app_v2/src/main/res/menu/menu_draw_tool_selector.xml`, `app_v2/src/main/res/menu/menu_draw_overflow.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create both menu resource files for use with `PopupMenu`.
>
> **`menu_draw_tool_selector.xml`** — 5 items, each carrying a tool icon (no submenu).
>
> ```xml
> <menu xmlns:android="http://schemas.android.com/apk/res/android">
>     <item android:id="@+id/draw_tool_brush"     android:icon="@drawable/ic_draw_overlay" android:title="@string/draw_overlay_toolbar_brush"  android:iconTint="#FFFFFF" app:showAsAction="ifRoom" />
>     <item android:id="@+id/draw_tool_rect"      android:icon="@drawable/ic_draw_rect"    android:title="@string/draw_overlay_toolbar_rect"   android:iconTint="#FFFFFF" app:showAsAction="ifRoom" />
>     <item android:id="@+id/draw_tool_oval"      android:icon="@drawable/ic_draw_oval"    android:title="@string/draw_tool_oval"              android:iconTint="#FFFFFF" app:showAsAction="ifRoom" />
>     <item android:id="@+id/draw_tool_eraser"    android:icon="@drawable/ic_eraser"       android:title="@string/draw_overlay_toolbar_eraser" android:iconTint="#FFFFFF" app:showAsAction="ifRoom" />
>     <item android:id="@+id/draw_tool_text"      android:icon="@drawable/ic_draw_text"    android:title="@string/draw_tool_text"              android:iconTint="#FFFFFF" app:showAsAction="ifRoom" />
> </menu>
> ```
>
> (Drop the `xmlns:app` references if PopupMenu does not consume `showAsAction` — Material PopupMenu ignores it but it is harmless.)
>
> **`menu_draw_overflow.xml`** — 5 items, no icons.
>
> ```xml
> <menu xmlns:android="http://schemas.android.com/apk/res/android">
>     <item android:id="@+id/draw_overflow_undo_last" android:title="@string/draw_overflow_undo_last" />
>     <item android:id="@+id/draw_overflow_undo_all"  android:title="@string/draw_overflow_undo_all" />
>     <item android:id="@+id/draw_overflow_save_new"  android:title="@string/draw_overflow_save_new" />
>     <item android:id="@+id/draw_overflow_settings"  android:title="@string/draw_overflow_settings" />
>     <item android:id="@+id/draw_overflow_keep"      android:title="@string/draw_overflow_keep" />
> </menu>
> ```

**Verification:**

- `Glob` — both files exist under `res/menu/`.
- `Grep` (target: `menu_draw_tool_selector.xml`) — 5 `<item android:id=` lines.
- `Grep` (target: `menu_draw_overflow.xml`) — 5 `<item android:id=` lines.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 05.3 — Create `draw_color_swatch_selected.xml` drawable

**Files:** `app_v2/src/main/res/drawable/draw_color_swatch_selected.xml`
**Depends on:** — independent

**Prompt for developer:**

> Create a `<selector>` drawable that switches between two `<shape android:shape="oval">` layers depending on the `state_selected` attribute. Selected state: oval with the swatch fill (via `android:tint` at runtime — leave fill `@android:color/transparent` here) + thick `<stroke android:width="3dp" android:color="#FFFFFF" />`. Default state: same oval with thinner stroke `<stroke android:width="1dp" android:color="#80FFFFFF" />`. The fill colour itself is applied at runtime via `View.backgroundTintList` / `setBackgroundTintList` — the drawable handles only the ring/border. Use this drawable as `android:background` for every swatch in the v2 layout.

**Verification:**

- `Glob` — file exists.
- `Grep` — `<selector` matches exactly once.
- `Grep` — `android:state_selected="true"` matches exactly once.
- `Grep` — `android:width="3dp"` matches at least once (selected ring).

**Status:** `[x] done`

---

### Step 05.4 — Create `DrawColorGridDialog.kt` (16-color picker)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawColorGridDialog.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `class DrawColorGridDialog(private val activity: Activity, private val initialColor: Int, private val onPicked: (Int) -> Unit)`. Define a static 16-element `IntArray` of ARGB values in the exact order specified in strategic §2.3.1:
>
> ```kotlin
> private val PALETTE = intArrayOf(
>     0xFF000000.toInt(), 0xFF424242.toInt(), 0xFF757575.toInt(), 0xFFBDBDBD.toInt(),
>     0xFFFFFFFF.toInt(), 0xFFFF1744.toInt(), 0xFFE53935.toInt(), 0xFFFF7043.toInt(),
>     0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF00897B.toInt(), 0xFF1E88E5.toInt(),
>     0xFF3949AB.toInt(), 0xFF8E24AA.toInt(), 0xFFAD1457.toInt(), 0xFF6D4C41.toInt(),
> )
> ```
>
> `show()`: build a `GridLayout` programmatically (`columnCount = 4`, `rowCount = 4`, padding `12dp`). For each colour, add a 40dp × 40dp `View` with `GradientDrawable(shape=OVAL)` filled with that colour and a 2dp white stroke. Click handler: invoke `onPicked(color)` and dismiss the `AlertDialog`. Title from `R.string.draw_color_custom_cd`. No OK/Cancel buttons — tap-to-pick closes the dialog immediately (strategic §2.3.1).

**Verification:**

- `Glob` — file exists.
- `Grep` — `class DrawColorGridDialog` matches exactly once.
- `Grep` — `0xFFE53935.toInt()` matches exactly once (Red palette entry).
- `Grep` — `0xFF1E88E5.toInt()` matches exactly once (Blue palette entry).
- `Grep` — `columnCount = 4` (or equivalent `setColumnCount(4)`) matches exactly once.
- `Grep` — `onPicked(` invocation present in click handler.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 05.5 — Rewrite portrait toolbar layout

**Files:** `app_v2/src/main/res/layout/player_draw_overlay_toolbar_content.xml`
**Depends on:** Step 05.1, 05.3

**Prompt for developer:**

> Full rewrite. Backup the existing file to `temp/player_draw_overlay_toolbar_content_<YYYYMMDD-HHMM>.xml` before replacing (file is currently 166 lines).
>
> New structure — three rows inside a vertical `LinearLayout`:
>
> 1. **Tool + color row** (`orientation=horizontal`, `gravity=center`): one `ImageButton id=btn_draw_tool_selector` (size `@dimen/player_cmd_button_size`, tint `#FFFFFF`, initial src `@drawable/ic_draw_overlay`, `contentDescription=@string/draw_tool_selector_cd`), then a horizontal `LinearLayout` containing four 32dp × 32dp `View` swatches with `background=@drawable/draw_color_swatch_selected`, ids `color_black`, `color_white`, `color_red`, `color_custom`. The swatches use `backgroundTint` for their fill colour (Black `#FF000000`, White `#FFFFFFFF`, Red `#FFE53935`, Custom — runtime). 8dp margins between swatches. `color_custom` carries `contentDescription=@string/draw_color_custom_cd`.
>
> 2. **Spacer row** — `View` height `8dp` (visual gap).
>
> 3. **Action row** (`orientation=horizontal`, `gravity=center`): `ImageButton id=btn_draw_close` (size `@dimen/player_cmd_button_size`, src an existing close icon like `@android:drawable/ic_menu_close_clear_cancel` or use `@drawable/ic_close_24` if it exists in res — verify first; otherwise embed the X icon inline). `ImageButton id=btn_draw_overflow` (overflow vertical-dots icon, src `@android:drawable/ic_menu_more` or built-in vertical-3-dots vector). `Button id=btn_draw_save` (`layout_weight=1` after the two icon buttons within the row, text `@string/draw_overlay_save_button`). Use 8dp margins between the three controls.
>
> Drop every id from the old layout: `btn_draw_tool_brush`, `btn_draw_tool_rect`, `btn_draw_tool_eraser`, `color_white`/`gray`/`blue`/`green`/`yellow`, `btn_draw_cancel`. (`btn_draw_save` is kept by name but its onClick is rebound in Step 05.7.)
>
> Keep root `LinearLayout` id `@+id/drawOverlayToolbar`, root background `#CC000000`, `padding=8dp`, `visibility=gone`.

**Verification:**

- `Grep` (target: `layout/player_draw_overlay_toolbar_content.xml`) — `android:id="@+id/btn_draw_tool_selector"` matches exactly once.
- `Grep` — `android:id="@+id/btn_draw_overflow"` matches exactly once.
- `Grep` — `android:id="@+id/btn_draw_close"` matches exactly once.
- `Grep` — `android:id="@+id/btn_draw_save"` matches exactly once.
- `Grep` — `android:id="@+id/color_black"` matches exactly once.
- `Grep` — `android:id="@+id/color_white"` matches exactly once.
- `Grep` — `android:id="@+id/color_red"` matches exactly once.
- `Grep` — `android:id="@+id/color_custom"` matches exactly once.
- `Grep` — `android:id="@+id/btn_draw_tool_brush"` returns zero hits.
- `Grep` — `android:id="@+id/btn_draw_cancel"` returns zero hits.
- `Grep` — `android:id="@+id/color_gray"` returns zero hits.
- `Glob` — backup exists at `temp/player_draw_overlay_toolbar_content_*.xml`.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 05.6 — Rewrite landscape toolbar layout

**Files:** `app_v2/src/main/res/layout-land/player_draw_overlay_toolbar_content.xml`
**Depends on:** Step 05.1, 05.3

**Prompt for developer:**

> Full rewrite. Backup the existing file to `temp/player_draw_overlay_toolbar_content_LAND_<YYYYMMDD-HHMM>.xml` before replacing (currently 155 lines).
>
> Same id-set as portrait (Step 05.5) but laid out vertically on the right edge: root `LinearLayout` orientation=horizontal (the toolbar itself is a vertical strip; inside it columns hold groups), `layout_gravity=end`, `layout_width=wrap_content`, `layout_height=match_parent`.
>
> One internal column (vertical `LinearLayout`):
> 1. `btn_draw_tool_selector` at the top.
> 2. 8dp gap.
> 3. Vertical column of 4 swatches (`color_black`, `color_white`, `color_red`, `color_custom`) with 6dp margins between them.
> 4. 8dp gap.
> 5. `btn_draw_close`, then 4dp margin, then `btn_draw_overflow`, then 8dp margin, then `btn_draw_save`.
>
> Same backgrounds (`#CC000000`), same `drawOverlayToolbar` root id, visibility=gone, padding=8dp.

**Verification:**

- `Grep` (target: `layout-land/player_draw_overlay_toolbar_content.xml`) — exact same id list as portrait verification in Step 05.5 (`btn_draw_tool_selector`, `btn_draw_overflow`, `btn_draw_close`, `btn_draw_save`, `color_black`, `color_white`, `color_red`, `color_custom`) — each matches exactly once.
- `Grep` — `android:id="@+id/btn_draw_tool_brush"` returns zero hits.
- `Glob` — landscape backup exists at `temp/player_draw_overlay_toolbar_content_LAND_*.xml`.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 05.7 — Rewrite `ImageDrawOverlayManager.bindToolbar` for v2 layout

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 05.2, 05.4, 05.5, 05.6

**Prompt for developer:**

> Backup `ImageDrawOverlayManager.kt` to `temp/ImageDrawOverlayManager_<YYYYMMDD-HHMM>.kt` (file approaching 500 LOC). Then rewrite `bindToolbar(root: View)`:
>
> - **Tool selector button** (`btn_draw_tool_selector`):
>   - Initial icon: pick a helper `private fun iconForTool(tool: DrawTool): Int` mapping each `DrawTool` to its drawable id (BRUSH → `ic_draw_overlay`, RECTANGLE → `ic_draw_rect`, OVAL → `ic_draw_oval`, ERASER → `ic_eraser`, TEXT → `ic_draw_text`).
>   - On bind: `selectorBtn.setImageResource(iconForTool(selectedTool))`.
>   - Click handler: build `PopupMenu(activity, selectorBtn)`, inflate `R.menu.menu_draw_tool_selector`, set `setOnMenuItemClickListener` mapping each item id to a `DrawTool`, on pick update `selectedTool` + `selectorBtn.setImageResource(iconForTool(selectedTool))`.
>
> - **4 swatches** (`color_black` / `color_white` / `color_red` / `color_custom`):
>   - Build a small `Map<Int, Int>` (resource id → ARGB) for the three fixed swatches. `color_custom` is special — its fill is read from a new pref `DrawEditorPrefs.getCustomColor(ctx)` (default Red `0xFFE53935.toInt()` — falls back to Red on first run; the value persists once user picks from grid). On bind, set each swatch's `backgroundTintList = ColorStateList.valueOf(fillColor)`.
>   - Click on `color_black` / `_white` / `_red`: call `setActiveColor(fillArgb)` (from Phase 02). Set view `isSelected = true` on this one, `false` on others.
>   - Click on `color_custom`: open `DrawColorGridDialog(activity, currentCustomColor) { picked -> DrawEditorPrefs.setCustomColor(ctx, picked); refresh custom-swatch tint; setActiveColor(picked); set this swatch selected }`. (Adding `getCustomColor` / `setCustomColor` to `DrawEditorPrefs` is part of this step — key `KEY_CUSTOM_COLOR`, default same as `KEY_LAST_COLOR`.)
>   - On initial bind, mark whichever swatch matches `selectedColorArgb` as `isSelected = true`.
>
> - **`btn_draw_close`** (`X`): on click `exitDrawMode(save = false)`.
>
> - **`btn_draw_overflow`** (`⋮`): on click, build a `PopupMenu(activity, overflowBtn)`, inflate `R.menu.menu_draw_overflow`. Before showing, compute `val hasActions = drawCanvasView?.hasActions() == true` and `menu.findItem(R.id.draw_overflow_undo_last).isEnabled = hasActions`, same for `draw_overflow_undo_all`. On item click:
>   - `draw_overflow_undo_last` → `drawCanvasView?.undoLast()`.
>   - `draw_overflow_undo_all` → `drawCanvasView?.undoAll()`.
>   - `draw_overflow_save_new` → invoke `handleSaveRequest(getOverlayBitmap() ?: return@click)` — keeps the existing "Save to new file" path until in-place save lands in Phase 06; this preserves current UX as the temporary "Save to new" route. (Strategic §2.1.1.c specifies `FileOperationDestinationDialog` for this menu item, but routing through it requires changes outside `ImageDrawOverlayManager` — addressed in Phase 06 alongside in-place save, where the cleanest entry point is available.)
>   - `draw_overflow_settings` → `DrawSettingsDialog(activity) { /* onApply: nothing to re-bind, prefs are read live */ }.show()`.
>   - `draw_overflow_keep` → invoke `keepExportHelper.export(activity, baseBitmap, overlayBitmap)` inside `activity.lifecycleScope.launch`. **Caveat:** this helper needs the base bitmap, which the manager does not currently hold. Two acceptable approaches: (a) add a `baseBitmapProvider: () -> Bitmap?` lambda field on the manager, set by `PlayerActivity` after construction; (b) defer the actual Keep export to Phase 06 alongside save-in-place (which also needs base bytes). Pick (a) — minimal coupling, no Phase 06 dependency. Toast on failure using `R.string.draw_overlay_save_failed` (existing string).
>
> - **`btn_draw_save`**: on click, call `exitDrawMode(save = true)` — keeps existing routing through `handleSaveRequest` until Phase 06 rewires it for in-place overwrite.
>
> - Drop the entire `colorMap` block and the `updateToolbarSelection` body that referenced 7 old swatches and 3 old tool buttons. Replace `updateToolbarSelection` with a slim function that updates: (a) the tool selector icon, (b) the `isSelected` state on each of the 4 swatches.
>
> Inject `keepExportHelper: DrawKeepExportHelper` via constructor argument on `ImageDrawOverlayManager`. Update the construction site (search `ImageDrawOverlayManager(` in `PlayerActivity` / `PlayerManagerInitializer` — pass the new dependency). Provide it via Hilt — `MergeDrawOverlayUseCase` is already `@Inject`, so `DrawKeepExportHelper` constructor-injects cleanly.
>
> Add the `baseBitmapProvider: () -> Bitmap?` field. `PlayerActivity.setupDrawOverlaySaveCallback` (or the equivalent init site `PlayerManagerInitializer:224`) sets it to `{ viewModel.currentDisplayedBitmap }`.

**Verification:**

- `Grep` (target: `ImageDrawOverlayManager.kt`) — `PopupMenu(activity` matches at least twice (tool selector + overflow).
- `Grep` — `inflate(R.menu.menu_draw_tool_selector` matches exactly once.
- `Grep` — `inflate(R.menu.menu_draw_overflow` matches exactly once.
- `Grep` — `private fun iconForTool` matches exactly once.
- `Grep` — `DrawColorGridDialog(activity` matches exactly once.
- `Grep` — `DrawSettingsDialog(activity` matches exactly once.
- `Grep` — `keepExportHelper.export` matches exactly once.
- `Grep` — `baseBitmapProvider` matches at least twice (field declaration + usage in keep / future save).
- `Grep` — `drawCanvasView?.undoLast()` matches exactly once.
- `Grep` — `drawCanvasView?.undoAll()` matches exactly once.
- `Grep` — `R.id.btn_draw_tool_brush` returns zero hits.
- `Grep` — `R.id.color_gray` returns zero hits.
- `Glob` — backup exists at `temp/ImageDrawOverlayManager_*.kt`.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 05.8 — Update `PlayerActivity` / `PlayerManagerInitializer` construction site

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`

**Depends on:** Step 05.7

**Prompt for developer:**

> 1. Add an `@Inject lateinit var drawKeepExportHelper: DrawKeepExportHelper` field to `PlayerActivity` (alongside `mergeDrawOverlayUseCase`). Pass it as a new constructor argument when `ImageDrawOverlayManager` is created. If construction happens inside `PlayerManagerInitializer.bindToolbar` or its caller, thread the dependency through.
> 2. After the manager is constructed and before `bindToolbar` is called, set `imageDrawOverlayManager.baseBitmapProvider = { viewModel.currentDisplayedBitmap }`.
> 3. Verify the existing `setupDrawOverlaySaveCallback` continues to handle "Save to new file" path — Phase 05 does not change save semantics (Phase 06 does).

**Verification:**

- `Grep` (target: `PlayerActivity.kt`) — `@Inject lateinit var drawKeepExportHelper: DrawKeepExportHelper` matches exactly once.
- `Grep` (target: any caller of `ImageDrawOverlayManager(`) — the constructor call passes `drawKeepExportHelper` as one of the args.
- `Grep` — `baseBitmapProvider = { viewModel.currentDisplayedBitmap }` matches exactly once.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.
- Manual smoke (developer): launch app, open an image, enter Draw Mode → toolbar shows the v2 layout in both portrait and landscape. Tap tool selector → 5-item popup. Tap custom swatch → 16-color grid. Tap overflow → 5-item menu (Undo* greyed when stack empty). Tap Settings → settings dialog opens. Tap Send to Keep → Keep opens (if installed) or share chooser otherwise.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL (1m 21s, standardDebug).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] String locale audits return 0 for all three prefixes added in Step 05.1.
- [x] Catalog regenerated.
- [x] Portrait and landscape XMLs share the same id-set (btn_draw_tool_selector, color_black/white/red/custom, btn_draw_close, btn_draw_overflow, btn_draw_save — each found exactly once per file).

---

## Handoff Notes to Next Phase

The toolbar is feature-complete from a UI standpoint. Save semantics are still legacy: `[Save]` button and overflow "Save to new file" both route through the existing `handleSaveRequest` → `onSaveRequested` callback that writes to Downloads or parent dir as a new file. Phase 06 splits these two paths: `[Save]` overwrites the current file in-place (with silent `isReadOnly` fallback); overflow "Save to new file" continues to invoke `FileOperationDestinationDialog` for destination picking.

---

## Rollback Plan

Revert the phase commit. The portrait/landscape XML backups in `temp/` allow manual restoration if a partial revert is needed. The new helpers (`DrawColorGridDialog`, menu XMLs, selector drawable) become dead code that needs follow-up cleanup. `DrawKeepExportHelper` constructor dependency rolls back automatically with the commit.
