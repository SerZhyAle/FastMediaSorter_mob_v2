# Tactical Plan: S1675 - camera-sub-1x-zoom-not-offered

**Strategic spec:** [`../S1675_camera-sub-1x-zoom-not-offered.md`](../S1675_camera-sub-1x-zoom-not-offered.md)
**Research inputs:** none as files - the API map was produced in-session and is reproduced in each phase's step bodies
**Feature:** Rear-lens pills on a lens whose own zoom range is a single point
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Not started
**Phases:** 3 / 3 done
**Last updated:** 2026-08-15

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | rear-lens-floors | - | ✅ Done | 3/3 | [PHASE_01__rear-lens-floors.md](PHASE_01__rear-lens-floors.md) |
| 02 | lens-pills-ui | 01 | ✅ Done | 3/3 | [PHASE_02__lens-pills-ui.md](PHASE_02__lens-pills-ui.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - strategic §3.2.1 and §3.3 carry the owner's variant, its scope limit, and the slider decision.

---

## Planning findings

- **The tap target needs no lens id.** `CameraCaptureSessionManager.switchCamera(targetEquivalentFloor)` resolves the lens itself through `lensReaching()`, so a pill only has to carry its equivalent floor value. The new capability property is therefore a plain `List<Float>`, not a list of lens handles, and stays free of Android types.
- **The guard must change, not disappear.** `CameraCaptureFlowManager.onCrossLensFloorSelected` returns early on `!showsCrossLensFloor`, and that property is false by definition on the widest lens. Deleting the guard outright would also admit the front camera, so it is rewritten to admit exactly the two cases that own a pill.
- **Pill tags must stay non-Float.** `CameraZoomControlsManager.syncSelection` matches pills by `Float` tag; a lens pill carrying a Float tag would be highlighted by zoom value rather than by active lens.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic spec carries no §8 FEATURES sentence (bugfix).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if the public API changed.
- [ ] `/spec-check S1675` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1675`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-15 - Initial tactical plan authored by `/spec-tech`.
