# Спецификация: Полное покрытие клавиатуры и мыши в app_v2

**Status:** Draft
**Date:** 2026-04-24
**Tier:** 4 — Strategic (multi-surface, high regression risk)
**Roadmap entry:** Ad-hoc initiative for keyboard/mouse parity on tablets, ChromeOS, DeX, Android TV and Bluetooth-keyboard VR usage.
**Supersedes:** None. This document is the canonical behavioral spec; the follow-up implementation spec must lock exact classes, files and rollout phases.

---

## 1. Проблема

FastMediaSorter уже частично поддерживает клавиатуру и мышь, но поддержка фрагментирована по поверхностям:

- на главном экране, в обзоре файлов и в основном плеере многое уже работает;
- в настройках, диалогах, picker'ах, `StandalonePlayerActivity` и ряде вспомогательных экранов поддержки почти нет или она случайна;
- правый клик, Enter/Escape, фокус, выделение диапазона и единые F-клавиши ведут себя по-разному в зависимости от экрана.

Это ломает базовый пользовательский сценарий на устройствах без постоянного тача:

- планшеты и Chromebook с внешней клавиатурой/мышью;
- DeX и другие оконные режимы;
- Android TV / Google TV с пультом или USB/Bluetooth-клавиатурой;
- Waydroid / WSA / эмуляторы;
- Meta Quest с подключённой Bluetooth-клавиатурой.

Пользователь должен иметь возможность пройти весь основной сценарий без тача: выбрать ресурс, открыть файл, перемещаться по папкам, выделять, копировать, удалять, переименовывать, управлять плеером, работать с диалогами и выйти обратно. Сейчас это невозможно на уровне всего приложения.

---

## 2. Цели

1. Зафиксировать единый контракт клавиатуры и мыши для всех пользовательских поверхностей `app_v2`.
2. Свести файловые действия к одной раскладке: NC/Far/Total Commander как первичный слой, Windows-комбинации как обязательные дубли.
3. Обеспечить предсказуемые правила для диалогов, списков, focus-ring, контекстных меню, Enter/Escape и правого клика.
4. Перечислить текущие пробелы по экранам и сделать их явными для дальнейшей реализации.
5. Зафиксировать архитектурные ограничения, тестовую стратегию, accessibility и документационные выходы, чтобы спецификация соответствовала стандарту `PLAN/spec_*.md` в этом репозитории.
6. Подготовить основу для отдельной технической спецификации `spec_keyboard-mouse-coverage-impl.md`, где будут названы точные классы, менеджеры, тесты и этапы внедрения.

**Non-goals для этой спецификации:**

- подробная реализация по классам и файлам;
- решение по конкретному механизму повторного использования `KeyboardShortcutHandler`, `MouseEventHandler`, `FocusManager`;
- ремаппинг клавиш пользователем;
- стилус / S Pen, трекпад-жесты Chromebook, голосовое управление;
- геймпады вне уже существующих Quest-контроллеров;
- Wear OS.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Полный объём задачи. Основной целевой flavor. |
| `lite` | ✅ | Та же клавиатурно-мышиная модель, но только для доступных в flavor экранов и типов контента. |
| `photos` | ✅ | Применяется к главному экрану, обзору, изображениям, настройкам и диалогам; аудио- и документ-специфичные сочетания неактуальны. |
| `legacy` | ✅ | Та же модель поведения, но с учётом `minSdk 23`. |
| `vr` | ✅ | Применяется к общим экранам `app_v2`, к `PlayerActivity`, а для `VrPlayerActivity` — к Bluetooth-клавиатуре поверх VR-сценариев. |
| `vrUnlicensed` | ✅ | Наследует поведение `vr` при том же коде `src/vr/`. |
| `wear` | ❌ | Не входит в задачу: в модуле нет полноценных клавиатурно-мышиных сценариев. |

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23-25 (`legacy`) | Поддержка обязательна, но без опоры на API, которых нет в legacy-ветке. |
| 26+ (`standard` / `lite` / `photos` / `vr`) | Базовый путь реализации. |
| 32+ (Quest / HorizonOS) | Для `VrPlayerActivity` клавиатура рассматривается как дополнительный, а не единственный способ управления. |
| 34 / 35 (`compileSdk 35`) | Поведение должно быть проверено на современных окнах, predictive back и актуальной навигации фокуса. |

### 3.3 Device Modes In Scope

| Mode | In scope? | Notes |
|------|:---------:|-------|
| Планшет + внешняя клавиатура | ✅ | Базовый сценарий |
| Планшет + мышь / трекпад | ✅ | Базовый сценарий |
| ChromeOS / Chromebook | ✅ | Базовый сценарий |
| DeX / оконный режим | ✅ | Базовый сценарий |
| Android TV / Google TV + D-pad / keyboard | ✅ | D-pad и Enter должны совпадать по смыслу с клавиатурной моделью |
| Quest + Bluetooth-клавиатура | ✅ | Дополнительный способ управления в `VrPlayerActivity` |
| Тач-only устройство | ❌ | Не целевая поверхность этой задачи |

