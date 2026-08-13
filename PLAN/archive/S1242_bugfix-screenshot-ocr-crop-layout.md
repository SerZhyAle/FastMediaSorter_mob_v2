# Стратегическая спецификация: S1242 - Исправление снимка и обрезки OCR по жесту

**Ticket:** S1242
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-28
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-28
**Tactical spec:** `PLAN/S1242_bugfix-screenshot-ocr-crop-layout/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation
- **Goal / expected outcome:** Provided by user - при жесте снимка с обрезкой и отправкой снимок не содержит меню-подсказку, нижняя граница обрезки доступна, а нижняя панель имеет нормальную высоту.
- **Local anchor:** Provided by user - сценарий «жест скриншот-обрезать-отправить» и приложенный снимок экрана.
- **Scope boundaries / forbidden areas:** Delegated by user - исправить только поток снимка и обрезки, не меняя назначение жестов и другие функции захвата.
- **Done / success signal:** Provided by user - меню-подсказка не попадает в результат, нижнюю границу рамки можно потянуть, нижняя панель не занимает избыточную высоту.
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions after examining the screenshot and current flow.
- **UI decisions / delegation:** Provided by user - подсказка исчезает до захвата; рамка остаётся над панелью с доступной зоной захвата; размеры и расположение панели в портретной и альбомной ориентации уточняет агент по существующему UI-паттерну.

---

## 1. Проблема

При запуске OCR-снимка с краевого жеста всплывающая подсказка выбора действия остаётся поверх экрана в момент захвата и становится частью изображения. На шаге обрезки нижняя граница рамки визуально и физически конфликтует с нижней панелью. Панель действий занимает больше вертикального пространства, чем требуется для её элементов.

---

## 2. Цели

1. Захваченный экран не содержит подсказку краевого жеста.
2. Нижняя граница и углы рамки обрезки доступны для перетаскивания.
3. Нижняя панель сохраняет 48dp цель касания, но не использует лишнюю вертикальную высоту.
4. Поведение остаётся одинаковым для доступного и MediaProjection путей захвата.

**Non-goals:**

- Не менять набор и назначение действий краевого жеста.
- Не менять результаты OCR, выбор языков или последующую отправку.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Убрать меню-подсказку из снимка.
2. Сделать нижнюю рамку обрезки перетаскиваемой.
3. Уменьшить нижнюю панель.

### 3.2 Жёсткие ограничения

- **Flavor:** исправление покрывает доступные реализации захвата, без утечки флейворной логики в общий UI.
- **API level:** без изменения поддерживаемых версий Android.
- **Wear OS:** не затрагивается.
- **Производительность:** не добавлять заметную задержку перед захватом.
- **Совместимость данных:** нет.
- **Локализация:** существующие строки используются без изменений.
- **Доступность:** сохранить минимум 48dp для кнопок, D-pad-фокус и описание рамки для TalkBack.

---

## 4. Контекст текущей архитектуры

Краевой жест показывает временную подсказку поверх стороннего приложения, затем запускает один из путей системного снимка. После сохранения OCR-путь открывает экран предварительного просмотра с рамкой обрезки и нижней панелью действий. Геометрия рамки зависит от фактической области изображения и зарезервированного места для панели.

---

## 5. Предлагаемый подход

Перед началом захвата временная подсказка синхронно снимается с окна. Экран обрезки получает один согласованный резерв под нижнюю панель: рамка ограничивается выше её области, а панель использует компактные внутренние отступы при сохранении размера целей касания. Обе реализации захвата применяют одинаковое правило очистки подсказки.

### 5.1 Основные столпы / модули

- Жизненный цикл подсказки жеста перед захватом.
- Геометрия области обрезки и доступность нижней границы.
- Компактная нижняя панель действий OCR.

### 5.2 Потоки данных и событий

Жест → снятие временной подсказки → системный захват → подготовка изображения → экран обрезки → подтверждение OCR/отправки.

### 5.3 Точки расширяемости

Общий контракт скрытия временной подсказки остаётся пригодным для других действий, запускаемых из краевой панели.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. Запрос делегирует точные отступы и ориентационные детали агенту в рамках существующих UI-паттернов.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Подсказка исчезнет, но её окна будут возвращаться с задержкой | Низкая | Попадание подсказки в часть снимков | Снять только временное окно без остановки самих зон жеста |
| Сжатие панели ухудшит нажатие | Низкая | Недоступные действия | Сохранить цели касания не менее 48dp |
| Изменение резерва исказит нормализацию рамки | Средняя | Неверная область OCR | Проверить нормализованную рамку у края изображения |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES: это исправление существующего OCR-сценария.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Снимать только подсказку, а не отключать зоны жеста**

- **Решение:** убрать из окна временную подсказку непосредственно перед захватом, сохранив рабочие зоны жеста.
- **Альтернативы:** остановить весь оверлей или ждать таймер его исчезновения.
- **Почему:** так снимок чистый без потери доступности следующего жеста и искусственной задержки.

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. На снимке, полученном через краевой OCR-жест, отсутствует подсказка действий.
2. В портретной ориентации нижняя грань и оба нижних угла рамки обрезки доступны для перетаскивания.
3. Панель действий внизу имеет компактную высоту и содержит все доступные действия.
4. В альбомной ориентации рамка и панель не перекрывают друг друга.
5. После обрезки распознавание и отправка продолжают работать как прежде.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S1242` - создаст `PLAN/S1242_bugfix-screenshot-ocr-crop-layout/` с фазами.

---

## Last Audit

### Manual device test - 2026-07-29 (emulator-5554, standard debug)

