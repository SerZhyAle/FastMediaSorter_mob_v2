# Tactical Plan: S0325 - browse-filter-doc-types-reset

**Strategic spec:** [`../S0325_browse-filter-doc-types-reset.md`](../S0325_browse-filter-doc-types-reset.md)
**Feature:** Office type checkbox + reset-checkboxes button in Browse file-filter dialog (portrait + landscape)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-01

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | filter-layout-grid | - | ✅ Done | 3/3 | [PHASE_01__filter-layout-grid.md](PHASE_01__filter-layout-grid.md) |
| 02 | dialog-logic | 01 | ✅ Done | 3/3 | [PHASE_02__dialog-logic.md](PHASE_02__dialog-logic.md) |
| 03 | docs-catalog-cleanup | 01,02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update required (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0325` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0325`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-01 - Initial tactical plan authored by `/spec-tech`.