### 3.4 Wear OS Impact

No Wear OS changes.

---

## 4. Current Architecture & Coverage

### 4.1 Relevant Surfaces

| Surface | Current state | Notes |
|---------|---------------|-------|
| Главный экран / список ресурсов | Частично хорошо покрыт | Есть стрелки, Page Up/Down, Enter, Delete, F1..F5, Insert/`+`, колесо и ПКМ в карточке |
| `BrowseActivity` | Частично хорошо покрыт | Есть стрелки, Page, Enter, Space, Escape, Del, F2, F5, `Ctrl+A/C/X`, Backspace |
| `PlayerActivity` | Лучшее текущее покрытие | Есть базовые клавиши, медиаклавиши и колесо, но поведение не унифицировано с остальными экранами |
| `StandalonePlayerActivity` | Почти без поддержки | Критичный пробел для сценария «Открыть с помощью» |
| `SettingsActivity` | Частичная поддержка | Есть только навигационный минимум |
| Диалоги и `DialogFragment` | Почти без явной поддержки | Работают лишь дефолты Android |
| VR-плеер | Частичная поддержка | Есть Quest bindings, но нет полной Bluetooth-клавиатурной модели |

### 4.2 Existing Coverage

| Экран / поверхность | Клавиатура | Колесо / ПКМ мыши | Комментарий |
|---|:--:|:--:|---|
| Главный экран (список ресурсов) | ✅ | ✅ | Стрелки, Page Up/Down, Home/End, Enter, Del, F1..F5, Insert / `+` |
| Обзор файлов (`BrowseActivity`) | ✅ | ✅ | Стрелки, Page, Enter, Space, Escape, Del, F2, F5, `Ctrl+A/C/X`, Backspace |
| Плеер (`PlayerActivity`) | ✅ | ✅ | Стрелки, Page Up/Down, Home/End, Enter/Space, медиаклавиши, `[` / `]`, F1..F7, `Ctrl+Z`, Channel Up/Down, Bookmark, Escape |
| Настройки (`SettingsActivity`) | ✅ частично | ❌ | Tab, стрелки между вкладками, Escape |
| VR-плеер (`VrPlayerActivity`) | ✅ частично | н/п | Только Quest controller bindings |

### 4.3 Missing Coverage

| Экран / Activity | Пробел |
| --- | --- |
| `StandalonePlayerActivity` | Нет обработчиков клавиатуры и мыши; сценарий «Открыть с помощью» неравноправен по сравнению с `PlayerActivity` |
| `WelcomeActivity` | Нет полноценной навигации фокуса и Enter/Escape-модели |
| `AddResourceActivity` | Нет единой модели `Tab -> действие -> Enter` |
| `DuplicatesActivity` | Нет навигации стрелками, нет Delete, нет мышиных действий |
| `ResourceEditorActivity` | Нет `Ctrl+S`, `Ctrl+Z`, быстрых действий и предсказуемого закрытия |
| `ReceiveShareActivity` | Нет выбора назначения с клавиатуры |
| Cloud-folder pickers (`GoogleDrive` / `Dropbox` / `OneDrive`) | Нет навигации по дереву, Enter/Backspace-модели, ПКМ |
| `ResourceLaunchWidgetConfigActivity` | Нет выбора ресурса с клавиатуры |

### 4.4 Dialogs & Fragments

Ни один диалог не задаёт общий клавиатурный контракт явно. Сейчас в основном работает только дефолт Android:

- Escape / Back закрывает диалог;
- Tab переводит фокус между focusable-элементами;
- Enter срабатывает только если фокус случайно стоит на кнопке.

В результате отсутствуют единые правила для:

- Enter как «основное подтверждение»;
- Escape как «отмена»;
- стрелок для внутренних списков и radio-групп;
- Space для checkbox / radio;
- ПКМ по элементу списка внутри диалога;
- видимого focus-ring.

### 4.5 Existing Shared Input Code

В репозитории уже есть общие компоненты, которые выглядят как база для решения, но сейчас не определяют поведение всего приложения:

- `util/KeyboardShortcutHandler.kt`;
- `ui/common/MouseEventHandler.kt`;
- `ui/common/FocusManager.kt`.

Следующая техническая спецификация обязана явно решить, что из этого:

1. переиспользуется как основа;
2. дорабатывается;
3. заменяется новым общим слоем диспетчеризации.

---

## 5. Proposed Input Contract

### 5.1 Design Principles

