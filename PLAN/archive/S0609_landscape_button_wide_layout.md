# Стратегическая спецификация: S0609 - Многоколоночная раскладка экрана настроек в ландшафте

**Ticket:** S0609
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-22
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-22 (follow-up к S0605)
**Tactical spec:** `PLAN/S0609_landscape_button_wide_layout/`
**Tactical plan:** `PLAN/S0609_landscape_button_wide_layout/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-22
**Захвачено во время:** S0605 (verified)

**Текст:**

"Возможно, потом отдельно по каким-то кнопкам в ландшафте переберем их, чтобы занимали больше места. А то справа теперь много свободного места, и множество ингредиентов можно выразить в две колонки."

Контекст (follow-up к S0605): после унификации S0605 кнопки сжаты до размера текста и в ландшафте упакованы слева (Flow packed-left), справа остаётся много свободного места. Идея - выборочно по отдельным формам перебрать ландшафтные раскладки, чтобы группы кнопок/элементов использовали ширину широкого экрана (например, в две колонки), а не жались к левому краю. Это НЕ возврат к full-width растягиванию (инвариант S0605 остаётся в силе), а осмысленное распределение элементов по ширине. Конкретные формы определить на этапе research. Пройтись после следующего релиза.

**Вложения:**

Вложений нет.

**Уточнение фокуса (2026-06-22):** владелец сузил объём до экрана настроек. Задача - изучить фрагменты настроек в ландшафте и определить, какие элементы можно расположить в одну строку по 2/3/4 и более (наборы кнопок, тумблеры, текстовая помощь/описания), чтобы сократить вертикальную высоту групп и задействовать ширину экрана. Идея перебора кнопок в других формах из исходного захвата остаётся отдельным потенциальным follow-up, но в объём S0609 больше не входит.

---

## 1. Проблема

Экран настроек состоит из набора фрагментов-вкладок (общие, изображения, видео, аудио, воспроизведение, документы, назначения, потоки, медиаконтейнер, прочее). Каждый фрагмент - вертикальный список строк-настроек внутри карточек-секций. В ландшафте `layout-land`-варианты повторяют портретную раскладку в одну колонку (а часть фрагментов вообще не имеет ландшафтного варианта и падает на портретный), поэтому на широком экране справа простаивает большое свободное пространство, а группы настроек получаются неоправданно высокими и требуют долгой прокрутки. Пользователю на планшете/в ландшафте телефона приходится много скроллить там, где элементы свободно поместились бы в 2-4 колонки.

---

## 2. Цели

1. Сократить вертикальную высоту групп настроек в ландшафте за счёт размещения подходящих элементов в одну строку по нескольку штук.
2. Задействовать ширину экрана в ландшафте, убрав простаивающее правое поле в фрагментах настроек.
3. Уменьшить объём прокрутки на каждом фрагменте настроек в ландшафте.
4. Получить переиспользуемое решение многоколоночной раскладки внутри секции, применимое к нескольким фрагментам единообразно.

**Non-goals:**

- Возврат к full-width растягиванию одиночных элементов (инвариант S0605 остаётся в силе).
- Изменение портретной раскладки настроек.
- Перебор кнопочных групп в других экранах/формах вне настроек (потенциальный отдельный follow-up).
- Изменение состава, поведения или логики самих настроек - только их геометрическое расположение.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Единый, осмысленный принцип «сколько колонок для какого типа элемента», а не точечный хардкод под каждый фрагмент.
2. Группировать в строку прежде всего однотипные компактные элементы (тумблеры, короткие кнопки, парные значения).
3. Текстовую помощь/описания тоже рассмотреть как кандидата на размещение рядом с управляющим элементом, а не отдельной полной строкой.
4. Покрыть и те фрагменты, у которых сейчас нет ландшафтного варианта.

### 3.2 Жёсткие ограничения

- **Flavor:** все флейворы, показывающие экран настроек; раскладки настроек общие (в основном наборе ресурсов), без флейвор-специфичных source set. Фрагменты, скрытые по capability-гейтам в части флейворов, не должны ломаться.
- **API level:** без API-специфики (раскладочное изменение, базовый minSdk проекта).
- **Wear OS:** не затрагивается.
- **Производительность:** не вводить лишних вложенных весов/измерений, способных замедлить прокрутку и инфляцию длинных фрагментов.
- **Совместимость данных:** миграции нет (изменение только в ресурсах раскладки).
- **Локализация:** EN/RU/UK - длинные локализованные подписи и помощь не должны ломать многоколоночную строку (перенос/обрезка/доступная высота).
- **Доступность:** сохранить порядок фокуса (клавиатура, D-pad/TV), корректные `nextFocus*` при переходе от вертикального к строчному порядку, минимальные touch target, не-цветовое отличие.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** в ландшафте подходящие элементы фрагментов настроек размещаются в одну строку по 2/3/4+ (число колонок зависит от типа элемента и доступной ширины); одиночные широкие элементы не растягиваются на всю ширину (инвариант S0605); портрет не меняется. Конкретный матчинг «тип элемента → число колонок» фиксируется по итогам research (§6).
- **Accessibility:** порядок обхода фокуса для клавиатуры и D-pad/TV сохраняется логичным при переходе к строчной раскладке; touch target и не-цветовое отличие не деградируют.
- **Validation level:** ландшафтная и портретная инфляция каждого затронутого фрагмента настроек проверяется на устройстве/эмуляторе (узкий и широкий экран), плюс прогон EN/RU/UK на длинных подписях.
- **Owner sign-off:** 2026-06-22.
- **Related tickets:** S0605 (предшественник - унификация ширины кнопок в ландшафте; задаёт инвариант «не растягивать на всю ширину»).

---

## 4. Контекст текущей архитектуры

За экран настроек отвечает слой UI: фрагменты-вкладки, каждый со своим ресурсом раскладки, наполняемые переиспользуемыми виджетами-строками (тумблер, выпадающий список, выбор, ввод). Сейчас раскладка каждого фрагмента - вертикальный контейнер строк внутри карточек-секций; ландшафтные варианты, где они есть, дублируют вертикальную одноколоночную структуру, а где их нет - используется портретная раскладка. Решить проблему из §1 без правки раскладок нельзя: одноколоночная вертикаль зашита в сами ресурсы, и нет общего механизма, который в ландшафте перераспределял бы подходящие элементы по колонкам.

---

## 5. Предлагаемый подход

Ввести для ландшафта переиспользуемый принцип многоколоночной раскладки внутри секций настроек: подходящие однотипные элементы группируются в строки по нескольку колонок, а текстовая помощь/описание может занимать соседнюю колонку вместо отдельной полной строки. Портретная раскладка остаётся одноколоночной без изменений. Сначала - research-инвентаризация всех фрагментов настроек и классификация элементов по пригодности и числу колонок; затем - применение единого раскладочного приёма к отобранным группам.

### 5.1 Основные столпы / модули

- **Инвентаризация и классификация.** Пройтись по всем фрагментам настроек, переписать элементы каждой секции и отнести к категориям: «компактный, группируется по N в строку», «широкий, остаётся в одну колонку», «помощь/описание, размещается рядом с управляющим элементом».
- **Правило колонок.** Зафиксировать матчинг «категория элемента → число колонок в ландшафте» с учётом доступной ширины и длины локализованных подписей.
- **Переиспользуемая раскладка.** Единый ландшафтный приём группировки, применяемый ко всем подходящим секциям единообразно, а не точечно под каждый фрагмент.
- **Покрытие фрагментов без land-варианта.** Завести ландшафтную раскладку для тех фрагментов настроек, что сейчас её не имеют.

### 5.2 Потоки данных и событий

Изменение чисто презентационное: данные и поведение настроек не меняются. UI инфлейтит ландшафтную раскладку фрагмента → подходящие элементы попадают в многоколоночные строки → биндинг и обработчики каждого элемента остаются прежними. Слой ViewModel/применения настроек не затрагивается.

### 5.3 Точки расширяемости

- Правило «тип элемента → число колонок» должно легко расширяться на новые типы строк и новые фрагменты.
- Число колонок должно адаптироваться к доступной ширине (узкий ландшафт телефона против широкого планшета), а не быть жёстко прибитым.
- Приём должен переиспользоваться, если позже захочется применить ту же идею к формам вне настроек (follow-up из §0).

---

## 6. Открытые вопросы / Research items

Весь research выполнен 2026-06-22 (codebase-driven, без внешних зависимостей). Все пункты Resolved.

1. **Инвентаризация элементов по фрагментам**
   - **Вопрос:** какие конкретно элементы в каждом фрагменте настроек и к какой категории пригодности относятся?
   - **Статус:** Resolved - 10 фрагментов, классификация COMPACT/BUTTONSET/WIDE/HELP, перечень уже-спаренных и оставшихся пробелов.
   - **Артефакт:** `PLAN/S0609_landscape_button_wide_layout/research/01__settings-fragment-element-inventory.md`

2. **Правило числа колонок по типу элемента**
   - **Вопрос:** сколько колонок (2/3/4+) допустимо для каждой категории элементов и при какой ширине экрана?
   - **Статус:** Resolved - тумблеры 2/ряд; кнопки/радио/чипы 3-4+/ряд; широкие input/dropdown 1/ряд; 3-колоночные тумблеры только на планшете (sw720dp) - вынесено в extensibility, не в первую итерацию.
   - **Артефакт:** `PLAN/S0609_landscape_button_wide_layout/research/02__column-count-rule.md`

3. **Размещение текстовой помощи/описаний**
   - **Вопрос:** как именно располагать помощь - соседней колонкой к управляющему элементу, под группой, или иначе?
   - **Статус:** Resolved - встроенный `iconHelp`-слот строки остаётся; короткую метку+контрол объединять в один ряд; длинные пояснения не дробить.
   - **Артефакт:** `PLAN/S0609_landscape_button_wide_layout/research/03__help-text-placement.md`

4. **Единый раскладочный механизм против точечных правок**
   - **Вопрос:** реализовать многоколоночность общим переиспользуемым приёмом или по-фрагментно?
   - **Статус:** Resolved - канон = weighted horizontal LinearLayout (уже устоявшийся приём в нескольких land-раскладках настроек), чистый XML без Kotlin; Flow только для кнопочных групп.
   - **Артефакт:** `PLAN/S0609_landscape_button_wide_layout/research/04__canonical-mechanism.md`

5. **Фрагменты без ландшафтного варианта**
   - **Вопрос:** какие фрагменты настроек сейчас не имеют land-раскладки и падают на портрет?
   - **Статус:** Resolved - documents, streams, media_container без land-варианта; для documents/streams завести land-файлы, 2-up shell для media_container вынести в extensibility.
   - **Артефакт:** `PLAN/S0609_landscape_button_wide_layout/research/05__fragments-without-landscape.md`

6. **Сохранение порядка фокуса**
   - **Вопрос:** как сохранить логичный обход фокуса (клавиатура, D-pad/TV) при переходе от вертикального к строчному порядку?
   - **Статус:** Resolved - `nextFocus*` нигде нет; для каждой новой пары задать `nextFocusLeft/Right` в том же шаге; вертикаль по умолчанию.
   - **Артефакт:** `PLAN/S0609_landscape_button_wide_layout/research/06__focus-order.md`

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Длинные локализованные подписи (RU/UK) ломают многоколоночную строку | Высокая | Обрезка/наезд текста, рваная высота строк | Прогон EN/RU/UK на длинных подписях; перенос/адаптивное число колонок |
| Нарушение порядка фокуса для клавиатуры/D-pad/TV | Средняя | Нелогичная навигация, регресс доступности | Явное задание `nextFocus*` и проверка обхода в ландшафте |
| Точечные правки вместо единого приёма раздувают и расходятся между фрагментами | Средняя | Несогласованность, дорогая поддержка | Сначала зафиксировать общее правило колонок, затем применять единообразно |
| Случайный возврат к full-width растягиванию одиночных элементов | Низкая | Нарушение инварианта S0605 | Явный non-goal; проверка одиночных широких элементов при ревью |
| Лишние вложенные веса замедляют инфляцию/прокрутку длинных фрагментов | Низкая | Просадка плавности | Избегать глубокой вложенности весов; замер на длинных фрагментах |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES. Это UX-полировка раскладки в ландшафте, а не новая воспринимаемая способность.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Канон многоколоночности - weighted horizontal LinearLayout, чистый XML**

- **Решение:** группировать элементы в ландшафте взвешенным горизонтальным LinearLayout (внешний horizontal, внутренние обёртки `0dp`+`weight=1`, виджет-строка `match_parent`), без изменений в Kotlin.
- **Альтернативы:** ConstraintLayout.Flow (оставлен только для кнопочных групп); GridLayout (хрупкие фиксированные индексы при скрытых по флейвору строках); sw-qualified раскладки (отложены как supplement).
- **Почему:** уже устоявшийся приём в нескольких land-раскладках настроек; работает со всеми виджетами-строками без правок; ViewBinding по id одинаков в обеих ориентациях, setup-хелперы не трогаются; нет позиционного доступа к детям.

**ADR-2: Колоночность только в ландшафте, портрет неизменен; 3+ колонки тумблеров отложены**

- **Решение:** многоколоночность задаётся только ресурсами `layout-land/`; тумблеры - 2 колонки на телефоне; 3 колонки (планшет, sw720dp) и 2-up shell media_container вынесены в extensibility, не в первую итерацию.
- **Альтернативы:** сразу вводить sw720dp-бакеты и 3-колоночные тумблеры.
- **Почему:** 3 колонки тумблеров читаемы только на ширине планшета и клипуют RU/UK-подписи на телефоне; узкие input/dropdown непригодны в половинной колонке; ограничивает рост числа файлов и риск превышения лимита LOC (особенно destinations/land ~1207).

---

## 10. Связи с другими спеками

- S0605 - предшественник: унификация ширины кнопок в ландшафте, задаёт инвариант «не растягивать одиночные элементы на всю ширину», который S0609 обязан сохранить.

---

## 11. Критерии готовности (strategic-level)

1. В ландшафте подходящие элементы фрагментов настроек размещены в строки по 2/3/4+ согласно зафиксированному правилу.
2. Высота групп настроек в ландшафте заметно сокращена, правое поле задействовано.
3. Объём прокрутки на затронутых фрагментах в ландшафте уменьшен.
4. Портретная раскладка настроек не изменилась.
5. Фрагменты настроек, ранее без ландшафтного варианта, теперь имеют осмысленную ландшафтную раскладку.
6. EN/RU/UK на длинных подписях не ломают многоколоночные строки.
7. Порядок обхода фокуса (клавиатура, D-pad/TV) в ландшафте логичен.
8. Инвариант S0605 (одиночные широкие элементы не растягиваются на всю ширину) сохранён.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0609` - создаст `PLAN/S0609_landscape_button_wide_layout/` с фазами.

