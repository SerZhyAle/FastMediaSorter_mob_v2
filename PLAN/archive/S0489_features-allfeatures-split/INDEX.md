# Tactical Plan: S0489 - features-allfeatures-split

**Strategic spec:** [`../S0489_features-allfeatures-split.md`](../S0489_features-allfeatures-split.md)
**Research inputs:** none
**Feature:** Split FEATURES (curated showcase) and ALL_FEATURES (JSONL inventory, replaces FUNCTIONALITY.log)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | allfeatures-schema-tooling | - | ✅ Done | 4/4 | [PHASE_01__allfeatures-schema-tooling.md](PHASE_01__allfeatures-schema-tooling.md) |
| 02 | migrate-functionality-log | 01 | ✅ Done | 3/3 | [PHASE_02__migrate-functionality-log.md](PHASE_02__migrate-functionality-log.md) |
| 03 | inventory-population | 01, 02 | ✅ Done | 4/4 | [PHASE_03__inventory-population.md](PHASE_03__inventory-population.md) |
| 04 | features-revision | 03 | ✅ Done | 3/3 | [PHASE_04__features-revision.md](PHASE_04__features-revision.md) |
| 05 | rules-skills-split | 01, 02 | ✅ Done | 4/4 | [PHASE_05__rules-skills-split.md](PHASE_05__rules-skills-split.md) |
| 06 | drift-gate | 01, 03 | ✅ Done | 3/3 | [PHASE_06__drift-gate.md](PHASE_06__drift-gate.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No blocking research items. Strategic §6.5 (Wear OS coverage) is `Open` with a default decision (defer; JSONL schema extensible) and explicitly does NOT block the first iteration - it is out of scope for these phases.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - updated (strategic §8 mandates a FEATURES change: showcase pruned + reference to ALL_FEATURES inventory).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration not required (no `.kt`/public API change).
- [ ] `/spec-check S0489` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0489`.

---

## Blockers Log

- none

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
