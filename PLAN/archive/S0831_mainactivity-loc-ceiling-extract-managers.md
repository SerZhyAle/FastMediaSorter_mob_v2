# Стратегическая спецификация: S0831 - MainActivity превышает лимит 1500 LOC

**Ticket:** S0831
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-01
**Tactical spec:** реализовано inline (Simple-путь `/spec-all`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-01

**Захвачено во время:** S0782

**Текст:**

MainActivity.kt exceeds the 1500 LOC ceiling (currently 1531 LOC), violating CLAUDE.md Rule 2 / Rule 10.2. It accumulates main-window feature wiring and per-panel helper methods (e.g. disableStreamsFromPanel, hideStreamsPanelFromPanel, confirmRemoveChannel, confirmRemoveProgram). Extract cohesive groups into ui/main/helpers/*Manager.kt (per S0777 guidance: fold main-window feature wiring into a Main*Manager, not new MainActivity fields). Out-of-scope finding surfaced while implementing S0782.

---

## 1. Проблема

`MainActivity.kt` разросся до 1536 LOC и нарушал лимит 1500 LOC (CLAUDE.md Rule 2 / Rule 10.2). Лишний вес - когезивная группа обработчиков контекстных меню элементов панелей главного окна (программы S0755 / стримы S0756, находки S0770) вместе с примитивами запуска в отдельном окне. Для пользователя эффекта нет, но файл стал труднее сопровождать и блокировал дальнейшее наращивание фич главного окна.

## 2. Цели

1. Вернуть `MainActivity.kt` под лимит 1500 LOC без изменения поведения.
2. Вынести обработчики меню элементов панелей (new-window launch, Remove/Disable-подтверждения) в отдельный `Main*Manager` по гайдлайну S0777 (сворачивать в менеджер, а не плодить поля Activity).

**Non-goals:**

- Изменение поведения панелей, меню или диалогов подтверждения.
- Извлечение других групп `MainActivity` сверх необходимого для прохода под лимит.

## 3. Пожелания и ограничения

### 3.2 Жёсткие ограничения

- **Flavor:** все (код в `src/main`, без flavor-специфики).
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Совместимость данных:** без изменений.
- **Локализация:** без изменений (строки не трогались).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0777, S0782

## 4. Контекст текущей архитектуры

Слой UI: `MainActivity` - хост главного окна. Обработчики меню элементов панелей жили прямо в Activity как приватные методы, ссылаясь на изменяемый снапшот настроек (`latestSettings`) и инжектированные зависимости хоста. Уже существует богатый набор `ui/main/helpers/*Manager.kt`; недоставало дома именно для действий контекстных меню элементов панелей.

## 5. Предлагаемый подход

Извлечь блок «S0770 per-item context menus» в новый `MainPanelItemActionsManager` (`ui/main/helpers/`). Менеджер получает host-Activity, `SettingsRepository`, `UnpinStreamSourceUseCase` и провайдер `() -> AppSettings?` (чтение свежего снапшота настроек хоста). `MainActivity` конструирует менеджер в `setupViews()` до координатора меню и делегирует все call-site'ы. Поведение сохраняется дословно.

Перенесённые методы: `openResourceInNewWindow`, `isNewWindowAvailable`, `launchInNewWindow`, `confirmRemoveProgram`, `disableStreamsFromPanel`, `hideStreamsPanelFromPanel`, `confirmRemoveChannel`. Тонкие обёртки `programNewWindowActionFor` / `programRemoveActionFor` устранены - инлайн-делегация к `programsMenuCoordinator`.

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

## 7. Риски

- Неверная перепривязка call-site панельного меню - Низкая - действие меню перестаёт работать - компиляция + ручной smoke-тест панелей.

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (внутренний рефакторинг).

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшемуся паттерну `Main*Manager` (S0777).

## 10. Связи с другими спеками

- S0777 (гайдлайн: сворачивать фичи-обвязку главного окна в `Main*Manager`).
- S0782 (тикет, при реализации которого находка всплыла).

## 11. Критерии готовности (strategic-level)

1. `MainActivity.kt` < 1500 LOC.
2. Проект компилируется (`compileStandardDebugKotlin`).
3. Действия меню элементов панелей (программы/стримы: Remove/Disable/Hide, open-in-new-window; ресурс: open-in-new-window) работают как прежде.

---

## Реализация (2026-07-01, Simple-путь `/spec-all`)

- Новый `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainPanelItemActionsManager.kt` - перенос блока S0770 дословно.
- `MainActivity.kt`: 1536 -> 1467 LOC. Поле `panelItemActions`, конструирование в `setupViews()`, делегация всех call-site'ов, удаление обёрток и мёртвых импортов (`StreamSourceEntity`, `BrowseActivity`).
- Компиляция: `compileStandardDebugKotlin` - BUILD SUCCESSFUL.
- Остаётся ручной smoke-тест на устройстве (критерий 3) - запаркован как `BlockNeedUserTest`.
