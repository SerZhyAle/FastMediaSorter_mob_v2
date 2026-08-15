# Стратегическая спецификация: S0348 - Иконоподобные home-screen widgets

**Ticket:** S0348
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-04
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-04
**Tactical plan:** `PLAN/S0348_home-widget-icon-refresh/INDEX.md` (first wave)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - обновить существующие widgets: `1x1` widgets должны выглядеть как иконки, Camera-OCR должен стать `1x1` action-widget, а из настроек должна появиться кнопка «Добавить виджет на домашний экран..» с выбором существующего widget и добавлением на home screen.
- **Local anchor:** Provided by user - скриншот текущих home-screen widgets и текущий Camera-OCR widget.
- **Scope boundaries / forbidden areas:** Provided by user - сейчас создать спецификацию; следующие задания по widgets будут добавлены позже в этот же ticket.
- **Done / success signal:** Provided by user - стратегическая спецификация создана; итоговая реализация считается успешной, когда `1x1` widgets визуально читаются как launcher icons, Camera-OCR занимает одну ячейку, а пользователь может начать добавление существующего widget из настроек приложения.
- **Autonomy rule:** Provided by user - approve the ticket; agent may decide tactical details with explicit assumptions and must ask only when implementation would otherwise become unsafe or contradictory.
- **UI decisions / delegation:** Provided by user - approve for tactical planning; use icon-only `1x1` home-screen surfaces with accessible labels, lightweight launcher-icon-style backgrounds, preview parity, hidden unavailable widgets in the in-app picker, explicit fallback for unsupported launcher pinning, and separate tactical handling for resizable/content widgets.

Approval gate complete on 2026-06-04 by explicit owner request: "check the ticket and approve it".

---

## 1. Проблема

Текущие `1x1` widgets выглядят как уменьшенные карточки: тёмная рамка, маленькая пиктограмма и подпись, которая часто обрезается. На рабочем столе они конкурируют с настоящими launcher icons и визуально выглядят тяжёлыми, хотя по смыслу большинство из них являются быстрыми действиями.

Camera-OCR сейчас занимает больше места, чем требует сценарий. Это не информационный widget, а прямой запуск Camera OCR flow, поэтому в нём нечего показывать кроме узнаваемой точки входа.

---

## 2. Цели

1. Сделать все `1x1` action widgets визуально похожими на launcher icons.
2. Убрать обрезаемые подписи из `1x1` home-screen поверхности.
3. Перевести Camera-OCR в `1x1` widget с тем же смыслом запуска.
4. Сохранить текущие действия при нажатии на widgets.
5. Добавить в настройки кнопку «Добавить виджет на домашний экран..», которая открывает меню выбора из существующих widgets.
6. После выбора widget инициировать добавление на home screen из самого приложения, с размещением в первое доступное место там, где это поддерживает launcher.
7. Новые виджеты вынесены в отдельные суб-спецификации (решение владельца 2026-06-04, см. §13) и не входят в первую волну S0348:
   - S0349 - Быстрый Диктофон (Quick Audio Recorder, `1x1`).
   - S0350 - Панель Захвата и OCR (Capture & OCR Panel, `2x1` / `4x1`).
   - S0351 - Виджет воспроизведения (Audio Now Playing Controller, `2x1` / `4x1`).
   - S0352 - Случайный кадр / Цифровая фоторамка (Random Photo Frame, `2x2` / `3x3`).
   - S0353 - Виджет задач по расписанию (Scheduled Tasks Manager, `2x1` / `2x2`).
8. Оставить спецификацию расширяемой для следующих задач по widgets.
9. Провести research, какие новые widgets могут быть полезны пользователям приложения (выполнено, см. §6.3).
10. Провести research возможностей производства интерактивных widgets (выполнено, см. §6.4).

**Non-goals:**

- Не реализовывать изменения в рамках `/spec`.
- Не переписывать весь widget-модуль без тактической спецификации.
- Не превращать крупные информационные widgets в icon-only, если они не работают в `1x1`.
- Не менять основной Camera OCR flow, OCR-настройки или результат распознавания.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. `1x1` widgets должны ощущаться как иконки, а не как грустные мини-карточки.
2. Camera-OCR должен быть маленьким action-entry, потому что его задача - вызвать сценарий.
3. В настройках нужна кнопка «Добавить виджет на домашний экран..».
4. Эта кнопка должна открыть меню выбора из уже существующих widgets.
5. После выбора программа должна сама инициировать добавление выбранного widget на home screen в первое доступное место.
6. Нужен research-блок по потенциально полезным новым widgets.
7. Нужен отдельный research-блок по интерактивным widgets: что можно сделать устойчиво, красиво и без ложных обещаний платформе.
8. Документ должен быть удобным местом для следующих widget-задач.

