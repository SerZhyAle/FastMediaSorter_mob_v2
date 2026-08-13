# Стратегическая спецификация: S0501 - activity_camera_ocr_translate тёмная-только разметка

**Ticket:** S0501
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-18
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - parked during S0500 (unify-buttons) research 2026-06-18

> **Scope:** STRATEGIC. Скелет-захват из /spec-draft. Без имён классов/путей в финальной редакции.

---

## 0. Захваченный материал (inbox)

> Сырой захват находки на лету. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`.

**Захвачено:** 2026-06-18

**Захвачено во время:** S0500 (research кнопок, android-solution-researcher)

**Текст:**

`app_v2/src/main/res/layout/activity_camera_ocr_translate.xml` (~475 строк) - монолитная разметка с захардкоженными hex-цветами по всему файлу: `android:textColor`, `app:strokeColor`, `android:background` (строки ~29,50,63,78,99,113,128,161,167,180,186,199,218,224,252,261,277,278). Дизайн рассчитан только на тёмный фон и ломается на системной светлой теме. Нарушение CLAUDE.md Rule 19 (hardcoded hex в layout вместо `?attr/`/`@color/`).

SCOPE: вне объёма S0500 (унификация кнопок исключает поверхность камеры - §6 форк 1). Нетривиально: полноценный пересмотр темизации экрана OCR-перевода (тёмный контекст видоискателя + читаемость на обеих темах), требует собственного ресерча и решения по подсемейству стилей камеры.

**Вложения:**

Вложений нет.

---

## 1. Проблема

Разметка `activity_camera_ocr_translate.xml` (~475 строк) содержит захардкоженные hex-цвета по всему файлу - `android:textColor`, `app:strokeColor`, `android:background` (тёмные фоны, светлый текст, зелёный акцент). Это нарушает CLAUDE.md Rule 19 (raw hex в layout вместо `?attr/`/`@color/`).

Дизайн рассчитан только на тёмный контекст видоискателя и при попытке следовать системной светлой теме ломается по читаемости.

Экран существует только в `res/layout/` - landscape-аналога нет.

---

## 3. Решение

### 3.1 Подход

Экран OCR-перевода камеры - намеренно тёмная поверхность: накладки управления лежат поверх живого (как правило тёмного) изображения видоискателя, поэтому остаются тёмными независимо от системной темы (паттерн Google Camera/Lens).

Захардкоженные hex выносятся в выделенные токены `@color/camera_*` (при необходимости - scoped `ThemeOverlay`, проектный паттерн уже применяется в `themes.xml`). Токены не зависят от light/dark системной темы, что закрывает Rule 19 без потери дизайна.

Объём - только темизация: миграция hex → токены при идентичном визуале. Без структурной декомпозиции 475-строчного файла и без добавления layout-land.

### 3.3 Owner inputs (Approval gate)

- **Темизация:** тёмное под-семейство - экран остаётся тёмным всегда, независимо от системной темы; hex → выделенные `@color/camera_*` токены (+ опционально scoped `ThemeOverlay`).
- **Объём:** только темизация (Rule 19), визуал идентичен; без декомпозиции файла и без layout-land в этом тикете.
- **Flavor scope:** по месту поставки экрана OCR-перевода (определяется архитектурой OCR-бакетов, не выбор владельца).
- **Validation level:** сборка целевого варианта + ручная проверка читаемости на устройстве (визуальная темизация юнит-тестами не покрывается).
- **Related tickets:** запарковано во время research S0500 (унификация кнопок, форк 1 - поверхность камеры исключена из объёма S0500); жёстких блокеров-`Sxxxx` нет.

---

## 6. Открытые вопросы / Research items

- Нужно ли камере отдельное стилевое подсемейство (тёмный видоискатель) или адаптивная темизация под обе темы? - Status: Resolved - тёмное под-семейство (выделенные `@color/camera_*` токены, без `?attr/`), визуал тёмным всегда.

---

### Quiz decisions (2026-06-19)
- Направление темизации экрана OCR-перевода камеры → Тёмное под-семейство (видоискатель - живое тёмное изображение; накладки должны быть читаемы поверх него; стандартная практика камер-приложений).
- Объём тикета помимо устранения hex → Только темизация (чистая миграция hex → токены по Rule 19, визуал идентичен; декомпозиция и layout-land - вне объёма).

---

## Revision History

- **2026-06-19** - by `/spec-test-device` (device R5CY9070WNB online): scenario-only, automation out-of-scope (camera-capture flow + visual color judgment + system-theme toggle). Manual test recipe: temp/S0501_mobile_test_scenario_20260619_0045.md · PASS/FAIL/SKIPPED 0/0/6. Compile/resource validated (`a.ps1 fc` PASS).

---

## Last Audit

**Date:** 2026-06-19
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Visual: `activity_camera_ocr_translate` stays dark under system LIGHT theme in all 4 UI states (result, empty, crop, loading) - legible text, card backgrounds, stroke, save-accent colors. Scenario: `temp/S0501_mobile_test_scenario_20260619_0045.md`
