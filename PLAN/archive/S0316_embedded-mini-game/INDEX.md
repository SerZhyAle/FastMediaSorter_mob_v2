# S0316 Tactical Plan - Embedded Mini-Game

Strategic spec: `PLAN/S0316_embedded-mini-game.md`
Feature: `Kryvavitsa and the Monster`
Status: `Implemented`

## Owner Decisions

- Autonomy: agent may decide Android-specific details with explicit assumptions.
- Flavor scope: all main app flavors, game disabled by default.
- Main menu: lower `btnMainDropdownMenu` group before Settings/secondary actions; hidden while disabled.
- Widget: disabled state opens Settings on the game toggle.
- Storage: app-local typed storage, no Room unless a later phase proves it necessary.
- Board scaling: presets fit to screen; large custom boards use zoom/pan and unreadable-size warnings.
- Wear OS: out of scope.

## Pre-Implementation Blockers

None. All strategic §6 research items were resolved by owner acceptance of the recommended package.

## Global Implementation Rules

- Before editing a file over 500 LOC, create a timestamped backup under `temp/`.
- Before editing a Kotlin/XML area, read the local comments/KDoc and preserve intent.
- After every `.kt` change, run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.
- After every `strings.xml` change, run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "game_"`.
- After every changed file or cohesive change batch, run `pwsh -NoProfile -File scripts/add_to_dev_log.ps1 "<path>" "S0316" "<description>"` or `scripts/post-change.ps1` where appropriate.
- Any edit under `res/layout/` must check and update the matching `res/layout-land/` file in the same phase.
- No `Log.d`; use Timber without permanent `S0316` ticket ids.
- Keep `MainActivity` and Activities thin; delegate game launch/menu logic to helpers/managers.

## Phase Order

- [x] Phase 01 - Domain Engine: `PHASE_01__domain-engine.md`
- [x] Phase 02 - Local State And Settings Model: `PHASE_02__local-state-settings-model.md`
- [x] Phase 03 - Resources And Strings: `PHASE_03__resources-strings-layouts.md`
- [x] Phase 04 - Game Screen: `PHASE_04__game-screen.md`
- [x] Phase 05 - Main Menu And Settings Entry: `PHASE_05__main-menu-settings-entry.md`
- [x] Phase 06 - Launcher Widget: `PHASE_06__launcher-widget.md`
- [x] Phase 07 - Validation, Docs, Catalog Cleanup: `PHASE_07__validation-docs-catalog.md`

## Static Completion Criteria

- `GameRulesEngine` has deterministic tests for move order, wall pushes, enemy capture, exits, scoring, level advance, and invalid custom boards.
- The Settings toggle defaults to `false` on a fresh install and controls both launcher surfaces.
- `btnMainDropdownMenu` shows the game item only when enabled and keeps focus stitching intact.
- Widget click opens the game when enabled and opens Settings on the game toggle when disabled.
- The game screen supports portrait, landscape, touch, keyboard/D-pad, and back navigation.
- Game state survives process recreation and resets incompatible schema versions without crashing.
- EN/RU/UK strings are complete and pass the project string audit.
- `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` include the new public capability.
- Final verification runs `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "game_"`, and a Standard debug build/test command.