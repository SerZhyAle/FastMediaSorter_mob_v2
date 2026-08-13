# S0192 — Draw Editor Toolbar UX v2

**Status:** Verified  
**Implemented date:** 2026-05-16  
**Verified date:** 2026-05-17  
**Tactical plan:** `PLAN/S0192_draw-editor-toolbar-ux-v2/INDEX.md`  
**Priority:** 50  
**Created:** 2026-05-14  
**Updated:** 2026-05-17 (rev 5 — swatch filled-disc indicator + Save never prompts)  
**Ticket:** S0192  
**File:** `PLAN/S0192_draw-editor-toolbar-ux-v2.md`

---

## 1. Context

Existing draw editor (`ImageDrawOverlayManager` + `DrawCanvasView`, S0107) has:
- Three separate tool icon-buttons (Brush, Rectangle, Eraser) in one toolbar row.
- Seven color swatches (White, Black, Gray, Red, Blue, Green, Yellow) in one row.
- Two full-width action buttons: "Cancel" + "Save as new file".
- No undo stack — clearing all at once is impossible.
- No Oval tool, no Text tool.
- No persistent color preference.

This spec redesigns the toolbar without touching the canvas-drawing engine itself (the `DrawCanvasView` stroke/rect/eraser logic stays as-is). The canvas engine is extended to support oval and text primitives and an undo stack.

---

## 2. Changes

### 2.1 Toolbar layout — single row (Phase 06 revision)

The entire draw toolbar is one horizontal row in portrait (one vertical column in landscape). The Phase 05 separate "action row" with a standalone Save text button was removed on 2026-05-17 (user feedback: the labels "Save as new file" on the bottom button and "Save to new file" in the overflow read as duplicates, and the two-row toolbar wasted vertical space on small/Quest screens). Both save modes now live inside the overflow popup.

**Toolbar row (left → right in portrait, top → bottom in landscape):**

| Slot | Control | Behaviour |
|------|---------|-----------|
| 1 | Tool selector icon | Opens `R.menu.menu_draw_tool_selector` (Brush / Rectangle / Oval / Eraser / Text). Icon mirrors active tool. |
| 2 | 4 colour swatches | Black / White / Red / Custom. Custom long-tap (Phase 05) → 16-colour grid dialog. |
| 3 | Weighted spacer | Pushes right cluster to the edge so colour swatches stay grouped with the tool selector. |
| 4 | `[⋮]` overflow icon | Opens the action popup listed below. |
| 5 | `[X]` close icon | Exit Draw Mode without saving. No confirmation dialog (drawings are in-memory only). |

**Overflow menu items (in display order):**

- **a. Save** (`draw_overflow_save_inplace`) — primary. Overwrites the **current file** in-place. Local paths go through `FileOutputStream`; `content://` URIs go through `contentResolver.openOutputStream(uri, "wt")`. Success → "Saved" toast + close draw mode. Failure (write denied, read-only resource, etc.) → "Failed to save" toast + stay in draw mode. **Never prompts for a filename** — the picker-based flow is the separate "Save as.." command.
- **b. Save as..** (`draw_overflow_save_new`) — shows the existing `FileOperationDestinationDialog` (resource-picker) with the pre-filled auto-generated filename. On successful write, closes draw editor. On cancel, stays in draw mode.
- **c. Undo last change** — pops one entry from the undo stack and redraws. Disabled (greyed) when stack is empty.
- **d. Undo all changes** — clears the entire undo stack and canvas. Disabled when stack is empty.
- **e. Settings** — opens the draw-editor settings dialog (§2.5).
- **f. Send to Google Keep** — exports the current drawing as an image to Google Keep (§2.6).

### 2.6 Send to Google Keep

Exports the current drawing as an image to Google Keep via the standard Android share intent. The editor **stays open** after the intent is fired — the user continues editing or closes manually with `[X]`.

#### Export flow

1. Merge overlay onto the base image: `MergeDrawOverlayUseCase.execute(baseBitmap, overlayBitmap, JPEG, quality=95)` — produces bytes of the final composite.
2. Write bytes to a temp file in `context.cacheDir` (`draw_keep_export_<timestamp>.jpg`). Overwrite if exists.
3. Obtain a `FileProvider` URI using the already-configured authority `${packageName}.fileprovider`.
4. Build `Intent.ACTION_SEND` with `type = "image/jpeg"`, `EXTRA_STREAM = uri`, `EXTRA_TEXT = ""` (empty — Keep will show the image as the note body), `addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`.
5. Set `intent.setPackage("com.google.android.keep")` to target Keep directly.
6. Check `intent.resolveActivity(packageManager) != null`:
   - **Keep installed** → `activity.startActivity(intent)` directly.
   - **Keep not installed** → remove `setPackage`, wrap in `Intent.createChooser(intent, …)` and start — falls back to the generic share sheet so the user can pick any note app.