### 3.2 Жёсткие ограничения

- **Flavor:** затрагиваются app_v2 flavors, где соответствующий widget доступен; Camera-OCR остаётся доступен только там, где доступен Camera OCR flow.
- **API level:** widget sizing должен уважать минимальные Android API целевых flavors и launcher fallback для старых размеров.
- **Wear OS:** не затрагивается.
- **Производительность:** icon widgets не должны добавлять периодический refresh, фоновые вычисления или тяжёлые bitmap-операции.
- **Совместимость данных:** уже размещённые widgets должны обновиться без потери своего действия, где это возможно.
- **Локализация:** EN/RU/UK обязательны для новых или изменённых labels, descriptions, accessibility text и picker-preview text.
- **Доступность:** icon-only поверхность должна сохранять понятное TalkBack-имя, корректный touch target и не зависеть только от цвета.
- **Widget picker:** preview должен соответствовать тому, что пользователь увидит на home screen.
- **Settings entry:** кнопка добавления widget из настроек должна использовать существующие UI-паттерны настроек, поддерживать touch, keyboard, D-pad, mouse и держать текст внутри safe bounds.
- **Launcher capability:** автоматическое добавление в первое свободное место является желаемым поведением; если launcher требует подтверждение или не поддерживает программное закрепление widgets, нужен явный fallback.

### 3.3 Owner inputs (Approval gate)

- **Approval signal:** owner approved S0348 on 2026-06-04 with the request "check the ticket and approve it".
- **Autonomy:** agent may decide tactical details with explicit assumptions and must ask only when implementation would otherwise become unsafe or contradictory.
- **Icon surface policy:** `1x1` action widgets are icon-only on the home-screen surface; accessible names remain in content descriptions, picker labels, and preview descriptions.
- **Settings placement policy:** the in-app picker hides unavailable widgets; unsupported launcher pinning uses an explicit fallback instead of silent failure.
- **Resizable/content policy:** content widgets remain content-first; tactical planning must separate compact action surfaces from resizable/content widget surfaces.
- **Related tickets:** S0134, S0320, S0316, S0289.

---

## 4. Контекст текущей архитектуры

Приложение уже имеет набор отдельных home-screen widgets для быстрых действий, списков и продолжения сценариев. Большинство компактных widgets запускают действие напрямую, но используют одинаковый карточный визуальный язык с маленькой иконкой и подписью.

Крупные widgets выполняют другую роль: показывают список, пустое состояние или состояние продолжения. Их нельзя механически привести к icon-only без отдельного решения, потому что пользователь ожидает от них информации, а не только запуска.

---

## 5. Предлагаемый подход

Ввести продуктовую классификацию widgets: compact action widgets, content widgets и configurable shortcuts. Для compact action widgets основная поверхность должна быть иконкой с доступной семантикой, а не мини-карточкой с текстом. Content widgets сохраняют пространство для списка, состояния или динамического текста.

Camera-OCR переводится в compact action widget. Он запускает тот же Camera OCR flow, не показывает промежуточные данные и не занимает `2x2`.

### 5.1 Основные столпы / модули

**Compact action visual language**

- `1x1` поверхность строится вокруг крупной узнаваемой пиктограммы.
- Видимая подпись не должна обрезаться на home screen.
- Подложка должна быть лёгкой и ближе к launcher icon, чем к dashboard card.

**Camera-OCR as action-only**

- Camera-OCR получает `1x1` sizing.
- Нажатие запускает тот же flow, что и раньше.
- Отсутствие данных внутри widget считается нормальным состоянием, а не пустотой.

**Content widgets stay content-first**

- Favorites и другие widgets с реальным содержимым сохраняют информационную роль.
- Если content widget поддерживает `1x1`, tactical stage должен определить отдельный compact fallback.

**Picker and preview consistency**

- Widget picker previews не должны показывать старую карточную форму после перехода на icon-style.
- Описание в picker должно объяснять действие, а не пытаться заменить подпись на home screen.

