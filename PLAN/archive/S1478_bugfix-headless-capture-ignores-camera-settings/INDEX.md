# Tactical Plan: S1478 - bugfix-headless-capture-ignores-camera-settings

**Strategic spec:** [`../S1478_bugfix-headless-capture-ignores-camera-settings.md`](../S1478_bugfix-headless-capture-ignores-camera-settings.md)
**Research inputs:** none (AS-IS mapping performed inline during `/spec-all` F1 and recorded in strategic §1-§2)
**Feature:** Headless photo capture parity with the on-screen capture path
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | photo-only-use-case | - | ✅ Done | 2/2 | [PHASE_01__photo-only-use-case.md](PHASE_01__photo-only-use-case.md) |
| 02 | headless-target-rotation | 01 | ✅ Done | 2/2 | [PHASE_02__headless-target-rotation.md](PHASE_02__headless-target-rotation.md) |
| 03 | headless-lens-parity | 01 | ✅ Done | 2/2 | [PHASE_03__headless-lens-parity.md](PHASE_03__headless-lens-parity.md) |
| 04 | aspect-crop-extraction | 01 | ✅ Done | 4/4 | [PHASE_04__aspect-crop-extraction.md](PHASE_04__aspect-crop-extraction.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done |  2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 carries no Open research item - the compact bugfix template has no §6, and the AS-IS questions it would have held were resolved before approval and written into strategic §1-§2.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: the strategic spec adds no user-visible surface or string, so there is no showcase item.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - Phase 04 adds a class.
- [ ] `/spec-check S1478` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1478`.

---

## Verification reachable statically vs only on device

Strategic §4.2 requires this split to be stated rather than blurred, because none of the five affected classes carries a unit test.

- **Static (this plan's step predicates):** the headless path builds its `ImageCapture` through `CameraUseCaseFactory`; it applies a `targetRotation` sourced from `CameraOrientationManager`; it selects its lens through the shared initial-lens rule; the crop helper exists, is called by both paths, and restores EXIF.
- **On device only (Phase 05 hands these to the device-test gate):** file orientation with auto-rotate off, identical field of view between the two capture routes, identical proportions at 16:9, surviving GPS tags. No static predicate proves any of these.

---

## Blockers Log

- (none yet)
