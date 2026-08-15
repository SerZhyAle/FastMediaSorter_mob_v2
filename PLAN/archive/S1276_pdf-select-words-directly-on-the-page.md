# S1276 - Copying two words from a PDF means waiting for the whole page, then hunting inside it

**Status:** Archived
**Priority:** 55

<!-- auto-approved by /spec-all - 2026-07-29 -->

## 0. Raw capture

Owner voice note, 2026-07-29, after a full day reading a 130 MB / 240-page PDF on a phone.
Audio and full transcript: `PLAN/S1273_pdf-reader-daily-use-friction/attachments/`.

Verbatim (RU):

> "часто мне надо скопировать одно-два слова, предложения, а не всю страницу. То есть [на] смартфоне я могу с картинки из галереи, ну, со скриншота, зажать и вытянуть себе нужные там две строчки либо одно слово, а у тебя надо ждать пока всю страницу она выберет и потом выбирать из той страницы, что тебе надо"

And the owner's own scope relief, in the same breath:

> "то есть технически если это реализовать сложно, наверное, и кнопки текста достаточно"

## 1. Correction to the first draft: the gesture already exists

The first version of this ticket said selection is "a *mode*, entered from `btnSelectTextPdf` rather
than from a long-press on the page". That is wrong, and it matters, because it would have sent the
work at a problem that is already solved.

Long-press on the page is wired in both PDF hosts and already passes the touch point:

- `PlayerGestureSetupManager:462-463` - in the in-app player, a long-press on a `MediaType.PDF` file
  routes to `pdfViewerManager.handlePdfLongPress(lastPdfDownX, lastPdfDownY)`.
- `DocumentStandaloneActivity:659-660` - the standalone document viewer does the same.
- `PdfViewerManager.handlePdfLongPress` calls `openPdfTextSelection(x, y)`, which hands the point to
  `PdfTextSelectionManager.enterTextSelectionMode`, which calls `preselectWordAt`.

So the owner's gesture is implemented. What he is describing is what happens *after* he performs it.

## 2. The real mechanism behind "надо ждать"

Two costs sit between the long-press and a pair of selected words, and neither is the gesture.

**The page text is extracted whole, before anything is selectable.** `enterTextSelectionMode` shows a
progress row, awaits `extractPageText` for the entire page, and only then makes the overlay visible.
On API 35+ that is `PdfRenderer.Page.getTextContents()` and is fast; below 35 it is a full-page OCR
pass and is not.

**The word under the finger is located by a second, always-OCR pass.** `preselectWordAt` calls
`translationManager.recognizeTextBlocksForSelection(bitmap)` unconditionally - there is no API-level
branch in it. On API 35+ that means the fast native extraction is followed by a full-page OCR run
whose only purpose is to produce a bounding box for one word. `RecognitionBackend` loads
`DeliverableSet.OCR_ENGINES` native libraries to do it.

That second pass is the wait the owner is describing, and on his own device it is pure waste: the
same `getTextContents()` call that already produced the text also carries the geometry.

**Worse, on many builds the pre-selection silently does not happen at all.**
`recognizeTextBlocksForSelection` returns null when the OCR engines are not installed - they are an
on-demand deliverable, not bundled in every build. `preselectWordAt` then returns early, the overlay
opens with no selection, and the user is left exactly where the owner says he is left: looking at
the whole page, hunting for his two words.

## 3. Goal and scope

Make the long-press land on the pressed word without an OCR pass, wherever the platform already
knows where the words are.

### 3.1 In scope

- Word boxes derived from the native API 35+ text content instead of from OCR.
- Exact character offsets for the pre-selection, computed while the page text is assembled.
- The OCR path kept unchanged as the pre-API-35 route and as the fallback when native content is
  empty (a scanned PDF has no text layer).
- Honest feedback when neither route can pre-select, instead of an overlay that silently opens with
  nothing selected.

### 3.2 Out of scope

- Region-scoped or lazy extraction of the page text. The full-page extraction is fast on the native
  path, and on the OCR path the text is needed in full anyway for the overlay to be usable.
- Selecting directly on the rendered bitmap with native handles, without the text overlay. That is a
  different architecture, and the owner has pre-approved a cheaper answer.
