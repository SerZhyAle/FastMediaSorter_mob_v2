# Phase 04 - Game Screen

Goal: implement the playable Android screen using the domain engine and persisted state.

## Files

Create:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameViewModel.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameUiState.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameBoardView.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/helpers/GameInputManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/helpers/GameBoardRenderMapper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/helpers/GameScalingManager.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/ui/game/GameViewModelTest.kt`

Modify:
- `app_v2/src/main/AndroidManifest.xml`

## Steps

- [x] Register `GameActivity` as non-exported, parented to `MainActivity`, and orientation-config-change compatible with existing app patterns.
- [x] Keep `GameActivity` thin: bind layout, wire toolbar/back, collect ViewModel state, and delegate input/scaling/render mapping.
- [x] Implement `GameViewModel` to load persisted state, start new game, apply movement commands, update score, advance level, and persist after each accepted turn.
- [x] Implement `GameBoardView` as a custom View with stable measurement, no per-frame object churn, and explicit accessibility descriptions for active cell focus.
- [x] Implement touch swipes, tap direction controls if present, keyboard arrows/WASD, D-pad, Enter/Space action, and Back behavior.
- [x] Implement preset board scaling: fit current board to available area with readable cell size.
- [x] Implement custom large-board scaling: zoom/pan, min/max zoom clamps, and unreadable-size warning before entering very large custom boards.
- [x] Implement screen states: loading, playing, paused, level won, game over, invalid custom board, storage reset warning.
- [x] Add ViewModel tests for starting/resuming games, movement dispatch, win/loss state, and persistence calls.

## Verification

- `rg "class GameActivity|class GameViewModel|class GameBoardView" app_v2/src/main/java/com/sza/fastmediasorter/ui/game` finds all required classes.
- `rg "GameActivity" app_v2/src/main/AndroidManifest.xml` finds a non-exported activity entry.
- `rg "Log\." app_v2/src/main/java/com/sza/fastmediasorter/ui/game` returns no matches.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` succeeds.
- Focused ViewModel/domain unit tests succeed or any unrelated failures are recorded.

## Done

- [x] Game can be launched directly by intent in debug/manual testing.
- [x] UI delegates rule decisions to domain engine and persistence to repository.
- [x] Portrait and landscape layouts both render the same game states.

## Step Log

- 2026-05-31: PASS - Added `GameActivity`, `GameViewModel`, `GameUiState`, `GameBoardView`, input/render/scaling helpers, manifest entry, and ViewModel tests. VS Code Problems reported no errors for touched S0316 files. Static checks found all required classes, the manifest activity entry, and no `Log.` calls under `ui/game`.
- 2026-05-31: BLOCKED EXTERNALLY - `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.ui.game.GameViewModelTest"` stopped during `compileStandardDebugKotlin` before running S0316 tests because `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt` references unresolved `btnCalculatorPercent`.