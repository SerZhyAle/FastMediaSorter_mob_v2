# Tactical Plan: S0938 - pinned-stream-reorder

**Strategic spec:** [`../S0938_pinned-stream-reorder.md`](../S0938_pinned-stream-reorder.md)
**Research inputs:** [`research/01__index-determinism.md`](research/01__index-determinism.md) · [`research/02__order-consumers.md`](research/02__order-consumers.md)
**Feature:** Порядок закреплённых трансляций
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 3 / 3 done
**Last updated:** 2026-07-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | reorder-persistence | - | ✅ Done | 3/3 | [PHASE_01__reorder-persistence.md](PHASE_01__reorder-persistence.md) |
| 02 | list-grid-menu | 01 | ✅ Done | 5/5 | [PHASE_02__list-grid-menu.md](PHASE_02__list-grid-menu.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see `research/`). No blockers - Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` trilingual: **skip** - populated by `/skill-release` from the `ALL_FEATURES` diff, never per-spec (CLAUDE.md §11).
- [ ] `docs/ALL_FEATURES.jsonl` has the reorder capability record (via `scripts/all_features/add.ps1`).
- [ ] `dev/CHANGELOG.md` has an entry for the change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new use case class).
- [ ] `/spec-check S0938` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0938`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-04 - Initial tactical plan authored by `/spec-tech`.
