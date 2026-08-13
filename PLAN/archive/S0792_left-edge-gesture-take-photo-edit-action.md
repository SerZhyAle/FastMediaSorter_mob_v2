# Спецификация: S0792 - Действие жеста для фото и редактирования

**Ticket:** S0792
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:** /spec-draft Новое действие для жеста с левого края - "сделать фото и редактировать" - результат фото с камеры открывается нашим редактором картинок.

---

## 1. Проблема

Есть встроенный редактор (draw/crop в просмотрщике) и авто-фото (S0790), но нет жеста «снять и сразу редактировать».

## 2. Цели

1. Новое действие `TAKE_PHOTO_EDIT`: авто-снимок -> открытие снятого фото в редакторе (draw-режим просмотрщика).

**Non-goals:**

- Полностью невидимый захват (см. S0790).
- Отдельный/новый экран редактора - переиспользуется существующий draw-режим.

## 3. Ограничения

- **Flavor:** `src/main`; переиспользует авто-захват S0790 + `AUTO_ACTION_DRAW` просмотрщика.
- **Локализация:** EN/RU/UK - добавлена `screenshot_gesture_action_take_photo_edit`.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0790, S0563.
- **Уточнение:** «наш редактор картинок» = draw/crop-инструменты в `PhotoVideoStandaloneActivity` (`AUTO_ACTION_DRAW`).

## 4. Критерии готовности

1. `TAKE_PHOTO_EDIT` в enum, диспетчере, picker-е с меткой.
2. Жест снимает фото и открывает его в редакторе.
3. Проект компилируется (standard + noLegal).

## Реализация (2026-07-01)

- `ScreenshotGestureAction.TAKE_PHOTO_EDIT` (pre-capture); диспетчер `launchPhotoCapture(context, PhotoVideoStandaloneActivity.AUTO_ACTION_DRAW)`.
- `PhotoCaptureLaunchManager`: авто-захват -> FileProvider Uri -> `PhotoVideoStandaloneActivity` (image/jpeg, `EXTRA_AUTO_ACTION=AUTO_ACTION_DRAW`) -> `enterDrawMode()`.
- Метка `screenshot_gesture_action_take_photo_edit` (EN "Take a photo and edit" / RU "Сделать фото и редактировать" / UK "Зробити фото і редагувати").
- Валидация: `a.ps1 fk` + `a.ps1 fkn` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** назначить «Сделать фото и редактировать»; жест снимает фото, открывает редактор. Проверить на standard.