**Settings-driven widget placement**

- В настройках появляется отдельная команда «Добавить виджет на домашний экран..».
- Команда открывает внутреннее меню выбора из существующих widgets приложения.
- Выбор widget инициирует системное добавление на home screen без перехода пользователя в launcher picker, где это возможно.
- Размещение в первое доступное место считается целевым поведением для launchers, которые поддерживают такой сценарий.
- Если launcher не даёт приложению разместить widget автоматически, пользователь получает понятный fallback без потери выбранного widget.

**Future widget opportunity research**

- Research должен собрать идеи новых widgets из реальных сценариев приложения, а не из декоративного списка.
- Каждая идея оценивается по частоте использования, ценности одного нажатия, необходимости показывать состояние, размеру, privacy/permission impact и fallback-поведению.
- Приоритет получают widgets, которые сокращают повторяемые действия: продолжить работу, открыть нужный контекст, запустить capture/OCR, управлять текущей операцией или показать важное состояние.
- Research должен отделить полезные widgets от команд, которым достаточно main menu, quick settings или launcher shortcut.

**Interactive widget feasibility research**

- Research должен проверить, какие интерактивные widgets можно сделать на домашнем экране без хрупких launcher-зависимых трюков.
- Первая обязательная идея: audio now-playing widget с текущим треком, playlist/queue context и минимальными playback actions.
- Вторая обязательная идея: photo gallery widget по одному выбранному ресурсу, показывающий фото/thumbnail и позволяющий перейти к просмотру или листать доступный набор там, где это возможно.
- Для каждого кандидата нужно оценить, где проходит граница между полезной интерактивностью и перегруженным home-screen mini-app.

**Реализация новых виджетов (вынесено в суб-спецификации)**

- Детальные требования по каждому новому виджету живут в его суб-спецификации (§13): S0349, S0350, S0351, S0352, S0353.
- Первая волна S0348 даёт фундамент, который суб-спеки переиспользуют: icon-style язык, picker registry (`HomeWidgetCatalog`), pinning flow (`HomeWidgetPinner`) и flavor-gating через манифест.

### 5.2 Потоки данных и событий

Пользователь добавляет widget через launcher picker → launcher показывает compact action или content surface → пользователь нажимает widget → приложение открывает существующий целевой сценарий → если функция выключена или недоступна, widget использует утверждённый fallback.

Пользователь открывает настройки → нажимает «Добавить виджет на домашний экран..» → приложение показывает меню существующих widgets → пользователь выбирает widget → приложение инициирует закрепление widget на home screen → launcher размещает его в первое доступное место или показывает системный fallback → приложение сообщает результат, если платформа возвращает его явно.

### 5.3 Точки расширяемости

- Новые `1x1` widgets должны по умолчанию попадать в compact action visual language.
- Меню добавления из настроек должно автоматически получать новые widgets из общего widget registry / contract tactical-уровня, а не требовать ручного дублирования списка.
- Будущие widget-задачи добавляются как новые цели или research items в этот ticket.
- Tactical stage должен позволить переиспользовать один визуальный contract для нескольких compact action widgets.

---

## 6. Research findings

### 6.1 Платформенные ограничения

Research опирался на текущий widget inventory приложения, локальные источники состояния и официальные Android App Widgets docs:

- Android App Widgets - overview: `https://developer.android.com/develop/ui/views/appwidgets`
- Flexible widget layouts: `https://developer.android.com/develop/ui/views/appwidgets/layouts`
- Widget discoverability / in-app pinning: `https://developer.android.com/develop/ui/views/appwidgets/discoverability`
- Collection widgets: `https://developer.android.com/develop/ui/views/appwidgets/collections`
- Advanced widget updates: `https://developer.android.com/develop/ui/views/appwidgets/advanced`
- AppWidgetProviderInfo reference: `https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo`

Ключевые выводы:

- Widget surface остаётся `RemoteViews`-поверхностью. Это не полноценный экран приложения: поддерживаются ограниченные layout/view типы, `PendingIntent` actions, stateful controls Android 12+ и collection views.
- `1x1` sizing нельзя считать пиксельно одинаковым на всех launchers. На Android 12+ `targetCellWidth` / `targetCellHeight` дают cell hint, на старых версиях работают `minWidth` / `minHeight`, а реальный размер и margins выбирает launcher. Для гарантированного размещения `1x1` используются параметры: `minWidth="48dp"`, `minHeight="48dp"`, `targetCellWidth="1"`, `targetCellHeight="1"`.
- Программное закрепление виджетов из приложения через `AppWidgetManager.requestPinAppWidget` добавлено в Android 8.0 (API level 26). Для `legacy` flavor (где `minSdk = 23`) вызовы API закрепления обязаны сопровождаться рантайм-проверкой `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O`. В случае старых версий ОС или отсутствия поддержки со стороны launcher, приложение показывает пользователю тост `widget_pin_not_supported` (уже локализован как "Widget pinning is not supported on this device" / "Добавление виджетов не поддерживается на этом устройстве").
- Launcher управляет финальным размещением widget. Формулировка "в первое доступное место" допустима как целевое ожидание, но не как безусловное обещание приложения.
- Collection widgets подходят для списков, grid/stack галерей и небольших очередей. Они требуют отдельного data snapshot/update contract; тяжёлый network scan или bitmap work прямо из provider неприемлем.
- `updatePeriodMillis` не подходит для частых live updates: системный период не меньше 30 минут. Для интерактивных/stateful widgets нужны явные updates по событиям приложения, user interaction, service/broadcast callback или WorkManager с battery-aware ограничениями.
- Existing placed widgets могут получить новый layout через update, но изменение provider sizing не обязано физически ужать уже размещённый widget на launcher. Fresh add и existing update нужно проверять отдельно.

### 6.2 Закрытые research items

1. **Visible label policy**
   - **Вывод:** compact `1x1` action widgets должны быть icon-only на home-screen поверхности.
   - **Почему:** видимая подпись в `1x1` конкурирует с launcher label, режется и делает widget похожим на маленькую карточку. Accessibility не ломается, если сохранить content description, picker label и preview description.
   - **Тактическое решение:** во всех макетах `1x1` (`widget_calculator.xml`, `widget_camera_ocr_translate.xml`, `widget_camera_photos.xml`, `widget_continue_reading.xml`, `widget_game_launch.xml`, `widget_random_music.xml`, `widget_resource_launch.xml`) убрать элемент `TextView`. Центрировать и увеличить иконку `ImageView` (до размера порядка `36dp` или `40dp`). Сохранить доступность, задав `android:contentDescription` на корневом контейнере.
   - **Статус:** Resolved - do.

2. **Resource shortcut identity**
   - **Вывод:** configured resource shortcut в `1x1` должен показывать identity через source/type icon, virtual-resource icon и, при необходимости, небольшой badge/tint.
   - **Почему:** без подписи нельзя надёжно различить два одинаковых SMB/SFTP ресурса, но возвращать обрезаемый текст в `1x1` нельзя.
   - **Тактическое решение:** в первой волне использовать icon-only source/type identity; если владелец захочет различать много похожих ресурсов на home screen, добавить отдельный `2x1` labelled resource shortcut или configurable badge pack позже.
   - **Статус:** Resolved - do icon identity, defer labelled variant.

3. **Existing placed widgets**
   - **Вывод:** сохранить действия и обновить surface на месте, где launcher это применяет; не обещать автоматическое сжатие старого Camera-OCR `2x2` до `1x1`.
   - **Почему:** layout update и provider sizing - разные слои. Существующий instance может остаться в прежней grid footprint до manual resize/re-add.
   - **Тактическое решение:** criteria должны отдельно проверять fresh add, existing compact widget update и existing Camera-OCR update. Release note/help fallback: если старый widget не ужался, удалить и добавить заново.
   - **Статус:** Resolved - do with manual verification.

4. **Disabled and unavailable states**
   - **Вывод:** меню добавления из настроек показывает только доступные в текущем flavor/settings widgets. Уже размещённые optional widgets не должны быть dead taps.
   - **Почему:** пользовательский выбор из настроек обязан быть рабочим. Для уже размещённых widgets приложение не всегда может скрыть provider задним числом.
   - **Тактическое решение:** unavailable entries скрывать из in-app picker; placed unavailable widgets показывают disabled icon/state и открывают settings/help/fallback там, где функция может быть включена. Если функция отсутствует во flavor, click не должен молча исчезать.
   - **Статус:** Resolved - do.

