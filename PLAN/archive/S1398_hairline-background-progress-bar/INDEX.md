# Tactical Plan: S1398 - hairline-background-progress-bar

**Strategic spec:** [`../S1398_hairline-background-progress-bar.md`](../S1398_hairline-background-progress-bar.md)
**Research inputs:** none - strategic §6.9 carries the code-level inventory inline, and §6.1-§6.8 are all Resolved.
**Feature:** Hairline background-operation progress bar at the bottom of every screen except Browse
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done - awaiting device test
**Phases:** 4 / 4 done
**Last updated:** 2026-08-10

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | indicator-view | - | ✅ Done | 4/4 | [PHASE_01__indicator-view.md](PHASE_01__indicator-view.md) |
| 02 | progress-observer | 01 | ✅ Done | 2/2 | [PHASE_02__progress-observer.md](PHASE_02__progress-observer.md) |
| 03 | base-activity-host | 01, 02 | ✅ Done | 3/3 | [PHASE_03__base-activity-host.md](PHASE_03__base-activity-host.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 item is Resolved: §6.2, §6.3, §6.7 and §6.8 by owner rulings dated 2026-08-10 and recorded in §3.3; §6.1, §6.4, §6.5 and §6.6 as consequences of the architecture, the code and the owner's captured text.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped as planned; `/skill-release` owns the showcase and strategic §8 routes this capability to `docs/ALL_FEATURES.jsonl`, where record `file-transfer.background-progress-hairline` now sits.
- [x] `dev/CHANGELOG.md` has an entry per phase, four in total, one per logical change.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - four new classes, each with a role and `status=new` (the plan said three; the state model is its own file).
- [ ] `/spec-check S1398` returns `Verified` - blocked on the device test; no device attached.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` - same block. Ticket currently `BlockNeedUserTest`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1398`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-10 - Initial tactical plan authored by `/spec-tech`.
