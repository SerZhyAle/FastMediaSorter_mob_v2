# Tactical Plan: S0331 - calculator-memory-and-modulo

**Strategic spec:** [`../S0331_calculator-memory-and-modulo.md`](../S0331_calculator-memory-and-modulo.md)
**Feature:** Calculator memory register, modulo, collapsible memory row, operator styling
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-06-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | engine-memory-modulo | - | ✅ Done | 3/3 | [PHASE_01__engine-memory-modulo.md](PHASE_01__engine-memory-modulo.md) |
| 02 | memory-persistence | 01 | ✅ Done | 2/2 | [PHASE_02__memory-persistence.md](PHASE_02__memory-persistence.md) |
| 03 | layout-and-styles | - | ✅ Done | 3/3 | [PHASE_03__layout-and-styles.md](PHASE_03__layout-and-styles.md) |
| 04 | manager-wiring | 01,02,03 | ✅ Done | 4/4 | [PHASE_04__manager-wiring.md](PHASE_04__manager-wiring.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 research items are resolved at tactical level (none block implementation):

- §6.1 mod history format → resolved: render `x mod y=z` (binary-operator history, like the other operators). See Phase 01.
- §6.2 MR behavior → resolved: MR replaces the current entry with the memory value as a fresh standalone number (start-new-input semantics). See Phase 04.

No unchecked blockers. Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class).
- [ ] `/spec-check S0331` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log; set journal status to the relevant `Block*` if the whole spec is blocked.
5. All done: flip `Status:` to `Done`, run `/spec-check S0331`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-02 - Initial tactical plan authored by `/spec-tech`.
