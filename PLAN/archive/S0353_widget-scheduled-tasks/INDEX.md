# Tactical Plan: S0353 - widget-scheduled-tasks

**Strategic spec:** [`../S0353_widget-scheduled-tasks.md`](../S0353_widget-scheduled-tasks.md)
**Feature:** Scheduled Tasks Manager home-screen widget (2x1 / 2x2)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (all phases done; awaiting on-device verification)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | scheduler-controls | - | ✅ Done | 6/6 | [PHASE_01__scheduler-controls.md](PHASE_01__scheduler-controls.md) |
| 02 | widget-surface | 01 | ✅ Done | 7/7 | [PHASE_02__widget-surface.md](PHASE_02__widget-surface.md) |
| 03 | wiring-refresh-nav | 01, 02 | ✅ Done | 3/3 | [PHASE_03__wiring-refresh-nav.md](PHASE_03__wiring-refresh-nav.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. All strategic §6 research items are Resolved (durable source = Room `scheduled_operations`, active = `is_enabled`, upcoming = order by `next_run_at`, empty-state pattern from Favorites widget, serialized Run All, durable pause, event-driven refresh, all-flavor exposure).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a Smart Widgets sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes added).
- [ ] `/spec-check S0353` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of the Block* states.
5. All done: flip `Status:` to `Done`, run `/spec-check S0353`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-04 - Initial tactical plan authored by `/spec-tech`.