5. **Large widget boundary**
   - **Вывод:** первая волна icon refresh касается compact action widgets и Camera-OCR. Favorites остаётся content widget. Continue Reading остаётся compact action в первой волне, а не resizable content redesign.
   - **Почему:** Favorites уже имеет список и пустое состояние, поэтому icon-only сломает назначение. Camera-OCR, Calculator, Random Music, Game, Camera Photos, Continue Reading и простые resource shortcuts являются action-first.
   - **Тактическое решение:** content widgets сохраняют content-first layout; отдельные будущие content widgets проектируются отдельно от `1x1` icon language.
   - **Статус:** Resolved - do.

6. **Settings placement launcher support**
   - **Вывод:** использовать системный in-app pinning flow с capability check. "Первое доступное место" - launcher-owned result, а не прямое действие приложения.
   - **Почему:** Android API отдаёт запрос launcher; launcher может показать подтверждение, отказать, проигнорировать повторные заявки или не поддерживать pinning.
   - **Тактическое решение:** кнопка в настройках открывает меню widgets; выбор вызывает системный pinning flow, если он поддержан; иначе показывается fallback-инструкция через launcher picker. Для configurable resource shortcut конфигурация должна выполняться до pin request или через success callback.
   - **Статус:** Resolved - do.

7. **Widget choice menu content**
   - **Вывод:** меню должно быть registry-driven и фильтроваться по текущей доступности в рантайме.
   - **Почему:** ручной список быстро начнёт расходиться с flavors, settings и optional features.
   - **Тактическое решение:** создать реестр (registry) виджетов. Проверять доступность перед показом в меню:
     - `RandomMusicWidgetProvider`: зависит от `BuildConfig.SUPPORT_AUDIO` (скрыт во flavor `photos`).
     - `CameraPhotosWidgetProvider`: зависит от `BuildConfig.SUPPORT_IMAGES`.
     - `CameraOcrTranslateWidgetProvider`: зависит от `BuildConfig.ENABLE_TRANSLATION` (скрыт во flavors `lite` и `photos`).
     - `GameLaunchWidgetProvider`: зависит от настройки `embeddedGameEnabled`.
     - `FavoritesWidgetProvider`: зависит от настройки `enableFavorites`.
     Скрывать недоступные виджеты из меню in-app picker.
   - **Статус:** Resolved - do.

8. **New useful widgets research**
   - **Вывод:** полезные кандидаты есть, но их надо отделить от S0348 first wave. Самые сильные кандидаты: recent resources, photo gallery by selected resource, audio now-playing controls, transfer/progress status после появления общего progress snapshot.
   - **Почему:** S0348 должен сначала починить текущие widgets и placement flow. Новые content/stateful widgets имеют отдельные data/update contracts.
   - **Тактическое решение:** добавить shortlist ниже; в S0348 не реализовывать новые widgets, кроме текущего refresh и foundations для picker registry.
   - **Статус:** Resolved - shortlist ready.

9. **Interactive widget feasibility**
   - **Вывод:** interactive widgets возможны, но только как snapshot/control surfaces с явными updates. Не делать launcher mini-app.
   - **Почему:** platform limits, update throttling, privacy, battery и network cost делают "живые" home-screen поверхности хрупкими.
   - **Тактическое решение:** audio controls и photo gallery считаются feasible future widgets при соблюдении ограничений; playlist/grid/transfer требуют отдельных state snapshot contracts.
   - **Статус:** Resolved - feasibility report ready.

### 6.3 Shortlist новых widgets

**Do / next candidates after S0348**

- **Recent resources widget** - shortcut или небольшой collection widget для последних ресурсов. Ценность высокая: приложение уже имеет recent-resource concept и dynamic shortcuts; widget может открыть recent virtual resource или конкретный ресурс.
- **Photo gallery by selected resource** - `2x2` / `3x2` content widget по одному выбранному ресурсу. Должен брать только cached file snapshot и cached/local thumbnails; при отсутствии cache показывает icon/empty state и открывает resource.
- **Audio now-playing controls** - control widget для текущей audio session: title, artwork/icon, play/pause, next, previous, open player. Первая версия должна быть control-first; полный playlist внутри widget отложить.
- **Capture/OCR action group** - набор compact actions: Camera Photos, Camera OCR, возможно Scan/Import shortcuts. Это не stateful widget, а аккуратная группа entry points.

