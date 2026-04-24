# Specification: VR-HAND-TRACKING — Управление VR-плеером жестами рук (без контроллеров)

**Status:** Backlog — не реализуется до завершения `spec_vr-immersive-controls.md`
**Date:** 2026-04-24
**Tier:** будет проставлен при активации
**Roadmap entry:** Ad-hoc — запрос пользователя 2026-04-24. Контроллеры — основной способ управления; hand-tracking — дополнительная поверхность для сценариев, когда контроллеры отложены.

---

## 1. Проблема

После того как `spec_vr-immersive-controls.md` добавит управление контроллерами Touch Plus, останется один нереализованный сценарий: пользователь смотрит фильм лёжа и отложил контроллеры. Управлять воспроизведением без физического устройства сейчас невозможно. Horizon OS поддерживает `XR_EXT_hand_tracking` + `XR_META_hand_tracking_aim`, что даёт готовый луч из руки и силу щипка (pinch) — именно этого достаточно для видеоплеера.

---

## 2. Цели

1. При отложенных контроллерах приложение автоматически переключается в режим hand-tracking (детектируется по `XrSessionState` + отсутствию активного grip).
2. **Щипок (pinch)** правой или левой рукой = клик по элементу оверлея под лучом-указателем.
3. **Луч из руки** (`XR_META_hand_tracking_aim`) = аналог луча-указателя контроллера.
4. **Жест пальцем влево/вправо** (thumb swipe через `XR_META_hand_tracking_microgestures`) = перемотка. Жест вверх/вниз = громкость.
5. **Двойной щипок** = пауза / воспроизведение (без оверлея).
6. При обнаружении контроллера в руке — автоматически возврат к слою A (OpenXR action system контроллера).
7. Шпаргалка hand-tracking маппинга (отличается от контроллерной).

Non-goals для этой итерации:
- Распознавание произвольных жестов (ASL, кастомные позы).
- Render hand mesh (полигональная сетка рук) — достаточно системного passthrough.
- Взаимодействие двумя руками одновременно (только ведущая рука; определяется автоматически).
- Zoom через разведение рук (pinch-to-zoom двумя руками) — в следующей волне.

---

## 3. Техническая сложность: СРЕДНЯЯ

Почему не высокая:

- `XR_EXT_hand_tracking` — стандартное расширение Khronos, доступно на Quest 2/3/Pro.
- `XR_META_hand_tracking_aim` предоставляет готовую `XrPosef` (позицию + ориентацию) для луча из руки, без ручных вычислений по суставам. Это то, что реализует системный лазерный луч в Horizon OS shell.
- `XR_META_hand_tracking_microgestures` предоставляет дискретные события thumb-tap и thumb-swipe — готовые для seek/volume без разработки детектора жестов.
- Pinch detection: `pinchStrengthIndex` > 0.9 (предоставляется `XR_META_hand_tracking_aim`) — одна строка кода.

Что требует работы:
- Регистрация расширений при создании `XrInstance` в `OpenXrNative.cpp`.
- Создание `XrHandTrackerEXT` для обеих рук.
- Per-frame опрос `XrHandTrackingAimStateFB` для каждой руки.
- Автопереключение между hand-tracking и controller-mode (обнаружение по присутствию grip в кадре).
- Kotlin-side: расширение `VrControllerInputManager` для обработки событий из hand-tracking-колбэков.
- Обновление шпаргалки маппинга (отдельный оверлей для hand-tracking mode).
- Разрешение `HAND_TRACKING` в манифесте VR-флейвора (`<uses-permission>`).

---

## 4. Зависимости

- `spec_vr-immersive-controls.md` должен быть реализован первым — hand-tracking расширяет тот же `VrControllerInputManager` и HUD-систему.
- OpenXR action system (`spec_vr-immersive-controls.md`, Слой A) должен быть рабочим — hand-tracking добавляет параллельный Слой E, а не заменяет A.

---

## 5. Справочные материалы

- [Meta — XR_META_hand_tracking_aim](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-input/)
- [Meta — Hand tracking microgestures](https://developers.meta.com/horizon/documentation/unity/unity-microgestures/)
- [Godot OpenXR — Hand tracking guide](https://docs.godotengine.org/en/stable/tutorials/xr/openxr_hand_tracking.html)
- [Meta-OpenXR-SDK — XrHandDataSource sample](https://github.com/meta-quest/Meta-OpenXR-SDK)

---

## 14. Что явно выходит за рамки этого спека

- Hand-tracking в 2D-режиме приложения (только иммерсивный VR-плеер).
- Голосовые команды.
- Пользовательская настройка жестов.
- Zoom жестом "разведение рук" — отдельный тикет.
