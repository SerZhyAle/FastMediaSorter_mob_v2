# S1274 - Pages cannot be turned by swiping, and the owner could not find a setting for it

**Status:** Archived
**Priority:** 60

## 0. Raw capture

Owner voice note, 2026-07-29, after a full day reading a 130 MB / 240-page PDF on a phone.
Audio and full transcript: `PLAN/S1273_pdf-reader-daily-use-friction/attachments/`.

Verbatim (RU):

> "и все-таки не могу листать страницы пальцем и не сильно понимаю как это настроить, только получается надо по стрелочкам бить, вроде бью по стрелочкам, окей"

## 1. Problem

Turning a page requires hitting an arrow button. The owner expected a finger swipe, could not make one work, and could not find a setting that enables it. Arrows were the only way through 240 pages.

The cause is settled. Swipe paging exists and is enabled; it is suppressed by the state he reads in. While a page is zoomed, `PdfViewerManager.handlePdfFling` deliberately refuses to page so the page can pan, and an unzoomed page turns only on a true fling - so a slow, deliberate drag does nothing, and a drag that begins with a pause is delivered as a long press and starts text extraction. The code trace is **S1273** section 2 and is not repeated here.

Correction, 2026-07-29: this section originally offered two candidate failure modes - paging does not exist at all, or it exists but is off by default or bound to an undiscovered gesture - and refused to pick between them. Neither was right. The gesture exists, it is on, and no setting governs it. "Could not find a setting" was read as a discoverability defect, when there is no setting to find and, per section 3.1, none is wanted.

## 2. What the code trace established

Traced in **S1273** section 2 over `PlayerGestureSetupManager.configurePhotoViewGestures` and `PdfViewerManager.handlePdfFling`. The findings that decide this ticket:

- A vertical fling pages the document and a horizontal fling is a zoom step, so the PDF reader is not a plain vertical scroller with no paging gesture.
- `handlePdfFling` declines to page in three states: `isScrollMode` on (the RecyclerView owns vertical scrolling), `photoView.scale` above `PDF_NAV_ZOOM_THRESHOLD` (a zoomed page pans instead), and a drag that fails the distance or velocity threshold.
- The zoom guard is the state a dense 240-page document forces him into, so for him the gesture is permanently off; the velocity threshold removes what is left.
- A single tap only resolves links and a long press starts text selection, which is the extraction he waits for after a mis-timed swipe.

One question from the original list stays open, but as a tactical-phase design input rather than a strategic unknown: what the sibling viewers in the same unified player already bind, so the new gesture lands as a player-wide convention and not a PDF-only one.

Amended 2026-07-31: **S1273** section 2.1 established from the PhotoView sources that `handlePdfFling` is unreachable in all three states this ticket cares about - zoomed, two-finger, and slow drag - because the attacher drops the single-fling callback before the app sees it. The gesture must therefore be detected on the raw `MotionEvent` stream. That is a mechanism finding, not a change of direction; section 3.1 stands as written.

## 3. Direction - decided

- Paging while zoomed: a two-finger vertical swipe, with one finger still panning the zoomed page.
- Paging while not zoomed: the velocity requirement is dropped, so a slow deliberate drag pages.
- Discoverability is not the fix, and no first-open hint belongs to this ticket - there is nothing to discover beyond the gesture itself.

Two options this section previously floated are closed rather than deleted, so they are not proposed again:

- "Swipe pages only at 1.0x" is the behaviour that already ships. It is the complaint, not the remedy.
- A reserved vertical-edge band that pages while zoomed was offered to the owner and not taken (**S1273** section 4.1).

### 3.1 Decision - resolved (owner, 2026-07-29)

**Section 3 is closed.** The shared half of the decision lives in **S1273 section 4.1** - S1273 and this ticket were answered as one, because they are one root cause. In short:

- A **two-finger vertical swipe** turns the page **while zoomed in**; one finger keeps panning a zoomed page, so the zoom guard is not traded away.
- The **velocity requirement is dropped when not zoomed**, so a slow deliberate drag pages instead of doing nothing or arriving as a long press.

Offered and **not** taken: edge strips that page while zoomed, and arrows-only - the latter would have made **S1275** the whole fix and closed this ticket as a refusal.

This ticket is **no longer blocked behind S1273**; they are now a single piece of work. Do not re-derive the gesture map here - amend S1273 section 4.1 if it ever changes.

**Second half, this ticket's own question: unconditional, no setting.** The gesture ships on for everyone, with no settings row to enable it. Reasons:

