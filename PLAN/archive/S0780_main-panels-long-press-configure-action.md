# Стратегическая спецификация: S0780 - Пункт "Настроить" в long-press меню главных панелей

**Ticket:** S0780
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29
**Tactical spec:** реализовано напрямую (Tier 3, без отдельной тактической папки)

---

## 1. Проблема

На главном окне у панели программ и у кнопки трансляции есть контекстное меню по долгому нажатию ("Открыть", опц. "Открыть в новом окне", "Удалить"/"Отключить"). Из него нельзя быстро перейти к настройкам соответствующей программы/сценария или трансляции - пользователь вынужден вручную искать нужную группу в Настройках. Затронуто: главное окно (`ui/main`) и экран настроек (`ui/settings`).

## 2. Цели

1. В per-item меню панели программ добавлен пункт "Настроить" между "Открыть" и "Удалить".
2. В меню кнопки трансляции добавлен пункт "Настроить" перед "Отключить".
3. Тап "Настроить" открывает Настройки сразу на нужной группе с раскрытой секцией, проскролленной в видимую область.

**Non-goals:**

- Per-channel меню закреплённых каналов трансляции (owner упомянул только кнопку трансляции).
- Overflow-элементы панели программ (у них и сейчас нет per-item меню - отдельная находка, parked).
- Подсветка/scroll к конкретной строке внутри группы (раскрываем и скроллим к заголовку группы).

## 3. Пожелания и ограничения

### 3.2 Жёсткие ограничения

- **Flavor:** панель программ - все флейворы; "Настроить" трансляции - только там, где `SUPPORT_STREAMS=true` (standard, legacy, noLegal, vr). На lite/photos панель трансляции отсутствует компайл-тайм.
- **API level:** без API-специфики (PopupMenu, Intent extras, ViewPager2 - от API 23).
- **Wear OS:** не затрагивается.
- **Локализация:** EN/RU/UK - добавлен ключ `action_configure`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0755/S0770 (панель программ + per-item меню), S0756/S0779 (панель трансляции), S0353 (прецедент deep-link в секцию настроек). Зависимостей-блокеров нет.

## 4. Контекст текущей архитектуры

Меню панелей строит общий `PanelItemContextMenu` (PopupMenu) из списка `Action(@StringRes, () -> Unit)`; хосты - `MainProgramsPanelManager` и `MainStreamsPanelManager`, колбэки приходят из `MainActivity`. Настройки - `SettingsActivity` (ViewPager2, 4 таба) с коллапс-секциями (`CollapsibleSectionsManager`). Прямой переход в группу уже существовал только для Media-таба (`MediaSettingsFragment.ensureSectionExpanded`) и для Scheduled (`EXTRA_OPEN_SCHEDULED` + `checkAndExpandFromIntent`); общего механизма не было.

## 5. Реализованный подход

### 5.1 Основные изменения

- Новая строка `action_configure` (EN/RU/UK).
- `SettingsActivity`: extra `EXTRA_EXPAND_SECTION` + константы табов/секций + фабрики `openStreamsSectionIntent`/`openProgramsSectionIntent` (зеркало `GameLaunchIntents`).
- Каждый таб-фрагмент сам раскрывает свою секцию из интента в `onViewCreated` (`checkAndExpandSectionFromIntent`, с `removeExtra` - зеркало `checkAndExpandFromIntent`). Добавлен `OperationsSettingsFragment.ensureSectionExpanded` (его не было).
- В меню добавлен пункт "Настроить": в `MainProgramsPanelManager` (новый колбэк `onConfigure`) и `MainStreamsPanelManager` (новое поле `StreamsPanelMenuActions.onConfigureStreams`).

### 5.2 Потоки данных и событий

- Программы: long-press элемента -> "Настроить" -> `MainActivity` -> `SettingsActivity.openProgramsSectionIntent` -> Management tab -> `OperationsSettingsFragment` раскрывает `operations__additional_programs` и скроллит к заголовку.
- Трансляция: long-press кнопки -> "Настроить" -> `openStreamsSectionIntent` -> Media tab -> `MediaSettingsFragment` раскрывает `streams` и скроллит.

### 5.3 Точки расширяемости

`EXTRA_EXPAND_SECTION` - generic deep-link: любой таб-фрагмент может зарегистрировать свои секции в `ensureSectionExpanded`/`checkAndExpandSectionFromIntent` и стать целью.

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

## 7. Риски

- `EXTRA_HIGHLIGHT_SETTING` объявлен, но не читается (мёртвый deep-link). Не используется в S0780 - запаркован отдельным findings.
- `MainActivity` у потолка LOC (~1514); добавлены только две строки-колбэка, без новых полей-менеджеров.

## 8. Влияние на пользователя (docs/FEATURES)

Новая возможность: из меню панели программ и панели трансляции на главном окне можно сразу открыть соответствующие настройки. Capability записан в `docs/ALL_FEATURES.jsonl`.

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (deep-link через Intent extra + self-expand во фрагменте).

## 10. Связи с другими спеками

- Расширяет S0770 (per-item меню панелей) и S0779 (меню кнопки трансляции).
- Переиспользует паттерн S0353 (`EXTRA_OPEN_SCHEDULED`).

## 11. Критерии готовности (strategic-level)

1. Пункт "Настроить" виден в обоих меню в нужной позиции.
2. Тап открывает Настройки на правильном табе с раскрытой и видимой целевой секцией.
3. EN/RU/UK строки присутствуют, паритет проходит.
4. Сборка standard `fc` зелёная.
