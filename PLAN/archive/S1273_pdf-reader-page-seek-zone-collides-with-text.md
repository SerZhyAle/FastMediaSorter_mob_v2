# S1273 - Paging a zoomed PDF is impossible, so paging attempts land on the text and start extraction

**Status:** Archived
**Priority:** 65
**Tactical plan:** `PLAN/S1273_pdf-reader-page-seek-zone-collides-with-text/INDEX.md`

## 0. Raw capture

Owner voice note, 2026-07-29, after a full day reading a 130 MB / 240-page PDF on a phone.
Audio and full transcript: `PLAN/S1273_pdf-reader-daily-use-friction/attachments/`.

Verbatim (RU), the two passages that belong together:

> "из неудобств промазываю постоянно место пролистывать на тексте и жду пока он достанет текст то есть может быть текст либо в другое место перенести либо сместить левее чтобы не попадать явно в нее пальцем места перемотки"

> "и все-таки не могу листать страницы пальцем и не сильно понимаю как это настроить только получается надо по стрелочкам бить"

## 1. The first reading of this ticket was wrong

The original §1 said "reading taps scrub the document" - that a reading touch triggers seeking. The transcript says the opposite: he aims at *paging*, lands on the *text*, and then waits while the app extracts text. Corrected 2026-07-29 after reading the full transcript rather than the single quoted line, and after tracing the gesture code.

## 2. What the code actually does

Traced through `PlayerGestureSetupManager.configurePhotoViewGestures` and `PdfViewerManager.handlePdfFling`.

- A PDF **single tap** only checks for a link (`handlePdfTap`). It does not page and does not seek. There is no seek hot zone over the page at all - the premise of the original ticket does not exist.
- A PDF **fling** pages the document: vertical for previous/next page, horizontal for a zoom step.
- A PDF **long press** starts text selection, which is the extraction he waits for.

`handlePdfFling` refuses to page in three cases (`PdfViewerManager.kt:897-923`):

1. `isScrollMode` is on - the RecyclerView owns vertical scrolling.
2. `photoView.scale > PDF_NAV_ZOOM_THRESHOLD` - a zoomed-in page pans instead of paging.
3. The gesture is not a fling - it must clear both a distance threshold and a velocity threshold.

Case 2 is the one that bites. Reading a dense 240-page document on a phone means zooming in; from that moment vertical swipe paging is switched off by design and the same drag pans the page. Case 3 removes the rest: a deliberate, slow swipe produces no fling, so nothing happens, and a swipe that begins with a pause is delivered as a long press - which is precisely "промазываю .. и жду пока он достанет текст".

So both complaints are one root cause: **while zoomed in there is no finger gesture that turns a page**, which forces him onto the arrow buttons - and that is where **S1275** met him.

### 2.1 Correction - none of the three cases ever reaches `handlePdfFling`

Established 2026-07-31 from the PhotoView 2.3.0 sources (`PhotoViewAttacher.java:178-191`, `CustomGestureDetector.java`), not inferred. `handlePdfFling` is wired through `PhotoView.setOnSingleFlingListener`, and the attacher refuses to call that listener before the app ever sees the event:

- `getScale() > DEFAULT_MIN_SCALE` (1.0f) returns false, so a zoomed page never reaches the app's fling handler. `PDF_NAV_ZOOM_THRESHOLD = 1.05f` in `handlePdfFling` is therefore dead code, not the guard it reads as.
- `e1.getPointerCount() > SINGLE_TOUCH` (1) returns false, so a two-finger gesture never reaches it either.
- `CustomGestureDetector` only emits a fling when velocity clears `ViewConfiguration.getScaledMinimumFlingVelocity()`, so a slow deliberate drag produces no callback at all.

Consequence for the tactical phase: all three halves of the decision in section 4.1 must be implemented on the raw `MotionEvent` stream, in the `PhotoView.setOnTouchListener` that already forwards to `attacher.onTouch`. Relaxing the thresholds inside `handlePdfFling` would change nothing observable - the method is unreachable in exactly the states this ticket is about. This is a mechanism correction only; the owner's decision is untouched, and section 3.1 of **S1274** already assigned the detector choice to the tactical phase.

## 3. Why his own two remedies would not work

He proposed moving the text, or shifting it left, so his finger stops hitting "the seek place". That was reasoning from an overlap that is not there - no zone overlays the page. Moving the text would change nothing, because the conflict is between *gestures* on the same pixels, not between two regions.

Saying so is the point of this ticket: implementing what he asked for would have cost a layout change and fixed nothing.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1274 (the same root cause and the same decision - one piece of work, not a dependency), S1275 (the arrow buttons he was forced onto; already Verified), S1276 (the whole-page text extraction he waits for after a mis-timed swipe), S0953 (Archived - earlier standalone PDF gesture parity).
- **UI placement contract:** no new control and no new screen - the change lives entirely in the PDF reader's gesture map. Two-finger vertical swipe pages a zoomed page, one finger still pans it, a slow drag pages an unzoomed page without needing a fling.
- **Setting gate:** unconditional, no setting. Decided as S1274's own half of the question in **S1274** section 3.1, and binding here because the two tickets are one piece of work.
- **Flavor scope:** flavors that build the document reader, that is `SUPPORT_DOCUMENTS = true` - standard, legacy, noLegal, vr. lite and photos ship no PDF reader, so there is nothing to gate there.
- **Localization:** no new user-visible strings.
- **Accessibility:** the arrow buttons remain the non-gesture path through a document, so the two-finger gesture is additive and never the only way to turn a page.
- **Validation level:** on-device on a phone with a long real PDF - two-finger paging while zoomed, one-finger panning while zoomed, and a slow unzoomed drag that clears no velocity threshold.

## 4. Open for the owner

The zoom guard is deliberate - a zoomed page must pan, or the document becomes unreadable. So the question is what should turn a page while zoomed in:

- A two-finger vertical swipe, leaving one finger free to pan.
- Edge strips (top/bottom or left/right band) that page even when zoomed.
- Keep the arrow buttons as the only zoomed-in paging, and treat **S1275** as the whole fix.
- Drop the velocity requirement so a slow, deliberate drag pages when *not* zoomed, which at least fixes the unzoomed case.

This needs his decision because each shape trades a different gesture away, and he is the one reading 240-page documents daily.

### 4.1 Decision - resolved (owner, 2026-07-29)

**Section 4 is closed.** One decision with two halves, both in scope:

- **A two-finger vertical swipe turns the page while zoomed in.** Two fingers leave one free for panning, so the deliberate zoom guard stays intact - a zoomed page still pans under one finger - rather than being traded away for paging.
- **The velocity requirement is dropped for the unzoomed case**, so a slow deliberate drag pages. This is the everyday complaint from the transcript: a slow swipe did nothing, and a swipe that started with a pause was taken as a long press and began text extraction.

Offered and **not** taken:

- Edge strips that page while zoomed.
- Arrows only, which would have made **S1275** the whole fix and closed **S1274** as a refusal.

The remaining half of the question - whether the gesture should be unconditional or hide behind a setting - was never asked here; it is S1274's own. It is answered in **S1274 section 3.1**: unconditional, no setting.

**S1274 is no longer blocked behind this ticket.** The two are one piece of work, and this shared decision is the record for both.

## 5. Related

- **S1274** - "cannot page with a finger" is the same root cause; that ticket and this one must be resolved together, not separately.
- **S1275** - the arrow buttons he is forced onto, already fixed.
- **S1276** - the whole-page extraction he waits for; word-level selection would shorten the wait but not stop the mis-gesture.
- **S0953** (Archived) - earlier standalone PDF gesture parity work.
