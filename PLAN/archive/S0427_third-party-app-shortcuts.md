# Стратегическая спецификация: S0427 - Запуск shortcut'ов сторонних приложений с домашней поверхности

**Ticket:** S0427
**Status:** Archived
**Priority:** 40
**Tactical plan:** `PLAN/S0427_third-party-app-shortcuts/INDEX.md`
**Date:** 2026-06-15
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - выделено из research S0404 (§6 item 8) 2026-06-15

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Draft - до апрува допускаются черновые формулировки.

---

## 0. Происхождение

- Запрос: с домашней поверхности лаунчера (S0404) запускать сторонние приложения (Google/Samsung и др.) не только по иконке, но и по их быстрым действиям - app-shortcut'ам.
- Решено вынести из S0404 в отдельный независимый спек: это надстройка поверх реестра приложений и роли Home, не нужная для базовой роли лаунчера.
- Research-вход: `PLAN/S0404_android-launcher-mode-profiles/research/08__third-party-app-shortcuts.md`.

---

## 1. Проблема

Реестр приложений S0404 (Phase 03) умеет показать иконку и запустить главную активность стороннего приложения. Но настоящий лаунчер показывает ещё и быстрые действия приложения (долгое нажатие на иконку): «проложить маршрут домой», «новая вкладка инкогнито» и т.п. Сейчас этих published-shortcut'ов на домашней поверхности нет - пользователь видит только сам запуск приложения.

---

## 2. Цели

