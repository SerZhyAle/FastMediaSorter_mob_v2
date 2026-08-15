# S1327 - PDF zoom resets to fit on every page turn, so a zoomed read has to be re-zoomed per page

**Status:** Archived
**Tactical plan:** `PLAN/S1327_pdf-zoom-resets-on-every-page-turn/INDEX.md`
**Priority:** 45

## 0. Raw capture

Not an owner report. Found 2026-07-31 while tracing the PDF gesture map for **S1273**, and parked under CLAUDE.md 3.1 rather than folded into that ticket - S1273 decides *how a page is turned*, this decides *what survives the turn*.

Evidence, from the shipped code and the PhotoView 2.3.0 sources, not inferred:

- `PdfViewerManager.showPdfPage` pushes each rendered page with `safeViews.photoView.setImageBitmap(bitmap)` (`PdfViewerManager.kt:804-807`).
- `PhotoView.setImageDrawable` - which `setImageBitmap` calls through to - calls `attacher.update()` (`PhotoView.java:104-110`).
- `PhotoViewAttacher.update()` calls `updateBaseMatrix(..)`, whose last statement is `resetMatrix()` (`PhotoViewAttacher.java:492-500`, `:646`).
- `resetMatrix()` does `mSuppMatrix.reset()`, which is exactly the zoom and pan state.

So every page turn - arrow button, thumbnail jump, or the swipe gestures S1273 and S1274 are adding - lands the next page at fit scale, centred.

## 1. Why it matters

The owner's S1273 transcript is a full day reading a 130 MB / 240-page PDF on a phone, and the reason paging was broken for him at all was that he reads *zoomed in*. Giving him a two-finger page turn while zoomed still drops him to fit scale on the new page, so he re-pinches every page. The gesture work closes the "I cannot turn the page" complaint without closing the reading loop behind it.

## 2. Scope not yet decided

Open, and the reason this is a Draft rather than a step inside S1273:

- Whether zoom alone is preserved, or zoom and the pan offset together.
- What happens when consecutive pages differ in aspect ratio or size, where a carried-over matrix does not mean the same region of the page.
- Whether preservation is unconditional or a setting, given the project's stance on new settings rows in **S1274** section 3.1.
- Whether the same rule should apply to the thumbnail-sheet jump, which is a deliberate move to a distant page rather than a step through a document.

### 2.1 Settled 2026-07-31, owner-confirmed 2026-08-02

Every bullet below was re-asked as an explicit owner question on 2026-08-02 - see "Quiz decisions" at
the end of this file - because §2.2 shows this ticket already carried one inference labelled as an
owner statement. The answers came back unchanged, so the text stands as written.

- **Zoom and pan together, on every transition** - owner decision. The page arrives in exactly the
  frame the reader left, so a column stays put. Accepted consequence: finishing the bottom of page N
  lands at the bottom of page N+1 rather than its top.
- **Including the thumbnail jump and the go-to-page dialog.** Zoom describes *how* the reader reads,
  not how far they jumped; an exception there would be a rule the user cannot predict.
- **Differing page sizes are not a problem** - resolved by the widget's own model rather than by
  choice. The carried matrix is a supplementary transform layered over a base matrix that is
  recomputed for each new page, so it carries zoom *relative to fit*, and the bounds check pulls any
  leftover pan back onto the page. There is no illegal state to guard against.
- **Unconditional, no setting** - same reasoning the owner accepted for the sibling gesture work.

### 2.2 Correction to §1: the premise about how the owner reads is not evidence

§1 states that paging was broken for the owner because he reads zoomed in, and that he re-pinches
every page. **The S1273 voice transcript does not say that** - it was read in full, and zoom is not
mentioned anywhere in it. The "make the buttons bigger" remark at 00:50 is about the arrow buttons
and belongs to S1275.

The defect itself is real and objectively reproducible, so the ticket stands. But its priority was
argued from an inference presented as an owner complaint, and that argument does not hold. Priority
worth a second look on that basis alone.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1273 (`BlockNeedUserTest`) - the gesture map that makes a zoomed page turn
  reachable, and whose status note already names this ticket as the owner of the zoom reset; S1274
  (`BlockNeedUserTest`) - the same body of work; S1275 (Verified) - the arrow buttons, which lose the
  zoom through the same route; S1276 (`BlockNeedUserTest`) - text selection in the same reader,
  untouched here; S0953 (Archived) - earlier standalone PDF gesture parity.
- **Behaviour:** zoom and pan carry across every page transition, unconditionally - see §2.1.
- **UI:** no new control, no new screen, no new settings row. Scroll mode is out of scope: it renders
  into a plain image view and has no zoom to carry.
- **Flavors:** builds that ship the document reader - `standard`, `noLegal`, `legacy`, `vr`. `lite`
  and `photos` ship no PDF reader, read off the `SUPPORT_DOCUMENTS` gate rather than off a sibling
  inventory record.
