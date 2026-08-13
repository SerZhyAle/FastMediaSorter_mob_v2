# S0519 - Surface-aware gamepad resolution (browser off hardcode)

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-18
**Tier:** 4 - Complex (ad-hoc)
**Origin:** follow-up of S0509 (gamepad button remap) - parked during S0509 research

<!-- auto-approved by /spec-all - 2026-06-19 -->

---

## 1. Проблема

- S0509 сделал gamepad-бинды перезахватываемыми из UI, но ремап применяется только на surface'ах, резолвящихся через `KeyBindingManager` (PLAYER).
- Browser-surface геймпад зашит в `GamepadInputManager.mapBrowserButton` - литеральное `when`-дерево по keycode'ам, которое игнорирует пользовательские бинды.
- Корневая причина - `KeyBindingManager` хранит плоскую `Map<InputTrigger, String>` (один trigger -> один commandId) и `resolve(trigger, surface)` игнорирует `surface`.
- Из-за плоской мапы один и тот же физический trigger не может одновременно значить `playback.pause_play` в плеере и `browser.select` в браузере: вторая запись затирает первую.
- Конкретные коллизии в дефолтах: `BUTTON_A(96)` = `playback.pause_play` против browser Select; `BUTTON_X(99)` = `navigation.next_file` против browser MultiSelect; `BUTTON_Y(100)` = `navigation.previous_file` против browser ContextMenu; `L1/R1` = seek против switch-tab.

## 2. Цель

- Сделать резолв `KeyBindingManager` surface-aware, чтобы один trigger мог нести разный смысл на разных surface'ах.
- Перевести browser-геймпад с литерального дерева на резолвер, чтобы ремап из S0509 действовал и в браузере.
- Дать browser-действиям собственные `CommandId` и собственную группу в экране ремапа, чтобы пользователь мог их перепривязывать наравне с плеерными.
- Не вводить Room-миграцию: схема `input_bindings` остаётся прежней.

## 3. Объём

### В объёме

- Surface-aware `resolve` в `KeyBindingManager` (multi-candidate структура + дизамбигуация по surface).
- Новые browser-`CommandId` и группа `CommandGroup.BROWSER_ACTIONS`.
- Удаление `mapBrowserButton`; browser-путь `GamepadInputManager` через резолвер.
- Browser gamepad-дефолты в `assets/input/default_bindings.json`.
- Трилингвальные подписи команд и группы (`strings_input.xml` EN/RU/UK).
- Инверсия теста `GamepadInputManagerTest` №5 (browser теперь идёт через резолвер).

### Вне объёма

- Изменение схемы Room и любая миграция базы.
- Browser-дефолты для клавиатуры/мыши (browser-клавиатура уже работает через общие commandId и `routeBrowserCommandId`).
- DIALOG/VR surface (отдельные тикеты при необходимости).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0509 (ремап-UX, делает перезахват действующим в браузере), S0289 (multimodal parity), S0508 (gamepad navigation)
- **UI placement:** новая сворачиваемая группа "Browser actions" в существующем экране ремапа `KeybindingRemapActivity`, ниже прочих групп по `ordinal`; отдельных экранов не добавляется
- **Localization:** 7 подписей команд + 1 заголовок группы добавляются в `strings_input.xml` для EN/RU/UK в локстепе

## 4. Решение - Вариант C (namespace через commandId)

Выбран **Вариант C**: browser-действия получают собственные `browser.*` commandId, surface выводится из группы команды, а `resolve` выбирает кандидата по surface.

### Почему C

- Не требует Room-миграции - схема `input_bindings` (PK `command_id, device, slot`) не меняется; нет необратимого шага и риска несовместимой миграции.
- Переиспользует существующую инфраструктуру префиксов: `ResetGroupUseCase.prefixForGroup`, `clearAllOverridesForGroup(prefix)`, `InputBindingDao.deleteByCommandPrefix` уже работают по строковому префиксу commandId.
- `device`-таксономия остаётся чистой (`gamepad`), не смешивается с surface.
- Ремап-хранилище без коллизий: `browser.select` - отдельный commandId со своей override-строкой, независимой от `playback.pause_play`.

### Механика резолва

- `KeyBindingManager` переходит с `Map<InputTrigger, String>` на `Map<InputTrigger, List<String>>` (trigger -> все привязанные commandId).
- `resolve(trigger, surface)`: при единственном кандидате возвращает его (обратная совместимость для всех текущих player/keyboard/mouse путей); при нескольких выбирает кандидата, чья surface совпадает с запрошенной.
- surface команды выводится из commandId: префикс `browser.` -> BROWSER, `vr.` -> VR, иначе -> PLAYER (surface-agnostic-фолбэк для общих команд вроде `system.search`).

