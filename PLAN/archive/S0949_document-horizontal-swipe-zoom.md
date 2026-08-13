# Draft: S0949 - Document horizontal swipe zoom in player and standalone

**Ticket:** S0949
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-05
**Tier:** 3 - Moderate
**Source:** User request 2026-07-05 (`/spec-draft`)

> Draft inbox - raw request captured plus initial codebase analysis. Not yet approved or tactical.

## 0. Captured request

**Captured:** 2026-07-05

**Text:**

/spec-draft документы PDF и другие. Standalone и обычный плеер. Найти возможность задействовать жесты листания вправо-влево дла увеличения-уменьшения зума с шагом от x0.3 до x10

**Attachments:** none.

## 1. Current state

- PDF already has real zoom controls on both surfaces:
  - `DocumentStandaloneActivity` uses `btnPdfZoomIn` / `btnPdfZoomOut` and directly calls `binding.photoView.setScale(...)`
  - the in-app player uses the same `PdfViewerManager` and the same `PhotoView`-based PDF surface
- The in-app player already uses horizontal swipes for document navigation:
  - PDF: swipe left/right = next/previous page (owner-observed 2026-07-05: horizontal PDF swipe is effectively non-functional today, so repurposing it for zoom does not remove a working gesture; file next/prev already lives on two on-screen buttons)
  - EPUB: swipe left/right = next/previous chapter
  - Office / other files: swipe left/right = next/previous file
- The in-app player also suppresses horizontal page swipes when the left-edge gesture overlay is enabled.
- `PdfViewerManager` already has PDF-specific gesture routing and zoom-aware guards:
  - page-fling navigation is disabled when the PDF is in scroll mode
  - page-fling navigation is also disabled when `photoView.scale > 1.05f`
- Other document viewers do not currently share one common "true zoom" contract:
  - EPUB already uses horizontal swipe for font-size changes
  - TXT already uses horizontal swipe for font-size changes
  - Office documents go through a separate internal/external viewer path
- Standalone document viewing is split:
  - active specialized host: `DocumentStandaloneActivity`
  - deprecated legacy host still present: `StandalonePlayerActivity` via `StandaloneViewManager`

## 2. Problem

Владелец хочет единый жест вправо-влево для увеличения/уменьшения масштаба документов в обычном и standalone-плеере. Но сейчас этот же жест уже несёт другой смысл:

- в обычном плеере он листает PDF/EPUB или соседние файлы
- в TXT/EPUB он уже меняет размер текста
- при включённом left-edge gesture overlay часть горизонтальных свайпов уже имеет отдельный конфликтующий слой

Поэтому задача не сводится к простой подмене обработчика. Нужен новый продуктовый контракт: где именно горизонтальный свайп означает zoom, а где он остаётся навигацией.

## 3. Initial feasibility

### 3.1 PDF

- Реализация выглядит реалистичной.
- На обеих поверхностях уже есть `PhotoView` и рабочий zoom API через `setScale(...)`.
- В standalone уже существуют отдельные zoom-кнопки, значит жест можно привязать к уже понятной операции.
- В обычном плеере лучше не дублировать математику по активити, а вынести общий шаг zoom в документный helper / manager.

### 3.2 EPUB and TXT

- Здесь сейчас не "zoom страницы", а "font size adjustment".
- Горизонтальный свайп уже занят именно этим поведением.
- Если запрос "PDF и другие документы" должен включать EPUB/TXT, надо отдельно решить:
  - считать ли это тем же самым UX-контрактом
  - или PDF получает true zoom, а EPUB/TXT сохраняют text-size gesture как отдельную семантику

### 3.3 Office documents

- Для Office нет подтверждённого общего zoom API, аналогичного PDF `PhotoView`.
- Внутренний noLegal-viewer и внешний fallback живут отдельно.
- Поэтому "другие документы" нельзя автоматически обещать как единый технический слой вместе с PDF без дополнительного research.

### 3.4 Standalone coverage

- Основной современный standalone путь - `DocumentStandaloneActivity`.
- Пока legacy `StandalonePlayerActivity` ещё не удалён, поведение лучше не оставлять расходящимся, если этот маршрут всё ещё может открываться как fallback.

