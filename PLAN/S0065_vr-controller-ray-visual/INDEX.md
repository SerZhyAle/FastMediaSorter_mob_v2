# Tactical Plan: S0065 — vr-controller-ray-visual

**Strategic spec:** [`../S0065_vr-controller-ray-visual.md`](../S0065_vr-controller-ray-visual.md)
**Feature:** Visible controller-ray indicator in immersive
**Tier:** 3 — Moderate
**Priority:** 60
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ray-state-scaffolding | — | ✅ Done | 4/4 | [PHASE_01__ray-state-scaffolding.md](PHASE_01__ray-state-scaffolding.md) |
| 02 | gl-resource-lifecycle | 01 | ✅ Done | 3/3 | [PHASE_02__gl-resource-lifecycle.md](PHASE_02__gl-resource-lifecycle.md) |
| 03 | capture-endpoints | 01 | ✅ Done | 2/2 | [PHASE_03__capture-endpoints.md](PHASE_03__capture-endpoints.md) |
| 04 | per-eye-draw | 02, 03 | ✅ Done | 2/2 | [PHASE_04__per-eye-draw.md](PHASE_04__per-eye-draw.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 research items §6.1 and §6.2 are Resolved. §6.3 (`syncAim` existence) is closed by code reading: `OpenXrHandTracking::syncHandTracking` already computes aim-pose at lines 251-280; Phase 03.2 extends that block to emit endpoints into shared `ctx.rayState`.

Architecture refinement (resolved during tactical review): the strategic ADR-1 phrase "in the same FBO as HUD layer" is incorrect — HUD is delivered as a separate `XrCompositionLayerQuad` swapchain. The ray must be drawn into the **per-eye projection FBO** between `invokeRenderCallback` and `xrReleaseSwapchainImage` (`OpenXrFrame.cpp:140-183`). The visible result still satisfies §3.2 because the line stops at the HUD-plane intersection where the HUD composition layer overdraws it; there is no z-fighting because the HUD lives in a separate swapchain.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-facing — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (cpp-only changes do not enter the catalog, but Kt-side touchpoints in Phase 03 do).
- [ ] `/spec-check S0065` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0065`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
