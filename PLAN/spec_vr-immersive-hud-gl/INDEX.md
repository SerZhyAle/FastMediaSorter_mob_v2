# Tactical Plan: vr-immersive-hud-gl

**Strategic spec:** [`../spec_vr-immersive-hud-gl.md`](../spec_vr-immersive-hud-gl.md)
**Feature:** VR Immersive HUD через отдельный композитный слой OpenXR
**Tier:** 3 — Moderate (ad-hoc)
**Status:** Not started
**Phases:** 0 / 7 done
**Last updated:** 2026-04-25

> **Scope of this document:** tactical, English, developer handoff. Every step has an explicit verification predicate. Strategic rationale lives in `../spec_vr-immersive-hud-gl.md`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|------------|--------|------:|------|
| 01 | foundations | — | ⬜ Not started | 0/5 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | composition-layer | 01 | ⬜ Not started | 0/5 | [PHASE_02__composition-layer.md](PHASE_02__composition-layer.md) |
| 03 | bitmap-upload | 02 | ⬜ Not started | 0/4 | [PHASE_03__bitmap-upload.md](PHASE_03__bitmap-upload.md) |
| 04 | scene-composer | 03 | ⬜ Not started | 0/4 | [PHASE_04__scene-composer.md](PHASE_04__scene-composer.md) |
| 05 | event-routing | 04 | ⬜ Not started | 0/5 | [PHASE_05__event-routing.md](PHASE_05__event-routing.md) |
| 06 | transitional-guard | 05 | ⬜ Not started | 0/4 | [PHASE_06__transitional-guard.md](PHASE_06__transitional-guard.md) |
| 07 | docs-catalog-cleanup | 01..06 | ⬜ Not started | 0/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`.

---

## Pre-Implementation Blockers

None of the `Open` research items in strategic §6 block Phase 01 — all have fixed start-defaults. The items below are verification checkpoints during implementation, not pre-start blockers:

- [ ] **Research §6.1:** Quest 3 runtime blending of transparent quad layer over equirect/cylinder/cinema video layer. Verify on device during Phase 02.
- [ ] **Research §6.3:** HUD swapchain lifecycle across `onPause`/`onResume` cycles. Verify on device during Phase 05.
- [ ] **Research §6.4:** HUD quad placement (1.0 m × 0.3 m at 1.5 m, −20° below gaze) ergonomics. Verify on device during Phase 05.

All three may force parameter tuning but do not alter the architecture.

---

## Completion Gate

The feature is Done when **every** item below is ticked:

- [ ] All phases show ✅ Done in the Phase Overview.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated per strategic §8.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after Phase 07.
- [ ] `/spec-check vr-immersive-hud-gl` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. **Before starting a phase:** flip its row to `🚧 In Progress` in the Phase Overview. Update `Phases: X/N done` at the top.
2. **During a phase:** inside the phase file, flip each step's `Status:` line to `[~] in progress` when you start it, `[x] done` when its Verification passes. Never flip a step to `[x]` on intent — only on verified signal.
3. **On phase completion:** confirm every step is `[x]`, then confirm every item in the phase's "Phase Done Criteria". Flip the phase row in this INDEX to `✅ Done` and bump the counter.
4. **If blocked:** flip the row to `⛔ Blocked`, add a bullet to "Blockers Log" below with date + cause + next action.
5. **On all phases done:** flip this file's top `Status:` to `Done` and run `/spec-check vr-immersive-hud-gl` for the final audit.

---

## Blockers Log

- _(empty)_

---

## Change Log

- 2026-04-25 — Initial tactical plan authored by `/spec-tech`.
