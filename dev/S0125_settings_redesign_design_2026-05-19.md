# S0125 - Дизайн reboot для нового окна настроек

**Дата:** 2026-05-19
**Ветка:** DEBUG-v004
**Основание:** пользовательский rejection mirror-подхода и verified AS-IS audit

## 1. Проблема, которую решаем

Текущее окно настроек исторически выросло из множества локальных добавлений. Оно функционально насыщенное, но структура слабо объясняет себя пользователю. Чтобы найти нужный параметр, часто нужно помнить внутреннюю логику приложения или пользоваться поиском как обходным путём. Дополнительно существующая компоновка плохо масштабируется между portrait, landscape, малыми экранами и широкими окнами.

Промежуточная revised-ветка проблему не решила. Она создала второй host, но не создала вторую IA. Основной контент там остался legacy. Поэтому дальнейшая работа должна начинаться не с «допиливания второй копии», а с пересборки модели settings как продукта.

## 2. Принципы redesign

1. **Structure first.** Сначала каноническая IA, потом визуальная сборка.
2. **One truth for placement.** Для любой настройки должно быть понятно, почему она находится именно здесь.
3. **Behavior preserved, form redesigned.** Можно менять форму и placement, нельзя терять смысл и поведение.
4. **Search is a navigator, not a crutch.** Search ускоряет путь, но не компенсирует плохую структуру.
5. **Responsive by grammar, not by stretching.** Wide layouts получают новую укладку, а не просто больше пустоты.
6. **Future-proof growth.** Добавление новой настройки не должно требовать поиска случайного места в legacy XML.

## 3. Целевая UX-модель

### 3.1 Верхний shell

- Top bar: back, title, search.
- Top-level navigation: те же 4 pages, но с более сильной визуальной идентификацией active page.
- Search открывает overlay-палитру с grouped results и коротким пояснением, куда ведёт результат.

### 3.2 Внутренняя структура каждой page

Каждая top-level page собирается не как один длинный accordion-список, а как набор section cards. Каждая card содержит:

- section title;
- short summary с human-language смыслом секции;
- grouped body с одной доминирующей логикой;
- optional secondary action cluster;
- явную danger / service / route семантику, если section не является просто preferences-блоком.

### 3.3 Типы строк

Все элементы settings раскладываются по ограниченному набору row archetypes:

- **Preference row**: toggle, checkbox, dropdown, text input.
- **Action row**: разовое действие с подтверждением или без него.
- **Route row**: переход на отдельный manager/help/legal screen.
- **Info row**: статус/summary с optional refresh action.
- **Danger row**: destructive control внутри явно отделённого блока.
- **Dependent cluster**: дочерние controls, которые показываются только при включённом parent setting.

Это нужно, чтобы пользователь визуально считывал не только название, но и тип взаимодействия.

## 4. Целевая IA по страницам

### 4.1 General

General перестаёт быть «сборной солянкой всего подряд» и делится на пять смысловых зон:

- **Workspace & Interface**: язык, видимость файлов, compact density, favorites, browse presentation.
- **Storage & Data**: remember-file-list, credentials, import/export, backup/restore, logs.
- **System & Background**: sleep, sync, prefetch, cache, cleanup, network parallelism.
- **Permissions & Recovery**: явный route в permissions management и recovery-related entries.
- **About & Help**: guide, how-to, privacy, OSS, tutorial, version info.

Global reset и debug-only controls не участвуют в первой публичной redesigned-волне. Они остаются на legacy path до отдельной проработки.

### 4.2 Operations

Operations получает не длинный mixed list, а четыре ясные зоны:

- **Safety Defaults**: safe mode, confirmations, trash rules.
- **Copy / Move Rules**: enable flags, overwrite rules, auto-advance policy.
- **Destinations**: recipients limit, add route, sortable destinations list.
- **Scheduled Automation**: master toggle, permission CTA, add/log/clear/list cluster.

Scheduled automation визуально подаётся как management cluster, а не как ещё один список toggle-строк.

### 4.3 Media

Media остаётся отдельной page, но перестаёт быть слабо связанной простынёй секций. Внутри page каждая media family получает собственную card с summary и dependent cluster grammar. Для wide layouts возможна paired layout подтипа «left summary, right controls».

### 4.4 Playback

Playback группируется по сценарию использования:

- browsing/grid behavior;
- player UI;
- touch zones and hints;
- playback behavior and resume;
- controls & keybindings route.

Dangerous file-operation toggles визуально отделяются от purely playback-related controls.

## 5. Responsive contract

### 5.1 Narrow portrait

- One-column stack.
- Cards идут в фиксированном canonical order.
- Secondary help text остаётся рядом с control, не уезжает в отдельные панели.

### 5.2 Compact landscape

- Сохраняется тот же порядок секций.
- Внутри cards допускаются paired rows или denser action clusters.
- Нельзя терять section summaries и helper affordances.

### 5.3 Wide landscape / tablet-like

- Слева: section navigator / overview list текущей page.
- Справа: detail body активной секции.
- При переходе через search открывается нужная page, в overview выделяется section, справа раскрывается body и target control получает focus.

## 6. Extensibility model

Новая settings-система должна собираться из schema-led registry, а не из произвольного набора XML includes.

Минимальная единица описания будущей системы:

- page id;
- section id;
- row id;
- row type;
- title / summary / help text resource ids;
- visibility gates;
- dependency rules;
- search aliases EN/RU/UK;
- persistence binding / action callback binding;
- accessibility metadata.

Из этого одного источника должны строиться:

- section composition;
- search index;
- focus order metadata;
- parity audit mapping.

## 7. ADR

### ADR-01 - Mirror-host approach is rejected

Revised host cannot expose legacy fragments or legacy settings XML includes as its primary content.

### ADR-02 - Public rollout is gated by native distinction

Revised host may return to Main/Browse only after at least the first public tab is visually and structurally distinct from legacy.

### ADR-03 - Four top-level pages stay stable

Top-level growth is forbidden. Future expansion happens inside pages or through dedicated management surfaces.

### ADR-04 - Search registry is derived, not hand-diverged

Search aliases and destinations must come from the same schema that renders the settings rows.

## 8. First implementation wave

1. Retract fake public exposure of revised host.
2. Build reusable section-card shell primitives and schema contract.
3. Rebuild General natively as the first visibly new page.
4. Rebuild Operations on the same primitives.
5. Rebuild Media and Playback.
6. Re-enable public revised entry only after search, parity, and input validation are green.

## 9. Acceptance criteria for the reboot

- Пользователь визуально отличает revised page от legacy page без подсказок.
- В каждой page есть ясные section summaries и predictable placement.
- Search result всегда объяснимо приводит в каноническое место.
- Ни одна legacy setting не теряется по title, summary, helper, dependent control, dialog, route или action behavior.
- Добавление новой настройки не требует копировать существующий fragment/layout блок.