7. Temp file is left in `cacheDir`; Android cleans it up on next cache trim. No manual cleanup required.

#### What is exported

The **merged composite** (base image + overlay) — the same bytes that would be written on "Save to new file". Not the raw transparent overlay.

#### No internet permission implication

The share intent is handled by the Google Keep app (or another app). This feature requires no new permissions in the manifest — `FileProvider` grants read-only URI access to the receiving app automatically.

---

### 2.2 Tool selector (replacing three separate tool buttons)

Current: three always-visible `ImageButton` controls (Brush, Rectangle, Eraser).  
New: **one `ImageButton`** that shows the icon of the currently active tool.  
Tap → popup menu (or BottomSheet, same style as 2.1 overflow) listing all tools. Selected tool gets a checkmark or highlight.

**Tool list (5 items):**

| Tool | Icon | Notes |
|------|------|-------|
| Brush | `ic_draw_overlay` | Freehand stroke. Already implemented. |
| Rectangle | `ic_draw_rect` | Stroke rect. Already implemented. |
| Eraser | `ic_eraser` | PorterDuff CLEAR stroke. Already implemented. |
| **Oval** | `ic_draw_oval` (new) | Stroke ellipse, same UX pattern as Rectangle: preview on drag, commit on ACTION_UP. |
| **Text** | `ic_draw_text` (new) | Tap on canvas → `EditText` dialog to enter text → draw at tap coordinates with current color and a fixed readable size (20sp equivalent in canvas px). Confirmed text is one undo entry. |

Active tool icon button shows the icon of the active tool (so user always sees which tool is selected).

### 2.3 Color palette

Current: 7 fixed color swatches (inline row).  
New: **4 controls** in one compact row:

| Control | Behaviour |
|---------|-----------|
| Black swatch | Select black (#FF000000). |
| White swatch | Select white (#FFFFFFFF). |
| Red swatch | Select red (#FFE53935). |
| Custom swatch | Shows the **currently selected custom color** as its fill. Tap → shows a 16-color grid dialog (see §2.3.1). |

**Selected color indicator (Phase 06 revision):** the active swatch renders as a **solid filled disc** tinted with the swatch colour; inactive swatches render as a 2dp tinted ring. The qualitative shape difference (disc vs ring) makes the active colour unmistakable. Source: user feedback 2026-05-17 — the previous "thick white ring" indicator was too subtle on small/Quest screens. Applies to all four swatches including Custom.

#### 2.3.1 16-color custom palette dialog

Simple `AlertDialog` with a `GridView` (4 columns × 4 rows) of 16 color circles:

```
Row 1: #FF000000  #FF424242  #FF757575  #FFBDBDBD
Row 2: #FFFFFFFF  #FFFF1744  #FFE53935  #FFFF7043
Row 3: #FFFDD835  #FF43A047  #FF00897B  #FF1E88E5
Row 4: #FF3949AB  #FF8E24AA  #FFAD1457  #FF6D4C41
```

Selecting a color closes the dialog, sets Custom swatch fill to that color, and selects it as the active color.  
No "OK" step — tap-to-select closes immediately.

### 2.5 Settings dialog

Opened from overflow menu item **d** (§2.1). Standard `AlertDialog` with a scrollable form and **Cancel / OK** buttons.

All values are persisted in `PreferenceManager`/DataStore and restored on every `enterDrawMode()` call.

#### Controls

| Setting | UI | Values | Default | Pref key |
|---------|-----|--------|---------|----------|
| Brush size | Horizontal `SeekBar` (range 1–36, step 1) + numeric label showing current value | 1–36 px | 12 | `draw_brush_size` |
| Text size | `RadioGroup` with three options | Small / Medium / Large | Medium | `draw_text_size` |
| Opacity | `RadioGroup` with five options | 0 / 25 / 50 / 75 / 100 % | 100 | `draw_opacity_pct` |

**Eraser size** is not a separate setting — it is always `brush_size × 2`, applied automatically. No control for it in the dialog.

#### Behaviour

- **Cancel** — closes dialog, discards any in-dialog edits, all settings retain their last-saved values.
- **OK** — saves all three values to prefs, applies them to the active session immediately (no editor restart needed).
- Changes take effect for all strokes drawn **after** OK is pressed; existing strokes in the undo stack are unaffected.
- If the dialog is opened mid-session, the controls show the currently active values (not defaults).

#### Text size mapping

| Label | Canvas text size (sp-equivalent in px) |
|-------|----------------------------------------|
| Small | 14sp |
| Medium | 20sp |
| Large | 30sp |

(Conversion: `textSizePx = spValue * resources.displayMetrics.scaledDensity`)

#### Opacity mapping

Opacity applies to both brush/shape strokes and text. Implemented via `Paint.alpha` (0–255):

| % | alpha byte |
|---|------------|
| 0 | 0 |
| 25 | 64 |
| 50 | 128 |
| 75 | 191 |
| 100 | 255 |

---

### 2.4 Default color and persistence

- **Default on first open:** Red (`#FFE53935`).
- **Persistence:** active color (as ARGB int) is stored via `PreferenceManager`/DataStore under key `draw_editor_last_color` after each color change.
- On next `enterDrawMode()` call, the stored color is restored. If no stored value → Red.
- Stored value can be any ARGB int (a fixed preset or a custom palette pick).

---

## 3. Undo stack

`DrawCanvasView` currently commits each stroke/shape atomically to a single `Bitmap`. To support per-action undo, the internal model is extended:

- Replace the single `bitmap` with a `MutableList<DrawAction>` (sealed class variants: `Stroke`, `Shape`, `TextEntry`).
- `onDraw()` replays all entries from scratch onto a temporary canvas → drawn to view.  
  (Acceptable for typical drawing session lengths; no performance concern until ~1000+ entries.)
- `undoLast()` → `actions.removeLastOrNull(); invalidate()`.
- `undoAll()` → `actions.clear(); invalidate()`.
- `getBitmap()` renders the current list to a fresh `Bitmap` for export (same as current but from list).

**DrawAction sealed class:**

```kotlin
sealed class DrawAction {
    // alpha is baked into color ARGB — set at stroke/text creation time from current opacity pref
    data class Stroke(val points: List<PointF>, val color: Int, val width: Float) : DrawAction()
    data class ShapeRect(val l: Float, val t: Float, val r: Float, val b: Float, val color: Int, val width: Float) : DrawAction()
    data class ShapeOval(val l: Float, val t: Float, val r: Float, val b: Float, val color: Int, val width: Float) : DrawAction()
    data class TextEntry(val x: Float, val y: Float, val text: String, val color: Int, val textSizePx: Float) : DrawAction()
}
```

Opacity is applied at action-creation time: the ARGB `color` stored in each `DrawAction` has its alpha byte set from the active `draw_opacity_pct` value. This means replay is correct with no extra per-action opacity state.

---

## 4. "Save" in-place (overwrite current file)

Triggered by **overflow item a** (`draw_overflow_save_inplace` — labelled simply "Save" / "Сохранить" / "Зберегти"). Flow:

1. `drawCanvasView.getBitmap()` → overlay.
2. `MergeDrawOverlayUseCase.execute(baseBitmap, croppedOverlay, outputFormat)` → merged bytes. `outputFormat` follows the current file's extension (JPEG for `.jpg`/`.jpeg`, otherwise PNG).
3. Write merged bytes back to the current file:
   - `content://` URI → `contentResolver.openOutputStream(uri, "wt")`
   - Local path → `FileOutputStream(File(path))`
4. On success: close draw mode, show toast "Saved", and refresh the view-model so the gallery cache reflects the new bytes.
5. On failure: show toast "Failed to save", stay in draw mode. **No filename prompt is shown under any circumstance** — the prompt-based flow is the separate "Save as.." command (overflow item b).

The in-place pipeline does not silently re-route to "Save as.." anymore. If the user actually needs a picker (e.g. resource is read-only or write is denied), they pick "Save as.." themselves after seeing the failure toast.

---

## 5. Files affected

| File | Change |
|------|--------|
| `ui/player/helpers/ImageDrawOverlayManager.kt` | Refactor `DrawTool` enum (add OVAL, TEXT); refactor `DrawColor` → 4-entry enum + custom ARGB int; replace `DrawCanvasView` bitmap model with action list; add undo methods; add `saveInPlace()` flow; add color persistence via PreferenceManager; add settings-dialog show/apply logic; rebind toolbar to new layout. |
| `ui/player/helpers/DrawCanvasView` (inner class) | Migrate to `DrawAction` list; implement OVAL and TEXT input; expose `undoLast()`, `undoAll()`. |
| `res/layout/player_draw_overlay_toolbar_content.xml` | Single horizontal row: `[tool] [B][W][R][C] [spacer weight=1] [⋮] [X]`. Phase 06: removed bottom Save text button + the second row entirely. |
| `res/layout-land/player_draw_overlay_toolbar_content.xml` | Single vertical column mirror of portrait. Phase 06: removed bottom Save button + intermediate spacers. |
| `res/menu/menu_draw_overflow.xml` | 6 items: Save · Save as.. · Undo last · Undo all · Settings · Send to Google Keep. |
| `res/drawable/ic_draw_oval.xml` | New vector icon for oval tool. |
| `res/drawable/ic_draw_text.xml` | New vector icon for text tool. |
| `res/layout/dialog_draw_settings.xml` | Settings dialog layout: SeekBar + label for brush size, two RadioGroups (text size, opacity). |
| `res/values/strings.xml` + RU + UK | New keys: tool names, overflow menu items, save toast strings. |
| `domain/usecase/MergeDrawOverlayUseCase.kt` | No change needed — accepts any `Bitmap`. |

---

## 6. Out of scope

- Filled (non-stroke) shapes.
- Text font selection.
- Multi-finger undo gesture.
- Per-tool independent brush-size settings (one global size applies to all stroke tools).
- Saving to network resources directly (the existing `FileOperationDestinationDialog` handles resource routing; this spec does not extend its logic).

---

## 7. ADRs

**ADR-1: Action list replay vs. layer stack**  
Replay from action list is simpler than maintaining per-layer bitmaps and sufficient for typical session lengths. If performance becomes a concern, a snapshot-every-N-entries strategy can be added later.

**ADR-2: Text input via AlertDialog**  
An inline floating `EditText` on the canvas requires complex focus/keyboard management. A simple `AlertDialog` is consistent with the existing filename dialog pattern in this file.

**ADR-3: 16 fixed custom colors, no free color picker**  
A full HSV/RGB picker adds significant UI complexity. 16 curated colors cover all practical use cases (annotations, highlights). A free picker can be added later behind the same Custom button.

**ADR-4 (revised 2026-05-17): "Save" never prompts**  
Originally Phase 05 silently fell back from in-place save to the "Save as.." picker when the file was read-only or non-local. User feedback 2026-05-17: "Save means replace the file, no prompt — failures show a toast and that's it." Phase 06 drops the fallback entirely. In-place save attempts the write; on denial, the operation surfaces a single failure toast. The user can then explicitly pick "Save as.." if they want a copy. This keeps the semantic meaning of the two overflow commands unambiguous: **Save** = overwrite, **Save as..** = pick a destination.

**ADR-9: Both save modes live in the overflow popup (Phase 06)**  
Originally Phase 05 used a prominent text button `[Save]` in a second toolbar row + a `Save to new file` overflow item. Field test showed the two labels ("Save as new file" on the button, "Save to new file" in the popup) read as duplicates and the two-row toolbar wasted vertical space on phone portrait / Quest. Phase 06 collapses everything to one row, removes the bottom Save button entirely, and puts both modes in the overflow as distinct items (`Save` = in-place, `Save as..` = picker). The trade-off: in-place save now costs two taps instead of one, but visual clarity wins and there is no more lost vertical space below the canvas. Source: user feedback 2026-05-17.

**ADR-5: Undo stack is session-only**  
Undo history is not persisted. On `exitDrawMode(save=false)` the stack is discarded. This is the expected behavior for a drawing session.

**ADR-6: Opacity baked into color ARGB at creation, not at replay**  
Storing alpha in the ARGB of each `DrawAction.color` keeps replay trivial — `Paint.color = action.color` already carries the transparency. The alternative (storing raw ARGB + separate alpha and applying at render time) would require touching every `onDraw()` branch. Baking at creation is the simpler invariant.

**ADR-7: SeekBar for brush size, RadioGroup for text size and opacity**  
Brush size is a continuous range (1–36) → SeekBar is the natural fit. Text size and opacity have a small fixed set of meaningful values where the label matters more than the exact number → RadioGroup keeps the UI readable without a numeric picker.

**ADR-8: Target Keep directly, fall back to generic chooser**  
`setPackage("com.google.android.keep")` sends straight to Keep without an intermediate chooser. If Keep is absent, the package restriction is removed and `createChooser` is used — same image ends up shareable to any installed note-taking app. No hard dependency on Keep being installed.

---

## 8. String keys required (EN / RU / UK)

| Key | EN | RU | UK |
|-----|----|----|-----|
| `draw_tool_selector_cd` | Select tool | Выбрать инструмент | Вибрати інструмент |
| `draw_tool_oval` | Oval | Овал | Овал |
| `draw_tool_text` | Text | Текст | Текст |
| `draw_overflow_undo_last` | Undo last | Отменить последнее | Скасувати останнє |
| `draw_overflow_undo_all` | Undo all | Отменить все | Скасувати все |
| `draw_overflow_save_inplace` | Save | Сохранить | Зберегти |
| `draw_overflow_save_new` | Save as.. | Сохранить как.. | Зберегти як.. |
| `draw_save_ok_toast` | Saved | Сохранено | Збережено |
| `draw_save_failed_toast` | Failed to save | Не удалось сохранить | Не вдалося зберегти |
| `draw_color_custom_cd` | Custom color | Пользовательский цвет | Довільний колір |
| `draw_text_input_hint` | Enter text | Введите текст | Введіть текст |
| `draw_settings_title` | Draw settings | Настройки рисования | Налаштування малювання |
| `draw_settings_brush_size` | Brush size | Размер кисти | Розмір пензля |
| `draw_settings_text_size` | Text size | Размер текста | Розмір тексту |
| `draw_settings_text_small` | Small | Маленький | Малий |
| `draw_settings_text_medium` | Medium | Средний | Середній |
| `draw_settings_text_large` | Large | Большой | Великий |
| `draw_settings_opacity` | Opacity | Прозрачность | Прозорість |
| `draw_overflow_settings` | Settings | Настройки | Налаштування |
| `draw_overflow_keep` | Send to Google Keep | Отправить в Google Keep | Надіслати до Google Keep |
| `draw_keep_not_installed` | Google Keep is not installed | Google Keep не установлен | Google Keep не встановлено |

---

## 9. AI Review & Suggestions (Added by Antigravity)

**1. Architecture & Performance (`DrawAction` replay)**
- **Path Caching:** In the `DrawAction.Stroke` data class, instead of re-building a `android.graphics.Path` from `List<PointF>` on every `onDraw()` replay, consider caching the compiled `Path` inside the `Stroke` object. This avoids object allocation (and GC churn) during `onDraw` when the stroke count grows.
- **Eraser Replay:** Ensure the `onDraw()` temporary canvas is initialized with `Color.TRANSPARENT` before replaying the stack, so `PorterDuff.Mode.CLEAR` (Eraser) works correctly without carving through the base image beneath the overlay.

**2. Google Keep Export (Android 11+ compat)**
- **ClipData for FileProvider:** When firing `ACTION_SEND` with a `FileProvider` URI, it's highly recommended to add `intent.clipData = ClipData.newRawUri("", uri)` in addition to `FLAG_GRANT_READ_URI_PERMISSION`. Without this, some versions of Android 11+ (API 30+) may fail to grant read access to the target app.

**3. UX & Edge Cases**
- **Text Tool Limitations:** The spec describes text entry via `AlertDialog`. Consider specifying if multiline text (`\n`) is supported or if the `EditText` should enforce `singleLine="true"`. Additionally, since text cannot be moved after placement, users might misclick and place text poorly. The `Undo` feature mitigates this, but a quick note about text bounds (e.g., text clipping at screen edges) might be useful.
- **Eraser Size:** Max brush size is 36px, so max eraser is 72px. This might feel small for wiping out large sections, but `Undo all` provides a quick wipe alternative.
- **In-place Save (Scoped Storage):** The spec assumes `WriteFileUseCase` can overwrite `currentFile` seamlessly. On Android 10/11+, overwriting a media file not owned by the app might trigger a `RecoverableSecurityException` (or require `MediaStore.createWriteRequest`). Verify that `WriteFileUseCase` handles these system dialogs gracefully.

---

## Last Audit

*(not yet run)*
