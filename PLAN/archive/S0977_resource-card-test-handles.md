# Спецификация (compact): S0977 - Стабильные E2E-хендлы на overflow-кнопках карточек ресурсов

**Ticket:** S0977
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-08
**Tier:** 2 - Easy (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-09 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-08

**Захвачено во время:** разработки Maestro-регрессионного набора (расширение S0551).

**Текст:**

Add stable E2E test handles to resource-card overflow buttons (btnMoreActions) so automated UI tests can target a specific resource's actions in the multi-column grid. Symptom/evidence: Maestro resource_lifecycle flow cannot reliably delete a specific resource because the main-screen resource list is a multi-column grid that always contains the app's default virtual resources (Recent Media, All Music, All Video, Camera Photos, All Images, Downloads) plus the test resource, so btnMoreActions is never unique and relative selectors (below/rightOf a resource name) hit the wrong card's overflow. The delete mechanism itself works (overflow menu -> "Удалить" -> confirm dialog android:id/button1 removes the resource); only per-card targeting is unreliable. Proposed: give each card's btnMoreActions a stable, unique handle carrying the resource name (e.g. contentDescription = "more_options:<resourceName>" or a testTag) in item_resource_grid.xml / item_resource.xml bind, and update maestro/features/resource/resource_lifecycle.yaml to tap by that handle. Unblocks the 9th flow (currently 8/9 green) and any future per-resource E2E targeting. Scope: app_v2 ResourceAdapter bind + two item layouts + the Maestro flow.

---

## 1. Цель

Дать каждой overflow-кнопке (`btnMoreActions`) карточки ресурса стабильный уникальный хендл, несущий имя ресурса, чтобы Maestro (и любой future E2E) мог адресовать overflow конкретной карточки в многоколоночном гриде. Сейчас `btnMoreActions` неотличимы между карточками, относительные селекторы (`below`/`rightOf` имени) промахиваются, и флоу `resource_lifecycle` (9-й из 9) не может удалить именно созданный тестовый ресурс. Реализуется через динамический `contentDescription = "more_options:<resourceName>"` в обоих ViewHolder'ах адаптера - Maestro сопоставляет `text:` с contentDescription, так что хендл сразу таргетируем без правки XML.

**Non-goals:**

- Менять сам механизм удаления или overflow-меню (работает).
- Трогать inline-режим действий (`layoutInlineActions`) - у него отдельные именованные кнопки, коллизии нет.
- Пользовательская доступность: хендл технический, TalkBack-строка `@string/more_options` для скрытого состояния не нужна (кнопка при показе всегда переустанавливает contentDescription).

---

## 2. Ограничения

- **Flavor:** все (правка в `src/main`, поведение общее).
- **API level:** без API-специфики.
- **Локализация:** не требуется - хендл - технический ASCII-префикс `more_options:` + имя ресурса (имя вводит пользователь, не переводится).
- **Совместимость данных:** нет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0551 (библиотека Maestro-флоу - прямой потребитель).

---

## 4. Фазы

### Фаза 1 - Динамический contentDescription в ResourceAdapter

- **Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
- **Шаг 1.1:** В `GridViewHolder.bind` в ветке `overflowModeEnabled` (после `btnMoreActions.visibility = View.VISIBLE`, ~строка 443) добавить:
  `btnMoreActions.contentDescription = "more_options:${resource.name}"`
- **Шаг 1.2:** В `ResourceViewHolder.bind` в ветке `else` overflow (после `btnMoreActions.visibility = View.VISIBLE`, ~строка 797) добавить ту же строку.
- **Инвариант:** хендл ставится ровно когда кнопка `VISIBLE`; для скрытой кнопки (`GONE`, Favorites/inline) не трогаем - Maestro скрытые view не матчит.
- **Verification:** `.\a.ps1 fk` компилируется (exit 0); Grep обоих ViewHolder'ов показывает установку `more_options:` рядом с `VISIBLE`.

### Фаза 2 - Переключить resource_lifecycle на стабильный хендл

- **Файл:** `maestro/features/resource/resource_lifecycle.yaml`
- **Шаг 2.1:** Заменить delete-tap `id: btnMoreActions` + `below: text ZzLifecycleTmp` на `tapOn: text: "more_options:ZzLifecycleTmp"`.
- **Шаг 2.2:** В idempotent-ветке проверки overflow (`when: notVisible id btnMoreActions`) оставить как есть - `id` там нужен как «включён ли режим», не как таргетинг конкретной карточки.
- **Шаг 2.3:** Удалить блок `KNOWN LIMITATION` из шапки флоу (ограничение снято); заменить на одну строку про адресацию по `more_options:<name>`.
- **Verification:** флоу проходит на устройстве через `maestro/run-tests.ps1` (single, без retry) - `ZzLifecycleTmp` исчезает, `assertNotVisible` зелёный.

---

## 5. Критерии готовности

1. Оба ViewHolder'а адаптера ставят `contentDescription = "more_options:${resource.name}"` при видимой overflow-кнопке.
2. `resource_lifecycle.yaml` таргетит overflow по `text: "more_options:ZzLifecycleTmp"`, без относительных селекторов.
3. Флоу проходит одиночным прогоном на устройстве (create + rename + delete + gone-oracle).
4. `KNOWN LIMITATION` из флоу удалён; 9/9 Maestro-флоу зелёные.
5. Standard debug собирается.

---

## Last Audit

**Дата:** 2026-07-09
**Статус:** Verified
**Сборка:** standard debug v2.60.7041.926 (BUILD SUCCESSFUL); установлена на emulator-5554 (API 27).

**Реализовано:**

- `app_v2/.../ui/main/ResourceAdapter.kt` - в `GridViewHolder.bind` и `ResourceViewHolder.bind`, в ветках показа overflow-кнопки, добавлен `btnMoreActions.contentDescription = "more_options:${resource.name}"` (маркер `// S0977`). Хендл ставится только когда кнопка `VISIBLE`; Maestro сопоставляет его через `text:`.
- `maestro/features/resource/resource_lifecycle.yaml` - delete-шаг таргетит overflow нужной карточки по `text: "more_options:ZzLifecycleTmp"` вместо относительного `below`. Блок `KNOWN LIMITATION` удалён. Меню из 12 пунктов выше доступного места у якоря, поэтому «Удалить» (последний пункт) достаётся коротким swipe внутри области popup (scrollUntilVisible прокручивал бы фон-грид и закрывал меню).

**Проверка (on-device, одиночный прогон maestro, без retry-полировки):**

- create «Download» -> rename в «ZzLifecycleTmp» -> overflow по хендлу -> popup именно этой карточки -> swipe -> «Удалить» -> подтверждение (`android:id/button1`) -> `ZzLifecycleTmp` исчез. Все команды COMPLETED, crash-guard чист. Экран-дамп подтвердил, что открывается popup нужного ресурса.
- Гейты post-change: assert-neuroslop PASS, detekt [scoped] PASS (0 findings в изменённом файле), listener-symmetry PASS, deprecated-pm PASS.

**Остаточные заметки:**

- `swipe` использует процентные координаты области popup - это жест, не селектор по координатам (oracle-конвенция запрещает именно координатные *селекторы*); задокументировано комментарием во флоу.
- Флоу по-прежнему исключён из `-Suite all` - причина не в таргетинге (снято), а в требовании All-Files access для create на API 30+; запускается через `-Suite features\resource`.

**Критерии готовности:** 1-5 выполнены.
