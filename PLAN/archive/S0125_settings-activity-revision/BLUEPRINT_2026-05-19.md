# S0125 - Blueprint нового окна настроек до реализации

**Date:** 2026-05-19  
**Branch:** DEBUG-v004  
**Strategic spec:** `PLAN/S0125_settings-activity-revision.md`  
**Status:** REVIEW DRAFT - pre-implementation blueprint  
**Purpose:** зафиксировать целевой пользовательский результат нового окна настроек до сборки нового tactical plan и до начала реализации.

---

## 1. Что это за документ

Это не tactical checklist и не phase plan.

Это целевой blueprint итогового окна настроек, который отвечает на вопросы:

- какое окно пользователь должен увидеть в итоге;
- какие top-level страницы в нём будут;
- в каком порядке будут идти группы и management-entry points;
- как будут называться вкладки, секции и ключевые зоны;
- какие UI-элементы будут собраны какими Android / Material-компонентами;
- что останется inline-строкой, а что останется отдельной management surface;
- в каком порядке это разумно собирать после утверждения дизайна.

Документ предназначен для owner review до любого нового `/spec-tech` и до нового `/spec-dev`.

---

## 2. UI Clarification Status

**Status:** READY

### Approved Decisions

- Новое окно сохраняет 4 top-level страницы в том же порядке: `General` -> `Media` -> `Playback` -> `Operations`.
- Portrait и landscape используют один и тот же набор страниц и один и тот же порядок секций внутри каждой страницы.
- Search остаётся глобальным для всего revised host и открывается из правого верхнего угла toolbar.
- Search result всегда ведёт в каноническую вкладку, раскрывает нужную секцию, ставит фокус на целевой control и кратко подсвечивает его.
- Legacy settings window остаётся рабочим fallback-path на всём протяжении миграции.
- Revised window собирается на новых объектах и новых поверхностях; legacy host не переписывается in-place.

### Delegated Assumptions

- Публичная версия revised host не должна показывать пользователю название `New settings`; после re-exposure итоговый title должен стать обычным локализованным `Settings` / `Настройки` / `Налаштування`.
- Внутренняя hidden-incubation сборка может временно использовать технический title `New settings`, пока revised host не является публичным путём.
- На wide-screen layout приоритет у одной читаемой вертикальной колонки с локальными двухколоночными строками внутри секций, а не у двух независимых колонок секций.

---

## 3. Общее устройство окна

### 3.1 Хост-поверхность

Новое окно должно оставаться отдельной Activity-поверхностью, а не режимом внутри legacy `SettingsActivity`.

Целевой host:

- отдельная Activity для revised settings;
- собственный toolbar;
- собственный global search overlay;
- `ViewPager2` + `TabLayout` для top-level страниц;
- отдельная keyboard / D-pad navigation manager;
- отдельный search registry для canonical deep-link navigation.

### 3.2 Верхняя часть окна

В итоговом варианте верхняя часть окна должна выглядеть так:

- слева кнопка Back;
- в центре или слева title окна;
- справа кнопка Search;
- под title-row в portrait и в одной горизонтали с ним в landscape - strip top-level tabs.

### 3.3 Search overlay

Search overlay должен открываться поверх body-части окна под toolbar.

Он состоит из:

- строки поиска;
- кнопки закрытия поиска;
- вертикального списка результатов;
- текста пустого состояния.

Search overlay не должен быть отдельной Activity или dialog. Это встроенная overlay-поверхность окна настроек.

---

## 4. Top-level страницы

Итоговая карта top-level страниц:

1. `General` / `Общие` / `Загальні`
2. `Media` / `Медиа` / `Медіа`
3. `Playback` / `Воспроизведение` / `Відтворення`
4. `Operations` / `Операции` / `Операції`

Число top-level страниц не растёт.

Новые крупные сущности не создают новые top-level tabs. Они либо встраиваются в одну из этих страниц, либо остаются отдельной management surface с entry point из одной из них.

---

## 5. Итоговый вид страницы General

### 5.1 Порядок секций

Итоговый порядок General page:

1. `Interface` / `Интерфейс` / `Інтерфейс`
2. `Grid & Browse` / `Сетка и навигация` / `Сітка та навігація`
3. `Network & Cache` / `Сеть и кэш` / `Мережа та кеш`
4. `App Data & Backups` / `Данные и резервные копии` / `Дані та резервні копії`
5. `Permissions & Access` / `Разрешения и доступ` / `Дозволи та доступ`
6. `About` / `О приложении` / `Про програму`

