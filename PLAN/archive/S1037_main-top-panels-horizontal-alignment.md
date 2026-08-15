# Стратегическая спецификация: S1037 - Унифицировать горизонтальное выравнивание трёх верхних панелей главного экрана

**Ticket:** S1037
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-13
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-13
**Tactical spec:** `PLAN/S1037_main-top-panels-horizontal-alignment/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения.

**Захвачено:** 2026-07-13

**Текст:**

"/spec-draft нужно выполнить работу по унификации горизонтального положения кнопок этих трёх панелей наверху. Чтобы на всех устройствах ои были выровнены по горизонтали для портретного режима"

**Вложения:**

- скриншот-референс портретного экрана с тремя верхними полосами и видимым разъездом стартовых позиций - `PLAN/S1037_main-top-panels-horizontal-alignment/attachments/01__reference-portrait.jpg`

---

## 1. Проблема

На главном экране несколько верхних горизонтальных полос с кнопками и табами стартуют с разных X-позиций, поэтому визуальная сетка в портретном режиме "плывёт" от устройства к устройству. На референсе видно, что зелёная панель программ, оливковая панель стримов и фиолетовая панель ресурсных табов не делят общий горизонтальный якорь и воспринимаются как случайно сдвинутые друг относительно друга. Область: верхняя навигационно-командная зона главного экрана списка ресурсов.

## 2. Цели

1. В портретном режиме три верхние пользовательские панели главного экрана используют один и тот же горизонтальный стартовый якорь для первого основного интерактивного элемента.
2. Визуальное выравнивание сохраняется на узких и широких телефонах без ручной подгонки под отдельные модели.
3. Поведение переполнения, прокрутки и скрытия элементов в каждой панели остаётся функционально прежним.

**Non-goals:**

- Полный редизайн содержимого панелей или смена их порядка.
- Изменение ландшафтной раскладки, если для неё не потребуется зеркальная техническая правка по правилу парных layout.
- Пересмотр набора кнопок, цветов, иконок или логики long-press/overflow.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Результат должен читаться как аккуратная общая сетка без визуального "ступенчатого" старта строк.
- Изменение должно относиться именно к портретному режиму и не ломать существующую доступность.

### 3.2 Жёсткие ограничения

- **Flavor:** все app_v2 flavor'ы, где присутствует главный экран ресурсов.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** без дополнительной измеримой нагрузки; только layout/spacing/presentation.
- **Совместимость данных:** без миграций и без влияния на persisted state панелей.
- **Локализация:** новые строки не требуются.
- **Доступность:** touch target, D-pad/TalkBack, порядок фокуса и discoverability overflow должны сохраниться.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **UI scope:** только горизонтальное выравнивание трёх верхних полос главного экрана в portrait.

## 4. Контекст текущей архитектуры

Главный экран собирает верхнюю область из нескольких независимых полос: отдельная панель программ с собственным стартовым меню, отдельная панель стримов с широкой entry-кнопкой и отдельная полоса ресурсных табов. Каждая полоса сейчас имеет свою внутреннюю геометрию, собственные минимальные ширины и собственную стратегию распределения свободного места, поэтому общий визуальный якорь между ними не гарантирован.

Проблема не в данных или бизнес-логике, а в том, что presentation-слой описывает три горизонтальных контейнера по разным правилам. Пока у них нет общего контракта на leading inset / first-action anchor, устройство с другой шириной или плотностью может усиливать этот разъезд.

## 5. Предлагаемый подход

Ввести единый визуальный контракт для трёх верхних полос в portrait: первый смысловой интерактивный элемент каждой полосы должен стартовать от одного общего leading anchor, а внутренние декоративные или служебные элементы не должны сдвигать этот якорь относительно соседних полос. Реализация должна остаться в рамках существующих layout/presentation-owner'ов, без переноса логики между слоями.

### 5.1 Основные столпы / модули

- Общий контракт горизонтального старта для верхних полос главного экрана.
- Нормализация внутренних start-padding / min-width / служебного pre-slot у полос, где это сейчас различается.
- Сохранение существующих overflow/collapse механизмов без функциональных изменений.

### 5.2 Потоки данных и событий

- Пользователь открывает главный экран в portrait.
- Presentation-слой строит верхние полосы по общей сетке start-anchor.
- При нехватке места каждая полоса по-прежнему использует свой текущий механизм scroll/overflow/collapse, но уже без различающегося стартового сдвига.

### 5.3 Точки расширяемости

- Общий anchor должен быть задан так, чтобы будущая четвёртая верхняя полоса могла принять тот же контракт без ручного пиксель-подгона.

## 6. Открытые вопросы / Research items

1. Что именно считается общим якорем для выравнивания: левый край первого видимого icon-only control, центр первой кнопки, или start у основного контентного блока после служебной кнопки/entry? **RESOLVED (2026-07-14):** общий якорь = START первой рабочей/контентной кнопки, ПРОПУСКАЯ любой ведущий служебный/entry-контрол; не буквально первый видимый элемент. Закрывает вопрос якоря, блокировавший переход Draft -> Approved.
2. Нужно ли выравнивать только старт первой "рабочей" кнопки, или также добиваться одинакового ритма межэлементных промежутков между всеми тремя полосами? **RESOLVED (2026-07-14):** только старт первой рабочей кнопки; полная нормализация ритма межэлементных промежутков не требуется.
3. На очень узких portrait-экранах, где полоса ресурсов уходит в scrollable-режим, достаточно ли общего стартового якоря при сохранении scrollable tab strip, или владелец хочет ещё и отдельную нормализацию ширины первых табов?

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Выравнивание одной полосы за счёт minWidth сломает overflow другой | Средняя | Часть кнопок раньше времени уходит в overflow или клипится | Считать якорь отдельно от логики переполнения; валидировать на узком и широком portrait |
| Исправление только portrait XML разойдётся с парным landscape layout | Средняя | Несимметричная поддержка layout pair, регрессия при следующем редактировании | В approved/tactical фазе заранее описать парную техническую правку и проверить оба layout-набора |
| Служебная кнопка панели трактуется как "первый элемент", а владелец ожидал выравнивание контентных элементов | Средняя | Формально ровно, но визуально всё ещё "не то" | Закрыть вопрос якоря до перехода Draft -> Approved |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (визуальная полировка существующего экрана).

## 9. Архитектурные решения (ADR)

ADR нет - решение в рамках существующего presentation/layout паттерна главного экрана.

## 10. Связи с другими спеками

Связей нет.

## 11. Критерии готовности (strategic-level)

1. В portrait три верхние панели главного экрана визуально стартуют от одного горизонтального якоря.
2. На минимум одном узком и одном более широком телефоне портретный экран не показывает ступенчатого смещения между тремя полосами.
3. Overflow, collapse и выбор ресурсных табов продолжают работать как до правки.

## Last Audit

**Date:** 2026-07-15
**Mode:** strategic (compact bugfix)
**Outcome:** Verified
**Counts:** PASS 1 · WARN 0 · FAIL 0

Device-verified on emulator-5554: programs "Quick launch", streams channel strip, and resource "ALL" tab all start their first button at X=154 (56dp anchor), held identically at 800 / 1080 / 1440 px widths. `S1037:` probe removed on Verified flip.

### Manual (device) - 2026-07-15, emulator-5554 (Android 17 / SDK 37), standard-debug v2.60.7151.516, portrait

**Verdict: PASS.**

Panels programs / streams require content to render, so both toggles were enabled (Settings -> General -> Main window interface -> "Programs panel" + "Show streams panel in main window") before measurement; the programs panel then populated with the mirrored program actions (Quick launch / Camera / Camera OCR translation / Download by link) and the streams panel showed its entry control + (empty) channel strip.

Leading anchor = 56dp. At density 440 (physical 1080x2280) that is 154px. First working element X (left edge), by width:

- 1080px (393dp): programs first item "Quick launch" = 154; streams channel strip (rvStreamChannels) start = 154; resource tab "ALL" = 154. expected: all 154 | actual: all 154.
- 800px narrow: programsPanelItems start = 154; rvStreamChannels start = 154; tab "ALL" = 154. expected: all 154 | actual: all 154.
- 1440px wide: programs "Quick launch" = 154; rvStreamChannels = 154; tab "ALL" = 154. expected: all 154 | actual: all 154.

All three top panels (programs / streams / resource tabs) share the 56dp first-working-button anchor on narrow, native, and wide widths - no stepped offset between the three. The leading service control of each panel (programs three-dots menu, streams entry button) occupies the 0..154 zone and is correctly skipped, per the resolved anchor definition (§6 Q1). Overflow/collapse untouched; resource tabs ALL/Local/SMB/S-FTP present and selectable (fixedGrid tabs).

Probe tags (BlockNeedUserTest): `MainActivity: S1037: top-panels leading-anchor applied` observed in logcat.

Note (out of S1037 scope, belongs to S1049): the command bar's first button btnExit shares 154 at native width but collapses to X=0 at 800px and 1440px under width pressure (leading Space squeezed). This does not affect the three S1037 panels.

Evidence: temp/S1037/ (ui_1080_panels.xml, ui_narrow_800.xml, ui_wide_1440.xml, main_1080_four_panels.png, main_800_narrow.png, probe_logcat.log).