- The other three frictions from the same voice note - S1273, S1274, S1275.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1273, S1274, S1275 (siblings from the same voice note), S0323 (introduced `PdfSelectionCoordinateMapper` and the long-press pre-selection), S0953 (gave the standalone document viewer the same PDF touch parity)
- **UI placement:** no new controls. The long-press gesture, the selection overlay and the TXT button all stay where they are; only what happens between the press and the handles changes.
- **Flavor scope:** `SUPPORT_DOCUMENTS` is true in `standard`, `noLegal`, `legacy` and `vr`, and false in `lite` and `photos` - read from `app_v2/build.gradle.kts` lines 318, 391, 419, 445, 474, 533 against the flavor blocks at 304, 338, 405, 431, 457, 487.
- **Device coverage:** the native path needs API 35 or newer; `legacy` runs down to API 23 and keeps the OCR path. Both branches must be exercised, so this is not a single-device check.
- **Verification:** emulator is sufficient. A PDF with a real text layer on an API 35+ image proves the native path; an API 26-30 image or a scanned PDF proves the OCR fallback still behaves.

## 4. Design decisions resolved from the codebase

- **The native text content is the source of both the text and the boxes.** Building the page string
  and the per-item boxes in one pass makes the character offsets exact. Today
  `PdfSelectionCoordinateMapper.charRangeForPoint` ends with `fullText.indexOf(word)`, which selects
  the *first* occurrence of the word on the page - press the third "the" and the handles jump to the
  first. That bug disappears rather than being fixed, because the offset is known when the string is
  assembled.
- **Coordinates need one more conversion than the OCR path.** OCR boxes are already in bitmap pixels;
  native bounds are in PDF page points. The bitmap is rendered from the same page, so the scale is
  `bitmapWidth / page.width` and `bitmapHeight / page.height` - both available at render time in
  `PdfViewerManager`.
- **The API accessor must be confirmed, not assumed.** The code already uses
  `page.getTextContents()` and reads `.text` from each item. Whether the same item exposes its
  geometry, and under what name, is a compileSdk-36 stub question and is the first verification
  predicate of the implementation, not a spec claim.
- **The OCR path is not removed.** Below API 35 there is no text layer to read, and a scanned PDF has
  none at any API level. The native route is an addition with a fallback, not a replacement.
- **Silence is the current failure mode and is worth ending.** When no route can resolve the pressed
  word the overlay should say so - the user has already waited, and an unexplained lack of selection
  reads as the app ignoring the gesture.
- **A content item is not a word, so it has to be cut into words.** The 2026-07-29 device run
  found `getTextContents()` returning one item for an entire 1884-character page, which made
  "select the item" mean "select the page" - the very complaint this ticket answers. Each item does
  carry one box per line it spans, so the split is: lines against those boxes (real newlines when the
  run has exactly as many as boxes, otherwise apportioned by box width), then words inside each line
  with x-spans interpolated from character counts. Interpolation is approximate for a proportional
  font, which is why the lookup falls back to the nearest word centre instead of demanding strict
  containment, and why the split lives in a platform-free function that the unit suite pins directly -
  the `@RequiresApi(35)` factory cannot be exercised under Robolectric at sdk 34.

## 5. Acceptance criteria

- On API 35+ with a text-layer PDF, a long-press pre-selects the pressed word and no OCR engine is
  loaded for it - provable from the absence of the `RecognitionBackend` load line in logcat.
- Pressing the third occurrence of a repeated word selects that occurrence, not the first.
- On API 34 or below the behaviour is unchanged: OCR runs, the nearest word is pre-selected.
- A scanned PDF on API 35+ falls back to the OCR path instead of opening with nothing selected.
- When the press point cannot be resolved to text at all, the overlay opens with a stated reason
  rather than in silence. Reachable trigger, established by the 2026-07-29 device run: the mapping
  from view to bitmap coordinates fails, or the page has neither a text layer nor an OCR engine. On a
  page that *does* have text the notice is unreachable by construction - both lookups fall back to the
  nearest word, so a range always exists - so verify this one on an unmappable press, never by hunting
  for an unrecognised word on a normal page.
- The TXT button keeps working with no point and no pre-selection.

## 6. The owner's fallback, on the record

> "то есть технически если это реализовать сложно, наверное, и кнопки текста достаточно"

The owner has pre-approved closing this cheap if the fix proves expensive. Section 2 makes that
unlikely - the expensive half of the current flow is work that does not need to happen - but if the
native geometry turns out to be unavailable on compileSdk 36, that fallback is the answer and this
ticket closes as such rather than growing an OCR-optimisation scope.

## 7. Risks

