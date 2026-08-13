# Tactical Plan: S0844 - camera-capture-activity-detekt-baseline-drift

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Research inputs:** none (research performed inline by `/spec-all`, findings captured in strategic spec §0/§4)
**Feature:** Fix CameraCaptureActivity detekt LargeClass/TooManyFunctions baseline drift
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 40
**Status:** Done
**Phases:** 8 / 8 done
**Last updated:** 2026-07-02

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | mechanical-fixes | - | ✅ Done | 4/4 | [PHASE_01__mechanical-fixes.md](PHASE_01__mechanical-fixes.md) |
| 02 | overlay-rotation-manager | 01 | ✅ Done | 2/2 | [PHASE_02__overlay-rotation-manager.md](PHASE_02__overlay-rotation-manager.md) |
| 03 | zoom-controls-manager | 02 | ✅ Done | 2/2 | [PHASE_03__zoom-controls-manager.md](PHASE_03__zoom-controls-manager.md) |
| 04 | capture-result-manager | 03 | ✅ Done | 2/2 | [PHASE_04__capture-result-manager.md](PHASE_04__capture-result-manager.md) |
| 05 | save-destination-label-manager | 02,04 | ✅ Done | 2/2 | [PHASE_05__save-destination-label-manager.md](PHASE_05__save-destination-label-manager.md) |
| 06 | gesture-callback-handler | 03 | ✅ Done | 2/2 | [PHASE_06__gesture-callback-handler.md](PHASE_06__gesture-callback-handler.md) |
| 07 | settings-callback-handler | 01 | ✅ Done | 2/2 | [PHASE_07__settings-callback-handler.md](PHASE_07__settings-callback-handler.md) |
| 08 | docs-catalog-cleanup | 02,03,04,05,06,07 | ✅ Done | 3/3 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 has no open research items.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8: "Без изменений").
- [x] `dev/CHANGELOG.md` has entry for every modified file (see final dev-log batch).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new classes added).
- [x] `/spec-check S0844` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/8 done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status.
5. All done: flip `Status:` to `Done`, run `/spec-check S0844`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-07-02 - Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
