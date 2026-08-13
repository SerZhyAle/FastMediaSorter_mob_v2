# Tactical Plan: S1595 - detekt-preflight-coverage-gap

**Strategic spec:** [`../S1595_detekt-preflight-coverage-gap.md`](../S1595_detekt-preflight-coverage-gap.md)
**Research inputs:** [`research/01__lexical-size-rule-calibration.md`](research/01__lexical-size-rule-calibration.md) · [`research/02__autocorrect-feasibility.md`](research/02__autocorrect-feasibility.md) · [`research/04__actual-failing-rules.md`](research/04__actual-failing-rules.md) · [`research/05__scoped-runner-cost-and-proof.md`](research/05__scoped-runner-cost-and-proof.md)
**Feature:** Scoped real-detekt preflight replacing lexical rule emulation
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-12

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | scoped-detekt-runner | - | ✅ Done | 5/5 | [PHASE_01__scoped-detekt-runner.md](PHASE_01__scoped-detekt-runner.md) |
| 02 | preflight-delegation-and-gating | 01 | ✅ Done | 4/4 | [PHASE_02__preflight-delegation-and-gating.md](PHASE_02__preflight-delegation-and-gating.md) |
| 03 | debt-ticket-touch-frequency | - | ✅ Done | 2/2 | [PHASE_03__debt-ticket-touch-frequency.md](PHASE_03__debt-ticket-touch-frequency.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** lexical reproduction of the size rules - Resolved NO, see strategic §6.1 and `research/01`.
- [x] **Research:** `--auto-correct` viability - Resolved REJECT, see strategic §6.2 and `research/02`.
- [x] **Research:** which rules actually fail the gate - Resolved, see strategic §6.4 and `research/04`.
- [x] **Research:** does a scoped run diverge from a whole-module run - strategic §6.5. **Not a start blocker for Phase 01** - Phase 01 only builds the runner. It blocks Step 02.3 (the advisory-to-blocking flip) and is measured by Step 02.2.
- [ ] **Owner:** how to derive debt-ticket priority - strategic §6.3. **Not a blocker for any phase.** Phase 03 produces the measurement and stops; no catalog write happens without the owner's ruling (ADR-4).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file (one row, set of 9).
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected: no Kotlin touched.
- [ ] `/spec-check S1595` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1595`.

---

## Blockers Log

- 2026-08-12 - none yet.

---

## Change Log

- 2026-08-12 - Initial tactical plan authored by `/spec-tech`.
