# S0953 - Standalone PDF gesture parity (clean gaps)

**Ticket:** S0953
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-05
**Tier:** 2 - Easy

<!-- discovered by /spec-all S0952 - 2026-07-05 (owner-blessed split of the CLEAN parity gaps) -->

## Goal

Закрыть два подтверждённых CLEAN-пробела PDF-жестов из матрицы S0952 (Divergence 2 и 3), не трогая намеренные per-host различия (видео/аудио, §6 S0952). Каноничный референс - in-app player (`PlayerGestureSetupManager.configurePhotoViewGestures`):

1. `DocumentStandaloneActivity` не подключает single-tap (открытие ссылок, `handlePdfTap`) и long-press (выделение текста, `handlePdfLongPress`), хотя `PdfViewerManager` их экспонирует и in-app их использует.
2. Legacy `StandalonePlayerActivity` (через `StandaloneViewManager`) не подключает вертикальный swipe-листинг PDF (`handlePdfFling`) - его `photoView` шарится между IMAGE/GIF/PDF, поэтому нужен guard по текущему типу.

Out of scope (намеренно, per S0952 §6.3): legacy PDF zoom/tap/long-press полной параллельностью (относится к S0393 harvest); видео/аудио вокабуляр (закрыт как deliberate difference); Divergence 4 IMAGE zone-nav.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0952 (parent audit + matrix), S0951 (DocumentStandalone PDF fling, Part A), S0949 (shared PDF zoom contract), S0393 (legacy standalone removal/harvest).
- **Flavor scope:** both edits live in `src/main` gesture wiring; applies to all flavors, no `BuildConfig` specifics.

## Phase 1 - DocumentStandaloneActivity PDF tap + long-press

Wire the two missing PDF gestures on `binding.photoView`, mirroring `PlayerGestureSetupManager.configurePhotoViewGestures` (never `setOnTouchListener` alone - it replaces the PhotoView attacher and breaks pinch/pan; forward events to `attacher.onTouch`).

1. Add `lastPdfDownX`/`lastPdfDownY` float fields to the activity.
   - Verification: `Grep` finds both fields declared.
2. In `setupPdfButtons()`, after the existing `setOnSingleFlingListener`, add:
   - `setOnDoubleTapListener` whose `onSingleTapConfirmed` returns `pdfViewerManager.handlePdfTap(e.x, e.y)` and whose `onDoubleTap`/`onDoubleTapEvent` return `false` (pinch-to-zoom only, matches in-app).
   - `setOnTouchListener` that records `lastPdfDownX/Y` on `ACTION_DOWN` then forwards to `binding.photoView.attacher.onTouch(v, ev)`.
   - `setOnLongClickListener` returning `pdfViewerManager.handlePdfLongPress(lastPdfDownX, lastPdfDownY)`.
   - Verification: standard debug compiles; single-tap on a PDF link opens it; long-press opens text selection.
3. Imports: `android.view.GestureDetector`, `android.view.MotionEvent`.
   - Verification: no unresolved-symbol errors.

## Phase 2 - Legacy StandaloneViewManager PDF fling

Legacy `photoView` is shared IMAGE/GIF/PDF, so the fling must be guarded by the current media type (in-app reference guards on `currentFile?.type == MediaType.PDF`).

1. Add `private var currentMediaType: MediaType? = null` to `StandaloneViewManager`; set it at the top of `show(mediaFile, mediaType, ..)`.
   - Verification: `Grep` finds the field set in `show()`.
2. In `showPdf(mediaFile)`, after `pdfViewerManager.displayPdf(mediaFile)`, attach a `setOnSingleFlingListener` on `safeViews.photoView` whose lambda calls `pdfViewerManager.handlePdfFling(e1, e2, velocityX, velocityY)` only when `currentMediaType == MediaType.PDF`, else returns `false`.
   - Verification: standard debug compiles; vertical swipe on a legacy-host PDF pages; vertical swipe on a legacy-host image does NOT page (guard holds).
3. Import `com.github.chrisbanes.photoview.OnSingleFlingListener` if absent.
   - Verification: no unresolved-symbol errors.

