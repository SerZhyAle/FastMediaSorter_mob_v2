# Tactical Plan: S0364 - settings-interface-group-split

**Strategic spec:** [`../S0364_settings-interface-group-split.md`](../S0364_settings-interface-group-split.md)
**Feature:** Split overcrowded interface settings group into two collapsible groups + adopt "браузер файлов" terminology
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 6 / 7 done
**Last updated:** 2026-06-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-group-split-layout | - | ✅ Done | 3/3 | [PHASE_01__settings-group-split-layout.md](PHASE_01__settings-group-split-layout.md) |
| 02 | section-state-and-wiring | 01 | ✅ Done | 3/3 | [PHASE_02__section-state-and-wiring.md](PHASE_02__section-state-and-wiring.md) |
| 03 | group-title-strings | 01 | ✅ Done | 2/2 | [PHASE_03__group-title-strings.md](PHASE_03__group-title-strings.md) |
| 04 | terminology-inventory-glossary | - | ✅ Done | 2/2 | [PHASE_04__terminology-inventory-glossary.md](PHASE_04__terminology-inventory-glossary.md) |
| 05 | terminology-strings-sweep | 04 | ✅ Done | 2/2 | [PHASE_05__terminology-strings-sweep.md](PHASE_05__terminology-strings-sweep.md) |
| 06 | terminology-docs-sweep | 04 | ✅ Done | 2/2 | [PHASE_06__terminology-docs-sweep.md](PHASE_06__terminology-docs-sweep.md) |
| 07 | docs-catalog-cleanup | all | 🚧 In Progress | 2/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - terminology wording aligned to "браузер файлов" per strategic §8.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0364` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0364`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-05 - Initial tactical plan authored by `/spec-tech`.
