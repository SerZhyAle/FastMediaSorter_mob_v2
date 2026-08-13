# Tactical Plan: S1441 - launcher-radio-toggle-tiles

**Strategic spec:** [`../S1441_launcher-radio-toggle-tiles.md`](../S1441_launcher-radio-toggle-tiles.md)
**Research inputs:** none as files - the AS-IS survey that drove this plan is recorded in strategic §6.5-§6.7
**Feature:** Wi-Fi and Bluetooth launcher tiles toggle the radio, falling back to the system screen
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | radio-control-contract | - | ✅ Done | 3/3 | [PHASE_01__radio-control-contract.md](PHASE_01__radio-control-contract.md) |
| 02 | radio-control-impl | 01 | ✅ Done | 4/4 | [PHASE_02__radio-control-impl.md](PHASE_02__radio-control-impl.md) |
| 03 | catalog-action-model | 01, 02 | ✅ Done | 3/3 | [PHASE_03__catalog-action-model.md](PHASE_03__catalog-action-model.md) |
| 04 | state-driven-icons | 03 | ✅ Done | 3/3 | [PHASE_04__state-driven-icons.md](PHASE_04__state-driven-icons.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Design fixed by this plan

Names and placements below are decided here so no step has to invent one.

- **Seam.** The radio component reuses the shape `NetworkMonitorContract` already established (strategic §6.7): `RadioControlContract` + `RadioKind` in `src/main/java/.../domain/radio/`, the real implementation in `src/networkMonitor/java/.../radio/`, the no-op in `src/networkMonitorDisabled/java/.../radio/` with its Hilt module beside the existing `NetworkMonitorModule`. `src/networkMonitor` is mounted by exactly `standard` and `noLegal` (`app_v2/build.gradle.kts` lines 644-651 and 676-680), which is also where the permissions may legally live.
- **Contract surface.** `val isToggleSupported: Boolean`, `fun state(kind: RadioKind): Flow<Boolean?>`, `suspend fun toggle(kind: RadioKind): Boolean`. `state` emits `null` for "unknown" exactly as `LauncherTrayBluetoothMonitor` already does. `toggle` returns `true` only when the observed state flipped (ADR-1).
- **Proof of success.** `toggle` awaits the first state change from `state(kind)` under `withTimeoutOrNull(RADIO_TOGGLE_CONFIRM_WINDOW_MS)`; the return value of `setWifiEnabled` / `BluetoothAdapter.enable()` is never trusted (ADR-1, strategic §5.2).
- **Single execution point.** `ToggleRadioTargetUseCase` in `domain/usecase/radio/`. Both `ExecuteLauncherCommandUseCase.launchOsShortcut` and `LaunchAppLaunchPanelTileUseCase`'s `OsShortcut` branch call it; neither keeps its own radio branch (strategic §5.1 pillar 3).
- **Catalog model.** `OsShortcutCatalog.Target` gains two defaulted fields - `radio: RadioKind? = null` and `fallbackIntent: ((Context) -> Intent)? = null` - so the other fifteen targets and all seventeen positional constructions stay untouched. `fallbackIntent` carries `Settings.Panel.ACTION_WIFI` for Wi-Fi on API 29+; the existing `intent` stays the last resort.
- **State on the tile.** New `ic_wifi_off` / `ic_bluetooth_off` vectors; the desktop resolve pipeline combines `observeCells()` with the two state flows so a radio change re-emits a different `LauncherCellUi` and passes `LauncherCellViewBinder`'s `lastBound` guard.
- **Not in scope.** No Start-menu rows (strategic §6.5), no runtime-permission request flow (strategic §6.6 - a missing `BLUETOOTH_CONNECT` is an ordinary fallback), no auto-rotate or other targets (strategic §6.4).

---

## Pre-Implementation Blockers

None - every strategic §6 item is Resolved.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/ALL_FEATURES.jsonl` carries the capability record; `docs/FEATURES*` stays untouched - it is `/skill-release`-owned.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - this ticket adds public classes.
- [ ] `/spec-check S1441` returns `Verified` - gated on the device test, the ticket is `BlockNeedUserTest`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1441`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-08 - Phases 04 and 05 executed by `/spec-dev`. Ticket moved to `BlockNeedUserTest` with three `Timber.d("S1441: ..")` probes at the toggle and the two tile-resolution entries; no device attached this session.
