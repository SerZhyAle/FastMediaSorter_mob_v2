# Стратегическая спецификация: S0541 - Хардкод-цвета в диалоге camera OCR

**Ticket:** S0541
**Status:** Archived
**Priority:** 35
**Date:** 2026-06-19
**Tier:** 1 - Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc - авто-захват при инвентаризации тогглеров S0536 (2026-06-19).

> **Scope:** STRATEGIC DRAFT - идея-инбокс. Без ресёрча/аппрува/цепочки.

---

## 0. Захват

Найдено при исчерпывающей инвентаризации разметки для S0536. `dialog_camera_ocr_settings.xml` содержит хардкод hex-цветов в layout - нарушение CLAUDE.md Rule 19 (в разметке только `?attr/`/`@color/`).

Evidence (из sweep): `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml` - `android:textColor="#FFFFFF"`, `#A1A1AA`, `#B00020` и др. (8+ литералов) на текстовых элементах overlay-UI камеры. Соседний `dialog_capture_keybinding.xml:40` также подозрителен (`#..`).

Примечание: строка `cbOcrOnly` будет переведена на `SettingsToggleRow` в рамках S0536 (Phase 04.4), что попутно уберёт хардкод-цвет её лейблов. Остальные хардкод-цвета диалога - НЕ в объёме S0536.

---

## 1. Проблема

Overlay-UI камеры/OCR использует хардкод hex-цвета вместо токенов темы (`?attr/`/`@color/`). Это нарушает Rule 19, мешает корректной работе тем (light/dark) и единообразию, и обходит neuroslop-гейт только через ratchet-baseline.

---

## 2. Цели

1. Заменить хардкод hex-цвета в `dialog_camera_ocr_settings.xml` (и при подтверждении - `dialog_capture_keybinding.xml`) на токены темы/ресурсы цвета.
2. Сохранить читаемость overlay на тёмном фоне камеры (если фон всегда тёмный - использовать корректный фиксированный `@color/` токен, а не сырой hex).

**Non-goals:**

- Унификация тогглеров (S0536).

---

## 6. Открытые вопросы / Research items

1. **Тёмный фон overlay**
   - **Нужно выяснить:** какие из цветов обусловлены тем, что overlay всегда на тёмном фоне предпросмотра камеры (тогда нужен фиксированный `@color/` токен, а не `?attr/`), а какие должны следовать теме.
   - **Статус:** Resolved - ни один из диалогов не является overlay на превью камеры. Оба показываются как обычные `AlertDialog` (`CameraOcrTranslateActivity.showSettingsDialog` через `AlertDialog.Builder().setView()`; `CaptureDialogFragment` как themed-диалог). Хардкод-`#FFFFFF`/`#A1A1AA` ломались в light-теме (белый текст на светлом фоне) - корректны `?attr/`-токены, а не фиксированный `@color/`.

---

## 7. Реализация

Все hex-литералы заменены на theme-токены (BUILD не запускался по запросу):

- `dialog_camera_ocr_settings.xml`: `#FFFFFF` (значения спиннеров, заголовок OCR-only) -> `?attr/colorOnSurface`; `#A1A1AA` (заголовки языков, описание OCR-only) -> `?attr/colorOnSurfaceVariant`.
- `dialog_capture_keybinding.xml`: `#B00020` (текст конфликта) -> `?attr/colorError` (консистентно с уже используемым `?attr/colorOnSurfaceVariant` в этом же файле).
- Neuroslop-гейт `layout-hardcoded-colors` заратчечен 145 -> 98.
- Land-аналогов нет (Rule 11 не применим).

---

## 10. Связи с другими спеками

- S0536 - попутно убирает хардкод-цвет только для строки `cbOcrOnly`; этот тикет покрывает остальные хардкод-цвета диалога.

---

## 12. Дальнейшие шаги

Реализовано (см. §7). Аудит подтвердил.

## Last Audit

**Date:** 2026-06-19
**Mode:** full
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0

Static checks: 0 hex literals remaining in `dialog_camera_ocr_settings.xml` and `dialog_capture_keybinding.xml` (all `="#.."` gone -> `?attr/colorOnSurface`/`colorOnSurfaceVariant`/`colorError`). Neuroslop `layout-hardcoded-colors` ratcheted 145 -> 98. No `.kt` / no debug tags. No land counterpart (Rule 11 n/a). No device acceptance.