Device: `sdk_gphone16k_x86_64`, Android 17 (SDK 37), 1600x2560 @320dpi (density 2.0), package `com.sza.fastmediasorter.debug`. Evidence: `temp/S1242/`.

Entry-point substitution: the edge-gesture strip could not be used, so the crop screen was reached through the camera source of the same `CameraOcrTranslateActivity` flow (`Settings -> Management -> Additional programs -> Camera OCR translation` on, then the exported `MainActivity` action `com.sza.fastmediasorter.action.CAMERA_OCR_TRANSLATE`). The crop screen, its frame geometry and its bottom bar are shared by both sources, so criteria 2-5 are exercised unchanged; only criterion 1 depends on the gesture overlay. Probe `S1242: OCR crop step shown` fired on entry, confirming the changed flow was the one under test. Direct `am start` of `CameraOcrTranslateActivity` with `source_image_path` is not usable: the activity is `exported="false"` and the emulator image has no root.

- Критерий 1, подсказка жеста не попадает в снимок: INCONCLUSIVE. The removal runs in `ScreenGestureOverlayManager.onTouch` on `ACTION_UP`, which only a real touch on the `TYPE_APPLICATION_OVERLAY` strip can raise; `adb input swipe` does not reach a `FLAG_NOT_FOCUSABLE` overlay window. The QS-tile fallback cannot substitute: `ScreenshotGestureTileService.onClick` starts `ScreenCaptureConsentActivity` directly and never constructs or hides a hint. The installed APK also lacks that tile component (`dumpsys package` lists only `AudioToggleTileService` and `AppLaunchPanelTileService`), matching `fms.edgeGestureOverlay`/`fms.edgeGestureTile` defaulting to off. Rebuilding with both properties would still not make the swipe reachable, so this needs a real device.
- Критерий 2, нижняя грань рамки перетаскивается: PASS. Expected: dragging the bottom edge moves it. Actual: bottom edge moved 1734 -> 1364 px after `input swipe 800 1734 800 1380`.
- Критерий 2, нижний левый угол: PASS. Expected: the corner resizes both sides. Actual: left 18 -> 375 px and bottom 1364 -> 1184 px after one diagonal drag.
- Критерий 2, нижний правый угол: PASS. Expected: same, mirrored. Actual: right 1581 -> 1203 px and bottom 1711 -> 1408 px. The first attempt was swallowed by the system back gesture (activity returned to `MainActivity`); re-run under three-button navigation succeeded, so the earlier miss was a system-gesture artefact, not app behaviour.
- Критерий 3, компактная нижняя панель: PASS. Expected: no excess vertical height, 48dp targets kept. Actual: `cropActionBar` = 128 px = 64dp (48dp buttons plus 8dp padding top and bottom); `btnCropRetry`/`btnCropOcrLang`/`btnCropConfirm` each 96 px = 48dp tall. Identical 128 px under gesture nav and three-button nav, so the bar does not grow with the navigation inset.
- Критерий 4, альбомная ориентация без перекрытия: PASS. Expected: frame and panel do not overlap. Actual: at 2560x1600 `cropOverlay` = [0,48][2560,1360] and `cropActionBar` = [0,1360][2560,1488] - they tile, and the frame stays a further 48dp above the image bottom. Bar height still 128 px = 64dp. Verified by reshaping the display, not by rotating the AVD.
- Критерий 5, OCR и отправка: PASS. Expected: recognition and sending keep working after cropping. Actual: `OK` produced the result screen (`OCR Translation Result`, `ORIGINAL TEXT` = `=`, the virtual scene carries almost no glyphs); ML Kit modules `VisionOcr`/`MlkitOcrCommon` loaded with no crash; `Save TXT` reported `Saved text to Downloads/OCR_TXT_20260729_164641.txt`.
- Reserved band matches the code: the frame bottom sat about 96 px above the image bottom in both orientations, agreeing with `CropOverlayView.BOTTOM_SAFE_INSET_DP` = 48dp at density 2.0.

Overall: 5 of 6 checks PASS on emulator; the gesture-hint criterion is INCONCLUSIVE because the overlay strip is not drivable there, which is a harness limit rather than a defect. Spec stays in `BlockNeedUserTest` for the hint check on a real device; debug tags retained.

Device state restored after the run: gesture navigation re-enabled, portrait rotation restored, `Camera OCR translation` toggled back off, staged files removed.

---

## Remote log pass 2026-08-01/02 - criterion 1 VERIFIED

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog`.

The one criterion left open by the emulator sweep was: the gesture hint must not appear in the
captured image. The bundle settles it.

- Seven screen captures in the bundle are each preceded by `S1242: gesture hint removed before capture`
  within 0.5 to 1.8 seconds, so all seven are genuine gesture-triggered captures on a real device:
  `screenshot_20260801_212849.png`, `_20260801_234733`, `_20260802_000119`, `_20260802_000510`,
  `_20260802_000817`, `_20260802_001049`, `_20260802_001802`.
- Five were inspected image by image. None contains the gesture hint - no direction legend, no action
  rows, no cancel target. Two of them carry a semi-transparent circular control at the right edge;
  that is the Browse scroll-jump control from `activity_browse.xml`, present only in the captures of a
  populated file list and absent from the empty-list one, so it is not the hint.
- This is the case the emulator could not reach: `adb input swipe` cannot drive a `FLAG_NOT_FOCUSABLE`
  overlay, and the QS-tile fallback never builds a hint at all.

Criterion 1 passes. Debug probes removed from `ScreenGestureOverlayManager.kt` and
`CameraOcrTranslateActivity.kt`; `.\a.ps1 fkn` -> `BUILD SUCCESSFUL`, exit 0.
