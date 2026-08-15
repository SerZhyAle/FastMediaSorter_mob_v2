# Tactical Plan: S1544 - house-style-unenforced-where-it-applies

**Strategic spec:** [`../S1544_house-style-unenforced-where-it-applies.md`](../S1544_house-style-unenforced-where-it-applies.md)
**Research inputs:** [`research/01__debt-measurement-and-control-points.md`](research/01__debt-measurement-and-control-points.md)
**Feature:** House text style applied on the write paths the canon declares mandatory
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 6 / 6 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | style-library | - | ✅ Done | 3/3 | [PHASE_01__style-library.md](PHASE_01__style-library.md) |
| 02 | translation-ingest | 01 | ✅ Done | 2/2 | [PHASE_02__translation-ingest.md](PHASE_02__translation-ingest.md) |
| 03 | authored-write-path | 01 | ✅ Done | 2/2 | [PHASE_03__authored-write-path.md](PHASE_03__authored-write-path.md) |
| 04 | fixer-consolidation | 01 | ✅ Done | 3/3 | [PHASE_04__fixer-consolidation.md](PHASE_04__fixer-consolidation.md) |
| 05 | accumulated-debt-pass | 04 | ✅ Done | 2/2 | [PHASE_05__accumulated-debt-pass.md](PHASE_05__accumulated-debt-pass.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three strategic §6 research items are `Resolved` against `research/01__debt-measurement-and-control-points.md`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без записи в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, no Kotlin in scope.
- [ ] `/spec-check S1544` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1544`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
