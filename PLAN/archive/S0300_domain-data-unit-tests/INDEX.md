# Tactical Plan: S0300 - domain-data-unit-tests

**Strategic spec:** [`../S0300_domain-data-unit-tests.md`](../S0300_domain-data-unit-tests.md)
**Feature:** Юнит-покрытие слоёв domain и data
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 8 / 8 done
**Last updated:** 2026-05-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Coverage cutoff (applies to every phase)

A class is **in scope** only if it carries non-trivial logic: conditional branching, data transformation/mapping, or error handling. **Out of scope:** pure data classes, enums, thin delegates with no branching, generated code. The authoritative per-class work-list is `COVERAGE_INVENTORY.md`, produced in Phase 01. Phases 02–07 consume their subset of that inventory; no phase enumerates classes that the inventory marks trivial.

Test placement follows the source set of the class under test: shared logic → `app_v2/src/test/`; `noLegal`/`vr`-only logic → `app_v2/src/testNoLegal/` / `app_v2/src/testVr/`. No `BuildConfig` flavor guards inside test code.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | test-foundations | - | ✅ Done | 5/5 | [PHASE_01__test-foundations.md](PHASE_01__test-foundations.md) |
| 02 | domain-usecases | 01 | ✅ Done | 6/6 | [PHASE_02__domain-usecases.md](PHASE_02__domain-usecases.md) |
| 03 | domain-models-strategies | 01 | ✅ Done | 6/6 | [PHASE_03__domain-models-strategies.md](PHASE_03__domain-models-strategies.md) |
| 04 | data-repositories-local | 01 | ✅ Done | 4/4 | [PHASE_04__data-repositories-local.md](PHASE_04__data-repositories-local.md) |
| 05 | data-network-remote | 01 | ✅ Done | 5/5 | [PHASE_05__data-network-remote.md](PHASE_05__data-network-remote.md) |
| 06 | data-transfer-link-cloud | 01 | ✅ Done | 6/6 | [PHASE_06__data-transfer-link-cloud.md](PHASE_06__data-transfer-link-cloud.md) |
| 07 | flavor-data-logic | 04,05,06 | ✅ Done | 2/2 | [PHASE_07__flavor-data-logic.md](PHASE_07__flavor-data-logic.md) |
| 08 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 02–06 depend only on Phase 01 (the shared harness) and may proceed in any order or in parallel once 01 is Done. Phase 07 depends on the data phases that define the shared contracts it overrides.

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (owner defaults, 2026-05-29). No open blockers.

- [x] **Research:** Cutoff for "non-trivial logic" - Resolved (strategic §6.1: branch/transform/error only).
- [x] **Research:** Pre-existing red tests - Resolved (strategic §6.2: do not fix here, do not add new red; validate per-class).
- [x] **Research:** Room isolation degree - Resolved (strategic §6.3: in-memory Room only where DAO/query logic is under test, else fake source).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [x] `dev/CHANGELOG.md` has per-phase + per-file dev-log entries.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (test source sets do not change public API, but regen anyway after the work).
- [ ] `/spec-check S0300` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0300`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-29 - Initial tactical plan authored by `/spec-tech`.
