# Tactical Plan: S1443 - landscape-collapsed-panels-inline-topbar

**Strategic spec:** [`../S1443_landscape-collapsed-panels-inline-topbar.md`](../S1443_landscape-collapsed-panels-inline-topbar.md)
**Research inputs:** none
**Feature:** Collapsed main-screen panel chips climb into the free tail of the command bar in wide layout
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | placement-planner | - | ✅ Done | 3/3 | [PHASE_01__placement-planner.md](PHASE_01__placement-planner.md) |
| 02 | placement-executor | 01 | ✅ Done | 4/4 | [PHASE_02__placement-executor.md](PHASE_02__placement-executor.md) |
| 03 | panel-wiring | 02 | ✅ Done | 4/4 | [PHASE_03__panel-wiring.md](PHASE_03__panel-wiring.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 carries no Open research items - no blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states no showcase entry, only an `ALL_FEATURES` CHANGE record.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - two new classes are introduced.
- [ ] `/spec-check S1443` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1443`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-08 - Initial tactical plan authored by `/spec-tech`.
