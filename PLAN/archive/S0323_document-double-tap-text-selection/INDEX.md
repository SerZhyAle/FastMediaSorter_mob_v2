# Tactical Plan: S0323 - document-double-tap-text-selection

**Strategic spec:** [`../S0323_document-double-tap-text-selection.md`](../S0323_document-double-tap-text-selection.md)
**Feature:** Text selection by long-press + draggable handles + floating "Copy" across document viewers
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 3 / 4 done (Phase 03 deferred → S0324)
**Last updated:** 2026-06-01

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
>
> Selection entry gesture is **long-press** (native, as in EPUB). Double-tap is NOT repurposed (stays zoom; for PDF double-tap is pinch-only already). Owner chose **full scope**: PDF entry + PDF coordinate word pre-selection + noLegal Office unified menu.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | pdf-longpress-selection-entry | - | ✅ Done | 4/4 | [PHASE_01__pdf-longpress-selection-entry.md](PHASE_01__pdf-longpress-selection-entry.md) |
| 02 | pdf-word-preselection | 01 | ✅ Done | 3/3 | [PHASE_02__pdf-word-preselection.md](PHASE_02__pdf-word-preselection.md) |
| 03 | office-selection-actionmode | - | ⏭️ Skipped → S0324 | 0/3 | [PHASE_03__office-selection-actionmode.md](PHASE_03__office-selection-actionmode.md) |
| 04 | docs-catalog-cleanup | 01,02 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are resolved by owner decision (2026-06-01) - no unchecked blockers.

- [x] **Research §6.1** (selection entry gesture) - Resolved: long-press; double-tap stays zoom.
- [x] **Research §6.2** (coordinate→word mapping) - Resolved by design: OCR block-box hit-test as primary path (all SDKs); API35 `getTextContents()` geometry used only if exposed; fallback = open overlay without pre-selection (manual handle correction). Owner accepts approximate pre-selection.
- [x] **Research §6.3** (EPUB long-press) - Resolved: native WebView selection + augmented ActionMode already works; verification only.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a sentence for PDF/EPUB/TXT selection).
- [ ] `docs/FEATURES_noLegal.md` + `_RU` + `_UK` updated for Office selection (noLegal-only).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0323` returns `Verified`.

---

## How to Track Progress

1. Before a phase: flip row to `🚧 In Progress`, update `Phases: X/N done`.
2. During: `[~]` started, `[x]` done when Verification passes. Never `[x]` on intent.
3. On completion: every step `[x]`, Phase Done Criteria met, row `✅ Done`, bump counter.
4. If blocked: row `⛔ Blocked`, add to Blockers Log; set journal Block* status if whole spec blocked.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-01 - Initial tactical plan authored by `/spec-tech` (full scope, owner-confirmed).
