# Стратегическая спецификация: S0433 - Мёртвый тоггл «Скриншот жестом вниз»

**Ticket:** S0433
**Status:** Archived
**Priority:** 35
**Date:** 2026-06-15
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - auto-captured during S0418 (/spec-draft)

> **Scope:** STRATEGIC skeleton. Захват находки, без research/approval. Draft.

---

## 0. Сырой захват (verbatim)

Обнаружено при разведке S0418 (порт жест-скриншота в standard).

- Настройка `screenshotGestureDownEnabled` персистится (`ScreenshotSettingsStore`, поле `AppSettings`) и отображается в UI настроек («Скриншот жестом вниз», `PlaybackSettingsFragment`, строка `rowScreenshotGestureDown`), но **не читается ни одной реализацией захвата** - ни в `src/noLegal` (a11y/MediaProjection), ни в новой Play-реализации `src/screenCapturePlay`.
- То есть тоггл - no-op: пользователь его включает/выключает, поведение не меняется. Второго направления жеста (отдельно «вниз») в распознавателе нет; жест-триггер фиксирован (диагональ вправо-вниз ~45°).
- Наследие S0405 (реализация на noLegal). Порт S0418 переносит ту же UI-строку в Play-флаворы, поэтому теперь мёртвый тоггл виден и в основном Play-релизе.

**Evidence:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt` - ключ `screenshot_gesture_down_enabled`.
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` - поле `screenshotGestureDownEnabled`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` - строка настройки.
- `grep -rn "screenshotGestureDownEnabled" app_v2/src/screenCapture* app_v2/src/noLegal` - ноль чтений в коде захвата.

**Возможные направления (на research, не решено):**
- Убрать строку UI + поле + ключ (чистый путь, если второе направление не планируется).
- Скрыть строку до реализации.
- Довести до реального поведения: распознавать отдельный жест «вниз» и привязать к нему действие.

---

## 1. Проблема

Тоггл настройки без эффекта вводит пользователя в заблуждение и нарушает Rule 20 (мёртвый вес). До S0418 виден только в noLegal; после - в Play-релизе.

---

## 11. Критерии готовности

Заполнить при переходе Draft → Approved.
