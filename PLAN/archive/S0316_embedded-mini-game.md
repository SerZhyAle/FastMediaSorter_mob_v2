---
ticket: S0316
status: Verified
priority: 50
date: 2026-05-31
tier: 3
---

# Стратегическая спецификация: S0316 - Крывавица и Чудовище

**Ticket:** S0316
**Status:** Archived
**Priority:** 50
**Date:** 2026-05-31
**Tier:** 3 - Moderate, ad-hoc
**Roadmap entry:** Ad-hoc - запрос 2026-05-31
**Tactical spec:** `PLAN/S0316_embedded-mini-game/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec-update; переписать S0316 по предоставленным GAME_ru/GAME design docs.
- **Goal / expected outcome:** Provided by user - встроенная мини-игра **Крывавица и Чудовище**: пошаговая survival-puzzle на сетке, запускаемая из приложения как optional feature.
- **Local anchor:** Provided by user - настройки приложения, `btnMainDropdownMenu` в основном окне, отдельный launcher-widget запуска.
- **Scope boundaries / forbidden areas:** Provided by user - MVP без пользовательских скинов, звука, online leaderboard, multiplayer и отдельных desktop/mobile store packaging targets; web/PWA/GitHub Pages scope из исходного документа не переносится как Android-требование.
- **Done / success signal:** Provided by user - gameplay, прогрессия, scoring, генерация поля, локализация, сохранение и UI соответствуют правилам из предоставленного game design; entry points появляются только после включения игры в настройках.
- **Autonomy rule:** Provided by user - agent may decide Android-specific details with explicit assumptions.
- **UI decisions / delegation:** Provided by user - игра выключена по умолчанию; включается в настройках; запускается новой кнопкой внутри `btnMainDropdownMenu`; имеет свой launcher-widget запуска; оба entry point видимы только когда игра включена в настройках.

`Approved` is blocked while any mandatory line in this section contains `MISSING - requires owner input`.

---

## 1. Проблема

В приложении нет встроенного короткого игрового режима, который можно открыть как optional entertainment surface, не мешая основным сценариям работы с медиа и файлами. Владелец предоставил готовый game design для ремейка **Крывавица и Чудовище**: пошаговой логической игры на выживание, где игрок управляет Чудовищем, читает поле, толкает стены, избегает Крывавицу и Теней и пытается дойти до Выхода.

Задача S0316 - адаптировать этот game design в Android-приложение как выключенную по умолчанию встроенную мини-игру с контролируемыми entry points. Игра не должна превращать media/file tool в игровой продукт и не должна появляться в основном UI без явного выбора пользователя.

## 2. Цели

1. Встроить **Крывавица и Чудовище** как опциональную мини-игру внутри приложения.
2. Сохранить identity игры: Чудовище, Крывавица, Тени, Выход, стены, пошаговый цикл, логика выживания без реакции на скорость.
3. Дать два entry point после включения: кнопку внутри `btnMainDropdownMenu` в основном окне и отдельный launcher-widget запуска.
4. На свежей установке держать игру выключенной и не показывать игровые entry points.
5. Обеспечить локальное сохранение текущей партии, рекорда, настроек поля и совместимой версии сохранения.
6. Поддержать EN/RU/UK пользовательские строки и игровые термины без исторической путаницы `Кровавица` vs `Крывавица`.
7. Изолировать игровой режим от playback, browse, cloud/network, file operations и startup-пути приложения.

**Non-goals:**

- Пользовательские скины в MVP.
- Звук, haptics или отдельная audio focus policy в MVP.
- Online leaderboard.
- Multiplayer.
- Отдельный standalone web/PWA/GitHub Pages delivery в рамках Android-задачи.
- Отдельная публикация игры в Google Play, MSIX/WinGet или iOS App Store.
- Wear OS surface до отдельного решения владельца.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Мини-игра встроена в приложение.
2. Игра - **Крывавица и Чудовище**, современное название именно через `ы`; старое `Кровавица` допустимо только в исторических заметках, не в игровом UI.
3. Управление доступностью идёт через настройки.
4. На свежей установке игра выключена.
5. После включения появляется новая кнопка запуска внутри `btnMainDropdownMenu` в основном окне.
6. После включения доступен отдельный launcher-widget для запуска игры.
7. MVP использует цветной режим отображения; скины остаются после MVP.
8. Игра локальная: серверы, аккаунты и online state не нужны.

### 3.2 Жёсткие ограничения

- **Flavor:** all main app flavors, default off.
- **API level:** launcher-widget и игровой screen должны уважать минимальные Android API целевых flavor-сборок.
- **Wear OS:** не входит в scope без отдельного решения.
- **Производительность:** при выключенной игре не должно быть startup, memory, CPU или battery overhead; во время игры генерация поля не должна зависать на больших размерах.
- **Совместимость данных:** игра хранит state локально; несовместимая версия сохранения сбрасывает текущую партию, но сохраняет рекорд и совместимые настройки.
- **Локализация:** EN/RU/UK обязательны; язык по умолчанию - английский, но Android-адаптация должна следовать общей app-localization policy.
- **Имена в UI:** только Чудовище, Крывавица, Тень, Выход; технические имена из исходных материалов не должны попадать в пользовательский UI.
- **Управление:** четыре направления; диагональных ходов нет; touch/tap по соседней клетке, keyboard arrows, D-pad/TV remote и mouse должны быть покрыты, если feature доступна на этих устройствах.
- **Доступность:** board, cells, menu, overlays и launcher-widget должны иметь TalkBack-friendly labels и не полагаться только на цвет.
- **UI policy:** пользовательские строки должны пройти tone checklist из `docs/COMMUNICATION_POLICY.md` перед интеграцией.
- **UI ambiguity gate:** placement, видимость, ориентации, fallback и accessibility должны быть утверждены до реализации.

### 3.3 Owner inputs (Approval gate)

- **Goal / expected outcome:** Embedded Android mini-game **Крывавица и Чудовище** with rules matching GAME_ru/GAME design docs.
- **Scope boundaries:** MVP excludes skins, sound, online leaderboard, multiplayer, standalone web/PWA delivery and separate store packaging; Wear OS is out of scope.
- **Autonomy rule:** Agent may decide Android-specific details with explicit assumptions.
- **Flavor scope:** All main app flavors, default off.
- **Main dropdown placement:** Lower `btnMainDropdownMenu` group before Settings/secondary actions; hidden while game disabled.
- **Launcher-widget fallback:** Disabled state opens Settings game toggle when the game is disabled after widget placement.
- **Storage:** App-local typed storage, no Room unless tactical research proves it necessary.
- **Board scaling:** Fit-to-screen for presets; zoom/pan for large custom boards; warn for unreadable large sizes.
- **Validation level:** Tactical plan must include domain rule tests, target build, trilingual string checks and widget/menu visibility checks.
- **Feature docs:** Update `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` after implementation.
- **Related tickets:** S0134, S0253, S0289.

## 4. Контекст текущей архитектуры

Приложение уже имеет отдельные surface для настроек, основного окна, `btnMainDropdownMenu` и home-screen widgets. Эти surface должны оставаться владельцами только отображения и пользовательского ввода; состояние включения игры и решение о видимости entry point должны идти через общий слой настроек.

Игровой режим должен жить как самостоятельный feature surface. Он не должен зависеть от медиаплеера, браузера файлов, сетевых ресурсов, облачных провайдеров или фоновых file operations. Если пользователь не включил игру, основной интерфейс, виджеты и startup-путь должны вести себя как раньше.

Исходные GAME_ru/GAME design docs описывают standalone web/PWA MVP. Для S0316 это считается product-design источником правил игры, а не требованием к доставке через web hosting или PWA.

## 5. Предлагаемый подход

Фича делится на шесть продуктовых ролей: доступность и запуск, игровой экран, правила поля, пошаговый цикл врагов, локальное состояние и локализация. Доступность управляет тем, существует ли игра в UI. Launcher surfaces только открывают игровой экран. Игровой экран отображает поле и overlays. Правила поля и хода определяют исход каждой команды игрока. Локальное состояние сохраняет текущую партию и рекорд. Локализация закрепляет игровые термины и исключает историческое имя из активного UI.

### 5.1 Основные столпы / модули

**Доступность и запуск**

Игра выключена по умолчанию. После включения появляются два entry point: команда в нижней группе `btnMainDropdownMenu` перед Settings/secondary actions и отдельный launcher-widget. Если widget остаётся на home screen после отключения игры, он показывает disabled state и открывает настройки с game toggle.

**Игровой экран**

Экран один и постоянный: верхняя строка с меню, названием, уровнем и счётом; поле, занимающее максимум доступного места; нижняя строка с ходами и убитыми Тенями. Победа и поражение показываются короткой overlay-плашкой около 1.5 секунды, без блокирующего modal flow.

**Поле и клетки**

Поле прямоугольное. Presets: 10x10, 15x15, 20x20. Custom size: от 10x10 до 100x100. Типы клеток: пусто, стена, Чудовище, Крывавица, Тень, Выход. Для прямоугольных правил расстояния используется меньшая сторона поля как base distance.

**Ход Чудовища**

Игрок ходит первым. Пустая клетка принимает Чудовище. Выход завершает уровень. Враг в целевой клетке убивает Чудовище. Стена может быть сдвинута на одну клетку, если за ней пусто, Тень или Выход; цепочки стен не толкаются; Крывавица, другая стена и граница блокируют ход.

**Пошаговый цикл врагов**

После засчитанного действия игрока ходит Крывавица, затем все живые Тени в перемешанном порядке. Крывавица одна, действует жадно: сначала пытается сократить расстояние до Чудовища, затем к Выходу, затем выбирает случайное доступное направление, иначе стоит. Тени ходят случайно в первое доступное пустое направление. Враги не заходят на клетку Чудовища; убийство происходит через ортогональное соседство после их собственного действия или стояния.

**Победа, поражение и прогрессия**

Уровень пройден, когда Чудовище входит в Выход. Победа увеличивает количество Теней на 1, генерирует новое поле того же размера, сохраняет текущий счёт и обновляет рекорд уровня. Поражение генерирует новое поле того же уровня и размера без сброса текущего счёта. Новая игра и смена размера поля сбрасывают уровень к 1 и счёт к 0, но рекорд сохраняется.

**Очки**

Победа даёт +1000. Сдвиг стены даёт +10. Убийство Тени стеной даёт +50. Каждый ход Чудовища снимает 10. Перезапуск уровня снимает 500. Очки не уходят ниже 0. Рекорд - максимальный достигнутый уровень за все игры.

**Генерация поля**

Новое поле генерируется случайно: стены занимают 30-40% клеток, затем размещаются Чудовище, Крывавица, Тени и Выход с минимальными стартовыми дистанциями. Валидное поле гарантирует путь от Чудовища к Выходу без толкания стен, минимум один легальный первый ход, допустимые дистанции врагов и возможность разместить нужное количество Теней. Невалидная попытка отбрасывается и повторяется.

**Визуальный режим MVP**

MVP использует цветной board: пусто - белое, стена - серая, Чудовище - тёмно-зелёное, Крывавица - тёмно-красная или бордовая, Тень - чёрная, Выход - розовый или светло-красный. Тёмная тема использует соответствующую тёмную палитру. Сетка между клетками включается в настройках.

**Локальное состояние**

Сохраняются уровень, счёт, рекорд, матрица поля, позиции объектов, размер поля, цветной режим, тёмная тема, сетка, язык и версия формата. Текущая игра сохраняется после полного цикла хода. При запуске игра продолжает совместимое сохранение.

### 5.2 Потоки данных и событий

1. Пользователь включает игру в настройках.
2. Основное окно получает право показать команду запуска внутри `btnMainDropdownMenu`.
3. Launcher-widget становится доступным как отдельная точка запуска.
4. Пользователь запускает игру из меню или widget.
5. Игровой экран загружает совместимое локальное сохранение или создаёт новую партию.
6. Пользователь делает четырёхнаправленный ход или пытается толкнуть стену.
7. После засчитанного действия игрока выполняется ход Крывавицы и всех живых Теней.
8. После каждого врага проверяется поражение от ортогонального соседства.
9. Победа, поражение, перезапуск, новая игра или смена размера немедленно обновляют поле и локальное состояние.
10. Если пользователь отключает игру, меню и widget-поведение синхронно отражают выключенное состояние.

### 5.3 Точки расширяемости

- Скины и альтернативные визуальные темы после MVP.
- Звук и haptics после отдельного решения по audio focus и accessibility.
- Расширенная статистика и leaderboards только как отдельная спецификация.
- Дополнительные размеры поля и difficulty presets без изменения базовых правил.
- Исторический раздел о DOS/VB6 версиях как help/about content, но не как часть активного игрового UI.

## 6. Открытые вопросы / Research items

1. **Flavor scope**
   - **Вопрос:** в каких сборках Android-приложения игра должна быть доступна?
   - **Варианты:** все main-app flavors.
   - **Нужно выяснить:** ничего; владелец принял recommended package.
   - **Статус:** Resolved - all main app flavors, default off.

2. **Main dropdown placement**
   - **Вопрос:** где находится новая команда внутри `btnMainDropdownMenu` и каков её приоритет относительно существующих действий?
   - **Варианты:** lower group, before Settings/other secondary actions.
   - **Нужно выяснить:** ничего; владелец принял recommended package.
   - **Статус:** Resolved - lower group before Settings/secondary actions; hidden while game disabled.

3. **Launcher-widget fallback**
   - **Вопрос:** что должен делать widget, если игра выключена после добавления widget на рабочий стол?
   - **Варианты:** disabled state opens Settings game toggle.
   - **Нужно выяснить:** ничего; владелец принял recommended package.
   - **Статус:** Resolved - disabled widget opens Settings game toggle.

4. **Android storage adaptation**
   - **Вопрос:** как заменить web `localStorage` из источника на app-local persistence без потери требований к совместимости?
   - **Варианты:** app-local typed storage, no Room unless tactical research proves it necessary.
   - **Нужно выяснить:** формат версии сохранения в tactical plan.
   - **Статус:** Resolved - app-local typed storage; no Room by default.

5. **Board scaling on small screens**
   - **Вопрос:** как отображать до 100x100 клеток на телефонах, TV и планшетах без нечитаемости и лагов?
   - **Варианты:** fit-to-screen for presets; zoom/pan for large custom boards; warn for unreadable large sizes.
   - **Нужно выяснить:** конкретные threshold values в tactical plan.
   - **Статус:** Resolved - fit presets; zoom/pan custom large boards; warn on unreadable sizes.

6. **Autonomy approval**
   - **Вопрос:** может ли агент сам закрыть Android-specific решения, которых нет в GAME_ru/GAME?
   - **Варианты:** agent may decide with explicit assumptions.
   - **Нужно выяснить:** ничего; владелец принял recommended package.
   - **Статус:** Resolved - agent may decide Android-specific details with explicit assumptions.

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Большие поля 100x100 тормозят или становятся нечитаемыми | Средняя | Плохой UX на телефонах и TV | Утвердить scaling policy, static limits и performance gate до реализации |
| Генерация поля долго ищет валидную карту | Средняя | Зависание при старте уровня | Ограничить попытки, добавить fallback generation strategy и validation metrics |
| Widget остаётся активным после отключения игры | Средняя | Пользователь получает противоречивую точку запуска | Утвердить disabled/fallback поведение widget до реализации |
| Игровые термины расходятся между языками | Средняя | Потеря identity игры | Закрепить glossary EN/RU/UK и запретить `Кровавица` в активном UI |
| Игра влияет на основной media/file workflow | Низкая | Регрессии в ключевых сценариях приложения | Default-off, изолированный экран, отсутствие фоновых игровых задач при выключенной настройке |
| Правила врагов реализуются неоднозначно | Средняя | Игра перестаёт совпадать с design docs | В tactical spec вынести machine-verifiable rule tests для turn order, AI и adjacency defeat |

## 8. Влияние на пользователя (docs/FEATURES)

После реализации добавить в `docs/FEATURES.md` + `_RU` + `_UK` короткий пункт о встроенной пошаговой мини-игре **Крывавица и Чудовище**, которая включается в настройках и запускается из основного dropdown menu или launcher-widget.

## 9. Архитектурные решения (ADR)

**ADR-1: Game identity is Kryvavitsa and the Monster**

- **Решение:** активный UI использует современное название **Крывавица и Чудовище** и термины Чудовище, Крывавица, Тень, Выход.
- **Альтернативы:** сохранить историческое имя `Кровавица` или технические имена из старого кода.
- **Почему:** предоставленный design doc фиксирует 2026-ремейк как современную версию, а исторические имена оставляет только для справочных материалов.

**ADR-2: Turn-based puzzle, not reaction game**

- **Решение:** мир продвигается только после действия игрока: Чудовище, затем Крывавица, затем Тени.
- **Альтернативы:** real-time movement, timers, enemy idle movement.
- **Почему:** core identity игры - чтение поля, толкание стен и планирование, а не скорость реакции.

**ADR-3: Default-off entertainment surface**

- **Решение:** мини-игра выключена по умолчанию; меню и launcher-widget становятся доступными только после явного включения.
- **Альтернативы:** показывать игру всегда; включить игру по умолчанию; оставить только hidden entry point.
- **Почему:** приложение в первую очередь остаётся media/file tool; развлекательная фича не должна менять базовый UX без выбора пользователя.

**ADR-4: Android adaptation uses local-only state**

- **Решение:** Android-адаптация сохраняет партию, рекорд и настройки локально; серверы и online state не используются.
- **Альтернативы:** online leaderboard или account-bound progress.
- **Почему:** исходный game design явно задаёт локальное хранение и исключает online leaderboard из MVP.

## 10. Связи с другими спеками

- **S0134** - widget picker and home polish; использовать как ориентир для widget-поведения и home-screen surface.
- **S0253** - overflow menus default-on; учесть существующую политику overflow/ниспадающих команд.
- **S0289** - TV keyboard D-pad navigation; учесть input coverage для меню, widget и игрового экрана.

## 11. Критерии готовности (strategic-level)

1. На свежей установке игра выключена и не добавляет видимых entry points в основной пользовательский поток.
2. Пользователь может включить игру в настройках.
3. После включения пользователь видит команду запуска игры внутри `btnMainDropdownMenu` в основном окне.
4. После включения пользователь может запустить игру через отдельный launcher-widget.
5. После отключения игры меню и widget-поведение синхронно отражают выключенное состояние.
6. Игровой экран показывает название, уровень, счёт, поле, ходы и убитых Теней без блокирующих victory/defeat screens.
7. Игрок управляет Чудовищем четырёхнаправленно через touch, keyboard, D-pad/TV remote и mouse, если устройство поддерживает соответствующий input.
8. Правила стен, Выхода, поражения, победы, Крывавицы и Теней соответствуют design docs.
9. Уровень, количество Теней, scoring, рекорд и reset/new-game/size-change progression работают по описанным правилам.
10. Генерация создаёт только валидные поля: путь к Выходу существует, первый ход возможен, стартовые дистанции соблюдены, Тени помещаются.
11. Совместимое сохранение продолжается при повторном запуске; несовместимое сохранение сбрасывает текущую партию, но сохраняет рекорд и совместимые настройки.
12. UI строки и игровые термины локализованы на EN/RU/UK; активный UI не использует историческое имя `Кровавица`.
13. Выключенная игра не ухудшает startup, playback, browse, file operations, cloud/network и widget behavior приложения.

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-dev S0316` - выполняет tactical phases из `PLAN/S0316_embedded-mini-game/` по порядку.

