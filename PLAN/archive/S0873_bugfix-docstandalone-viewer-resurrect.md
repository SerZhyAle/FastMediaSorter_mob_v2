# Спецификация (compact bugfix): S0873 - DocumentStandaloneActivity - type-gated release воскрешает PdfRenderer/WebView

**Ticket:** S0873
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1 (2026-07-02, dedicated skeptic; every step verified, none refuted). Mechanics: releaseActiveViewer() (:792-803) gates on resolvedType and runs BEFORE resolvedType reassignment (:709-710) - releases only the outgoing type, cannot cancel an in-flight load. displayPdf (PdfViewerManager.kt:316-353) launches an UNTRACKED coroutine (Job discarded; close() cancels only the unrelated pageRenderJob :689-690) which after delay(50)+IO unconditionally re-creates pdfParcelFileDescriptor + PdfRenderer (:352-353), zero generation/cancellation check. EPUB mirror: destroyAndClear() nulls webView (EpubWebViewLifecycle.kt:122) but stale showChapter coroutine re-creates via getOrCreate() miss (:29-43, EpubViewerManager :480). onDestroy (:786-789) calls the SAME type-gated release -> resurrected off-type resource never released even at final teardown; BaseActivity.onDestroy has no registry sweep. Trigger user-reachable: host supports {PDF, EPUB, OFFICE} (:681), StandaloneFolderPagingManager filters only by type-in-supported with no homogeneity constraint (:76-77) - any folder with a.pdf + b.epub gives a mixed-type pageNext via on-screen button. Same untracked-coroutine root-cause theme as S0854/S0865. Fix shape: track the load Job per viewer and cancel in close()/on type switch, or generation-check before assigning renderer/PFD/WebView; make onDestroy release ALL initialized viewers, not the active type only.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt:792** - Type-gated releaseActiveViewer() lets an in-flight async load of the previous document type resurrect PdfRenderer/PFD/WebView that no teardown path ever releases
  - Evidence: releaseActiveViewer() releases only the viewer of the current resolvedType: `when (resolvedType) { MediaType.PDF -> pdfViewerManager.close(); MediaType.EPUB -> epubViewerManager.release(); MediaType.OFFICE_DOCUMENT -> if (officeViewerHostDelegate.isInitialized()) { officeViewerHost.release() }; else -> Unit }` (lines 792-803), and the paging swap runs it before switching type: `if (lastShownPath != null) releaseActiveViewer(); resolvedType = type; displayDocument(file, type)` (lines 709-711). The viewers' display entry points are unguarded async coroutines on lifecycleScope: PdfViewerManager.displayPdf launches `coroutineScope.launch(Dispatchers.Main) { kotlinx.coroutines.delay(50); ... pdfParcelFileDescriptor = ParcelFileDescriptor.open(file, ...); pdfRenderer = PdfRenderer(pdfParcelFileDescriptor!!)` (PdfViewerManager.kt:316-353) with no cancellation or current-file generation check; EpubViewerManager.displayEpub likewise delays 50ms then getOrCreateWebView() re-creates the WebView (EpubViewerManager.kt:333-335, 480; EpubWebViewLifecycle.getOrCreate():29-43); openOfficeInternally calls `officeViewerHost.open(...)` only after suspending prepareFileForRead (lines 750-755), so at swap time officeViewerHostDelegate.isInitialized() is still false and the release is skipped. CONCRETE PATH: folder-paged mixed-type docs (host supports PDF+EPUB+OFFICE neighbours, line 681) - user opens a PDF and taps Next onto an EPUB neighbour within the load window (guaranteed >=50ms + IO prepare + renderer open; seconds for SMB/cloud). releaseActiveViewer() runs while pdfRenderer is still null (no-op), resolvedType becomes EPUB, then the stale coroutine resumes: opens a new ParcelFileDescriptor + PdfRenderer and renders a 2x-screen ARGB_8888 bitmap (up to 2560px, ~25MB) into photoView over the EPUB. Neither a later swap nor onDestroy calls pdfViewerManager.close() again (resolvedType != PDF), so the native renderer, fd (CloseGuard 'PFD leaked') and bitmap outlive teardown. Symmetric: EPUB->PDF resurrects a destroyed WebView; noLegal Office->other leaves an undestroyed office WebView with officeDocumentViewerContainer visible. Contrast the family contract: the unified host releases EVERY initialized manager unconditionally - `if (activity._epubViewerManager != null) activity.epubViewerManager.release(); if (activity._officeDocumentViewerManager != null) ...release(); if (activity._pdfViewerManager != null) activity.pdfViewerManager.close()` (PlayerLifecycleManager.kt:247-253); StandaloneViewManager.release() does `_pdfViewerManager?.close(); _epubViewerManager?.release(); _textViewerManager?.release()` (StandaloneViewManager.kt:328-333).
  - Fix hint: Make onDestroy/releaseActiveViewer release every initialized viewer (nullable backing or lazy-delegate isInitialized() checks, as already done for officeViewerHostDelegate), and/or add a load-generation guard inside displayPdf/displayEpub/openOfficeInternally that aborts a stale load after each suspension point.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

