# Tactical Plan: S0127 — image-player-draw-crop-immersive

**Strategic spec:** [`../S0127_image-player-draw-crop-immersive.md`](../S0127_image-player-draw-crop-immersive.md)
**Feature:** Immersive mode in Draw and Crop in image player
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-09

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | state-foundation | — | ✅ Done | 3/3 | [PHASE_01__state-foundation.md](PHASE_01__state-foundation.md) |
| 02 | edit-mode-callbacks | 01 | ✅ Done | 3/3 | [PHASE_02__edit-mode-callbacks.md](PHASE_02__edit-mode-callbacks.md) |
| 03 | immersive-controller | 02 | ✅ Done | 4/4 | [PHASE_03__immersive-controller.md](PHASE_03__immersive-controller.md) |
| 04 | crop-fullscreen-override | 03 | ✅ Done | 2/2 | [PHASE_04__crop-fullscreen-override.md](PHASE_04__crop-fullscreen-override.md) |
| 05 | pinch-passthrough | 03 | ✅ Done | 2/2 | [PHASE_05__pinch-passthrough.md](PHASE_05__pinch-passthrough.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — strategic §6 research items are Resolved (2026-05-09): full immersive (system bars + panels), no animation.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 entry).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class added in Phase 03).
- [ ] `/spec-check S0127` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0127`.

---

## Blockers Log

- _none yet_

---

## Change Log

- 2026-05-09 — Initial tactical plan authored by `/spec-tech`.
