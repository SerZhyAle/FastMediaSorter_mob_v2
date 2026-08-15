# Спецификация (compact bugfix): S1416 - Повтор пункта «Камера» в ярлыке лаунчера

**Ticket:** S1416
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-05
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-05

**Текст:**

Диалог "Выбор функции" для ярлыка для лаунчера. Пункт "Камера" встречается два раза. Если это одинаковый ярлык - лишний удалить. Если там разное поведение - переименовать

---

## 1. Проблема / симптом

В диалоге выбора функции для ячейки лаунчера показывались две строки «Камера». Одна открывала
обычную камеру, другая запускала быстрое сохранение снимка, но по подписи различить их было нельзя.

---

## 2. Корневая причина

Маршрут быстрой съёмки повторно использовал строку «Камера», уже занятую маршрутом обычного
запуска камеры. Это два разных entry point с разным результатом съёмки.

---

## 3. Исправление

Маршрут быстрой съёмки использует существующую локализованную строку «Быстрая съёмка». Обычная
камера сохраняет подпись «Камера». Добавлен unit-тест, проверяющий, что эти маршруты не используют
один string resource.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

`pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Flavor Standard -Tests
'com.sza.fastmediasorter.core.launcher.LauncherFeatureRouteLabelsTest' -Quiet`:

- expected: targeted test completes successfully and the two camera routes use distinct labels
- actual: exit 0, `BUILD SUCCESSFUL`