- **Localization:** no new string keys.
- **Known adjacent reset, deliberately out of scope:** rotating the screen drops the zoom through the
  same mechanism, because the view gets a new frame even though the activity is not recreated. Same
  symptom, different trigger. Owner confirmed 2026-08-02 that it stays out of this ticket and is
  parked as its own draft - see "Quiz decisions" below.

## 3. Related

- **S1273** - the gesture map that makes zoomed paging reachable in the first place; this ticket is what makes it worth reaching.
- **S1274** - the same piece of work as S1273.
- **S1275** (Verified) - the arrow buttons, which already lose the zoom the same way.
- **S1355** (Draft) - the same reset on screen rotation, spun off from decision C below. Ordered
  after this ticket so it reuses the carry field Phase 01 introduces instead of adding a second one.

## 4. Repro record

**Before, 2026-07-31 - static, the defect is in the widget's contract rather than in a runtime symptom.**
`showPdfPage` pushed every page with `setImageBitmap`, `PhotoView.setImageDrawable` called
`attacher.update()`, `updateBaseMatrix` ended in `resetMatrix()`, and `resetMatrix()` did
`mSuppMatrix.reset()` - the whole of the user's zoom and pan. `getScale()` reads nothing else, so
every page arrived at fit scale, centred, by construction. No runtime capture was taken before the
fix; the code path above is the evidence, and it is unambiguous.

**After, 2026-08-03 - runtime, on `emulator-5554` (Android 15, 1080x2424 @ 420dpi).**
A 48-page PDF zoomed to 2.5x kept that zoom across an arrow-button turn, a go-to-page jump and a
thumbnail-sheet jump; the probe reported `zoom=2.5` on each. A second document opened immediately
afterwards came up at `zoom=1.0`, so nothing leaks between documents. In a second document zoomed to
2.0x and panned, the page after the turn showed the same scale **and** the same crop, so the pan
carried with the zoom. Full run: `temp/S1327/mobile_test_scenario_20260803_0110.md`.

### Quiz decisions (2026-08-02)

Answers to the two Pre-Implementation Blockers in the tactical `INDEX.md`, plus one scope boundary
§3.3 had left flagged. All three came from the owner directly, not from a recommendation adopted by
silence.

- Owner decision A, what is carried → **zoom and pan together, restored as one matrix**. The page
  lands in exactly the frame the reader left it in. Step 01.2 writes the captured matrix back
  unchanged; no `getValues` / `setValues` vertical zeroing.
- Owner decision B, which page moves carry it → **all of them**, arrows, swipes, thumbnail sheet and
  the go-to-page dialog alike. Zoom describes how the reader reads, not how far they jumped. No
  `carryZoom` parameter, no call-site changes: every route already funnels through `showPdfPage`.
- Scope boundary, the rotation reset → **stays out of this ticket**, parked as S1355. Same symptom,
  different trigger, and a separate device-test surface; this ticket stays strictly about the page
  turn.

## Last Audit

**Date:** 2026-08-03
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 21 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

Contract checks that carry the verdict: the carry field is declared once and cleared in both
`displayPdf` and `close()`; the capture sits before the bitmap swap behind a `drawable != null` guard
and the restore after the `currentPageBitmap` assignment; the matrix is written back unchanged (owner
decision A); no `carryZoom` parameter and no call-site change (owner decision B); no `setFrame` /
configuration hook, so the rotation reset stays with S1355; no `BuildConfig` guard, no new string key,
no new settings row; `PdfViewerManager.kt` at 1092 lines, inside the phase budget of 1110. Closure:
`a.ps1 fk` exit 0, scoped detekt gate exit 0, `post-change` clean PASS on both phases, one
`ALL_FEATURES` record whose flavor list matches the `SUPPORT_DOCUMENTS` gate, the `HOW_TO` bullet in
all three locales, catalog regenerated. Device run 2026-08-03 on emulator-5554: 10 PASS, 0 FAIL, zero
app-side errors in the log.

EXEMPT: `docs/FEATURES*.md` - the showcase is written by `/skill-release` from the inventory diff, not
per spec.

### Manual / on-device

- [ ] Two-finger swipe page turn while zoomed - the one route the emulator cannot drive, since
      mobile-mcp is single-pointer. It shares `showPdfPage` with the three routes proven on device.
- [ ] A long read on real hardware: the emulator proves the matrix carries, not that a 240-page
      document still feels right after many turns.

## Revision History

- **2026-08-03** - by `/spec-test-device` (`sdk_gphone64_x86_64`, device: emulator-5554, Android 15)
  - Scenario: `temp/S1327/mobile_test_scenario_20260803_0110.md` · PASS/FAIL/SKIPPED 10/0/2 · Errors in log: 0
