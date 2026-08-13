# Tactical Plan: S0410 - standalone-image-action-parity

**Strategic spec:** [`../S0410_standalone-image-action-parity.md`](../S0410_standalone-image-action-parity.md)
**Research inputs:** [`research/01__dialog-extraction.md`](research/01__dialog-extraction.md), [`research/02__save-target-temp-lifecycle.md`](research/02__save-target-temp-lifecycle.md), [`research/03__draw-save-nonlocal.md`](research/03__draw-save-nonlocal.md)
**Feature:** Full parity of static-image actions in the specialized standalone viewer (translation/OCR settings, draw overlay, crop-to-file/compress for non-local images)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-06-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | uri-materialization | - | ✅ Done | 2/2 | [PHASE_01__uri-materialization.md](PHASE_01__uri-materialization.md) |
| 02 | settings-dialog-extract | - | ✅ Done | 2/2 | [PHASE_02__settings-dialog-extract.md](PHASE_02__settings-dialog-extract.md) |
| 03 | standalone-settings-crop | 01, 02 | ✅ Done | 3/3 | [PHASE_03__standalone-settings-crop.md](PHASE_03__standalone-settings-crop.md) |
| 04 | standalone-draw-overlay | - | ✅ Done | 3/3 | [PHASE_04__standalone-draw-overlay.md](PHASE_04__standalone-draw-overlay.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 research items 1-3 are Resolved (see `research/`). All blockers cleared - Phase 01 may start.

- [x] **Research §6.1 - dialog extraction without main-player regression:** Resolved. `showTranslationSettingsDialog` is binding-free except a player-specific post-save apply step, lifted into a `TranslationSettingsDialog` helper with an optional `onApplied` hook (in-app passes it; standalone passes none). See `research/01__dialog-extraction.md`.
- [x] **Research §6.2 - save target & temp lifecycle:** Resolved. `ImageCropManager` already saves to Downloads + MediaScanner for `saveTo = null`; only a materialized local source path is missing. No custom Pictures-publish - Step 03.4 dropped. See `research/02__save-target-temp-lifecycle.md`.
- [x] **Research §6.3 - draw save on non-local:** Resolved. Draw saves via host callbacks + `MergeDrawOverlayUseCase`; standalone saves the merged result to Downloads, sharing the crop save mechanism. See `research/03__draw-save-nonlocal.md`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update only if strategic §8 confirms a new user-facing capability sentence (decide in Phase 05; default skip if "распространение уже описанных возможностей").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new use case + dialog helper are public API).
- [ ] `/spec-check S0410` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0410`.

---

## Blockers Log

- 2026-06-13 - Phases gated on strategic §6 items 1-3 (Open). Resolved via `research/` (all 3); blockers cleared.
- 2026-06-13 - Implementation paused after Phase 03 (build-green). Phases 01-03 done: materialization use case, binding-free `TranslationSettingsDialog` (in-app delegates, unchanged), standalone settings menu + crop-to-file/compress for non-local images. Phase 04 (draw overlay) deferred to a focused pass: it is a UI-subsystem port, not a gate change - needs the `draw_overlay_toolbar_stub` ViewStub added to `activity_standalone_photo_video.xml` (portrait + landscape) plus a `StandaloneDrawSaveHelper` mirroring `PlayerDrawingSaveHelper` (baseBitmapProvider, save/save-as/cancel callbacks, merge via `MergeDrawOverlayUseCase`, save to Downloads, displayRect crop) + `enterDrawMode`/back-press wiring. Resume: `/spec-dev S0410 --phase 04`.

---

## Change Log

- 2026-06-13 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-13 - All 5 phases implemented; full debug build green; status -> BlockNeedUserTest.
- 2026-06-13 - Emulator smoke (local image): all new overflow items present, draw toolbar mounts. Caught a crash on draw Save (`IllegalArgumentException: x + width must be <= bitmap.width()` in `cropOverlayToImage`, uncaught). Fixed in `StandaloneDrawSaveHelper`: strict crop-rect clamp, whole save wrapped in try/catch, save target switched to MediaStore Pictures (scoped-storage-correct). Re-tested: draw Save writes to Pictures, no crash.
