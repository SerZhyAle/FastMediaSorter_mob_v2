# Tactical Plan: S0993 - Contrast mode + guide arrow

**Ticket:** S0993
**Strategic spec:** `PLAN/S0993_minigame-contrast-mode-guide-arrow.md`
**Status:** Tactical

## Цель

Добавить третий визуальный мод мини-игры «На контрасте» (сплошные контрастные ячейки, читаемое покадровое движение) и общий для всех модов слой стрелки от игрока к ближайшему выходу в стартовом окне. Игровая логика не меняется - расширяется только презентационный слой (паттерн S0804).

## Инварианты

- Никаких изменений `domain/game` кроме одного нового значения `GameMode`.
- `onDraw` без аллокаций на кадр - только кэшированные `Paint`/`Path`.
- Способ отрисовки актёра - атрибут темы, не проверка имени мода в слое поля (ADR-2).
- Стрелка привязана к существующему стартовому окну подсветки, отдельного таймера нет (ADR-3).
- EN/RU/UK для нового названия мода.

## Фазы

1. `PHASE_1_contrast_mode_skin.md` - перечень мода, тема, строки, пикер, экран помощи.
2. `PHASE_2_contrast_render.md` - сплошные кубики актёров/выхода и читаемое покадровое движение.
3. `PHASE_3_guide_arrow.md` - стрелка игрок -> ближайший выход во всех модах.
4. `PHASE_4_tests_build_docs.md` - тесты, строковый аудит, ALL_FEATURES, сборка, девайс-гейт.

## Порядок

1 -> 2 -> 3 -> 4. Фазы 2 и 3 независимы по коду (разные методы `GameBoardView`), но обе зависят от Фазы 1 (мод/тема) только частично - стрелка (3) от Фазы 1 не зависит и может идти параллельно. Держим последовательно ради одной сборки в конце.

## Затрагиваемые файлы (ориентир)

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/game/GameModels.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/helpers/GameBoardTheme.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/helpers/GameModeMenuManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/helpers/GameBoardRenderMapper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameBoardView.kt`
- `app_v2/src/main/res/values*/strings_game.xml` (EN/RU/UK)
- `app_v2/src/test/java/com/sza/fastmediasorter/ui/game/...` (тесты маппера/мода)
