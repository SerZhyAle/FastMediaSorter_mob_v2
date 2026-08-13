# Tactical Plan: S0675 - stream-grid-frame-capture

**Strategic spec:** [`../S0675_stream-grid-frame-capture.md`](../S0675_stream-grid-frame-capture.md)
**Research inputs:** [`research/01__stream-architecture-and-frame-capture.md`](research/01__stream-architecture-and-frame-capture.md)
**Feature:** Stream browser grid mode with live-frame capture
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 6 / 6 done
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 2/2 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | frame-cache | 01 | ✅ Done | 1/1 | [PHASE_02__frame-cache.md](PHASE_02__frame-cache.md) |
| 03 | snapshot-engine | 02 | ✅ Done | 2/2 | [PHASE_03__snapshot-engine.md](PHASE_03__snapshot-engine.md) |
| 04 | grid-cell-adapter | 02 | ✅ Done | 3/3 | [PHASE_04__grid-cell-adapter.md](PHASE_04__grid-cell-adapter.md) |
| 05 | grid-mode-wiring | 01,03,04 | ✅ Done | 5/5 | [PHASE_05__grid-mode-wiring.md](PHASE_05__grid-mode-wiring.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- No open research blockers. Strategic §6 resolved (snapshot mode determined by owner wording; residual params are tactical defaults).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 mandates a FEATURES sentence: add it in Phase 06.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes added).
- [ ] `/spec-check S0675` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0675`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-25 - Initial tactical plan authored by `/spec-tech`.