1. **NC-first:** файловые действия привязаны к привычной модели Norton Commander / Far Manager / Total Commander.
2. **Windows duplicate mandatory:** каждое файловое действие имеет обязательный `Ctrl+` дубль, если это разумно для десктопного пользователя.
3. **Modern media keys second:** `Space`, `M`, `F`, `[` / `]` и похожие клавиши используются только там, где NC / Windows не задают более сильную традицию.
4. **Same action -> same semantic result:** `Delete`, `Enter`, `Escape`, ПКМ и колесо должны означать одно и то же по всему приложению, если контекст не требует явного исключения.
5. **Keyboard-only and mouse-only parity:** пользователь должен иметь возможность сделать основное действие без тача.
6. **Visible focus:** если что-то управляется стрелками или Tab, у этого должен быть видимый focus-ring.
7. **Right click equals context:** ПКМ всегда означает контекстное действие / long-press equivalent.

### 5.2 Global Keys

| Клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **F1** | — | Справка / шпаргалка по клавишам текущего экрана | NC |
| **F10** | **Alt+F4** | Выйти из приложения или закрыть текущий полноэкранный хост | NC / Windows |
| **Backspace** | — | Подняться на уровень выше, если есть иерархическая навигация | NC |
| **Escape** | — | Закрыть диалог, отменить выделение, выйти из fullscreen или откатиться на предыдущую поверхность | Windows |
| **Tab / Shift+Tab** | — | Следующий / предыдущий элемент с фокусом | Windows |
| — | **Ctrl+F** | Открыть поиск / фильтр | Windows |
| — | **Ctrl+Z** | Отменить последнюю файловую операцию, если она поддерживается экраном | Windows |
| — | **Ctrl+Y** | Повторить отменённое действие, если оно поддерживается экраном | Windows |

### 5.3 File Lists: Browse, Duplicates, Cloud Pickers

#### 5.3.1 Навигация

| Клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **↑ / ↓** | — | Предыдущий / следующий элемент | NC |
| **← / →** | — | Влево / вправо в сетке либо по вторичной оси списка | NC |
| **Page Up / Page Down** | — | Страница вверх / вниз | NC |
| **Home / End** | — | В начало / в конец списка | NC |
| **Enter** | **Ctrl+Enter** | Открыть: войти в папку или запустить файл | NC |
| **Backspace** | — | Подняться на уровень выше | NC |

#### 5.3.2 Выделение

| Клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **Insert** | — | Выделить текущий и перейти к следующему | NC |
| **Space** | — | Переключить выделение текущего; для папки допустим спец-смысл по экрану | NC |
| **Numpad+** | **Ctrl+A** | Выделить всё или выделить по маске, если позже появится маска | NC / Windows |
| **Numpad-** | **Ctrl+Shift+A** | Снять всё выделение | NC / Windows |
| **Numpad*** | — | Инвертировать выделение | NC |
| **Shift+↑ / Shift+↓** | — | Расширить выделение вверх / вниз | Windows |
| **Escape** | **Ctrl+Shift+A** | Снять текущее выделение, если оно есть | Windows |

#### 5.3.3 Файловые операции

| F-клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **F2** | **Ctrl+R** | Переименовать | NC / TC |
| **F3** | **Ctrl+Q** | Просмотреть файл через подходящий viewer / player | NC |
| **F4** | **Ctrl+E** | Редактировать | NC |
| **F5** | **Ctrl+C** | Копировать | NC |
| **F6** | **Ctrl+X** | Переместить | NC |
| **F7** | **Ctrl+Shift+N** | Создать папку | NC / Windows |
| **F8** / **Delete** | **Ctrl+D** | Удалить с подтверждением | NC / Windows |
| — | **Ctrl+Shift+R** | Обновить список | NC adaptation |
| — | **Ctrl+F** | Поиск / фильтр | Windows |
| — | **Ctrl+V** | Вставить, если появится буфер файловых операций | Windows |

**Примечание:** текущая реализация `BrowseActivity`, где `F5 = Refresh`, конфликтует с NC-моделью. Для целевой модели это считается дефектом поведения.

### 5.4 Main Screen: Resource List

`Ресурс` не равен файлу, но модель клавиш должна быть максимально похожей на файловую.

| F-клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **F1** | — | Справка | NC |
| **F2** | **Ctrl+R** | Переименовать ресурс | NC |
| **F5** | **Ctrl+C** | Скопировать ресурс | NC |
| **F6** | **Ctrl+X** | Переместить / переименовать ресурс | NC |
| **F7** | **Ctrl+Shift+N** | Создать группу ресурсов | NC / Windows |
| **F8** / **Delete** | **Ctrl+D** | Удалить ресурс | NC |
| **F10** | **Alt+F4** | Выйти из приложения | NC |
| **Insert** | — | Добавить новый ресурс | NC reinterpretation |
| — | **Ctrl+Shift+R** | Обновить список ресурсов | NC adaptation |
| — | **Ctrl+F** | Поиск / фильтр ресурсов | Windows |
| — | **Ctrl+Z** | Отменить последнюю операцию | Windows |

Навигация по списку ресурсов следует правилам §5.3.1.

### 5.5 Player: `PlayerActivity` and `StandalonePlayerActivity`

#### 5.5.1 Управление файлами

