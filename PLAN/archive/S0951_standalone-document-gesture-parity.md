# Draft: S0951 - Standalone document gesture parity with in-app player

**Ticket:** S0951
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-05
**Tier:** 3 - Moderate
**Source:** User request 2026-07-05 (`/spec-draft`)

> Draft inbox - raw request captured plus initial codebase analysis. Not yet approved or tactical.

## 0. Captured request

**Captured:** 2026-07-05

**Text:**

/spec-draft небольшой баг - в standalone плеере документов не работают жесты вверх-вниз для листания страниц, ка кэто делает плеер в программе. Все жесты у всех плееров должны быть одинаковые - всё проверить и починить

**Attachments:** none.

## 1. Confirmed current state

- The specialized standalone document host is `DocumentStandaloneActivity`.
- That host wires:
  - document command buttons
  - PDF / EPUB navigation buttons
  - keyboard / D-pad paging via `StandaloneKeyboardManager`
  - translator / OCR / search / fullscreen callbacks
- But `DocumentStandaloneActivity` does not wire the same touch gesture stack as the in-app `PlayerActivity`.
- In the in-app player, PDF page swipes are handled through:
  - `PlayerGestureSetupManager.configurePhotoViewGestures()`
  - `photoView.setOnSingleFlingListener(...)`
  - `PdfViewerManager.handlePdfFling(...)`
- That path gives vertical swipe page navigation for PDF in the app player.
- No equivalent `PhotoView` fling wiring is present in `DocumentStandaloneActivity`.

## 2. Confirmed symptom

Черновой кодовый анализ подтверждает, что reported bug выглядит реальным:

- в обычном плеере PDF vertical fling routed into `PdfViewerManager.handlePdfFling()`
- в standalone document host есть only button and keyboard wiring for PDF / EPUB navigation
- touch parity layer for document surfaces there is missing

То есть проблема похожа не на device-specific сбой, а на architectural parity gap: standalone document host не подключён к тому же gesture contract.

## 3. Why this is broader than one bug

Фраза владельца "все жесты у всех плееров должны быть одинаковые" расширяет объём сильнее, чем точечный PDF bugfix.

Сейчас в проекте gesture behavior already differs by host and by media family:

- in-app PDF:
  - vertical swipe -> page navigation
  - long press -> text selection
  - single tap -> link/open or touch-zone handling
- standalone documents:
  - button/keyboard parity partly present
  - touch parity incomplete
- EPUB:
  - own swipe detector already exists for chapter/navigation/font-size behavior
- text viewer:
  - horizontal swipe already changes font size
- video standalone:
  - separate `StandaloneVideoTouchDelegate`
- photo/video standalone:
  - separate host and gesture model

So this request can be split conceptually into:

- **A. confirmed bug:** standalone document PDF vertical page swipes missing
- **B. parity audit:** define which gestures must be identical across which player hosts

## 4. Initial analysis

### 4.1 PDF standalone bug

- Strongly confirmed by code structure.
- The standalone document host exposes PDF zoom/page buttons but does not attach the in-app PhotoView fling listeners.
- Best first fix candidate:
  - port or extract the PDF touch wiring used by `PlayerGestureSetupManager`
  - reuse `PdfViewerManager.handlePdfFling()` instead of inventing a separate standalone-only gesture path

### 4.2 EPUB / other document surfaces

- Not yet confirmed as the same specific bug.
- EPUB in the app already has a separate gesture detector and does not mirror PDF's exact gesture contract.
- So "up/down page swipes like PDF" cannot be assumed for every document type without a product decision.

### 4.3 Cross-player parity request

- The owner wants gesture uniformity, but the project currently has several intentionally separate gesture subsystems.
- A parity pass needs a matrix, not ad hoc fixes:
  - in-app player
  - standalone document host
  - standalone photo/video host
  - standalone audio host
  - standalone text host
  - legacy `StandalonePlayerActivity` if it still remains reachable

## 5. Likely root cause

- `DocumentStandaloneActivity` was built as a trimmed specialized host and already imported several parity pieces from the legacy standalone/player family:
  - keyboard paging
  - action-mode augmentation
  - translator/search/document controls
- But touch gesture parity was not brought over with those ports.
- Result:
  - visible document controls exist
  - keyboard navigation exists
  - touch navigation remains incomplete

## 6. Open points

1. Does "all players" mean:
   - all standalone hosts + the in-app player
   - only document players
   - all media families regardless of type
2. For parity, which behavior is canonical when hosts currently differ:
   - in-app player becomes the source of truth
   - standalone keeps some host-specific exceptions
