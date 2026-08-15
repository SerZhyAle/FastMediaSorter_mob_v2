# Tactical Plan: S1637 - activity-logic-debt-player-hosts

**Strategic spec:** [`../S1637_activity-logic-debt-player-hosts.md`](../S1637_activity-logic-debt-player-hosts.md)
**Research inputs:** none as files - the §6.1 measurement is recorded inline in the strategic spec §4 and §6.1
**Feature:** Remove the last 32 ActivityLogicViolation fields from the two player hosts
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 45
**Status:** Not started
**Phases:** 6 / 6 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | draw-overlay-reach-through | 02 | ✅ Done | 3/3 | [PHASE_01__draw-overlay-reach-through.md](PHASE_01__draw-overlay-reach-through.md) |
| 02 | image-edit-factory | - | ✅ Done | 2/2 | [PHASE_02__image-edit-factory.md](PHASE_02__image-edit-factory.md) |
| 03 | player-host-cluster | 02 | ✅ Done | 3/3 | [PHASE_03__player-host-cluster.md](PHASE_03__player-host-cluster.md) |
| 04 | standalone-host-cluster | 02 | ✅ Done | 4/4 | [PHASE_04__standalone-host-cluster.md](PHASE_04__standalone-host-cluster.md) |
| 05 | remaining-domain-fields | 03, 04 | ✅ Done | 2/2 | [PHASE_05__remaining-domain-fields.md](PHASE_05__remaining-domain-fields.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The single strategic §6 item (form of the shared edit cluster) is Resolved by measurement: 15 pass-through use sites, 0 behaviour calls, so the form is a factory.

---

## Field budget

The gate counts 32. This plan removes them in named groups; the sum is checked in Phase 06.

- Phase 01 removes 1 (`mergeDrawOverlayUseCase` in `PlayerActivity`).
- Phase 03 removes 8 (the remaining `PlayerActivity` cluster fields).
- Phase 04 removes 6 (all `PhotoVideoStandaloneActivity` cluster fields).
- Phase 05 removes 17 (11 in `PlayerActivity`, 6 in `PhotoVideoStandaloneActivity`).
- Total 32. Any deviation found during implementation is a plan defect - correct this table, do not silence the gate.

---

## Line-budget constraint

`PlayerActivity.kt` is 1425 LOC against the 1500 ceiling (CLAUDE.md Rule 2), so it has about 75 lines of headroom. Every phase touching it must end with the file no longer than it started. This is a Phase Done Criterion in phases 01, 03 and 05, not a hope.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений в docs/FEATURES."
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - a new class is added in Phase 02.
- [ ] `/spec-check S1637` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1637`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-14 - Phase 01 / 02 dependency inverted during implementation. Step 01.3 deletes the host's `mergeDrawOverlayUseCase` field, and the only construction site of `PlayerDrawingSaveHelper` (`PlayerManagerInitializer.kt:307`) has no other injectable source for the use case, so the factory built in Phase 02 must exist first. Phase 02 in turn needs nothing from Phase 01: it only adds a new class. Phase 01 also becomes Phase 02's consumption point, which is what step 02.2 asks for.
- 2026-08-14 - Phase 01 line budgets corrected against the tree: `PlayerDrawingSaveHelper.kt` 600 -> 680 (it already stood at 671 lines when the plan was written, so the figure was unreachable before any edit; the step adds 4, landing at 675), and the newly listed `PlayerManagerInitializer.kt` at 965 (959 today, 956 before the step). Rule 2's real ceiling is 1500 and is not in play for either file.
- 2026-08-14 - Step 01.1 was already satisfied in the tree before this run: `DrawCropCompositor` has taken `MergeDrawOverlayUseCase` by constructor since S0679. The strategic §6.1 line naming `DrawCropCompositor.kt:25,104` as reach-through sites was a mis-attributed grep - both lines are the constructor parameter and its use. The step's own Verification predicates pass as written, so the field budget is unchanged.