| F-клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **F1** | — | Справка по клавишам текущего режима плеера | NC |
| **F2** | **Ctrl+R** | Переименовать текущий файл | NC |
| **F3** | **Ctrl+I** | Информация о файле | NC extension |
| **F4** | **Ctrl+E** | Редактировать или открыть редактор / настройки, если это применимо к типу контента | NC |
| **F5** | **Ctrl+C** | Копировать текущий файл | NC |
| **F6** | **Ctrl+X** | Переместить текущий файл | NC |
| **F7** | **Ctrl+G** | GIF-редактор / Edit-dialog / type-specific action | NC extension |
| **F8** / **Delete** | **Ctrl+D** | Удалить текущий файл с подтверждением | NC |
| **F9** | **Ctrl+M** | Контекстное меню файла | NC |
| **F10** / **Escape** | **Alt+F4** | Закрыть плеер или вернуться в предыдущую поверхность | NC |
| — | **Ctrl+Z** | Отменить последнюю файловую операцию | Windows |
| — | **Ctrl+S** | Сохранить кадр / снимок / документный результат, если поддерживается типом контента | Windows |
| — | **Ctrl+Shift+R** | Перезагрузить текущий файл | NC adaptation |

#### 5.5.2 Управление воспроизведением

| Клавиша | Действие | Традиция |
| --- | --- | --- |
| **Space** | Play / Pause | WinAmp / VLC |
| **Enter** | Play / Pause | Windows |
| **← / →** | Предыдущий / следующий файл | NC |
| **Page Up / Page Down** | Предыдущий / следующий файл | NC |
| **Shift+← / Shift+→** | Перемотка `-60 / +60` секунд | MPC-HC |
| **[ / ]** | Перемотка `-10 / +10` секунд | MPV / VLC |
| **↑ / ↓** | Громкость `+ / -` | VLC / MPC-HC |
| **M** | Mute toggle | VLC |
| **F** | Fullscreen toggle | VLC / MPC-HC |
| **, / .** | Кадр назад / вперёд на паузе | VLC |

#### 5.5.3 Documents: PDF, EPUB, TXT

| Клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **Page Up / Page Down** | — | Предыдущая / следующая страница | Windows |
| **Home / End** | — | Первая / последняя страница | Windows |
| **← / →** | — | Предыдущий / следующий файл, а не страница | NC |
| **+ / -** | **Ctrl+Колесо** | Зум `+ / -` для PDF и других поддерживаемых document views | Windows |
| — | **Ctrl+F** | Поиск в документе | Windows |

#### 5.5.4 `StandalonePlayerActivity`

Для сценария «Открыть с помощью» поддержка должна стать функционально равной `PlayerActivity` по клавиатуре, мыши, контекстному меню, Escape/Back и колесу.

### 5.6 Settings: `SettingsActivity`

| Клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **← / →** | — | Переключить вкладку влево / вправо | Windows |
| **↑ / ↓** | — | Следующий / предыдущий пункт | Windows |
| **Enter / Space** | — | Открыть пункт или переключить `Switch` / `Checkbox` | Windows |
| **Tab / Shift+Tab** | — | Следующий / предыдущий focusable-элемент | Windows |
| **Escape** | — | Назад / закрыть | Windows |
| **F10** | **Alt+F4** | Выйти к главной поверхности | NC |
| — | **Ctrl+F** | Глобальный поиск по настройкам | Windows |

Дополнительно требуется:

- колесо мыши в содержимом вкладок;
- ПКМ по пункту, если по нему есть смысловое контекстное действие, например «сбросить по умолчанию»;
- сохранение canonical trigger-row паттерна, если реализация добавит новые help-row или toggle-row.

### 5.7 Dialogs and Fragments

#### 5.7.1 Общие правила

| Клавиша | Ctrl+ дубль | Действие | Традиция |
| --- | --- | --- | --- |
| **Enter** | **Ctrl+Enter** | Основная кнопка: OK / Применить / Подтвердить / Удалить | Windows |
| **Escape** | — | Отмена / закрыть | Windows / NC |
| **Tab / Shift+Tab** | — | Следующий / предыдущий элемент | Windows |
| **↑ / ↓** | — | Навигация внутри списков, radio-групп и menu-like диалогов | NC |
| **Space** | — | Переключить `Checkbox` / `Radio` | Windows |
| **Numpad+** | **Ctrl+A** | Выделить всё внутри list-based диалога | NC / Windows |
| **Numpad-** | **Ctrl+Shift+A** | Снять выделение внутри list-based диалога | NC / Windows |

#### 5.7.2 Специфичные диалоги

- `PlaybackControlDialogFragment`: клавиши `1..7` переключают секции; `← / →` регулируют активный ползунок.
- `DeleteDialog`: `Y` / `Д` подтверждает удаление без мыши.
- `RenameDialog`: `Enter` применяет, `Escape` отменяет, `Ctrl+Z` возвращает исходное имя.
- `FilterResourceDialog`: `Ctrl+Enter` применяет фильтр, `Ctrl+Del` сбрасывает.

### 5.8 VR Player

