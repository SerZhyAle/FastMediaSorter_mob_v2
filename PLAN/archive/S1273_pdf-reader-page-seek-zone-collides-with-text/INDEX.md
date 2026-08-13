# Tactical Plan: S1273 - pdf-reader-page-seek-zone-collides-with-text

**Strategic spec:** [`../S1273_pdf-reader-page-seek-zone-collides-with-text.md`](../S1273_pdf-reader-page-seek-zone-collides-with-text.md)
**Research inputs:** none - the mechanism finding is recorded in strategic section 2.1, sourced from the PhotoView 2.3.0 sources.
**Feature:** PDF reader page-turn gesture map
**Tier:** bugfix
**Priority:** 65
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

This plan implements **S1273 section 4.1 and S1274 section 3.1 together** - the two tickets are one piece of work by their own statement, sharing one decision and one gesture map.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | page-swipe-detector | - | ✅ Done | 3/3 | [PHASE_01__page-swipe-detector.md](PHASE_01__page-swipe-detector.md) |
| 02 | gesture-wiring | 01 | ✅ Done | 4/4 | [PHASE_02__gesture-wiring.md](PHASE_02__gesture-wiring.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic section 4.1 is resolved by the owner, S1274 section 3.1 answers the settings half, and section 2.1 settles the mechanism.

---

## Design contract (binding on both phases)

The gesture is detected on the raw `MotionEvent` stream inside the `PhotoView.setOnTouchListener` that already forwards to `attacher.onTouch`. Strategic section 2.1 establishes why no part of it can live in `handlePdfFling`.

- **Two fingers, any scale.** Both pointers travel vertically past the platform paging slop, in the same direction, with the pinch span within tolerance - turn one page. This is the zoomed-in case.
- **One finger, unzoomed only.** Vertical travel past the platform paging slop, with no velocity requirement at all - turn one page. One finger on a zoomed page is left alone so it keeps panning.
- **Direction.** Travel up turns to the next page, travel down to the previous, matching the direction the fling handler used.
- **Claiming.** The moment the detector commits, the attacher is sent one `ACTION_CANCEL` and the rest of the gesture is consumed, so a page never turns twice and a zoomed page stops panning mid-turn.
- **Threshold source.** `ViewConfiguration.getScaledPagingTouchSlop()` - the platform's own "this is a paging swipe" distance, verified present in `android.jar` for API 36 and available since API 8. No magic pixel constants.
- **Three hosts, not one.** The reader is driven from `PlayerGestureSetupManager` (unified player, one detector per PhotoView surface), `DocumentStandaloneActivity`, and `StandaloneViewManager`. All three get the same detector; the last one additionally gates on `currentMediaType == MediaType.PDF` because its PhotoView is shared with images and GIFs. Found mid-Phase 02 by grepping `handlePdfFling`, and recorded as Step 02.5 rather than folded in silently.

---

## Known non-goal, do not treat as a defect

A page turn resets the PhotoView zoom to fit, because `setImageBitmap` drives `PhotoViewAttacher.update()` into `resetMatrix()`. Turning a page while zoomed therefore lands the next page unzoomed. This predates the ticket, applies equally to the arrow buttons, and is parked as **S1327**. Device testing must not report it against S1273.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped; the showcase is populated only by `/skill-release` from the `ALL_FEATURES` diff.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - a new public class is added.
- [ ] `/spec-check S1273` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1273`.

---

## Blockers Log

- none

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
