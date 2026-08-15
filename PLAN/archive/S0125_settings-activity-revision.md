# Стратегическая спецификация: S0125 - Пересмотр экрана настроек

<!-- auto-approved by /spec-all — 2026-05-14 -->

**Ticket:** S0125
**Status:** Partial
**Priority:** 65
**Date:** 2026-05-09
**Tier:** 4 - Strategic
**Roadmap entry:** Ad-hoc - запрос пользователя 2026-05-09
**Tactical plan:** `PLAN/S0125_settings-activity-revision/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Экран настроек давно перестал быть одной простой формой и превратился в крупную многоуровневую поверхность с верхними разделами, вложенными секциями, feature-зависимыми ветками, поиском, сервисными действиями и отдельными management-сценариями. Система в целом работает, но её структура воспринимается исторической: новые пункты попадали туда, где их было проще встроить технически, а не туда, где пользователь естественно ожидает их найти.

Из-за этого внутри одной поверхности смешиваются разные типы сущностей: долговременные preferences, destructive и safety-переключатели, разрешения, служебные действия, переходы на отдельные управляющие экраны, справочные ссылки и debug-зона. Часть спорных пунктов сейчас находится в неочевидных местах, часть поведения дублируется, а часть discoverability уже компенсируется поиском вместо того, чтобы вытекать из самой структуры.

Живое состояние продукта показывает несколько характерных симптомов. Destructive controls распределены по разным смысловым зонам и частично дублируются. Одна management-entry привязана к соседнему feature-toggle, хотя по смыслу относится к отдельной сервисной сущности. Справочные и legal entry points ощущаются нижним хвостом общего раздела, а не самостоятельной информационной зоной. Search уже встроен как реальный navigation layer, но его reveal-поведение остаётся неоднородным между разными ветками. На широких окнах связанные controls местами теряют локальность и расползаются по избыточному пространству.

Дополнительная проблема состоит в том, что предыдущая итерация локальных перестановок не дала устойчивого результата. Она пыталась улучшить отдельные зоны, но не зафиксировала достаточно жёсткий контракт на сохранение поведения, каноническое место спорных сущностей и правила будущего роста. В итоге локальные решения были откатаны, а исходная стратегическая потребность осталась.

Новая спецификация должна начать заново от живого состояния продукта, сохранить полезную стратегическую рамку прошлой версии и явно отделить её от неудачной тактической декомпозиции.

---

## 2. Цели

1. Зафиксировать понятную и устойчивую информационную архитектуру экрана настроек, основанную на пользовательской логике, а не на близости к текущему коду.
2. Развести внятные типы сущностей: долговременные preferences, safety / destructive controls, permissions, service-actions, dedicated management surfaces, help / legal / info-зоны, debug / expert-элементы.
3. Устранить наиболее спорные placement-конфликты на стратегическом уровне: неочевидные destructive controls, дубли подтверждений, смешение player-параметров с management-entry, разорванные help / info-зоны, неоднородность search-target поведения.
4. Сохранить поиск по настройкам как ускоритель навигации, но перестать использовать его как замену хорошей структуры.
5. Зафиксировать канонический placement-contract для будущих фич: куда должна попадать новая настройка, а в каких случаях она не должна попадать в глобальный Settings вообще.
6. Сохранить все реальные пользовательские сценарии при миграции: toggles, confirmations, expand / collapse, helper-affordances, transitions на отдельные management surfaces, search deep-links, focus-based navigation.
7. Зафиксировать responsive contract для узких и широких окон, чтобы на маленьких экранах настройки не превращались в бесконечную прокрутку, а на больших не распадались на пустые растянутые области.
8. Зафиксировать multilingual discoverability contract для EN/RU/UK, включая search aliases и partial-word matching.
9. Подготовить безопасную фазовую миграцию без изменения storage semantics и без потери текущих функций.
10. Сформировать основу для нового тактического плана, который не будет повторять ошибки предыдущей локальной перегруппировки.
11. Зафиксировать обязательный non-regression-принцип: при переписывании и унификации settings не теряется ни одна существующая пользовательская функция, уже привязанная к текущим элементам интерфейса.
12. Сохранить и формализовать input parity для всех поддерживаемых способов взаимодействия: touch, mouse, keyboard shortcuts, D-pad / TV remote.
13. Сохранить и формализовать theme parity: новая модель settings одинаково корректно работает в светлой и тёмной теме без потери контраста, визуальной иерархии и распознаваемости interactive-элементов.
14. Сохранить и формализовать localization parity: EN, RU и UK должны быть синхронны не только по видимым строкам, но и по discoverability-слою, включая поиск по настройкам.
15. Зафиксировать дизайн-контракт settings так, чтобы визуальная унификация не разрушала уже существующие affordances: раскрытие групп, helper-buttons, поиск, фокус-навигацию, management screens и action-oriented элементы.
16. Зафиксировать orientation composition parity: portrait и landscape обязаны содержать одинаковый набор groups в одинаковом порядке; различаться может только внутренняя body-укладка группы.
17. Зафиксировать pre / post inventory contract: до начала тактических работ собирается полная инвентаризация всех видимых элементов settings в portrait и landscape; после завершения работ выполняется обратная сверка, подтверждающая, что ни один элемент не пропал и не потерял своё user-facing behavior.
18. Зафиксировать стабильность верхнего каркаса: число top-level страниц settings не растёт; допускается переименование разделов и переразложение их содержимого, но не добавление новых вкладок верхнего уровня.
19. Разрешить staged internal incubation revised settings-host: новая модель может развиваться скрыто и без публичного экспонирования до появления хотя бы одной нативно переработанной и визуально отличимой top-level страницы.
20. Зафиксировать removal-gate для legacy settings: старая система может быть отключена только после подтверждённой parity не только по самим настройкам, но и по их descriptions, helper-текстам и встроенному form-behavior.
21. Зафиксировать gated re-exposure input parity: после возврата revised surface в публичный доступ она проходит отдельную проверку touch, mouse, keyboard shortcuts и D-pad / TV remote, а до этого legacy settings остаётся единственным публичным all-input path.
22. Зафиксировать implementation-isolation contract: все тактические работы по revised settings выполняются на новых объектах и поверхностях, без in-place переписывания уже существующих legacy-объектов до отдельного parity-sign-off и removal-gate.

**Non-goals:**

- Немедленный полный редизайн всех экранов и flows в рамках одной стратегической спеки.
- Изменение логики хранения значений, ключей, дефолтов и underlying persistence только ради перестановки элементов.
- Удаление глобального поиска по настройкам.
- Публичное экспонирование mirror-based revised host до появления нативно переработанной и визуально отличимой страницы.
- Переписывание всех user-facing текстов вне тех случаев, где переименование реально нужно новой архитектуре.
- Полный пересмотр Wear OS в первой итерации.
- Принудительное превращение всех сложных зон в единый toggle-list.
- Автоматическое доверие старым тактическим артефактам после отката предыдущей волны.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. В новую спеку нужно импортировать сильные части старой стратегической рамки, но не наследовать автоматически старую тактику.
2. Перегруженность нужно решать структурно, а не декоративно.
3. Должно быть понятно не только как переразложить текущее содержимое, но и куда класть новые настройки в будущем.
4. Спорные и неочевидные места должны быть явно названы и формально разрешены, а не замаскированы косметической группировкой.
5. Нужно сохранить реальные рабочие user flows, даже если конкретный элемент меняет форму или место.
6. Wide и tablet-like режимы должны рассматриваться как полноценная часть задачи, а не как побочный случай после phone portrait.
7. Search должен остаться сильным инструментом, но с ясным contract на раскрытие канонического места и multilingual discoverability.
8. Тактический план после этой спеки должен быть осторожным, фазовым и проверяемым по non-regression-признакам.
9. В первой волне revised settings должен развиваться как скрытая redesign-incubation, а не как публичная вторая копия legacy settings.
10. Публичное возвращение revised surface должно быть заблокировано до появления хотя бы одной нативно переработанной страницы и отдельного parity-sign-off по сохранности настроек, описаний, helper-слоя и встроенного form-функционала.
11. Пока revised surface не переэкспонирована, legacy settings остаётся единственным публичным путём и обязан сохранять полную доступность с клавиатуры, ТВ-пульта, мыши и touch.
12. Все работы по revised settings должны вестись через новые объекты, контейнеры и поверхности, чтобы legacy окно настроек оставалось рабочим на всём протяжении производства новой settings-модели.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `lite`, `photos`, `legacy`.
- **API level:** структура должна оставаться корректной на всех поддерживаемых уровнях; platform-gated блоки и системные entry-points не должны терять корректную видимость и discoverability.
- **Wear OS:** не входит в первую итерацию.
- **Производительность:** новая модель не должна требовать тяжёлой runtime-классификации дерева настроек при каждом открытии.
- **Совместимость данных:** перестройка IA не должна сама по себе менять persisted keys, values и semantics существующих preferences.
- **Постепенная миграция:** решение должно позволять фазовый rollout с mapping текущего элемента на preserved или relocated equivalent.
- **Локализация:** EN/RU/UK обязательны для названий разделов, help-текстов и discoverability-слоя.
- **Доступность:** touch, mouse, keyboard, D-pad / TV remote и screen-reader сценарии входят в hard contract.
- **Focus parity:** новая структура не должна ломать существующую модель фокуса, переходов и активации элементов без touch.
- **Search contract:** поиск должен поддерживать partial-word matching и multilingual aliases; effectively English-only модель недопустима.
- **Responsive contract:** одна и та же логическая группировка должна корректно работать в narrow portrait, wide portrait, compact landscape, wide landscape, square-like и tablet-like окнах.
- **Функциональный non-regression:** перенос или визуальная унификация элемента не могут молча убирать его user-facing behavior.
- **Surface parity:** если текущий элемент ведёт на отдельную management surface, открывает dialog или выполняет action-heavy сценарий, это поведение должно быть явно сохранено или осознанно перенесено.
- **Element-bound behavior parity:** для каждого существующего элемента settings должно быть понятно, что именно обязано сохраниться после реорганизации: toggle semantics, раскрытие секции, переход на отдельный экран, вызов диалога, запуск сервисного действия, deep-link из search, helper-tooltip, bulk-action, management-flow.
- **Theme parity:** все новые group headers, containers, states, helper affordances и search results должны одинаково корректно выглядеть в light и dark theme.
- **Row layout contract:** базовая структура строки настройки — toggle / checkbox слева, основной текст сразу за ним, helper-button справа от текста — является сохраняемым паттерном; унификация не может переставлять эти роли или убирать helper-button в overflow без явного обоснования в migration map.
- **Orientation composition parity:** portrait и landscape обязаны содержать одинаковый набор groups в одинаковом порядке. Допускается, что внутри group body относительное положение элементов отличается (paired layout на широких окнах против стека на узких), но пользователь должен находить любую настройку в обоих режимах по одной и той же mental map. Разные source-set'ы для portrait и landscape не должны расходиться по составу.
- **Top-level frame stability:** число top-level страниц settings не может расти. На узких экранах текущее число вкладок уже не помещается комфортно, поэтому добавление новых верхнеуровневых разделов запрещено; новые сущности либо встраиваются в существующие категории, либо выносятся на dedicated management surface. Переименование существующих разделов разрешено.
- **Inventory baseline:** до начала любых тактических изменений собирается полная инвентаризация существующих элементов settings в portrait и landscape по обоим source-set'ам. Инвентарь служит baseline для post-completion regression sweep и для migration map.
- **Multilingual search index:** search corpus строится по каноническому id плюс alias lists для EN, RU и UK; совпадение ищется по всему alias-набору независимо от текущей UI-локали и допускает partial-word matching; результат отображается в активной локали.
- **Hidden incubation before re-exposure:** в переходной фазе revised settings может оставаться полностью скрытой от пользовательских entry points, пока хотя бы одна top-level page не станет нативно переработанной и визуально отличимой от legacy.
- **New-object implementation isolation:** revised settings реализуется через новые объекты, контейнеры и поверхности; прямое in-place переписывание уже существующих legacy-объектов запрещено до explicit parity-sign-off и прохождения legacy removal gate.
- **Legacy removal gate:** удаление legacy settings допускается только после post-migration audit, подтверждающего parity по controls, descriptions, helper-текстам, dependent inline controls, embedded actions и local form logic.
- **Gated re-exposure parity:** после возврата revised surface в публичный доступ input parity валидируется отдельно для legacy и revised path по touch, mouse, keyboard shortcuts и D-pad / TV remote; до этого legacy path остаётся единственным публичным all-input route.
- **Description and inline-function parity:** inventory и migration map обязаны учитывать не только наличие самой настройки, но и её title, summary / helper description, dependent inline controls, embedded actions и local form behavior.

---

## 4. Контекст текущей архитектуры

Сейчас экран настроек уже устроен как составная система. На верхнем уровне пользователь видит несколько крупных разделов, внутри которых скрываются сворачиваемые группы, leaf-настройки и переходы в отдельные управляющие поверхности. Один крупный раздел сам служит контейнером для подкатегорий по типам контента, а другие одновременно несут preferences, safety-controls, maintenance-actions и service-entry points.

Поиск уже встроен в архитектуру не как декоративная надстройка, а как реальный слой навигации. Пользователь может искать настройку по ключевым словам и переходить к целевому месту. Это полезно и должно сохраниться, но одновременно показывает, что одной текущей структуры уже недостаточно для предсказуемого поиска нужных пунктов без knowledge of internals.

Внутри текущей поверхности уже существуют не только обычные toggles и поля, но и collapsible groups, helper-affordances, dedicated manager screens и action-heavy зоны. Значит, новая модель не может исходить из упрощённого предположения, что всё settings можно привести к одному шаблону строки.

Живой baseline также показывает, что нынешняя IA уже имеет точечные несоответствия. Часть destructive behavior относится к операциям с файлами, но остаётся рядом с playback-смыслом. Отдельные подтверждения дублируются в разных ветках. Один из management-entry points живёт внутри feature-specific зоны и даже зависит от соседнего toggle для своей доступности. Search overlay уже умеет доводить пользователя до канонического control, но contract раскрытия скрытых секций неравномерен между разделами. На больших окнах общая логика остаётся той же, но визуальная локальность связанных controls местами теряется.

Предыдущая волна локальных перестановок подтвердила важный вывод: без общего placement-contract, inventory текущего поведения и аккуратной migration map даже разумные точечные изменения остаются нестабильными. Поэтому новая спека должна опираться на живой baseline, а архивную предыдущую стратегию использовать только как источник проблематики, целей и желаемых качеств результата.

Текущие элементы settings уже несут не только визуальную, но и поведенческую нагрузку. Search result сегодня не просто показывает совпадение, а закрывает overlay, переключает нужную вкладку, при необходимости раскрывает media-section и переводит фокус к target-control в SettingsActivity.kt:349 и SettingsActivity.kt:360. Это означает, что search уже встроен в navigation model и не может рассматриваться как чисто декоративная надстройка.

Часть group headers тоже уже не является простым label. Например, секция Permissions в General ведёт на отдельный full-screen management screen, а не только раскрывает локальный контейнер, что видно в GeneralSettingsFragment.kt:184 и PermissionsManagementFragment.kt:30. Аналогично, scheduled operations внутри Operations уже содержит add, log и bulk-clear actions, то есть представляет собой management surface, а не обычную секцию toggles, что видно в OperationsSettingsFragment.kt:437.

По input-модели settings уже поддерживает семантическую keyboard / remote navigation. Для SettingsActivity существует отдельный navigation manager с action-моделью для help, back, search, open current, focus move и page jump в SettingsKeyboardNavigationManager.kt. В самой activity это подключено как часть surface contract, включая переключение вкладок, открытие search overlay и фокусную навигацию в SettingsActivity.kt:43. Значит, любая унификация settings обязана считаться не только с touch-layout, но и с keyboard / D-pad semantics.

По visual system settings уже живёт внутри Material3 DayNight theme в themes.xml:3. Текущие group headers и interactive rows массово используют theme-aware surface tokens вроде colorSurfaceVariant и colorOnSurface, а layout-элементы уже помечены как clickable / focusable и часто снабжены contentDescription в fragment_settings_general.xml, fragment_settings_playback.xml и fragment_settings_destinations.xml. Поэтому новая система должна сохранять и расширять уже существующую theme-aware и focus-aware модель, а не пересобирать её заново с риском регрессий.

По скриншотам из S0125 подтверждается, что базовый паттерн collapsible groups и row-structure с toggle слева, текстом рядом и helper справа удачен, но headers слишком слабы графически и смыслово, а large-width layouts местами растягивают связанные controls слишком далеко. Это укрепляет вывод, что S0125 должна задавать не только IA-модель, но и визуально-пространственный контракт для groups, headers и responsive-body-укладки.

---

## 5. Предлагаемый подход

Главная идея этой спеки - перестать рассматривать экран настроек как длинный список пунктов и зафиксировать для него устойчивую модель поверхностей, правил размещения и правил роста. Пересмотр должен отвечать не только на вопрос «что куда переставить», но и на вопрос «почему именно туда» и «куда пойдёт следующий аналогичный элемент».

### 5.1 Основные столпы / модули

**Каноническая типология сущностей**

- Каждая существующая и будущая единица в settings классифицируется по двум главным осям: что именно она делает и какой тип взаимодействия от пользователя требует.
- Минимальные типы: долговременное preference, safety / destructive preference, permission / capability gate, service-action, dedicated management entry, info / legal / help entry, debug / expert control.
- Эта типология должна объяснять placement лучше, чем историческое происхождение пункта.

**Иерархия поверхностей**

- Новая архитектура должна различать top-level category, section inside category, contextual control, dedicated management surface и system redirect.
- Не каждая управляемая сущность обязана жить в глобальном Settings.
- Если сценарий по смыслу action-heavy, high-cardinality или живёт как отдельный manager, это должно считаться нормальной архитектурной формой, а не исключением.

**Placement-contract для текущих и будущих фич**

- Для любой текущей и новой настройки нужен короткий placement-checklist: глобальна ли она, относится ли к everyday behavior, опасна ли ошибка пользователя, требует ли она частого доступа, является ли она service-action, зависит ли от capability или от отдельного workflow.
- Ответы на этот checklist должны приводить к каноническому месту размещения.
- Это правило должно работать и для текущей инвентаризации, и для будущих фич.

**Inventory поведения, а не только списка строк**

- Инвентаризация должна фиксировать не только название и место, но и реальное user-facing behavior элемента, его description-layer и embedded form logic.
- В инвентарь входят toggles, confirmations, collapsible headers, helper-buttons, search entries, dedicated transitions, destructive affordances, service buttons и management-entry points.
- В тот же инвентарь обязаны попадать titles, summaries, helper descriptions, dependent inline controls и локальные action-affordances, потому что их потеря для пользователя эквивалентна потере самой настройки.
- Миграция не может считаться успешной, если сохранён только persisted value, но потеряно пользовательское поведение вокруг него.

**Search и deep-link как второй слой навигации**

- Search должен оставаться вторичным, но сильным слоем navigation.
- Любой search result обязан вести в каноническое место сущности, гарантировать раскрытие нужного контекста и не зависеть от удачного случая.
- Multilingual aliases и partial-word discoverability входят в обязательный contract, а не в optional polish.

**Responsive grammar вместо случайной растяжки**

- На узких окнах приоритет у короткого вертикального пути и читаемой локальной плотности.
- На широких окнах приоритет у ограничения ширины, локальной близости связанных controls и ясных group boundaries, а не у растягивания одного блока на весь canvas.
- Разные типы group bodies могут иметь разную пространственную грамматику, если логика группировки и каноническое место сущности остаются постоянными.

**Multi-input contract**

- Экран настроек рассматривается как multi-input surface.
- Любая новая компоновка обязана сохранять понятный focus order, keyboard / D-pad usability и равноправную работу search-driven navigation.
- Touch-first решения допустимы только там, где не ухудшают остальную input-модель.

**Phased migration contract**

- Перестройка не должна быть одномоментной.
- Для каждой спорной зоны нужен mapping: current placement -> target surface -> preserved behavior -> rationale -> phase.
- Тактический план должен идти от наиболее конфликтных и явно полезных зон к более тонкой унификации.

**Контракт скрытой инкубации и gated re-exposure**

- В ранних тактических фазах revised settings может существовать только как internal incubation surface без публичных entry points.
- Публичное возвращение revised host допускается только после появления хотя бы одной нативно переработанной и визуально отличимой top-level page.
- До gated re-exposure legacy settings остаётся единственным публичным all-input path и не может деградировать до touch-only fallback.
- После re-exposure revised path проходит отдельный parity-аудит по controls, descriptions, helper-layer, inline form behavior и input model.

**Контракт изоляции реализации**

- Тактическая реализация revised settings ведётся через новые объекты, контейнеры и поверхности, а не через in-place переписывание уже существующих legacy-объектов.
- Legacy settings window обязано оставаться рабочим на всём протяжении производства revised surface и сохраняет роль эталонного fallback-path до explicit parity-sign-off.
- До legacy removal gate существующие legacy-объекты могут получать только поддерживающие правки совместимости, bugfix-правки или parity-обвязку, но не становиться носителем новой IA.

**Контракт сохранения поведения существующих элементов**

- Для каждого текущего interactive element в settings фиксируется не только его placement, но и его user-facing behavior.
- В mapping inventory обязаны попадать не только toggles и fields, но и headers, helper-buttons, search entries, dialog-launchers, management screens, bulk-actions и service buttons.
- Рефакторинг или унификация могут менять форму элемента, но не могут молча убирать его смысловую и поведенческую роль.
- Если текущая роль элемента переносится на другую поверхность, это должно быть явно отражено в migration map.

**Контракт input-моделей**

- Settings рассматривается как multi-input surface, а не как touch-only экран.
- Любой новый или переработанный layout обязан сохранять работоспособность для touch, mouse, keyboard shortcuts и D-pad / TV remote.
- Группы, action rows, list items, search results и management screens должны иметь предсказуемую focus-navigation модель.
- Для элементов, которые раньше были directly activatable с клавиатуры или пульта, это свойство должно быть сохранено и после унификации.

**Контракт theme parity**

- Все design decisions для settings должны верифицироваться не только в одной теме, но и в light / dark pair.
- Group headers, collapsed / expanded states, helper buttons, search overlay, selected states и focus highlights должны сохранять достаточный контраст и читаемость в обеих темах.
- Визуальное усиление headers не может опираться только на один цветовой режим.

**Контракт responsive layouts**

- Для settings вводится не бинарное деление portrait / landscape, а набор целевых пространственных режимов: narrow portrait, wide portrait, square-like, compact landscape, wide landscape, tablet-like.
- Логическая группировка между этими режимами остаётся постоянной, но body layout внутри групп может адаптироваться.
- На малых экранах приоритет у короткого вертикального пути и локальной плотности.
- На больших экранах приоритет у ограничения ширины и локальной близости связанных controls, а не у растягивания группы на весь доступный canvas.
- Разные типы group bodies могут иметь разные responsive grammars: toggle stacks, paired rows, card grids, dedicated manager screens.

**Контракт multilingual search**

- Search corpus должен быть построен не только на одном наборе английских titles / keywords, а на каноническом id плюс alias lists для EN, RU и UK.
- Совпадение допускается по части слова.
- Search result визуально отображается в текущей UI-локали, но совпадение ищется по всему multilingual alias set.
- Section labels, result descriptions и synonyms должны быть согласованы с actual IA, а не быть отдельно живущим словарём.

### 5.2 Потоки данных и событий

- Пользователь приходит в settings либо напрямую по разделам, либо через search, либо через переход к отдельному management-сценарию.
- Новая архитектура должна сначала довести пользователя до канонической поверхности по его задаче, а уже потом раскрывать конкретную настройку или action.
- Если пользователь приходит через search, он должен попадать не во временный обходной путь, а в то же каноническое место, которое существует и для ручной навигации.
- Если сущность по смыслу относится к dedicated manager surface, глобальный settings-body должен выступать точкой входа, а не искусственным контейнером для всей логики.
- Если элемент имеет preserved behavior вроде confirmation, expand / collapse, helper-affordance или отдельного перехода, этот behavior должен сохраняться независимо от нового placement.
- Хранилище значений и общая модель применения настроек при этом остаются прежними; пересматривается способ структурировать, находить и визуально подавать эти сущности.
- Если пользователь приходит через keyboard shortcut, TV remote или search overlay, он должен попадать в ту же каноническую поверхность и тот же preserved behavior, что и при обычной touch-навигации.
- Если элемент раньше открывал dedicated screen или dialog flow, его унификация не может незаметно деградировать сценарий до простого статического row.
- Если helper-button раньше раскрывал смысл настройки, этот affordance не должен теряться из-за уплотнения layout.
- Если элемент был найден через search, система обязана довести пользователя до реального control и сохранить возможность дальнейшей навигации не только touch-ом, но и focus-based input.

### 5.3 Точки расширяемости

- Flavor-specific скрытие отдельных ветвей не должно ломать общий mental model экрана настроек.
- Новые feature areas должны не только выбирать placement, но и сразу определять search aliases, input parity, theme parity и responsive expectations.
- Новые dedicated management surfaces должны проектироваться как законный результат placement-contract, если simple inline-row для них не подходит.
- Documentation, help-слой и terminology sync должны опираться на ту же каноническую структуру, что и UI.
- Если будущая фича не проходит критерий «глобальное долговременное preference», у неё должна быть возможность не попадать в глобальный settings-host.

---

## 6. Открытые вопросы / Research items

Все стратегические вопросы первой волны закрыты ниже. Часть закрыта решением владельца, часть — best-practice анализом для последующего ревью при `/spec-tech`. Открытых блокирующих вопросов на стратегическом уровне не осталось.

### 6.1 Workflow gate (решение владельца)

**Q-1. Pre / post inventory contract.** До начала тактических работ собирается полная инвентаризация всех видимых элементов settings в portrait и landscape отдельно. После завершения работ выполняется обратная сверка: ни один элемент не пропал и не потерял своё user-facing behavior. Инвентарь служит baseline для §11 и для migration map в §12.

**Q0. Orientation composition parity.** Portrait и landscape обязаны содержать одинаковый набор groups в одинаковом порядке. Внутри group body относительное положение элементов может отличаться, но пользователь должен находить любую настройку в обоих режимах по одной mental map. Закреплено как hard constraint в §3.2.

### 6.2 Архитектурные рамки (решение владельца)

**Q1. Границы верхнего каркаса.** Сохранить существующее число top-level страниц settings и общую форму верхнего каркаса. Допускается переименование разделов и переразложение их содержимого. Запрещено увеличивать число вкладок верхнего уровня — на узких экранах текущее число уже не помещается комфортно. Новые сущности либо встраиваются в существующие категории, либо выносятся на dedicated management surface. Закреплено как hard constraint «Top-level frame stability» в §3.2.

**Q4. Multilingual search behavior.** Search ищет совпадения по полному alias-корпусу EN + RU + UK независимо от текущей UI-локали. Результат отображается в активной локали, но matching строится по всем трём языкам и допускает partial-word matching. Закреплено как hard constraint «Multilingual search index» в §3.2 и в ADR-8.

**Q7. Migration visibility.** Применяется staged hidden incubation для settings-host. Revised settings может развиваться внутренне и без публичных entry points до тех пор, пока хотя бы одна top-level page не станет нативно переработанной и визуально отличимой от legacy. После этого допускается controlled re-exposure revised path с отдельным parity-аудитом по controls, descriptions, helper affordances, inline form logic и input model. До re-exposure legacy settings остаётся единственным публичным путём. Закреплено как ADR-9.

### 6.3 Best-practice выводы (требуют sign-off на этапе `/spec-tech`)

**Q2. Приоритеты первой тактической волны.** В первую волну попадают placement-конфликты, которые ломают саму IA-логику и блокируют дальнейшую миграцию:

- Destructive controls, разбросанные по разным смысловым зонам и частично дублирующиеся, сводятся к единому safety-блоку или к owner-сущности по типу операции (file ops, playback ops, account ops).
- Management entry, живущая внутри feature-specific зоны и зависящая от соседнего toggle для своей доступности, переносится в каноническую категорию своей сущности.
- Help / legal / info как нижний хвост существующего раздела выделяется в отдельную нижнюю зону (см. Q6).
- Wide-layout stretching связанных controls регулируется content-width contract и paired-layout правилами (см. Q11).

Во вторую (отложенную) волну: тонкая унификация helper-affordances, polishing search reveal inconsistency между разделами, нормализация названий, secondary debug / expert reorganization, перенос logically стабильных групп без active conflict.

**Q3. Граница global settings vs dedicated management surfaces.** Сущность остаётся в глобальном settings-body, если она представлена одиночным persisted value (toggle, choice, текстовое поле, slider) или коротким static choice list без CRUD. Сущность выносится на dedicated management surface, если выполняется хотя бы один из критериев:

- Требуется CRUD (add / edit / delete entities).
- Существует bulk-action поверх коллекции.
- Есть собственная фильтрация, сортировка или поиск внутри коллекции.
- Нужен собственный лог, история или журнал.
- Cardinality сущности нелинейная и плохо ложится в один скролл.

В этих случаях settings-host выступает только entry point, а вся логика управления живёт на отдельной поверхности.

**Q5. Responsive acceptance set первой волны.** Обязательные для валидации режимы:

- Narrow portrait (compact width, < 600 dp): phone portrait baseline.
- Narrow landscape (compact width, sub-600 dp height): phone landscape с укороченной высотой.
- Medium / wide portrait (600 – 839 dp): tablet portrait, foldable closed.
- Expanded / tablet-like (≥ 840 dp): tablet landscape, foldable open, square-like окна для VR-companion сценариев.

Все четыре режима валидируются на light и dark theme. Square-like режим — специфический для проекта, но включён в обязательный набор из-за VR companion-сборок.

**Q6. Info / legal / help surface.** Help / legal / about-зона выделяется как отдельная каноническая нижняя группа: версия приложения, what's new, ToS, privacy policy, licenses, support / feedback entry. Группа имеет явный визуальный разделитель сверху и всегда располагается последней внутри своей категории. Это уравновешивает её с операционными preferences и устраняет восприятие как «хвоста раздела».

**Q8. Скрытая поведенческая нагрузка элементов.** Migration inventory обязан явно классифицировать и фиксировать следующие классы behavior, потому что они не выводятся из текста элемента:

- Group header, открывающий full-screen management screen (а не только collapse / expand).
- Section с inline add / log / bulk-clear actions (management surface, замаскированная под обычную секцию).
- Search result, который не просто прокручивает к строке, а закрывает overlay, переключает вкладку, раскрывает свёрнутую секцию и переводит фокус.
- Helper-button рядом со строкой, открывающий объяснение, диалог или dependent setting.
- System-level redirect (например, к настройкам разрешений ОС) внутри обычной с виду строки.
- Destructive row с required confirmation dialog.
- Row с зависимыми вложенными settings, которые меняются от parent toggle.

**Q9. Граница безопасной визуальной унификации.** Унификация до общего row template безопасна для:

- Простых toggle stacks (label + switch).
- Simple preference rows (label + current value).
- Destination rows (label + chevron, без скрытого management behavior).
- Choice rows (label + selected option).

Сохраняют special-case visual grammar:

- Management entry-rows, ведущие на отдельную поверхность, — им нужен явный affordance перехода и часто action-bar на target surface.
- Permission-rows с system-redirect и capability state.
- Destructive rows — им нужен явный visual cue (color accent или icon) и обязательная confirmation.
- Help / legal / info rows — они не являются toggle и не должны мимикрировать под него.
- Debug / expert rows — они часто несут verbose info и helper-buttons; их компрессия в общий template уничтожает discoverability.

**Q10. Visual cues у group headers.** Обязательный минимум для каждого header:

- Distinct typography (Title Medium / Small по Material 3), отличная от обычной row.
- Vertical spacing сверху и снизу header'а, отделяющий группу от соседних.
- Theme-aware surface (через surface / surfaceVariant token), а не raw color.
- Чёткая left-edge alignment группы с content внутри неё.

Опциональные усиления:

- Иконка категории, когда категорий много и иконка усиливает идентификацию.
- State indicator (chevron) для collapsible headers.
- Слабый divider или background tint для усиления locality на широких окнах.

Запрещено усиливать header только за счёт цвета, который работает в одной теме и теряет контраст в другой.

**Q11. Paired layouts на широких экранах.** Допустимы:

- Парные колонки внутри одной group body, когда элементы независимы (не обновляют друг друга, не имеют parent-child зависимости).
- Парные колонки, когда обе колонки несут логически близкий тип сущностей одной группы (две независимые toggle-секции).

Запрещены как логический разрыв:

- Разделение toggle и его helper-button по разным колонкам.
- Разделение parent setting от его dependent children.
- Разделение setting от его inline error / warning / hint state.
- Разделение элементов одной atomic group (toggle + dependent label + helper).

Дополнительно: максимальная content-width одной колонки ограничивается порядка 400 – 600 dp по Material 3 list pattern; за пределами этого предела предпочтительнее single column с боковыми полями вместо растягивания.

### 6.4 Частично закрытые по evidence

- **Input-model support:** Partially resolved. Evidence: SettingsKeyboardNavigationManager.kt, SettingsActivity.kt:43.
- **Theme parity baseline:** Partially resolved. Evidence: themes.xml:3.
- **Current header weakness and wide-layout stretch:** Partially resolved from screenshots in S0125.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Пересмотр снова сведётся к косметической перегруппировке без placement-model | Высокая | Через несколько задач settings снова станет хаотичным | Сначала утвердить surface taxonomy и placement-contract, потом раскладывать спорные зоны |
| Будет сохранён список пунктов, но потеряны descriptions, helper-тексты или встроенное поведение элементов | Высокая | Пользователь потеряет confirmations, helper flows, dependent inline controls, dedicated transitions, search-target semantics или понимание смысла настройки | Делать inventory не только поведения, но и description-layer / embedded form logic; removal gate legacy entry только после parity-audit |
| Search останется компенсатором плохой структуры | Средняя | Пользователь без точных слов всё равно не сможет уверенно находить нужное | Зафиксировать, что search вторичен, а каноническая ручная навигация обязательна |
| Wide layouts снова получат только растянутые строки и пустые зоны | Высокая | На больших окнах settings останется визуально слабым и неудобным | Ввести responsive grammar и content-width rules как часть IA, а не post-fix |
| Миграция сломает keyboard / D-pad / mouse navigation | Средняя | Не-touch сценарии ухудшатся даже при визуально хорошем результате | Включить multi-input parity в hard acceptance и tactical verification |
| Flavor-specific ветви начнут расходиться по собственной логике | Средняя | В разных сборках экран настроек будет ощущаться как разный продукт | Разрешать различия только на leaf-level и хранить единый mental model |
| Архивные тактические артефакты снова будут приняты за источник истины | Средняя | Новый plan унаследует уже опровергнутые предположения | Явно объявить live baseline единственным источником текущего состояния |
| Слишком большая первая волна повысит regression-risk | Средняя | Повторится откат и потеря доверия к инициативе | Разбить migration на осторожные фазы с чётким mapping и локальной проверкой |
| Public re-exposure revised host произойдёт слишком рано и снова покажет mirror вместо redesign | Средняя | Пользователь снова увидит ложный прогресс и потеряет доверие к инициативе | Держать revised host скрытым до появления нативной визуально отличимой страницы и использовать explicit re-exposure gate |
| Шаблонное уплотнение layout ухудшит mouse / touch usability | Средняя | Меньшие hit areas, ошибки нажатия, визуальная путаница | Разделить compactness и hit-target rules; не жертвовать affordance ради плотности |
| Усиление group headers будет работать только в одной теме | Средняя | В dark или light theme headers потеряют контраст и состояние | Ввести explicit theme parity review для light / dark pair |
| Редкие management flows будут случайно сплющены в обычные rows | Средняя | Сложные сценарии потеряют ясность и discoverability | Разрешить dedicated manager screens как first-class pattern |

---

## 8. Влияние на пользователя (docs/FEATURES)

Это стратегическая planning-спека без реализованной пользовательской функциональности. Обновление `docs/FEATURES.md` откладывается до фактической тактической реализации и зависит от того, станет ли новая модель settings заметным пользовательским изменением.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Новый цикл начинается от живого baseline, а не от архивной тактики**

- **Решение:** текущим источником истины считается только живое состояние продукта; архивная предыдущая стратегическая спека используется как источник проблематики и целей, а не как готовый tactical blueprint.
- **Альтернативы:** восстановить и доработать старую тактическую декомпозицию почти без пересмотра.
- **Почему:** прошлый tactical path уже доказал свою нестабильность и был полностью откатан.

**ADR-2: Placement определяется пользовательской задачей и типом сущности**

- **Решение:** место элемента определяется не технической близостью и не историей добавления, а пользовательским намерением, типом сущности и ожидаемой частотой обращения.
- **Альтернативы:** продолжать складывать новые пункты в ближайший существующий раздел.
- **Почему:** иначе settings продолжит разрастаться как имплементационный склад.

**ADR-3: Сохранение поведения важнее сохранения формы**

- **Решение:** migration защищает прежде всего user-facing behavior элемента, а не конкретный виджет или контейнер.
- **Альтернативы:** считать успешным перенос только по факту сохранения persisted value.
- **Почему:** часть текущих элементов уже несёт search, management, confirmation и helper semantics, которые нельзя потерять.

**ADR-4: Search является вторичным слоем, но обязан быть каноническим и multilingual**

- **Решение:** search сохраняется как shortcut и fallback, но всегда ведёт в каноническое место сущности и работает по multilingual alias corpus.
- **Альтернативы:** search-first модель или сохранение English-first discoverability.
- **Почему:** discoverability является частью IA не меньше, чем сами разделы.

**ADR-5: Dedicated management surfaces являются нормальной формой архитектуры**

- **Решение:** action-heavy и manager-like сценарии могут существовать как отдельные поверхности с entry point из settings.
- **Альтернативы:** пытаться насильно встроить всё в один scroll-body.
- **Почему:** глобальный settings-host не должен быть контейнером для всей сложной логики приложения.

**ADR-6: Responsive и multi-input contracts входят в стратегическую архитектуру**

- **Решение:** narrow / wide behavior и non-touch navigation фиксируются уже на strategic-уровне.
- **Альтернативы:** считать их поздним implementation polish.
- **Почему:** именно здесь текущие проблемы становятся пользовательски заметными и системными.

**ADR-7: Theme parity обязательна на strategic уровне**

- **Решение:** все новые visual rules для settings обязаны быть theme-safe в light и dark theme; это входит в acceptance criteria, а не в post-implementation polish.
- **Альтернативы:** решить theme compatibility на implementation-этапе по мере появления проблем.
- **Почему:** group headers, helper affordances и search overlay являются частью core readability и не должны зависеть от одной цветовой схемы; settings уже живёт в Material3 DayNight theme и использует surface tokens, значит новая система должна расширять эту модель, а не создавать точки выпадения из неё.

**ADR-8: Multilingual search является частью IA, а не бонус-функцией**

- **Решение:** поиск по настройкам проектируется сразу для EN, RU и UK с partial-word matching и canonical alias corpus; это часть discoverability contract.
- **Альтернативы:** оставить English-first index и локализовать только видимые labels.
- **Почему:** discoverability относится к информационной архитектуре не меньше, чем placement section itself; пользователь на RU / UK не должен искать английские ключевые слова для того, чтобы найти настройку.

**ADR-9: Скрытая инкубация и gated re-exposure вместо раннего public dual-run**

- **Решение:** revised settings сначала развивается как internal incubation surface без публичных entry points. Публичное возвращение допускается только после появления хотя бы одной нативно переработанной и визуально отличимой top-level page и после отдельного parity-аудита по controls, descriptions, helper-layer, inline form behavior и input model.
- **Альтернативы:** ранний public dual-run legacy и revised settings; немедленный single-cutover на новую settings-систему.
- **Почему:** главный риск владельца — не просто потеря настройки, а ложное ощущение завершённого redesign при фактическом mirror-host подходе. Скрытая инкубация убирает misleading exposure и позволяет показать revised surface только тогда, когда там уже есть реальный продуктовый сдвиг.

**ADR-10: Orientation composition parity — hard contract, не polish**

- **Решение:** portrait и landscape settings обязаны содержать одинаковый набор groups в одинаковом порядке; различия допускаются только во внутренней body-укладке группы. Это часть acceptance, а не косметика.
- **Альтернативы:** разрешить landscape source-set'у отклоняться по составу groups ради лучшего использования ширины.
- **Почему:** пользователь должен находить любую настройку по одной mental map независимо от ориентации; расхождение состава между portrait и landscape source-set'ами уже наблюдалось как источник регрессий и затрудняет migration audit.

**ADR-11: Top-level frame стабилизирован — расти только вглубь и вбок, не вверх**

- **Решение:** число top-level страниц settings фиксируется на текущем уровне; расширение происходит за счёт переразложения содержимого, переименования разделов или выноса на dedicated management surface.
- **Альтернативы:** добавлять новые верхнеуровневые вкладки при появлении больших feature-областей.
- **Почему:** на узких экранах текущее число вкладок уже не помещается комфортно, и любое расширение каркаса вверх ломает navigability на phone portrait baseline.

---

## 10. Связи с другими спеками

- **S0119:** используется как архивный источник сильной стратегической формулировки проблемы, целей и ограничений.
- **S0121, S0122, S0123, S0124:** используются только как исторический контекст и негативный пример неудачной локальной тактики; они не являются основой новой декомпозиции.
- **Других обязательных блокирующих связей нет.** Если в tactical planning будут выявлены новые зависимости, они должны быть добавлены отдельно.

---

## 11. Критерии готовности (strategic-level)

1. Утверждена каноническая типология сущностей экрана настроек.
2. Утверждена иерархия допустимых поверхностей: top-level category, section, contextual control, dedicated management surface, system redirect.
3. Для текущей user-facing поверхности существует inventory `current placement -> target surface -> preserved behavior -> rationale`.
4. Для спорных placement-зон определён приоритет первой тактической волны.
5. Зафиксирован placement-checklist для будущих новых настроек.
6. Явно проведена граница между global preferences, safety / destructive controls, service-actions, help / legal / info entry points и debug / expert зоной.
7. Зафиксирован search contract: canonical target, guaranteed reveal behavior, multilingual aliases, partial-word matching.
8. Зафиксирован responsive contract как минимум для narrow portrait, wide landscape и tablet-like / square-like режимов.
9. Зафиксирован multi-input contract для touch, mouse, keyboard и D-pad / TV remote.
10. Утверждено, какие dedicated management surfaces остаются отдельными и по каким критериям новые будут создаваться в будущем.
11. Стратегия миграции допускает фазовый rollout без изменения storage semantics и без молчаливой потери поведения элементов.
12. Спека достаточно конкретна, чтобы по ней можно было построить новый tactical plan без опоры на архивную откатанную тактику.
13. Для всех текущих interactive elements в settings существует inventory с фиксацией их текущего user-facing behavior и решением о его сохранении или контролируемом переносе.
14. Спека явно определяет, какие behaviors считаются обязательными к сохранению при унификации: expand / collapse, search deep-link, helper affordance, dedicated manager screens, dialogs, bulk actions, service buttons.
15. Новая модель settings описывает обязательную parity-проверку в light и dark theme для всех новых visual элементов.
16. Спека явно разрешает dedicated management surfaces для сложных high-cardinality и action-heavy sections вместо насильственного встраивания всего в единый toggle-list.
17. Существует полная pre-task инвентаризация всех видимых элементов settings в portrait и landscape source-set'ах; она используется как baseline для post-completion regression sweep.
18. Portrait и landscape содержат одинаковый набор groups в одинаковом порядке; различия допустимы только во внутренней body-укладке группы (см. ADR-10).
19. Число top-level settings pages не выросло; допускается только переименование существующих разделов (см. ADR-11).
20. Search index построен по EN + RU + UK alias corpus и обеспечивает совпадение независимо от текущей UI-локали с partial-word matching.
21. Help / legal / info зона выделена как отдельная каноническая нижняя группа с явным визуальным разделителем.
22. Для границы global-settings vs dedicated management surface зафиксирован формальный checklist (CRUD, bulk-action, own filter / sort / search, own log, non-linear cardinality).
23. Responsive acceptance set первой волны охватывает narrow portrait, narrow landscape, medium / wide portrait и expanded / tablet-like режимы; каждый валидируется в light и dark theme.
24. Тактическая миграция не публикует revised host, пока хотя бы одна top-level page не собрана нативно и не отличается визуально от legacy.
25. Post-migration parity audit явно проверяет controls, titles, summaries, helper descriptions, dependent inline controls, embedded actions и local form behavior.
26. После gated re-exposure input parity валидируется отдельно для legacy и revised settings surfaces по touch, mouse, keyboard и D-pad / TV remote.
27. До gated re-exposure legacy settings сохраняет статус единственного публичного non-touch path; после re-exposure revised path не может деградировать legacy settings до touch-only fallback.
28. Тактическая реализация revised settings выполняется на новых объектах и поверхностях, а не через in-place переписывание уже существующих legacy-объектов.
29. На всём протяжении производства revised settings legacy settings window остаётся рабочим и пригодным как fallback-path до отдельного parity-sign-off и прохождения removal gate.

**Что уже можно считать закрытым по evidence:**

- Keyboard / remote baseline есть в SettingsKeyboardNavigationManager.kt и SettingsActivity.kt:43.
- Search-to-target behavior уже есть в SettingsActivity.kt:349.
- Dedicated manager-surface уже есть хотя бы для permissions в GeneralSettingsFragment.kt:184 и PermissionsManagementFragment.kt:30.
- Theme baseline уже есть в themes.xml:3.
- EN / RU / UK локализация уже является базовым repo-contract через strings.xml.
- Визуальная слабость headers и проблема растяжения wide layouts подтверждены скриншотами из S0125.

---

## 12. Дополнительные задачи

### 12.1 Аудит и нормализация существующих текстов settings

- Пройти все user-facing строки экрана настроек (titles, summaries, helper-tooltips, dialog texts, error / empty states, search-overlay подписи) параллельными батчами по 30 строк за итерацию.
- Каждый батч: вычитка, проверка соответствия будущей "политике общения" (см. §12.2), правка терминологии, единообразия тона, длины и многоточий.
- Менять только видимые тексты; persisted keys и id ресурсов сохраняются как есть.
- Каждое изменение `strings.xml` проверять `scripts/check_strings_localized.ps1` для EN/RU/UK parity.
- Аудит идёт параллельно с IA-перекладкой и не блокирует её, но любой relocated элемент перепроверяется на соответствие политике в момент переноса.

### 12.2 Политика общения проекта

- Создать в каталоге документации проекта отдельный документ с каноническими правилами общения с пользователем во всех user-facing поверхностях.
- Документ обязан покрывать: тон голоса, обращение на "вы" / "ты", запреты на жаргон, правила для destructive / safety wording, формулировки confirmations, формулировки ошибок и empty states, длину titles / summaries / helper-tooltips, правила переноса терминологии между EN / RU / UK, оговорки про style-rules автора (`..` вместо `...`, обязательная ё/Ё в русском).
- Документ должен иметь зеркала EN / RU / UK или быть выпущен в формате, совместимом с уже принятой моделью документации.
- Политика является источником истины для §12.1: любые правки текстов settings должны опираться на этот документ, а не на ad-hoc решение конкретного исполнителя.
- Открытый вопрос: каноническое имя файла и его место внутри `docs/`; решается отдельно при первой реализации.

### 12.3 Внедрение политики общения в правила и скилы проекта

- Добавить ссылку на политику общения в `CLAUDE.md` как обязательный reference для любого UI / messages workflow.
- Расширить существующие skills, влияющие на пользовательский текст, требованием сверяться с политикой: как минимум `/spec`, `/spec-tech`, `/spec-dev`, `/spec-check`, `/quick`, `/doc-update`, `/ui-clarify`.
- Для каждого затронутого skill зафиксировать, на каком этапе он обязан проверять соответствие текстов политике (при написании спеки, при кодинге, при аудите, при правке доков).
- Внедрение должно идти после фиксации §12.2: правила без существующего документа недопустимы.
- Любой будущий новый skill, который генерирует или меняет user-facing текст, обязан унаследовать тот же contract.

---

## 13. Ссылка на тактическую спецификацию

Активный tactical plan восстановлен: `PLAN/S0125_settings-activity-revision/INDEX.md` + `PHASE_01__inventory-shell-foundation.md` .. `PHASE_07__docs-catalog-cleanup.md`.

Новая тактическая волна собрана с нуля по live baseline, blueprint `BLUEPRINT_2026-05-19.md` и текущему corrective состоянию revised host. Следующий шаг — review tactical plan и затем `/spec-dev S0125`.

Исторические упоминания удалённых tactical files в `Revision History` и `Last Audit` ниже сохраняются как журнал уже произошедших действий и не отменяют новый активный tactical track.

---

## Revision History

- **2026-05-19** — by `/spec-tech` (manual tactical rebuild from approved blueprint)
  - Создан новый tactical plan: `PLAN/S0125_settings-activity-revision/INDEX.md` + phases 01 - 07.
  - Strategic `Status:` переведён из `Partial` в `Tactical`.
  - `§13` обновлён: следующий шаг — review tactical plan и затем `/spec-dev S0125`.

- **2026-05-19** — manual owner reset of S0125 tactical artefacts
  - Удалены все текущие tactical-файлы S0125 из `PLAN/S0125_settings-activity-revision/` и связанные reboot / phase0-артефакты из `dev/`.
  - В стратегической спеки снята ссылка на активный tactical plan; следующая тактическая волна должна быть пересобрана с нуля.

- **2026-05-19** — manual owner clarification about new-object migration isolation
  - В стратегический контракт добавлено явное требование вести revised settings через новые объекты и поверхности, а не через in-place переписывание legacy settings.
  - Зафиксировано, что legacy окно настроек обязано оставаться рабочим на всём протяжении производства revised surface до parity-sign-off и removal gate.

- **2026-05-19** — manual redesign reboot update after public rollback
  - Из текущего стратегического контракта убрана ставка на ранний public dual-run как основной способ миграции.
  - Q7, ADR-9 и критерии готовности пересобраны под hidden incubation + gated re-exposure.
  - `§13` обновлён: live baseline теперь указывает на reboot-артефакты в `dev/`, а не на повторное public re-entry mirror-host surface.
  - `## Last Audit` синхронизирован с тем, что revised host снова убран из публичных entry points до появления нативно переработанной страницы.