Существующие Quest controller bindings сохраняются. Если подключена Bluetooth-клавиатура, на `VrPlayerActivity` применяется тот же смысловой контракт, что и в §5.5, без разрушения VR-специфичных кнопок контроллеров.

### 5.9 Mouse Contract

#### 5.9.1 Общие правила

- **ЛКМ одиночный клик:** равен tap.
- **ЛКМ двойной клик:** равен `Enter` для list-like элементов.
- **ПКМ:** открыть контекстное меню или long-press equivalent для элемента под курсором.
- **Средняя кнопка:** reserved until user decision.
- **Колесо:** прокрутка списка либо перелистывание / переход между файлами там, где это уже естественно для плеера.
- **Shift+колесо:** горизонтальная прокрутка, если поверхность её поддерживает.
- **Ctrl+колесо:** масштаб для изображений, PDF и подобных surface types.
- **Hover:** tooltip на иконках и действиях, если у них нет явной текстовой подписи.
- **XButton1 / XButton2:** навигационный back / forward semantics, если платформа и view это поддерживают.

#### 5.9.2 Там, где ПКМ надо довести до паритета

| Поверхность | Ожидание ПКМ |
| --- | --- |
| Главный экран | Проверить и унифицировать уже существующее меню ресурса |
| `BrowseActivity` | Сохранить текущее поведение как эталон |
| Cloud-folder pickers | Меню папки / элемента: обновить, подробнее, копировать ссылку, если доступно |
| `DuplicatesActivity` | Действия по конкретной копии |
| Плеер | Контекстное меню файла / документа / изображения |
| `SettingsActivity` | Контекстное действие для reset / help, где оно уместно |

### 5.10 Screen-by-Screen Gaps To Close

#### 5.10.1 `StandalonePlayerActivity`

- Полная раскладка плеера по §5.5.
- Колесо мыши для перелистывания файлов и прокрутки документов.
- ПКМ для контекстного меню.
- Escape / Back всегда закрывает surface без «застревания» из-за system bars.

#### 5.10.2 `SettingsActivity`

- `Ctrl+F` открывает поиск настроек.
- `Enter` и `Space` гарантированно работают на сфокусированном пункте.
- ПКМ по пункту доступен там, где есть смысловой reset / help.
- Колесо работает на содержимом вкладок.

#### 5.10.3 Главный экран

- `Ctrl+F` для поиска ресурсов.
- `Ctrl+Z` для undo последних операций.
- Фокус по умолчанию должен быть на рабочем списке, а не на случайном toolbar-элементе.
- ПКМ по вкладке типа ресурса допустим, если будет понятное меню управления вкладкой.

#### 5.10.4 `BrowseActivity`

- `Ctrl+Shift+A` / `Escape` снимает выделение.
- `Ctrl+V` готов для будущего file-operation buffer.
- `F7` создаёт папку.
- `Shift+стрелки`, `Shift+клик`, `Ctrl+клик` дают desktop-like range / toggle selection.

#### 5.10.5 `PlayerActivity`

- `↑ / ↓` регулируют громкость.
- `M` — mute.
- `F` — fullscreen toggle.
- `Shift+← / Shift+→` — минутная перемотка.
- ПКМ открывает меню текущего файла.
- Для PDF и EPUB доводятся zoom / search shortcuts.

#### 5.10.6 Dialogs

- Enter всегда нажимает primary action.
- Escape всегда отменяет.
- List-based dialogs поддерживают стрелки, Enter и Space.
- Focus-ring виден.
- ПКМ по list item работает там, где контекстные действия допустимы.

#### 5.10.7 `AddResourceActivity` and Cloud Pickers

- Навигация стрелками по дереву.
- `Enter` входит в папку.
- `Backspace` идёт на уровень выше.
- `Ctrl+L` допускается как переход к ручному вводу пути, если surface поддерживает путь.
- `F5` обновляет текущую папку.
- ПКМ открывает локальное меню элемента.

#### 5.10.8 `WelcomeActivity`

- Tab между кнопками.
- Enter = primary next action.
- Escape = skip, при необходимости с подтверждением.

#### 5.10.9 `DuplicatesActivity`

- Стрелки по списку.
- Space выбирает / снимает.
- Delete удаляет.
- Enter открывает предпросмотр.

#### 5.10.10 `ResourceEditorActivity`

- `Ctrl+S` сохраняет.
- `Ctrl+Z` отменяет.
- Escape закрывает с подтверждением при несохранённых изменениях.

#### 5.10.11 `ReceiveShareActivity`

- Стрелки по списку назначений.
- Enter подтверждает.
- Escape отменяет приём.

#### 5.10.12 `ResourceLaunchWidgetConfigActivity`

- Стрелки по списку ресурсов.
- Enter выбирает.
- Escape отменяет создание.

#### 5.10.13 `VrPlayerActivity`

- Quest bindings сохраняются.
- Bluetooth-клавиатура следует §5.5.

### 5.11 Visual Artifacts Required