## Verification (device)

- DocumentStandalone: open a PDF with a hyperlink -> single-tap opens the link; long-press -> text-selection overlay with the word under the finger pre-selected.
- DocumentStandalone: pinch still zooms, single fling still pages (S0951 unbroken).
- Legacy StandalonePlayerActivity: open a PDF -> vertical swipe pages; open an image in the same host -> vertical swipe pans (no paging).

## Implementation (v1, 2026-07-05)

- Phase 1 - `DocumentStandaloneActivity.setupPdfButtons()`: added `setOnDoubleTapListener` (single-tap -> `handlePdfTap`, double-tap returns false = pinch-only), an attacher-forwarding `setOnTouchListener` recording `lastPdfDownX/Y`, and `setOnLongClickListener` -> `handlePdfLongPress`. `@SuppressLint("ClickableViewAccessibility")` justified (touch forwarded to attacher; click semantics from native callbacks). Fields `lastPdfDownX/Y` + imports `GestureDetector`/`MotionEvent` added.
- Phase 2 - `StandaloneViewManager`: added `currentMediaType` (set in `show()`); `showPdf()` attaches a `setOnSingleFlingListener` gated on `currentMediaType == MediaType.PDF` -> `handlePdfFling`, so an image on the shared photoView is never paged.
- Build: `standard debug` BUILD SUCCESSFUL (52s). Scoped detekt gate PASS (no new findings among changed files).
- Debug probes present while BlockNeedUserTest: `S0953:` on standalone tap, standalone long-press, legacy fling.

Out of scope confirmed unchanged: video/audio vocabulary (deliberate per S0952 §6), legacy PDF zoom/tap/long-press (S0393), Divergence 4 IMAGE zone-nav.

## Last Audit

- **2026-07-07** - `/spec-test-device` on emulator-5554 (Android 17 / SDK 37, standard debug 2.60.7041.926-DEBUG). Status left BlockNeedUserTest (device run does not flip status).

### Manual / on-device

- [x] DocumentStandalone single-tap on PDF -> `handlePdfTap` - verified on-device 2026-07-07. Expected: single-tap fires tap handler (opens link when a link is hit). Actual: `S0953: standalone pdf single-tap -> handlePdfTap` (D) fired on the tap.
- [x] DocumentStandalone long-press on PDF -> text selection with word pre-selected - verified on-device 2026-07-07. Expected: long-press opens text-selection overlay. Actual: `pdfTextSelectionOverlay` + `tvPdfSelectableText` + Cancel appeared; `S0953: standalone pdf long-press -> handlePdfLongPress` (D) fired.
- [x] DocumentStandalone single fling still pages (S0951 unbroken) - verified on-device 2026-07-07. Expected: vertical swipe pages. Actual: page indicator 1/48 -> 2/48.
- [x] Legacy StandalonePlayerActivity PDF vertical swipe pages - verified on-device 2026-07-07. Expected: swipe pages + probe fires. Actual: `S0953: legacy pdf fling -> handlePdfFling` (D) fired; page 2/48 -> 3/48.
- [x] Legacy image vertical swipe pans, NO paging (guard holds) - verified on-device 2026-07-07. Expected: image swipe does not page, probe absent. Actual: `legacy pdf fling` probe did NOT fire on the image swipe (guard `currentMediaType != PDF`).
- [ ] DocumentStandalone pinch still zooms - not driven (mobile-mcp has no pinch primitive). Code-verified: `onDoubleTap`/`onDoubleTapEvent` return false and touch is forwarded to `attacher.onTouch`, so PhotoView pinch/pan stays intact.

## Revision History

- **2026-07-07** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 17)
  - Scenario: temp/S0953/mobile_test_scenario_20260707.md - PASS/FAIL/SKIPPED 5/0/1 - Errors in log: 0 (FMS process)

## Related

- S0952 - parent audit + gesture matrix (this is the owner-blessed CLEAN-gaps child).
- S0951 - DocumentStandalone PDF vertical-swipe paging (Part A).
- S0949 - shared PDF zoom-step contract.
- S0393 - legacy standalone removal/harvest (owns broader legacy parity).
