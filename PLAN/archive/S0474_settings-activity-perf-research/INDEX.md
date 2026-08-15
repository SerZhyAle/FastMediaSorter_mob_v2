# Tactical Plan: S0474 - settings-activity-perf-research

**Strategic spec:** [`../S0474_settings-activity-perf-research.md`](../S0474_settings-activity-perf-research.md)
**Research inputs:** [`research/01__settings-screen-architecture.md`](research/01__settings-screen-architecture.md) · [`research/02__load-cost-bottlenecks.md`](research/02__load-cost-bottlenecks.md) · [`research/03__embedded-tables-inventory.md`](research/03__embedded-tables-inventory.md) · [`research/04__improvement-options.md`](research/04__improvement-options.md) · [`research/05__android-large-settings-bestpractice.md`](research/05__android-large-settings-bestpractice.md)
**Feature:** Settings screen load-cost reduction (group A - invisible optimizations)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec. Group B (operations decomposition / list extraction) is out of scope here - see S0479.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | host-offscreen-search-index | - | ✅ Done | 3/3 | [PHASE_01__host-offscreen-search-index.md](PHASE_01__host-offscreen-search-index.md) |
| 02 | operations-disk-read | - | ✅ Done | 2/2 | [PHASE_02__operations-disk-read.md](PHASE_02__operations-disk-read.md) |
| 03 | playback-pm-async | - | ✅ Done | 2/2 | [PHASE_03__playback-pm-async.md](PHASE_03__playback-pm-async.md) |
| 04 | media-lazy-sections | - | ✅ Done | 3/3 | [PHASE_04__media-lazy-sections.md](PHASE_04__media-lazy-sections.md) |
| 05 | docs-catalog-cleanup | 01,02,03,04 | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

> Phases 01-04 touch disjoint files and have no producer/consumer dependency; they may proceed in any order. The numbering reflects ascending implementation risk. Phase 05 depends on all.

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (artifacts in `research/`, owner decisions 2026-06-17). No blockers.

- [x] §6 research items 1-9 Resolved - no item blocks Phase 01.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 states "Без изменений" (group A is invisible to the user).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] On-device cold-open baseline captured before/after on a legacy-profile device (strategic §11.4) using existing DEBUG instrumentation (`SettingsActivity ready in Xms`).
- [ ] `/spec-check S0474` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0474`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`. Scope = group A only; group B split to S0479.