3. Should this ticket family first land the confirmed PDF standalone bugfix, then audit wider parity separately?
4. Is `StandalonePlayerActivity` still expected to mirror the same behavior until full removal, or is `DocumentStandaloneActivity` alone the supported standalone document target now?

## 7. Rough direction

- Treat the missing standalone document page-swipe handling as a real bug.
- Start with PDF touch parity because the in-app implementation already exists and the gap is directly observable in code.
- After that, audit and document the gesture matrix across hosts instead of assuming all current gesture differences are accidental.
- Prefer extracting shared document-gesture wiring over cloning the in-app code into each host.

## 8. Related

- S0393 - standalone host split and parity harvesting from the legacy standalone activity
- S0920 - recent standalone player UI parity work
- S0949 - adjacent draft about document horizontal swipe zoom in player and standalone
- S0952 - Part B (cross-host gesture parity audit + matrix), split out and parked as owner-gated

## 9. Implementation - Part A (2026-07-05)

Scope narrowed to the confirmed PDF standalone bug (section 4.1). The broad parity audit (section 3 / section 4.3 "all players") is split to S0952 as owner-gated and is not part of this ticket.

### 9.1 What changed

- `DocumentStandaloneActivity.setupPdfButtons()` now attaches `binding.photoView.setOnSingleFlingListener(...)`, routing every fling into its own `pdfViewerManager.handlePdfFling(e1, e2, velocityX, velocityY)` - the exact same handler the in-app player calls from `PlayerGestureSetupManager.configurePhotoViewGestures()`.
- Added import `com.github.chrisbanes.photoview.OnSingleFlingListener`.

### 9.2 Why this is the correct minimal fix

- `PdfViewerManager.handlePdfFling()` is fully self-contained: it owns the guards (renderer active, scroll-mode off, `photoView.scale <= 1.05`), computes the dominant-vertical swipe, and calls `showNextPage()` / `showPreviousPage()`. No standalone-specific gesture math is introduced - the standalone host reuses the in-app contract verbatim.
- `setOnSingleFlingListener` uses PhotoView's native callback API, so it does not replace the attacher's `OnTouchListener` (which would break pinch/pan/double-tap) - the same constraint documented in `PlayerGestureSetupManager`.
- The existing zoom buttons and keyboard paging are untouched; the swipe is additive.

### 9.3 Verification

- Compile/build: standard debug (file lives in `src/main`, all flavors compile it).
- Device (BlockNeedUserTest): open a multi-page PDF in the standalone document host, swipe up = next page, swipe down = previous page. Confirm the `S0951` probe fires (`S0951: standalone pdf fling -> handlePdfFling`), that scroll-mode and zoomed states still suppress paging (parity with the in-app player), and that pinch/pan/zoom buttons remain intact.

## Last Audit

### Manual / on-device

Device run 2026-07-06 (emulator-5554, Android 17 / API 37, standard-debug 2.60.7041.926). Fixture: test_doc_romcom.pdf (48 pages) opened via content:// into DocumentStandaloneActivity. Scenario + evidence: temp/S0951/mobile_test_scenario_20260706_2348.md.

- [x] Vertical swipe UP pages forward (1 -> 2 of 48); probe `S0951: standalone pdf fling -> handlePdfFling` fires - verified on-device 2026-07-06
- [x] Vertical swipe DOWN pages back (2 -> 1 of 48); probe fires - verified on-device 2026-07-06
- [x] Zoomed (scale>1.05 via zoom-in button) suppresses paging - page stays 1/48; un-zoom restores paging (1 -> 2) - verified on-device 2026-07-06
- [x] Zoom in/out buttons intact and additive fling does not replace the PhotoView attacher - verified on-device 2026-07-06
- [ ] Scroll-mode (`isScrollMode`) suppresses paging - NOT exercised on-device (no scroll-mode toggle reached); guard verified by code read only
- [ ] Pinch-to-zoom not driven directly (mobile-mcp lacks a pinch primitive); attacher preservation confirms path intact by construction

Note: the standalone host loads external documents from content:// URIs (real file-manager / share contract); a raw file:// VIEW intent is rejected by `NetworkFileManager.prepareFileForRead` (`Unsupported protocol`) - out of scope for S0951, surfaced as an owner-triage candidate in the scenario file.

## Revision History

- **2026-07-06** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 17/API 37)
  - Scenario: temp/S0951/mobile_test_scenario_20260706_2348.md - PASS/FAIL/SKIPPED 5/0/1 (scroll-mode not exercised) - Errors in log: 0 in S0951 flow (1 pre-flow file:// rig error, ignored)