### 5.2 Как страница должна выглядеть

Страница выглядит как вертикальный stack card-секций:

- каждая секция визуально завернута в `MaterialCardView`;
- header секции - отдельный clickable / focusable block;
- body секции - вертикальная группа rows;
- между секциями есть чёткие отступы и локальные границы.

### 5.3 Содержимое секций

`Interface`

- theme / dark mode selection;
- language selection;
- browse visibility toggles;
- hidden files / all files / subfolder presentation;
- compactness / control density toggles;
- resource-ops placement toggle;
- favorites and similar long-lived UI preferences.

`Grid & Browse`

- icon size;
- grid mode;
- grid action buttons visibility;
- file-ops overflow behavior in grid contexts.

`Network & Cache`

- network parallelism;
- prefetch / pre-cache settings;
- streaming cache cleanup and TTL;
- background sync toggle and interval;
- cache limit and clear-cache actions;
- section-level reset for system/network/cache slice.

`App Data & Backups`

- remember-list and similar app-behavior persistence toggles;
- default credentials inputs;
- export / import settings;
- backup / restore actions;
- log export and related support actions.

`Permissions & Access`

- это не обычная collapsible settings-группа;
- это явный management-entry point;
- пользователь должен видеть отдельный переход в dedicated permissions screen.

`About`

- user guide;
- privacy / terms / open source / about entries;
- version/build information;
- support / feedback entry points.

### 5.4 Что не должно остаться на публичной General page

- debug / expert controls не должны оставаться частью основного публичного General flow;
- элементы, которые являются отдельным account-management workflow, не должны визуально маскироваться под обычные toggles;
- destructive reset-all control не должен быть равноправной обычной строкой в общей массе General rows.

---

## 6. Итоговый вид страницы Media

### 6.1 Порядок секций

Итоговый порядок Media page:

1. `Images` / `Изображения` / `Зображення`
2. `Video` / `Видео` / `Відео`
3. `Audio` / `Аудио` / `Аудіо`
4. `Documents` / `Документы` / `Документи`
5. `Other` / `Прочее` / `Інше`

### 6.2 Верхняя action-зона страницы

Над списком media-sections допустим один action-level control:

- `Set default media player`.

Это action-button, а не настройка-строка. Он должен визуально отличаться от rows и оставаться вне card-body.

### 6.3 Как страница должна выглядеть

Media page должна оставаться одной top-level страницей с внутренними collapsible category cards, а не превращаться во второй уровень tab-strip.

Каждая media-category:

- отдельная `MaterialCardView`;
- header с названием категории;
- body как контейнер для category-specific rows;
- unsupported by flavor categories полностью скрываются, не disabled;
- карточки могут быть раскрыты одновременно (независимое скрытие/раскрытие), без строгого поведения accordion.

### 6.4 Содержимое

Каждая media-category хранит только свой профиль настроек:

- `Images` - image-specific viewer and interaction settings;
- `Video` - video-specific defaults and UI behavior;
- `Audio` - audio-specific preferences;
- `Documents` - document-reader related preferences;
- `Other` - residual media-specific settings, которые не заслуживают отдельной top-level страницы.

### 6.5 Принцип построения

Media page допускает внутренние child-fragment или binder-based containers на этапе миграции, но итоговый пользовательский вид должен читаться как одна цельная revised page, а не как host для legacy-контента в новом каркасе.

---

## 7. Итоговый вид страницы Playback

### 7.1 Порядок секций

Итоговый порядок Playback page:

1. `Sorting & Slideshow` / `Сортировка и слайдшоу` / `Сортування та слайдшоу`
2. `File Access in Player` / `Доступ к файлам в плеере` / `Доступ до файлів у плеєрі`
3. `Player UI` / `Интерфейс плеера` / `Інтерфейс плеєра`
4. `Touch Zones` / `Сенсорные зоны` / `Сенсорні зони`
5. `Remote & Gamepad` / `Пульт и геймпад` / `Пульт та геймпад`
6. `Autoplay & Resume` / `Автовоспроизведение и продолжение` / `Автовідтворення та продовження`

### 7.2 Как страница должна выглядеть

Playback page также строится как вертикальный stack section cards.

Внутри неё допустимы paired rows на широких окнах, но только если:

- две строки логически независимы;
- не рвётся parent-child связь;
- не разрывается связь toggle + help;
- не уходит в соседнюю колонку inline warning / dependent control.

