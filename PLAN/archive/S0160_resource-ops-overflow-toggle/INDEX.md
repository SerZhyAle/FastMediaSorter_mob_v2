# Tactical Plan: S0160 — resource-ops-overflow-toggle

**Strategic spec:** [`../S0160_resource-ops-overflow-toggle.md`](../S0160_resource-ops-overflow-toggle.md)
**Feature:** Overflow-menu toggle for resource action buttons + single-resource refresh
**Tier:** 2 — Easy
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|------------|--------|------:|------|
| 01 | settings-model | — | ✅ Done | 2/2 | [PHASE_01__settings-model.md](PHASE_01__settings-model.md) |
| 02 | scan-single-resource | 01 | ✅ Done | 2/2 | [PHASE_02__scan-single-resource.md](PHASE_02__scan-single-resource.md) |
| 03 | adapter-overflow | — | ✅ Done | 4/4 | [PHASE_03__adapter-overflow.md](PHASE_03__adapter-overflow.md) |
| 04 | settings-ui | 01 | ✅ Done | 5/5 | [PHASE_04__settings-ui.md](PHASE_04__settings-ui.md) |
| 05 | activity-wiring | 02, 03, 04 | ✅ Done | 2/2 | [PHASE_05__activity-wiring.md](PHASE_05__activity-wiring.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — all §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0160` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0160`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-13 — Initial tactical plan authored by `/spec-tech`.