---

## Last Audit

### 2026-06-23 - Manual device sweep (emulator-5554, Android 17, tablet AVD, noLegal-debug)

Settings shared from `src/main/res` -> renders identically to standard. Settings UI is 4 top tabs (General/Media/Player/Management), each holding collapsible accordion sections. The task's "tabs" map onto these sections.

**Landscape per-section matrix (EN, 2560x1600 / sw800dp / w1280dp land):**

- General interface settings: PASS - Language + Color theme dropdowns 2-up (input ~480px each, not stretched); Allow-windows / Favorites / Resource-ops toggles 3-up; no clipping.
- File browser interface: PASS - Icon-size dropdown + grid-view toggle 2-up; Hide-actions / File-ops-overflow toggles 2-up; "Create resource" button wrap, right-aligned.
- Images: PASS - 3 toggle rows 2-up (Support images/GIF, Load-full/Crop, Dynamic-bg/Slideshow-music); Min/Max size inputs compact (264px each in 2364px container); "Set as default" button wrap.
- Video: PASS - Support/Thumbnails 2-up; Save-frames/Show-FPS 2-up; Min/Max inputs compact; frame-type PNG/JPG radios in one row; "Select Destination" + "Set default" buttons wrap.
- Audio: PASS - Support/Search-covers 2-up; Min/Max inputs compact; empty-state visualizer dropdown compact (560px), not stretched; "Set default" button wrap.
- Documents (Text/PDF/EPUB/Office): PASS - has a landscape variant now (previously portrait-fallback); EPUB/Office toggles 2-up, long subtitles fit; text/PDF single-wide toggles 1-up; "Set default" button wrap.
- Player interface and commands: PASS - 4 rows 2-up; 3-line "3D content from one eye" subtitle stays inside its column, no overlap.
- Sorting/slideshow/playback order: PASS for multi-column - sort-mode dropdown compact (480px). NOTE: slideshow-interval `SettingsInputRow` (`etSlideshowInterval`) is full-width (`match_parent`, ~2432px); this is a composite title+input row widget identical in portrait, not a S0609-introduced single-element stretch - flagged below, not counted as fail.
- Deletion and renaming in player: PASS - Allow-rename / Allow-delete / Confirm-delete toggles 3-up.
- Touch zones and on-screen hints: PASS - hint/overlay toggles 2-up; 3x3 zone grid + legend list 2-column; "Show hint next time" button wrap.
- File deletion and trash (Management): PASS - Confirm-delete / Confirm-move toggles 2-up; Safe-mode / Use-trash single-wide 1-up.
- Copy/move/overwrite behavior: PASS (no clip/stretch) - Allow-copying / Allow-moving are 1-up master gates (not paired by design; overwrite radios are gated behind enabling these, not visible in default state).
- Quick Sort destinations: PASS - Max-recipients dropdown compact (~280px); destination button Flow not exercised (empty state, no folders added).
- Streams: trivial single toggle.

