# Phase 02 — DrawEditorPrefs + Settings dialog

**Strategic spec:** [`../S0192_draw-editor-toolbar-ux-v2.md`](../S0192_draw-editor-toolbar-ux-v2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Add a SharedPreferences-backed helper (`DrawEditorPrefs`) and a settings `AlertDialog` (`DrawSettingsDialog`) for brush size / text size / opacity. Migrate the active-color model from `DrawColor` enum to ARGB `Int`. Wire reads on `enterDrawMode()` and writes on changes. Settings dialog stays unreachable from UI in this phase — it is invoked by the overflow menu in Phase 05.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawEditorPrefs.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawSettingsDialog.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 480 |
| `app_v2/src/main/res/layout/dialog_draw_settings.xml` | New | ≤ 140 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

> No landscape-variant layout needed for `dialog_draw_settings.xml` — it is a standard `AlertDialog` content view; Android handles orientation re-inflation.

---

## Steps

### Step 02.1 — Create `DrawEditorPrefs.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawEditorPrefs.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create an `object DrawEditorPrefs` mirroring the `PlayerLayoutModePrefs` pattern. Use file name `"draw_editor"`. Keys: `KEY_LAST_COLOR` (Int ARGB), `KEY_BRUSH_SIZE` (Int 1..36), `KEY_TEXT_SIZE` (Int ordinal: 0=Small, 1=Medium, 2=Large), `KEY_OPACITY_PCT` (Int 0/25/50/75/100). Defaults: last color = `0xFFE53935.toInt()` (Red), brush size = 12, text size ordinal = 1 (Medium), opacity = 100. Expose typed getters/setters: `getLastColor(ctx)`, `setLastColor(ctx, argb)`, `getBrushSize(ctx)`, `setBrushSize(ctx, value)`, `getTextSizeOrdinal(ctx)`, `setTextSizeOrdinal(ctx, ordinal)`, `getOpacityPct(ctx)`, `setOpacityPct(ctx, pct)`. Also expose a derived helper `fun textSizePx(ctx: Context): Float` that converts ordinal → sp (14/20/30) → px via `resources.displayMetrics.scaledDensity`, and `fun opacityAlpha(ctx: Context): Int` that maps 0/25/50/75/100 → 0/64/128/191/255.

**Verification:**

- `Glob` — file exists at `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawEditorPrefs.kt`.
- `Grep` — `object DrawEditorPrefs` matches exactly once.
- `Grep` — `private const val KEY_LAST_COLOR` matches exactly once.
- `Grep` — `fun textSizePx(ctx: Context): Float` matches exactly once.
- `Grep` — `fun opacityAlpha(ctx: Context): Int` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS. Files: DrawEditorPrefs.kt (+108 LOC, new file). Dev log recorded.

---

### Step 02.2 — Replace `DrawColor` enum field with ARGB Int in `ImageDrawOverlayManager`

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Change the public field `var selectedColor: DrawColor` to `var selectedColorArgb: Int = 0xFFE53935.toInt()`. Keep the `DrawColor` enum definition in place (still referenced by the old toolbar layout binding until Phase 05) — but the live state is the Int. In `enterDrawMode()`, read `selectedColorArgb = DrawEditorPrefs.getLastColor(activity)`. Add a private helper `private fun setActiveColor(argb: Int) { selectedColorArgb = argb; DrawEditorPrefs.setLastColor(activity, argb); toolbarRoot?.let { updateToolbarSelection(it) } }` and route every color-click handler in `bindToolbar` through it. Update `DrawCanvasView` references from `selectedColor.argb` to `selectedColorArgb` (touch handler, ACTION_DOWN brush/eraser color, ACTION_UP rectangle color).

**Verification:**

- `Grep` — `var selectedColorArgb: Int` matches exactly once.
- `Grep` — `selectedColor.argb` returns zero hits.
- `Grep` — `DrawEditorPrefs.getLastColor(activity)` matches at least once inside `enterDrawMode`.
- `Grep` — `DrawEditorPrefs.setLastColor` matches at least once inside `setActiveColor`.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (55s).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS. selectedColor enum field swapped for selectedColorArgb Int (default 0xFFE53935 Red). setActiveColor helper centralises color picks; persists via DrawEditorPrefs. Build PASS. Dev log recorded.

---

### Step 02.3 — Apply opacity + brush-size from prefs at action creation

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `DrawCanvasView.onTouchEvent` `ACTION_DOWN` (brush/eraser): replace hardcoded `width = 12f` with `width = DrawEditorPrefs.getBrushSize(context).toFloat()` for `BRUSH`; for `ERASER`, use `DrawEditorPrefs.getBrushSize(context).toFloat() * 2f` (eraser is always brush × 2 — strategic §2.5). In `ACTION_UP` `RECTANGLE`: replace hardcoded `width = 6f` with the same brush-size value (the spec does not separate rectangle width from brush size). Opacity is baked into the action `color` ARGB at construction time (Antigravity §9.1 / ADR-6): build `val argb = (selectedColorArgb and 0x00FFFFFF) or (DrawEditorPrefs.opacityAlpha(context) shl 24)` and pass `argb` (not `selectedColorArgb`) into the `Stroke` / `ShapeRect`. For `ERASER` strokes, color stays `Color.TRANSPARENT` regardless of opacity (eraser is full transparency by definition).

**Verification:**

- `Grep` — `DrawEditorPrefs.getBrushSize(context)` matches at least twice (brush + eraser) inside `DrawCanvasView`.
- `Grep` — `DrawEditorPrefs.opacityAlpha(context)` matches at least once inside `DrawCanvasView`.
- `Grep` — `width = 12f` returns zero hits inside `DrawCanvasView`.
- `Grep` — `width = 6f` returns zero hits inside `DrawCanvasView`.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (55s).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS. Brush size sourced from `DrawEditorPrefs.getBrushSize` (×2 for eraser). Opacity baked into stroke/shape ARGB at creation time (ADR-6). Eraser color remains `Color.TRANSPARENT`. Build PASS. Dev log recorded.

---

### Step 02.4 — Add new strings (EN / RU / UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the following keys to all three `strings.xml` files. Strings pass `docs/COMMUNICATION_POLICY.md` §2 (formula) and §6 tone checklist — short, neutral, no jargon.
>
> | Key | EN | RU | UK |
> |-----|----|----|-----|
> | `draw_settings_title` | Draw settings | Настройки рисования | Налаштування малювання |
> | `draw_settings_brush_size` | Brush size | Размер кисти | Розмір пензля |
> | `draw_settings_text_size` | Text size | Размер текста | Розмір тексту |
> | `draw_settings_text_small` | Small | Маленький | Малий |
> | `draw_settings_text_medium` | Medium | Средний | Середній |
> | `draw_settings_text_large` | Large | Большой | Великий |
> | `draw_settings_opacity` | Opacity | Прозрачность | Прозорість |
>
> Russian text must use `ё`/`Ё` where applicable.

**Verification:**

- `Grep` (target: `values/strings.xml`) — each of the 7 keys present exactly once.
- `Grep` (target: `values-ru/strings.xml`) — each of the 7 keys present exactly once.
- `Grep` (target: `values-uk/strings.xml`) — each of the 7 keys present exactly once.
- Strings pass COMMUNICATION_POLICY §6 checklist (developer self-check).
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_settings_"` returns exit 0. expected: 0 missing | actual: PASS (7 keys × 3 locales all present).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 7/7 keys × 3 locales PASS via `check_strings_localized.ps1`. All values written via `set-android-string.ps1`. Author style: `ё`/`Ё` not required in these labels. Dev log recorded.

---

### Step 02.5 — Create `dialog_draw_settings.xml` + `DrawSettingsDialog.kt`

**Files:**
- `app_v2/src/main/res/layout/dialog_draw_settings.xml`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawSettingsDialog.kt`

**Depends on:** Step 02.4

**Prompt for developer:**

> **Layout** (`dialog_draw_settings.xml`): root `LinearLayout` vertical, padding `16dp`. Children top-to-bottom:
> 1. `TextView` label `@string/draw_settings_brush_size` + horizontally a `SeekBar` `id=seek_brush_size` (max=35, i.e. 1..36 with offset, step 1) and to its right a `TextView` `id=label_brush_value` showing the current value.
> 2. `TextView` label `@string/draw_settings_text_size` + a `RadioGroup` `id=radio_text_size` orientation=horizontal with three `RadioButton` (`id=radio_text_small` / `_medium` / `_large`) using strings `draw_settings_text_small/medium/large`.
> 3. `TextView` label `@string/draw_settings_opacity` + a `RadioGroup` `id=radio_opacity` orientation=horizontal with five `RadioButton` (`id=radio_op_0/_25/_50/_75/_100`) labelled `"0%"`, `"25%"`, etc. (literal `%`, no string resource — labels are language-neutral).
>
> **Helper class** (`DrawSettingsDialog.kt`): `class DrawSettingsDialog(private val activity: Activity, private val onApply: () -> Unit)`. Method `fun show()` inflates the layout, reads current values from `DrawEditorPrefs`, pre-selects controls, wires SeekBar `OnSeekBarChangeListener` to update the numeric label live (current brush-size value = progress + 1 in the 1..36 range; UI label shows that absolute value). Builds an `AlertDialog`: Negative `android.R.string.cancel` (no-op), Positive `android.R.string.ok` (writes all three values back via `DrawEditorPrefs.set*` then calls `onApply()`). Title from `@string/draw_settings_title`. No saved state across dialog dismissal (Cancel = discard; ADR-5 invariant applies to undo stack — this dialog is configuration, no undo concern).

**Verification:**

- `Glob` — `dialog_draw_settings.xml` exists.
- `Glob` — `DrawSettingsDialog.kt` exists.
- `Grep` (target: `dialog_draw_settings.xml`) — `android:id="@+id/seek_brush_size"` matches once; `android:id="@+id/radio_text_size"` once; `android:id="@+id/radio_opacity"` once.
- `Grep` (target: `DrawSettingsDialog.kt`) — `class DrawSettingsDialog` matches exactly once.
- `Grep` (target: `DrawSettingsDialog.kt`) — `DrawEditorPrefs.setBrushSize`, `DrawEditorPrefs.setTextSizeOrdinal`, `DrawEditorPrefs.setOpacityPct` each match at least once.
- `Grep` (target: `DrawSettingsDialog.kt`) — `onApply()` invocation present in OK click path.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (1m 4s).

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verification 7/7 PASS. dialog_draw_settings.xml (~125 LOC, SeekBar 0..35 maps to 1..36; 2 RadioGroups). DrawSettingsDialog.kt (~95 LOC, inflates layout, reads/writes prefs, OK persists + invokes onApply). Build PASS. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL (1m 4s, standardDebug).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] String locale audit returns 0 (7 × 3 OK).
- [x] Catalog regenerated — 1327 records.

---

## Handoff Notes to Next Phase

The brush-size / opacity / color values are now live, persisted, and applied at action creation. The dialog exists but is unreachable from UI. Phase 03 extends the engine with Oval + Text tools (also using `DrawEditorPrefs.textSizePx` / `opacityAlpha`). Phase 05 wires both this dialog and the new tools into the rebuilt toolbar.

---

## Rollback Plan

Revert the phase commit — `DrawEditorPrefs` and `DrawSettingsDialog` become unused dead code that needs a follow-up deletion. The `DrawColor` enum lookup paths still work for the old toolbar (the enum was left in place). No data migration required.
