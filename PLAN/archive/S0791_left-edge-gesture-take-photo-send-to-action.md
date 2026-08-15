# Спецификация: S0791 - Действие жеста для фото и отправки

**Ticket:** S0791
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:** /spec-draft Новое действие для жеста с левого края - "сделать фото и отправить в.." - меню "отправить в.." у нас реализовано.

---

## 1. Проблема

Есть меню «отправить в..» (S0680) и авто-фото (S0790), но нет жеста «сделать фото и сразу отправить».

## 2. Цели

1. Новое действие `TAKE_PHOTO_SEND_TO`: авто-снимок -> открытие снятого фото в просмотрщике с меню «отправить в..».

**Non-goals:**

- Полностью невидимый захват (см. S0790).

## 3. Ограничения

- **Flavor:** `src/main`; переиспользует авто-захват S0790 + `AUTO_ACTION_SEND_TO` просмотрщика.
- **Локализация:** EN/RU/UK - добавлена `screenshot_gesture_action_take_photo_send_to`.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0790, S0563, S0568, S0680.

## 4. Критерии готовности

1. `TAKE_PHOTO_SEND_TO` в enum, диспетчере, picker-е с меткой.
2. Жест снимает фото и открывает его в просмотрщике с «отправить в..».
3. Проект компилируется (standard + noLegal).

## Реализация (2026-07-01)

- `ScreenshotGestureAction.TAKE_PHOTO_SEND_TO` (pre-capture); диспетчер `launchPhotoCapture(context, PhotoVideoStandaloneActivity.AUTO_ACTION_SEND_TO)`.
- `PhotoCaptureLaunchManager`: авто-захват -> снятый файл оборачивается в FileProvider Uri -> `PhotoVideoStandaloneActivity` (ACTION_VIEW, image/jpeg, `EXTRA_AUTO_ACTION=AUTO_ACTION_SEND_TO`). Скретч-файл не удаляется (просмотрщик читает его).
- Метка `screenshot_gesture_action_take_photo_send_to` (EN "Take a photo and send to.." / RU "Сделать фото и отправить в.." / UK "Зробити фото і надіслати до..").
- Валидация: `a.ps1 fk` + `a.ps1 fkn` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** назначить «Сделать фото и отправить в..»; жест снимает фото, открывает просмотрщик с меню «отправить в..». Проверить на standard.