- **2026-05-19** — manual owner-clarification update after Phase 08 Main re-entry
  - Уточнено, что второе окно настроек в S0125 является global settings window, а не Browse-only affordance.
  - `MainActivity` снова может экспонировать отдельный launch в `RevisedSettingsActivity`, пока legacy `SettingsActivity` остаётся сохранённым эталонным путём.
  - Browse dual-run сохраняется как resource-scoped automation shortcut, а не как единственная пользовательская точка входа в revised host.

- **2026-05-19** — manual owner clarification after tactical Phase 06 blocker review
  - Подтверждено, что для текущего S0125 публичный revised entry path должен идти через отдельную кнопку в `MainActivity`, а не через Browse managers.
  - Tactical Phase 06.3 должен ссылаться на MainActivity re-exposure и preserved legacy `SettingsActivity`, а не на Browse-only launch path.

- **2026-05-19** — by `/spec-update` (manual corrective replanning, force-locked by explicit owner request, focus: consistency, completeness)
  - Текущая реализация признана shell-prototype, а не delivery revised IA: revised host сохраняет legacy fragment content и поэтому не закрывает обещанные regroup / reorder / rename goals S0125.
  - Tactical `INDEX.md` переоткрыт corrective phases 07 – 11: scope restore, real revised General/Operations rewrite, real revised Media/Playback rewrite, Browse re-exposure, final docs / audit cleanup.
  - Historical phase docs 03 / 04 / 05 помечены как prototype milestones, а не как финальная приёмка revised surface.
  - `§13` и `## Last Audit` обновлены, чтобы следующие исполнители не трактовали hosted-legacy shell как завершённый пересмотр экрана настроек.