### 7.3 Содержимое

`Sorting & Slideshow`

- default sort mode;
- slideshow interval;
- playback progression defaults.

`File Access in Player`

- rename/delete availability;
- confirm-delete in player context;
- file-op access rules inside player.

`Player UI`

- fullscreen system UI behavior;
- command panel defaults;
- detailed error visibility;
- PiP and rotation-related behavior;
- larger player UI-specific toggles.

`Touch Zones`

- touch-zone layout and interaction mapping controls.

`Remote & Gamepad`

- D-pad / Remote button mapping inside player;
- gamepad shortcuts;
- DPAD navigation adjustments for Android TV playback.

`Autoplay & Resume`

- auto-play next logic;
- save/resume last position;
- loop settings and playback flow behavior rules.

---

## 8. Итоговый вид страницы Operations

### 8.1 Порядок секций

Итоговый порядок Operations page:

1. `Safety & Confirmation` / `Безопасность операций` / `Безпека операцій`
2. `Delete & Trash` / `Удаление и корзина` / `Видалення та кошик`
3. `Scheduled` / `По расписанию` / `За розкладом`
4. `Copy & Move` / `Копирование и перемещение` / `Копіювання та переміщення`
5. `Quick Sort List` / `Список быстрой сортировки` / `Список швидкого сортування`

### 8.2 Как страница должна выглядеть

Operations должна читаться как страница действий и safety-contracts, а не как хвост из старых destination-настроек.

### 8.3 Содержимое

`Safety & Confirmation`

- safe mode;
- confirm delete / confirm move.

`Delete & Trash`

- trash usage (move to trash vs delete permanently);
- clear-trash action;
- auto-empty trash settings (if applicable).

`Scheduled`

- master toggle for scheduled operations;
- global notification settings for background operations (sound, vibration, silent);
- permission request action when notifications are unavailable;
- list of scheduled operations;
- add / edit / log / bulk-clear actions.

Это management surface внутри страницы. Она не должна мимикрировать под простой toggle-list.

`Copy & Move`

- enable copying / moving;
- overwrite rules;
- go-to-next-after-copy;
- related help affordances.

`Quick Sort List`

- max recipients / destination limit control;
- add destination action;
- destinations list;
- reorder, recolor, delete and similar list-management actions.

Эта секция тоже остаётся semi-management surface и использует list-based body, а не только статические строки.

---

## 9. Search и навигация

### 9.1 Что пользователь увидит

Search должен выглядеть как глобальный поиск по настройкам этого окна, а не как технический фильтр текущей вкладки.

Пользователь вводит запрос и получает список результатов, где каждый результат показывает:

- название настройки;
- секцию или контекст, где она лежит.

### 9.2 Поведение search result

По тапу, клику, Enter или D-pad select:

1. search overlay закрывается;
2. окно переключается на нужную top-level страницу;
3. нужная секция раскрывается;
4. нужный control получает фокус;
5. control кратко подсвечивается.

### 9.3 Search corpus

Search index должен строиться по:

- canonical key;
- EN aliases;
- RU aliases;
- UK aliases;
- partial-word matching.

### 9.4 Поиск по вложенным экранам

Если целевая настройка находится внутри `management surface` (например, внутри выделенного экрана Permissions или списка Scheduled operations):

- результат поиска должен вести на `entry-point` (кнопку входа) родительской страницы;
- альтернативно (опционально, если поддерживается) - выполнять deep-link прямо во вложенный Fragment/Dialog с фокусом на элементе.

---

## 10. Portrait / Landscape / Wide-screen

### 10.1 Portrait

- title/search row сверху;
- tab strip отдельной строкой под ним;
- body как одна вертикальная колонка card-секций;
- длинные paired controls могут становиться stacked, если не хватает ширины.

### 10.2 Landscape

- toolbar и tab strip живут в одной верхней полосе;
- body остаётся той же по смыслу картой;
- разрешены горизонтальные paired rows внутри card-body.

### 10.3 Wide / tablet-like

- набор страниц и секций тот же;
- секции не расползаются на всю ширину экрана без ограничений;
- приоритет у боковых полей и локальной близости controls;
- две независимые настройки могут жить в одной строке;
- atomic setting-groups не разрываются.

### 10.4 Сохранение состояния (State restoration)

При смене конфигурации (например, поворот экрана) хост обязан:

- сохранять текущую активную вкладку (top-level page);
- сохранять состояние всех раскрытых (expanded) карточек и секций;
- восстанавливать позицию скролла в `NestedScrollView`.

