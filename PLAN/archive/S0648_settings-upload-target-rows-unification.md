# Стратегическая спецификация: S0648 - Унификация строк выбора upload target в настройках

**Ticket:** S0648
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-23
**Roadmap entry:** Ad-hoc - запрос 2026-06-23
**Tactical spec:** будет создан через `/spec-tech` после разблокировки S0644

> **Scope:** STRATEGIC.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** owner request `/spec-draft`

**Текст запроса (verbatim):**

Настройки-Управление-Кнопка -группа "Взаимодействие с ОС" -"Ресурс для загрузки" и в группе "Жесты с левого края" - "Загружать снимки в".. .. В обоих случаях всё должно выглядеть как в группе "Камера, микрофон и другие функции" у элементов "По умолчанию папка камеры.." или для видео

**Ключевые требования из запроса:**

- Экран: Settings -> Operations.
- Первая строка: Button group -> "OS interaction" -> "Resource for downloads".
- Вторая строка: "Left-edge gestures" -> "Upload screenshots to..".
- Обе строки должны выглядеть одинаково.
- Визуальный эталон: группа "Camera, microphone and other functions", элементы "Default camera folder.." и аналогичный элемент для video.

**Что ожидается позже при доработке:**

- Проверить текущий visual/layout pattern у двух целевых строк.
- Сравнить их с эталонными camera/video target rows.
- Привести обе строки к одному виду в portrait и landscape, если для этого нужны layout counterpart edits.

**Вложения:** нет.

---

## 1. Контекст

Целевые строки на экране Operations (`OperationsSettingsFragment` + `fragment_settings_destinations.xml`):

- "Resource for downloads" - `rowLinkAutodownloadResource` (`OperationsSettingsFragment:370`, `SettingsSelectionRow` -> `showDestinationPicker`).
- "Upload screenshots to.." - строка screenshot-destination в группе left-edge gestures (`OperationsGesturesManager`, тот же `showDestinationPicker`).

Эталон, названный owner - строки camera/video folder в группе "Camera, microphone and other functions" ("Default camera folder.."). Это узкий случай общего row-эталона: вид строки выбора значения определяет S0644. S0648 - применение к двум конкретным строкам.

---

## 2. Цель

Привести две destination-строки к виду эталонных camera/video folder-строк (одинаковый layout, no-stretch, portrait + landscape).

---

## 3. Объём работ (scope)

### 3.1 В объёме

- Сравнить layout двух целевых строк с camera/video folder-строками.
- Привести к единому виду в portrait + landscape.

### 3.2 Вне объёма

- Прочие строки (общий аудит - S0644).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0644 (row-эталон, блокер), S0645, S0646, S0649 (settings-UI батч), S0567 (доставил `SettingsSelectionRow`), S0605 (no-full-width).
- **UI scope:** две строки на Settings -> Operations, portrait + landscape.

---

## 10. Зависимости и связанные тикеты

- **S0644** - блокер. Определяет канонический вид строки выбора значения. После разблокировки S0644 подтвердить: эталон S0648 (camera-folder строки) совпадает с эталоном S0644 (gesture-action), либо согласовать, какой вид побеждает.
- **S0646 / S0645 / S0649** - settings-UI батч.
