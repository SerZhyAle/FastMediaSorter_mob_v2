# Tactical Plan: S1658 - bugfix-camera-viewfinder-zoom-focus

**Strategic spec:** [`../S1658_bugfix-camera-viewfinder-zoom-focus.md`](../S1658_bugfix-camera-viewfinder-zoom-focus.md)
**Research inputs:** none
**Feature:** Viewfinder aspect selection drives the live stream; every lens remembers its own capture set
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** In Progress
**Phases:** 7 / 7 done
**Last updated:** 2026-08-15

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | aspect-selection-model | - | ✅ Done | 3/3 | [PHASE_01__aspect-selection-model.md](PHASE_01__aspect-selection-model.md) |
| 02 | stream-follows-selection | 01 | ✅ Done | 3/3 | [PHASE_02__stream-follows-selection.md](PHASE_02__stream-follows-selection.md) |
| 03 | full-screen-preview-crop | 02 | ✅ Done | 5/5 | [PHASE_03__full-screen-preview-crop.md](PHASE_03__full-screen-preview-crop.md) |
| 04 | aspect-picker-ui | 03 | ✅ Done | 3/3 | [PHASE_04__aspect-picker-ui.md](PHASE_04__aspect-picker-ui.md) |
| 05 | lens-settings-memory | 01 | ✅ Done | 4/4 | [PHASE_05__lens-settings-memory.md](PHASE_05__lens-settings-memory.md) |
| 06 | lens-settings-restore | 05 | ✅ Done | 5/5 | [PHASE_06__lens-settings-restore.md](PHASE_06__lens-settings-restore.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §3.3 records the owner's rulings for both mechanisms this plan implements, and §6 carries no open research item: the sub-1x zoom claim that needed a device measurement was moved out to S1675.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic spec has no §8 FEATURES sentence, and the showcase is `/skill-release`-owned.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - two new classes ship.
- [ ] `/spec-check S1658` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1658`.

---

## Facts established while planning

Read once here so no phase re-derives them.

1. `camera_aspect_ratio` in `CaptureSettingsStore` holds the raw CameraX constant: `AspectRatio.RATIO_4_3` is `0`, `AspectRatio.RATIO_16_9` is `1`. A third selection therefore takes `2`, and both existing values keep their meaning without a migration.
2. `CameraUseCaseFactory.effectiveAspectRatioInt()` returns `PHOTO_ASPECT_RATIO` unconditionally on the photo branch, so the live stream is 4:3 whatever the user picked. That single expression is the defect of strategic §2.1.
3. `PHOTO_ASPECT_RATIO` has two other readers: `CameraSettingsDialogFragment` filters the resolution dropdown by it, and `HeadlessPhotoCapturer` crops the widget shot by a hardcoded 16:9 test.
4. `shouldShowResultFrame()` is photo-only and true only for 16:9. Once the stream carries the selection, its frame always coincides with the preview bounds, so both it and `ResultFrameOverlayView` lose their job. The overlay has exactly two other references: `previewScaleLinkedViews` in `CameraCaptureActivity:509` and the visibility flip at `:780`.
5. Per-lens state lives as flat fields on `CameraCaptureSessionManager:101-137` and is cleared wholesale by `bindLens()` at `:295-303`. The profile is dropped separately by `CameraCaptureFlowManager.onLensSwitch()` via `releaseWithoutClearing`.
6. `lastBoundLensId` (`:147`) already carries the lens identity across rebinds, and `CameraLensEntry.id` is what feeds it - strategic §3.2 names it as the memory key, so no new identity type is needed.
7. The camera screen has no `res/layout-land/` counterpart: S0754 locks it to portrait in the manifest. Rule 11 is satisfied by that absence, not by an omission.
8. `rowCameraAspect` is already registered in `docs/settings/settings-manifest.json` and `settings-annotations.json`, so phase 07 regenerates rather than registers.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-08-15 - Initial tactical plan authored by `/spec-tech`.
