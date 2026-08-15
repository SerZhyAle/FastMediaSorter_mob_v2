# Tactical Plan: S1276 - pdf-select-words-directly-on-the-page

**Strategic spec:** [`../S1276_pdf-select-words-directly-on-the-page.md`](../S1276_pdf-select-words-directly-on-the-page.md)
**Feature:** Long-press on a PDF pre-selects the pressed text without an OCR pass on API 35+
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 55
**Status:** Done - awaiting device verification
**Phases:** 2 / 2 done
**Last updated:** 2026-07-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | native-text-layout | - | ✅ Done | 3/3 | [PHASE_01__native-text-layout.md](PHASE_01__native-text-layout.md) |
| 02 | wiring-and-fallback | 01 | ✅ Done | 4/4 | [PHASE_02__wiring-and-fallback.md](PHASE_02__wiring-and-fallback.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Ground truth this plan is built on

- `PdfPageTextContent` on compileSdk 36 exposes **both** `getText(): String` and
  `getBounds(): List<RectF>`. Verified against the platform stub, not assumed:
  `javap -classpath <sdk>/platforms/android-36/android.jar android.graphics.pdf.content.PdfPageTextContent`.
- Bounds are **per content item**, and one item carries a *list* of rects - one per line it spans.
  An item is therefore a run of text, not a word. The plan selects the whole matched item and lets
  the native handles narrow it, which is what the owner asked for ("одно-два слова, предложения").
- `PdfTextSelectionManager.extractTextNative` already opens the page itself, so page width and height
  are available at the same point as the content items. `PdfViewerManager` needs no change.
- The current text is built as `getTextContents().joinToString(" ") { it.text }`. Keeping that exact
  join is what lets item char ranges be computed instead of searched for.
- `PdfSelectionCoordinateMapper.charRangeForPoint` currently resolves a word by
  `fullText.indexOf(word)`. That is a first-occurrence bug on repeated words and it disappears on the
  native path rather than being patched.

---

## Pre-Implementation Blockers

None. The one API question the strategic spec flagged is answered above.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES*.md` - NOT edited per-spec; the capability record goes to `docs/ALL_FEATURES.jsonl` in Phase 02, flavors read off `SUPPORT_DOCUMENTS`.
- [x] `dev/CHANGELOG.md` has an entry for the change.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `.\a.ps1 fk` passes and the new unit test passes (5/5, 0 failures).
- [ ] `/spec-check S1276` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`.
2. During a phase: flip a step to `[x] done` only when its Verification passes.
3. On phase completion: confirm every step, confirm Phase Done Criteria, flip the row to `✅ Done`.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log, set the journal status.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-07-29 - Initial tactical plan authored by `/spec-all` stage F2.