**Defer**

- **Audio playlist / queue widget** - feasible, но нужен доступный snapshot текущей queue и current index вне player UI. Делать после now-playing controls.
- **Transfer/progress status widget** - полезен, но текущий progress живёт в потоках конкретных операций. Сначала нужен централизованный operation snapshot с последним состоянием, cancellation policy и privacy rules.
- **Continue browsing carousel** - возможен, если появится история нескольких resume states. Текущий Continue Reading лучше оставить action widget.
- **Favorites quick actions** - Favorites уже закрыт content widget. Отдельный `1x1` "Open Favorites" можно добавить позже, если владелец хочет быстрый вход без списка.
- **Slideshow control widget** - отложить до отдельной модели slideshow state; иначе будет дублировать player controls с устаревшим состоянием.

**Reject for now**

- **OCR result history widget** - высокий privacy risk и быстро устаревающее содержимое на home screen.
- **Destructive cleanup / duplicate actions** - риск случайных операций слишком высок для widget.
- **Live network gallery without cache** - плохой battery/network profile и непредсказуемое поведение launchers.
- **Diagnostics/status widget** - низкая everyday value для обычного пользователя; оставить только как owner-requested debug idea.

### 6.4 Interactive widget feasibility report

**Audio now-playing playlist**

- **Type:** control widget first; collection widget later.
- **Feasibility:** Medium.
- **What is ready:** есть persistent audio playback, Media3 session, foreground service, notification-style controls and commands.
- **Gap:** widget-friendly snapshot текущей queue/current index не является отдельным стабильным contract. Без него playlist widget будет зависеть от живого player UI или показывать stale data.
- **Verdict:** do first as now-playing controls; defer playlist/queue collection.

**Photo gallery by one resource**

- **Type:** content / collection widget.
- **Feasibility:** Medium-high for cached/local content; low for live network content.
- **What is ready:** есть cached file list per resource and thumbnail/cache infrastructure.
- **Gap:** photo-specific thumbnail policy for widget must be explicit; current preload focus is not "all photos for home-screen gallery".
- **Verdict:** do as future content widget with selected resource, cache-only rendering, strict item limit and open-resource fallback. Reject live network scan from widget.

**Transfer/progress**

- **Type:** status widget, maybe control widget for cancel/open details later.
- **Feasibility:** Medium-low today.
- **What is ready:** operations emit progress and byte counts for dialogs/use cases.
- **Gap:** no durable shared snapshot for "current operations" that a widget can read after process death or launcher refresh.
- **Verdict:** defer until central operation-state store exists. No destructive controls in first version.

**Recent resources carousel**

- **Type:** collection widget or shortcut-plus-state.
- **Feasibility:** High.
- **What is ready:** recent resource data and direct-open flows already exist.
- **Gap:** choose between launcher shortcuts, `1x1` recent action, and small widget collection to avoid duplicate UX.
- **Verdict:** do after S0348 if owner wants one more practical widget.

**Continue browsing carousel**

- **Type:** content / collection widget.
- **Feasibility:** Medium-low.
- **What is ready:** resume state exists for continuation.
- **Gap:** a carousel needs multiple resume entries, age rules and privacy handling.
- **Verdict:** defer; keep current compact action in S0348.

**Slideshow/audio controls**

- **Type:** control widget.
- **Feasibility:** Medium.
- **What is ready:** player/slideshow controls exist inside app.
- **Gap:** state ownership and update rules are not unified for widget surface.
- **Verdict:** defer and merge with audio now-playing direction where possible.

**OCR/capture action group**

- **Type:** compact actions, possibly `2x1` group.
- **Feasibility:** High for launch actions, low for OCR result content.
- **What is ready:** existing entry actions are direct and stateless.
- **Gap:** grouping UI and picker story only.
- **Verdict:** do as action group only; reject OCR history/state surface for now.

**Diagnostics/status**