- **2026-05-18** — by `/spec-tech` (manual tactical planning pass)
  - Создан tactical plan: `PLAN/S0125_settings-activity-revision/INDEX.md` + phase files 01 – 06.
  - Strategic `Status:` переведён из `Approved` в `Tactical`.
  - `§13` обновлён: следующий шаг теперь review tactical plan и только затем `/spec-dev S0125`.

- **2026-05-18** — manual update по уточнениям владельца
  - Q7 и ADR-9 пересобраны из модели прямого cutover в controlled temporary dual-run через две Browse entry points.
  - §2 пополнены целями 19 – 21: parallel Browse migration, legacy removal gate, dual-entry input parity.
  - §3.1 пополнен owner-request на временное параллельное существование legacy / revised settings и запрет отключать legacy до parity-sign-off.
  - §3.2 пополнены hard constraints: `Parallel Browse migration`, `Legacy removal gate`, `Dual-entry input parity`, `Description and inline-function parity`.
  - §5.1 усилен inventory contract: теперь он защищает не только control list и behavior, но и titles, summaries, helper descriptions и embedded form logic.
  - §7 пополнен risk про затяжной dual-run; risk потери behavior расширен до description-layer и inline form behavior.
  - §11 пополнен критериями 24 – 27.
  - Status journal: `Verified` → `Approved` из-за расширения стратегического scope новыми migration-gates.