## 13. Implementation Notes

- Implemented tactical phases 01..07 in `app_v2`.
- Added a domain rules engine, deterministic board generator, scoring model, typed local game-state repository, Settings toggle, game screen, main dropdown entry, and launcher widget.
- Game is default-off through `AppSettings.embeddedGameEnabled`; when disabled, the main dropdown item is hidden and the placed widget opens Settings on the game toggle.
- EN/RU/UK `game_` strings pass the project localization audit; feature inventory docs were updated in all three languages.
- Final validation passed: catalog sync, `game_` string audit, focused `domain.game`/`data.game`/`ui.game` unit tests, and `assembleStandardDebug`.

## Revision History

- **2026-05-31** - by `/spec-update` (`GitHub Copilot`, focus: consistency)
  - Applied: уточнён основной entry point: игра появляется внутри `btnMainDropdownMenu` только когда включена в настройках; launcher-widget остаётся вторым entry point.
- **2026-05-31** - by `/spec-update` (`GitHub Copilot`, focus: completeness)
  - Applied: S0316 переписана по предоставленным GAME_ru/GAME design docs; закреплены название, персонажи, правила хода, AI, генерация, scoring, UI, local-only state и MVP exclusions.
- **2026-05-31** - by `/spec-update` (`GitHub Copilot`, focus: approval-gate)
   - Applied: accepted recommended package; closed autonomy, flavor, menu placement, widget fallback, storage and board scaling decisions for `/spec-tech`.
- **2026-05-31** - by `/spec-tech` (`GitHub Copilot`, focus: tactical-plan)
   - Applied: created tactical folder with 7 implementation phases and promoted S0316 to Tactical.
- **2026-05-31** - by `/spec-dev` (`GitHub Copilot`, focus: implementation-start)
   - Applied: moved S0316 into In Progress before Phase 01 code execution.
- **2026-05-31** - by `/spec-dev` (`GitHub Copilot`, focus: implementation-complete)
   - Applied: completed phases 01..07, added docs and validation notes, and moved S0316 to Implemented.

## Last Audit

**Date:** 2026-05-31
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 23 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 0

### Manual / on-device

- [x] Launch the enabled game from `btnMainDropdownMenu` on a device/emulator - verified on emulator-5554 2026-05-31.
- [ ] Place the game widget and verify enabled launch plus disabled Settings fallback.