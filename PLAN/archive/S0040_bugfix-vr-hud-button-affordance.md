# Баг-фикс спецификация: S0040 — Визуальная affordance кнопок HUD в иммерсиве

**Ticket:** S0040
**Status:** Verified
**Implemented date:** 2026-04-30
<!-- auto-approved by /spec-all — 2026-04-30 -->
**Tactical plan:** `PLAN/S0040_bugfix-vr-hud-button-affordance/INDEX.md`
**Date:** 2026-04-30
**Tier:** 2 — Small
**Priority:** 65
**Roadmap entry:** Field session Quest 3, 2026-04-30; пункт 6Б в `PLAN/new-vr.txt`
**Replaces:** S0031 (`vr-immersive-hud-ux-gaps`, Archived — файл спека не существовал на диске)
**Related:** S0009 (vr-immersive-hud-gl — Implemented), S0024 (vr-hud-ray-input — BlockByOtherTask)

> **Scope:** BUGFIX/UX. Кнопки HUD выглядят как текстовые надписи без визуальной рамки. Добавить минимальную affordance: прямоугольная рамка вокруг каждой кнопки.

---

## 1. Проблема

В иммерсивном режиме просмотра VR180/360 видео HUD виден перед пользователем, однако интерактивные элементы (кнопки управления плеером) визуально неотличимы от текстовых подписей. Пользователь не понимает, что на них можно нажать.

**Цитата из `PLAN/new-vr.txt`:**
> «Кнопки HUD должны выглядеть как кнопки а не надписи. Хотя бы в прямоугольник их помести»

HUD реализован как bitmap `1024×256` (`VrHudRenderer: first HUD bitmap upload succeeded (1024x256)`), заливаемый в XR quad layer. Рендеринг — через Canvas/Bitmap в Kotlin, шрифт + иконки на прозрачном фоне.

---

## 2. Цели

1. Каждая кнопка HUD (pause/play, prev, next, seek, exit) имеет видимую прямоугольную рамку или заливку фона с закруглёнными углами.
2. Активная/нажатая кнопка визуально отличается от неактивной (иной оттенок или обводка).
3. Hover-состояние (когда луч контроллера над кнопкой) — опционально, не блокирует данный тикет; реализуется в S0024.
4. Изменения локализованы в Bitmap-composer HUD (`VrHudSceneComposer` / compositor), не затрагивают GL-слой.

**Non-goals (§3.2 hard constraints — см. ниже):**
- Не вводится ray-input / click-handling (это S0024).
- Не меняется размер HUD swapchain (1024×256 — достаточно для текущего набора кнопок).
- Не меняется позиция HUD в 3D-пространстве.

---

## 3. Предлагаемый подход

### 3.1 Реализация

В Bitmap-compositor HUD (класс, рисующий bitmap перед заливкой в `VrHudRenderer`):

1. Каждый элемент-кнопка имеет bounding rect.
2. Перед рисованием иконки/текста — `canvas.drawRoundRect(bounds, r, r, bgPaint)` с заполнением `Color.argb(120, 255, 255, 255)` (полупрозрачный белый фон).
3. Поверх — `canvas.drawRoundRect(bounds, r, r, borderPaint)` с `strokeWidth=2dp`, цвет `#80FFFFFF`.
4. Текст/иконка центрируется внутри bounds.

Радиус скругления `r=6dp` → пересчитать в пиксели bitmap-координат (`1024×256` при density=1.0).

Активное состояние (playing): фон более непрозрачный (`Color.argb(170, 255, 255, 255)`) в отличие от неактивного (paused): `Color.argb(100, 255, 255, 255)`.

### 3.2 Жёсткие ограничения (hard constraints)

- **НЕ** добавлять ray-input / click-handling (относится к S0024).
- **НЕ** менять размер HUD swapchain (`1024×256` фиксировано на сессию).
- **НЕ** менять позицию HUD в 3D-пространстве.
- **НЕ** трогать GL-слой (`VrHudRenderer` — только upload bitmap, не меняется).
- **НЕ** добавлять новые файлы/классы — только правки существующего compositor.

---

## 4. Затрагиваемые классы

- Compositor HUD bitmap (имя класса уточнить через `/catalog`) — добавить `drawRoundRect` для каждой кнопки
- `VrHudRenderer` — не меняется (только upload bitmap)

---

## 5. Критерии готовности

1. В иммерсивном режиме кнопки HUD визуально отличимы от текстовых подписей.
2. [MANUAL] Снимок экрана (screenshot via ADB или scrcpy) показывает рамки вокруг кнопок.
3. Производительность: перерисовка HUD bitmap занимает < 5ms (Canvas операции CPU-side).
4. Grep: `"drawRoundRect"` в `VrHudSceneComposer.kt` → ≥ 3 строки (progress + play/pause bg + play/pause border).
5. Grep: `"HUD_ELEMENT_PLAY_PAUSE"` в `VrHudSceneComposer.kt` → 1 строка объявления + 1 строка использования.

---

## 6. Связи

- **S0009** (Implemented) — базовый HUD рендер; данный тикет только расширяет visual style.
- **S0024** (BlockByOtherTask) — интерактивность кнопок; данный тикет — независимый предшественник.

---

## Last Audit

_Не проводился._

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-04-30 | /spec-all | Initial spec authored (S0040). |
| 2026-04-30 | /spec-update | Status Draft→Approved; §3.2 hard constraints extracted; §5 criteria refined with grep predicates and MANUAL markers; §2 Non-goals separated; §3.1/§3.2 subsections added. |