1. На домашней поверхности (минимум - для «избранных приложений профиля») показывать опубликованные приложением app-shortcut'ы (manifest/dynamic/pinned) рядом с запуском самого приложения.
2. Запуск выбранного shortcut'а штатным механизмом лаунчера.
3. Корректная деградация там, где способность недоступна (нет роли дефолтного лаунчера, API < 25, приложение без shortcut'ов) - без падений, с откатом на запуск главной активности.

**Non-goals:**

- Приём и хранение pin-запросов от сторонних приложений (`requestPinShortcut`) - расширяемость, не старт.
- Редактирование/создание shortcut'ов за стороннее приложение.
- Мульти-профиль пользователя устройства (рабочие профили) - расширяемость, как и в реестре приложений.

---

## 3. Пожелания и ограничения (черновые, до апрува)

- **Зависимость от роли Home:** `LauncherApps.getShortcuts`/`startShortcut` доступны только текущему дефолтному лаунчеру; способность строго гейтится условием «приложение - дефолтный лаунчер» (роль предоставляет S0404).
- **Без чувствительных разрешений:** не требует `QUERY_ALL_PACKAGES` и restricted-прав - Play-совместимо на всех целевых флейворах с ролью Home; флейвор-сплит не нужен.
- **API level:** app-shortcuts с API 25. `standard`/`photos` (minSdk 26) покрыты; `legacy` на API 23-24 - секция shortcut'ов скрыта, остаётся запуск главной активности.
- **Производительность:** запрос shortcut'ов ленивый (по раскрытию у плитки приложения), не на каждый возврат на Home (требование производительности S0404).
- **Локализация:** EN/RU/UK - подписи секции, пустые состояния.
- **Доступность:** список shortcut'ов и их запуск работают с D-pad/пульта/клавиатуры/мыши (контракт ввода S0404).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0404 (эпик лаунчера, `Archived` - блокировки больше нет); S1103 (Part 2 «third-party app-shortcuts» отложена сюда); S0426, S0429 (сёстры, выделенные из того же research); S1090 (long-press по пустому столу → edit-режим, не пересекается с long-press по плитке).
- **Подача в UI (владелец, 2026-07-24):** идиома стокового лаунчера - долгое нажатие по плитке приложения раскрывает popup с быстрыми действиями. Плоского постоянного под-списка нет.
- **Объём v1:** плитки рабочего стола с командой `app:<pkg>` и сетка «Все приложения» в Start-меню - обе точки запуска стороннего приложения, у обеих long-press сегодня свободен. Иконки taskbar вне v1: их долгое нажатие резервируется под pin/unpin.
- **Кап и порядок:** показывать не более 5 действий, порядок - как вернул API (manifest → dynamic); обрезка молча, без «показать ещё».
- **Деградация (владелец согласовал research §8):** нет роли Home, API < 25 или у приложения нет shortcut'ов → popup не появляется вовсе, обычный тап продолжает запускать приложение.
- **Флейворы:** без сплита - способность живёт в `src/launcherEnabled/`, гейтится ролью Home и `SDK_INT >= 25`; `legacy` на API 23-24 просто не показывает popup.
- **Greenlight:** владелец дал go на тактику и реализацию 2026-07-24 («разблокировать + выполнить»).

---

## 4. Открытые вопросы / Research items

1. **Место и подача shortcut'ов в UI домашней поверхности** - [RESOLVED-BY-OWNER 2026-07-24]
   - **Вопрос:** где показывать shortcut'ы приложения - раскрытие по долгому нажатию (как стоковый лаунчер) или плоский под-список под плиткой; сколько показывать.
   - **Решение (владелец, 2026-07-24):** идиома стокового лаунчера - долгое нажатие по плитке приложения раскрывает всплывающий список его быстрых действий; постоянного плоского под-списка нет. Кап показа ~4-5 (предел publisher'а `getMaxShortcutCountPerActivity()`), порядок manifest → dynamic как вернул API. Запрос ленивый - только на раскрытие.
   - **Статус:** Resolved. Долгое нажатие по покоящейся плитке сегодня свободно (в edit-режиме его перехватывает edit-scrim S1096, там раскрытие не показывается).

2. **Набор флагов запроса на старте** - [RESOLVED-BY-RESEARCH 2026-06-23]
   - **Решение:** `FLAG_MATCH_MANIFEST | FLAG_MATCH_DYNAMIC`. `FLAG_MATCH_PINNED` НЕ включать: для обычного лаунчера он возвращает только shortcut'ы, закреплённые САМИМ нашим лаунчером (`pinShortcuts`), а приём pin-запросов в S0427 - non-goal → набор пуст и бесполезен в v1. `FLAG_MATCH_PINNED_BY_ANY_LAUNCHER` - assistant-only, для лаунчера игнорируется. Опционально - стартовый дешёвый зонд `FLAG_GET_KEY_FIELDS_ONLY` («есть ли shortcut'ы?») перед полным запросом полей.
   - **Статус:** Resolved. Pinned становится релевантным только если позже добавить отложенный `requestPinShortcut`.

---

## 5. Связи с другими спеками

- **S0404** (launcher-mode) - эпик `Archived` (2026-07-18). Блокировки больше нет: поверхность, которой ждал S0427, существует в коде - source set `app_v2/src/launcherEnabled/`, `LauncherCell` (kind `SHORTCUT`/`GADGET`), `LauncherCellCommand.App`, `LauncherCellViewBinder`, `LauncherHomeViewModel.run(..)`.
- **S1103** (cell actions) - явно отложил сюда третью часть своего объёма («third-party app-shortcuts out of scope here - deferred to S0427»). Закрытие S0427 снимает эту дыру.
- Research-вход: `PLAN/S0404_android-launcher-mode-profiles/research/08__third-party-app-shortcuts.md`.

---

## 6. Критерии готовности (strategic-level)

1. Когда приложение - дефолтный лаунчер и API ≥ 25, на домашней поверхности у приложения с published-shortcut'ами видны его быстрые действия.
2. Запуск выбранного shortcut'а открывает соответствующее действие стороннего приложения.
3. При отсутствии роли/при API < 25/у приложения без shortcut'ов поведение деградирует на запуск главной активности без падений.
4. Способность не вводит чувствительных разрешений и не ломает Play-сборку.

---

## 7. Следующий шаг

- Owner-апрув объёма получен 2026-07-24 («разблокировать + выполнить»); оба research-вопроса закрыты (§4.1 и §4.2).
- `/spec-tech S0427` - тактический план (контракт shortcut-провайдера поверх реестра приложений, гейт `hasShortcutHostPermission()`/is-default-launcher, popup по long-press, деградация по API/роли).

---

## 8. Research-итоги (2026-06-23)

Источник: research-агент, сверка с developer.android.com на 2026-06-23. Research-вход - `PLAN/S0404_android-launcher-mode-profiles/research/08__third-party-app-shortcuts.md`.

- **API/precondition:** `LauncherApps.getShortcuts(ShortcutQuery, UserHandle)` и `startShortcut(..)` - с API 25; `ShortcutQuery` - API 25. Доступ строго гейтится `LauncherApps.hasShortcutHostPermission()` (true только у текущего дефолтного лаунчера); иначе - `SecurityException`. Сквозных breaking-изменений до Android 14/15 (API 34/35) для launcher-стороны нет. <https://developer.android.com/reference/android/content/pm/LauncherApps>
- **Флаги:** `FLAG_MATCH_MANIFEST` (8), `FLAG_MATCH_DYNAMIC` (1), `FLAG_MATCH_PINNED` (2, для обычного лаунчера = СВОИ закреплённые), `FLAG_MATCH_CACHED` (API 30), `FLAG_GET_KEY_FIELDS_ONLY`. Решение v1 - manifest+dynamic (см. §4.2). <https://developer.android.com/reference/android/content/pm/LauncherApps.ShortcutQuery>
- **Разрешения/Play:** ни `QUERY_ALL_PACKAGES`, ни sensitive-прав не нужно - shortcut-host-permission даётся неявно ролью Home. `LauncherApps` системно-медиирован: его результаты НЕ фильтруются `<queries>` package-visibility (Android 11+), отдельный `<queries>`-блок для S0427 не нужен. Play-риска сверх того, что несёт S0404, нет. <https://developer.android.com/training/package-visibility>
- **Деградация:** (a) не дефолт-лаунчер → `SecurityException` (проверять `hasShortcutHostPermission()` ДО запроса) → секция скрыта; (b) API < 25 (`legacy` 23-24) → гард `SDK_INT >= 25`, секция скрыта; (c) приложение без shortcut'ов → пустой список. Fallback везде - `LauncherApps.startMainActivity(..)` (с API 21, покрывает все флейворы).
- **Производительность:** документированного throttle на launcher-стороне `getShortcuts` нет (rate-limit только publisher-side). Это IPC + декод иконок → off-main-thread, лениво по раскрытию плитки (требование производительности S0404).
- **Ловушки:** `FLAG_MATCH_PINNED` - ложный друг (в v1 пуст); роль Home отзываема в рантайме → оборачивать `getShortcuts`/`startShortcut` в try/catch `SecurityException` и пере-проверять `hasShortcutHostPermission()`; «Obsolete»-метки на `FlagMatch*` в Microsoft Learn - артефакт .NET-биндинга, не Android-deprecation.
