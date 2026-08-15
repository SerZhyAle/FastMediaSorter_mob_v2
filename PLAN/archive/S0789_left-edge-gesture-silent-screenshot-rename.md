# Спецификация: S0789 - Переименование действия жеста "Тихий снимок"

**Ticket:** S0789
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 1 - Quick Win
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:** действие для жеста с левого края - "Тихий снимок" переименовать в "Тихий снимок экрана".

---

## 1. Проблема

Метка действия edge-жеста `SILENT_SCREENSHOT` в RU звучала как "Тихий скриншот". С появлением действий работы с камерой (S0788, S0790+) в том же picker-е слово без явного "экрана" не отделяет снимок экрана от фото с камеры.

## 2. Цели

1. RU-метка действия `SILENT_SCREENSHOT` читается как "Тихий снимок экрана" - явно про экран, не про камеру.
2. Локализационный паритет EN/RU/UK сохранён.

**Non-goals:**

- Поведение действия и логика захвата экрана - без изменений.
- Остальные метки действий жеста (см. S0793).

## 3. Ограничения

- **Flavor:** без разницы (строка в `src/main/res`, действие ships standard `fms.edgeGestureOverlay` + noLegal).
- **Локализация:** EN/RU/UK - обязательна.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0672, S0793.

## 4. Критерии готовности

1. RU-метка = "Тихий снимок экрана".
2. EN = "Silent screenshot" (уже корректно, без изменений).
3. UK-метка = "Тихий знімок екрана" (паритет).
4. `check_strings_localized.ps1 -KeyPrefix screenshot_gesture_action` - PASS.

## Реализация (2026-07-01)

- Строка `screenshot_gesture_action_silent`: RU "Тихий скриншот" -> "Тихий снимок экрана"; UK "Тихий знімок" -> "Тихий знімок екрана"; EN оставлено "Silent screenshot".
- Правка через `scripts/utils/set-android-string.ps1 -Action set` (byte-preserving, guard `-ExpectedOldValue`).
- Валидация: `check_strings_localized.ps1 -KeyPrefix screenshot_gesture_action` - exit 0 (11 ключей в EN/RU/UK).