1. Видимое кольцо фокуса для keyboard navigation.
2. F1-шпаргалка по горячим клавишам текущего экрана.
3. Tooltip / hover-подсказки на иконках.
4. Контекстное меню должно открываться рядом с курсором или с однозначной привязкой к цели, а не случайно в центре экрана.

---

## 6. Interaction Data Flow

Целевая реализация должна укладываться в единый semantic flow:

```text
Hardware event (KeyEvent / MotionEvent / ContextClick)
  -> Activity / Dialog surface callback
  -> Shared input dispatcher layer
  -> Screen-specific semantic action resolver
  -> existing ViewModel / Manager / helper
  -> UseCase / Repository only when action реально меняет данные
  -> UI state update (focus, selection, command availability, tooltip, dialog state)
```

Ключевая идея: экран не должен оперировать «сырыми» кодами клавиш как бизнес-логикой. Он должен получать уже осмысленное действие уровня `OpenCurrent`, `DeleteSelection`, `ApplyDialog`, `BackOneLevel`, `ToggleMute`, `SearchRequested`.

---

## 7. Architecture Compliance

| Rule | Compliant target | Notes |
|------|:----------------:|-------|
| No heavy logic in Activities | ✅ | Обработку input-контракта нужно вынести в managers / helpers / dispatchers |
| Manager Pattern mandatory | ✅ | Крупные surfaces не должны разрастаться из-за `onKeyDown` / `onGenericMotionEvent` ветвлений |
| Data flow `UI -> ViewModel -> UseCase -> Repository -> DataSource` | ✅ | Клавиши и мышь дают semantic UI-actions, а не обходят ViewModel |
| File size limit `<= 1000` LOC | ✅ | При росте экранов логика должна уходить в `helpers/*Manager` |
| Timber only | ✅ | Логирование input-flow только через Timber |
| Existing comments / KDoc preserved as requirements | ✅ | Техническая реализация должна читать и уважать уже записанные комментарии |
| Settings trigger-row pattern preserved | ✅ | Если реализация добавит новые keyboard/mouse help-row или toggle-row в settings, они обязаны следовать `docs/ARCHITECTURE.md` |

**Implementation constraint:** итоговое решение может переиспользовать текущие `KeyboardShortcutHandler`, `MouseEventHandler`, `FocusManager`, но не обязано. Обязано только одно: поведение должно быть централизовано и не расходиться между поверхностями.

---

## 8. Likely Files / Surfaces To Modify

Точный список должен быть зафиксирован в `PLAN/spec_keyboard-mouse-coverage-impl.md`, но уже сейчас понятно, что работа затронет как минимум эти зоны:

| Area | Likely surfaces |
|------|-----------------|
| Main / resource list | `ui/main/`, `MainActivity`, resource adapters |
| Browse / file lists | `ui/browse/`, selection helpers, adapters, focus navigation |
| Player | `ui/player/`, `PlayerActivity`, `StandalonePlayerActivity`, player dialogs |
| Settings | `ui/settings/`, fragments, search/open actions |
| Dialogs | `dialog/*`, `DialogFragment` surfaces, picker dialogs |
| Shared input infra | `util/KeyboardShortcutHandler.kt`, `ui/common/MouseEventHandler.kt`, `ui/common/FocusManager.kt` or their replacements |
| Visual feedback | focus drawables, tooltip surfaces, F1 help dialog |
| Tests | unit, instrumentation, manual scenarios, possible debug helpers |
| Docs | keyboard-shortcuts docs, mouse docs, quick-start / FAQ / FEATURES mirrors |

---

## 9. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Shortcut conflicts with existing per-screen behaviour | High | Central semantic mapping and per-surface override list must be explicit in impl spec |
| Dialog Enter/Escape changes may trigger destructive actions unexpectedly | High | Every destructive dialog keeps explicit confirmation and a clearly defined primary action |
| Focus remains invisible even after keyboard support lands | Med | Focus-ring is a first-class acceptance criterion, not a cosmetic follow-up |
| `StandalonePlayerActivity` diverges from `PlayerActivity` again | Med | Shared player input layer or exact parity checklist required |
| Mouse parity drifts across flavors | Med | Validate at least `standard`, `photos`, `legacy`, `vr` on supported surfaces |
| External keyboard layouts (`RU` / `UK`) collide with Latin assumptions | Med | Keep this as an explicit open question and cover in test matrix |
| TV / D-pad semantics diverge from keyboard semantics | Med | D-pad must map to the same semantic navigation contract where possible |
| Reusing existing dead code may cost more than rewriting | Med | Technical spec must decide this up front and not mix both strategies blindly |

---

## 10. Testing Plan

### 10.1 Unit Tests

Техническая реализация должна добавить unit tests минимум для:

1. преобразования клавиши в semantic action по экрану / контексту;
2. правил выделения (`Insert`, `Space`, `Ctrl+A`, `Ctrl+Shift+A`, `Shift+selection`);
3. диалогового контракта Enter / Escape / Space / стрелки;
4. player-specific shortcuts (`Space`, `M`, `F`, `[` / `]`, seek shortcuts);
5. fallback-правил между глобальными и screen-specific bindings.