- While a page is zoomed, panning is a one-finger gesture, so a two-finger vertical drag collides with nothing. There is no conflict for a setting to arbitrate.
- The project already carries a large settings surface, and a page-turn gesture with no discoverability problem does not earn another row in it.
- A gesture that exists only when a setting is on is a gesture users never find - it would reproduce this ticket instead of closing it.

One implementation constraint follows, and it belongs to the tactical phase rather than to this decision: the two-finger vertical drag must be disambiguated from the start of a pinch-zoom - near-parallel travel of the two pointers with a low scale delta is a page turn, diverging travel with a growing scale delta is a zoom. How that threshold is drawn, and against which existing gesture detector, is a tactical-phase question.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1273 (the same root cause and the same decision - one piece of work, not a dependency), S1275 (the arrow buttons he was forced onto; already Verified), S1276 (the text extraction a mis-timed swipe triggers), S0953 (Archived - earlier standalone PDF gesture parity).
- **UI placement contract:** no new control, no new screen and no new settings row - the change is entirely inside the PDF reader's gesture map. Two-finger vertical swipe pages a zoomed page, one finger still pans it, a slow drag pages an unzoomed page.
- **Setting gate:** unconditional, no setting - see section 3.1 for the reasoning.
- **Flavor scope:** flavors that build the document reader, that is `SUPPORT_DOCUMENTS = true` - standard, legacy, noLegal, vr. lite and photos ship no PDF reader, so there is nothing to gate there.
- **Localization:** no new user-visible strings.
- **Accessibility:** the arrow buttons stay exactly as they are, so the non-gesture path through a document is unchanged and the new gesture is purely additive.
- **Validation level:** on-device on a phone with a long real PDF - two-finger paging while zoomed, one-finger panning while zoomed, and a slow unzoomed drag that clears no velocity threshold.

## 3.4 Where this ticket's behaviour lives

Implemented 2026-07-31 under **S1273**'s tactical plan, because both halves of the shared decision are one gesture map and one piece of code. Nothing further is planned here; this ticket's own audit decides its status.

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfPageSwipeDetector.kt` - new. Both claims carry an inline `// S1274:` marker: the two-finger zoomed page turn and the one-finger unzoomed drag with no velocity requirement.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt` - one detector per PhotoView surface, fed from the touch listener ahead of the attacher.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` - `isPageSwipeEnabled()` and `turnPage(next)`; `handlePdfFling` reduced to the horizontal zoom step.

## 4. Non-goals

- Changing the arrow buttons themselves - that is **S1275**.
- Anything about text selection - that is **S1276**.

## Last Audit

2026-07-31, review mode. The code landed in the same session under **S1273**'s tactical plan, so this is a read of the shipped tree rather than a fresh implementation pass. Each claim below is checked against source, not against the plan's intent.

Section 3.1 decision, half by half:

- **Two-finger vertical swipe turns the page while zoomed** - PASS. `PdfPageSwipeDetector.detectTwoFingerPageTurn` carries no scale gate at all, so it fires at any zoom.
- **Velocity requirement dropped when not zoomed** - PASS. `detectOneFingerPageTurn` decides on distance only, comparing travel against `ViewConfiguration.getScaledPagingTouchSlop()`; there is no velocity term anywhere in the class.
- **One finger still pans a zoomed page** - PASS. The one-finger branch returns early above `PAGE_PAN_SCALE_THRESHOLD`, and an unclaimed event is forwarded to the PhotoView attacher unchanged.
- **Pinch disambiguation, the tactical-phase constraint in 3.1** - PASS. The two-finger branch abandons page-turn candidacy for the rest of the gesture once the span between the pointers leaves `1f ± PINCH_SPAN_TOLERANCE`, which is the "near-parallel travel with a low scale delta" test that section asked for.
- **Unconditional, no setting** - PASS. No settings key, no manifest entry, nothing to regenerate under Rule 22.

Coverage: the gesture is installed on all three PDF hosts - `PlayerGestureSetupManager`, `DocumentStandaloneActivity`, `StandaloneViewManager` - through one shared `PdfPageSwipeDetector.install(..)`. The third gates on `currentMediaType == MediaType.PDF` because its PhotoView is shared with images and GIFs.

Not claimed: nothing here was exercised on a device. Section 3.3 sets the validation level as a phone with a long real PDF, so this ticket stays `BlockNeedUserTest` on the same run that verifies **S1273** - one gesture map, one test. The feature-inventory record was written once, under S1273, because both tickets deliver the same single capability.

## 5. Related

- **S1273** - the same root cause in the same reader; the gesture map was decided with that ticket, not after it.
- **S0953** (Archived) - previous gesture parity work on the standalone PDF viewer.