DocumentStandaloneActivity - type-gated release воскрешает PdfRenderer/WebView. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

- `releaseActiveViewer()` был type-gated по `resolvedType` и релизил только viewer текущего типа. На folder-paging он вызывается ДО переключения `resolvedType` и не может отменить in-flight загрузку предыдущего типа.
- Точки входа viewer'ов - untracked async-корутины на `lifecycleScope`: `PdfViewerManager.displayPdf` после `delay(50)`+IO безусловно пере-создаёт `pdfParcelFileDescriptor` + `PdfRenderer` без generation/cancellation-проверки (`close()` отменяет лишь `pageRenderJob`, не эту корутину). EPUB-зеркало: `destroyAndClear()` обнуляет `webView`, но stale `showChapter`-корутина пере-создаёт его через `getOrCreate()`.
- `onDestroy` вызывает тот же type-gated release -> воскрешённый off-type ресурс не релизится даже при финальном teardown: `CloseGuard 'PFD leaked'`, открытый fd, нативный `PdfRenderer` и ~25MB ARGB_8888 bitmap переживают Activity.
- Триггер user-reachable: host поддерживает {PDF, EPUB, OFFICE}; folder-paging фильтрует соседей только по type-in-supported (без homogeneity-констрейнта) - папка с `a.pdf` + `b.epub` даёт mixed-type pageNext по кнопке.

---

## 3. Исправление

Single-file fix in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt`, mirroring the unified-host family contract (`PlayerLifecycleManager` / `StandaloneViewManager`).

1. Name the PDF and EPUB lazy delegates (`pdfViewerManagerDelegate`, `epubViewerManagerDelegate`) like the existing `officeViewerHostDelegate`, so their `isInitialized()` is checkable without forcing construction.
2. Rewrite `releaseActiveViewer()` to drop the `resolvedType` gate and release EVERY initialized viewer: `if (delegate.isInitialized()) viewer.close()/release()`.
   - `PdfViewerManager.close()` runs `closePdfRenderer()` + recycles the page bitmap; `EpubViewerManager.release()` destroys the WebView - so an off-type resource resurrected by a stale load is freed at the next swap and at `onDestroy`.
   - Verification: PDF -> EPUB page then teardown -> `pdfViewerManagerDelegate.isInitialized()` is true -> `pdfViewerManager.close()` runs -> no `PFD leaked` CloseGuard.

Note: this converts the leak from "outlives teardown" to "released at teardown/next swap". A load-generation guard inside `displayPdf`/`displayEpub` (untracked-coroutine theme, S0854/S0865) would additionally prevent the transient off-type resurrection during the session; out of scope for this ticket.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` (standard Kotlin compile) - PASS.
- Static gates `.\a.ps1 fg` (neuroslop, pm-flags, listener, flavor, ticket-log) - PASS.
- Optional on-device (deferred, not a merge gate): open a folder holding a `.pdf` and a `.epub`; open the PDF, tap Next onto the EPUB within the load window; leave the screen and confirm no `PdfRenderer`/`PFD leaked` CloseGuard warning in logcat.

---

## Last Audit

**Date:** 2026-07-02
**Verdict:** Verified
**Method:** static - `compileStandardDebugKotlin` + scoped gates + resource-ownership inspection (CODE_AUDIT_PROTOCOL player/resource + lifecycle trigger). On-device leak regression optional, not a merge gate.

- Fix present in `DocumentStandaloneActivity`: `releaseActiveViewer()` now releases every initialized viewer (`pdfViewerManagerDelegate` / `epubViewerManagerDelegate` / `officeViewerHostDelegate` `isInitialized()` guards), no longer gated on `resolvedType`.
- Ownership reasoning:
  - `PdfViewerManager.close()` frees the native renderer + PFD (`closePdfRenderer()`) and recycles the bitmap; `EpubViewerManager.release()` destroys the WebView. So an off-type resource resurrected by a stale async load is reachable at the next swap and at `onDestroy`.
  - `resolvedType` is still used elsewhere (fullscreen checks, swap) - no dead symbol.
  - Named delegates preserve identical lazy (SYNCHRONIZED) construction semantics; only add an `isInitialized()` handle.
- Residual: the transient in-session resurrection (off-type render until teardown) is bounded by release-all; full prevention needs the load-generation guard (S0854/S0865 theme), tracked separately.

