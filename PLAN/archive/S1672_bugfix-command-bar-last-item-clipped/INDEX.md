# Tactical Plan: S1672 - bugfix-command-bar-last-item-clipped

**Strategic spec:** [`../S1672_bugfix-command-bar-last-item-clipped.md`](../S1672_bugfix-command-bar-last-item-clipped.md)
**Research inputs:** none
**Feature:** Main-screen command bar - adaptive overflow instead of the fixed single-victim rule
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-08-15

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | command-bar-planner | - | ✅ Done | 2/2 | [PHASE_01__command-bar-planner.md](PHASE_01__command-bar-planner.md) |
| 02 | chrome-manager-wiring | 01 | ✅ Done | 4/4 | [PHASE_02__chrome-manager-wiring.md](PHASE_02__chrome-manager-wiring.md) |
| 03 | overflow-menu-host | 02 | ✅ Done | 3/3 | [PHASE_03__overflow-menu-host.md](PHASE_03__overflow-menu-host.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - strategic §3.3 carries every owner decision this plan needs (victim order, overflow destination, label rollback, `layout-w600dp` coverage).

---

## Planning findings that changed the strategic plan

- **No XML edit is needed.** Strategic §3 item 5 assumed the "⋮" menu requires a new button in all three layout variants. `btnMainDropdownMenu` (icon `ic_more_vert`, wrapper `layoutMainDropdownMenu`) already exists in `layout/`, `layout-land/` and `layout-w600dp/` `activity_main.xml`, so this plan reuses it as the single overflow anchor instead of adding a second three-dots button next to it. Recorded in the strategic spec under "Implementation State".
- **The browse-screen precedent is an existing menu host, not a dedicated one.** `ResourceOpsMenuManager` surfaces overflowed commands as extra conditional items inside the menu that screen already had. Reusing `btnMainDropdownMenu` is the same shape, which is what owner decision 2 ("как на экране просмотра файлов") describes.
- **The pure-planner precedent lives on this screen already.** `MainCollapsedChipPlacementPlanner` + `MainCollapsedChipPlacementPlannerTest` set the object/data-class/test shape Phase 01 follows, so the plan does not relocate `allocateCommandBar` out of the browse package.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic spec carries no §8 FEATURES sentence (bugfix).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - two new classes.
- [ ] `/spec-check S1672` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1672`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-15 - Initial tactical plan authored by `/spec-tech`.
