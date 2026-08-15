# Tactical Plan: S1604 - codify-agent-skills-rules-canon

**Strategic spec:** [`../S1604_codify-agent-skills-rules-canon.md`](../S1604_codify-agent-skills-rules-canon.md)
**Research inputs:** none - both §6 items resolved inline in the strategic spec (no artifact files)
**Feature:** Codify agent skills, rules and canon
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Verified
**Phases:** 5 / 5 done
**Last updated:** 2026-08-12

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | hook-inventory-and-gate | - | ✅ Done | 4/4 | [PHASE_01__hook-inventory-and-gate.md](PHASE_01__hook-inventory-and-gate.md) |
| 02 | rules-compression | 01 | ✅ Done | 3/3 | [PHASE_02__rules-compression.md](PHASE_02__rules-compression.md) |
| 03 | refuted-approaches-index | 02 | ✅ Done | 3/3 | [PHASE_03__refuted-approaches-index.md](PHASE_03__refuted-approaches-index.md) |
| 04 | canon-propagation | 01, 03 | ✅ Done | 3/3 | [PHASE_04__canon-propagation.md](PHASE_04__canon-propagation.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 research items carry `Status: Resolved` with their rulings recorded inline.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, no Kotlin touched.
- [ ] `/spec-check S1604` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1604`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-12 - Initial tactical plan authored by `/spec-tech`.
