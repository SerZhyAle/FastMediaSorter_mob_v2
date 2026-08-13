# Tactical Plan: S1175 - launcher-google-maps-integration

**Strategic spec:** [`../S1175_launcher-google-maps-integration.md`](../S1175_launcher-google-maps-integration.md)
**Feature:** Google Maps integration in Launcher Mode
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-08-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | geographic-command | - | ✅ Done | 2/2 | [PHASE_01__geographic-command.md](PHASE_01__geographic-command.md) |
| 02 | place-share-ingress | 01 | ✅ Done | 2/2 | [PHASE_02__place-share-ingress.md](PHASE_02__place-share-ingress.md) |
| 03 | map-data-foundation | 01 | ✅ Done | 3/3 | [PHASE_03__map-data-foundation.md](PHASE_03__map-data-foundation.md) |
| 04 | map-gadget | 03 | ✅ Done | 3/3 | [PHASE_04__map-gadget.md](PHASE_04__map-gadget.md) |
| 05 | docs-catalog-cleanup | 02, 04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

## Pre-Implementation Blockers

- [x] S1205 is Verified; the pinned-shortcut path already fulfills the tracked-person goal.
- [x] The owner selected documented `google.navigation:q=` as the immediate-navigation action.
- [x] The owner selected a launcher-scoped, separately labelled share alias.
- [x] The owner selected OSM tiles behind a provider seam with caching and attribution.

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/ALL_FEATURES.jsonl` records the shipped capability.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S1175` returns `Verified` after device acceptance.

## How to Track Progress

1. Before a phase, mark its row `🚧 In Progress`.
2. Mark a step `[x]` only after its current verification passes.
3. Mark the phase `✅ Done` only after its done criteria pass.
4. Finish with `/spec-check S1175`; it owns the `Verified` transition.

## Blockers Log

- None.

## Change Log

- 2026-08-10 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-11 - Phase 02 closed: share parsing extracted behind `LauncherPlaceShareParser` and covered by tests, share alias given its own icon.
- 2026-08-11 - Phases 03-05 closed: keyless OSM map data path, launcher map gadget, capability records, and device-test handoff. Three audit rounds; the second and third each found a defect introduced by the previous round's fix, which is why `OsmMapTileProvider` now has its own suite.