## 4. Main conflicts and decisions (resolved 2026-07-05)

1. Что происходит с horizontal swipe в обычном плеере?
   - **Resolved: горизонтальный свайп = zoom (модель TXT font-size).** Владелец: горизонтальный свайп влево-вправо для PDF сейчас фактически не работает, а навигация по файлам вперёд/назад уже вынесена на две экранные кнопки. Поэтому горизонтальный свайп целиком отдаётся под zoom in/out - ровно как TXT уже использует его для размера шрифта. Конфликта с навигацией нет (свайп-навигации на этой оси нет).
2. Что означает "от x0.3 до x10"?
   - **Resolved: диапазон масштаба 0.3x..10x.** 0.3x - минимум, 10x - максимум; каждый свайп = фиксированный шаг внутри границ, clamp по краям.
3. Допустим ли zoom ниже текущего fit-scale?
   - **Resolved: да (следует из явного запроса x0.3 < fit-scale).** Нижняя граница 0.3x лежит ниже baseline `~1x`, поэтому minimum-scale PhotoView надо опустить до 0.3x, а порог "is zoomed" (сейчас `scale > 1.05f`) - пересмотреть, чтобы zoom-out ниже fit не считался навигационным состоянием.
4. Должен ли scroll mode для PDF поддерживать жест?
   - **Resolved: нет.** Горизонтальный zoom-свайп конфликтует с вертикальным RecyclerView reading flow. В scroll mode zoom остаётся на pinch/кнопках.
5. Что входит в "другие документы"?
   - **Resolved: PDF (v1) + Office (отдельный research).** EPUB/TXT НЕ трогаем - их горизонтальный свайп остаётся font-size gesture. PDF - основной v1 (zoom-поверхность уже есть на обоих хостах). Office владелец тоже хочет, но общего zoom API как у PDF PhotoView нет - требуется отдельный research zoom-seam перед реализацией.

### Quiz decisions (2026-07-05)

- Свайп-семантика -> горизонтальный свайп = zoom (модель TXT font-size); навигация по файлам на кнопках. Владелец: PDF horizontal swipe сейчас не работает, конфликта нет.
- "x0.3..x10" -> диапазон 0.3x..10x с фиксированным шагом на свайп, clamp по границам.
- Zoom ниже fit-scale -> да (x0.3 явно < fit); опустить min-scale, пересмотреть порог isZoomed 1.05.
- Scroll mode -> не поддерживать жест (конфликт с вертикальным reading flow); zoom через pinch/кнопки.
- Охват -> PDF в v1 + Office отдельным research (нет общего zoom API); EPUB/TXT сохраняют font-size gesture.

## 5. Rough direction

- Best v1 candidate: start with PDF only, because the zoom surface already exists in both player families.
- Reuse one shared zoom-step contract for:
  - in-app player
  - `DocumentStandaloneActivity`
  - legacy standalone path if it still remains reachable
- Keep pinch and on-screen buttons as fallback controls even if swipe zoom is added.
- Treat EPUB/TXT as a separate owner decision:
  - either keep current font-size gestures unchanged
  - or explicitly redefine them as part of a broader "document scale" UX family
- Treat Office as separate feasibility work unless a shared internal zoom seam is proven.

## 6. Related

- S0301 - Office document viewer family and fallback behavior
- S0393 - standalone host split / legacy standalone parity context
- S0927 - horizontal page-swipe suppression when the left-edge gesture overlay is enabled

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0301 (Office viewer - deferred Office zoom research), S0393 (standalone host split), S0927 (left-edge gesture suppression), S0951 (standalone PDF vertical-swipe paging - same fling path), S0952 (cross-host gesture parity audit).
- **Delegated by user - /spec-all auto-approval:** scope limited to PDF v1 gesture/zoom on the shared PhotoView surface; EPUB/TXT font-size gestures untouched; Office zoom is separate research.

## 7. Implementation (v1 - PDF, 2026-07-05)

Delivered against the §4 resolved decisions, PDF only.

