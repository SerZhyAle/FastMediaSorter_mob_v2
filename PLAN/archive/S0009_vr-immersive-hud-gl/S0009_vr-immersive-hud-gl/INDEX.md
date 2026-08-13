# Tactical Plan: S0009 — VR Immersive HUD через отдельный композитный слой OpenXR

**Strategic spec:** [`../S0009_vr-immersive-hud-gl.md`](../S0009_vr-immersive-hud-gl.md)
**Feature:** VR Immersive HUD через отдельный композитный слой OpenXR
**Tier:** 3 — Moderate (ad-hoc)
**Status:** Partial (code-level Done; on-device acceptance pending)
**Phases:** 7 / 7 done
**Last updated:** 2026-05-02

> **Scope of this document:** tactical, English, developer handoff. Every step has an explicit verification predicate. Strategic rationale lives in `../spec_vr-immersive-hud-gl.md`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
| - | ----- | ---------- | ------ | -----: | ---- |
| 01 | foundations | — | ✅ Done | 5/5 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | composition-layer | 01 | ✅ Done | 5/5 | [PHASE_02__composition-layer.md](PHASE_02__composition-layer.md) |
| 03 | bitmap-upload | 02 | ✅ Done | 4/4 | [PHASE_03__bitmap-upload.md](PHASE_03__bitmap-upload.md) |
| 04 | scene-composer | 03 | ✅ Done | 4/4 | [PHASE_04__scene-composer.md](PHASE_04__scene-composer.md) |
| 05 | event-routing | 04 | ✅ Done | 5/5 | [PHASE_05__event-routing.md](PHASE_05__event-routing.md) |
| 06 | transitional-guard | 05 | ✅ Done | 4/4 | [PHASE_06__transitional-guard.md](PHASE_06__transitional-guard.md) |
| 07 | docs-catalog-cleanup | 01..06 | ✅ Done | 5/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`.

---

## Pre-Implementation Blockers

None of the `Open` research items in strategic §6 block Phase 01 — all have fixed start-defaults. The items below are verification checkpoints during implementation, not pre-start blockers:

- [ ] **Research §6.1:** Quest 3 runtime blending of transparent quad layer over equirect/cylinder/cinema video layer. Verify on device during Phase 02.
- [ ] **Research §6.3:** HUD swapchain lifecycle across `onPause`/`onResume` cycles. Verify on device during Phase 05.
- [ ] **Research §6.4:** HUD quad placement (1.0 m × 0.3 m at 1.5 m, −20° below gaze) ergonomics. Verify on device during Phase 05.

All three may force parameter tuning but do not alter the architecture.

---

## Field-log — Quest 3 2026-05-02

`logs/fastmediasorter_20260502_035656.log` confirms passive HUD pipeline is live:

- `VrPlayerActivity: HUD scene driver active (immersive)` at frame 1 — ✅ scene driver started
- `VR_PERF: hud_swapchain=76ms` + `panel_swapchain=114ms` — ✅ two independent swapchains created (ADR-1)
- `vr_hud_guard_controls`/`_file_ops` banner triggered correctly on `OpenControls`/`OpenFileOps` (Phase 06 transitional guard works as designed)
- Volume/zoom/recenter commands go through `VrHudSink` interface — `showVolumeIndicator`, `showZoomIndicator`, `showRecenterFlash` all implemented in [`VrHudSceneDriver.kt`](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt)
- `VrHudSceneDriver.updateProgress` ([line 238-252](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt#L238-L252)) implemented — progress bar updates do not auto-extend visibility window (correct per inline WHY)

**Outstanding (manual on-device acceptance):**

- [ ] §11.1 — progress bar visually appears during pause/seek/file-change with ~3 s auto-hide
- [ ] §11.2 — each of 8 indicators (pause/seek/volume/zoom/file/recenter/mode/repeat) visually pops up on its trigger
- [ ] §11.4 — immersive ↔ phone transition without indicator desync (note: blocked by S0038 home-intent regression — exiting immersive recreates VrPlayerActivity instead of returning to back-stack)
- [ ] §11.6 — idle suppression: in idle state HUD swapchain is not added to composition (verify via `VR_PERF` absence of submit lines in idle)

**User-facing symptom (logged 2026-05-02):** «вместо HUD с инструментами вижу только надпись 'выйдите в обычный режим'». **Root cause is in S0008**, not here — S0009 ADR-3 transitional-guard fires `vr_hud_guard_controls` banner because `S0008.VR_UI_COMPOSITION_LAYER_ENABLED=false` keeps the interactive panel locked. S0009 is functioning correctly within its passive-only scope; remove the perceived regression by fixing S0008 (then ADR-3 guard becomes obsolete per Proposal P-1 in S0019).

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