**S0605 single-wide invariant:** all dropdowns (Language, Color theme, Sort mode, Visualizer, Icon size, Max recipients) bounded to ~480px or less, none edge-to-edge. Only `etSlideshowInterval` (and equivalent SettingsInputRow inputs) is full-width - same in portrait, pre-existing widget behavior, not a S0609 regression.

**Locale stress (RU, full restart applied):**

- General interface, File browser, Images, Video, Audio, Player interface, Touch zones re-checked in landscape RU. All 2-up / 3-up rows hold; long RU labels and multi-line subtitles (e.g. the 3-line "3D-контент с одного глаза", "Сворачивает инлайн-кнопки в меню «⋮» на каждой планке ресурса") wrap inside their own column without clipping or overlap. RU = PASS.
- Ukrainian: not exercised separately (RU is the long-label stress case and passed). UK = not tested.

**Portrait spot-check:**

- Tablet portrait (sw800dp, 1600x2560): wide portrait still shows multi-column rows (3-up toggles, 2-up dropdowns) - expected on a wide screen.
- Phone-width portrait forced (wm size 1080x1920 @420dpi, sw411dp, clean activity re-inflate): MOSTLY single-column. Most rows collapse to 1-up, BUT a few horizontal pairs remain 2-up even at 411dp and look cramped (3-4 line wraps): `containerImagesGif` (Support images / Support GIF), Min/Max size inputs, and the Player "Show command panel / Show detailed errors" pair.
- Source check: `layout/fragment_settings_playback.xml` has 4 `orientation="horizontal"` groups vs 9 in `layout-land/`; `layout/fragment_settings_images.xml` has 2 vs 4 in land. `containerFullscreenAndRotation` (Hide-OS-UI / Rotate 2-up) exists ONLY in `layout-land/`. So S0609 did add most 2-up to landscape, but the portrait default layout still carries several horizontal pairs that render 2-up on narrow phones. Whether these portrait pairs predate S0609 or were introduced by it could not be determined from the working tree alone - flagged for owner.

