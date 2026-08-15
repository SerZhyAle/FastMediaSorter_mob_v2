# Tactical Plan: S1327 - pdf-zoom-resets-on-every-page-turn

**Strategic spec:** [`../S1327_pdf-zoom-resets-on-every-page-turn.md`](../S1327_pdf-zoom-resets-on-every-page-turn.md)
**Research inputs:** none
**Feature:** PDF reader keeps the reader's zoom across a page turn
**Tier:** none recorded in the strategic spec
**Priority:** 45
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-08-03

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Mechanism, as verified in the tree

The reset is not a bug in this project's code - it is PhotoView's documented contract, reached once per page.

- Every page route funnels into `PdfViewerManager.showPdfPage(index)`: the arrow buttons through `showPreviousPage` / `showNextPage`, the swipe gestures through `turnPage`, the go-to-page dialog directly, the thumbnail sheet through `onPagePicked`.
- `showPdfPage` pushes the rendered page with `safeViews.photoView.setImageBitmap(bitmap)`.
- `PhotoView.setImageDrawable` calls `attacher.update()`, which calls `updateBaseMatrix(..)`, whose last statement is `resetMatrix()`, which does `mSuppMatrix.reset()`.
- `mSuppMatrix` is the whole of the user's zoom and pan. `PhotoViewAttacher.getScale()` reads nothing else.

So the fix is **restore after rebuild**, not **preserve across the turn**. The rebuild cannot be avoided: pushing a new bitmap is the only way to show the next page, and pushing it always resets. The state must be read off the view before the swap and written back after it.

`PhotoView` 2.3.0 exposes exactly the pair needed: `getSuppMatrix(Matrix)` reads the zoom and pan together, `setSuppMatrix(Matrix)` writes them back and clamps the result through `checkMatrixBounds()`. Two consequences that settle strategic section 2:

- A page of a different size or aspect ratio cannot land illegally. The supplementary matrix sits on top of a base matrix recomputed for the new page, so it carries a zoom factor relative to fit rather than an absolute one, and the bounds check pulls any leftover pan back onto the page.
- `setSuppMatrix` refuses while the view holds no drawable, so the restore must follow the bitmap push, never precede it.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | zoom-carry-across-page-turn | - | ✅ Done | 3/3 | [PHASE_01__zoom-carry-across-page-turn.md](PHASE_01__zoom-carry-across-page-turn.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Two phases, because the change is one field and two call sites in one file. Padding it into more would produce steps that fail the real-work filter.

---

## Flavor reach

Read off the gate in `app_v2/build.gradle.kts`, not off a sibling record:

- `SUPPORT_DOCUMENTS = true` - standard, noLegal, legacy, vr. These ship the PDF reader and this change.
- `SUPPORT_DOCUMENTS = false` - lite, photos. No PDF reader, nothing to gate.

No flavor source set is involved: `PdfViewerManager` lives in `src/main` and the change adds no `BuildConfig` guard.

---

## Pre-Implementation Blockers

Both are owner decisions about behaviour, not open research. Neither is technical - every variant below is implementable in the same file at the same cost. Phase 01 is written for the recommended answer and names the single step that changes if the owner picks otherwise.

Both answered by the owner on 2026-08-02 through `/spec-quiz`, both on the recommended option. Step 01.2 is now fully determined and its alternative branches are dead - implement the recommended path only.

- [x] **Owner decision A - what is carried.** Answered 2026-08-02: zoom and pan together, restored as one matrix, so the page lands framed exactly as the previous one was. The rejected alternative - carry the zoom but drop the reader to the top of the new page - is not to be implemented, so Step 01.2 needs no `getValues` / `setValues` vertical zeroing.
- [x] **Owner decision B - which page moves carry it.** Answered 2026-08-02: all of them, including the thumbnail sheet and the go-to-page dialog, on the grounds that the zoom describes how the owner reads rather than how far he jumped. The rejected alternative needed a `carryZoom` parameter on `showPdfPage` and two updated call sites - do not add it, and leave `showPdfPage`'s signature alone.

Two questions strategic section 2 left open are closed here and are **not** blockers:

- Pages of differing size or aspect ratio need no special handling, for the reason given under "Mechanism" above.
- No setting gates this. S1274 section 3.1 settled the same question for the sibling gesture work with the owner, on the grounds that the project already carries a large settings surface and a behaviour with no discoverability problem does not earn a row in it. The same reasoning applies to a reader that simply keeps the zoom it was given.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped. The strategic spec carries no FEATURES sentence, and the showcase is written by `/skill-release` from the `ALL_FEATURES` diff.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1327` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1327`.

---

## Blockers Log

- 2026-07-31 - Phase 01 blocked on owner decisions A and B above. Next: one owner answer on each, then Step 01.2 is fully determined.
- 2026-08-02 - Cleared. Owner answered A and B via `/spec-quiz`, both on the recommended option; spec status restored to `Tactical`. A third answer parked the adjacent rotation reset as S1355, so it stays out of this plan. No blocker remains.

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-02 - Owner decisions A and B answered via `/spec-quiz`; Pre-Implementation Blockers ticked, Blockers Log cleared.
