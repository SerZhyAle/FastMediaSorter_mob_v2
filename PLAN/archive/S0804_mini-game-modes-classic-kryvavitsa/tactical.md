# Тактический план: S0804 - Режимы мини-игры

**Strategic:** `PLAN/S0804_mini-game-modes-classic-kryvavitsa.md`
**Status:** In Progress

Один слой на фазу; логика игры не трогается.

## Фаза 1 - Домен и персист

- `GameMode { CLASSIC, KRYVAVITSA }` в `domain/game/GameModels.kt` c безопасным парсером имени.
- `GameStateRepository`: `suspend fun loadMode(): GameMode`, `suspend fun saveMode(mode)`.
- `GameStateRepositoryImpl`: ключ `embedded_game_mode`, дефолт CLASSIC, безопасный разбор.

## Фаза 2 - UI-состояние и ViewModel

- `GameUiState.Ready.mode: GameMode = CLASSIC`.
- `GameViewModel`: поле `currentMode`, загрузка в `resumeGame`, проброс во все `Ready`, `fun setMode(mode)` (persist + обновить текущее состояние без сброса партии).

## Фаза 3 - Рендер темы

- `ui/game/helpers/GameBoardTheme.kt`: палитра (пол/стена), drawable актёров/двери (null = примитивы/рамка), флаг `stomp`; фабрика `forMode(context, mode)`.
- `GameBoardRenderMapper`/`GameBoardRenderState`: добавить `mode`.
- `GameBoardView`: применять тему (цвета пола/стен), рисовать силуэты/дверь либо примитивы/рамку, «топанье» (вертикальный подскок) при `stomp`.

## Фаза 4 - Ассеты

- Векторные силуэты: `ic_game_fig_monster`, `ic_game_fig_kryvavitsa`, `ic_game_fig_shadow`, дверь `ic_game_door`; чёрный свотч стены для легенды.

## Фаза 5 - Селектор

- `btnGameMode` под `btnGameHelp` в `layout/` и `layout-land/` `activity_game.xml`.
- `ui/game/helpers/GameModeMenuManager.kt`: строит `ListSelectionConfig<GameMode>`.
- `GameActivity`: биндинг кнопки, показ пикера, метка кнопки = активный режим, проброс режима в intent справки.

## Фаза 6 - Справка/легенда

- `GameHelpActivity`: читает extra режима, подставляет легенду/intro/rules.
- `view_game_help_legend.xml`: id иконок и подписей; `<include>` с id в обеих ориентациях.

## Фаза 7 - Строки EN/RU/UK

- Имена режимов, заголовок пикера, kryvavytsia-легенда/intro/rules в `strings_game.xml` (values, -ru, -uk).

## Фаза 8 - Сборка и закрытие

- `.\a.ps1 fc`, статические гейты, catalog sync, dev log, ALL_FEATURES, string audit.
- Timber.d("S0804: ..") в изменённых точках потока, статус -> BlockNeedUserTest.
