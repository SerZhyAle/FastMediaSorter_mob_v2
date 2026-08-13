# S0928 - Кнопка «пропуск хода» в мини-игре

**Ticket:** S0928
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-04
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-04

<!-- auto-approved by /spec-all - 2026-07-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-04

Мини-игра. Добавить кнопку «пропуск хода». Разместить между клавишами стрелочек. Игрок не ходит - остальные ходят.

---

## Goal

В мини-игре добавить кнопку «пропуск хода» в центр крестовины стрелок (3×3 D-pad, ныне пустая центральная ячейка). Тап: игрок остаётся на месте, ход засчитывается, враги делают свой ход (как после обычного шага). Работает во всех режимах игры; доступно с клавиатуры/D-pad через фокус кнопки.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **UI-решение:** кнопка `btnGameSkip` занимает центральную ячейку GridLayout D-pad (замена `Space` между стрелками) в обоих layout'ах (портрет + land); стиль идентичен стрелкам (OutlinedButton, прозрачный фон, `?attr/colorPrimary`), иконка `ic_skip_next`, `contentDescription=@string/game_skip_turn`.
- **Rules-решение:** пропуск = полноценный ход - `GameRulesEngine.applySkipTurn` тикает счётчик хода (штраф `-10`, как обычный ход) и запускает `moveEnemies`, не двигая игрока; capture-проверка врагов сохраняется.

---

## Фазы

### Фаза 1 - Правило «пропуск хода» (domain)

1. В `domain/game/GameRulesEngine.kt` добавить `applySkipTurn(state, shadowOrderSeed = state.config.seed + state.stats.turns): GameTurnResult`:
   - если `status != PLAYING` -> `GameTurnResult(state, accepted=false, rejectReason=NOT_PLAYING)`;
   - иначе тикнуть `scoring.afterAcceptedTurn(state.stats, wallPushed=false, shadowsCrushed=0)`, затем `moveEnemies(..)`, собрать события, вернуть `accepted=true`.
2. **Verification:** `.\a.ps1 fk` -> BUILD SUCCESSFUL; grep `applySkipTurn`.

### Фаза 2 - ViewModel

1. В `ui/game/GameViewModel.kt` добавить `skipTurn()` по образцу `move(..)`: guard `Ready` + `canAcceptMoves`, `rulesEngine.applySkipTurn(levelState)`, публикация `publishAndPersist(..)` с `turnMoves = result.toActorMoves()` и `defeatConnection`.
2. **Verification:** `.\a.ps1 fk` -> BUILD SUCCESSFUL; grep `skipTurn`.

### Фаза 3 - UI: кнопка в центре D-pad

1. `res/layout/activity_game.xml` **и** `res/layout-land/activity_game.xml`: центральный `Space` (между `btnGameLeft` и `btnGameRight`) заменить на `MaterialButton btnGameSkip` (стиль стрелок, `app:icon="@drawable/ic_skip_next"`, `contentDescription=@string/game_skip_turn`).
2. `ui/game/GameActivity.kt`: `binding.btnGameSkip.setOnClickListener { viewModel.skipTurn() }` в `setupViews()`.
3. Строка `game_skip_turn` (EN/RU/UK) в `strings_game.xml`.
4. **Verification:** `.\a.ps1 fc` -> BUILD SUCCESSFUL; `check_strings_localized.ps1 -KeyPrefix game_skip`.

### Фаза 4 - Debug-теги, сборка, блок на устройство

1. Вставить `Timber.d("S0928: ..")` на входе `skipTurn()`.
2. `.\a.ps1 d` -> BUILD SUCCESSFUL.
3. Статус -> `BlockNeedUserTest` (проверка на устройстве: тап по центру не двигает игрока, враги ходят, счётчик хода растёт).

---

## Критерии готовности

1. В центре крестовины стрелок есть кнопка пропуска хода (портрет + ландшафт).
2. Тап: игрок не двигается, враги делают ход, счётчик ходов увеличивается.
3. Если враг уже вплотную - пропуск может завершиться поражением (capture сохраняется).
4. Кнопка доступна с клавиатуры/D-pad (фокусируема), есть contentDescription.

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая возможность: в мини-игре можно пропустить ход - игрок стоит, враги ходят.

- **EN:** The mini-game now has a skip-turn button in the middle of the arrow pad - your character waits while enemies move.
- **RU:** В мини-игре появилась кнопка пропуска хода в центре крестовины - персонаж стоит, пока враги ходят.
- **UK:** У міні-грі з'явилася кнопка пропуску ходу в центрі хрестовини - персонаж стоїть, поки вороги ходять.
