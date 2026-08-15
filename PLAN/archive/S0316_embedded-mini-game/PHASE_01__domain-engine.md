# Phase 01 - Domain Engine

Goal: implement the pure Kotlin game model and deterministic rules with no Android UI dependencies.

## Files

Create:
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameModels.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameRulesEngine.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameBoardGenerator.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameScoring.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/domain/game/GameRulesEngineTest.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/domain/game/GameBoardGeneratorTest.kt`

## Steps

- [x] Define immutable domain models: `GameBoard`, `GameCell`, `GamePosition`, `GameDirection`, `GameActor`, `GameEnemy`, `GameDifficulty`, `GameTurnResult`, `GameStats`, `GameLevelConfig`.
- [x] Implement four-direction movement only; reject diagonal/no-op input as a static invalid command.
- [x] Implement turn order: player move, Kryvavitsa move, shuffled Shadow moves.
- [x] Implement wall pushing as a rule-level action with deterministic validation: blocked by board edge, exit, enemies, or immovable chain.
- [x] Implement capture rules: enemies kill only by orthogonal adjacency after their own action.
- [x] Implement exit rules: player wins a level only by stepping onto the exit cell alive.
- [x] Implement scoring: turn penalty, wall-push penalty, level completion bonus, survival streak bonus, and deterministic high-score comparison.
- [x] Implement generation presets and custom board validation: connected reachable area, one player, one Kryvavitsa, at least one exit, no impossible start adjacency.
- [x] Use injectable `Random` or seed value so tests reproduce the same Shadow order and generated boards.
- [x] Add unit tests for all static completion criteria in this phase.

## Verification

- `rg "android\." app_v2/src/main/java/com/sza/fastmediasorter/domain/game` returns no matches.
- `rg "GameRulesEngine" app_v2/src/test/java/com/sza/fastmediasorter/domain/game` shows move-order and capture tests.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` succeeds.
- `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.game.*"` succeeds or any pre-existing unrelated failures are recorded with exact task output.

## Done

- [x] Domain rules compile without Android UI dependencies.
- [x] Unit tests cover the rules from GAME_ru/GAME.
- [x] Dev changelog entry exists for every new/changed file batch.

## Step Log

- 2026-05-31 - Static verification PASS: no Android imports in `domain/game`, Problems reports no new domain/test errors, catalog sync PASS, dev log recorded. Focused Gradle test command was blocked before S0316 tests by existing `PlaybackSettingsFragment.kt` nullable-binding compile errors.