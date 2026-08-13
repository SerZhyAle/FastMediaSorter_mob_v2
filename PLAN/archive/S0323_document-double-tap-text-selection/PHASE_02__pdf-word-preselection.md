# Phase 02 - PDF word pre-selection by long-press coordinate

**Strategic spec:** [`../S0323_document-double-tap-text-selection.md`](../S0323_document-double-tap-text-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-01
**Completed:** 2026-06-01

> **Step Log:** 2026-06-01 - Steps 02.1-02.2 verification PASS (greps). 02.3 build standard debug PASS (BUILD SUCCESSFUL 2m31s; first attempt failed on stale kapt cache, clean re-run green). Files: PlayerGestureSetupManager.kt, PdfViewerManager.kt, TranslationManager.kt, PdfTextSelectionManager.kt, PdfSelectionCoordinateMapper.kt (new). Catalog regenerated. Accuracy is OCR word-box approximate - device-test confirms real-world quality.

---

## Objective

When the PDF text-selection overlay opens from a long-press, pre-select the word nearest the long-press point so the native selection handles and floating Copy appear on it; the user then drags handles to adjust. Approximate by design - fallback is no pre-selection (overlay opens with no active selection).

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`openPdfTextSelection()` is the single overlay entry).
- [ ] Strategic §6.2 resolved by design (OCR block-box hit-test primary).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfSelectionCoordinateMapper.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` | Modified | ≤ 1040 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt` | Modified | ≤ 500 |

> No new Hilt module - `PdfSelectionCoordinateMapper` is plain (constructed by `PdfTextSelectionManager` / `PdfViewerManager`, no injection). No layout edits.

---

## Steps

### Step 02.1 - Capture the long-press point on the PDF surface

**Files:** `PlayerGestureSetupManager.kt`, `PdfViewerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `setOnLongClickListener` carries no coordinates. Track the last `ACTION_DOWN` point on the PDF `photoView`: in `configurePhotoViewGestures`, set a lightweight touch observer that records the latest down `x`/`y` (view coordinates) without consuming the event (return false / pass through to the attacher). Forward that point into `PdfViewerManager.handlePdfLongPress(x: Float, y: Float)` → `openPdfTextSelection(x, y)`. Default the coordinates to `null`-equivalent (e.g. `Float.NaN`) when unknown so the overlay opens without pre-selection.

**Verification:**

- `Grep` - `handlePdfLongPress(` signature in `PdfViewerManager.kt` now takes two `Float` params.
- `Grep` - `openPdfTextSelection(` is called with the captured coordinates.
- `Grep` - `PlayerGestureSetupManager.kt` stores a last-down point for the PDF photoView (e.g. `lastPdfDownX`).
- `Grep -n "Log\.d\("` on both modified files returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.2 - Coordinate mapper + pre-selection in the overlay

**Files:** `PdfSelectionCoordinateMapper.kt` (New), `PdfTextSelectionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `PdfSelectionCoordinateMapper` (object or class, no DI) with: (a) `fun viewToBitmap(photoViewMatrixValues, x, y): PointF?` converting a PDF `photoView` touch point to bitmap-pixel space using the PhotoView display matrix; (b) `fun charRangeForPoint(point: PointF, ocrBlocks: List<Rect-with-text-and-offset>): IntRange?` returning the character range (in the joined page text) of the word/block nearest the point, or `null`. The OCR block boxes come from the existing `TranslationManager` block-recognition path (`TranslatedTextBlock.boundingBox`), recognized on the same page bitmap; the joined page text and per-block start offsets must come from the same join used to fill the overlay TextView. Extend `PdfTextSelectionManager.enterTextSelectionMode` with optional `selectionPoint: PointF?`; after the overlay TextView text is set, if a range is resolved, call `android.text.Selection.setSelection(tvText.text as Spannable, start, end)` and request focus so native handles + floating Copy appear. If unresolved → leave unselected (current behavior).

**Verification:**

- `Glob` - `PdfSelectionCoordinateMapper.kt` exists.
- `Grep` - `class PdfSelectionCoordinateMapper` or `object PdfSelectionCoordinateMapper` matches once.
- `Grep` - `fun charRangeForPoint` and `fun viewToBitmap` both present.
- `Grep` - `enterTextSelectionMode` in `PdfTextSelectionManager.kt` accepts a `PointF` parameter.
- `Grep` - `Selection.setSelection(` present in `PdfTextSelectionManager.kt`.
- `Grep -n "Log\.d\("` on modified files returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.3 - Build gate

**Files:** -
**Depends on:** Steps 02.1-02.2

**Prompt for developer:**

> Run `/build` → standard debug (`a.ps1 dq`). PASS required. The mapping accuracy is verified on-device (manual) - this build gate only proves compilation + wiring.

**Verification:**

- `/build` standard debug exits 0.
- `Grep` - `TODO(phase-02)` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry for every file in Files Touched.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class `PdfSelectionCoordinateMapper`).

---

## Handoff Notes to Next Phase

PDF long-press now opens the overlay with the nearest word pre-selected when mapping succeeds. Accuracy is approximate (OCR block-box level); manual handle adjustment covers misses. Device-test gate confirms real-world accuracy.

---

## Rollback Plan

Revert phase commit(s). Phase 01 entry (overlay without pre-selection) remains intact and usable.
