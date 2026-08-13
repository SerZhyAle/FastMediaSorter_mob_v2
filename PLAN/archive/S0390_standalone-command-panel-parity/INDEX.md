# Tactical Plan: S0390 - standalone-command-panel-parity (Group A)

**Strategic spec:** [`../S0390_standalone-command-panel-parity.md`](../S0390_standalone-command-panel-parity.md)
**Feature:** Type-specific Group A actions (crop / cropToFile / compress / draw-overlay / screen-rotate toggle) in the STANDALONE image host, placed by priority into bar↔overflow, gated by capability × media type × `SUPPORT_IMAGES`.
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** BlockNeedUserTest - Group A (crop/cropToFile/compress/rotate) implemented; `standard` build green; awaiting device-test
**Phases:** 5 / 5 done (draw-overlay deferred to follow-up phase 06)
**Last updated:** 2026-06-10

> **Scope:** tactical, English, developer handoff. Group A image actions only. Owner sign-off (2026-06-10): land **crop / cropToFile / compress / screen-rotate toggle** this iteration; **draw-overlay deferred** to follow-up phase 06 because it needs a standalone base-bitmap provider seam + a draw toolbar layout in both orientations + a ~300-LOC standalone save helper (the spec's "draw is generic-ready" premise was wrong - the engine is generic but `PlayerDrawingSaveHelper` is `PlayerActivity`-bound). Waves C (OCR/Lens/print/translate/save-frame/sleep-timer/lyrics) remain separate tickets. Engines (`ImageCropManager`, `CommandPanelLayoutPlanner`) are NOT edited - standalone wiring only, to keep the in-app player regression-safe.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | vm-editable-state-rotate | - | ✅ Done | 2/2 | [PHASE_01__vm-editable-state-rotate.md](PHASE_01__vm-editable-state-rotate.md) |
| 02 | crop-compress-controller | 01 | ✅ Done | 1/1 | [PHASE_02__crop-compress-controller.md](PHASE_02__crop-compress-controller.md) |
| 03 | layout-buttons-overflow | 01 | ✅ Done | 2/2 | [PHASE_03__layout-buttons-overflow.md](PHASE_03__layout-buttons-overflow.md) |
| 04 | activity-wiring-gate | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__activity-wiring-gate.md](PHASE_04__activity-wiring-gate.md) |
| 05 | strings-docs-catalog | 01-04 | ✅ Done | 3/3 | [PHASE_05__strings-docs-catalog.md](PHASE_05__strings-docs-catalog.md) |
| 06 | draw-overlay (follow-up) | 01-05 | ⬜ Not started | - | [PHASE_06__draw-overlay-followup.md](PHASE_06__draw-overlay-followup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Dependency:** S0389 (`supportsTypeSpecificActions` flag, standalone hosts, `ResolveLocalPathFromUriUseCase`) - implemented + committed (`25ec5345`), at `BlockNeedUserTest`. S0390 builds on already-landed code; does not require S0389 device-test to pass first (additive, image-only).

---

## Completion Gate

- [x] All phases ✅ Done (Group A; draw deferred to 06).
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8).
- [x] No new strings - reused existing localized keys (`menu_crop`, `menu_crop_to_file`, `menu_compress_copy`, `rotation_toggle_sensor_*_desc`), so EN/RU/UK parity already holds.
- [x] `dev/CHANGELOG.md` entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (`StandaloneImageEditController` indexed, role/status filled).
- [x] `standard` debug build green (proves all S0390 `src/main` code compiles).
- [ ] `photos` debug build: currently fails on a PRE-EXISTING, unrelated breakage - `TesseractManager.kt` (src/main) references `TessBaseAPI`, but the in-flight S0386 OCR de-bundling (uncommitted `build.gradle.kts`) gives `tesseract4android` only to `standard`/`legacy`, not `photos`. S0390 touches no OCR code; `photos` uses the same `src/main` as `standard`, so S0390 is photos-safe. Re-verify `photos` once S0386 lands.
- [x] Status → `BlockNeedUserTest` with a device-test note (external-intent crop/cropToFile/compress/rotate scenarios).

---

## Architecture Decisions (from strategic §6, resolved 2026-06-10)

- Reuse `CommandPanelLayoutPlanner.planLayout()` (pure geometry); introduce a small standalone Group A command enum. Do NOT touch `buildActiveCommands` / `CommandPanelAvailabilityUpdater` / `CommandPanelController`.
- Engines generic and untouched: `ImageCropManager`, `ImageDrawOverlayManager`. New standalone-side controllers wire them to the standalone layout/VM.
- Bar buttons = real `ImageButton`s in `topCommandPanel`; overflow spills into the existing `PopupMenu` (`overflow_menu_standalone_player.xml`).
- Rotate = screen-rotation sensor toggle (`StandalonePlayerViewModel` method + `ScreenRotationManager`), mirroring in-app `ROTATION_TOGGLE`. NOT pixel rotation.
- Gate: media is static bitmap (image, not `.gif`/`.apng`) × resolves to local writable path (`ResolveLocalPathFromUriUseCase`) × `SUPPORT_IMAGES`. content:// without a writable path → save-to-Downloads fallback (mirror `ImageCropManager.resolveDestinationPath`), not hidden.
- No `BuildConfig.IS_*` flavor guards in `src/main`; `SUPPORT_IMAGES` is true in every external-entry flavor.

---

## Blockers Log

- 2026-06-10 - `photos` debug compile fails on `TesseractManager.kt` (`TessBaseAPI` unresolved). Root cause: in-flight S0386 OCR de-bundling (uncommitted `build.gradle.kts`) scopes `tesseract4android` to `standard`/`legacy` only, while `TesseractManager.kt` still lives in `src/main`. Not an S0390 regression (S0390 touches no OCR code; `standard` builds green). Re-verify `photos` after S0386 lands or moves `TesseractManager` to a flavor/feature source set.

---

## Change Log

- 2026-06-10 - Initial tactical plan authored from android-solution-researcher report.
