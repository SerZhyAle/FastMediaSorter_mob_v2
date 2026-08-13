# Tactical Plan: S0317 - embedded-calculator

**Strategic spec:** [`../S0317_embedded-calculator.md`](../S0317_embedded-calculator.md)
**Feature:** Embedded calculator
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 2 / 2 done
**Last updated:** 2026-05-31

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | calculator-feature | - | ✅ Done | 8/8 | [PHASE_01__calculator-feature.md](PHASE_01__calculator-feature.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No blockers. Strategic §6 research items are all Resolved by owner decision.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file touched by S0317 work.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [x] Status is `BlockNeedUserTest` with `Timber.d("S0317: ...")` debug tags awaiting device verification.
- [x] Strategic spec `Status:` advanced to `BlockNeedUserTest`; `/spec-check S0317` is deferred until device verification.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0317` unless status is `BlockNeedUserTest`.

---

## Blockers Log

- 2026-05-31 - No pre-implementation blockers.

---

## Change Log

- 2026-05-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-31 - Both phases completed; S0317 is implemented and waiting for on-device calculator menu/widget verification.
- 2026-05-31 - Applied owner feedback patch while staying in `BlockNeedUserTest`: compact button grid, operation history line, repeated equals, grey digit buttons, larger button text, and calculator Copy/Paste/Round menu.
- 2026-05-31 - Applied second owner feedback patch while staying in `BlockNeedUserTest`: classic percent behavior, in-memory calculation history, Send result, Save history to Downloads, Clear history, and pink C/CE buttons.
- 2026-05-31 - Applied third owner feedback patch while staying in `BlockNeedUserTest`: top-left calculator history is now a small scrollable action log.
- 2026-05-31 - Applied fourth owner feedback patch while staying in `BlockNeedUserTest`: Paste now parses noisy calculator expressions, ignores non-numeric text, and supports comma/dot decimal separators.