- Shared zoom-step contract in `PdfViewerManager` (`stepPdfZoom(zoomIn)`): one multiplicative step (factor 1.5x) clamped to 0.3x..10x. Drives both the horizontal-swipe gesture and the standalone on-screen zoom buttons, so both player hosts share one range.
- Widened the shared PhotoView zoom band to 0.3x..10x via `setScaleLevels(0.3, 2.0, 10)` in the manager `init` (was the PhotoView default 1x..3x). Applies to the in-app player and `DocumentStandaloneActivity` because both construct the same manager against the same PhotoView.
- `handlePdfFling`: horizontal-dominant swipe now zooms (right = in, left = out) instead of returning unhandled; vertical swipe still pages. Not applied in scroll mode. Zoom-out below fit (down to 0.3x) works because the single-fling gate fires at scale <= fit.
- Standalone zoom buttons routed through `stepPdfZoom` (were direct `setScale(scale * 1.25f)`), unifying the step + clamp.

### Known v1 limitation (owner-visible)

- PhotoView 2.3.0's `OnSingleFlingListener` only fires while `scale <= fit` (its `onFling` returns false above `DEFAULT_MIN_SCALE = 1.0f`; the pre-existing `scale > 1.05f` page-nav guard corroborates this). So repeated horizontal swipes cannot walk the whole range up to 10x purely by swiping - swipe covers the fit band (incl. zoom-out to 0.3x and one step past fit); pinch and the on-screen buttons reach deep zoom. Full-range swipe would need a custom gesture-interception layer above the attacher (S0952 audit scope). Owner decision 2 ("each swipe = fixed step within bounds") is met within the swipe-reachable band; deep zoom is button/pinch-driven. Flag for owner if full-range swipe is required.

## 8. Verification

- Compile: `.\a.ps1 dq` PASS.
- On-device (BlockNeedUserTest): open a PDF in the in-app player and in the document standalone host; horizontal swipe right zooms in, left zooms out; swipe-out shrinks below fit toward 0.3x; pinch/buttons reach up to 10x; vertical swipe still pages; scroll mode ignores the horizontal gesture. Probe: `Timber.d("S0949: pdf horizontal swipe zoom ..")`.

## Last Audit

### Manual device test - 2026-07-07 (emulator-5554, API 37, x86_64, 1080x2280 @440dpi, standard-debug)

**Verdict: PASS** - every driveable acceptance holds on both hosts; only pinch was not driveable (harness has no pinch primitive) and is code-verified.

- In-app player, horizontal swipe right: expected zoom in + probe `in=true`; actual page grew + probe fired. PASS.
- In-app player, horizontal swipe left: expected zoom out below fit + probe `in=false`; actual page shrank below fit (page framed by wide black margins) + probe fired. PASS.
- In-app player, vertical swipe: expected page navigation, no zoom probe; actual page indicator advanced 1/48 -> 2/48, no probe. PASS.
- In-app player, zoom-in button: expected deep zoom via shared `stepPdfZoom`; actual reached deep zoom (single word filled the viewport). PASS.
- In-app player, scroll mode: expected horizontal swipe ignored (RecyclerView owns scroll); actual no probe fired and no zoom. PASS.
- Standalone host (`.StandaloneDocsPlayer` alias enabled via System media handler, `content://media/external/downloads/60` VIEW): expected same PdfViewerManager PDF surface; actual PDF loaded in `DocumentStandaloneActivity` with the shared PDF controls. PASS.
- Standalone, horizontal swipe right / left: expected zoom in / zoom out below fit + probes `in=true` / `in=false`; actual both fired and page grew / shrank below fit. PASS.
- Standalone, zoom-in button: expected deep zoom via shared `stepPdfZoom`; actual reached deep zoom. PASS.
- Pinch to 10x: not driveable (mobile-mcp has no pinch primitive). Code-verified - PhotoView native pinch preserved through the attacher, band widened to 0.3x..10x via `setScaleLevels(0.3, 2.0, 10.0)`; the button path (same `stepPdfZoom` contract) reached deep zoom on both hosts, matching the §7 known-limitation note (deep zoom is button/pinch-driven).

Probe log (4 entries, PID 2639): 2x in-app + 2x standalone, `in=false`/`in=true` each. Evidence under `temp/S0949/` (13 screenshots + `S0949_probe_log.txt`).