- **2026-05-18** — manual implementation hygiene update after Phase 05
  - Browse dual-run now lives on the Resource Ops command surface with distinct current and new automation settings routes.
  - `INDEX.md`, `PHASE_05__dual-run-browse-parity.md`, `PARITY_AUDIT_CHECKLIST.md`, and `BROWSE_DUAL_RUN_MATRIX.md` were reconciled to the implemented Browse behavior.
  - Public feature inventory and app catalog were refreshed, while legacy `SettingsActivity` remained explicitly in scope as the preserved fallback path.

- **2026-05-15** — by `/spec-update` (claude-sonnet-4-7, focus: completeness, structure, consistency)
  - Все §6 research items закрыты: 5 решением владельца (Q-1, Q0, Q1, Q4, Q7), 6 best-practice анализом (Q2, Q3, Q5, Q6, Q8, Q9, Q10, Q11).
  - §2 пополнены целями 16 – 18 (orientation parity, inventory contract, top-frame stability).
  - §3.2 пополнены hard constraints: «Orientation composition parity», «Top-level frame stability», «Inventory baseline», «Multilingual search index».
  - §9 пополнены ADR-9 (прямой перенос с clearer grouping), ADR-10 (orientation composition parity), ADR-11 (top-level frame стабилизирован).
  - §11 пополнен критериями 17 – 23.
  - §6 переписан в три блока: workflow gate / решения владельца / best-practice выводы.
  - Применено: 6. Proposed (DISCUSS): 0.
  - Status journal: `BlockQuestions` → `Approved` (по подтверждению владельца) после закрытия всех §6 research items. Следующий шаг — `/spec-tech S0125`.