### Отклонённые варианты

- **Вариант A (колонка `surface` в Room):** честная изоляция, но Room-миграция + смена PK/`InputBindingEntity`/DAO/домена - максимальный blast-radius и необратимый шаг; surface уже выводится из commandId, отдельная колонка избыточна.
- **Вариант B (namespace через device-строку `gamepad_browser`):** смешивает device и surface, ломает `deviceOf`/`insertAllAsOverrides`/фильтр устройств в UI; плоскую мапу резолвера всё равно не чинит.

## 5. Затронутые компоненты

- `domain/input/CommandId.kt` - новые browser-константы.
- `domain/input/CommandGroup.kt` - значение `BROWSER_ACTIONS`.
- `core/input/KeyBindingManager.kt` - multi-candidate резолв.
- `core/input/GamepadInputManager.kt` - удаление `mapBrowserButton`, browser через резолвер, browser-ветки в `mapCommandToGamepadAction`.
- `ui/keybinding/KeybindingRemapViewModel.kt` - ветка `browser.` в `commandGroupOf`.
- `domain/input/usecase/ResetGroupUseCase.kt` - префикс для `BROWSER_ACTIONS` (компилятор требует ветку).
- `assets/input/default_bindings.json` - browser gamepad-дефолты.
- `res/values{,-ru,-uk}/strings_input.xml` - подписи команд и группы.
- `src/test/java/com/sza/fastmediasorter/core/input/GamepadInputManagerTest.kt` - инверсия теста №5.

## 6. Открытые вопросы

- Нет. Архитектурный форк закрыт в §4.

## 10. Связанные тикеты

- S0509 - gamepad button remap (родитель; этот тикет делает перезахват действующим в браузере).
- S0289 - multimodal parity.
- S0508 - gamepad navigation.

## Last Audit

**Дата:** 2026-06-19. **Режим:** `/spec-all NO-BUILD` - статическая верификация, сборка не запускалась.

### Реализовано (Вариант C)

- `domain/input/CommandId.kt` - 7 констант `browser.*`.
- `domain/input/CommandGroup.kt` - значение `BROWSER_ACTIONS` (между `SORTING_ACTIONS` и `VR_ONLY`).
- `domain/input/usecase/ResetGroupUseCase.kt` - ветка `BROWSER_ACTIONS -> "browser."` (когда `when` снова исчерпывающий).
- `ui/keybinding/KeybindingRemapViewModel.kt` - ветка `browser.` в `commandGroupOf`.
- `core/input/KeyBindingManager.kt` - `Map<InputTrigger, List<String>>`, surface-aware `resolve`, приватный `surfaceOf`.
- `core/input/GamepadInputManager.kt` - удалён `mapBrowserButton`, browser через резолвер, 7 browser-веток в `mapCommandToGamepadAction`, обновлён KDoc.
- `assets/input/default_bindings.json` - 7 browser-записей (A/B/X/Y/START/L1/R1).
- `res/values{,-ru,-uk}/strings_input.xml` - 7 подписей + заголовок группы, парность EN/RU/UK подтверждена.
- `core/input/GamepadInputManagerTest.kt` - тест №5 инвертирован, добавлен тест №6 (дизамбигуация по surface).
- `domain/input/usecase/ResetGroupUseCaseTest.kt` - покрытие новой ветки.

### Пройденные проверки (статические)

- `default_bindings.json` парсится; 7 записей `BROWSER_ACTIONS`.
- `check_strings_localized.ps1` - EN/RU/UK парность для `keybinding_label_browser_*` и `keybinding_group_browser_actions`: exit 0.
- `assert-neuroslop.ps1 -Gate`: PASS.
- `assert-no-ticket-logs.ps1 -Gate`: PASS (2 S0519-пробы разрешены статусом `BlockNeedUserTest`).
- Компилятор-завязки выверены вручную: оба `when (CommandGroup)` исчерпывающи; `GamepadAction.BrowserAction` покрывает все 7 веток; импорты `Timber` добавлены; осиротевших импортов нет.

### Не проверено

- Сборка `standard debug` не запускалась (флаг NO-BUILD) - компиляция не подтверждена машинно.
- Юнит-тесты не запускались.
- On-device gamepad-проверка не выполнялась (требуется железо + свежая сборка).

### Остаточные действия

- Запустить `.\a.ps1 fc` (компиляция + ресурсы), затем `--tests *GamepadInputManagerTest`.
- On-device: проверить browser-кнопки и ремап browser-действия (см. **Status note**).
- S0508 - gamepad navigation.
