# Tactical Plan: S0329 - calculator-history-and-functions

**Strategic spec:** [`../S0329_calculator-history-and-functions.md`](../S0329_calculator-history-and-functions.md)
**Feature:** Persistent calculator history + scientific functions
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-06-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | engine-functions | - | ✅ Done | 4/4 | [PHASE_01__engine-functions.md](PHASE_01__engine-functions.md) |
| 02 | history-persistence | - | ✅ Done | 3/3 | [PHASE_02__history-persistence.md](PHASE_02__history-persistence.md) |
| 03 | function-menu | 01 | ✅ Done | 3/3 | [PHASE_03__function-menu.md](PHASE_03__function-menu.md) |
| 04 | layout-recompose | - | ✅ Done | 2/2 | [PHASE_04__layout-recompose.md](PHASE_04__layout-recompose.md) |
| 06 | expression-evaluator | 01 | ✅ Done | 3/3 | [PHASE_06__expression-evaluator.md](PHASE_06__expression-evaluator.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 research items are resolved at tactical level (none block implementation):

- §6.1 history record format → resolved: unary functions render `label(arg)=result`; postfix forms `x²`, `n!`; `π` sets display without a completed entry. See Phase 01 / Phase 03.
- §6.2 xʸ behavior → resolved: integrated into the existing operator pipeline as binary operator with symbol `^`. See Phase 01.
- §6.3 transcendental rounding → resolved: reuse the engine's existing 12-significant-digit `format()` so irrational results are stripped like every other result. See Phase 01.

No unchecked blockers. Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed - new class).
- [ ] `/spec-check S0329` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log; set journal status to the relevant `Block*` if the whole spec is blocked.
5. All done: flip `Status:` to `Done`, run `/spec-check S0329`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-02 - Initial tactical plan authored by `/spec-tech`.