- **Type:** status widget.
- **Feasibility:** Technically possible, product value low.
- **What is ready:** app has diagnostic/settings surfaces.
- **Gap:** everyday user value and privacy/noise justification.
- **Verdict:** reject for S0348; revisit only on explicit owner request.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Launcher по-разному трактует `1x1` размеры | Средняя | Widget может выглядеть слишком маленьким или обрезанным | Проверить sizing на стандартном launcher, Pixel-like launcher и устройстве владельца |
| Удаление видимой подписи ухудшит узнаваемость configured shortcut | Средняя | Пользователь не отличит несколько resource widgets | Утвердить badge/tint/icon policy до tactical stage |
| Preview расходится с реальным home-screen видом | Средняя | Пользователь выбирает одно, а получает другое | Включить preview parity в criteria и device check |
| Camera-OCR provider sizing изменится, но уже размещённый widget останется `2x2` | Средняя | Владелец не увидит исправление без re-add | Зафиксировать manual verification для fresh add и existing widget update |
| Optional widgets получат неясный disabled state | Низкая | Нажатие ведёт в никуда или вызывает путаницу | Принять единый fallback contract |
| Launcher не поддерживает программное добавление widget | Средняя | Кнопка в настройках не сможет выполнить обещанное автоматическое размещение | Использовать системный fallback и заранее проверить capability |
| Меню выбора покажет widget, недоступный в текущем flavor или настройках | Средняя | Пользователь выберет нерабочий entry point | Сформировать visibility contract для списка widgets |
| Интерактивный widget окажется mini-app с тяжёлым состоянием | Средняя | Home-screen поверхность станет нестабильной, медленной или непредсказуемой | До реализации провести feasibility research и отсечь перегруженные идеи |
| Photo gallery widget потянет network/cloud thumbnails слишком часто | Средняя | Батарея, трафик и launcher refresh будут вести себя плохо | Ограничить refresh, использовать кэш и определить offline fallback |
| Audio playlist widget покажет устаревшее состояние playback | Средняя | Пользователь будет видеть неверный трек или очередь | Проверить источник состояния и update contract до tactical stage |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации обновить `docs/FEATURES.md` + `_RU` + `_UK`: Smart Widgets получают icon-style `1x1` action widgets, Camera OCR запускается из компактного `1x1` widget, а настройки позволяют добавить существующий widget на home screen из приложения.

---

## 9. Архитектурные решения (ADR)

**ADR-1: `1x1` widgets are action icons**

- **Решение:** компактные `1x1` widgets считаются action icons, а не content cards.
- **Альтернативы:** оставить карточки с подписью; делать adaptive content inside `1x1`.
- **Почему:** пользователь воспринимает одну ячейку launcher как иконку, а не как место для текста.

**ADR-2: Camera-OCR is action-only**

- **Решение:** Camera-OCR widget занимает `1x1` и не показывает содержимое.
- **Альтернативы:** оставить `2x2`; показывать подсказки или последнее OCR-состояние.
- **Почему:** сценарий начинается с камеры, поэтому до нажатия нет полезного состояния.

**ADR-3: Content widgets keep their own role**

- **Решение:** widgets со списком, пустым состоянием или resume-content не меняются на icon-only без отдельного решения.
- **Альтернативы:** унифицировать все widgets под один визуальный стиль.
- **Почему:** content widgets решают другую задачу и могут нуждаться в большем размере.

**ADR-4: Settings can start widget placement**

- **Решение:** настройки получают команду добавления widget, которая показывает выбор существующих widgets и инициирует закрепление выбранного widget на home screen.
- **Альтернативы:** оставить только launcher picker; добавить отдельные кнопки для каждого widget; показывать справку вместо действия.
- **Почему:** пользователь уже находится внутри приложения и должен иметь короткий путь к нужному widget без ручного поиска в системном списке.

**ADR-5: Launcher owns final placement**

- **Решение:** целевой сценарий - размещение в первое доступное место, но финальное поведение принадлежит launcher и системному pinned-widget flow.
- **Альтернативы:** обещать безусловное авторазмещение; требовать ручное добавление через launcher picker.
- **Почему:** разные launchers по-разному поддерживают закрепление widgets, и спецификация не должна обещать недоступное платформенное поведение.

---

## 10. Связи с другими спеками

- **S0134** - archived widget picker and home polish; исторический ориентир для widget-surface.
- **S0320** - Camera OCR translate; определяет смысл Camera-OCR flow и widget entry point.
- **S0316** - embedded mini-game; содержит optional launcher-widget и disabled fallback precedent.
- **S0289** - TV keyboard D-pad navigation; учитывать accessibility и input coverage там, где меняются config surfaces.

---

## 11. Критерии готовности (strategic-level)