---

## Last Audit

**Date:** 2026-05-19
**Mode:** full
**Flags:** -
**Outcome:** Partial
**Counts:** PASS 18 · WARN 2 · FAIL 1 · MANUAL 4 · EXEMPT 1

### Action items

1. **[FOLLOW-UP] [FAIL Phase 02.1]** `app_v2/src/main/res/layout-land/fragment_settings_revised_general.xml` does not exist, so the verification predicate `btnPermissionsManagement present in layout-land/fragment_settings_revised_general.xml` cannot be satisfied. Other revised pages (operations, media, playback) all ship a landscape variant; General is the only outlier. Either create the landscape mirror to match the tactical predicate, or amend Phase 02.1 to record the conscious decision that General reuses portrait in landscape via Android resource fallback. ADR-10 orientation composition parity is not violated functionally (Android falls back to portrait, so groups + order stay identical), but the tactical predicate is hard-broken. Not auto-fixable: landscape composition is a design judgment, not a mechanical edit. Owner decision needed: create the mirror or amend the predicate.
2. **[FOLLOW-UP] [WARN Phase 01.3]** `sectionId = "network_cache"` returns 0 hits in `RevisedSettingsSearchIndex.kt`. Network-cache coverage IS present via `key = "general.section_network_cache"` and `R.string.settings_section_network_cache` (lines 169..287). Other sections (`quick_sort_list`, `images`, `other`, `permissions_access`, `remote_gamepad`) use the `sectionId = "..."` form. Either rename the network-cache entry to use `sectionId =` for consistency, or relax the Phase 01.3 predicate to match the `key = "general.section_network_cache"` form. Not auto-fixable: predicate text edit belongs to `/spec-update`, search-index parameter rename is a structural decision.
3. **[FOLLOW-UP] [WARN Phase 04.2]** Predicate `MediaSettingsFragment()` returns zero hits in `RevisedMediaSectionBinder.kt` is technically violated: substring match catches `OtherMediaSettingsFragment(` at lines 13/58. The intended check (no legacy `MediaSettingsFragment` instantiation) actually passes - `OtherMediaSettingsFragment` is a different class. Tighten the predicate with a word boundary, e.g. `\bMediaSettingsFragment\(`. Not auto-fixable: predicate text edit belongs to `/spec-update`.