- **The native bounds may be per-item, not per-word.** `getTextContents()` returns content items,
  which can be a run of text rather than a single word. If an item spans a whole line, the
  pre-selection would select the line, which is closer to the owner's "две строчки" than to his "одно
  слово" but is still a behaviour change worth checking before it ships.
- **Rotated or non-uniformly scaled pages.** The scale factor assumes the bitmap is a straight render
  of the page. `PdfViewerManager` renders with `Page.RENDER_MODE_FOR_DISPLAY` and no transform today;
  a future rotation feature would break the mapping silently.

## 8. Related

- **S1273**, **S1274**, **S1275** - the other three frictions from the same voice note.
- **S0323** - added the long-press pre-selection and `PdfSelectionCoordinateMapper`.
- **S0953** - brought the standalone document viewer to PDF touch parity with the in-app player.

## Last Audit

Device test, 2026-07-29. emulator-5554 (tablet, API 37) and emulator-5556 (phone, API 33). No build,
install or git operation performed. Full evidence: `temp/S1276/device-test-evidence.md`.

**Verdict: FAIL.** The plumbing works - the native text layer is read, the second OCR pass is gone,
and the fallback behaves. What fails is the payload: the selection is not a word.

### Per-criterion

- **crit1 pressed word pre-selected (API 37) - FAIL, emulator-5554.** Expected the pressed word
  selected; actual the entire 1884-character page selected. Three long-presses on separated words
  produced a byte-identical overlay region (md5 `2F472FA46690382B14683B7E73D01C3F` over 9 600 054
  pixel bytes), so the press point does not influence the selection at all.
- **crit2 native probe, no OCR engine load - PASS, emulator-5554.** Expected
  `S1276: selection resolved from native layout=true` with no OCR engine line; actual exactly that,
  preceded by `S1276: native page text ready, layout=true`. No `RecognitionBackend`,
  `libtesseract.so` or `TesseractManager` line in the same window.
- **crit3 third occurrence, not the first - FAIL, emulator-5554.** Occurrence index resolved from the
  overlay `uiautomator` dump: `phone` appears exactly three times, at offsets 787, 1019 and 1264.
  Expected the 1264 instance selected and the 787 one not; actual both presses yield the identical
  whole-page selection, which contains all three.
- **crit4 API 33 pre-selects via OCR with native=false - BLOCKED, emulator-5556.** Expected
  `S1276: selection resolved from native layout=false`; actual no `S1276:` line at all. The APK
  installed on that device does not contain the S1276 code - dex symbol counts are 0 for
  `PdfNativeTextLayout` and 0 for the probe string, against 24 for the pre-existing
  `PdfTextSelectionManager` control. Identical `versionName` on both devices is not build identity,
  because the fast debug target reuses a pinned version string. Re-test after installing the current
  build on emulator-5556.
- **crit5 scanned PDF falls back to OCR (API 37) - PASS, emulator-5554.** Expected OCR fallback
  rather than an empty selection; actual `native layout=false`, Tesseract loaded, 375 characters
  extracted, and the line containing the pressed word pre-selected. The fallback selects at line
  granularity, which is closer to the ticket's goal than the native path currently manages.
- **crit6 "Could not find that word" notice - INCONCLUSIVE, emulator-5554.** Three presses aimed at
  low-confidence scan areas all resolved to a nearest block, so the notice never appeared and no
  trigger for it could be constructed from the UI.

### Root cause of crit1 and crit3

`PdfNativeTextLayout.from()` builds one `Item` per `PdfPageTextContent`, and on the tested page
`getTextContents()` returns a single content item covering the whole page. `charRangeForPoint` then
returns that one range for every point. Section 7 anticipated per-item rather than per-word bounds
but assumed the worst case was a line; the observed worst case is the entire page, which reproduces
the owner's original complaint rather than fixing it. Splitting items into word or line spans, using
the per-line boxes each item already carries, is the shape of the remaining work.

### Note on crit6 reachability

The notice looks unreachable on a build with OCR bundled in base. The native lookup returns null only
when `items` is empty, and the OCR lookup falls back to the nearest word by centre distance, so any
page with text always yields a range. The only null path is `recognizeTextBlocksForSelection`
returning null when the OCR engines are absent - but then `extractTextOcr` also returns blank page
text and `enterTextSelectionMode` shows `pdf_text_empty` instead, never
`pdf_text_selection_word_not_found`. Worth deciding whether that notice needs a reachable trigger or
whether the empty-text branch already covers the honest-feedback goal from section 3.1.