1. Пользователь добавляет compact `1x1` widget и видит иконоподобную поверхность без обрезанной подписи.
2. Camera-OCR добавляется как `1x1` widget и запускает существующий Camera OCR flow.
3. Нажатия на существующие compact widgets сохраняют прежние действия.
4. Widget picker previews соответствуют новому home-screen виду.
5. TalkBack называет icon-only widgets понятными action labels.
6. Крупные content widgets не теряют список, пустое состояние или resume-смысл.
7. Уже размещённые widgets либо обновляются на месте, либо tactical notes явно требуют re-add и manual verification.
8. В настройках есть кнопка «Добавить виджет на домашний экран..».
9. По этой кнопке пользователь видит выбор из существующих widgets, отфильтрованный по текущей доступности.
10. После выбора widget приложение инициирует добавление на home screen и использует системный fallback, если launcher не даёт разместить widget автоматически.
11. Research shortlist потенциально полезных новых widgets зафиксирован в §6.3 с выводом `do / defer / reject`.
12. Interactive widget feasibility report зафиксирован в §6.4 по audio now-playing playlist, resource photo gallery и дополнительным кандидатам.

> Критерии готовности по каждому новому виджету (бывшие 13-17) перенесены в их суб-спецификации §13 (S0349-S0353) и не входят в проверку первой волны S0348.

---

## 12. Ссылка на тактическую спецификацию

Первая волна разложена на фазы: `PLAN/S0348_home-widget-icon-refresh/INDEX.md` (icon refresh, Camera-OCR compaction, widget registry, settings pin picker, docs cleanup).

---

## 13. Spun-out sub-specs (новые виджеты)

Владелец решил 2026-06-04 вести каждый новый виджет отдельной суб-спецификацией, а не внутри S0348 first wave (полный объём превышал бы потолок одной тактической спеки). S0348 остаётся фундаментом, который они переиспользуют.

- **S0349** - Быстрый Диктофон (Quick Audio Recorder, `1x1`). Бывшая цель §2.7/1.1, критерий §11.13.
- **S0350** - Панель Захвата и OCR (Capture & OCR Panel, `2x1` / `4x1`). Бывшая цель §2.7/1.2, критерий §11.14.
- **S0351** - Виджет воспроизведения (Audio Now Playing Controller, `2x1` / `4x1`, MediaSession). Бывшая цель §2.7/2.2, критерий §11.15; feasibility §6.4.
- **S0352** - Случайный кадр / Цифровая фоторамка (Random Photo Frame, `2x2` / `3x3`, WorkManager). Бывшая цель §2.7/2.3, критерий §11.16; feasibility §6.4.
- **S0353** - Виджет задач по расписанию (Scheduled Tasks Manager, `2x1` / `2x2`). Бывшая цель §2.7/2.5, критерий §11.17.

Все пять созданы в статусе `Draft` и ждут approval gate перед `/spec-tech`.

## Revision History

- **2026-06-04** - by `/spec-tech` (focus: first-wave tactical plan + descope new widgets)
  - Created tactical plan `S0348_home-widget-icon-refresh/` (5 phases, first wave).
  - Descoped 5 new widgets into sub-specs S0349-S0353 (owner decision); removed criteria §11.13-17 and §5.1 new-widget detail; added §13.
  - Status -> Tactical.
- **2026-06-04** - by `/spec-update` (`Codex`, focus: approval gate)
  - Applied: 1. Proposed (DISCUSS): 0.
- **2026-06-04** - by `/spec-update` (`Codex`, focus: add new widgets implementation details and scheduled tasks widget)
  - Applied: 1. Proposed (DISCUSS): 0.
- **2026-06-04** - by `/spec-update` (`Codex`, focus: update research findings with codebase details)
  - Applied: 1. Proposed (DISCUSS): 0.
- **2026-06-04** - by `/research` + `/spec-update` (`Codex`, focus: S0348 research closure)
  - Applied: 1. Proposed (DISCUSS): 0.
- **2026-06-04** - by `/spec-update` (`Codex`, focus: completeness)
  - Applied: 1. Proposed (DISCUSS): 0.
- **2026-06-04** - by `/spec-update` (`Codex`, focus: completeness)
  - Applied: 1. Proposed (DISCUSS): 0.
- **2026-06-04** - by `/spec-update` (`Codex`, focus: completeness)
  - Applied: 1. Proposed (DISCUSS): 0.
