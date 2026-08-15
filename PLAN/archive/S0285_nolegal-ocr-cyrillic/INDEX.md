# Tactical Plan: S0285 - nolegal-ocr-cyrillic

**Strategic spec:** [`../S0285_nolegal-ocr-cyrillic.md`](../S0285_nolegal-ocr-cyrillic.md)
**Feature:** OCR Cyrillic research (two-axis STANDARD vs noLegal)
**Tier:** 4 - Strategic
**Priority:** 50
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-21

> **Scope:** Research-only tactical plan. Output artifact is `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`. No Kotlin/XML code changes in any phase. Each phase fills a defined section of the output document with concrete candidate analysis. Verification predicates are static text checks against the output document.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 4/4 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | store-safe-baseline | 01 | ✅ Done | 5/5 | [PHASE_02__store-safe-baseline.md](PHASE_02__store-safe-baseline.md) |
| 03 | on-device-deep-learning | 01 | ✅ Done | 5/5 | [PHASE_03__on-device-deep-learning.md](PHASE_03__on-device-deep-learning.md) |
| 04 | commercial-pathways | 01 | ✅ Done | 5/5 | [PHASE_04__commercial-pathways.md](PHASE_04__commercial-pathways.md) |
| 05 | sidecar-runtimes | 01 | ✅ Done | 4/4 | [PHASE_05__sidecar-runtimes.md](PHASE_05__sidecar-runtimes.md) |
| 06 | cross-axis-synthesis | 02, 03, 04, 05 | ✅ Done | 5/5 | [PHASE_06__cross-axis-synthesis.md](PHASE_06__cross-axis-synthesis.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

The 10 Open research items in strategic §6 are NOT pre-implementation blockers — they are the research scope itself. They get resolved during phases 02–06, not before phase 01. Phase 01 builds the empty container into which subsequent phases write findings.

- [x] **No external blockers identified.** Phase 01 may start immediately. Research-only ticket; no code dependencies, no Room schema, no Hilt graph, no UI surface.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped (strategic §8 states «Без изменений» for this research-only ticket).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regeneration - skipped (no `.kt` files modified).
- [x] `/spec-check S0285` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [x] `ocr-cyrillic.md` research document is fully populated with verdicts for both axes across all 7 categories.
- [x] At least one store-safe upgrade candidate identified for axis A, or explicit negative result with justification.
- [x] Ranked noLegal candidate list assembled with blocker type for each entry.
- [x] Follow-up implementation spec proposals listed (without id assignment).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0285`.

---

## Blockers Log

- _No blockers logged yet._

---

## Change Log

- 2026-05-21 - Initial tactical plan authored by `/spec-tech`.