---

## 11. Android / Material элементы, из которых это должно быть собрано

### 11.1 Хост-уровень

- Activity host: `BaseActivity` + `ViewBinding`
- Root layout: `ConstraintLayout`
- Page switching: `ViewPager2`
- Tabs: `TabLayout` + `TabLayoutMediator`

### 11.2 Search layer

- search field: `EditText`
- close / open buttons: `ImageButton`
- result list: `RecyclerView` + `ListAdapter`
- empty state: `TextView`

### 11.3 Page body

- scroll container: `NestedScrollView`
- section wrapper: `MaterialCardView`
- section header: clickable `TextView` acting as disclosure / expand control
- section body: `LinearLayout`, `ConstraintLayout`, `FrameLayout` or list-container depending on content type

### 11.4 Row level

- switch rows: `MaterialSwitch`
- checkbox rows: `MaterialCheckBox`
- primary labels / summaries: `TextView`
- help affordance: `ImageButton` with `ic_help_outline_24`
- dropdown / enum settings: `TextInputLayout` + `MaterialAutoCompleteTextView` or `AutoCompleteTextView`
- free-text inputs: `TextInputLayout` + `TextInputEditText`
- action rows / commands: `MaterialButton`

### 11.5 List / management slices

- destinations list: `RecyclerView`
- scheduled operations list: `RecyclerView`
- confirmation UX: `AlertDialog` or dedicated `DialogFragment`
- complex managers: отдельные `Fragment` / `DialogFragment`, а не перегруженные inline-rows и не новые `Activity` поверх текущей.

---

## 12. Naming contract

### 12.1 Top-level tabs

- `General` / `Общие` / `Загальні`
- `Media` / `Медиа` / `Медіа`
- `Playback` / `Воспроизведение` / `Відтворення`
- `Operations` / `Операции` / `Операції`

### 12.2 General sections

- `Interface`
- `Grid & Browse`
- `Network & Cache`
- `App Data & Backups`
- `Permissions & Access`
- `About`

### 12.3 Media sections

- `Images`
- `Video`
- `Audio`
- `Documents`
- `Other`

### 12.4 Playback sections

- `Sorting & Slideshow`
- `File Access in Player`
- `Player UI`
- `Touch Zones`
- `Remote & Gamepad`
- `Autoplay & Resume`

### 12.5 Operations sections

- `Safety & Confirmation`
- `Delete & Trash`
- `Scheduled`
- `Copy & Move`
- `Quick Sort List`

---

## 13. Что должно остаться отдельными management surfaces

Следующие сущности не должны насильно превращаться в простой inline toggle-list:

- permissions management;
- scheduled operations management;
- destinations / quick-sort list management;
- account / auth-session management;
- open-source licenses and similar long-form informational surfaces;
- help / guide / docs transitions.

---

## 14. Порядок реализации после review

Это не tactical file, но рекомендуемый порядок сборки результата такой:

1. **Shell contract**  
   Новый revised host, toolbar, search overlay, tab strip, keyboard navigation, focus contract, state restoration.

2. **General page**  
   Сначала собрать наиболее читаемый пользовательский baseline: Interface -> Grid & Browse -> Network & Cache -> App Data & Backups -> Permissions & Access -> About.

3. **Operations page**  
   Затем собрать safety и management-heavy страницу: Safety -> Delete & Trash -> Scheduled -> Copy & Move -> Quick Sort List.

4. **Media page**  
   После этого зафиксировать category-based media map: Images -> Video -> Audio -> Documents -> Other.

5. **Playback page**  
   Затем собрать playback-specific vertical card map с нативными секциями.

6. **Search parity**  
   После стабилизации page map довести search до полного canonical reveal, focus, highlight, multilingual alias coverage.

7. **Public re-exposure gate**  
   Только после native page parity, input parity и human sign-off revised host может вернуться в публичный user path.

---

## 15. Что не входит в первую публичную волну

- удаление legacy settings window;
- in-place переписывание legacy `SettingsActivity`;
- расширение числа top-level tabs;
- превращение management surfaces в длинный flat list;
- публикация revised host с техническим title `New settings`.

---

## 16. Итог в одной фразе

Итоговое новое окно настроек должно быть отдельной 4-tab поверхностью с глобальным поиском, сильными card-секциями, явными management-entry points и одинаковой mental map в portrait / landscape, при этом legacy окно остаётся рабочим до полного parity-sign-off.