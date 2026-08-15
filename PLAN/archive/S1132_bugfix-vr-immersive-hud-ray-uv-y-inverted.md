# Спецификация: S1132 - Инверсия UV.Y луча в immersive HUD (подсветка браузера зеркалится)

**Ticket:** S1132
**Status:** Archived
**Priority:** 70
**Date:** 2026-07-20
**Tier:** 2 - Small

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-20 (device-test VR-сценария, Quest 3 noLegal)

**Текст:**

В immersive VR-браузере (S1116) луч контроллера виден правильно, но подсвечивается не та плашка - как будто по вертикали перевёрнуто (навожу на верхний ряд - подсвечивается нижний). Нажать на нужную ячейку невозможно.

---

## 1. Проблема / корень

Луч даёт UV по геометрии квада: `outUv.y = local_y/height + 0.5` (xr_raycast.cpp) - **v=0 внизу, v=1 вверху** (GL-конвенция). Квад (`buildQuadMesh`) отображает texture v=0 на ВЕРХ квада, а битмап рисуется в Canvas с row 0 сверху -> панель видна правильно (верхом вверх).

Но hit-test переводит UV в canvas-пиксели без флипа Y:
`py = uvY * PANEL_HEIGHT` (ImmersiveBrowseInteractionDispatcher). При uvY=1 (геом. верх = верх панели) получается py=PANEL_HEIGHT (низ канваса) -> `cell.bounds.contains(px, py)` попадает в зеркальный ряд. X не затронут (uvX слева-направо совпадает).

## 2. Исправление

`py = (1f - uvY) * PANEL_HEIGHT` в `ImmersiveBrowseInteractionDispatcher.dispatch`. Инверсия только по Y.

Тот же паттерн `py = uvY * HEIGHT` есть в плеерном `HudInteractionDispatcher` (латентно инвертирован, но кнопки в одной горизонтальной строке - незаметно). Правится ОТДЕЛЬНО после подтверждения направления флипа на устройстве, чтобы не задеть уже принятый S0964.

## 3. Проверка

Quest 3 (noLegal): immersive-браузер - навести луч на верхний ряд плашек, подсвечивается ИМЕННО верхний ряд; клик открывает наведённую ячейку. logcat marker `S1132:`.

## 4. Затронутые области

- `app_v2/src/vr/.../browse/ImmersiveBrowseInteractionDispatcher.kt` (hit-test Y-flip).
- Follow-up: `HudInteractionDispatcher` (плеерный HUD) - тот же флип, отдельным заходом.
