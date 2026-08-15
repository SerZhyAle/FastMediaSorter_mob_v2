# Спецификация: S0794 - Действие жеста для фото и OCR-перевода

**Ticket:** S0794
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:** /spec-draft Новое действие для жеста с левого края - "Сделать фото - OCR - Перевод" - у нас есть такой сценарий уже.

---

## 1. Проблема

OCR-перевод уже применяется к скриншоту (действие `OCR_TRANSLATE`) и к открытому изображению, но нет жеста «снять фото и сразу распознать/перевести».

## 2. Цели

1. Новое действие `TAKE_PHOTO_OCR_TRANSLATE`: авто-снимок -> открытие снятого фото в просмотрщике с OCR-переводом.
2. Действие скрыто, когда способность перевода не скомпилирована (как у `OCR_TRANSLATE`).

**Non-goals:**

- Полностью невидимый захват (см. S0790).

## 3. Ограничения

- **Flavor:** `src/main`; переиспользует авто-захват S0790 + `AUTO_ACTION_TRANSLATE` просмотрщика; гейтинг по `CapabilityAvailability.isTranslationAvailable()`.
- **Локализация:** EN/RU/UK - добавлена `screenshot_gesture_action_take_photo_ocr_translate`.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0563, S0790.

## 4. Критерии готовности

1. `TAKE_PHOTO_OCR_TRANSLATE` в enum, диспетчере, picker-е с меткой.
2. Жест снимает фото и открывает его с OCR-переводом; действие скрыто без способности перевода.
3. Проект компилируется (standard + noLegal).

## Реализация (2026-07-01)

- `ScreenshotGestureAction.TAKE_PHOTO_OCR_TRANSLATE` (pre-capture); диспетчер: если `isTranslationAvailable()` -> `launchPhotoCapture(context, AUTO_ACTION_TRANSLATE)`, иначе fallback на сохранение (Timber.i).
- `PhotoCaptureLaunchManager`: авто-захват -> FileProvider Uri -> `PhotoVideoStandaloneActivity` (image/jpeg, `EXTRA_AUTO_ACTION=AUTO_ACTION_TRANSLATE`) -> `translateCurrentImage()`.
- `ScreenshotGestureActionPickerManager.availableActions()`: скрывает действие при `!isTranslationAvailable()` (наравне с `OCR_TRANSLATE`).
- Метка `screenshot_gesture_action_take_photo_ocr_translate` (EN "Take a photo and OCR-translate" / RU "Сделать фото и OCR-перевод" / UK "Зробити фото і OCR-переклад").
- Валидация: `a.ps1 fk` + `a.ps1 fkn` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** назначить «Сделать фото и OCR-перевод»; жест снимает фото, открывает просмотрщик с переводом. Проверить скрытие действия без способности перевода. Проверить на standard.