### Manual / on-device

- [ ] **Phase 06.3 owner sign-off** - public revised MainActivity re-exposure remains correctly blocked pending explicit owner sign-off; this is the live BlockQuestions reason and not an audit failure.
- [ ] **Phase 07** - docs / catalog / FEATURES sync is cascade-blocked by Phase 06.3 and stays Not Started until owner gate clears.
- [ ] After Phase 06.3 + 07 close, validate touch, mouse, keyboard, and D-pad / TV remote on device against legacy and re-exposed revised paths.
- [ ] Confirm functional non-regression sweep against `temp/S0125_migration_map.md` for every relocated control after gated re-exposure.

### Post-audit runtime regression (recorded outside the grep predicates)

- **2026-05-19 14:34** - Revised General page crashed on first open with `NullPointerException: Missing required view with ID: btnImportTestCredentials` (logs/current.log:659). Root cause: `RevisedGeneralSectionBinder.legacyBinding()` still calls `FragmentSettingsGeneralBinding.bind(root)` as a helper-compatibility shim, and `ensureCompatibilityViews()` covered only 5 of the 11 legacy-only ids the binding requires.
- **2026-05-19 14:42** - First hotfix: extended `ensureCompatibilityViews()` with hidden compatibility views for `containerGeneralActions`, `btnShowLog`, `btnShowSessionLog`, `containerIntegrationTests`, `btnIntegrationTests`, `btnImportTestCredentials`. Used plain `Button` type uniformly. `BUILD SUCCESSFUL`.
- **2026-05-19 14:51** - Crash returned with `ClassCastException: android.widget.Button cannot be cast to MaterialButton` at `FragmentSettingsGeneralBinding.bind()` line 960. Root cause: generated binding declares `btnShowLog` and `btnShowSessionLog` as `MaterialButton`, but the first hotfix used `android.widget.Button` for their compatibility views.
- **2026-05-19 14:58** - Second hotfix: added `ensureHiddenMaterialButton()` helper, switched `btnShowLog` + `btnShowSessionLog` to `MaterialButton`. Performed full type cross-check between `FragmentSettingsGeneralBinding` field declarations and revised XML tags - only safe upcast `containerDocLinks` (binding View vs revised ConstraintLayout) remains. `BUILD SUCCESSFUL`.
- **2026-05-19 15:02** - Parity review saved to `temp/S0125_parity_review_2026-05-19.md`. Major findings: VR category dropped on revised Media (no flavor gate), `btnResetMediaSection` / `btnResetPlaybackSection` not migrated, revised General has no landscape layout, Grid View controls (`switchGridMode` / `switchHideGridActionButtons` / `switchFileOpsOverflowMenu` / `etIconSize`) not present in revised Playback or General, `RevisedPlaybackUiManager` field-name vs id snake_case mismatch suspected (needs compile-test).
- **2026-05-19 15:30** - Parity batch landed. (a) VR card (`cardVr`/`headerVr`/`iconHelpVr`/`containerVr`) re-added to revised Media XML + landscape; `RevisedMediaSettingsFragment` now `@AndroidEntryPoint` and injects `VrMediaSectionContract`; `RevisedMediaSectionBinder` accepts the contract and attaches the VR fragment via `createFragment()`. (b) Grid View card (`headerGridView`/`containerGridView`) re-added to revised Playback XML + landscape; `RevisedPlaybackUiManager.moveChildren()` now moves the legacy cluster into the new shell container; section toggle wired in `RevisedPlaybackSectionBinder`. (c) `btnResetMediaSection` and `btnResetPlaybackSection` re-exposed with confirmation dialogs mirroring legacy reset flows. (d) `headerNetworkCache` text reference switched to `@string/settings_section_network_cache` so the UI label finally matches the section id. (e) `layout-land/fragment_settings_revised_general.xml` created as a mirror of portrait (resolves Strict Rule §12 violation). `BUILD SUCCESSFUL` in 1m 20s. `RevisedPlaybackUiManager` snake_case vs camelCase concern from the parity review (`row_link_autodownload_resource` etc.) verified as a false positive - ViewBinding auto-converts and the fields exist in the generated binding.
- **Architectural debt:** `GeneralSettings*Helper` classes still depend on `FragmentSettingsGeneralBinding`, in direct contradiction of Phase 02.2's "do not pin the fragment to legacy layout ids" intent. Grep-only audit cannot catch this. Future corrective phase should decouple helpers from the legacy binding (e.g. accept narrow per-helper interfaces) before any public re-exposure.
