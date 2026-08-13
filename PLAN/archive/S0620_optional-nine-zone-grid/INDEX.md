# Tactical Plan: S0620 - optional-nine-zone-grid

**Strategic spec:** [`../S0620_optional-nine-zone-grid.md`](../S0620_optional-nine-zone-grid.md)
**Research inputs:** none
**Feature:** Optional 9-zone touch grid (toggle to a simpler 3-zone player layout)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done - all phases implemented; spec advanced to BlockNeedUserTest (on-device gate)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-23

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-storage | - | ✅ Done | 5/5 | [PHASE_01__settings-storage.md](PHASE_01__settings-storage.md) |
| 02 | touch-zone-resolver | 01 | ✅ Done | 4/4 | [PHASE_02__touch-zone-resolver.md](PHASE_02__touch-zone-resolver.md) |
| 03 | three-zone-command-access | 02 | ✅ Done | 3/3 | [PHASE_03__three-zone-command-access.md](PHASE_03__three-zone-command-access.md) |
| 04 | settings-ui-and-strings | 01 | ✅ Done | 5/5 | [PHASE_04__settings-ui-and-strings.md](PHASE_04__settings-ui-and-strings.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. The one strategic §6 item (left-area geometry) is Resolved by the Quiz 2026-06-23 + pinned to a concrete value in Phase 03; device-test confirms it.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*` - NOT edited per-spec; the capability is recorded to `docs/ALL_FEATURES.jsonl` (Phase 05) and surfaces in FEATURES only via `/skill-release`. Strategic §8 supplies the EN/RU/UK showcase sentence for that later diff.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0620` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0620`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-23 - Initial tactical plan authored by `/spec-tech` (via `/spec-all` / `/spec-next`).
- 2026-06-23 - Implemented all 5 phases; spec -> BlockNeedUserTest. Deviations from the plan, reconciled against live code:
  - Phase 01: `SettingsManager.kt` was NOT touched - it owns a separate legacy `AppSettings` without the touch-zone field. The real DataStore persistence lives in `SettingsRepositoryImpl.kt` (new `KEY_NINE_ZONE_GRID_ENABLED`, read default `true`, write). Also mirrored the field into `DeviceProfilePresetApplier.kt` (not in the plan, kept the field fully wired).
  - Phase 02: the grid flag is read in the player via a new `TouchZoneCallback.getNineZoneGridEnabled()` -> `PlayerActivity.nineZoneGridEnabled` (cached in `PlayerObserverManager`, mirroring `loadFullSizeImages`). The hint-follows-flag logic lives in `PlayerUiStateCoordinator.determineTouchZoneHintType` (the actual hint-decision site), not `TouchZoneGestureManager`; grid-off fullscreen images now resolve to the existing `COMMAND_PANEL_3ZONE` hint (`hint_touch_zone_3zone`).
  - Phase 03: `get3ZoneFullscreenTapAction` added to `TouchZoneConfig`; fullscreen dispatch routes through it inside `handleTouchZone` (the real dispatch path) while preserving the reserved-bottom check for video. Keyboard/D-pad command-panel toggle confirmed independent of the grid (no change needed).
  - Phase 04: no per-field VM setter - the fragment uses the existing `updateSettings(current.copy(...))` path; the toggle is a `SettingsToggleRow` (`rowDisableNineZone`), and the 9-zone-specific views are grouped under `groupTouchZoneToggles` / `groupTouchZoneScheme` / `groupTouchZoneLegend` for visibility. The explanation string says "Tap the left edge.." (the design is a tap, not a swipe as an early draft string read).
  - Build note: full Kotlin/Java/Hilt compile validated (`fk`, `fc`, and the manifest-export build all green); the final `assembleStandardDebug` packaging step hit a transient `R.jar` file lock from concurrent daemons - rebuild needed for the installable APK.
