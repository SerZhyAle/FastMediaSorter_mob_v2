# Tactical Plan: S0292 - vr-content-launch-ui

**Strategic spec:** [`../S0292_vr-content-launch-ui.md`](../S0292_vr-content-launch-ui.md)
**Companion dependency:** [`../S0295_vr-generic-immerse-playback-contract.md`](../S0295_vr-generic-immerse-playback-contract.md)
**Feature:** Player-first VR launch UI and generic immersive launch contract
**Tier:** 3 - Moderate
**Priority:** 75
**Status:** Partial

## Phases

| # | Phase | Goal | Status |
|---|------|------|--------|
| 01 | contract-foundation | finish shared launch/result/return transport and start use-case | Done |
| 02 | xr-host-roundtrip | make XR host accept generic launch input and return typed result to player/settings | Done |
| 03 | player-ui-entry | add badge, prompt, overflow fallback, and player-side launch manager | Done |
| 04 | validation-cleanup | builds, catalog, docs/features/changelog closure without device test | Done |

## Implementation checklist

- [x] Phase 01 - `StartVrPlaybackUseCase` exists and is the single launch entry for player + settings.
- [x] Phase 01 - `XrEntryGateway.createImmersiveIntent()` is implemented in both real and no-op flavors.
- [x] Phase 01 - shared return payload covers player round-trip without activity-specific code in `core/xr`.
- [x] Phase 02 - `DiagnosticXrActivity` reads `VrLaunchInput` and supports `DIAGNOSTIC_PLAYLIST` plus single-image `FILE_URI`.
- [x] Phase 02 - XR exit returns typed `VrLaunchResult` to either Settings or Player through the Home handoff path.
- [x] Phase 03 - player overlay badge exists in portrait and landscape layouts via shared include.
- [x] Phase 03 - one-time toggle-off prompt is persisted and dismissible.
- [x] Phase 03 - overflow item `Open in VR` is gated by the same surface-state logic as the badge.
- [x] Phase 03 - prelaunch unsupported paths stay in flat player and surface inline/snackbar feedback instead of launching broken immersive flow.
- [x] Phase 04 - `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` mention the new user-visible VR entry behavior.
- [x] Phase 04 - `assembleStandardDebug` passes.
- [x] Phase 04 - `assembleNoLegalDebug` passes.
- [x] Phase 04 - `scripts/catalog_sync.ps1 -Module app_v2` passes.
- [x] Phase 04 - no device test is claimed in the closure notes.

## Closure Notes

- Validation completed on 2026-05-25.
- `assembleStandardDebug` passed via `build-debug.PS1`.
- `assembleNoLegalDebug` passed via `build-debug.PS1`.
- `scripts/catalog_sync.ps1 -Module app_v2` passed.
- `scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix player_vr_` passed.
- Device testing was intentionally excluded from this closure round.