### 10.2 Manual Test Matrix

Обязательные ручные сценарии:

1. Keyboard-only проход: главный экран -> ресурс -> `BrowseActivity` -> файл -> `PlayerActivity` -> назад.
2. Mouse-only проход: список ресурсов -> Browse -> ПКМ -> context action -> player -> context menu -> back.
3. `StandalonePlayerActivity`: открыть внешний файл через `Open with`, пройти все основные действия без тача.
4. `SettingsActivity`: tab / arrows / Enter / Space / `Ctrl+F` / колесо.
5. Dialog sweep: confirm, rename, filter, list-choice dialogs.
6. Cloud picker sweep: arrows / Enter / Backspace / refresh / ПКМ.
7. `DuplicatesActivity`, `ReceiveShareActivity`, widget config, resource editor.
8. VR check: Bluetooth-клавиатура в `VrPlayerActivity` не ломает controller bindings.

### 10.3 Flavor Validation

Минимум должны быть прогнаны:

- `standardDebug`;
- `photosDebug`;
- `legacyDebug`;
- `vrDebug` или `vrUnlicensedDebug`, если эта ветка участвует в rollout.

### 10.4 Build / Lint / Test Gates

Базовые команды для реализации:

- `./gradlew.bat assembleStandardDebug`
- `./gradlew.bat testStandardDebugUnitTest`
- `./gradlew.bat lintStandardDebug`
- при необходимости flavor-specific debug builds через принятые скрипты из `docs/DEV_OPS.md`

### 10.5 Automation Notes

Maestro и другие UI-автотесты могут покрыть лишь часть сценариев. Полная keyboard/mouse parity требует обязательной ручной валидации, потому что hover, ПКМ, focus-ring и внешние клавиатуры плохо моделируются эмуляторно.

---

## 11. Accessibility

1. Все keyboard-only сценарии должны быть выполнимы без мыши и без тача.
2. Focus-ring обязан быть визуально различим и не завязан только на цвет.
3. Tooltip не должен быть единственным источником смысла для действия.
4. Правый клик не должен быть единственным способом вызвать важную команду; у него должен быть клавиатурный эквивалент.
5. Диалоговая primary action должна быть понятна и предсказуема для Enter.
6. Новая F1-шпаргалка должна быть доступна для TalkBack / screen-reader чтения, если она появится как диалог.

---

## 12. User-Facing Feature Update

После реализации эта функция считается user-facing и обязана отразиться в документации.

### 12.1 FEATURES Mirrors

- `docs/FEATURES.md` (EN): `- Full keyboard and mouse coverage across the main resource list, file browser, player, settings and core dialogs, including NC-style function keys, Windows shortcut duplicates, right-click context menus and visible keyboard focus.`
- `docs/FEATURES_RU.md` (RU): `- Полное покрытие клавиатуры и мыши на главном экране, в обзоре файлов, плеере, настройках и основных диалогах, включая NC-стиль F-клавиш, Windows-дубли, контекстные меню по ПКМ и видимый клавиатурный фокус.`
- `docs/FEATURES_UK.md` (UK): `- Повне покриття клавіатури й миші на головному екрані, в огляді файлів, плеєрі, налаштуваннях і ключових діалогах, включно з NC-стилем F-клавіш, Windows-дублями, контекстними меню через ПКМ і видимим клавіатурним фокусом.`

### 12.2 Documentation Outputs Required By Implementation

| Document | Path | Purpose |
|---------|------|---------|
| Keyboard shortcuts guide EN | `docs/keyboard-shortcuts.md` | Полная таблица клавиш по поверхностям |
| Keyboard shortcuts guide RU | `docs/keyboard-shortcuts_ru.md` | Русское зеркало |
| Keyboard shortcuts guide UK | `docs/keyboard-shortcuts_uk.md` | Украинское зеркало |
| Mouse support guide EN | `docs/mouse-support.md` | Клики, ПКМ, средняя кнопка, колесо, hover |
| Mouse support guide RU | `docs/mouse-support_ru.md` | Русское зеркало |
| Mouse support guide UK | `docs/mouse-support_uk.md` | Украинское зеркало |
| Quick start updates | `docs/QUICK_START*.md` | Базовые частые сочетания |
| FAQ updates | `docs/FAQ*.md` | Как работать без тача |

### 12.3 In-app Help Output

Нажатие **F1** на любой поверхности должно открывать актуальную экрану шпаргалку. Источник правды для этой шпаргалки должен быть синхронизирован с keyboard-shortcuts documentation, а не поддерживаться в двух несвязанных таблицах.

---

## 13. Architecture Decision Records (ADRs)

**ADR-1: NC-first, Windows-duplicate model is the canonical file-operation contract.**