**Focus order (D-pad):** INCONCLUSIVE - D-pad keyevents reach SettingsActivity but no per-view focus highlight is set on this headless AVD (touch-mode overrides D-pad focus; uiautomator dump shows no `focused="true"` node). Not drivable reliably here.

**Logcat probe:** `S0609:` fired 4x - `SettingsActivity: S0609: settings opened in landscape - verify multi-column rows, focus order and EN/RU/UK labels across fragments`. Spec is BlockNeedUserTest; tag present and firing on settings entry - correct.

**Verdict: PARTIAL.** Landscape multi-column layout is correct across every section in EN and RU with no clipping/overflow and S0605 single-wide invariant held (dropdowns bounded). Two caveats prevent a clean PASS: (1) on phone-width portrait a few toggle pairs (Support images/GIF, command-panel/detailed-errors) still render 2-up and look cramped - needs owner confirmation this matches "portrait unchanged" intent; (2) focus order could not be device-verified on this AVD. Screenshots under `temp/S0609_sweep/`.

**Open observations for owner (not parked - need owner judgement, possible pre-existing):**
- Phone-portrait 2-up pairs in `layout/` media/playback fragments vs the ADR-2 "portrait unchanged, 2-up only in layout-land" statement.
- `etSlideshowInterval` full-width input on the playback fragment (pre-existing SettingsInputRow behavior).
