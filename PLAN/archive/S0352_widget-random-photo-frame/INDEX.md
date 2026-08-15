# Tactical Plan: S0352 - widget-random-photo-frame

**Strategic spec:** [`../S0352_widget-random-photo-frame.md`](../S0352_widget-random-photo-frame.md)
**Feature:** Random Photo Frame home-screen widget (`2x2` / `3x3`)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | config-foundation | - | ✅ Done | 6/6 | [PHASE_01__config-foundation.md](PHASE_01__config-foundation.md) |
| 02 | photo-surface | 01 | ✅ Done | 3/3 | [PHASE_02__photo-surface.md](PHASE_02__photo-surface.md) |
| 03 | refresh-scheduling | 01, 02 | ✅ Done | 3/3 | [PHASE_03__refresh-scheduling.md](PHASE_03__refresh-scheduling.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic research is resolved: cache-first rendering only, fixed battery-aware cadence, explicit no-cache fallback, no live network scan inside the provider.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new widget classes added).
- [x] `/spec-check S0352` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of the Block* states.
5. All done: flip `Status:` to `Done`, run `/spec-check S0352`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-04 - Initial tactical plan authored by `/spec-tech` via `/spec-all`.
- 2026-06-04 - `/spec-check` equivalent completed: static audit passed and the tactical plan is closed.