- **Decision:** `F2/F5/F6/F7/F8` и смежные файловые действия принимаются по NC-традиции, а `Ctrl+` комбинации обязательны как дубли для desktop-пользователя.
- **Alternatives considered:** чисто Windows-style model без F-клавиш; чисто media-player model.
- **Reason:** приложение уже похоже на file-oriented manager и выигрывает от предсказуемой desktop-парадигмы.

**ADR-2: Dialogs are not exempt from the global contract.**

- **Decision:** диалоги обязаны иметь явные Enter / Escape / arrows / Space semantics.
- **Alternatives considered:** оставить диалоги на дефолтах Android.
- **Reason:** именно диалоги сейчас создают самые заметные «провалы» в keyboard-only сценарии.

**ADR-3: A shared semantic input layer is mandatory.**

- **Decision:** реализация обязана централизовать input contract в общих helper / manager / dispatcher слоях.
- **Alternatives considered:** вручную прописать `onKeyDown` и mouse listeners на каждом экране отдельно.
- **Reason:** задача кросс-приложенческая; ad-hoc реализация гарантированно снова разъедется.

**ADR-4: Right click always means context.**

- **Decision:** ПКМ трактуется как контекстное действие / long-press equivalent на всех surface types, где есть осмысленный target.
- **Alternatives considered:** разрешать ПКМ только на части экранов.
- **Reason:** правый клик — главный десктопный affordance, и его отсутствие мгновенно делает поведение неполноценным.

**ADR-5: `StandalonePlayerActivity` must reach parity with `PlayerActivity`.**

- **Decision:** сценарий «Открыть с помощью» считается first-class и не может оставаться input-second-class surface.
- **Alternatives considered:** оставить его touch-oriented как упрощённый просмотрщик.
- **Reason:** это ломает ожидания пользователя именно в момент внешнего открытия файла, где клавиатура и мышь особенно типичны.

**ADR-6: Wear OS is explicitly out of scope.**

- **Decision:** модуль `wear/` не входит в rollout этой инициативы.
- **Alternatives considered:** распространить требования на весь workspace.
- **Reason:** в wearable-модуле нет эквивалентного keyboard/mouse UX и нет смысла размывать scope.

---

## 14. Open Questions

1. Нужна ли поддержка буквенных shortcut-ов в русской и украинской раскладке, или только в латинице?
2. Что делать со средней кнопкой мыши: оставить свободной, использовать для избранного или для фонового открытия?
3. Нужен ли пользовательский remapping клавиш, или раскладка фиксированная?
4. Для Android TV / пульта: оставляем только D-pad parity или добавляем отдельные color-key actions?
5. Для плеера `↑ / ↓ = volume`: это окончательное решение или опциональная модель?
6. Поиск `Ctrl+F` внутри EPUB / PDF / TXT входит в эту задачу полностью или должен быть вынесен отдельно?
7. F1-шпаргалка должна быть отдельным диалогом, bottom-sheet или частью settings/help surface?
8. Existing shared code (`KeyboardShortcutHandler`, `MouseEventHandler`, `FocusManager`) нужно дорабатывать или заменить?

---

## 15. Implementation Steps

1. Подготовить `PLAN/spec_keyboard-mouse-coverage-impl.md` с точными классами, менеджерами, файлами, split by phases и ownership.
2. На первом этапе внедрить общий semantic input layer, видимый focus-ring и F1 help contract.
3. На втором этапе довести до паритета главный экран, `BrowseActivity`, `PlayerActivity`, `StandalonePlayerActivity`.
4. На третьем этапе унифицировать `SettingsActivity`, ключевые dialogs, cloud pickers, `DuplicatesActivity`, `ResourceEditorActivity`, `ReceiveShareActivity`, widget config.
5. На четвёртом этапе закрыть docs, FEATURES mirrors, FAQ / Quick Start, ручные тесты и flavor validation.
6. После каждого code/config изменения вести `dev/CHANGELOG.md` через `scripts/add_to_dev_log.ps1` и, если затронута публичная API-структура классов, обновлять `dev/CATALOG/`.

**Mandatory checklist for the implementation phase:**

- [ ] Общий semantic input layer выбран и зафиксирован в impl spec.
- [ ] `StandalonePlayerActivity` выровнен с `PlayerActivity`.
- [ ] Dialog contract Enter / Escape / arrows / Space унифицирован.
- [ ] Focus-ring виден на всех keyboard-driven surface types.
- [ ] `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` обновлены.
- [ ] Keyboard / mouse docs созданы в EN / RU / UK.
- [ ] Пройдены build, unit, lint и manual sweeps по целевым flavor-ам.

---

## 16. Out of Scope

- стилус и S Pen hover / air-click;
- трекпад-жесты Chromebook вроде pinch-to-zoom;
- голосовое управление;
- пользовательский remapping, если он не будет отдельно утверждён;
- геймпады вне уже существующих Quest controller bindings;
- переработка VR controller schema;
- Wear OS;
- любые корневые архитектурные изменения, не нужные для keyboard/mouse parity.