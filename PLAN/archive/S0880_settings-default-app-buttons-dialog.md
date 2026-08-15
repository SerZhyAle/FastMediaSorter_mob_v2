# S0880 - Вынести кнопки «задать по умолчанию» в отдельный диалог

**Ticket:** S0880
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-02

<!-- auto-approved by /spec-all - 2026-07-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

Настройки - Управление - Взаимодействие с операционной системой. Группа «Кнопки для задания программой по-умолчанию» (вся группа) - вынести в отдельный диалог отсюда. А здесь сделать одну кнопку «Задать по-умолчанию» для вызова этого диалога.

---

## Goal

В настройках (Операции -> секция «System apps»/взаимодействие с ОС) подгруппа кнопок «сделать приложение проигрывателем по умолчанию» (`layoutDefaultPlayerSubgroup`, кнопки по типам медиа) выносится в **отдельный диалог**. На месте подгруппы остаётся одна кнопка «Задать по-умолчанию», открывающая этот диалог. Логика назначения по умолчанию (`DefaultPlayerManager`/`DefaultPlayerSettingsManager`) переиспользуется в диалоге без дублирования.

Проблема: подгруппа default-player кнопок занимает много места в списке настроек и захламляет секцию взаимодействия с ОС; её удобнее спрятать за одной кнопкой-входом.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0405 (system-apps settings group), S0435 (default-player subgroup + gating), S0538 (dialog button taxonomy)
- **UI-решение:** новый `DialogFragment` (или `Dialog`) хостит перенесённую подгруппу default-player кнопок; в `fragment_settings_destinations.xml` подгруппа заменяется одной кнопкой-входом `btnOpenDefaultAppsDialog` («Задать по-умолчанию»). Диалог соблюдает button-taxonomy (S0538): close/cancel = именованный стиль; действия по типам медиа - существующие кнопки. Гейтинг `supportsDefaultPlayer` / per-type сохраняется внутри диалога.
- **Data-решение:** без новой настройки; переиспользуется существующая логика назначения по умолчанию.

**Non-goals:**

- Изменение самой логики назначения по умолчанию (только перенос UI).
- Другие подгруппы секции System apps (screen-gesture overlay и т.п.) - не трогаются.

---

## Фазы

### Фаза 1 - Диалог (UI)

1. Новый layout `dialog_default_apps.xml` (+ `layout-land/` при необходимости) с перенесённой разметкой подгруппы default-player кнопок из `fragment_settings_destinations.xml` (`layoutDefaultPlayerSubgroup`, строка ~887). Row/button ids сохранить для settings-search continuity, где применимо.
2. Новый `DefaultAppsDialogFragment` (`ui/settings/` или `ui/dialog/`): инфлейтит layout, переиспользует `DefaultPlayerManager`/`DefaultPlayerSettingsManager` для назначения по умолчанию, соблюдает button-taxonomy S0538.

### Фаза 2 - Настройки: кнопка-вход

1. В `fragment_settings_destinations.xml` (+ land): подгруппу `layoutDefaultPlayerSubgroup` заменить одной кнопкой `btnOpenDefaultAppsDialog` (стиль settings-кнопки), видимость по тому же `supportsDefaultPlayer`-гейту.
2. В `OperationsSettingsFragment`: убрать прямую обвязку подгруппы, добавить `btnOpenDefaultAppsDialog.setOnClickListener { DefaultAppsDialogFragment().show(..) }`; перенести/переиспользовать хелпер-обвязку в диалоге.

### Фаза 3 - Строки, docs, сборка

1. Строки: заголовок диалога + кнопка-вход «Задать по-умолчанию» (EN/RU/UK) через `set-android-string.ps1` (кириллица - через UTF-8 .ps1).
2. settings-doc-sync (Rule 22): регенерировать manifest + reference + annotations (позиция/группировка настроек изменилась).
3. Проба `Timber.d("S0880: ..")` на входе открытия диалога; `.\a.ps1 d` -> BUILD SUCCESSFUL.
4. Статус -> `BlockNeedUserTest` (устройство: кнопка открывает диалог; кнопки назначения по умолчанию работают из диалога; гейтинг supportsDefaultPlayer сохранён; портрет+ландшафт).

---

## Критерии готовности

1. В настройках вместо подгруппы default-player кнопок - одна кнопка «Задать по-умолчанию».
2. Кнопка открывает диалог со всеми перенесёнными кнопками; назначение по умолчанию работает как раньше.
3. Гейтинг `supportsDefaultPlayer` и per-type видимость сохранены в диалоге.
4. Диалог соблюдает button-taxonomy S0538; портрет и ландшафт корректны.

---

## 8. Влияние на пользователя (docs/FEATURES)

Изменение UI: кнопки назначения приложения по умолчанию собраны в отдельный диалог, вызываемый одной кнопкой в настройках.

- **EN:** The "set as default app" buttons now live in a dedicated dialog opened by a single "Set as default" button in settings.
- **RU:** Кнопки «сделать приложением по умолчанию» теперь в отдельном диалоге, открываемом одной кнопкой «Задать по-умолчанию» в настройках.
- **UK:** Кнопки «зробити застосунком за замовчуванням» тепер в окремому діалозі, який відкривається однією кнопкою «Задати за замовчуванням» у налаштуваннях.
